package com.cinema.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 延迟队列（TTL + 死信交换机 DLX）：
 *
 * 下单 → 发消息到 order.delay.queue（躺 ORDER_TIMEOUT_MS，默认 5min）→ 过期
 *     → 转发到死信 order.cancel.exchange → order.cancel.queue
 *     → 消费者（订单模块）收到 → 查订单仍待支付 → 释放座位 + 取消订单
 */
@EnableRabbit
@Configuration
public class RabbitMQConfig {

    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_CANCEL_EXCHANGE = "order.cancel.exchange";
    public static final String ORDER_CANCEL_QUEUE = "order.cancel.queue";
    public static final String ORDER_CANCEL_ROUTING = "order.cancel";

    /** 订单超时时长：5 分钟 */
    public static final long ORDER_TIMEOUT_MS = 300_000;

    /** 延迟队列：消息 5 分钟过期进死信 */
    @Bean
    public Queue orderDelayQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", ORDER_TIMEOUT_MS);
        args.put("x-dead-letter-exchange", ORDER_CANCEL_EXCHANGE);
        args.put("x-dead-letter-routing-key", ORDER_CANCEL_ROUTING);
        return new Queue(ORDER_DELAY_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue orderCancelQueue() {
        return new Queue(ORDER_CANCEL_QUEUE, true);
    }

    @Bean
    public DirectExchange orderCancelExchange() {
        return new DirectExchange(ORDER_CANCEL_EXCHANGE);
    }

    @Bean
    public Binding cancelBinding() {
        return BindingBuilder.bind(orderCancelQueue())
                .to(orderCancelExchange())
                .with(ORDER_CANCEL_ROUTING);
    }
}
