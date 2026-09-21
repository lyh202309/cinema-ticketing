<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listConversations, createConversation, getMessages, deleteConversation, streamChat,
} from '../api/chat'
import { mdToHtml } from '../utils/md'

const route = useRoute()

const conversations = ref([])
const currentId = ref(null)
const messages = ref([]) // {role: 0用户/1助手, content, sources: 本轮引用的FAQ标题数组}
const input = ref('')
const sending = ref(false)
const listRef = ref(null)

function scrollEnd() {
  nextTick(() => listRef.value?.scrollTo({ top: listRef.value.scrollHeight }))
}

async function loadConversations() {
  conversations.value = await listConversations()
  if (!currentId.value && conversations.value.length) {
    selectConv(conversations.value[0].id)
  }
}

function selectConv(id) {
  currentId.value = id
  loadMessages(id)
}

/** sources 后端存的是 JSON 数组字符串（无引用时为 null），解析失败一律当空处理 */
function parseSources(s) {
  try {
    const arr = JSON.parse(s || '[]')
    return Array.isArray(arr) ? arr : []
  } catch { return [] }
}

async function loadMessages(id) {
  messages.value = (await getMessages(id)).map((m) => ({
    role: m.role, content: m.content, sources: parseSources(m.sources),
  }))
  scrollEnd()
}

async function newConversation() {
  const conv = await createConversation()
  conversations.value.unshift(conv)
  selectConv(conv.id)
}

async function removeConversation(conv, e) {
  e.stopPropagation()
  try { await ElMessageBox.confirm('删除后聊天记录将一并清除', '删除会话', { type: 'warning' }) } catch { return }
  try {
    await deleteConversation(conv.id)
    conversations.value = conversations.value.filter((c) => c.id !== conv.id)
    if (currentId.value === conv.id) {
      currentId.value = null
      messages.value = []
      if (conversations.value.length) selectConv(conversations.value[0].id)
    }
  } catch { /* 拦截器提示 */ }
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  if (!currentId.value) return ElMessage.warning('请先新建会话')
  input.value = ''
  messages.value.push({ role: 0, content: text, sources: [] })
  messages.value.push({ role: 1, content: '', sources: [] }) // AI 打字机占位
  const aiMsg = messages.value[messages.value.length - 1]
  sending.value = true
  scrollEnd()
  try {
    await streamChat(currentId.value, text, (delta) => {
      if (delta === '[DONE]') return
      aiMsg.content += delta
      scrollEnd()
    })
  } catch (e) {
    aiMsg.content += '\n[出错了，请稍后重试]'
  } finally {
    sending.value = false
    // 以 DB 存档为准同步最终内容 + 刷新标题
    await loadMessages(currentId.value)
    conversations.value = await listConversations()
  }
}

onMounted(async () => {
  await loadConversations()
  if (!conversations.value.length) await newConversation()
  // 支持从首页 AI 卡片带 ?q= 直达提问
  const q = route.query.q
  if (q) {
    input.value = String(q)
    await send()
  }
})
</script>

<template>
  <div class="chat-layout">
    <!-- 会话侧栏 -->
    <aside class="sidebar">
      <el-button type="primary" style="width:100%" @click="newConversation">＋ 新会话</el-button>
      <div class="conv-list">
        <div
          v-for="c in conversations" :key="c.id"
          class="conv" :class="{ on: c.id === currentId }"
          @click="selectConv(c.id)"
        >
          <span class="c-title">{{ c.title || '新会话' }}</span>
          <span class="c-del" @click.stop="removeConversation(c, $event)">✕</span>
        </div>
      </div>
    </aside>

    <!-- 聊天区 -->
    <section class="chat-panel">
      <div ref="listRef" class="msgs">
        <div v-for="(m, i) in messages" :key="i" class="bubble-wrap" :class="m.role === 0 ? 'right' : 'left'">
          <div class="bubble" :class="m.role === 0 ? 'user' : 'ai'">
            <!-- 用户消息：纯文本，不解析 markdown -->
            <span v-if="m.role === 0" class="text">{{ m.content }}</span>
            <!-- AI 消息：解析 markdown -->
            <template v-else>
              <div class="md" v-html="mdToHtml(m.content)"></div>
              <span v-if="i === messages.length - 1 && sending" class="caret"></span>
              <!-- 必须用插值：md.js 的 XSS 免疫靠「先整体转义再解析」，这里绕开它用 v-html 等于开注入口子 -->
              <div v-if="m.sources.length" class="src">依据：{{ m.sources.join('、') }}</div>
            </template>
          </div>
        </div>
      </div>
      <div class="input-bar">
        <el-input
          v-model="input" placeholder="问我电影、场次、座位、订单…（Enter 发送）"
          :disabled="!currentId || sending" clearable
          @keyup.enter="send"
        />
        <el-button type="primary" :loading="sending" :disabled="!currentId" @click="send">发送</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.chat-layout { display: flex; gap: 14px; height: calc(100vh - 130px); }
