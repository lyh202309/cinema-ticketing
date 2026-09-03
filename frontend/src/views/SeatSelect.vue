<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getSeatMap, lockSeats } from '../api/seckill'
import { getSession } from '../api/session'
import SeatMap from '../components/SeatMap.vue'

const route = useRoute()
const router = useRouter()
const sessionId = route.params.id

const detail = ref(null)
const seatMap = ref(null)
const forbidden = ref(false)
const selected = ref([]) // 受控：唯一真源，父级持有
const loading = ref(true)
const submitting = ref(false)
const MAX = 5

// ===== SSE 实时订阅（同场次锁座/释放事件推送）=====
let alive = false // 订阅开关（卸载/资格过期即关）
let reconnectTimer = null

function stopSubscribe() {
  alive = false
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

async function fetchSeatMap() {
  return getSeatMap(sessionId)
}

/** 把某个座位在本地矩阵置为 0 可售 / 1 占用 */
function applySeatNo(seatNo, val) {
  const [r, c] = String(seatNo).split('-').map(Number)
  const seats = seatMap.value?.seats
  if (seats && seats[r - 1] && c >= 1) {
    seats[r - 1][c - 1] = val
  }
}

/** 收到 SSE 事件：{action:'lock'|'release', seats:["5-8",...]} */
function handleSeatEvent(data) {
  let ev
  try {
    ev = JSON.parse(data)
  } catch {
    return
  }
  const seats = ev?.seats
  if (!Array.isArray(seats) || !seats.length) return
  if (ev.action === 'lock') {
    // 抢占方刚锁的座变灰；若正被自己选中 → 自动取消选择并提示
    const conflict = []
    for (const no of seats) {
      applySeatNo(no, 1)
      if (selected.value.some((s) => `${s.row}-${s.col}` === no)) conflict.push(no)
    }
    if (conflict.length) {
      selected.value = selected.value.filter((s) => !conflict.includes(`${s.row}-${s.col}`))
      ElMessage.warning(`座位 ${conflict.join('、')} 刚被他人锁定，已为你取消选择`)
    }
  } else if (ev.action === 'release') {
    for (const no of seats) applySeatNo(no, 0)
  }
}

/** 建立 SSE 长连接；断开后自动重连（重连前先拉一次全量对齐，避免漏事件） */
async function subscribeSeats() {
  if (!alive) return
  let resp
  try {
    resp = await fetch(`/seckill/seatmap/${sessionId}/subscribe`, {
      headers: { authorization: localStorage.getItem('token') },
    })
  } catch {
    scheduleReconnect()
    return
  }
  if (resp.status === 403) {
    // 热门场资格过期/越权：停止订阅，回到抢票空态
    stopSubscribe()
    ElMessage.warning('抢票资格已过期，请重新抢票')
    selected.value = []
    forbidden.value = true
    return
  }
  if (!resp.ok || !resp.body) {
    scheduleReconnect()
    return
  }
  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buf = ''
  const flush = () => {
    while (true) {
      const i = buf.indexOf('\n\n')
      if (i < 0) break
      const raw = buf.slice(0, i)
      buf = buf.slice(i + 2)
      const line = raw
        .split('\n')
        .filter((l) => l.startsWith('data:'))
        .map((l) => l.slice(5)).join('\n')
      if (line) handleSeatEvent(line)
    }
  }
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
      flush()
    }
  } catch {
    /* 连接异常 → 走重连 */
  }
  scheduleReconnect()
}

function scheduleReconnect() {
  if (!alive) return
  reconnectTimer = setTimeout(async () => {
    if (!alive) return
    // 重连前重拉全量对齐
    try {
      seatMap.value = await fetchSeatMap()
    } catch (e) {
      if (e?.response?.status === 403) {
        stopSubscribe()
        forbidden.value = true
        selected.value = []
        return
      }
    }
    subscribeSeats()
  }, 1500)
}

// ===== 数据加载与交互 =====

async function load() {
  loading.value = true
  try {
    detail.value = await getSession(sessionId)
    try {
      seatMap.value = await fetchSeatMap()
    } catch (e) {
      // 403：热门场次未抢票直闯（拦截器已提示"非法操作"）
      if (e?.response?.status === 403) {
        forbidden.value = true
      } else {
        throw e
      }
    }
  } finally {
    loading.value = false
  }
}

