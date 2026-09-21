package com.cinema.chat.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地哈希向量模型：零依赖、零 API Key、完全离线
 *
 * 为什么自己实现而不是用现成的 embedding：
 * - DeepSeek 官方 API 不提供 embedding 端点（/v1/embeddings 返回 404），项目现有的 key 用不了
 * - 本地 ONNX 方案（langchain4j-embeddings-bge-small-zh-*）版本线停在 1.0.0-beta5，
 *   POM 写死 core 1.0.0，传递依赖还带 88.7MB onnxruntime，与 core 1.16.2 有兼容风险
 * 所以用纯 Java 做哈希向量，换取「不申请任何 key、不改 pom.xml、不下载模型」。
 *
 * 算法：中文字符 bigram + unigram + 字母数字词
 *      → BM25 式 TF-IDF 加权
 *      → 符号哈希压到固定维度（碰撞时正负相消，期望上不引入偏差）
 *      → L2 归一化
 *
 * 【已知取舍】这是「字面匹配」而非「语义匹配」：问「能退钱吗」命中不了「退票」。
 * 两条缓解手段：① FaqSearchTool 的 Query Rewriting 把用户措辞翻译成知识库措辞；
 * ② 写 FAQ 时在标题和正文里把同义说法写全。
 *
 * 【有状态说明】embedAll（入库路径）会统计语料 df/avgLen，之后 embed（检索路径）复用同一份。
 * 统计量在启动时一次性写入、之后只读，用 volatile 发布不可变对象保证可见性。
 */
public class LocalHashEmbeddingModel implements EmbeddingModel {

    /** BM25 参数：k1 控制词频饱和速度，b 控制长度归一化强度 */
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    /** 两个不同的 FNV-1a 种子：一个定桶位，一个定符号 */
    private static final int SEED_BUCKET = 0x811c9dc5;
    private static final int SEED_SIGN = 0x01000193;

    /** 单字判别力弱于二字组合（「票」不如「退票」具体），故降权 */
    private static final double UNIGRAM_WEIGHT = 0.5;
    private static final double BIGRAM_WEIGHT = 1.0;
    private static final double WORD_WEIGHT = 1.0;

    private final int dimension;

    /** 语料统计量：仅 embedAll 写入一次，其余时刻只读 */
    private volatile CorpusStats stats;

    public LocalHashEmbeddingModel(int dimension) {
        if (dimension < 16) {
            throw new IllegalArgumentException("向量维度至少为 16，实际传入 " + dimension);
        }
        this.dimension = dimension;
    }

    // ================= EmbeddingModel =================

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        // 第一遍：提取每段的特征，同时累积 df（含该特征的片段数）
        List<Map<String, Double>> termFreqs = new ArrayList<>(segments.size());
        Map<String, Integer> df = new HashMap<>();
        long totalLen = 0;
        for (TextSegment segment : segments) {
            Map<String, Double> tf = extractFeatures(segment.text());
            termFreqs.add(tf);
            totalLen += tf.size();
            for (String feature : tf.keySet()) {
                df.merge(feature, 1, Integer::sum);
            }
        }

        // 发布统计量。入库一定发生在检索之前，后续所有 embed() 都复用这一份
        CorpusStats corpus = new CorpusStats(
                Map.copyOf(df),
                segments.size(),
                segments.isEmpty() ? 1.0 : (double) totalLen / segments.size());
        this.stats = corpus;

