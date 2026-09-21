import com.cinema.chat.embedding.FaqMarkdownSplitter;
import com.cinema.chat.embedding.LocalHashEmbeddingModel;
import com.cinema.chat.tool.FaqSearchTool;
import com.cinema.chat.tool.FaqSource;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * RAG 离线验证与门槛标定：不依赖 MySQL / Redis / DeepSeek / 网络。
 * 直接驱动生产代码（FaqMarkdownSplitter + LocalHashEmbeddingModel + FaqSearchTool）。
 *
 * 【为什么单独放 tools/ 而不是 target/ 或 src/test/】
 * - 放 target/ 会被 mvn clean 删掉；这个工装是产出 min-coverage=0.27 的工具，
 *   README 又要求「改完知识库要重新标定门槛」，得能反复用。
 * - 放 src/test/ 需要 JUnit，也就必须改 pom.xml（本方案承诺零新增依赖），
 *   而且会给一个本来没有测试的项目引入 surefire 的配置问题。
 * 所以放在 Maven 构建之外的 tools/ 下，手动 javac/java 跑，对构建零影响。
 *
 * 【怎么跑】（在 server/ 目录下，先保证 mvn compile 过）
 *   mvn -q compile
 *   mvn -q dependency:build-classpath "-Dmdep.outputFile=target/cp.txt"
 *   javac -encoding UTF-8 -cp "target/classes;$(cat target/cp.txt)" -d target/ragcheck tools/ragcheck/FaqCheck.java
 *   java  -Dfile.encoding=UTF-8 -cp "target/ragcheck;target/classes;$(cat target/cp.txt)" FaqCheck target/ragcheck/report.txt
 * PowerShell 下分隔符是 ';'，类路径拼接写法同上（$(cat …) 换成 (Get-Content target/cp.txt)）。
 * 报告写在 target/ragcheck/report.txt，控制台不输出。
 */
public class FaqCheck {

    /** 覆盖率门槛的候选值，标定用 */
    private static final double GATE = 0.27;

    private static final String[] FAQ_FILES = {
            "faq-buy.md", "faq-refund.md", "faq-seckill.md",
            "faq-pay.md", "faq-seat.md", "faq-misc.md",
    };

    private static PrintStream out;
    private static int failed = 0;

    /** 字面重合，必须命中 */
    private static final String[] HIT = {
            "退票怎么弄", "锁座多久失效", "一张订单最多买几张票",
            "抢票提示人数过多怎么办", "选座有什么限制", "支付方式有哪些", "订单超时多久取消",
    };

    /** 知识库未收录，必须什么都召不回 */
    private static final String[] MISS = {
            "能带宠物吗", "今天天气怎么样", "怎么开发票",
    };

    /** 与知识库措辞不重合，用来验证 Query Rewriting 的价值 */
    private static final String[] PARAPHRASE = {
            "我不想看了能退钱吗", "座位能留多久", "一次买太多张可以吗",
    };

