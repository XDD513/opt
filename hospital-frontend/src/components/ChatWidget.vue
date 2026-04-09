<template>
  <div v-if="visible" class="chat-widget">
    <!-- 聊天窗口头部 -->
    <div class="chat-header">
      <!-- 波浪背景图案 -->
      <div class="wave-pattern"></div>
      
      <!-- 头部内容 -->
      <div class="header-top">
        <div class="chat-title-wrapper">
          <div class="chat-icon-bubble">
            <el-icon :size="16"><ChatDotRound /></el-icon>
          </div>
          <span class="chat-title-text">聊天</span>
        </div>
      </div>
      
      <!-- 头像组 -->
      <div class="avatars-container">
        <el-tooltip
          v-for="(sender, index) in recentSenders"
          :key="index"
          :content="sender.senderName || '未知用户'"
          placement="top"
          :show-after="200"
          :hide-after="0"
        >
          <el-avatar
            :size="32"
            :src="sender.senderAvatar"
            class="avatar-item"
            :style="{ zIndex: recentSenders.length - index }"
          >
            <el-icon><User /></el-icon>
          </el-avatar>
        </el-tooltip>
      </div>
      
      <!-- 副标题 -->
      <div class="chat-subtitle">
        <span>有疑问吗?联系我们!</span>
      </div>
      
      <!-- 上次活动时间 -->
      <div class="chat-last-active">
        <span>上次活动 {{ lastActiveTime }}</span>
      </div>
    </div>

    <!-- 聊天消息区域 -->
    <div class="chat-messages" ref="messagesContainer">
      <div v-if="!conversationId" class="no-conversation-tip">
        <el-empty description="请先创建或选择对话" :image-size="80" />
      </div>
      <template v-else>
        <div
          v-for="(message, index) in messages"
          :key="message.id || index"
          :class="['message-item', isSelfMessage(message) ? 'self' : 'other']"
        >
          <el-avatar :size="28" :src="message.senderAvatar" class="message-avatar">
            <el-icon><User /></el-icon>
          </el-avatar>
          <div class="message-content">
            <div class="message-name">{{ message.senderName }}</div>
            <div class="message-bubble">{{ message.content }}</div>
          </div>
        </div>
        <div v-if="loadingMessages" class="loading-messages">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>加载中...</span>
        </div>
      </template>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input-area">
      <el-input
        v-model="inputMessage"
        type="textarea"
        :autosize="{ minRows: 1, maxRows: 4 }"
        placeholder="输入你的信息..."
        class="message-input"
        :disabled="!conversationId || sending || loadingMessages"
        @keydown.enter.exact.prevent="handleSend"
      />
      <div class="input-footer">
        <div class="input-toolbar">
          <el-button text circle class="toolbar-btn">
            <el-icon :size="16"><Picture /></el-icon>
          </el-button>
          <el-button text circle class="toolbar-btn">
            <el-icon :size="16"><Paperclip /></el-icon>
          </el-button>
          <el-button text circle class="toolbar-btn">
            <el-icon :size="16"><Microphone /></el-icon>
          </el-button>
        </div>
        <div class="crisp-branding">
          <span class="crisp-text">We run on</span>
          <el-icon :size="14" class="crisp-icon"><ChatDotRound /></el-icon>
          <span class="crisp-text-bold">crisp</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import message from '@/plugins/message'
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { User, Picture, Paperclip, Microphone, ChatDotRound, Loading } from '@element-plus/icons-vue'

import { fetchMessages, sendMessage, getRecentSenders } from '@/api/dialogue'
import { useUserStore } from '@/stores/user'
import { getAppConfig } from '@/config/runtimeConfig'
import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  conversationId: {
    type: [Number, String],
    default: null
  }
})

const emit = defineEmits(['close', 'send'])

const userStore = useUserStore()
const inputMessage = ref('')
const messagesContainer = ref(null)
const messages = ref([])
const recentSenders = ref([])
const loadingMessages = ref(false)
const sending = ref(false)

// WebSocket相关
const stompClient = ref(null)
const conversationSubscription = ref(null)

