package com.cinema.chat.tool;

import cn.hutool.core.util.StrUtil;
import com.cinema.chat.embedding.LocalHashEmbeddingModel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 工具：检索 FAQ 知识库（只读）
 *
 * 【为什么每次请求 new，不做成 @Component 单例】
 * hits 是请求级状态（这一轮对话引用了哪几条 FAQ）。做成单例会让 A 用户看到 B 用户的引用来源，
 * 也会让 sources() 无限增长。OrderQueryTool 已经因为「流式跑在异步线程、ThreadLocal 取不到 userId」
 * 采用了同样的「每次请求构造」模式，这里保持一致。
 *
 * 【检索策略：余弦粗筛 + 覆盖率打分守门 + Query Rewriting 补召回】
 * 各环节的分工与取舍都是用真实问句实测标定出来的，详见
 * LocalHashEmbeddingModel#coverage 与 #multiRouteSearch 的注释。
 */
@Slf4j
public class FaqSearchTool {

    /**
     * 改写用的系统提示词。要点是「每行一个、不要编号、不要解释」——
     * 模型的自由发挥越多，解析越容易出错。
     */
    private static final String REWRITE_SYSTEM_PROMPT = """
            你是电影院订票系统的检索词生成器。
            把用户的问题转换成若干个用于检索知识库的关键词组合。
            要求：
            1. 每行输出一个，不要编号、不要引号、不要任何解释说明
            2. 用词贴近影院订票系统的正式说法，如「退票」「锁座」「待支付」「热门场次」
            3. 适当覆盖用户可能说的同义口语，如「退钱」「不想看了」「没付款」
            4. 只输出检索词，不要输出完整句子
            """;

    /** 候选片段 + 它的查询词覆盖率 */
    private record Scored(EmbeddingMatch<TextSegment> match, double coverage) {
    }

    /**
     * 覆盖率优先、余弦兜底。
     * 覆盖率是「集合重叠度」，不区分词频与位置，很容易出现并列
     * （实测「退票怎么弄」下两个片段并列 0.3007）；并列时用余弦分胜负 ——
     * 余弦的绝对值不可用，但它的排序是准的，拿来打破并列正合适。
     */
    private static final Comparator<Scored> BY_COVERAGE_THEN_COSINE = Comparator
            .comparingDouble(Scored::coverage).reversed()
            .thenComparing(Comparator.comparingDouble((Scored s) -> s.match().score()).reversed());

    private final LocalHashEmbeddingModel embeddingModel;
    private final InMemoryEmbeddingStore<TextSegment> store;
    private final ChatModel rewriteChatModel;
    private final int topK;
    private final double minCoverage;
    private final boolean rewriteEnabled;
    private final int rewriteCount;

    /**
     * 命中收集器。用 ConcurrentHashMap + merge 而不是 List + append：
     * 模型可能在一条 AiMessage 里返回多个 tool_calls 并行执行，append 顺序不确定，
     * 且重复片段无法去重。键用 embeddingId 而非 title —— 不同文件可能出现同名小节标题。
     */
    private final Map<String, FaqSource> hits = new ConcurrentHashMap<>();

    /**
     * 片段首次被命中的序号，只为让 sources() 在分数并列时排序仍然确定。
     * 覆盖率是集合重叠度，并列很常见；而 hits 是 ConcurrentHashMap，
     * 直接按分数排并列项的顺序会随哈希分布漂移 —— 调试端点正是靠这个顺序看排名的。
     */
    private final Map<String, Integer> hitOrder = new ConcurrentHashMap<>();
    private final AtomicInteger hitSequence = new AtomicInteger();

    public FaqSearchTool(LocalHashEmbeddingModel embeddingModel, InMemoryEmbeddingStore<TextSegment> store,
                         ChatModel rewriteChatModel, int topK, double minCoverage,
                         boolean rewriteEnabled, int rewriteCount) {
        this.embeddingModel = embeddingModel;
        this.store = store;
        this.rewriteChatModel = rewriteChatModel;
        this.topK = topK;
        this.minCoverage = minCoverage;
        this.rewriteEnabled = rewriteEnabled;
        this.rewriteCount = rewriteCount;
    }

