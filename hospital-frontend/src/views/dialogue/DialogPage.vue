<template>
  <div class="dialogue-shell">
    <aside class="panel">
      <div class="panel-header">
        <h2>{{ activeRole === "PATIENT" ? "Opponents" : "Users" }}</h2>
        <div class="panel-actions">
          <el-button text size="small" @click="historyVisible = true">History</el-button>
          <el-button
            text
            size="small"
            @click="activeRole === 'PATIENT' ? loadOpponents() : loadUsers()"
            :loading="activeRole === 'PATIENT' ? loadingOpponents : loadingUsers"
          >
            Refresh
          </el-button>
        </div>
      </div>

      <div class="side-list" v-loading="activeRole === 'PATIENT' ? loadingOpponents : loadingUsers">
        <template v-if="activeRole === 'PATIENT'">
          <template v-if="opponentListWithMeta.length">
            <article
              v-for="opponent in opponentListWithMeta"
              :key="opponent.id"
              :class="['person-item', { active: currentConversation?.participant2UserId === opponent.id }]"
              @click="handleOpponentSelect(opponent)"
            >
              <div class="person-item-name">
                <strong>{{ opponent.name }}</strong>
                <el-badge v-if="opponent.unreadCount > 0" :value="opponent.unreadCount" :max="99" />
              </div>
              <div class="person-item-sub">{{ opponent.lastMessage || '' }}</div>
            </article>
          </template>
          <el-empty v-else description="No opponents" />
        </template>

        <template v-else>
          <template v-if="patientList.length">
            <article
              v-for="patient in patientList"
              :key="patient.id"
              :class="['person-item', { active: currentConversation?.participant1UserId === patient.id }]"
              @click="handlePatientSelect(patient)"
            >
              <div class="person-item-name">
                <strong>{{ patient.name }}</strong>
                <el-badge v-if="patient.unreadCount > 0" :value="patient.unreadCount" :max="99" />
              </div>
              <div class="person-item-sub">{{ patient.lastMessage || '' }}</div>
            </article>
          </template>
          <el-empty v-else description="No users" />
        </template>
      </div>
    </aside>

    <section class="chat-view">
      <header class="chat-meta">
        <div>
          <strong>{{ currentConversation?.title || 'Chat' }}</strong>
          <span>{{ currentConversation?.summary || '' }}</span>
        </div>
        <span>{{ formatTime(currentConversation?.updatedAt || currentConversation?.lastMessageTime) }}</span>
      </header>

      <div class="message-board" ref="messageBoardRef">
        <template v-if="!currentConversation">
          <div class="empty-state">
            <p>{{ activeRole === 'PATIENT' ? 'Select an opponent to start chat, or view history.' : 'Select a user to view chat history.' }}</p>
            <div class="empty-actions">
              <el-button type="primary" @click="historyVisible = true">History</el-button>
              <el-button v-if="activeRole === 'PATIENT'" @click="showOpponentDialog = true">Select</el-button>
            </div>
          </div>
        </template>

        <template v-else>
          <div v-if="loadingMessages" class="loading">Loading...</div>

          <div v-else-if="messages.length">
            <div v-for="msg in messages" :key="msg.id" :class="['message-row', isOwnMessage(msg) ? 'self' : 'other']">
              <div class="message-bubble">
                <div class="message-meta">
                  <strong>{{ msg.senderName || roleLabel(msg.senderRole) }}</strong>
                  <span>{{ formatTime(msg.sentAt) }}</span>
                </div>
                <div class="message-content">{{ msg.content }}</div>
              </div>
            </div>
          </div>

          <el-empty v-else description="No messages" />
        </template>
      </div>

      <div class="composer">
        <div class="identity">Identity: <span>{{ speakerLabel }}</span></div>
        <el-input
          v-model="messageInput"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 5 }"
          placeholder="Type a message..."
          @keydown.enter.exact.prevent="handleSend"
        />
        <el-button type="primary" :disabled="!currentConversation" :loading="sending" @click="handleSend">Send</el-button>
      </div>
    </section>

    <el-dialog v-if="activeRole === 'PATIENT'" v-model="showOpponentDialog" title="Select opponent" width="500px">
      <div v-loading="loadingOpponents">
        <el-empty v-if="!opponentList.length" description="No opponents" />
        <article v-else v-for="opponent in opponentList" :key="opponent.id" class="opponent-select-item" @click="handleOpponentSelect(opponent)">
          <div><strong>{{ opponent.name }}</strong></div>
        </article>
      </div>
      <template #footer>
        <el-button @click="showOpponentDialog = false">Close</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="historyVisible" title="History" size="360px" :append-to-body="true">
      <div class="drawer-content" v-loading="loadingConversations">
        <template v-if="conversations.length">
          <article v-for="item in conversations" :key="item.id" class="history-item">
            <div class="history-item-content" @click="handleHistorySelect(item)">
              <div class="history-head">
                <div class="history-title-wrapper">
                  <strong>{{ item.title }}</strong>
                  <el-badge v-if="getHistoryUnreadCount(item) > 0" :value="getHistoryUnreadCount(item)" :max="99" />
                </div>
                <span>{{ formatTime(item.updatedAt || item.lastMessageTime) }}</span>
              </div>
              <p>{{ item.summary || item.lastMessagePreview || '' }}</p>
            </div>
            <el-button text type="danger" size="small" @click.stop="handleDeleteConversation(item)" :loading="deletingConversationId === item.id">Delete</el-button>
          </article>
        </template>
        <el-empty v-else description="No history" />
      </div>

      <template #footer>
        <div style="display: flex; justify-content: space-between; width: 100%;">
          <el-button type="danger" text @click="handleDeleteAllConversations" :disabled="conversations.length === 0" :loading="deletingAll">Clear all</el-button>
          <el-button text @click="historyVisible = false">Close</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import message from '@/plugins/message'
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { ElMessageBox } from 'element-plus'
import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'
import { getAppConfig } from '@/config/runtimeConfig'
import {
  fetchConversations,
  createConversation,
  deleteConversation,
  deleteAllConversations,
  fetchMessages,
  sendMessage,
  markConversationAsRead
} from '@/api/dialogue'
import { useUserStore } from '@/stores/user'
import { getUserList } from '@/api/system'
import { generatePresignedUrl } from '@/api/oss'
import { getAvatarConfig } from '@/config/constants'

