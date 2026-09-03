import request from '../utils/request'

export const sendCode = (phone) => request.post('/user/code', null, { params: { phone } })
export const login = (data) => request.post('/user/login', data)
export const logout = () => request.post('/user/logout')
export const getMe = () => request.get('/user/me')
