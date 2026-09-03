<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { listMovies } from '../api/movie'
import { listSessions } from '../api/session'
import { listCinemas } from '../api/cinema'

const router = useRouter()

const loading = ref(true)
const movies = ref([])
const sessions = ref([])
const cinemas = ref([])
const now = ref(Date.now()) // 每秒刷新，驱动"距开场"倒计时

// 海报加载失败 → 隐藏 img，露出下方渐变占位
function onPosterErr(e) {
  e.target.classList.add('err')
}

const hotMovies = computed(() => movies.value.filter((m) => m.status === 1))
const comingMovies = computed(() => movies.value.filter((m) => m.status === 0))
const hotSessions = computed(() => sessions.value.filter((s) => s.isHot))

async function load() {
  loading.value = true
  try {
    const [mv, se, ci] = await Promise.all([
      listMovies({ page: 1, size: 30 }),
      listSessions({ page: 1, size: 20 }),
      listCinemas({ page: 1, size: 6 }),
    ])
    movies.value = mv?.records || []
    sessions.value = se?.records || []
    cinemas.value = ci?.records || []
  } finally {
    loading.value = false
  }
}

function fmt(t) {
  return String(t || '').replace('T', ' ').slice(0, 16)
}
function fmtDateBadge(t) {
  if (!t) return ''
  const d = String(t).slice(5).split('-')
  return `${Number(d[0])}月${Number(d[1])}日`
}
function fmtRemain(t) {
  const diff = new Date(String(t).replace(' ', 'T')).getTime() - now.value
  if (diff <= 0) return '已开场'
  const m = Math.floor(diff / 60000)
  if (m < 60) return `${m}分钟后开场`
  const h = Math.floor(m / 60)
  const day = Math.floor(h / 24)
  if (day > 0) return `距开场 ${day}天${h % 24}小时`
  return `距开场 ${h}小时${m % 60}分`
}
function moviePoster(movieId) {
  return movies.value.find((m) => m.id === movieId)?.poster || ''
}
function posterGrads(i) {
  const g = [
    'linear-gradient(135deg,#ff9a76,#e64d2e)',
    'linear-gradient(135deg,#667eea,#5a4bd1)',
    'linear-gradient(135deg,#43cea2,#185a9d)',
    'linear-gradient(135deg,#f7971e,#ffd200)',
    'linear-gradient(135deg,#e96443,#904e95)',
    'linear-gradient(135deg,#24c6dc,#514a9d)',
  ]
  return g[i % g.length]
}

let timer = null
onMounted(() => {
  load()
  timer = setInterval(() => (now.value = Date.now()), 1000)
})
onBeforeUnmount(() => clearInterval(timer))

const AI_QUICK = ['有什么电影正在上映？', '场次1还有可售座位吗？', '我的订单有哪些？', '这周末有什么片？']
function askAi(q) {
  router.push({ path: '/chat', query: { q } })
}
</script>