const userStore = useUserStore()

const opponentList = ref([])
const opponentListWithMeta = computed(() => {
  if (activeRole.value !== 'PATIENT') return []
  return opponentList.value.map((o) => {
    const meta = opponentConversationMeta.value[String(o.id)]
    return {
      ...o,
      unreadCount: meta?.unreadCount || 0,
      lastMessage: meta?.lastMessage,
      updatedAt: meta?.updatedAt,
      conversationId: meta?.conversationId || null
    }
  })
})

const conversations = ref([])
const allUsers = ref([])
const patientList = computed(() => {
  if (activeRole.value !== 'ADMIN') return []
  const adminId = currentUserId.value
  return allUsers.value.map((user) => {
    const conv = conversations.value.find((c) => {
      if (!c) return false
      return String(c.participant2UserId) === String(adminId) && String(c.participant1UserId) === String(user.id)
    })
    return {
      id: user.id,
      name: user.realName || user.username || 'User',
      unreadCount: conv?.unreadForParticipant2 ?? 0,
      lastMessage: conv?.lastMessagePreview,
      updatedAt: conv?.updatedAt || conv?.lastMessageTime || null,
      conversationId: conv?.id || null
    }
  })
})

const opponentConversationMeta = computed(() => {
  if (activeRole.value !== 'PATIENT') return {}
  const uid = currentUserId.value
  const map = {}

  conversations.value.forEach((c) => {
    if (!c || !c.participant2UserId) return
    if (!uid) return
    if (String(c.participant1UserId) === String(uid)) {
      const key = String(c.participant2UserId)
      const unread = c.unreadForParticipant1 ?? c.unreadForPatient ?? 0
      const updatedAt = c.updatedAt || c.lastMessageTime || null
      const existing = map[key]
      if (!existing) {
        map[key] = { unreadCount: unread, lastMessage: c.lastMessagePreview, updatedAt, conversationId: c.id }
      } else {
        map[key].unreadCount = (map[key].unreadCount || 0) + unread
        if (updatedAt && (!map[key].updatedAt || dayjs(updatedAt).isAfter(map[key].updatedAt))) {
          map[key].lastMessage = c.lastMessagePreview
          map[key].updatedAt = updatedAt
          map[key].conversationId = c.id
        }
      }
    }
  })

  return map
})

