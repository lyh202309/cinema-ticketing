<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listConversations, createConversation, getMessages, deleteConversation, streamChat,
} from '../api/chat'

const route = useRoute()

const conversations = ref([])
const currentId = ref(null)
const messages = ref([]) // {role: 0用户/1助手, content}
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

async function loadMessages(id) {
  messages.value = (await getMessages(id)).map((m) => ({ role: m.role, content: m.content }))
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

// 把内容按 ``` 代码块切开（AI 会用代码块画 ASCII 座位图）
function blocksOf(text) {
  if (!text) return []
  const parts = []
  const re = /```([\s\S]*?)```/g
  let idx = 0
  let m
  while ((m = re.exec(text))) {
    if (m.index > idx) parts.push({ t: 'text', x: text.slice(idx, m.index) })
    parts.push({ t: 'code', x: m[1] })
    idx = m.index + m[0].length
  }
  if (idx < text.length) parts.push({ t: 'text', x: text.slice(idx) })
  if (!parts.length) parts.push({ t: 'text', x: text })
  return parts
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  if (!currentId.value) return ElMessage.warning('请先新建会话')
  input.value = ''
  messages.value.push({ role: 0, content: text })
  messages.value.push({ role: 1, content: '' }) // AI 打字机占位
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
            <template v-for="(b, bi) in blocksOf(m.content)" :key="bi">
              <pre v-if="b.t === 'code'" class="code-block">{{ b.x }}</pre>
              <span v-else class="text" :class="{ caret: m.role === 1 && i === messages.length - 1 && sending }">{{ b.x }}</span>
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
.text.caret::after { content: '▍'; animation: blink 1s infinite; }
@keyframes blink { 50% { opacity: 0; } }
.code-block { background: #0f1115; color: #d5ff80; padding: 10px 12px; border-radius: 8px;
  font-family: Consolas, monospace; font-size: 13px; line-height: 1.5; overflow-x: auto;
  margin: 6px 0; white-space: pre; }
.input-bar { display: flex; gap: 10px; padding: 12px 14px; border-top: 1px solid var(--el-border-color-lighter); }
</style>
