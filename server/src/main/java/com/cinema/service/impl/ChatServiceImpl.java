package com.cinema.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cinema.chat.ChatMemoryStoreRedisDb;
import com.cinema.chat.CinemaAssistant;
import com.cinema.chat.embedding.LocalHashEmbeddingModel;
import com.cinema.chat.tool.FaqSearchTool;
import com.cinema.chat.tool.FaqSource;
import com.cinema.chat.tool.OrderQueryTool;
import com.cinema.chat.tool.SeatQueryTool;
import com.cinema.chat.tool.SessionQueryTool;
import com.cinema.common.BusinessException;
import com.cinema.entity.ChatConversation;
import com.cinema.entity.ChatMessage;
import com.cinema.mapper.ChatConversationMapper;
import com.cinema.mapper.ChatMessageMapper;
import com.cinema.mapper.MovieMapper;
import com.cinema.mapper.OrderMapper;
import com.cinema.mapper.OrderSeatMapper;
import com.cinema.mapper.SessionMapper;
import com.cinema.service.IChatService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements IChatService {

    private static final int MAX_MEMORY_MESSAGES = 20;

    /**
     * 工具调用最大轮次：默认值是 100，配合提示词里「规则类问题必须先调 searchFaq」的要求，
     * 最坏情况一次问答能打 100 轮检索。收紧到 4 轮足够覆盖「查规则 + 查座位 + 追问」的组合。
     */
    private static final int MAX_TOOL_ROUND_TRIPS = 4;

    private static final ExecutorService AI_EXECUTOR = Executors.newCachedThreadPool();

    private final ChatConversationMapper chatConversationMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMemoryStoreRedisDb chatMemoryStore;
    private final OpenAiStreamingChatModel openAiStreamingChatModel;
    private final SessionQueryTool sessionQueryTool;
    private final SeatQueryTool seatQueryTool;
    private final OrderMapper orderMapper;
    private final SessionMapper sessionMapper;
    private final MovieMapper movieMapper;
    private final OrderSeatMapper orderSeatMapper;
    private final LocalHashEmbeddingModel embeddingModel;
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private final OpenAiChatModel rewriteChatModel;

    @Value("${cinema.rag.top-k:4}")
    private int ragTopK;

    /** 守门阈值是「查询词覆盖率」不是余弦相似度，理由见 LocalHashEmbeddingModel#coverage */
    @Value("${cinema.rag.min-coverage:0.27}")
    private double ragMinCoverage;

    @Value("${cinema.rag.rewrite-enabled:true}")
    private boolean ragRewriteEnabled;

    @Value("${cinema.rag.rewrite-count:3}")
    private int ragRewriteCount;

    // ================= 会话管理 =================

    @Override
    public List<ChatConversation> listConversations(Long userId) {
        return chatConversationMapper.selectList(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getUserId, userId)
                .orderByDesc(ChatConversation::getUpdateTime));
    }

    @Override
    public ChatConversation createConversation(Long userId) {
        ChatConversation conv = new ChatConversation();
        conv.setUserId(userId);
        conv.setTitle("");
        chatConversationMapper.insert(conv);
        return conv;
    }

    @Override
    public void deleteConversation(Long conversationId, Long userId) {
        mustOwn(conversationId, userId);
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId));
        chatConversationMapper.deleteById(conversationId);
        chatMemoryStore.deleteMessages(String.valueOf(conversationId));
    }

    @Override
    public List<ChatMessage> listMessages(Long conversationId, Long userId) {
        mustOwn(conversationId, userId);
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getId));
    }

    // ================= FAQ 检索调试 =================

    @Override
    public List<FaqSource> searchFaq(String query) {
        FaqSearchTool tool = newFaqTool();
        tool.searchFaq(query);
        return tool.sources();
    }

    // ================= 流式问答 =================

    @Override
    public void ask(Long conversationId, Long userId, String message, SseEmitter emitter) {
        mustOwn(conversationId, userId);

        // 用户消息落 DB（永久存档），用户消息没有引用来源
        saveMessage(conversationId, 0, message, null);
        ensureTitle(conversationId, message);

        // 每个请求一个实例：hits 是请求级状态，做成单例会跨请求污染引用来源
        FaqSearchTool faqTool = newFaqTool();

        // 构建助手（每个请求绑定 userId 的工具；memoryId = 会话 id，从 DB/Redis 恢复历史）
        CinemaAssistant assistant = AiServices.builder(CinemaAssistant.class)
                .streamingChatModel(openAiStreamingChatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(MAX_MEMORY_MESSAGES)
                        .chatMemoryStore(chatMemoryStore)
                        .build())
                .tools(sessionQueryTool, seatQueryTool,
                        new OrderQueryTool(userId, orderMapper, sessionMapper, movieMapper, orderSeatMapper),
                        faqTool)
                .maxToolCallingRoundTrips(MAX_TOOL_ROUND_TRIPS)
                .build();

        TokenStream tokenStream = assistant.chat(String.valueOf(conversationId), message);

        AI_EXECUTOR.execute(() -> {
            try {
                tokenStream
                        .onPartialResponse(partial -> emit(emitter, partial))
                        .onCompleteResponse(response -> {
                            String full = response.aiMessage().text();
                            // 助手回复落 DB（永久存档），带上本轮引用的 FAQ 标题。
                            // 这一步必须排在 [DONE] 与 complete() 之前 —— 前端在 finally 里
                            // 会立刻回拉消息，晚了就读不到 sources。
                            saveMessage(conversationId, 1, full, sourcesJson(faqTool));
                            emit(emitter, "[DONE]");
                            emitter.complete();
                        })
                        .onError(error -> {
                            log.error("[AI] 对话异常 conversationId={}", conversationId, error);
                            emitter.completeWithError(error);
                        })
                        .start();
            } catch (Exception e) {
                log.error("[AI] 流式执行异常 conversationId={}", conversationId, e);
                emitter.completeWithError(e);
            }
        });
    }

    // ================= 私有 =================

    private FaqSearchTool newFaqTool() {
        return new FaqSearchTool(embeddingModel, embeddingStore, rewriteChatModel,
                ragTopK, ragMinCoverage, ragRewriteEnabled, ragRewriteCount);
    }

    /** 没有命中任何 FAQ 时存 null 而不是 "[]"，语义更清楚：这条回复没有依据 */
    private String sourcesJson(FaqSearchTool faqTool) {
        List<FaqSource> sources = faqTool.sources();
        if (sources.isEmpty()) {
            return null;
        }
        return JSONUtil.toJsonStr(sources.stream().map(FaqSource::title).toList());
    }

    private ChatConversation mustOwn(Long conversationId, Long userId) {
        ChatConversation conv = chatConversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUserId().equals(userId)) {
            throw new BusinessException("会话不存在");
        }
        return conv;
    }

    private void saveMessage(Long conversationId, int role, String content, String sources) {
        ChatMessage msg = new ChatMessage();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setSources(sources);
        chatMessageMapper.insert(msg);
    }

    private void ensureTitle(Long conversationId, String message) {
        String title = StrUtil.isBlank(message) ? "" : message.trim();
        if (title.length() > 30) {
            title = title.substring(0, 30);
        }
        ChatConversation conv = new ChatConversation();
        conv.setId(conversationId);
        conv.setTitle(title);
        chatConversationMapper.updateById(conv);
    }

    private void emit(SseEmitter emitter, String data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (IOException e) {
            // 客户端断开，忽略
        }
    }
}