const messages = ref([])
const currentConversation = ref(null)

const loadingConversations = ref(false)
const loadingOpponents = ref(false)
const loadingUsers = ref(false)
const loadingMessages = ref(false)
const sending = ref(false)
const messageInput = ref('')
const messageBoardRef = ref(null)
const historyVisible = ref(false)
const showOpponentDialog = ref(false)

const stompClient = ref(null)
const conversationSubscription = ref(null)
const deletingConversationId = ref(null)
const deletingAll = ref(false)

const roleType = computed(() => userStore.userInfo?.roleType || 0)
const activeRole = computed(() => (roleType.value === 1 ? 'ADMIN' : 'PATIENT'))
const speakerLabel = computed(() => (activeRole.value === 'ADMIN' ? 'Admin' : 'Patient'))
const currentUserId = computed(() => userStore.userInfo?.id || null)

const pagination = reactive({ page: 1, pageSize: 20, total: 0 })
const messagePagination = reactive({ page: 1, pageSize: 100, total: 0 })

const isSameId = (a, b) => {
  if (a === undefined || a === null || b === undefined || b === null) return false
  return String(a) === String(b)
}

const formatTime = (value) => {
  if (!value) return ''
  return dayjs(value).format('MM-DD HH:mm')
}

const roleLabel = (role) => {
  const normalized = role?.toUpperCase()
  if (normalized === 'ADMIN') return 'Admin'
  if (normalized === 'PATIENT') return 'Patient'
  if (normalized === 'USER') return 'Opponent'
  return 'System'
}

const roleFallbackAvatar = (role) => {
  const roleFallbacks = getAvatarConfig()
  const normalized = role?.toUpperCase() || 'SYSTEM'
  if (normalized === 'PATIENT') return roleFallbacks.PATIENT
  return roleFallbacks.SYSTEM
}

const isOwnMessage = (msg) => {
  const senderRoleUpper = msg?.senderRole?.toUpperCase()
  if (senderRoleUpper === activeRole.value) return true
  if (senderRoleUpper === 'USER' && msg?.senderId && isSameId(msg.senderId, currentUserId.value)) return true
  return false
}

const updatePageTitle = () => {
  const totalUnread = conversations.value.reduce((sum, c) => {
    const uid = currentUserId.value
    if (!uid) return sum
    if (String(c.participant1UserId) === String(uid)) return sum + (c.unreadForParticipant1 ?? c.unreadForPatient ?? 0)
    if (String(c.participant2UserId) === String(uid)) return sum + (c.unreadForParticipant2 ?? 0)
    return sum
  }, 0)
  document.title = totalUnread > 0 ? `(${totalUnread}) Chat` : 'Chat'
}

const resolveUnreadCountForCurrentUser = (conversation) => {
  const uid = currentUserId.value
  if (!uid || !conversation) return 0
  if (conversation.participant1UserId && isSameId(conversation.participant1UserId, uid)) {
    return conversation.unreadForParticipant1 ?? conversation.unreadForPatient ?? 0
  }
  if (conversation.participant2UserId && isSameId(conversation.participant2UserId, uid)) {
    return conversation.unreadForParticipant2 ?? 0
  }
  return 0
}

const markingConversationIds = new Set()
const clearUnreadLocally = (conversationId) => {
  const index = conversations.value.findIndex((item) => item.id === conversationId)
  if (index === -1) return
  const target = { ...conversations.value[index] }
  const uid = currentUserId.value

  if (uid) {
    if (target.participant1UserId && isSameId(target.participant1UserId, uid)) {
      target.unreadForParticipant1 = 0
      target.unreadForPatient = 0
    }
    if (target.participant2UserId && isSameId(target.participant2UserId, uid)) {
      target.unreadForParticipant2 = 0
    }
  }

  const next = [...conversations.value]
  next.splice(index, 1, target)
  conversations.value = next
  if (currentConversation.value?.id === target.id) currentConversation.value = target
}

