<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listOrders, getOrder, cancelOrder, refundOrder } from '../api/order'

const router = useRouter()
const tab = ref('')
const list = ref([])
const loading = ref(false)

const detailVisible = ref(false)
const detail = ref(null)

const STATUS = { 0: ['待支付', 'warning'], 1: ['已支付', 'success'], 2: ['已取消', 'info'], 3: ['已退款', 'danger'] }

async function load() {
  loading.value = true
  try {
    const page = await listOrders({ status: tab.value === '' ? undefined : Number(tab.value), page: 1, size: 20 })
    list.value = page?.records || []
  } finally {
    loading.value = false
  }
}

async function openDetail(o) {
  detail.value = await getOrder(o.orderId)
  detailVisible.value = true
}

async function onCancel(o) {
  try { await ElMessageBox.confirm('取消后将释放座位，确定？', '取消订单', { type: 'warning' }) } catch { return }
  try {
    await cancelOrder(o.orderId)
    ElMessage.success('已取消')
    load()
  } catch (e) { /* 拦截器提示 */ }
}

async function onRefund(o) {
  try {
    await ElMessageBox.confirm('确认整单退票？退款后座位将释放回可售。', '退票', { type: 'warning' })
  } catch { return }
  try {
    await refundOrder(o.orderId)
    ElMessage.success('退票成功')
    load()
  } catch (e) { /* 拦截器提示 */ }
}

function seatText() {
  return (detail.value?.seats || []).map((s) => `${s.seatRow}-${s.seatCol}`).join('、')
}

onMounted(load)
</script>

<template>
  <div>
    <h2 style="margin-bottom: 14px">我的订单</h2>
    <el-tabs v-model="tab" @tab-change="load">
      <el-tab-pane label="全部" name="" />
      <el-tab-pane label="待支付" name="0" />
      <el-tab-pane label="已支付" name="1" />
      <el-tab-pane label="已取消" name="2" />
      <el-tab-pane label="已退款" name="3" />
    </el-tabs>

    <div v-loading="loading">
      <div v-for="o in list" :key="o.orderId" class="order-card">
        <div class="oc-main">
          <div class="title">《{{ o.movieTitle || '电影' }}》</div>
          <div class="meta">{{ String(o.startTime || '').replace('T', ' ').slice(0, 16) }} · {{ o.seatCount }} 张</div>
          <div class="no">单号 {{ o.orderNo }}</div>
        </div>
        <div class="oc-side">
          <el-tag :type="STATUS[o.status][1]" size="small">{{ STATUS[o.status][0] }}</el-tag>
          <div class="money">¥{{ o.totalPrice }}</div>
          <div class="btns">
            <el-button v-if="o.status === 0" type="primary" size="small"
                       @click="router.push(`/pay/${o.orderId}`)">去支付</el-button>
            <el-button v-if="o.status === 0" size="small" @click="onCancel(o)">取消</el-button>
            <el-button v-if="o.status === 1" size="small" type="danger" plain @click="onRefund(o)">退票</el-button>
            <el-button size="small" text @click="openDetail(o)">详情</el-button>
          </div>
        </div>
      </div>
      <el-empty v-if="!loading && !list.length" description="暂无订单" />
    </div>

    <!-- 订单详情 -->
    <el-dialog v-model="detailVisible" title="订单详情" width="420px">
      <template v-if="detail">
        <div class="d-line">单号：{{ detail.order.orderNo }}</div>
        <div class="d-line">金额：¥{{ detail.order.totalPrice }}（{{ detail.seats.length }} 座）</div>
        <div class="d-line">座位：
          <el-tag v-for="(s, i) in detail.seats" :key="i" size="small" style="margin-right:4px">
            {{ s.seatRow }}-{{ s.seatCol }}
          </el-tag>
        </div>
        <div class="d-line">下单：{{ String(detail.order.createTime).replace('T', ' ') }}</div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.order-card { display: flex; justify-content: space-between; align-items: center;
  background: var(--app-card); border-radius: 10px; padding: 14px 18px;
  margin-bottom: 10px; box-shadow: var(--app-shadow); }
.title { font-weight: 600; }
.meta { color: var(--app-muted); font-size: 13px; margin-top: 5px; }
.no { color: var(--app-muted); font-size: 12px; margin-top: 3px; }
.oc-side { display: flex; flex-direction: column; align-items: flex-end; gap: 6px; }
.money { color: var(--el-color-danger); font-weight: 700; font-size: 18px; }
.btns { display: flex; gap: 6px; align-items: center; }
.d-line { padding: 4px 0; font-size: 14px; }
</style>
