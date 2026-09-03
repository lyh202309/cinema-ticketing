<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrder, payOrder, cancelOrder } from '../api/order'
import { getSession } from '../api/session'

const route = useRoute()
const router = useRouter()
const orderId = route.params.orderId

const data = ref(null) // {order, seats}
const session = ref(null)
const loading = ref(true)
const paying = ref(false)
const remain = ref(0) // 支付剩余秒
let timer = null

const STATUS = { 0: ['待支付', 'warning'], 1: ['已支付', 'success'], 2: ['已取消', 'info'], 3: ['已退款', 'danger'] }

const order = computed(() => data.value?.order)

function fmtTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 19)
}

function startCountdown() {
  const end = new Date(String(order.value.createTime).replace('T', ' ')).getTime() + 5 * 60 * 1000
  const tick = () => {
    remain.value = Math.max(0, Math.round((end - Date.now()) / 1000))
    if (remain.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
      ElMessage.warning('订单可能已超时，请刷新查看状态')
    }
  }
  tick()
  timer = setInterval(tick, 1000)
}

const remainText = computed(() => {
  const m = Math.floor(remain.value / 60)
  const s = remain.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

async function load() {
  loading.value = true
  try {
    data.value = await getOrder(orderId)
    // 拉场次/影片用于展示（公开接口）
    session.value = await getSession(data.value.order.sessionId)
    if (data.value.order.status === 0) {
      startCountdown()
    }
  } finally {
    loading.value = false
  }
}

async function onPay() {
  paying.value = true
  try {
    await payOrder(orderId) // 模拟支付：乐观锁
    ElMessage.success('支付成功，出票啦！')
    router.push('/orders')
  } catch (e) { /* 拦截器已提示（订单失效等） */ } finally {
    paying.value = false
  }
}

async function onCancel() {
  try {
    await ElMessageBox.confirm('确定取消这笔订单吗？已锁座位将释放', '取消订单', { type: 'warning' })
  } catch { return }
  try {
    await cancelOrder(orderId)
    ElMessage.success('订单已取消')
    data.value = await getOrder(orderId) // 刷新状态
  } catch (e) { /* 拦截器提示 */ }
}

function seatText() {
  return (data.value?.seats || []).map((s) => `${s.seatRow}-${s.seatCol}`).join('、')
}

onMounted(load)
onUnmounted(() => timer && clearInterval(timer))
</script>

<template>
  <div v-loading="loading" class="pay-page">
    <template v-if="order">
      <!-- 状态卡 -->
      <div class="card status-card">
        <div v-if="order.status === 0" class="pending">
          <h2>待支付</h2>
          <div class="countdown" :class="{ danger: remain <= 60 }">
            剩余支付时间 <b>{{ remainText }}</b>
          </div>
        </div>
        <div v-else class="result">
          <span class="icon" :class="{ paid: order.status === 1 }">
            {{ order.status === 1 ? '✓' : order.status === 2 ? '✕' : '↩' }}
          </span>
          <div class="r-text">
            <h2>{{ STATUS[order.status][0] }}</h2>
            <p v-if="order.status === 2">已取消的订单座位已释放，可重新购买</p>
            <p v-else-if="order.status === 3">已退款，座位已释放</p>
          </div>
        </div>
      </div>

      <!-- 订单信息 -->
      <div class="card">
        <div class="line"><span class="k">电影</span>{{ session?.movie?.title }}</div>
        <div class="line"><span class="k">影院</span>{{ session?.cinema?.name }} · {{ session?.hall?.name }}</div>
        <div class="line"><span class="k">开场</span>{{ fmtTime(session?.session?.startTime) }}</div>
        <div class="line"><span class="k">座位</span>{{ seatText() }}</div>
        <div class="line"><span class="k">订单号</span>{{ order.orderNo }}</div>
        <div class="line total"><span class="k">金额</span><b>¥{{ order.totalPrice }}</b></div>
      </div>

      <!-- 操作 -->
      <div class="card actions" v-if="order.status === 0">
        <el-button size="large" :disabled="remain <= 0" :loading="paying" type="primary"
                   style="min-width: 200px" @click="onPay">立即支付（模拟）</el-button>
        <el-button size="large" @click="onCancel">取消订单</el-button>
      </div>
      <div class="card actions" v-else>
        <el-button size="large" type="primary" @click="router.push('/orders')">查看我的订单</el-button>
        <el-button v-if="order.status === 2" size="large"
                   @click="router.push(`/session/${order.sessionId}`)">重新购票</el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.pay-page { max-width: 560px; margin: 0 auto; display: flex; flex-direction: column; gap: 14px; }
.card { background: var(--app-card); border-radius: 10px; padding: 20px 24px; box-shadow: var(--app-shadow); }
.status-card { text-align: center; }
.countdown { font-size: 16px; color: var(--app-muted); margin-top: 8px; }
.countdown b { color: var(--el-color-primary); font-size: 22px; margin-left: 6px; }
.countdown.danger b { color: var(--el-color-danger); }
.result { display: flex; align-items: center; justify-content: center; gap: 16px; }
.icon { width: 48px; height: 48px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  font-size: 24px; color: #fff; background: var(--el-color-danger); }
.icon.paid { background: var(--el-color-success); }
.r-text { text-align: left; }
.r-text p { color: var(--app-muted); font-size: 13px; margin-top: 4px; }
.line { padding: 6px 0; border-bottom: 1px dashed var(--el-border-color-lighter); font-size: 15px; }
.line:last-child { border-bottom: none; }
.k { color: var(--app-muted); width: 60px; display: inline-block; }
.total b { color: var(--el-color-danger); font-size: 20px; }
.actions { display: flex; justify-content: center; gap: 12px; }
</style>
