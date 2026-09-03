import request from '../utils/request'

// 电影列表（可按类型筛、分页）
export const listMovies = (params) => request.get('/movie', { params })
// 电影详情
export const getMovie = (id) => request.get(`/movie/${id}`)
