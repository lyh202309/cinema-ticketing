package com.cinema.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cinema.dto.OrderDetailVO;
import com.cinema.dto.OrderListItemVO;
import com.cinema.dto.PageResult;
import com.cinema.entity.Order;

/**
 * 订单服务：支付/取消/超时/退票/查询
 */
public interface IOrderService extends IService<Order> {

    /** 我的订单（按状态筛，分页） */
    PageResult<OrderListItemVO> myOrders(Long userId, Integer status, Integer page, Integer size);

    /** 订单详情（含座位，校验归属） */
    OrderDetailVO getDetail(Long orderId, Long userId);

    /** 支付：模拟 + DB 乐观锁（待支付→已支付）；成功后资格 ZREM */
    void pay(Long orderId, Long userId);

    /** 主动取消：仅待支付可取消，释放座位 + 清一人一单标记 */
    void cancel(Long orderId, Long userId);

    /** 系统超时取消（延迟队列消费者调用）：乐观锁(待支付→已取消) + 释放座位 */
    void cancelByTimeout(Long orderId);

    /** 退票：仅已支付可退（→已退款，整单退），释放座位 */
    void refund(Long orderId, Long userId);
}