const markConversationAsReadRequest = async (conversation) => {
  if (!conversation?.id) return
  if (resolveUnreadCountForCurrentUser(conversation) <= 0) return
  if (markingConversationIds.has(conversation.id)) return

  clearUnreadLocally(conversation.id)

  const params = { role: activeRole.value }
  if (currentUserId.value) params.userId = currentUserId.value

  markingConversationIds.add(conversation.id)
  try {
    await markConversationAsRead(conversation.id, params)
    await loadConversations(false, true)
  } catch (_) {
    await loadConversations(false, true)
  } finally {
    markingConversationIds.delete(conversation.id)
  }
}

const loadConversations = async (showLoading = true, skipAutoSelect = false) => {
  if (!currentUserId.value) return
  if (showLoading) loadingConversations.value = true

  try {
    const params = { page: pagination.page, pageSize: pagination.pageSize, userId: currentUserId.value }
    const res = await fetchConversations(params)
    const records = res.data?.records || []

    conversations.value = records.sort((a, b) => {
      const timeA = (a.updatedAt || a.lastMessageTime) ? dayjs(a.updatedAt || a.lastMessageTime).valueOf() : 0
      const timeB = (b.updatedAt || b.lastMessageTime) ? dayjs(b.updatedAt || b.lastMessageTime).valueOf() : 0
      return timeB - timeA
    })

    pagination.total = conversations.value.length

    if (!conversations.value.length) {
      if (!skipAutoSelect) {
        currentConversation.value = null
        messages.value = []
      }
      return
    }

    if (skipAutoSelect) {
      if (currentConversation.value?.id) {
        const latest = conversations.value.find((c) => c.id === currentConversation.value.id)
        if (latest) currentConversation.value = { ...latest }
      }
      return
    }

    if (!currentConversation.value?.id) {
      await handleSelectConversation(conversations.value[0])
    }
  } catch (_) {
    if (showLoading) message.error('Load conversations failed')
  } finally {
    if (showLoading) loadingConversations.value = false
  }
}

const loadOpponents = async () => {
  if (activeRole.value !== 'PATIENT') return
  loadingOpponents.value = true
  try {
    const res = await getUserList({ page: 1, pageSize: 1000 })
    if (res.code !== 200) {
      opponentList.value = []
      return
    }

    const currentUserId = userStore.userInfo?.id
    const admins = (res.data?.records || []).filter((u) => u.roleType === 1 && String(u.id) !== String(currentUserId))

    opponentList.value = await Promise.all(
      admins.map(async (u) => {
        const next = { ...u }
        if (next.avatar && next.avatar.includes('oss-cn-')) {
          const hasSignature = next.avatar.includes('Signature=') || next.avatar.includes('OSSAccessKeyId=')
          if (!hasSignature) {
            try {
              const signedRes = await generatePresignedUrl(next.avatar, 60)
              if (signedRes.code === 200 && signedRes.data) next.avatar = signedRes.data
            } catch (_) {}
          }
        }
        return { id: next.id, name: next.realName || next.username || 'Admin', avatar: next.avatar, introduction: next.introduction || '' }
      })
    )

    await loadConversations(false, true)
  } catch (_) {
    message.error('Load opponents failed')
  } finally {
    loadingOpponents.value = false
  }
}

const loadUsers = async () => {
  if (activeRole.value !== 'ADMIN') return
  loadingUsers.value = true
  try {
    const res = await getUserList({ page: 1, pageSize: 1000 })
    if (res.code !== 200) {
      allUsers.value = []
      return
    }

    const currentUserId = userStore.userInfo?.id
    const users = (res.data?.records || []).filter((u) => u.roleType !== 1 && String(u.id) !== String(currentUserId))

    allUsers.value = await Promise.all(
      users.map(async (u) => {
        const next = { ...u }
        if (next.avatar && next.avatar.includes('oss-cn-')) {
          const hasSignature = next.avatar.includes('Signature=') || next.avatar.includes('OSSAccessKeyId=')
          if (!hasSignature) {
            try {
              const signedRes = await generatePresignedUrl(next.avatar, 60)
              if (signedRes.code === 200 && signedRes.data) next.avatar = signedRes.data
            } catch (_) {}
          }
        }
        return next
      })
    )

    await loadConversations(false, true)
  } catch (_) {
    message.error('Load users failed')
  } finally {
    loadingUsers.value = false
  }
}

