import request from '../utils/request'

export const listConversations = () => request.get('/chat/conversations')
export const createConversation = () => request.post('/chat/conversations')
export const getMessages = (id) => request.get(`/chat/conversations/${id}/messages`)
export const deleteConversation = (id) => request.delete(`/chat/conversations/${id}`)

/**
 * SSE 流式对话（POST + fetch 读取，需带 token，不能用 EventSource）
 * 后端 SseEmitter 每段 partial 发一个 data 帧；结束后发 [DONE]
 */
export async function streamChat(conversationId, message, onDelta) {
  const token = localStorage.getItem('token')
  const resp = await fetch(`/chat/conversations/${conversationId}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', authorization: token },
    body: JSON.stringify({ message }),
  })
  if (!resp.ok || !resp.body) {
    throw new Error(`对话失败 HTTP ${resp.status}`)
  }
  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buf = ''

  const flush = () => {
    // 提取完整 SSE 事件（data: 开头行，多 data 行拼成一段）
    while (true) {
      const sep = buf.indexOf('\n\n')
      if (sep < 0) break
      const raw = buf.slice(0, sep)
      buf = buf.slice(sep + 2)
      const data = raw.split('\n')
        .filter((l) => l.startsWith('data:'))
        .map((l) => l.slice(5)).join('\n')
      if (data) onDelta(data)
    }
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buf += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
    flush()
  }
  flush() // 处理残余
  decoder.decode()
}
