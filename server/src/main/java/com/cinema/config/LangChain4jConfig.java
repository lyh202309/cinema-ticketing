package com.cinema.config;

import com.cinema.chat.embedding.LocalHashEmbeddingModel;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j 手动装配（不用 spring starter，避免与 Boot 自动配置冲突）
 * DeepSeek 兼容 OpenAI 协议：baseUrl 指到 DeepSeek，用 OpenAiStreamingChatModel（SSE 流式）
 */
@Configuration
public class LangChain4jConfig {

    @Value("${cinema.deepseek.api-key:}")
    private String apiKey;

    @Value("${cinema.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${cinema.rag.dimension:512}")
    private int embeddingDimension;

    /** DeepSeek 流式对话模型 */
    @Bean
    public OpenAiStreamingChatModel openAiStreamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName("deepseek-chat")
                .build();
    }

    /**
     * FAQ 向量模型：本地哈希实现，零依赖零 key 完全离线。
     *
     * 返回类型是具体类而不是 EmbeddingModel 接口：检索链路除了向量还要用
     * LocalHashEmbeddingModel#coverage 做守门（余弦的绝对值在本场景不可用作阈值）。
     */
    @Bean
    public LocalHashEmbeddingModel embeddingModel() {
        return new LocalHashEmbeddingModel(embeddingDimension);
    }

    /** FAQ 向量库（进程内，启动时灌入；规模几十条，无需向量数据库） */
    @Bean
    public InMemoryEmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    /**
     * 非流式对话模型：只用于检索前的 Query Rewriting。
     * 改写必须拿到完整结果才能去检索，流式没有意义。
     * 超时压到 8 秒且【不重试】—— 改写只是锦上添花，失败要立刻降级为单路检索，
     * 不能让重试把首 token 延迟拖垮。
     */
    @Bean
    public OpenAiChatModel rewriteChatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName("deepseek-chat")
                .temperature(0.0)
                .timeout(Duration.ofSeconds(8))
                .maxRetries(0)
                .build();
    }
}
