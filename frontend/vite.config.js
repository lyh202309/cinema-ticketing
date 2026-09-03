import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 前端开发服务器：把后端接口代理到 Boot(8081)，实现同源联调
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/user':    { target: 'http://localhost:8081', changeOrigin: true },
      '/movie':   { target: 'http://localhost:8081', changeOrigin: true },
      '/cinema':  { target: 'http://localhost:8081', changeOrigin: true },
      '/session': { target: 'http://localhost:8081', changeOrigin: true },
      '/seckill': { target: 'http://localhost:8081', changeOrigin: true },
      '/order':   { target: 'http://localhost:8081', changeOrigin: true },
      '/chat':    { target: 'http://localhost:8081', changeOrigin: true },
    }
  }
})
