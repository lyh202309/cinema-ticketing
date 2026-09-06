package com.cinema.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cinema.common.BusinessException;
import com.cinema.dto.OrderDetailVO;
import com.cinema.event.SeatEventHub;
import com.cinema.dto.OrderListItemVO;
import com.cinema.dto.PageResult;
import com.cinema.entity.Movie;
import com.cinema.entity.Order;
import com.cinema.entity.OrderSeat;
import com.cinema.entity.Session;
import com.cinema.mapper.MovieMapper;
import com.cinema.mapper.OrderMapper;
import com.cinema.mapper.OrderSeatMapper;
import com.cinema.mapper.SessionMapper;
import com.cinema.service.IOrderService;
import com.cinema.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单服务
 * 状态机（全部靠乐观锁 WHERE id + user_id + 原状态）：
 *   待支付(0) --支付--> 已支付(1) --退票--> 已退款(3)
 *   待支付(0) --取消/超时--> 已取消(2)
 * 座位释放统一走 releaseSeats.lua（归属校验），释放后清一人一单标记
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PAID = 1;
    private static final int STATUS_CANCELLED = 2;
    private static final int STATUS_REFUNDED = 3;

    private final OrderSeatMapper orderSeatMapper;
    private final SessionMapper sessionMapper;
    private final MovieMapper movieMapper;
    private final SeatEventHub seatEventHub;
    private final StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>();

    static {
        RELEASE_SCRIPT.setLocation(new ClassPathResource("lua/releaseSeats.lua"));
        RELEASE_SCRIPT.setResultType(Long.class);
    }

    // ================= 查询 =================

    @Override
    public PageResult<OrderListItemVO> myOrders(Long userId, Integer status, Integer page, Integer size) {
        Page<Order> p = page(new Page<>(page, size),
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .eq(status != null, Order::getStatus, status)
                        .orderByDesc(Order::getCreateTime));

        List<Order> orders = p.getRecords();
        List<OrderListItemVO> vos = orders.isEmpty() ? List.of() : assembleItems(orders);

        PageResult<OrderListItemVO> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setRecords(vos);
        return result;
    }

    @Override
    public OrderDetailVO getDetail(Long orderId, Long userId) {
        Order order = getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrder(order);
        vo.setSeats(orderSeatMapper.selectList(new LambdaQueryWrapper<OrderSeat>()
                .eq(OrderSeat::getOrderId, orderId)));
        return vo;
    }

    // ================= 支付 =================

    @Override
    public void pay(Long orderId, Long userId) {
        Order order = mustOwn(orderId, userId);
        // 乐观锁：仅待支付能支付（模拟支付，点击即成功）
        boolean ok = update(new LambdaUpdateWrapper<Order>()
                .set(Order::getStatus, STATUS_PAID)
                .set(Order::getPayTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, STATUS_PENDING));
        if (!ok) {
            throw new BusinessException("订单已失效，无法支付");
        }
        // 支付成功：资格 ZREM 腾名额（热门场次；普通场次无成员则 no-op）
        stringRedisTemplate.opsForZSet().remove(
                RedisConstants.QUALIFY_POOL_KEY + order.getSessionId(), String.valueOf(userId));
    }

    // ================= 主动取消 =================

    @Override
    public void cancel(Long orderId, Long userId) {
        Order order = mustOwn(orderId, userId);
        cancelCore(order, userId);
    }

    // ================= 系统超时取消（延迟队列消费者）=================

    @Override
    public void cancelByTimeout(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            return;
        }
        // 系统取消不带 user_id 条件，但必须仍待支付
        boolean ok = update(new LambdaUpdateWrapper<Order>()
                .set(Order::getStatus, STATUS_CANCELLED)
                .set(Order::getCancelTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, STATUS_PENDING));
        if (!ok) {
            // 已被支付/取消 → 无需释放
            return;
        }
        releaseOrderSeats(order);
    }

    /** 主动取消核心：乐观锁(带 userId) + 释放 */
    private void cancelCore(Order order, Long userId) {
        boolean ok = update(new LambdaUpdateWrapper<Order>()
                .set(Order::getStatus, STATUS_CANCELLED)
                .set(Order::getCancelTime, LocalDateTime.now())
                .eq(Order::getId, order.getId())
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, STATUS_PENDING));
        if (!ok) {
            throw new BusinessException("订单已失效，无法取消");
        }
        releaseOrderSeats(order);
    }

    // ================= 退票 =================

    @Override
    public void refund(Long orderId, Long userId) {
        Order order = mustOwn(orderId, userId);
        // 乐观锁：仅已支付可退（整单退）
        boolean ok = update(new LambdaUpdateWrapper<Order>()
                .set(Order::getStatus, STATUS_REFUNDED)
                .set(Order::getRefundTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, STATUS_PAID));
        if (!ok) {
            throw new BusinessException("订单不可退票");
        }
        releaseOrderSeats(order);
    }

    // ================= 私有 =================

    /** 校验订单属于当前用户并返回 */
    private Order mustOwn(Long orderId, Long userId) {
        Order order = getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        return order;
    }

    /** 释放该订单的 Redis 座位（归属校验）+ 清一人一单标记 */
    private void releaseOrderSeats(Order order) {
        List<OrderSeat> seatList = orderSeatMapper.selectList(
                new LambdaQueryWrapper<OrderSeat>().eq(OrderSeat::getOrderId, order.getId()));
        List<String> seatNos = new ArrayList<>();
        for (OrderSeat os : seatList) {
            seatNos.add(os.getSeatRow() + "-" + os.getSeatCol());
        }
        if (!seatNos.isEmpty()) {
            List<String> keys = List.of(RedisConstants.SEATS_KEY + order.getSessionId());
            List<String> args = new ArrayList<>();
            args.add(String.valueOf(order.getUserId()));
            args.addAll(seatNos);
            stringRedisTemplate.execute(RELEASE_SCRIPT, keys, args.toArray());
        }
        // 实时广播：该场次座位被释放 → 其它订阅者即时恢复可售（操作者自己跳过）
        if (!seatNos.isEmpty()) {
            seatEventHub.publish(order.getSessionId(), order.getUserId(),
                    "{\"action\":\"release\",\"seats\":" + JSONUtil.toJsonStr(seatNos) + "}");
        }
        // 清一人一单标记：允许重新下单/重新抢
        stringRedisTemplate.delete(
                RedisConstants.USER_ORDER_KEY + order.getSessionId() + ":" + order.getUserId());
    }

    /** 我的订单列表组装：关联场次/电影 + 座位数 */
    private List<OrderListItemVO> assembleItems(List<Order> orders) {
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        List<Long> sessionIds = orders.stream().map(Order::getSessionId).distinct().toList();

        // 座位数按订单聚合
        Map<Long, Long> seatCountMap = new HashMap<>();
        if (!orderIds.isEmpty()) {
            orderSeatMapper.selectList(new LambdaQueryWrapper<OrderSeat>()
                            .in(OrderSeat::getOrderId, orderIds))
                    .forEach(os -> seatCountMap.merge(os.getOrderId(), 1L, Long::sum));
        }
        // 场次 + 电影
        Map<Long, Session> sessionMap = sessionIds.isEmpty() ? Map.of()
                : sessionMapper.selectBatchIds(sessionIds).stream()
                .collect(Collectors.toMap(Session::getId, Function.identity(), (a, b) -> a));
        List<Long> movieIds = sessionMap.values().stream().map(Session::getMovieId).distinct().toList();
        Map<Long, Movie> movieMap = movieIds.isEmpty() ? Map.of()
                : movieMapper.selectBatchIds(movieIds).stream()
                .collect(Collectors.toMap(Movie::getId, Function.identity(), (a, b) -> a));

        return orders.stream().map(o -> {
            OrderListItemVO vo = new OrderListItemVO();
            vo.setOrderId(o.getId());
            vo.setOrderNo(o.getOrderNo());
            vo.setStatus(o.getStatus());
            vo.setTotalPrice(o.getTotalPrice());
            vo.setCreateTime(o.getCreateTime());
            vo.setSessionId(o.getSessionId());
            vo.setSeatCount(seatCountMap.getOrDefault(o.getId(), 0L).intValue());
            Session s = sessionMap.get(o.getSessionId());
            if (s != null) {
                vo.setMovieId(s.getMovieId());
                vo.setStartTime(s.getStartTime());
                Movie m = movieMap.get(s.getMovieId());
                if (m != null) {
                    vo.setMovieTitle(m.getTitle());
                }
            }
            return vo;
        }).toList();
    }
}
