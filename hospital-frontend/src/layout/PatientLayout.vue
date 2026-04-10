<template>
  <div class="layout-container" @touchstart="handleTouchStart" @touchend="handleTouchEnd">
    <!-- 顶部导航栏 -->
    <el-header class="header">
      <div class="header-left">
        <button class="menu-toggle" v-if="!isMobile" @click="toggleSidebar">
          <el-icon :size="20">
            <Expand v-if="sidebarCollapsed" />
            <Fold v-else />
          </el-icon>
        </button>
        <button class="menu-toggle" v-if="isMobile" @click="sidebarDrawerVisible = true">
          <el-icon :size="20">
            <Menu />
          </el-icon>
        </button>
        <el-icon class="logo-icon" :size="32">
          <Van />
        </el-icon>
        <span class="logo-text">{{ patientLogoText }}</span>
      </div>
      <div class="header-right">
        <span class="username">{{ userStore.userInfo.realName || userStore.userInfo.username }}</span>
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
                </el-icon> 个人中心
              </el-dropdown-item>
              
              <el-dropdown-item command="logout" divided>
                <el-icon>
                  <SwitchButton />
                </el-icon> 退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <!-- 主体内容 -->
    <el-container class="main-container">
      <!-- 侧边栏 -->
      <el-aside v-if="!isMobile" :width="sidebarCollapsed ? '64px' : '220px'" class="sidebar">
        <PatientSidebarMenu :active-menu="activeMenu" :collapsed="sidebarCollapsed" @select="handleMenuSelect" />
      </el-aside>

      <!-- 内容区 -->
      <el-main class="content">
        <router-view />
      </el-main>
    </el-container>

    <!-- 右下角聊天按钮 -->
    <div class="chat-float-button" @click="handleChatButtonClick">
      <el-icon :size="24">
        <Close v-if="chatWidgetVisible" />
        <ChatLineRound v-else />
      </el-icon>
    </div>

    <!-- 聊天窗口组件 -->
    <ChatWidget 
      :visible="chatWidgetVisible" 
      :conversationId="adminConversationId"
      @close="handleChatClose" 
      @send="handleChatSend" 
    />

    <el-drawer
      v-model="sidebarDrawerVisible"
      direction="ltr"
      size="70%"
      append-to-body
      :with-header="false"
      class="sidebar-drawer"
    >
      <div class="drawer-header">
        <span>功能导航</span>
        <el-button circle text @click="sidebarDrawerVisible = false">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
      <PatientSidebarMenu :active-menu="activeMenu" @select="handleMenuSelect" />
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import message from '@/plugins/message'
import {
  Van,
  HomeFilled,
  Calendar,
  Plus,
  Tickets,
  User,
  Document,
  Food,
  List,
  Star,
  Pointer,
  Location,
  Connection,
  ChatDotRound,
  ChatLineRound,
  Reading,
  EditPen,
  CollectionTag,
  Notebook,
  DataAnalysis,
  Setting,
  Close,
  Menu,
  Fold,
  Expand
} from '@element-plus/icons-vue'
import { getUserInfo } from '@/api/user'
import { useUserStore } from '@/stores/user'
 
import { getAppConfig } from '@/config/runtimeConfig'
import dayjs from 'dayjs'
import ChatWidget from '@/components/ChatWidget.vue'
import PatientSidebarMenu from '@/components/patient/PatientSidebarMenu.vue'
import { fetchConversations, createConversation, getOrCreateAdminConversation, markAllConversationsAsRead } from '@/api/dialogue'
import { DEFAULT_AVATAR } from '@/constants/avatar'
import { createArticleNotificationSocket } from '@/utils/articleNotificationSocket'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
 

const appConfig = inject('appConfig', getAppConfig())
const systemName = computed(() => appConfig?.systemInfo?.name || '中医体质辨识系统')
const patientLogoText = computed(() => `${systemName.value} - 患者端`)

let stompClient = null

// 当前激活的菜单
const activeMenu = computed(() => route.path)

