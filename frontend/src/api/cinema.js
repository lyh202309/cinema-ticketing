import request from '../utils/request'

export const listCinemas = (params) => request.get('/cinema', { params })
export const getCinema = (id) => request.get(`/cinema/${id}`)
