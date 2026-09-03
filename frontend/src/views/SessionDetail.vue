<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getSession } from '../api/session'
import { qualify } from '../api/seckill'

const route = useRoute()
const router = useRouter()
const detail = ref(null)
const loading = ref(true)
const grabbing = ref(false)

async function load() {
  try {
    detail.value = await getSession(route.params.id)
  } finally {
    loading.value = false
  }
}

const session = computed(() => detail.value?.session)
const isHot = computed(() => !!session.value?.isHot)

// 开售/截止状态（热门场次才有 sale 时间）
const now = () => Date.now()
const notStarted = computed(() => {
  const t = session.value?.saleStartTime
  return t && now() < new Date(String(t).replace(' ', 'T')).getTime()
})
const ended = computed(() => {
  const t = session.value?.saleEndTime
  return t && now() >= new Date(String(t).replace(' ', 'T')).getTime()
})

function fmt(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

function requireLogin() {
  if (!localStorage.getItem('token')) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return false
  }
  return true
}

// 购票：热门 → 抢资格；普通 → 直接选座
async function buy() {
  if (!requireLogin()) return
  const id = detail.value.session.id
  if (!isHot.value) {
    router.push(`/seckill/${id}`) // 选座页（下一步模块）
    return
  }
  grabbing.value = true
  try {
    await qualify(id) // code=1 data=0 → 抢到
    ElMessage.success('抢票成功，请在 3 分钟内选座')
    router.push(`/seckill/${id}`)
  } catch (e) {
    // "请勿重复操作" = 已有资格 → 直接放行进选座
    if (e.message === '请勿重复操作') {
      ElMessage.success('你已抢到，去选座吧')
      router.push(`/seckill/${id}`)
    }
    // 其余（人数过多/未开售/已截止）已由拦截器提示，停留在本页可稍后重试
  } finally {
    grabbing.value = false
  }
}

function onPosterErr(e) {
  e.target.classList.add('err')
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <template v-if="detail">
      <el-button link size="small" @click="router.back()">← 返回</el-button>
      <div class="big-card">
        <div class="poster">
          <span>{{ detail.movie?.title.slice(0, 1) }}</span>
          <img :src="detail.movie?.poster" alt="" @error="onPosterErr" />
        </div>
        <div class="info">
          <h1>{{ detail.movie?.title }}</h1>
          <div class="tag-row">
            <el-tag size="small">{{ detail.movie?.genre }}</el-tag>
            <el-tag v-if="isHot" type="danger" size="small">热门 · 需抢票</el-tag>
            <el-tag v-else type="success" size="small">可直接购票</el-tag>
          </div>
          <div class="rows">
            <div class="row"><span class="k">影院</span>{{ detail.cinema?.name }}</div>
            <div class="row"><span class="k">影厅</span>{{ detail.hall?.name }}（{{ detail.hall?.rowCount }}×{{ detail.hall?.colCount }}）</div>
            <div class="row"><span class="k">开场</span>{{ fmt(detail.session.startTime) }}</div>
            <div class="row"><span class="k">散场</span>{{ fmt(detail.session.endTime) }}</div>
            <div class="row"><span class="k">座位</span>{{ detail.seatsTotal }} 个</div>
          </div>
        </div>
        <div class="side">
          <div class="price">¥{{ detail.session.price }}</div>
          <div v-if="isHot">
            <el-button v-if="!ended" type="danger" size="large" :loading="grabbing"
                       :disabled="notStarted" @click="buy">
              {{ notStarted ? `未开售（${fmt(session.saleStartTime).slice(5, 16)}开抢）` : '立即抢票' }}
            </el-button>
            <el-button v-else type="info" size="large" disabled>已截止售票</el-button>
          </div>
          <el-button v-else type="primary" size="large" @click="buy">选座购票</el-button>
          <div class="tip">{{ isHot ? '热门场次需先抢票，抢到后 3 分钟内完成选座' : '直接选座，锁座后 5 分钟内支付' }}</div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.big-card { display: flex; gap: 20px; background: var(--app-card); border-radius: 12px;
  padding: 24px; margin-top: 12px; box-shadow: var(--app-shadow); align-items: flex-start; }
.poster { position: relative; overflow: hidden; width: 130px; height: 190px; border-radius: 8px; flex-shrink: 0;
  background: linear-gradient(135deg, var(--el-color-primary-light-5), var(--el-color-primary));
  display: flex; align-items: center; justify-content: center; font-size: 56px; color: #fff; }
.poster img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.poster img.err { display: none; }
.info { flex: 1; }
.tag-row { display: flex; gap: 8px; margin: 10px 0; }
.rows { display: flex; flex-direction: column; gap: 8px; margin-top: 8px; }
.row { font-size: 15px; }
.k { color: var(--app-muted); width: 44px; display: inline-block; }
.side { display: flex; flex-direction: column; align-items: center; gap: 14px; min-width: 200px; }
.price { color: var(--el-color-danger); font-weight: 800; font-size: 30px; }
.tip { color: var(--app-muted); font-size: 12px; text-align: center; }
</style>