// 聊天窗口显示状态
const chatWidgetVisible = ref(false)
const isMobile = ref(false)
const sidebarDrawerVisible = ref(false)
const sidebarCollapsed = ref(false)
const touchStartX = ref(0)
const touchStartY = ref(0)

// 管理员对话ID
const adminConversationId = ref(null)
let articleSocket = null

// 获取或创建管理员对话
const getOrCreateAdminConversationHandler = async () => {
  if (adminConversationId.value) {
    return adminConversationId.value
  }
  
  const currentUserId = userStore.userInfo?.id
  if (!currentUserId) {

    return null
  }
  
  try {
    // 调用后端接口获取或创建管理员对话
    const res = await getOrCreateAdminConversation(currentUserId)
    if (res.code === 200 && res.data) {
      adminConversationId.value = res.data.id
      return adminConversationId.value
    } else {

      return null
    }
  } catch (error) {

    return null
  }
}

// 处理聊天按钮点击
const handleChatButtonClick = async () => {
  if (!chatWidgetVisible.value) {
    // 打开聊天窗口时，尝试获取或创建管理员对话
    await getOrCreateAdminConversationHandler()
  }
  
  chatWidgetVisible.value = !chatWidgetVisible.value
}

// 关闭聊天窗口
const handleChatClose = () => {
  chatWidgetVisible.value = false
}

// 处理发送消息
const handleChatSend = (message) => {
  // 消息发送逻辑由ChatWidget组件处理
}

 
const avatarSrc = computed(() => userStore.userInfo?.avatar || DEFAULT_AVATAR)

