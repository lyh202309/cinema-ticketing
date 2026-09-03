package com.cinema.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我的订单列表项（平铺场次/电影概要）
 */
@Data
public class OrderListItemVO {

    private Long orderId;
    private String orderNo;
    /** 0待支付/1已支付/2已取消/3已退款 */
    private Integer status;
    private BigDecimal totalPrice;
    private LocalDateTime createTime;

    private Long sessionId;
    private Long movieId;
    private String movieTitle;
    private LocalDateTime startTime;
    private Integer seatCount;
}
