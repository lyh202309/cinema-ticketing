package com.cinema.chat;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * AI 助手接口（LangChain4j AiServices 动态代理）
 * memoryId = 会话 id（按会话恢复历史上下文）
 */
public interface CinemaAssistant {

    @SystemMessage(fromResource = "prompts/assistant-system.txt")
    TokenStream chat(@MemoryId String memoryId, @UserMessage String userMessage);
}