// 下拉菜单命令处理
const handleCommand = (command) => {
  if (command === 'profile') {
    router.push('/patient/profile')
  } else if (command === 'notifications') {
    router.push('/patient/article/notifications')
  } else if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(async () => {
      try {
        await userStore.logout()
        message.success('已退出登录')
      } finally {
        // 兜底跳转，避免因为其他清理逻辑异常导致停留在当前页
        router.replace('/login')
      }
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

  }
}

onMounted(async () => {
  handleResize()
  window.addEventListener('resize', handleResize)
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
  window.removeEventListener('resize', handleResize)
  articleSocket?.disconnect()
})

const handleResize = () => {
  isMobile.value = window.innerWidth < 992
  if (!isMobile.value) {
    sidebarDrawerVisible.value = false
  }
}

const handleMenuSelect = () => {
  if (isMobile.value) {
    sidebarDrawerVisible.value = false
  }
}

const toggleSidebar = () => {
  if (isMobile.value) return
  sidebarCollapsed.value = !sidebarCollapsed.value
}

const handleTouchStart = (event) => {
  if (!isMobile.value || event.touches.length !== 1) return
  const touch = event.touches[0]
  touchStartX.value = touch.clientX
  touchStartY.value = touch.clientY
}

const handleTouchEnd = (event) => {
  if (!isMobile.value || event.changedTouches.length !== 1) return
  const touch = event.changedTouches[0]
  const deltaX = touch.clientX - touchStartX.value
  const deltaY = touch.clientY - touchStartY.value
  if (Math.abs(deltaX) < 80 || Math.abs(deltaX) < Math.abs(deltaY)) {
    return
  }
  if (deltaX > 0 && touchStartX.value <= 60) {
    sidebarDrawerVisible.value = true
  } else if (deltaX < 0) {
    sidebarDrawerVisible.value = false
  }
}

watch(
  () => route.path,
  () => {
    if (isMobile.value) {
      sidebarDrawerVisible.value = false
    }
  }
)

const formatDateTime = (value) => {
  if (!value) return ''
  return dayjs(value).format('YYYY-MM-DD HH:mm')
}

</script>

<style scoped lang="scss">
.layout-container {
  width: 100%;
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  row-gap: 8px;
  padding: 8px 20px;
  background: linear-gradient(135deg, #52c41a 0%, #389e0d 100%);
  color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  min-width: 0;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
    min-width: 0;
    flex: 1 1 auto;

    .menu-toggle {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      border-radius: 8px;
      border: none;
      margin-right: 6px;
      background: rgba(255, 255, 255, 0.2);
      color: #fff;
      cursor: pointer;
    }

    .logo-icon {
      font-size: 32px;
    }

    .logo-text {
      font-size: 20px;
      font-weight: bold;
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 18px;
    flex-shrink: 0;
    flex-wrap: wrap;

    .notification-wrapper {
      position: relative;
      display: flex;
      align-items: center;
      border-radius: 20px;
      background: rgba(255, 255, 255, 0.18);
      transition: all 0.3s ease;

      &.has-unread {
        background: rgba(255, 255, 255, 0.28);
        box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.4);

        .notification-trigger {
          color: #fff5f0;
        }

        .el-badge__content.is-fixed {
          background-color: #ff4d4f;
        }
      }

      .notification-trigger {
        display: flex;
        align-items: center;
        gap: 6px;
        padding: 6px 12px;
        border: none;
        background: transparent;
        color: #f0f5ff;
        cursor: pointer;
        font-size: 14px;
      }

      .notification-text {
        white-space: nowrap;
      }
    }

    .username {
      font-size: 14px;
      max-width: 140px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .avatar-dropdown {
      :deep(.avatar-trigger) {
        cursor: pointer;
        border: 2px solid transparent;
        transition: all 0.3s;
        background: rgba(255, 255, 255, 0.08);

        &:hover {
          border-color: rgba(255, 255, 255, 0.85);
          background: rgba(255, 255, 255, 0.2);
          box-shadow: 0 0 12px rgba(255, 255, 255, 0.35);
        }
      }
    }
  }
}

.main-container {
  flex: 1;
  overflow: hidden;
  /* 允许内部 flex 子项在窗口缩放时正确收缩，避免溢出导致布局错乱 */
  min-width: 0;
}

.sidebar {
  background: #fff;
  border-right: 1px solid #e0e0e0;
  overflow-y: auto;
  transition: width 0.2s ease;

  :deep(.patient-menu) {
    border-right: none;
  }
}

.content {
  background: #f5f5f5;
  overflow-y: auto;
  padding: 20px;
  /* 关键修复：在 flex 布局中允许内容区收缩，避免在改变窗口大小时控件被挤压错位 */
  min-width: 0;
  flex: 1 1 auto;
}

:deep(.el-menu-item) {
  &.is-active {
    background: linear-gradient(90deg, #52c41a 0%, #389e0d 100%);
    color: white !important;

    .el-icon {
      color: white !important;
    }
  }
}

:deep(.el-sub-menu__title:hover) {
  background-color: #f0f9ff !important;
}

:deep(.el-sub-menu.is-active .el-sub-menu__title) {
  color: #52c41a !important;
}

.notification-popover {
  padding: 12px 0 0 !important;
}

.notification-header {
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

.notification-list {
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

.notification-empty {
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

.chat-float-button {
  position: fixed;
  right: 24px;
  bottom: 24px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #52c41a 0%, #389e0d 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(82, 196, 26, 0.4);
  transition: all 0.3s ease;
  z-index: 1000;

  &:hover {
    transform: scale(1.1);
    box-shadow: 0 6px 16px rgba(82, 196, 26, 0.5);
  }

  &:active {
    transform: scale(0.95);
  }
}

@media (max-width: 991px) {
  .header {
    padding: 8px 12px;
  }

  .header-right {
    gap: 10px;

    .username {
      max-width: 100px;
    }
  }

  .header-left .logo-text {
    font-size: 15px;
    max-width: min(52vw, 220px);
  }

  .content {
    padding: 12px;
  }

  .chat-float-button {
    right: 16px;
    bottom: 16px;
    width: 48px;
    height: 48px;
  }
}

.sidebar-drawer {
  :deep(.el-drawer__body) {
    padding: 0;
  }
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  font-weight: 600;
  font-size: 16px;
}
</style>
