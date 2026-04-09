import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'
import { getAppConfig } from '@/config/runtimeConfig'

export function createArticleNotificationSocket({ token, onMessage, onError } = {}) {
  let client = null

  const connect = () => {
    if (!token || client?.connected) return
    const config = getAppConfig()
    const wsBaseUrl = config?.wsBaseUrl || '/ws'
    const wsUrl = `${wsBaseUrl}?token=${encodeURIComponent(token)}`

    client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      reconnectDelay: 5000,
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

