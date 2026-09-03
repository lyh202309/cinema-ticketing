package com.cinema.controller;

import com.cinema.common.Result;
import com.cinema.dto.ChatRequest;
import com.cinema.entity.ChatConversation;
import com.cinema.entity.ChatMessage;
import com.cinema.service.IChatService;
import com.cinema.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 对话助手（需登录）：独立聊天页入口
 * SSE 流式：POST /chat/conversations/{id}/chat
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final IChatService chatService;

    @GetMapping("/conversations")
    public Result<List<ChatConversation>> conversations() {
        return Result.ok(chatService.listConversations(UserHolder.getUserId()));
    }

    @PostMapping("/conversations")
    public Result<ChatConversation> createConversation() {
        return Result.ok(chatService.createConversation(UserHolder.getUserId()));
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<List<ChatMessage>> messages(@PathVariable Long id) {
        return Result.ok(chatService.listMessages(id, UserHolder.getUserId()));
    }

    @DeleteMapping("/conversations/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        chatService.deleteConversation(id, UserHolder.getUserId());
        return Result.ok();
    }

    /** SSE 流式问答 */
    @PostMapping("/conversations/{id}/chat")
    public SseEmitter chat(@PathVariable Long id, @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        chatService.ask(id, UserHolder.getUserId(), request.getMessage(), emitter);
        return emitter;
    }
}
