import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

// axios 封装：请求带 token；code=1 直接返回 data，其余弹 msg；401/403 特殊处理
const request = axios.create({ timeout: 30000 })

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['authorization'] = token
  }
  return config
})

request.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 1) {
        return body.data
      }
      ElMessage.error(body.msg || '操作失败')
      return Promise.reject(new Error(body.msg))
    }
    return body // SSE / 非 Result 透传
  },
  (err) => {
    const status = err.response?.status
    const msg = err.response?.data?.msg
    if (status === 401) {
      ElMessage.warning('请先登录')
      router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
    } else if (status === 403) {
      ElMessage.error(msg || '非法操作')
    } else {
      ElMessage.error(msg || '网络异常，请稍后重试')
    }
    return Promise.reject(err)
  },
)

export default request