        // 第二遍：带上 IDF 生成向量
        List<Embedding> embeddings = new ArrayList<>(segments.size());
        for (Map<String, Double> tf : termFreqs) {
            embeddings.add(Embedding.from(toVector(tf, corpus)));
        }
        return Response.from(embeddings);
    }

    @Override
    public Response<Embedding> embed(String text) {
        return Response.from(Embedding.from(toVector(extractFeatures(text), stats)));
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        return embed(textSegment.text());
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String modelName() {
        return "local-hash-embedding";
    }

    /**
     * 查询词覆盖率：query 的 IDF 质量有多大比例出现在该片段中。取值 0~1。
     *
     * 【为什么阈值不能建在余弦相似度上】—— 这是实测踩出来的坑
     * 短 query 与长片段的余弦天生偏低：query 只有十来个特征，片段有数百个，
     * 即使 query 的每个词都出现在片段里，点积也会被片段自身的模长摊薄。
     * 实测 25 个片段下，正确命中的余弦落在 0.10~0.25，而知识库完全没收录的
     * 「怎么开发票」也能冲到 0.18 —— 两个区间重叠，任何余弦阈值都分不开，
     * 阈值定高了全部落空，定低了垃圾片段照进。
     *
     * 覆盖率没有这个毛病：它只统计 query 这一侧，片段多长都不影响结果。
     * 而且知识库里没有的词（df=0 → IDF 最大）会把覆盖率直接拉低 ——
     * 「问的东西没收录就什么都匹配不上」这个性质被完整保留。
     */
    public double coverage(String query, String document) {
        Map<String, Double> queryFeatures = extractFeatures(query);
        if (queryFeatures.isEmpty()) {
            return 0.0;
        }
        Map<String, Double> documentFeatures = extractFeatures(document);
        CorpusStats corpus = stats;
        double total = 0;
        double hit = 0;
        for (String feature : queryFeatures.keySet()) {
            double weight = idf(feature, corpus);
            total += weight;
            if (documentFeatures.containsKey(feature)) {
                hit += weight;
            }
        }
        return total <= 0 ? 0.0 : hit / total;
    }

    // ================= 向量生成 =================

    private float[] toVector(Map<String, Double> tf, CorpusStats corpus) {
        float[] vector = new float[dimension];
        for (Map.Entry<String, Double> entry : tf.entrySet()) {
            String feature = entry.getKey();
            double weight = idf(feature, corpus) * saturatedTf(entry.getValue(), tf.size(), corpus);
            int index = Math.floorMod(fnv(feature, SEED_BUCKET), dimension);
            // 符号哈希：两个特征撞进同一桶时正负相消，比单纯取模更稳健
            float sign = (fnv(feature, SEED_SIGN) & 1) == 0 ? 1f : -1f;
            vector[index] += (float) (sign * weight);
        }
        normalize(vector);
        return vector;
    }

    /**
     * BM25 的 IDF。语料里到处都是的字（「的」「是」）df≈N → idf≈0，自动失权；
     * 而 query 里知识库完全没收录的词 df=0 → idf 偏大，但它与任何片段都不共享该特征
     * （点积贡献为 0），只会抬高 query 自身的模长从而压低所有分数 ——
     * 正好是「问的东西知识库没有，就什么都匹配不上」的期望行为。
     */
    private static double idf(String feature, CorpusStats corpus) {
        if (corpus == null) {
            // 尚未入库就收到检索请求（正常流程走不到），退化为纯词频
            return 1.0;
        }
        int df = corpus.df().getOrDefault(feature, 0);
        return Math.log(1 + (corpus.totalDocs() - df + 0.5) / (df + 0.5));
    }

    /** BM25 的词频饱和 + 长度归一化，避免长片段仅因字多就占优 */
    private static double saturatedTf(double tf, int len, CorpusStats corpus) {
        if (corpus == null || corpus.avgLen() <= 0) {
            return tf;
        }
        return tf * (K1 + 1) / (tf + K1 * (1 - B + B * len / corpus.avgLen()));
    }

    private static void normalize(float[] vector) {
        double sum = 0;
        for (float v : vector) {
            sum += v * v;
        }
        double norm = Math.sqrt(sum);
        if (norm < 1e-9) {
            // 空文本或全是分隔符，保持零向量，避免除零
            return;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= (float) norm;
        }
    }

    // ================= 特征提取 =================

    /**
     * 不引入分词器：中文用字符 bigram 捕捉词信息（「退票」「支付」「座位」天然成词），
     * 配 unigram 兜底单字问法；连续的字母数字整体作为一个特征。
     *
     * @return 特征 -> 加权词频（unigram 已降权）
     */
    private static Map<String, Double> extractFeatures(String text) {
        Map<String, Double> tf = new HashMap<>();
        if (text == null || text.isEmpty()) {
            return tf;
        }
        StringBuilder hanRun = new StringBuilder();
        StringBuilder wordRun = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isHan(c)) {
                flushWord(wordRun, tf);
                hanRun.append(c);
            } else if (Character.isLetterOrDigit(c)) {
                flushHan(hanRun, tf);
                wordRun.append(Character.toLowerCase(c));
            } else {
                // 标点、空白、换行一律作为分隔符丢弃
                flushHan(hanRun, tf);
                flushWord(wordRun, tf);
            }
        }
        flushHan(hanRun, tf);
        flushWord(wordRun, tf);
        return tf;
    }

    private static void flushHan(StringBuilder run, Map<String, Double> tf) {
        int len = run.length();
        if (len == 0) {
            return;
        }
        for (int i = 0; i < len; i++) {
            tf.merge(String.valueOf(run.charAt(i)), UNIGRAM_WEIGHT, Double::sum);
            if (i + 1 < len) {
                tf.merge(run.substring(i, i + 2), BIGRAM_WEIGHT, Double::sum);
            }
        }
        run.setLength(0);
    }

    private static void flushWord(StringBuilder run, Map<String, Double> tf) {
        if (run.length() > 0) {
            tf.merge(run.toString(), WORD_WEIGHT, Double::sum);
            run.setLength(0);
        }
    }

    /** 用 UnicodeScript 判定汉字：中文标点（「」，。）属 COMMON，会被正确当作分隔符 */
    private static boolean isHan(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }

    /**
     * FNV-1a 32 位哈希。用它而不是 String.hashCode()：后者跨 JVM 虽稳定但分布性差，
     * 且 FNV 混入每个字节、雪崩效应更好，压到 512 维时碰撞更少。
     */
    private static int fnv(String s, int seed) {
        int hash = seed;
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= 0x01000193;
        }
        return hash;
    }

    /**
     * 语料统计量。record + Map.copyOf 保证不可变，配合 volatile 字段实现安全发布。
     *
     * @param df        特征 -> 含该特征的片段数
     * @param totalDocs 片段总数
     * @param avgLen    片段平均特征数
     */
    private record CorpusStats(Map<String, Integer> df, int totalDocs, double avgLen) {
    }
}
