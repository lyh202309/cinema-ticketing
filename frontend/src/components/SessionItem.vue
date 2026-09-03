<script setup>
// 场次单行卡片（购票入口）：热门显示"抢票"，普通显示"购票"
const props = defineProps({
  session: { type: Object, required: true },
})
const emit = defineEmits(['buy'])

function fmt(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <div class="session-card">
    <div class="sc-main">
      <div class="cinema">{{ props.session.cinemaName }}</div>
      <div class="time">{{ fmt(props.session.startTime) }}</div>
      <div class="hall muted">{{ props.session.hallName }} · {{ props.session.seatsTotal ?? '-' }}座</div>
    </div>
    <div class="sc-side">
      <el-tag v-if="props.session.isHot" type="danger" size="small">热门需抢票</el-tag>
      <el-tag v-else type="success" size="small">直接购票</el-tag>
      <div class="price">¥{{ props.session.price }}</div>
      <el-button size="small" :type="props.session.isHot ? 'danger' : 'primary'"
                 @click="emit('buy', props.session)">
        {{ props.session.isHot ? '抢票' : '购票' }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.session-card { display: flex; justify-content: space-between; align-items: center;
  background: var(--app-card); border-radius: 8px; padding: 14px 18px;
  box-shadow: var(--app-shadow); }
.cinema { font-weight: 600; margin-bottom: 6px; }
.time { font-size: 15px; }
.muted { color: var(--app-muted); font-size: 12px; margin-top: 4px; }
.sc-side { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; }
.price { color: var(--el-color-danger); font-weight: 700; font-size: 18px; }
</style>
