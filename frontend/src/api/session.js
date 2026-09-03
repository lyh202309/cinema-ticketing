import request from '../utils/request'

// 场次查询：movieId / cinemaId / date 可组合
export const listSessions = (params) => request.get('/session', { params })
// 场次详情
export const getSession = (id) => request.get(`/session/${id}`)