function toggle({ row, col }) {
  const i = selected.value.findIndex((s) => s.row === row && s.col === col)
  if (i >= 0) {
    selected.value.splice(i, 1)
    return
  }
  if (selected.value.length >= MAX) {
    ElMessage.warning(`单笔最多选 ${MAX} 个座位`)
    return
  }
  selected.value.push({ row, col })
}

const totalPrice = computed(() => {
  const p = detail.value?.session?.price
  return p ? (Number(p) * selected.value.length).toFixed(2) : '0.00'
})

async function submitLock() {
  if (!selected.value.length) return ElMessage.warning('请先选择座位')
  submitting.value = true
  try {
    const result = await lockSeats({ sessionId, seats: selected.value })
    stopSubscribe()
    ElMessage.success('锁座成功，请在 5 分钟内完成支付')
    router.push(`/pay/${result.orderId}`)
  } catch (e) {
    // 座位被抢(推送间隙) / 一人一单：刷新座位图，已选清空重选
    if (e?.message?.includes('座位') || e?.message?.includes('重新选座')) {
      ElMessage.error('所选座位有变化，请重新选座')
      selected.value = []
      await load()
    }
  } finally {
    submitting.value = false
  }
}

function back() {
  router.push(`/session/${sessionId}`)
}

onMounted(async () => {
  await load()
  if (!forbidden.value) {
    alive = true
    subscribeSeats()
  }
})

onBeforeUnmount(stopSubscribe)
</script>

<template>
  <div v-loading="loading">
    <!-- 热门未抢票/资格过期 → 403 空态 -->
    <el-empty v-if="forbidden" description="热门场次需先抢票才能选座">
      <el-button type="primary" @click="back">去抢票</el-button>
    </el-empty>

    <template v-else-if="detail && seatMap">
      <div class="bar">
        <div>
          <div class="title">{{ detail.movie?.title }}
            <el-tag v-if="detail.session?.isHot" type="danger" size="small">热门</el-tag>
          </div>
          <div class="meta">{{ detail.cinema?.name }} · {{ detail.hall?.name }} · {{ String(detail.session?.startTime).replace('T', ' ').slice(0, 16) }}</div>
        </div>
        <div class="price">¥{{ detail.session?.price }}<span class="each">/座</span></div>
      </div>

      <SeatMap
        :rows="seatMap.rows" :cols="seatMap.cols" :seats="seatMap.seats"
        :selected="selected" @toggle="toggle"
      />
      <div class="sync-tip"><span class="live-dot" />座位状态实时同步中（他人锁座/释放即时反映）</div>

      <div class="foot">
        <div class="left">已选 <b>{{ selected.length }}</b>/{{ MAX }} 座（最多 {{ MAX }} 个）</div>
        <div class="right">
          <span class="total">合计 ¥{{ totalPrice }}</span>
          <el-button type="primary" size="large" :loading="submitting"
                     :disabled="!selected.length" @click="submitLock">
            确认锁座并去支付
          </el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.bar { display: flex; justify-content: space-between; align-items: center;
  background: var(--app-card); border-radius: 10px; padding: 16px 20px;
  margin-bottom: 14px; box-shadow: var(--app-shadow); }
.title { font-size: 18px; font-weight: 700; display: flex; align-items: center; gap: 8px; }
.meta { color: var(--app-muted); font-size: 13px; margin-top: 6px; }
.price { color: var(--el-color-danger); font-weight: 800; font-size: 24px; }
.each { font-size: 12px; color: var(--app-muted); font-weight: 400; }
.sync-tip { text-align: center; color: var(--app-muted); font-size: 12px; margin-top: 10px; }
.live-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%;
  background: var(--el-color-success); margin-right: 5px; vertical-align: 0; animation: pulse 1.6s infinite; }
@keyframes pulse { 50% { opacity: .3; } }
.foot { display: flex; justify-content: space-between; align-items: center;
  background: var(--app-card); border-radius: 10px; padding: 14px 20px;
  margin-top: 14px; box-shadow: var(--app-shadow); position: sticky; bottom: 10px; }
.total { color: var(--el-color-danger); font-weight: 700; font-size: 20px; margin-right: 16px; }
.right { display: flex; align-items: center; }
</style>