<template>
  <div class="home" v-loading="loading">
    <!-- ============ 1+2. 正在热映 / 即将上映（6:4 并排） ============ -->
    <div class="row-64">
      <!-- 正在热映：横滑海报卡 -->
      <section class="block" style="min-width: 0">
        <div class="sec-head">
          <div class="sec-title">
            <span class="bar" style="background: #e64d2e" />
            正在热映
            <span class="sub">HOT SHOWING</span>
          </div>
        </div>
        <div class="h-scroll">
          <div v-for="(m, i) in hotMovies" :key="m.id" class="mp-card" @click="router.push(`/movie/${m.id}`)">
            <div class="ph" :style="{ background: posterGrads(i) }">
              <span class="ph-txt">{{ m.title.slice(0, 1) }}</span>
              <img :src="m.poster" alt="" @error="onPosterErr" />
              <span class="score"><b>{{ m.rating ? Number(m.rating).toFixed(1) : '--' }}</b> 分</span>
            </div>
            <div class="mp-name">{{ m.title }}</div>
            <div class="mp-meta">{{ m.genre }} · {{ m.duration }}分钟</div>
            <button class="buy" @click.stop="router.push(`/movie/${m.id}`)">选座购票</button>
          </div>
          <el-empty v-if="!loading && !hotMovies.length" description="暂无上映电影" :image-size="60" />
        </div>
      </section>

      <!-- 即将上映：紧凑海报墙 -->
      <section class="block" style="min-width: 0">
        <div class="sec-head coming-head">
          <div class="sec-title">
            <span class="bar" style="background: #5a4bd1" />
            即将上映
            <span class="sub">COMING</span>
          </div>
        </div>
        <div class="h-scroll right-align">
          <div v-for="(m, i) in comingMovies" :key="m.id" class="mp-card" @click="router.push(`/movie/${m.id}`)">
            <div class="ph" :style="{ background: posterGrads(i + 3) }">
              <span class="ph-txt">{{ m.title.slice(0, 1) }}</span>
              <img :src="m.poster" alt="" @error="onPosterErr" />
              <span class="badge">{{ fmtDateBadge(m.releaseDate) }}上映</span>
            </div>
            <div class="mp-name">{{ m.title }}</div>
            <div class="mp-meta">{{ m.genre }} · {{ m.duration }}分钟</div>
            <div class="expect">🎬 敬请期待</div>
          </div>
          <el-empty v-if="!loading && !comingMovies.length" description="暂无即将上映" :image-size="60" />
        </div>
      </section>
    </div>

    <!-- ============ AI 助手（正在热映/即将上映 之下、热门开抢 之上） ============ -->
    <section class="ai-wrap">
      <div class="ai-card">
        <div class="ai-left">
          <div class="ai-badge">🤖 AI 助手</div>
          <div class="ai-title">选片纠结？看哪场？直接问我</div>
          <div class="ai-desc">可以查场次、看剩余座位、问订单，随叫随到</div>
          <div class="chips">
            <span v-for="q in AI_QUICK" :key="q" class="chip" @click="askAi(q)">{{ q }}</span>
          </div>
        </div>
        <div class="ai-orb">🎬</div>
      </div>
    </section>

    <!-- ============ 3. 热门开抢（场次卡） ============ -->
    <section v-if="hotSessions.length" class="block">
      <div class="sec-head">
        <div class="sec-title">
          <span class="bar" style="background: #ff7a00" />
          热门开抢
          <span class="sub">Hot On Sale</span>
        </div>
      </div>
      <div class="ses-list">
        <div v-for="(s, i) in hotSessions" :key="s.id" class="ses-card">
          <div class="s-ph" :style="{ background: posterGrads(i) }">
            <span class="ph-txt">{{ s.movieTitle.slice(0, 1) }}</span>
            <img :src="moviePoster(s.movieId)" alt="" @error="onPosterErr" />
          </div>
          <div class="s-main">
            <div class="s-name">{{ s.movieTitle }} <el-tag type="danger" size="small" effect="dark">热门</el-tag></div>
            <div class="s-meta">📍 {{ s.cinemaName }} · {{ s.hallName }}</div>
            <div class="s-time">🕐 {{ fmt(s.startTime) }} · <span class="soon">{{ fmtRemain(s.startTime) }}</span></div>
          </div>
          <div class="s-side">
            <div class="s-price">¥{{ s.price }}<span class="s-unit">/张</span></div>
            <el-button type="danger" @click="router.push(`/session/${s.id}`)">去抢票</el-button>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 4. 影院推荐 ============ -->
    <section class="block">
      <div class="sec-head">
        <div class="sec-title">
          <span class="bar" style="background: #185a9d" />
          影院推荐
          <span class="sub">CINEMAS</span>
        </div>
        <span class="more" @click="router.push('/cinemas')">全部影院 ›</span>
      </div>
      <div class="cinema-grid">
        <div v-for="c in cinemas" :key="c.id" class="cinema-card" @click="router.push(`/cinema/${c.id}`)">
          <div class="c-top">
            <span class="c-logo">🎬</span>
            <div class="c-name">{{ c.name }}</div>
            <el-tag size="small" effect="plain">{{ c.region }}</el-tag>
          </div>
          <div class="c-meta">⭐ <b>{{ c.rating ? Number(c.rating).toFixed(1) : '-' }}</b> · {{ c.address }}</div>
          <div class="c-phone">☎ {{ c.phone }}</div>
          <button class="c-btn" @click.stop="router.push(`/cinema/${c.id}`)">看排片</button>
        </div>
        <el-empty v-if="!loading && !cinemas.length" description="暂无影院" :image-size="60" />
      </div>
    </section>

    <footer class="foot">
      <div>🎬 影院订票系统 · 仅供学习演示</div>
      <div class="f-sub">抢资格 → 选座 → 锁座 5 分钟支付 · 已支付可退票 · AI 助手随时答疑</div>
    </footer>
  </div>
