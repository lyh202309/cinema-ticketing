package com.cinema.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单座位（Redis 座位状态的对账依据；不存状态，由 order.status 推导）
 */
@Data
@TableName("tb_order_seat")
public class OrderSeat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 场次（冗余，重建 Redis 用） */
    private Long sessionId;

    private Integer seatRow;

    private Integer seatCol;

    private LocalDateTime createTime;
}
