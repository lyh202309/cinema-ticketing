<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listCinemas } from '../api/cinema'

const router = useRouter()
const cinemas = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const page = await listCinemas({ page: 1, size: 20 })
    cinemas.value = page?.records || []
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <div>
    <h2>影院</h2>
    <div v-loading="loading" class="cinema-grid">
      <div v-for="c in cinemas" :key="c.id" class="cinema-card" @click="router.push(`/cinema/${c.id}`)">
        <div class="name">{{ c.name }}</div>
        <div class="rating">⭐ {{ c.rating ?? '-' }}</div>
        <div class="meta">{{ c.region }} · {{ c.address }}</div>
      </div>
    </div>
    <el-empty v-if="!loading && !cinemas.length" description="暂无影院" />
  </div>
</template>

<style scoped>
.cinema-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; }
.cinema-card { background: var(--app-card); border-radius: 10px; padding: 16px;
  box-shadow: var(--app-shadow); cursor: pointer; transition: transform .15s; }
.cinema-card:hover { transform: translateY(-3px); }
.name { font-weight: 700; font-size: 16px; }
.rating { color: #f5a623; font-size: 14px; margin: 6px 0; }
.meta { color: var(--app-muted); font-size: 13px; }
</style>
