import request from '../utils/request'

// 我的订单（status 可选：0待支付/1已支付/2已取消/3已退款）
export const listOrders = (params) => request.get('/order/my', { params })
// 订单详情（含座位）
export const getOrder = (id) => request.get(`/order/${id}`)
export const payOrder = (id) => request.post(`/order/${id}/pay`)
export const cancelOrder = (id) => request.post(`/order/${id}/cancel`)
export const refundOrder = (id) => request.post(`/order/${id}/refund`)