.sidebar { width: 230px; flex-shrink: 0; background: var(--app-card); border-radius: 10px;
  padding: 12px; box-shadow: var(--app-shadow); display: flex; flex-direction: column; gap: 10px; }
.conv-list { overflow-y: auto; flex: 1; display: flex; flex-direction: column; gap: 4px; }
.conv { display: flex; align-items: center; justify-content: space-between; gap: 6px;
  padding: 8px 10px; border-radius: 6px; cursor: pointer; font-size: 14px; }
.conv:hover { background: var(--el-fill-color-light); }
.conv.on { background: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.c-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.c-del { color: var(--app-muted); font-size: 12px; }
.c-del:hover { color: var(--el-color-danger); }
.chat-panel { flex: 1; display: flex; flex-direction: column; background: var(--app-card);
  border-radius: 10px; box-shadow: var(--app-shadow); overflow: hidden; }
.msgs { flex: 1; overflow-y: auto; padding: 18px; }
.bubble-wrap { margin-bottom: 14px; display: flex; }
.bubble-wrap.right { justify-content: flex-end; }
.bubble { max-width: 76%; padding: 10px 14px; border-radius: 10px; font-size: 14px; line-height: 1.7; }
.bubble.user { background: var(--el-color-primary); color: #fff; }
.bubble.ai { background: var(--el-fill-color-light); }
.text { white-space: pre-wrap; word-break: break-word; }
.caret { display: inline-block; }
.caret::after { content: '▍'; animation: blink 1s infinite; }
@keyframes blink { 50% { opacity: 0; } }
/* RAG 引用来源：跟随全局「弱化色 + 小字号」约定 */
.src { margin-top: 6px; font-size: 12px; color: var(--app-muted); }

/* ===== AI 消息的 Markdown 渲染样式（v-html 内容用 :deep 命中） ===== */
.md { word-break: break-word; }
.md :deep(p) { margin: 4px 0; }
.md :deep(h1), .md :deep(h2), .md :deep(h3), .md :deep(h4) { margin: 10px 0 4px; font-weight: 600; line-height: 1.4; }
.md :deep(h1) { font-size: 18px; }
.md :deep(h2) { font-size: 16px; }
.md :deep(h3) { font-size: 15px; }
.md :deep(h4) { font-size: 14px; }
.md :deep(ul), .md :deep(ol) { margin: 4px 0; padding-left: 22px; }
.md :deep(li) { margin: 2px 0; }
.md :deep(code) { background: rgba(127, 127, 127, .18); padding: 1px 5px; border-radius: 4px;
  font-family: Consolas, monospace; font-size: 13px; }
.md :deep(pre) { background: #0f1115; color: #d5ff80; padding: 10px 12px; border-radius: 8px;
  font-family: Consolas, monospace; font-size: 13px; line-height: 1.5; overflow-x: auto;
  margin: 6px 0; white-space: pre; }
.md :deep(pre code) { background: transparent; padding: 0; color: inherit; }
.md :deep(blockquote) { border-left: 3px solid var(--el-border-color); margin: 6px 0;
  padding: 2px 10px; color: var(--app-muted); }
.md :deep(a) { color: var(--el-color-primary); text-decoration: underline; }
.md :deep(table) { border-collapse: collapse; margin: 6px 0; font-size: 13px; }
.md :deep(th), .md :deep(td) { border: 1px solid var(--el-border-color-lighter); padding: 4px 8px; text-align: left; }
.md :deep(th) { background: var(--el-fill-color-light); font-weight: 600; }
.input-bar { display: flex; gap: 10px; padding: 12px 14px; border-top: 1px solid var(--el-border-color-lighter); }
</style>