    @Tool("检索影院购票知识库，覆盖购票流程、退票退款、取消订单、热门场次抢票、"
            + "支付与订单超时、选座、影院与场次查询等规则类问题。"
            + "凡是询问规则、流程、能否、怎么办、多久、多少钱、有什么限制的问题，"
            + "必须先调用本工具，并严格依据返回内容回答。")
    public String searchFaq(@P("用户问题的原话") String query) {
        try {
            if (store.isEmpty()) {
                return "知识库未启用，请如实说明暂时无法确认规则细节。";
            }
            if (StrUtil.isBlank(query)) {
                return "问题为空，请重新组织用户的问题后再检索。";
            }

            List<Scored> matches = multiRouteSearch(buildRoutes(query.trim()));
            if (matches.isEmpty()) {
                return "知识库未收录相关内容。请如实告知不确定，不要凭常识编造。";
            }

            StringBuilder sb = new StringBuilder(
                    "以下是相关知识库片段，请严格依据这些内容回答，不要补充片段之外的说法：\n");
            for (Scored scored : matches) {
                TextSegment segment = scored.match().embedded();
                String title = segment.metadata().getString("title");
                log.info("[RAG] q={} title={} coverage={}", query, title, scored.coverage());
                String embeddingId = scored.match().embeddingId();
                hitOrder.computeIfAbsent(embeddingId, key -> hitSequence.getAndIncrement());
                hits.merge(embeddingId, new FaqSource(title, scored.coverage()),
                        (a, b) -> a.score() >= b.score() ? a : b);
                // 片段文本本身已含「【分类 > 标题】」前缀，直接输出即可
                sb.append(segment.text()).append("\n---\n");
            }
            return sb.toString();
        } catch (Exception e) {
            // 关键：绝不能让异常冒出去。langchain4j 的 ToolService 设了
            // propagateToolExecutionExceptions(true)，工具抛异常会让整条流走 onError，
            // 用户只看到「[出错了，请稍后重试]」，而且已经生成的内容全部丢失。
            log.warn("[RAG] 检索失败 query={}", query, e);
            return "检索暂时不可用，请如实说明无法确认，不要编造。";
        }
    }

    /**
     * 排序 + 截断，供落库到 tb_chat_message.sources
     *
     * 分数并列时按命中顺序（也就是检索排名）排，不用哈希顺序 ——
     * 否则「依据」列表和 /chat/faq/search 调试端点每次返回的顺序可能都不一样。
     */
    public List<FaqSource> sources() {
        return hits.entrySet().stream()
                .sorted(Comparator
                        .comparingDouble((Map.Entry<String, FaqSource> e) -> e.getValue().score()).reversed()
                        .thenComparingInt(e -> hitOrder.getOrDefault(e.getKey(), Integer.MAX_VALUE)))
                .limit(topK)
                .map(Map.Entry::getValue)
                .toList();
    }

    // ================= 检索 =================

    /**
     * 构造检索路。原始 query 永远是第 0 路 —— 改写可能跑偏，它是保底。
     */
    private List<String> buildRoutes(String query) {
        List<String> routes = new ArrayList<>();
        routes.add(query);
        if (rewriteEnabled) {
            routes.addAll(rewrite(query));
        }
        return routes.stream().distinct().toList();
    }

    /**
     * Query Rewriting：把用户的措辞翻译成知识库可能使用的措辞。
     *
     * 任何失败（超时 / 报错 / 输出解析不出）都返回空列表，让调用方降级为单路检索。
     * 改写只是锦上添花，绝不能因为它挂了就不检索了。
     */
    private List<String> rewrite(String query) {
        try {
            String answer = rewriteChatModel.chat(
                    SystemMessage.from(REWRITE_SYSTEM_PROMPT),
                    UserMessage.from(query)).aiMessage().text();
            if (StrUtil.isBlank(answer)) {
                return List.of();
            }
            List<String> variants = answer.lines()
                    .map(FaqSearchTool::cleanVariant)
                    .filter(StrUtil::isNotBlank)
                    .filter(variant -> !variant.equals(query))
                    .limit(rewriteCount)
                    .toList();
            log.info("[RAG] 查询改写 q={} -> {}", query, variants);
            return variants;
        } catch (Exception e) {
            log.warn("[RAG] 查询改写失败，降级为单路检索 query={}", query, e);
            return List.of();
        }
    }

