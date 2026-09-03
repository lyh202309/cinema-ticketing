<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMovie } from '../api/movie'
import { listSessions } from '../api/session'

const route = useRoute()
const router = useRouter()
const movie = ref(null)
const sessions = ref([])
const loading = ref(true)

async function load() {
  try {
    movie.value = await getMovie(route.params.id)
    const page = await listSessions({ movieId: route.params.id, page: 1, size: 30 })
    sessions.value = page?.records || []
  } finally {
    loading.value = false
  }
}

function fmt(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

function onPosterErr(e) {
  e.target.classList.add('err')
}

// 购票入口：统一进场次购票页（热门抢票 / 普通选座）
function onBuy(s) {
  router.push(`/session/${s.id}`)
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <template v-if="movie">
      <div class="movie-head">
        <div class="poster-big">
          <span>{{ movie.title.slice(0, 1) }}</span>
          <img :src="movie.poster" alt="" @error="onPosterErr" />
        </div>
        <div class="info">
          <h1>{{ movie.title }}</h1>
          <div class="tags">
            <el-tag size="small">{{ movie.genre }}</el-tag>
            <el-tag size="small" type="info">{{ movie.duration }}分钟</el-tag>
            <span class="rating">⭐ {{ movie.rating ?? '-' }}</span>
          </div>
          <div class="meta">导演：{{ movie.director }}</div>
          <div class="meta">上映：{{ movie.releaseDate }}</div>
          <p class="desc">{{ movie.description }}</p>
        </div>
      </div>

      <h2 class="sec-title">选择场次</h2>
      <div v-if="sessions.length" class="session-list">
        <div v-for="s in sessions" :key="s.id" class="session-card">
          <div class="sc-main">
            <div class="cinema link" @click.stop="router.push(`/cinema/${s.cinemaId}`)">{{ s.cinemaName }}</div>
            <div class="time">{{ fmt(s.startTime) }}</div>
            <div class="hall muted">{{ s.hallName }} · 余座充足</div>
          </div>
          <div class="sc-side">
            <el-tag v-if="s.isHot" type="danger" size="small">热门需抢票</el-tag>
            <el-tag v-else size="small" type="success">直接购票</el-tag>
            <div class="price">¥{{ s.price }}</div>
            <el-button size="small" type="primary" @click="onBuy(s)">购票</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else description="该电影暂无排片" />
    </template>
  </div>
</template>

<style scoped>
.movie-head { display: flex; gap: 20px; background: var(--app-card); border-radius: 10px;
  padding: 20px; box-shadow: var(--app-shadow); }
.poster-big { position: relative; overflow: hidden; width: 140px; height: 200px; border-radius: 8px; flex-shrink: 0;
  background: linear-gradient(135deg, var(--el-color-primary-light-5), var(--el-color-primary));
  display: flex; align-items: center; justify-content: center; font-size: 60px; color: #fff; }
.poster-big img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.poster-big img.err { display: none; }
.info h1 { font-size: 22px; margin-bottom: 10px; }
.tags { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.rating { color: #f5a623; font-weight: 600; }
.meta { color: var(--app-muted); font-size: 14px; margin: 4px 0; }
.desc { margin-top: 10px; color: var(--app-muted); font-size: 13px; line-height: 1.7; }
.sec-title { margin: 24px 0 12px; font-size: 18px; }
.session-list { display: flex; flex-direction: column; gap: 10px; }
.session-card { display: flex; justify-content: space-between; align-items: center;
  background: var(--app-card); border-radius: 8px; padding: 14px 18px; box-shadow: var(--app-shadow); }
.cinema { font-weight: 600; margin-bottom: 6px; }
.cinema.link { cursor: pointer; width: fit-content; }
.cinema.link:hover { color: var(--el-color-primary); }
.time { font-size: 15px; }
.muted { color: var(--app-muted); font-size: 12px; margin-top: 4px; }
.sc-side { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
.price { color: var(--el-color-danger); font-weight: 700; font-size: 18px; }
</style>