const loadMessages = async (conversationId, showLoading = true) => {
  if (!conversationId) return
  if (showLoading) loadingMessages.value = true

  try {
    const res = await fetchMessages(conversationId, { page: messagePagination.page, pageSize: messagePagination.pageSize })
    const records = res.data?.records || []
    messages.value = records.map((item) => ({
      ...item,
      senderAvatar: item.senderAvatar || roleFallbackAvatar(item.senderRole)
    }))
    messagePagination.total = res.data?.total || 0

    nextTick(() => {
      const board = messageBoardRef.value
      if (board) board.scrollTop = board.scrollHeight
    })
  } catch (_) {
    if (showLoading) message.error('Load messages failed')
  } finally {
    if (showLoading) loadingMessages.value = false
  }
}

const handleSelectConversation = async (conversation) => {
  if (!conversation?.id) return
  currentConversation.value = { ...conversation }
  loadingMessages.value = true

  try {
    await loadMessages(conversation.id, false)
    await nextTick()
    await markConversationAsReadRequest(conversation)
    historyVisible.value = false
  } finally {
    loadingMessages.value = false
  }
}

const handleHistorySelect = (conversation) => handleSelectConversation(conversation)

const handleOpponentSelect = async (opponent) => {
  if (activeRole.value !== 'PATIENT') return
  const uid = currentUserId.value
  if (!uid) {
    message.warning('Please login first')
    return
  }

  const existing = conversations.value.find((c) => String(c.participant2UserId) === String(opponent.id) && String(c.participant1UserId) === String(uid))
  if (existing) {
    await handleSelectConversation(existing)
    showOpponentDialog.value = false
    return
  }

  try {
    const payload = {
      patientId: uid,
      participant2UserId: opponent.id,
      conversationType: 'USER_USER',
      title: `${opponent.name} chat`,
      summary: 'Chat session'
    }

    const res = await createConversation(payload)
    if (res.data?.id) {
      showOpponentDialog.value = false
      historyVisible.value = false
      await loadConversations(false)
      const created = conversations.value.find((c) => String(c.id) === String(res.data.id))
      if (created) await handleSelectConversation(created)
      else if (conversations.value[0]) await handleSelectConversation(conversations.value[0])
    } else {
      message.error('Create conversation failed')
    }
  } catch (_) {
    message.error('Create conversation failed')
  }
}

const handlePatientSelect = async (patient) => {
  if (activeRole.value !== 'ADMIN') return
  const adminId = currentUserId.value
  if (!adminId) return

  const existing = conversations.value.find((c) => String(c.participant2UserId) === String(adminId) && String(c.participant1UserId) === String(patient.id))
  if (existing) return handleSelectConversation(existing)

  try {
    const res = await createConversation({
      patientId: patient.id,
      conversationType: 'ADMIN_USER',
      title: `${patient.name} x admin`,
      summary: 'Admin chat'
    })

    if (res.data?.id) {
      await loadConversations(false, true)
      const created = conversations.value.find((c) => String(c.id) === String(res.data.id))
      if (created) await handleSelectConversation(created)
    }
  } catch (_) {
    message.error('Create conversation failed')
  }
}

const handleDeleteConversation = async (conversation) => {
  if (!conversation?.id) return
  try {
    await ElMessageBox.confirm('Delete this conversation?', 'Delete', { type: 'warning' })
    deletingConversationId.value = conversation.id
    await deleteConversation(conversation.id, activeRole.value)

    if (currentConversation.value?.id === conversation.id) {
      currentConversation.value = null
      messages.value = []
    }

    await loadConversations(true, historyVisible.value)
    message.success('Deleted')
  } catch (e) {
    if (e !== 'cancel') message.error('Delete failed')
  } finally {
    deletingConversationId.value = null
  }
}

