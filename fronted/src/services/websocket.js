import SockJS from 'sockjs-client'
import { Stomp } from 'stompjs/lib/stomp'

let stompClient = null
let subscriptions = []

function parseFrame(frame) {
  if (!frame?.body) return {}
  try {
    return JSON.parse(frame.body)
  } catch {
    return { content: frame.body }
  }
}

export function connectWebSocket(handlers = {}) {
  const token = localStorage.getItem('accessToken')
  if (!token) return null

  disconnectWebSocket()
  const socket = new SockJS('/ws')
  stompClient = Stomp.over(socket)
  stompClient.debug = null
  stompClient.connect(
    { Authorization: `Bearer ${token}` },
    () => {
      subscriptions = [
        stompClient.subscribe('/user/queue/notifications', (frame) => {
          handlers.onNotification?.(parseFrame(frame))
        }),
        stompClient.subscribe('/user/queue/messages', (frame) => {
          handlers.onMessage?.(parseFrame(frame))
        }),
        stompClient.subscribe('/topic/broadcast', (frame) => {
          handlers.onBroadcast?.(parseFrame(frame))
        }),
      ]
    },
    () => {
      stompClient = null
      subscriptions = []
    },
  )
  return stompClient
}

export function disconnectWebSocket() {
  subscriptions.forEach((subscription) => subscription.unsubscribe())
  subscriptions = []
  if (stompClient?.connected) {
    stompClient.disconnect(() => {})
  }
  stompClient = null
}
