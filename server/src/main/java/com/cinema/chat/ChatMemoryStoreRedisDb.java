package com.cinema.chat;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cinema.mapper.ChatMessageMapper;
import com.cinema.utils.RedisConstants;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * ChatMemoryStore：双存储
 * - DB（tb_chat_message）：权威永久存档，历史恢复的来源（只取用户/助手文本消息）
 * - Redis（chat:ctx:{conversationId}，30min）：活跃会话上下文缓存
 *
 * 说明：Redis 缓存必须【完整保真】每一轮消息——包括带工具调用的 AiMessage 与
 * ToolExecutionResultMessage。langchain4j 流式工具循环的每一轮都会基于 ChatMemory
 * 重建请求上下文，若这里把工具消息滤掉，模型看不到自己查过什么/结果是什么，
 * 会反复发起同一工具调用直到 maxToolCallingRoundTrips 兜底（100 次死循环）。
 */
@Component
@RequiredArgsConstructor
public class ChatMemoryStoreRedisDb implements ChatMemoryStore {

    private static final int WINDOW = 20;
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final ChatMessageMapper chatMessageMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String convId = memoryId.toString();
        String cacheKey = RedisConstants.CHAT_CTX_KEY + convId;
        // 1. 优先 Redis 活跃缓存（完整保真，含工具调用/结果消息）
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return decode(cached);
        }
        // 2. miss → DB 权威加载最近 N 条（asc，仅用户/助手文本历史）
        List<com.cinema.entity.ChatMessage> rows = chatMessageMapper.selectList(
                new LambdaQueryWrapper<com.cinema.entity.ChatMessage>()
                        .eq(com.cinema.entity.ChatMessage::getConversationId, convId)
                        .orderByDesc(com.cinema.entity.ChatMessage::getId)
                        .last("LIMIT " + WINDOW));
        java.util.Collections.reverse(rows);
        List<ChatMessage> result = new ArrayList<>();
        for (com.cinema.entity.ChatMessage row : rows) {
            if (row.getContent() == null || row.getContent().isEmpty()) {
                continue;
            }
            if (row.getRole() != null && row.getRole() == 0) {
                result.add(UserMessage.from(row.getContent()));
            } else {
                result.add(AiMessage.from(row.getContent()));
            }
        }
        // 写回 Redis 缓存（30min 活跃窗口）
        stringRedisTemplate.opsForValue().set(cacheKey, encode(result), CACHE_TTL);
        return result;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // 会话窗口变化 → 覆盖写 Redis 活跃缓存（DB 存档由 ChatService 每轮单独落库）
        String cacheKey = RedisConstants.CHAT_CTX_KEY + memoryId;
        stringRedisTemplate.opsForValue().set(cacheKey, encode(messages), CACHE_TTL);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        stringRedisTemplate.delete(RedisConstants.CHAT_CTX_KEY + memoryId);
    }

    // ===== 编解码：完整保真（用户/系统/AI含工具请求/工具结果）=====

    private String encode(List<ChatMessage> messages) {
        JSONArray list = new JSONArray();
        for (ChatMessage m : messages) {
            JSONObject node = new JSONObject();
            if (m instanceof UserMessage um) {
                node.set("type", "user").set("text", um.singleText());
            } else if (m instanceof SystemMessage sm) {
                node.set("type", "system").set("text", sm.text());
            } else if (m instanceof ToolExecutionResultMessage t) {
                node.set("type", "tool").set("id", t.id()).set("toolName", t.toolName()).set("text", t.text());
            } else if (m instanceof AiMessage am) {
                node.set("type", "ai").set("text", am.text());
                List<ToolExecutionRequest> calls = am.toolExecutionRequests();
                if (calls != null && !calls.isEmpty()) {
                    JSONArray arr = new JSONArray();
                    for (ToolExecutionRequest r : calls) {
                        arr.add(new JSONObject().set("id", r.id()).set("name", r.name()).set("args", r.arguments()));
                    }
                    node.set("toolCalls", arr);
                }
            } else {
                continue; // 其它类型(如底层请求消息)不持久化
            }
            list.add(node);
        }
        return list.toString();
    }

    @SuppressWarnings("unchecked")
    private List<ChatMessage> decode(String json) {
        List<ChatMessage> result = new ArrayList<>();
        JSONArray arr = JSONUtil.parseArray(json);
        for (Object item : arr) {
            JSONObject node = (JSONObject) item;
            String text = node.getStr("text");
            // 兼容历史缓存：无 type 字段时按旧格式 role 0/1 解释
            String type = node.getStr("type");
            if (type == null) {
                type = (node.getInt("role", 1) == 0) ? "user" : "ai";
            }
            switch (type) {
                case "user" -> result.add(UserMessage.from(text));
                case "system" -> result.add(SystemMessage.from(text));
                case "tool" -> result.add(ToolExecutionResultMessage.builder()
                        .id(node.getStr("id"))
                        .toolName(node.getStr("toolName"))
                        .text(text)
                        .build());
                default -> {
                    JSONArray calls = node.getJSONArray("toolCalls");
                    if (calls == null || calls.isEmpty()) {
                        result.add(AiMessage.builder().text(text).build());
                    } else {
                        List<ToolExecutionRequest> reqs = new ArrayList<>();
                        for (Object c : calls) {
                            JSONObject tc = (JSONObject) c;
                            reqs.add(ToolExecutionRequest.builder()
                                    .id(tc.getStr("id"))
                                    .name(tc.getStr("name"))
                                    .arguments(tc.getStr("args"))
                                    .build());
                        }
                        result.add(AiMessage.builder().text(text).toolExecutionRequests(reqs).build());
                    }
                }
            }
        }
        return result;
    }
}