const handleDeleteAllConversations = async () => {
  if (!currentUserId.value) return
  if (!conversations.value.length) return

  try {
    await ElMessageBox.confirm('Clear all conversations?', 'Clear all', { type: 'warning' })
    deletingAll.value = true
    await deleteAllConversations(currentUserId.value, activeRole.value)

    currentConversation.value = null
    messages.value = []
    conversations.value = []
    historyVisible.value = false
    message.success('Cleared')
  } catch (e) {
    if (e !== 'cancel') message.error('Clear failed')
  } finally {
    deletingAll.value = false
  }
}

const handleSend = async () => {
  if (!currentConversation.value?.id) return
  if (!messageInput.value.trim()) return

  const payload = {
    senderRole: activeRole.value,
    senderId: currentUserId.value,
    senderName: userStore.userInfo?.realName || userStore.userInfo?.username || (activeRole.value === 'ADMIN' ? 'Admin' : 'Patient'),
    senderAvatar: userStore.userInfo?.avatar || roleFallbackAvatar(activeRole.value),
    content: messageInput.value.trim(),
    contentType: 'TEXT'
  }

  sending.value = true
  try {
    const res = await sendMessage(currentConversation.value.id, payload)
    messageInput.value = ''

    if (res.code === 200 && res.data) {
      const exists = messages.value.some((msg) => msg.id === res.data.id)
      if (!exists) messages.value.push(res.data)
    }

    nextTick(() => {
      const board = messageBoardRef.value
      if (board) board.scrollTop = board.scrollHeight
    })
  } catch (_) {
    message.error('Send failed')
  } finally {
    sending.value = false
  }
}

const showBrowserNotification = (event) => {
  if (!('Notification' in window)) return
  if (Notification.permission === 'granted') {
    createNotification(event)
  } else if (Notification.permission === 'default') {
    Notification.requestPermission().then((p) => {
      if (p === 'granted') createNotification(event)
    })
  }
}

const createNotification = (event) => {
  const title = event.senderName || 'New message'
  const notification = new Notification(title, {
    body: event.content || '',
    icon: event.senderAvatar || roleFallbackAvatar(event.senderRole),
    tag: `conversation-${event.conversationId}`
  })

  notification.onclick = () => {
    window.focus()
    const conversation = conversations.value.find((c) => c.id === event.conversationId)
    if (conversation) handleSelectConversation(conversation)
    notification.close()
  }

  setTimeout(() => notification.close(), 5000)
}

const applyIncomingEvent = async (event) => {
  const senderRoleUpper = event.senderRole?.toUpperCase()
  const isOwn = senderRoleUpper === activeRole.value || (senderRoleUpper === 'USER' && isSameId(event.senderId, currentUserId.value))

  const incomingMessage = {
    id: event.messageId,
    conversationId: event.conversationId,
    senderId: event.senderId,
    senderRole: event.senderRole,
    senderName: event.senderName,
    senderAvatar: event.senderAvatar || roleFallbackAvatar(event.senderRole),
    content: event.content,
    contentType: event.contentType,
    sentAt: event.sentAt,
    timestamp: event.sentAt
  }

  const isCurrentConversation = currentConversation.value?.id === event.conversationId
  if (!isOwn && (!isCurrentConversation || !document.hasFocus())) showBrowserNotification(event)

  if (isCurrentConversation) {
    const exists = messages.value.some((m) => m.id === incomingMessage.id)
    if (!exists) {
      messages.value.push(incomingMessage)
      nextTick(() => {
        const board = messageBoardRef.value
        if (board) board.scrollTop = board.scrollHeight
      })
    }
  }

  await loadConversations(false, true)
  updatePageTitle()
}

const handleSocketMessage = (frame) => {
  if (!frame?.body) return
  try {
    const event = JSON.parse(frame.body)
    if (!event?.conversationId) return
    applyIncomingEvent(event)
  } catch (_) {}
}

