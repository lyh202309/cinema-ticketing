import request from '../utils/request'

// 抢资格（热门场次）: data=0 抢到
export const qualify = (sessionId) => request.post(`/seckill/qualify/${sessionId}`)
// 座位图: {rows, cols, seats[][]}
export const getSeatMap = (sessionId) => request.get(`/seckill/seatmap/${sessionId}`)
// 锁座+下单
export const lockSeats = (data) => request.post('/seckill/lock', data)
