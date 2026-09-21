package com.cinema.config;

import com.cinema.chat.embedding.FaqMarkdownSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

/**
 * 启动时把 rag/*.md 切分并灌入 FAQ 向量库（进程内、不做持久化）
 *
 * 不做持久化的理由：几十个片段一次算完是毫秒级的，持久化的收益只存在于
 * 「启动时向量化失败」这一种情况；却会引入「知识库改了但缓存没失效 → 依据旧 FAQ 回答」
 * 这个对演示致命的失败模式，不划算。
 *
 * 与 BloomInitRunner 的关键差别：整个 run() 必须被 try/catch 包住。
 * BloomInitRunner 那种裸奔写法一旦初始化失败会直接让整个应用起不来 ——
 * 而 FAQ 检索只是锦上添花，绝不能因为知识库有问题就阻断购票主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaqVectorInitRunner implements CommandLineRunner {

    /** 显式文件名清单，理由见 FaqMarkdownSplitter#loadAll 的注释 */
    private static final String[] FAQ_FILES = {
            "faq-buy.md",
            "faq-refund.md",
            "faq-seckill.md",
            "faq-pay.md",
            "faq-seat.md",
            "faq-misc.md",
    };

    private static final String RAG_DIR = "rag/";

    @Value("${cinema.rag.enabled:true}")
    private boolean enabled;

    private final EmbeddingModel embeddingModel;
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.warn("[RAG] cinema.rag.enabled=false，跳过 FAQ 向量化，AI 将无法回答规则类问题");
            return;
        }
        try {
            List<TextSegment> segments = FaqMarkdownSplitter.loadAll(FAQ_FILES, RAG_DIR);
            if (segments.isEmpty()) {
                log.warn("[RAG] rag/*.md 未解析出任何片段，FAQ 检索不可用");
                return;
            }
            // 一次算完全部向量（本地实现，不涉及任何网络请求）
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            // ids / embeddings / segments 三个 list 长度必须一致，否则 addAll 抛 IllegalArgumentException
            List<String> ids = IntStream.range(0, segments.size())
                    .mapToObj(i -> "faq-" + i)
                    .toList();
            embeddingStore.addAll(ids, embeddings, segments);
            log.info("[RAG] 就绪 {} 片段", embeddingStore.size());
        } catch (Exception e) {
            log.error("[RAG] FAQ 向量化失败，本次启动不启用检索（不影响其它功能）", e);
        }
    }
}
