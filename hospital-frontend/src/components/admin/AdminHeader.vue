<template>
  <header class="admin-header">
    <div class="header-left">
      <el-icon 
        class="menu-toggle" 
        :size="24"
        @click="toggleSidebar"
      >
        <Menu />
      </el-icon>
      
      <nav class="breadcrumbs">
        <span 
          v-for="(item, index) in breadcrumbs" 
          :key="index"
          class="breadcrumb-item"
          :class="{ active: index === breadcrumbs.length - 1 }"
        >
          {{ item }}
          <span v-if="index < breadcrumbs.length - 1" class="separator">/</span>
        </span>
      </nav>
    </div>

    <div class="header-right">
      <button type="button" class="header-notify-btn" aria-label="通知" @click="goArticleNotifications">
        <svg class="notify-svg" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
          <path d="M18 8a6 6 0 10-12 0c0 7-3 9-3 9h18s-3-2-3-9" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          <path d="M13.73 21a2 2 0 01-3.46 0" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
      <span class="username">管理员：{{ userStore.userInfo.realName || userStore.userInfo.username }}</span>

      <!-- 用户头像下拉菜单 -->
      <el-dropdown class="avatar-dropdown" trigger="click" @command="handleCommand">
        <el-avatar class="avatar-trigger" :size="40" :src="avatarSrc">
          <el-icon>
            <User />
          </el-icon>
        </el-avatar>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon>
                <User />
              </el-icon> 个人信息
            </el-dropdown-item>
            <el-dropdown-item command="logout">
              <el-icon>
                <SwitchButton />
              </el-icon> 退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup>
import { computed, onMounted, onUnmounted, inject, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import message from '@/plugins/message'
import { Menu, User, SwitchButton } from '@element-plus/icons-vue'
import { DEFAULT_AVATAR } from '@/constants/avatar'
import { useUserStore } from '@/stores/user'
import { getUserInfo } from '@/api/user'
import { createArticleNotificationSocket } from '@/utils/articleNotificationSocket'
import { markAllConversationsAsRead } from '@/api/dialogue'
 

const props = defineProps({
  breadcrumbs: {
    type: Array,
    default: () => []
  },
  sidebarOpen: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['toggle-sidebar'])

const router = useRouter()
const userStore = useUserStore()
 

const avatarSrc = computed(() => userStore.userInfo?.avatar || DEFAULT_AVATAR)
let articleSocket = null
 

const toggleSidebar = () => {
  emit('toggle-sidebar')
}

const goArticleNotifications = () => {
  router.push('/admin/article/notifications')
}

const handleCommand = (command) => {
  if (command === 'profile') {
    router.push('/admin/profile')
  } else if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(async () => {
      await userStore.logout()
      message.success('已退出登录')
      router.push('/login')
    }).catch(() => { })
  }
}

const refreshUserInfo = async () => {
  try {
    const res = await getUserInfo()
    if (res.code === 200 && res.data) {
      userStore.setUserInfo(res.data)
    }
  } catch (error) {
    // 静默失败
  }
}

const connectNotificationSocket = () => {
  const token = userStore.token
  if (!token) return
  if (stompClient?.connected) {
    return
  }

  // 获取配置的 WebSocket URL
  const config = getAppConfig()
  const wsBaseUrl = config?.wsBaseUrl || '/ws'
  const wsUrl = `${wsBaseUrl}?token=${encodeURIComponent(token)}`
  
  stompClient = new Client({
    webSocketFactory: () => new SockJS(wsUrl),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000
  })

  stompClient.onConnect = () => {}

  stompClient.onStompError = (frame) => {
    // 静默失败
  }

  stompClient.onWebSocketError = (event) => {
    // 静默失败
  }

  stompClient.activate()
}

const disconnectNotificationSocket = () => {
  if (stompClient) {
    try {
      stompClient.deactivate()
    } catch (error) {
      // 静默失败
    }
    stompClient = null
  }
}

onMounted(async () => {
  await refreshUserInfo()
  articleSocket = createArticleNotificationSocket({
    token: userStore.token,
    onMessage: async () => {
      message.success('收到新的文章通知')
    }
  })
  articleSocket.connect()
})

onUnmounted(() => {
  articleSocket?.disconnect()
})
</script>

<style scoped lang="scss">
.admin-header {
  min-height: 50px;
  background: #009688;
  color: white;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  row-gap: 8px;
  padding: 8px 16px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  flex-shrink: 0;

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;
    min-width: 0;
    flex: 1 1 auto;

    .menu-toggle {
      cursor: pointer;
      padding: 4px;
      border-radius: 4px;
      transition: all 0.2s;

      &:hover {
        background: rgba(255, 255, 255, 0.1);
      }
    }

    .breadcrumbs {
      display: flex;
      align-items: center;
      font-size: 14px;
      flex-wrap: wrap;
      min-width: 0;

      .breadcrumb-item {
        opacity: 0.8;

        &.active {
          opacity: 1;
          font-weight: 500;
        }

        .separator {
          margin: 0 8px;
          opacity: 0.6;
        }
      }
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 18px;
    font-size: 14px;
    flex-shrink: 0;
    flex-wrap: wrap;

    .header-notify-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      padding: 0;
      border: none;
      border-radius: 8px;
      background: rgba(255, 255, 255, 0.15);
      color: #fff;
      cursor: pointer;
      transition: background 0.2s ease, transform 0.15s ease;
    }

    .header-notify-btn:hover {
      background: rgba(255, 255, 255, 0.28);
      transform: scale(1.05);
    }

    .notify-svg {
      width: 22px;
      height: 22px;
    }

    .notification-wrapper {
      position: relative;
      display: flex;
      align-items: center;
      border-radius: 4px;
      background: rgba(255, 255, 255, 0.1);
      transition: all 0.2s;

      &.has-unread {
        background: rgba(255, 255, 255, 0.15);
      }

      .notification-trigger {
        display: flex;
        align-items: center;
        gap: 6px;
        padding: 6px 12px;
        border: none;
        background: transparent;
        color: white;
        cursor: pointer;
        font-size: 14px;
      }

      .notification-text {
        white-space: nowrap;
      }
    }

    .username {
      font-size: 14px;
      white-space: nowrap;
    }

    .avatar-dropdown {
      :deep(.avatar-trigger) {
        cursor: pointer;
        border: 2px solid rgba(255, 255, 255, 0.3);
        transition: all 0.2s;
        background: rgba(255, 255, 255, 0.1);

        &:hover {
          border-color: rgba(255, 255, 255, 0.5);
          background: rgba(255, 255, 255, 0.15);
        }
      }
    }
  }
}

:deep(.notification-popover) {
  padding: 12px 0 0 !important;
}

:deep(.notification-header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 8px;
  font-size: 14px;
  font-weight: 500;
  color: #1f2933;

  .el-button {
    padding: 0 4px;
  }
}

:deep(.notification-list) {
  list-style: none;
  margin: 0;
  padding: 0 16px 12px;

  .notification-item {
    padding: 10px 0;
    border-bottom: 1px dashed #e9e9e9;

    &:last-child {
      border-bottom: none;
    }

    .notification-title {
      font-size: 14px;
      font-weight: 600;
      color: #1f2933;
      margin-bottom: 4px;
    }

    .notification-content {
      font-size: 13px;
      color: #4a5568;
      line-height: 1.5;
      margin-bottom: 6px;
    }

    .notification-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 12px;
      color: #94a3b8;
    }
  }
}

:deep(.notification-empty) {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 20px 0 24px;
  color: #94a3b8;

  p {
    margin: 0;
    font-size: 13px;
  }
}
</style>
