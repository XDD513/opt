import { Client } from '@stomp/stompjs'

/**
 * 使用 STOMP 直连后端的 /ws/native（原生 WebSocket），避免 SockJS 的 xhr / xhr_streaming
 * 在 Nginx 反代下产生大量短轮询与偶发失败；与 WebSocketConfig 中 /ws/native 端点一致。
 */
function buildNativeStompBrokerUrl(token) {
  if (typeof window === 'undefined' || !token) return null
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/native?token=${encodeURIComponent(token)}`
}

export function createArticleNotificationSocket({ token, onMessage, onError } = {}) {
  let client = null

  const connect = () => {
    if (!token || client?.connected) return
    const brokerURL = buildNativeStompBrokerUrl(token)
    if (!brokerURL) return

    client = new Client({
      brokerURL,
      reconnectDelay: 10000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000
    })

    client.onConnect = () => {
      client.subscribe('/user/queue/article-notifications', (frame) => {
        try {
          const payload = frame?.body ? JSON.parse(frame.body) : null
          onMessage?.(payload)
        } catch (e) {
          onMessage?.(null)
        }
      })
    }

    client.onStompError = (frame) => {
      onError?.(frame)
    }

    client.onWebSocketError = (event) => {
      onError?.(event)
    }

    client.activate()
  }

  const disconnect = () => {
    if (client) {
      try {
        client.deactivate()
      } catch (e) {
        // ignore
      }
      client = null
    }
  }

  return { connect, disconnect }
}