</template>

<style scoped>
.block { margin-bottom: 30px; }
/* AI 助手（正在热映/即将上映之下、热门开抢之上） */
.ai-wrap { margin: 0 0 30px; }
.ai-card { border-radius: 14px; padding: 24px 28px; display: flex; justify-content: space-between; align-items: center;
  background: linear-gradient(120deg, #514a9d, #24c6dc); color: #fff;
  box-shadow: 0 6px 20px rgba(81, 74, 157, .25); }
.ai-badge { display: inline-block; background: rgba(255, 255, 255, .2); padding: 3px 12px; border-radius: 12px; font-size: 12px; }
.ai-title { font-size: 20px; font-weight: 800; margin-top: 8px; }
.ai-desc { opacity: .85; font-size: 13px; margin-top: 4px; }
.chips { margin-top: 12px; display: flex; flex-wrap: wrap; gap: 8px; }
.chip { background: rgba(255, 255, 255, .18); border: 1px solid rgba(255, 255, 255, .35); padding: 4px 12px;
  border-radius: 16px; font-size: 13px; cursor: pointer; transition: background .15s; }
.chip:hover { background: rgba(255, 255, 255, .34); }
.ai-orb { font-size: 60px; opacity: .92; }
@media (max-width: 700px) { .ai-orb { display: none; } }

/* 6:4 并排（窄屏回落单列） */
.row-64 { display: grid; grid-template-columns: 6.5fr 3.5fr; gap: 24px; align-items: start; }
/* 桌面与窄桌面始终并排双栏；仅移动端(<860px)才回落单列 */
@media (max-width: 860px) { .row-64 { grid-template-columns: 1fr; } }

.sec-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 12px; }
/* 即将上映：标题容器与下方卡片组同宽(2×175 + 12gap = 362)并右端对齐 → 标题左缘正贴卡片组左缘 */
.coming-head { width: 362px; margin-left: auto; }
.sec-title { font-size: 18px; font-weight: 700; display: flex; align-items: center; gap: 10px; }
.sec-title .bar { width: 5px; height: 19px; border-radius: 3px; display: inline-block; }
.sec-title .sub { font-size: 11px; color: var(--app-muted); font-weight: 400; letter-spacing: 2px; }
.more { color: var(--app-muted); font-size: 13px; cursor: pointer; }
.more:hover { color: var(--el-color-primary); }

/* ---- 正在热映：横向滚动海报卡 ---- */
.h-scroll { display: flex; gap: 12px; overflow-x: auto; padding: 4px 0 8px; }
.h-scroll.right-align { justify-content: flex-end; }
.h-scroll::-webkit-scrollbar { height: 6px; }
.h-scroll::-webkit-scrollbar-thumb { background: var(--el-border-color); border-radius: 3px; }
.mp-card { width: 175px; flex-shrink: 0; background: var(--app-card); border-radius: 10px; overflow: hidden;
  box-shadow: var(--app-shadow); cursor: pointer; transition: transform .18s; padding-bottom: 10px; }
.mp-card:hover { transform: translateY(-4px); }
.ph { position: relative; height: 234px; display: flex; align-items: center; justify-content: center; overflow: hidden; }
.ph .ph-txt { font-size: 64px; color: rgba(255, 255, 255, .92); font-weight: 800; text-shadow: 0 2px 10px rgba(0, 0, 0, .18); }
.ph img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.ph img.err { display: none; }
.ph .score { position: absolute; left: 7px; top: 7px; background: rgba(0, 0, 0, .55); color: #ffd54f;
  font-size: 11px; padding: 2px 8px; border-radius: 11px; }
