<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCinema } from '../api/cinema'
import { listSessions } from '../api/session'
import SessionItem from '../components/SessionItem.vue'

const route = useRoute()
const router = useRouter()
const cinema = ref(null)
const halls = ref([])
const sessions = ref([])
const loading = ref(true)

async function load() {
  try {
    const detail = await getCinema(route.params.id)
    cinema.value = detail.cinema
    halls.value = detail.halls || []
    const page = await listSessions({ cinemaId: route.params.id, page: 1, size: 30 })
    sessions.value = page?.records || []
  } finally {
    loading.value = false
  }
}

function toSession(s) {
  router.push(`/session/${s.id}`)
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <template v-if="cinema">
      <div class="head">
        <div>
          <h1>{{ cinema.name }}</h1>
          <div class="meta">{{ cinema.region }} · {{ cinema.address }}</div>
          <div class="meta">☎ {{ cinema.phone }} · ⭐ {{ cinema.rating ?? '-' }}</div>
        </div>
      </div>

      <h2 class="sec-title">影厅</h2>
      <div class="halls">
        <div v-for="h in halls" :key="h.id" class="hall-card">
          <span class="h-name">{{ h.name }}</span>
          <span class="muted">{{ h.rowCount }} 排 × {{ h.colCount }} 列 · {{ h.rowCount * h.colCount }} 座</span>
        </div>
      </div>

      <h2 class="sec-title">本影院场次</h2>
      <div v-if="sessions.length" class="session-list">
        <SessionItem v-for="s in sessions" :key="s.id" :session="s" @buy="toSession" />
      </div>
      <el-empty v-else description="暂无排片" />
    </template>
  </div>
</template>

<style scoped>
.head { background: var(--app-card); border-radius: 10px; padding: 20px; box-shadow: var(--app-shadow); }
.meta { color: var(--app-muted); font-size: 14px; margin-top: 6px; }
.sec-title { margin: 22px 0 12px; font-size: 18px; }
.halls { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 8px; }
.hall-card { background: var(--app-card); padding: 12px 16px; border-radius: 8px;
  box-shadow: var(--app-shadow); display: flex; flex-direction: column; gap: 4px; }
.h-name { font-weight: 600; }
.muted { color: var(--app-muted); font-size: 12px; }
.session-list { display: flex; flex-direction: column; gap: 10px; }
</style>