    public static void main(String[] args) throws Exception {
        out = new PrintStream(new FileOutputStream(args[0]), true, StandardCharsets.UTF_8);
        System.setErr(out);

        // ================= A. 切分 =================
        section("A. 知识库切分");
        List<TextSegment> segments = FaqMarkdownSplitter.loadAll(FAQ_FILES, "rag/");
        out.printf("总片段数：%d%n", segments.size());
        check("片段数落在 20~40 的设计区间", segments.size() >= 20 && segments.size() <= 40);
        check("每个片段都有 title 元数据",
                segments.stream().allMatch(s -> s.metadata().getString("title") != null));
        check("每个片段都有 category 元数据",
                segments.stream().allMatch(s -> !s.metadata().getString("category").isEmpty()));
        for (String f : FAQ_FILES) {
            long n = segments.stream().filter(s -> f.equals(s.metadata().getString("source"))).count();
            check(f + " 至少切出 1 个片段", n > 0);
        }
        check("首个 ## 之前的内容不成为片段",
                FaqMarkdownSplitter.split("# 分类\n引言文字\n## 标题\n正文", "t.md").size() == 1);
        check("代码块围栏内的 ## 不切分",
                FaqMarkdownSplitter.split("# C\n## A\n```\n## 假标题\n```\n尾", "t.md").size() == 1);
        check("空小节被跳过",
                FaqMarkdownSplitter.split("# C\n## 空\n\n## 有内容\n正文", "t.md").size() == 1);
        check("分类名进入向量文本",
                FaqMarkdownSplitter.split("# 退票退款\n## 怎么退\n正文", "t.md")
                        .get(0).text().startsWith("【退票退款 > 怎么退】"));

        // ================= B. 建库 =================
        section("B. 建立向量库（本地哈希，512 维，零网络）");
        LocalHashEmbeddingModel model = new LocalHashEmbeddingModel(512);
        long t0 = System.currentTimeMillis();
        List<Embedding> embeddings = model.embedAll(segments).content();
        long embedMs = System.currentTimeMillis() - t0;
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        List<String> ids = IntStream.range(0, segments.size()).mapToObj(i -> "faq-" + i).toList();
        store.addAll(ids, embeddings, segments);
        out.printf("入库 %d 片段，向量化耗时 %d ms（= 启动开销）%n", store.size(), embedMs);
        check("向量数与片段数一致", embeddings.size() == segments.size());
        check("维度正确", embeddings.get(0).dimension() == 512);

        // ================= C. 两种指标的对照 =================
        section("C. 余弦 vs 覆盖率：同一个查询，两种指标各自的第一名");
        out.println("余弦 = 向量点积（LangChain4j 映射为 (cos+1)/2）；coverage = 查询词 IDF 质量落在片段里的比例");
        out.println("min-score=0.70 ⇔ 余弦 0.40；覆盖率门槛候选值 " + GATE + "\n");
        out.printf("%-22s %-8s %-8s %-34s %-8s %s%n",
                "查询", "余弦", "Top-1", "余弦第一名", "cov", "覆盖率第一名");
        out.println("-".repeat(140));
        for (String[] group : new String[][]{HIT, MISS, PARAPHRASE}) {
            for (String q : group) {
                EmbeddingMatch<TextSegment> c1 = rawSearch(store, model, q, 1).get(0);
                TextSegment vBest = null;
                double vc = -1;
                for (TextSegment s : segments) {
                    double c = model.coverage(q, s.text());
                    if (c > vc) {
                        vc = c;
                        vBest = s;
                    }
                }
                out.printf("%-22s %-8.4f %-8s %-34s %-8.4f %s%n",
                        q, c1.score(), "", clip(c1.embedded().metadata().getString("title"), 32),
                        vc, clip(vBest.metadata().getString("title"), 30));
            }
            out.println("-".repeat(140));
        }

        // ================= D. 覆盖率门槛的守门效果 =================
        section("D. 覆盖率门槛 " + GATE + " 的守门效果（直接跑 FaqSearchTool）");
        for (String q : HIT) {
            FaqSearchTool tool = new FaqSearchTool(model, store, null, 4, GATE, false, 0);
            String rr = tool.searchFaq(q);
            List<FaqSource> src = tool.sources();
            check("应命中却落空：" + q, !src.isEmpty());
            // 「依据」列表的顺序必须与喂给模型的上下文顺序一致：
            // sources() 是另起一次排序，hits 又是 ConcurrentHashMap，
            // 少了并列规则就会变成随哈希分布漂移的随机顺序
            check("sources() 顺序 == 上下文顺序：" + q,
                    src.stream().map(FaqSource::title).toList().equals(contextTitles(rr)));
            out.printf("  %-22s -> %s%n", q, src.isEmpty() ? "!! 空 !!"
                    : src.get(0).title() + String.format(" (%.4f)", src.get(0).score()));
        }
        for (String q : MISS) {
            FaqSearchTool tool = new FaqSearchTool(model, store, null, 4, GATE, false, 0);
            String r = tool.searchFaq(q);
            List<FaqSource> src = tool.sources();
            check("不应命中却召回了：" + q, src.isEmpty());
            check("未命中时给出「未收录」提示：" + q, r.contains("未收录"));
        }
        for (String q : PARAPHRASE) {
            FaqSearchTool tool = new FaqSearchTool(model, store, null, 4, GATE, false, 0);
            tool.searchFaq(q);
            List<FaqSource> src = tool.sources();
            out.printf("  %-22s -> %s%n", q, src.isEmpty() ? "空" : src.get(0).title());
        }

        // ================= E. Query Rewriting =================
        section("E. Query Rewriting 多路召回（假 ChatModel 注入预设改写，零网络）");
        String canned = "1. 退票\n2. \"退款 退钱\"\n- 取消订单 已支付\n3. 退票\n\n";
        out.println("E1 注入模型输出：" + canned.replace("\n", "\\n"));
        out.println("   （编号/引号/项目符号/空行/重复项都应被清洗掉，见日志 [RAG] 查询改写 行）");
        FaqSearchTool e1 = new FaqSearchTool(model, store, fakeRewriter(canned), 4, GATE, true, 3);
        e1.searchFaq("我不想看了能退钱吗");

        out.println("\nE2 改写失败必须降级为单路检索，不能连检索一起挂掉");
        FaqSearchTool e2 = new FaqSearchTool(model, store, fakeRewriter(null), 4, GATE, true, 3);
        String r2 = e2.searchFaq("退票怎么弄");
        check("改写抛异常后仍完成检索（未返回「检索暂时不可用」）", !r2.contains("检索暂时不可用"));
        check("改写抛异常后仍召回片段", !e2.sources().isEmpty());

        out.println("\nE3 字面不重合的问法：单路 vs 多路");
        for (String q : PARAPHRASE) {
            FaqSearchTool single = new FaqSearchTool(model, store, null, 4, GATE, false, 0);
            single.searchFaq(q);
            FaqSearchTool multi = new FaqSearchTool(model, store, fakeRewriter(String.join("\n", variantsFor(q))),
                    4, GATE, true, 3);
            multi.searchFaq(q);
            out.printf("  %s%n    单路：%s%n    多路：%s%n", q,
                    single.sources().isEmpty() ? "空" : single.sources().get(0).title(),
                    multi.sources().isEmpty() ? "空" : multi.sources().get(0).title());
            check("多路召回不劣于单路：" + q, multi.sources().size() >= single.sources().size());
        }

        // ================= F. 分离度总结 =================
        section("F. 分离度：HIT 组最小值 vs MISS 组最大值");
        double hitMinCov = 1, missMaxCov = 0, hitMinCos = 1, missMaxCos = 0;
        for (String q : HIT) {
            hitMinCov = Math.min(hitMinCov, bestCoverage(model, segments, q));
            hitMinCos = Math.min(hitMinCos, rawSearch(store, model, q, 1).get(0).score());
        }
        for (String q : MISS) {
            missMaxCov = Math.max(missMaxCov, bestCoverage(model, segments, q));
            missMaxCos = Math.max(missMaxCos, rawSearch(store, model, q, 1).get(0).score());
        }
        out.printf("  余弦   ：HIT 最小 %.4f，MISS 最大 %.4f，余量 %+.4f%n",
                hitMinCos, missMaxCos, hitMinCos - missMaxCos);
        out.printf("  覆盖率 ：HIT 最小 %.4f，MISS 最大 %.4f，余量 %+.4f%n",
                hitMinCov, missMaxCov, hitMinCov - missMaxCov);
        check("覆盖率能把两类分开（余量为正）", hitMinCov > missMaxCov);
        check("余弦分不开两类（余量为负，故不可用作阈值）", hitMinCos < missMaxCos);
        if (hitMinCov > missMaxCov) {
            out.printf("  => 门槛可取 %.2f ~ %.2f，本次验证用 %.2f%n",
                    missMaxCov, hitMinCov, GATE);
        }

        // ================= 结论 =================
        section("结论");
        out.printf("失败断言：%d%n", failed);
        out.println(failed == 0 ? "全部通过" : "存在失败项，见上方 !! 标记");
        out.close();
    }