// 上次活动时间
const lastActiveTime = computed(() => {
  const now = new Date()
  return `${now.getFullYear()}/${String(now.getMonth() + 1).padStart(2, '0')}/${String(now.getDate()).padStart(2, '0')}`
})

// 判断消息是否是自己发送的
const isSelfMessage = (message) => {
  if (!message) return false
  
  const currentUserId = userStore.userInfo?.id
  const currentUserRole = getCurrentUserRole()
  
  if (!currentUserId || !currentUserRole) return false
  
  // 在管理员对话中，senderId就是用户ID（无论是患者、医生还是管理员）
  // 所以直接比较senderId和用户ID，以及角色
  const isOwn = String(message.senderId) === String(currentUserId) && 
                message.senderRole?.toUpperCase() === currentUserRole.toUpperCase()
  
  return isOwn
}

// 获取当前用户角色
const getCurrentUserRole = () => {
  const roleType = userStore.userInfo?.roleType
  if (roleType === 1) return 'ADMIN'
  if (roleType === 0) return 'PATIENT'
  // 医生角色已移除：其他未知角色按患者侧处理
  return 'PATIENT'
}

// 加载最近发送者信息
const loadRecentSenders = async () => {
  if (!props.conversationId) {
    recentSenders.value = []
    return
  }
  
  try {
    const res = await getRecentSenders(props.conversationId)
    if (res.code === 200 && res.data) {
      recentSenders.value = res.data
    }
  } catch (error) {

  }
}

