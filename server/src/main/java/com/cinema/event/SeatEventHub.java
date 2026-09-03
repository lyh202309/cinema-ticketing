package com.cinema.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 选座实时事件中心（SSE 广播，按场次隔离）
 * 单机内存注册表：Map<sessionId, Map<emitter, userId>>
 *
 * 订阅（GET /seckill/seatmap/{sessionId}/subscribe）后，同一场次任意用户
 * 锁座 / 释放座位成功时 publish 增量事件，正在看该场次选座页的其它订阅者即时收到：
 *   {action:"lock", seats:["5-8","5-9"]}
 *   {action:"release", seats:["5-8"]}
 *
 * 关键设计：
 * - 按 userId 记录订阅者，publish 时【不回推操作者本人】——避免"自己锁座提醒自己"；
 *   提示语只可能出现在真正因他人抢座而失去已选座位的人前端（无冲突的订阅者只静默更新 UI）。
 * - 广播失败（客户端断开 / 异步上下文已 error 等）一律吞掉并摘除该连接，
 *   SSE 只是旁路同步，绝不能影响锁座/取消/退款的业务主链路。
 * - 若未来多实例部署，把 publish 换成 Redis Pub/Sub 即可横向扩展。
 */
@Slf4j
@Component
public class SeatEventHub {

    /** 连接保活时长：超时由前端断线重连兜底 */
    private static final long EMITTER_TIMEOUT_MS = 0;

    /** sessionId → (emitter → userId) */
    private final Map<Long, Map<SseEmitter, Long>> subscribers = new ConcurrentHashMap<>();

    /** 订阅某场次：返回长连接；完成/超时/异常时自动摘除 */
    public SseEmitter subscribe(Long sessionId, Long userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        emitter.onError(e -> remove(sessionId, emitter));
        subscribers.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(emitter, userId);
        return emitter;
    }

    /**
     * 向某场次广播事件（JSON 字符串），跳过操作者本人的连接（不回推"回声"）
     *
     * @param fromUserId 本次锁座/释放的操作者；null 则不跳过（如系统触发）
     */
    public void publish(Long sessionId, Long fromUserId, String payload) {
        Map<SseEmitter, Long> set = subscribers.get(sessionId);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (Map.Entry<SseEmitter, Long> entry : set.entrySet()) {
            if (fromUserId != null && fromUserId.equals(entry.getValue())) {
                continue; // 操作者自己不需要被通知自己的操作结果
            }
            SseEmitter emitter = entry.getKey();
            try {
                emitter.send(SseEmitter.event().data(payload));
            } catch (Exception e) {
                // 客户端断开 / 异步上下文已 error：摘除，避免重复推送（send 会抛 IllegalStateException）
                set.remove(emitter);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignore) {
                    // 连接已终止，complete 也失败则直接丢弃
                }
            }
        }
    }

    /** 订阅者数（对账/观察用） */
    public int subscriberCount(Long sessionId) {
        Map<SseEmitter, Long> set = subscribers.get(sessionId);
        return set == null ? 0 : set.size();
    }

    private void remove(Long sessionId, SseEmitter emitter) {
        Map<SseEmitter, Long> set = subscribers.get(sessionId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                subscribers.remove(sessionId);
            }
        }
    }
}