    /** 模拟 DeepSeek 对口语化问法会给出的正式检索词 */
    private static List<String> variantsFor(String q) {
        return switch (q) {
            case "我不想看了能退钱吗" -> List.of("退票", "退款 退钱", "取消订单 已支付");
            case "座位能留多久" -> List.of("锁座", "订单超时", "选座 时效");
            case "一次买太多张可以吗" -> List.of("购票 数量限制", "每单 张数", "限购");
            default -> List.of(q);
        };
    }

    private static ChatModel fakeRewriter(String canned) {
        return new ChatModel() {
            @Override
            public ChatResponse chat(ChatMessage... messages) {
                if (canned == null) {
                    throw new RuntimeException("模拟 DeepSeek 超时");
                }
                return ChatResponse.builder().aiMessage(AiMessage.from(canned)).build();
            }
        };
    }

    private static List<EmbeddingMatch<TextSegment>> rawSearch(
            InMemoryEmbeddingStore<TextSegment> store, EmbeddingModel model, String q, int n) {
        return store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(model.embed(q).content())
                .maxResults(n)
                .minScore(0.0)
                .build()).matches();
    }

    private static double bestCoverage(LocalHashEmbeddingModel model, List<TextSegment> segments, String q) {
        double best = 0;
        for (TextSegment s : segments) {
            best = Math.max(best, model.coverage(q, s.text()));
        }
        return best;
    }

    /** 从检索返回的文本里按顺序取出片段标题（每个片段以「【分类 > 标题】」开头） */
    private static List<String> contextTitles(String result) {
        List<String> titles = new ArrayList<>();
        for (String line : result.split("\n")) {
            if (!line.startsWith("【")) {
                continue;
            }
            int sep = line.indexOf(" > ");
            int end = line.lastIndexOf("】");
            if (sep > 0 && end > sep) {
                titles.add(line.substring(sep + 3, end));
            }
        }
        return titles;
    }

    private static String clip(String s, int n) {
        if (s == null) {
            return "(无)";
        }
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }

    private static void section(String title) {
        out.println("\n" + "=".repeat(120));
        out.println("  " + title);
        out.println("=".repeat(120));
    }

    private static void check(String what, boolean ok) {
        if (!ok) {
            failed++;
        }
        out.printf("  [%s] %s%n", ok ? "OK" : "!!", what);
    }
}