const connectConversationSocket = () => {
  if (!userStore.token || stompClient.value?.connected) return

  const config = getAppConfig()
  const wsBaseUrl = config?.wsBaseUrl || '/ws'
  const wsUrl = `${wsBaseUrl}?token=${encodeURIComponent(userStore.token)}`

  const client = new Client({
    webSocketFactory: () => new SockJS(wsUrl),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000
  })

  client.onConnect = () => {
    conversationSubscription.value = client.subscribe('/user/queue/conversations', handleSocketMessage)
  }

  client.onDisconnect = () => {
    conversationSubscription.value = null
  }

  client.onStompError = () => {}
  client.onWebSocketError = () => {}

  client.activate()
  stompClient.value = client
}

const disconnectConversationSocket = () => {
  try {
    conversationSubscription.value?.unsubscribe()
  } catch (_) {}

  conversationSubscription.value = null

  if (stompClient.value) {
    try {
      stompClient.value.deactivate()
    } catch (_) {}
    stompClient.value = null
  }
}

const getHistoryUnreadCount = (conversation) => resolveUnreadCountForCurrentUser(conversation)
const handleHistorySelectSafe = (item) => handleHistorySelect(item)

onMounted(async () => {
  if (activeRole.value === 'PATIENT') {
    await Promise.all([loadConversations(), loadOpponents()])
  } else {
    await Promise.all([loadConversations(), loadUsers()])
  }

  if (conversations.value.length > 0 && !currentConversation.value) {
    await handleSelectConversation(conversations.value[0])
  }

  connectConversationSocket()
  updatePageTitle()

  window.addEventListener('conversations-marked-all-read', () => loadConversations(false, true))
})

watch(messages, () => {
  nextTick(() => {
    const board = messageBoardRef.value
    if (board) board.scrollTop = board.scrollHeight
  })
})

watch(historyVisible, (visible) => {
  if (visible) loadConversations(true, true)
})

onUnmounted(() => {
  disconnectConversationSocket()
})
</script>

<style scoped>
.dialogue-shell {
  width: 100%;
  height: calc(100vh - 80px);
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 20px;
  padding: 20px;
}

.panel {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 24px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.panel-actions {
  display: flex;
  gap: 6px;
}

.side-list {
  overflow-y: auto;
}

.person-item {
  padding: 12px 14px;
  border-radius: 18px;
  background: rgba(148, 163, 184, 0.09);
  cursor: pointer;
  margin-bottom: 10px;
  border: 1px solid transparent;
}
.person-item.active {
  border-color: rgba(59, 130, 246, 0.4);
}
.person-item-name {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.person-item-sub {
  margin-top: 6px;
  color: #6b7280;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chat-view {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 32px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.message-board {
  flex: 1;
  background: #f8fafc;
  border-radius: 24px;
  padding: 16px;
  overflow-y: auto;
}

.empty-state {
  padding: 30px 0;
  text-align: center;
}
.empty-actions {
  margin-top: 16px;
  display: flex;
  gap: 12px;
  justify-content: center;
}
.loading {
  padding: 20px;
  text-align: center;
}

.message-row {
  display: flex;
  margin-bottom: 14px;
}
.message-row.self {
  justify-content: flex-end;
}
.message-row.other {
  justify-content: flex-start;
}
.message-bubble {
  max-width: 70%;
  padding: 12px 14px;
  border-radius: 18px;
  background: white;
  border: 1px solid rgba(15, 23, 42, 0.08);
}
.message-row.self .message-bubble {
  background: #0ea5e9;
  color: white;
  border-color: transparent;
}
.message-meta {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 12px;
}
.message-content {
  white-space: pre-wrap;
}

.composer {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}
.identity {
  font-size: 12px;
  color: #6b7280;
  margin-right: 10px;
}
.identity span {
  color: #111827;
  font-weight: 600;
}

.opponent-select-item {
  padding: 12px 14px;
  border-radius: 18px;
  cursor: pointer;
  border: 1px solid rgba(148, 163, 184, 0.3);
  margin-bottom: 10px;
}

.drawer-content {
  padding: 0 10px;
}
.history-item {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 12px;
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.3);
  margin-bottom: 12px;
}
.history-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}
.history-item-content p {
  margin: 8px 0 0;
  color: #6b7280;
  font-size: 12px;
}
</style>