.ph .score b { font-size: 14px; }
.ph .badge { position: absolute; left: 50%; bottom: 7px; transform: translateX(-50%); background: rgba(0, 0, 0, .62);
  color: #fff; font-size: 12px; padding: 2px 11px; border-radius: 11px; letter-spacing: .5px; white-space: nowrap; }
.mp-name { font-size: 15px; font-weight: 600; padding: 8px 10px 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mp-meta { color: var(--app-muted); font-size: 12px; padding: 2px 10px 0; }
.buy { margin: 8px 10px 0; width: calc(100% - 20px); height: 28px; border: none; border-radius: 6px;
  background: var(--el-color-primary); color: #fff; cursor: pointer; font-size: 13px; }
.buy:hover { opacity: .88; }

/* ---- 即将上映：横向滚动（复用 mp-card / h-scroll）---- */
.expect { margin: 8px 10px 0; width: calc(100% - 20px); height: 28px; display: flex; align-items: center;
  justify-content: center; border-radius: 6px; font-size: 12px;
  background: var(--el-color-primary-light-9); color: var(--el-color-primary); }

/* ---- 热门开抢：场次卡 ---- */
.ses-list { display: flex; flex-direction: column; gap: 10px; }
.ses-card { display: flex; align-items: center; gap: 14px; background: var(--app-card); border-radius: 10px;
  padding: 12px 16px; box-shadow: var(--app-shadow); }
.s-ph { width: 52px; height: 70px; border-radius: 6px; flex-shrink: 0; position: relative;
  display: flex; align-items: center; justify-content: center; overflow: hidden; }
.s-ph .ph-txt { font-size: 26px; color: rgba(255, 255, 255, .92); font-weight: 800; }
.s-ph img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.s-ph img.err { display: none; }
.s-main { flex: 1; min-width: 0; }
.s-name { font-size: 16px; font-weight: 700; display: flex; align-items: center; gap: 8px; }
.s-meta { color: var(--app-muted); font-size: 13px; margin-top: 5px; }
.s-time { color: var(--app-muted); font-size: 13px; margin-top: 4px; }
.s-time .soon { color: var(--el-color-danger); font-weight: 600; }
.s-side { text-align: right; flex-shrink: 0; }
.s-price { color: var(--el-color-danger); font-size: 22px; font-weight: 800; }
.s-unit { font-size: 12px; color: var(--app-muted); font-weight: 400; }
.s-side .el-button { margin-top: 8px; }

/* ---- 影院推荐 ---- */
.cinema-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(270px, 1fr)); gap: 14px; }
.cinema-card { background: var(--app-card); border-radius: 10px; padding: 14px 16px; box-shadow: var(--app-shadow);
  cursor: pointer; transition: transform .18s; }
.cinema-card:hover { transform: translateY(-3px); }
.c-top { display: flex; align-items: center; gap: 8px; }
.c-logo { width: 32px; height: 32px; border-radius: 8px; background: linear-gradient(135deg, #185a9d, #43cea2);
  display: flex; align-items: center; justify-content: center; font-size: 17px; }
.c-name { font-weight: 700; font-size: 15px; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.c-meta { color: var(--app-muted); font-size: 13px; margin-top: 9px; }
.c-meta b { color: #f5a623; }
.c-phone { color: var(--app-muted); font-size: 12px; margin-top: 4px; }
.c-btn { margin-top: 11px; width: 100%; height: 30px; border: 1px solid var(--el-color-primary); color: var(--el-color-primary);
  background: transparent; border-radius: 6px; cursor: pointer; font-size: 13px; }
.c-btn:hover { background: var(--el-color-primary); color: #fff; }

/* ---- footer ---- */
.foot { text-align: center; padding: 24px 0 8px; color: var(--app-muted); font-size: 13px; }
.f-sub { font-size: 12px; margin-top: 6px; opacity: .7; }
</style>