    /** 模型常带回编号、引号、项目符号，逐行清洗掉再当检索词用 */
    private static String cleanVariant(String line) {
        return line.trim()
                .replaceAll("^\\d+[.、)）]\\s*", "")
                .replaceAll("^[-*•]\\s*", "")
                .replaceAll("^[\"'「『]", "")
                .replaceAll("[\"'」』]$", "")
                .trim();
    }

    /**
     * 多路检索：原始 query 的结果占第一梯队，改写变体只在没填满时【补召回】。
     *
     * 【为什么不用 RRF】原设计是 RRF 排名融合，被实测否掉了：
     * 改写出来的变体是「退票」「退款 退钱」这类关键词式短查询，彼此高度相关
     * （共享同一套字面特征），而 RRF 的增益只在「多路互不相关」时才成立。
     * 高度相关时它会把某一路的噪声也累加起来，反而把原始 query 命中的正确片段挤下去 ——
     * 实测「我不想看了能退钱吗」单路命中「已支付的订单怎么退票？能退钱吗？」，
     * 加了 RRF 之后 top-1 变成了不相关的「订单有哪些状态？」。
     * 多路召回的价值本来就在于补召回，而不是重排，所以这里用两梯队而不是融合。
     */
    private List<Scored> multiRouteSearch(List<String> routes) {
        String primary = routes.get(0);
        // 第一梯队：原始 query 自己命中的，排序完全由它自己决定，变体不参与
        List<Scored> base = searchOne(primary);
        if (base.size() >= topK || routes.size() == 1) {
            return base.stream().limit(topK).toList();
        }

        // 第二梯队：原始 query 没填满，才用变体补
        Set<String> seen = new HashSet<>();
        for (Scored scored : base) {
            seen.add(scored.match().embeddingId());
        }
        List<Scored> extra = new ArrayList<>();
        for (String route : routes.subList(1, routes.size())) {
            for (Scored scored : searchOne(route)) {
                if (!seen.add(scored.match().embeddingId())) {
                    continue;
                }
                // 排序仍用【原始 query 上的覆盖率】，不用变体上的：
                // 变体多是「退票」这种单关键词，片段只要含这两个字覆盖率就是 1.0，
                // 毫无分辨力，拿它排序会把不相关片段排到前面。
                //
                // 注意：这里的覆盖率【允许低于门槛】。变体能给片段作保，但不能替它拿分 ——
                // 实测「一次买太多张可以吗」靠变体补回「一个订单最多能买几张票？」，
                // 而它在原话上的覆盖率只有 0.1857 < 0.27，是条真答案。
                // 它排在所有第一梯队片段之后，靠的是下面 base 在前的拼接顺序，不是分数。
                extra.add(new Scored(scored.match(),
                        embeddingModel.coverage(primary, scored.match().embedded().text())));
            }
        }
        extra.sort(BY_COVERAGE_THEN_COSINE);

        List<Scored> result = new ArrayList<>(base);
        result.addAll(extra);
        return result.stream().limit(topK).toList();
    }

    /**
     * 单路检索：余弦只负责「粗筛候选」，覆盖率负责「打分 + 守门」。
     *
     * 这个分工是实测逼出来的：短 query 与长片段的余弦天生偏低（0.10~0.25），
     * 知识库完全没收录的问题也能拿到 0.18，两个区间重叠，余弦的绝对值不可用作阈值。
     * 但余弦的【排序】是准的，所以留着它做粗筛。
     */
    private List<Scored> searchOne(String route) {
        // 候选取宽一些：既然余弦量纲不可靠，就宁可比 topK 多捞几倍，
        // 也不要让正确片段在粗筛阶段就被丢掉
        int candidateSize = Math.max(topK * 4, 16);
        List<EmbeddingMatch<TextSegment>> matches = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed(route).content())
                .maxResults(candidateSize)
                .minScore(0.0)
                .build()).matches();

        List<Scored> scored = new ArrayList<>(matches.size());
        for (EmbeddingMatch<TextSegment> match : matches) {
            double coverage = embeddingModel.coverage(route, match.embedded().text());
            if (coverage >= minCoverage) {
                scored.add(new Scored(match, coverage));
            }
        }
        scored.sort(BY_COVERAGE_THEN_COSINE);
        return scored;
    }
}
