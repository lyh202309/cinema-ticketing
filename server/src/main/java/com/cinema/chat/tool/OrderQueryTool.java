package com.cinema.chat.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cinema.entity.Movie;
import com.cinema.entity.Order;
import com.cinema.entity.OrderSeat;
import com.cinema.entity.Session;
import com.cinema.mapper.MovieMapper;
import com.cinema.mapper.OrderMapper;
import com.cinema.mapper.OrderSeatMapper;
import com.cinema.mapper.SessionMapper;
import dev.langchain4j.agent.tool.Tool;

import java.util.List;

/**
 * AI 工具：查当前用户的订单（只读）
 * 绑定了 userId（每次请求构造），因此不依赖 ThreadLocal（流式在异步线程执行）
 */
public class OrderQueryTool {

    private final Long userId;
    private final OrderMapper orderMapper;
    private final SessionMapper sessionMapper;
    private final MovieMapper movieMapper;
    private final OrderSeatMapper orderSeatMapper;

    public OrderQueryTool(Long userId, OrderMapper orderMapper, SessionMapper sessionMapper,
                          MovieMapper movieMapper, OrderSeatMapper orderSeatMapper) {
        this.userId = userId;
        this.orderMapper = orderMapper;
        this.sessionMapper = sessionMapper;
        this.movieMapper = movieMapper;
        this.orderSeatMapper = orderSeatMapper;
    }

    @Tool("查询当前登录用户的订单列表（含片名/场次时间/座位数/状态/金额）")
    public String queryMyOrders() {
        if (userId == null) {
            return "需要先登录才能查订单。";
        }
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime)
                .last("LIMIT 10"));
        if (orders.isEmpty()) {
            return "你还没有订单。";
        }
        StringBuilder sb = new StringBuilder("你的订单：\n");
        for (Order o : orders) {
            Session s = sessionMapper.selectById(o.getSessionId());
            String movieTitle = "";
            if (s != null) {
                Movie m = movieMapper.selectById(s.getMovieId());
                movieTitle = m == null ? "" : m.getTitle();
            }
            Long seatCount = orderSeatMapper.selectCount(new LambdaQueryWrapper<OrderSeat>()
                    .eq(OrderSeat::getOrderId, o.getId()));
            sb.append("订单").append(o.getOrderNo()).append("《").append(movieTitle).append("》")
                    .append(seatCount).append("张 共").append(o.getTotalPrice()).append("元 ")
                    .append(statusText(o.getStatus()))
                    .append("\n");
        }
        return sb.toString();
    }

    private String statusText(Integer status) {
        return switch (status == null ? -1 : status) {
            case 0 -> "待支付";
            case 1 -> "已支付";
            case 2 -> "已取消";
            case 3 -> "已退款";
            default -> "未知";
        };
    }
}
