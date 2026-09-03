package com.cinema.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单（DB 真源）
 */
@Data
@TableName("tb_order")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 下单用户 */
    private Long userId;

    /** 场次 */
    private Long sessionId;

    /** 总价 */
    private BigDecimal totalPrice;

    /** 0待支付/1已支付/2已取消/3已退款 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime payTime;

    private LocalDateTime cancelTime;

    private LocalDateTime refundTime;
}
