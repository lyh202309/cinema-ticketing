package com.cinema.config;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    /** DeepSeek 流式对话模型 */
    @Bean
    public OpenAiStreamingChatModel openAiStreamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName("deepseek-chat")
                .build();
    }
}
