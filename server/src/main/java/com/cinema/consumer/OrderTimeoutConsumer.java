package com.cinema.consumer;

import com.cinema.config.RabbitMQConfig;
import com.cinema.service.IOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 订单超时释放消费者
 * 收到的是下单时发的延迟消息（5 分钟后经 DLX 到达死信队列）：
 * 查订单仍待支付 → 乐观锁取消 + 释放座位（cancelByTimeout 内部保证只取消待支付）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutConsumer {

    private final IOrderService orderService;

    @RabbitListener(queues = RabbitMQConfig.ORDER_CANCEL_QUEUE)
    public void onOrderTimeout(String orderIdStr) {
        try {
            Long orderId = Long.valueOf(orderIdStr);
            log.info("[订单超时] 检查订单 {}", orderId);
            orderService.cancelByTimeout(orderId);
        } catch (Exception e) {
            // 消费失败不中断；已支付/已取消的订单在乐观锁处被正确跳过
            log.error("[订单超时] 处理异常 orderId={}", orderIdStr, e);
        }
    }
}
