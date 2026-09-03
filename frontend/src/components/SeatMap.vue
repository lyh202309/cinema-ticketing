<script setup>
// 受控座位图：selected 由父级持有（便于父级轮询冲突时主动移除被抢的座）
const props = defineProps({
  rows: { type: Number, required: true },
  cols: { type: Number, required: true },
  // seats[r-1][c-1]：0 可售 / 1 占用（已锁/已售都置灰）
  seats: { type: Array, default: () => [] },
  selected: { type: Array, default: () => [] }, // [{row, col}]
})
const emit = defineEmits(['toggle'])

function taken(r, c) {
  return props.seats[r - 1]?.[c - 1] === 1
}
function isSel(r, c) {
  return props.selected.some((s) => s.row === r && s.col === c)
}
function click(r, c) {
  emit('toggle', { row: r, col: c })
}
</script>

<template>
  <div class="seatmap">
    <div class="screen">银幕</div>
    <div class="rows-wrap">
      <div v-for="r in rows" :key="r" class="seat-row">
        <span class="row-no">{{ r }}排</span>
        <div class="row-seats">
          <button
            v-for="c in cols"
            :key="c"
            class="seat"
            :class="{
              taken: taken(r, c),
              selected: isSel(r, c) && !taken(r, c),
            }"
            :disabled="taken(r, c)"
            @click="click(r, c)"
          >
            {{ c }}
          </button>
        </div>
      </div>
    </div>
    <div class="legend">
      <span><i class="dot avail" />可售</span>
      <span><i class="dot taken" />已占</span>
      <span><i class="dot sel" />已选</span>
    </div>
  </div>
</template>

<style scoped>
.seatmap { background: var(--app-card); border-radius: 12px; padding: 20px; box-shadow: var(--app-shadow); }
.screen { width: 60%; margin: 0 auto 18px; text-align: center; padding: 6px; color: var(--app-muted);
  background: linear-gradient(#eee, transparent); border-radius: 50% / 100%; font-size: 13px; }
html.dark .screen { background: linear-gradient(#333, transparent); }
.rows-wrap { display: flex; flex-direction: column; gap: 6px; align-items: center; }
.seat-row { display: flex; align-items: center; gap: 8px; }
.row-no { width: 40px; text-align: right; color: var(--app-muted); font-size: 12px; }
.row-seats { display: flex; gap: 6px; flex-wrap: nowrap; }
.seat { width: 24px; height: 24px; border-radius: 5px; border: 1px solid transparent;
  font-size: 10px; color: var(--app-muted); background: var(--el-color-success-light-8);
  cursor: pointer; line-height: 1; }
.seat:hover:not(:disabled) { transform: scale(1.1); }
/* 已占：灰色填充 + 深色座号（保证字在灰底可读，且与可售/已选明显区分） */
.seat.taken { background: #c3c6cc; border-color: #aeb1b8; color: #55585f; cursor: not-allowed; }
html.dark .seat.taken { background: #3b3e44; border-color: #30333a; color: #aeb3bb; }
.seat.selected { background: var(--el-color-primary); color: #fff; }
.legend { display: flex; gap: 20px; justify-content: center; margin-top: 16px; font-size: 13px; color: var(--app-muted); }
.dot { display: inline-block; width: 12px; height: 12px; border-radius: 3px; margin-right: 5px; vertical-align: -1px; }
.dot.avail { background: var(--el-color-success-light-8); }
.dot.taken { background: #c3c6cc; }
html.dark .dot.taken { background: #3b3e44; }
.dot.sel { background: var(--el-color-primary); }
</style>
