package com.cinema.mq;

import com.cinema.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 延迟消息发送：下单成功后给订单超时释放排队
 * 发送失败仅告警——不影响下单主流程（本地无 MQ 时降级，生产需保障 MQ 可用）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqDelaySender {

    private final RabbitTemplate rabbitTemplate;

    /** 下单后调用：消息在延迟队列躺 5 分钟，过期进死信触发超时检查 */
    public void sendOrderTimeout(Long orderId) {
        try {
            rabbitTemplate.convertAndSend("", RabbitMQConfig.ORDER_DELAY_QUEUE, String.valueOf(orderId));
        } catch (Exception e) {
            log.error("发送订单超时延迟消息失败, orderId={}", orderId, e);
        }
    }
}