// 加载消息列表
const loadMessages = async () => {
  if (!props.conversationId) {
    messages.value = []
    return
  }
  
  loadingMessages.value = true
  try {
    const res = await fetchMessages(props.conversationId, {
      page: 1,
      pageSize: 100
    })
    if (res.code === 200 && res.data) {
      messages.value = res.data.records || []
      scrollToBottom()
    }
  } catch (error) {

    message.error('加载消息失败')
  } finally {
    loadingMessages.value = false
  }
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

// 发送消息
const handleSend = async () => {
  if (!inputMessage.value.trim() || !props.conversationId || sending.value) {
    return
  }

  const content = inputMessage.value.trim()
  const currentUserId = userStore.userInfo?.id
  const currentUserRole = getCurrentUserRole()
  
  if (!currentUserId || !currentUserRole) {
    message.error('用户信息不完整，无法发送消息')
    return
  }

  sending.value = true
  try {
    const payload = {
      senderId: currentUserId,
      senderRole: currentUserRole,
      senderName: userStore.userInfo?.realName || userStore.userInfo?.username || '我',
      senderAvatar: userStore.userInfo?.avatar || '',
      content: content,
      contentType: 'TEXT'
    }
    
    const res = await sendMessage(props.conversationId, payload)
    if (res.code === 200 && res.data) {
      // 不立即添加到列表，等待WebSocket推送（避免重复）
      // 如果WebSocket未连接，则立即添加
      if (!stompClient.value?.connected) {
        messages.value.push(res.data)
        scrollToBottom()
      }
      
      inputMessage.value = ''
      
      // 刷新最近发送者
      await loadRecentSenders()
      
      emit('send', content)
    } else {
      message.error(res.message || '发送消息失败')
    }
  } catch (error) {

    message.error('发送消息失败')
  } finally {
    sending.value = false
  }
}

// 关闭聊天窗口
const handleClose = () => {
  emit('close')
}

// 监听conversationId变化，重新加载数据
watch(() => props.conversationId, (newId) => {
  if (newId) {
    loadMessages()
    loadRecentSenders()
  } else {
    messages.value = []
    recentSenders.value = []
  }
}, { immediate: true })

// 监听消息变化，自动滚动
watch(() => messages.value.length, () => {
  scrollToBottom()
})

// 处理WebSocket消息
const handleSocketMessage = (frame) => {
  if (!frame?.body) {
    return
  }
  try {
    const event = JSON.parse(frame.body)
    if (!event?.conversationId) {
      return
    }
    
    // 如果是当前会话的消息，添加到消息列表
    if (event.conversationId === props.conversationId) {
      // 判断是否是自己发送的消息（避免显示自己发送的消息通知）
      const currentUserId = userStore.userInfo?.id
      const currentUserRole = getCurrentUserRole()
      const isOwnMessage = currentUserId && currentUserRole && 
                          String(event.senderId) === String(currentUserId) && 
                          event.senderRole?.toUpperCase() === currentUserRole.toUpperCase()
      
      const incomingMessage = {
        id: event.messageId,
        conversationId: event.conversationId,
        senderId: event.senderId,
        senderRole: event.senderRole,
        senderName: event.senderName,
        senderAvatar: event.senderAvatar,
        content: event.content,
        contentType: event.contentType || 'TEXT',
        sentAt: event.sentAt
      }
      
      // 检查消息是否已存在（避免重复添加）
      const exists = messages.value.some(msg => msg.id === incomingMessage.id)
      if (!exists) {
        messages.value.push(incomingMessage)
        scrollToBottom()
        
        // 刷新最近发送者
        loadRecentSenders()
      }
      
      // 如果不是自己发送的消息，且聊天窗口不可见，可以显示浏览器通知
      // 注意：ChatWidget主要用于管理员对话，这里不显示通知，通知由DialogPage处理
    }
  } catch (error) {

  }
}

// 连接WebSocket
const connectWebSocket = () => {
  const token = userStore.token
  if (!token) {
    return
  }
  
  if (stompClient.value?.connected) {
    return
  }

  // 获取配置的 WebSocket URL
  const appConfig = getAppConfig()
  const wsBaseUrl = appConfig?.wsBaseUrl || '/ws'
  const wsUrl = `${wsBaseUrl}?token=${encodeURIComponent(token)}`
  
  const client = new Client({
    webSocketFactory: () => new SockJS(wsUrl),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000
  })

  client.onConnect = () => {
    // 订阅用户专属的对话消息队列
    conversationSubscription.value = client.subscribe('/user/queue/conversations', handleSocketMessage)
  }

  client.onDisconnect = () => {
    conversationSubscription.value = null
  }

  client.onStompError = (frame) => {

  }

  client.onWebSocketError = (event) => {

  }

  client.activate()
  stompClient.value = client
}

// 断开WebSocket
const disconnectWebSocket = () => {
  try {
    conversationSubscription.value?.unsubscribe()
  } catch (error) {

  }
  conversationSubscription.value = null

  if (stompClient.value) {
    try {
      stompClient.value.deactivate()
    } catch (error) {

    }
    stompClient.value = null
  }
}

// 监听可见性变化
watch(() => props.visible, (newVal) => {
  if (newVal && props.conversationId) {
    loadMessages()
    loadRecentSenders()
    // 如果WebSocket未连接，则连接
    if (!stompClient.value?.connected) {
      connectWebSocket()
    }
    nextTick(() => {
      scrollToBottom()
    })
  } else if (!newVal) {
    // 关闭聊天窗口时，不断开WebSocket（可能其他组件在使用）
    // 只取消订阅当前会话
  }
})

// 监听conversationId变化，重新连接WebSocket
watch(() => props.conversationId, (newId) => {
  if (newId && props.visible) {
    // 如果WebSocket未连接，则连接
    if (!stompClient.value?.connected) {
      connectWebSocket()
    }
  }
})

onMounted(() => {
  if (props.visible && props.conversationId) {
    loadMessages()
    loadRecentSenders()
    connectWebSocket()
  }
})

onUnmounted(() => {
  disconnectWebSocket()
})
</script>

<style scoped lang="scss">
.chat-widget {
  position: fixed;
  right: 24px;
  bottom: 90px;
  width: 380px;
  height: 600px;
  max-height: calc(100vh - 120px);
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  z-index: 2000;
  overflow: hidden;
}

.chat-header {
  position: relative;
  background: linear-gradient(135deg, #4a90e2 0%, #5cb85c 100%);
  color: white;
  padding: 16px 20px 20px;
  border-radius: 16px 16px 0 0;
  overflow: hidden;
}

// 波浪背景图案
.wave-pattern {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image: 
    repeating-linear-gradient(
      0deg,
      transparent,
      transparent 8px,
      rgba(255, 255, 255, 0.08) 8px,
      rgba(255, 255, 255, 0.08) 16px
    ),
    repeating-linear-gradient(
      90deg,
      transparent,
      transparent 8px,
      rgba(255, 255, 255, 0.08) 8px,
      rgba(255, 255, 255, 0.08) 16px
    );
  opacity: 0.4;
  pointer-events: none;
}

.header-top {
  position: relative;
  z-index: 1;
  margin-bottom: 12px;
}

.chat-title-wrapper {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.chat-icon-bubble {
  width: 28px;
  height: 28px;
  background: rgba(74, 144, 226, 0.3);
  border: 2px solid rgba(255, 255, 255, 0.8);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 4px;
}

.chat-title-text {
  font-size: 16px;
  font-weight: 600;
}

.avatars-container {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  margin: 12px 0;
  
  .avatar-item {
    margin-left: -10px;
    border: 2.5px solid rgba(255, 255, 255, 0.95);
    cursor: pointer;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.15);
    transition: transform 0.2s;
    
    &:hover {
      transform: scale(1.1);
    }
    
    &:first-child {
      margin-left: 0;
    }
  }
}

.chat-subtitle {
  position: relative;
  z-index: 1;
  font-size: 13px;
  text-align: center;
  opacity: 0.95;
  margin-bottom: 4px;
}

.chat-last-active {
  position: relative;
  z-index: 1;
  font-size: 12px;
  text-align: center;
  opacity: 0.85;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #fff;
  
  .no-conversation-tip {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    min-height: 200px;
  }
  
  .loading-messages {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 16px;
    color: #909399;
    font-size: 14px;
  }
}

.message-item {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  align-items: flex-start;

  &.self {
    flex-direction: row-reverse;

    .message-content {
      align-items: flex-end;

      .message-bubble {
        background: #409eff;
        color: white;
        border-radius: 12px 12px 4px 12px;
      }
    }
  }

  &.other {
    .message-content {
      align-items: flex-start;

      .message-bubble {
        background: #4a90e2;
        color: white;
        border-radius: 16px 16px 16px 4px;
        box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
      }
    }
  }
}

.message-avatar {
  flex-shrink: 0;
}

.message-content {
  display: flex;
  flex-direction: column;
  max-width: 70%;
  gap: 4px;
}

.message-name {
  font-size: 12px;
  color: #606266;
  margin-bottom: 4px;
  font-weight: 500;
}

.message-bubble {
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.5;
  word-wrap: break-word;
}

.chat-input-area {
  border-top: 1px solid #e4e7ed;
  padding: 12px 16px;
  background: white;
}

.message-input {
  margin-bottom: 8px;

  :deep(.el-textarea__inner) {
    border: none;
    border-bottom: 1px solid #e4e7ed;
    border-radius: 0;
    font-size: 14px;
    resize: none;
    padding: 8px 0;
    box-shadow: none;
    background: transparent;

    &:focus {
      border-color: #409eff;
      box-shadow: none;
    }
    
    &:disabled {
      background: #f5f7fa;
      cursor: not-allowed;
    }
  }
}

.input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
}

.input-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;

  .toolbar-btn {
    padding: 4px;
    color: #909399;
    border: none;
    background: transparent;

    &:hover {
      color: #409eff;
      background: transparent;
    }
  }
}

.crisp-branding {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #909399;
  
  .crisp-text {
    font-style: normal;
  }
  
  .crisp-icon {
    color: #909399;
  }
  
  .crisp-text-bold {
    font-weight: 600;
    color: #606266;
  }
}

// 滚动条样式
.chat-messages {
  &::-webkit-scrollbar {
    width: 6px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }

  &::-webkit-scrollbar-thumb {
    background: #c0c4cc;
    border-radius: 3px;

    &:hover {
      background: #a0a4ac;
    }
  }
}
</style>
