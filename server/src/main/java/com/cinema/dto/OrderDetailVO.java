package com.cinema.dto;

import com.cinema.entity.Order;
import com.cinema.entity.OrderSeat;
import lombok.Data;

import java.util.List;

/**
 * 订单详情（含所选座位）
 */
@Data
public class OrderDetailVO {

    private Order order;

    private List<OrderSeat> seats;
}
