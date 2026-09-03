package com.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 锁座成功返回
 */
@Data
@AllArgsConstructor
public class LockResultVO {

    private Long orderId;

    /** 订单支付剩余时间（秒），= 5min */
    private Integer expireSeconds;
}
