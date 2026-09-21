package com.cinema.service;

import com.cinema.chat.tool.FaqSource;
import com.cinema.entity.ChatConversation;
import com.cinema.entity.ChatMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 聊天服务：会话管理 + 历史消息 + 流式问答
 */
public interface IChatService {

    /** 会话列表（按更新时间倒序） */
    List<ChatConversation> listConversations(Long userId);

    /** 新建会话 */
    ChatConversation createConversation(Long userId);

    /** 删除会话（含消息 + Redis 缓存） */
    void deleteConversation(Long conversationId, Long userId);

    /** 历史消息（校验归属） */
    List<ChatMessage> listMessages(Long conversationId, Long userId);

    /** 发送消息并 SSE 流式回答（校验归属；异步执行，消息落 DB 永久存档） */
    void ask(Long conversationId, Long userId, String message, SseEmitter emitter);

    /** FAQ 检索调试：直接打知识库、不走对话链路，用于标定 min-score */
    List<FaqSource> searchFaq(String query);
}
