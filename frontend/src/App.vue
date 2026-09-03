<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from './stores/user'
import { useThemeStore } from './stores/theme'
import { logout as apiLogout } from './api/user'
import { listMovies } from './api/movie'
import { ElMessage } from 'element-plus'
import ThemeSwitcher from './components/ThemeSwitcher.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const themeStore = useThemeStore()

const kw = ref('')
let movieCache = null

async function onSearch() {
  const q = kw.value.trim()
  if (!q) return
  if (!movieCache) {
    try {
      const p = await listMovies({ page: 1, size: 200 })
      movieCache = p?.records || []
    } catch {
      movieCache = []
    }
  }
  const hit = movieCache.find((m) => m.title.includes(q))
  if (hit) {
    kw.value = ''
    router.push(`/movie/${hit.id}`)
  } else {
    ElMessage.warning('未找到相关电影')
  }
}

onMounted(() => themeStore.init())

async function onLogout() {
  try { await apiLogout() } catch (e) { /* ignore */ }
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <div class="app">
    <header class="topbar" v-if="route.path !== '/login'">
      <div class="left">
        <div class="logo" @click="router.push('/')">🎬 影院订票</div>
        <nav class="nav">
          <span :class="{ on: route.path === '/' || route.path.startsWith('/movie') }"
                @click="router.push('/')">电影</span>
          <span :class="{ on: route.path.startsWith('/cinema') }"
                @click="router.push('/cinemas')">影院</span>
          <span :class="{ on: route.path.startsWith('/chat') }"
                @click="router.push('/chat')">AI 助手</span>
        </nav>
        <div class="search">
          <el-input v-model="kw" size="small" placeholder="搜电影" clearable @keyup.enter="onSearch">
            <template #prefix><span style="font-size:13px">🔍</span></template>
          </el-input>
        </div>
      </div>
      <div class="right">
        <ThemeSwitcher />
        <template v-if="userStore.token">
          <span class="nick">👤 {{ userStore.user?.nickName || '用户' }}</span>
          <el-button link type="primary" @click="router.push('/orders')">我的订单</el-button>
          <el-button size="small" @click="onLogout">退出</el-button>
        </template>
        <el-button v-else size="small" type="primary" @click="router.push('/login')">登录</el-button>
      </div>
    </header>
    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
:root {
  --app-bg: #f5f6fa;
  --app-card: #ffffff;
  --app-text: #333;
  --app-muted: #888;
  --app-shadow: 0 1px 4px rgba(0, 0, 0, .08);
}
html.dark {
  --app-bg: #15161a;
  --app-card: #1f2128;
  --app-text: #e6e6e6;
  --app-muted: #9a9a9a;
  --app-shadow: 0 1px 4px rgba(0, 0, 0, .5);
}
body {
  font-family: system-ui, -apple-system, 'Segoe UI', sans-serif;
  background: var(--app-bg);
  color: var(--app-text);
  transition: background .2s, color .2s;
}
.app { min-height: 100vh; }
.topbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 24px; height: 56px; background: var(--app-card);
  box-shadow: var(--app-shadow);
}
.left { display: flex; align-items: center; gap: 24px; }
.logo { font-size: 18px; font-weight: 700; cursor: pointer; color: var(--el-color-primary); }
.nav { display: flex; gap: 16px; }
.nav span { cursor: pointer; color: var(--app-muted); font-size: 15px; padding: 4px 2px; }
.nav span:hover { color: var(--el-color-primary); }
.nav span.on { color: var(--el-color-primary); font-weight: 600; border-bottom: 2px solid var(--el-color-primary); }
.search { width: 170px; margin-left: 6px; }
.right { display: flex; align-items: center; gap: 12px; }
.nick { font-size: 14px; color: var(--app-muted); }
.main { max-width: 1200px; margin: 20px auto; padding: 0 16px; }
</style>
