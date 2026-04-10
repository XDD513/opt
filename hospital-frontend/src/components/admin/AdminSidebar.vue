<template>
  <div class="admin-sidebar" :class="{ collapsed: !sidebarOpen }">
    <!-- Logo Area -->
    <div class="sidebar-logo">
      <div class="logo-content">
        <div class="logo-icon">
          <el-icon :size="14">
            <OfficeBuilding />
          </el-icon>
        </div>
        <span class="logo-text">管理权限系统</span>
      </div>
    </div>

    <!-- Menu Items -->
    <div class="sidebar-menu scrollbar-hide">
      <!-- 首页 -->
      <div 
        class="menu-item" 
        :class="{ active: activeKey === 'home' }"
        @click="handleMenuClick('home', '首页')"
      >
        <el-icon class="menu-icon"><House /></el-icon>
        <span class="menu-title">首页</span>
      </div>

      <!-- 用户管理 (可折叠) -->
      <div class="menu-group">
        <div 
          class="menu-item has-submenu" 
          :class="{ active: isUserActive }"
          @click="toggleUserMenu"
        >
          <el-icon class="menu-icon"><UserFilled /></el-icon>
          <span class="menu-title">用户管理</span>
          <el-icon class="submenu-arrow" :class="{ rotated: userOpen }">
            <ArrowRight />
          </el-icon>
        </div>
        
        <!-- 子菜单 -->
        <div class="submenu" :class="{ open: userOpen }">
          <div 
            class="submenu-item" 
            :class="{ active: activeKey === 'role' }"
            @click="handleMenuClick('role', '角色管理')"
          >
            <el-icon class="submenu-icon"><UserFilled /></el-icon>
            <span class="submenu-title">角色管理</span>
          </div>
        </div>
      </div>

      <!-- 系统管理 (可折叠) -->
      <div class="menu-group">
        <div 
          class="menu-item has-submenu" 
          :class="{ active: isSystemActive }"
          @click="toggleSystemMenu"
        >
          <el-icon class="menu-icon"><Setting /></el-icon>
          <span class="menu-title">系统管理</span>
          <el-icon class="submenu-arrow" :class="{ rotated: systemOpen }">
            <ArrowRight />
          </el-icon>
        </div>
        
        <!-- 子菜单 -->
        <div class="submenu" :class="{ open: systemOpen }">
          <div 
            class="submenu-item" 
            :class="{ active: activeKey === 'settings' }"
            @click="handleMenuClick('settings', '系统设置')"
          >
            <el-icon class="submenu-icon"><Setting /></el-icon>
            <span class="submenu-title">系统设置</span>
          </div>
          <div 
            class="submenu-item" 
            :class="{ active: activeKey === 'settingsTest' }"
            @click="handleMenuClick('settingsTest', '设置生效检测')"
          >
            <el-icon class="submenu-icon"><Tools /></el-icon>
            <span class="submenu-title">设置生效检测</span>
          </div>
          <div 
            class="submenu-item" 
            :class="{ active: activeKey === 'statistics' }"
            @click="handleMenuClick('statistics', '数据统计')"
          >
            <el-icon class="submenu-icon"><TrendCharts /></el-icon>
            <span class="submenu-title">数据统计</span>
          </div>
          <div 
            class="submenu-item" 
            :class="{ active: activeKey === 'logs' }"
            @click="handleMenuClick('logs', '操作日志')"
          >
            <el-icon class="submenu-icon"><Tickets /></el-icon>
            <span class="submenu-title">操作日志</span>
          </div>
        </div>
      </div>

      <!-- 社区管理 (可折叠) -->
      <div class="menu-group">
        <div
          class="menu-item has-submenu"
          :class="{ active: isCommunityActive }"
          @click="toggleCommunityMenu"
        >
          <el-icon class="menu-icon"><Collection /></el-icon>
          <span class="menu-title">社区管理</span>
          <el-icon class="submenu-arrow" :class="{ rotated: communityOpen }">
            <ArrowRight />
          </el-icon>
        </div>

        <div class="submenu" :class="{ open: communityOpen }">
          <div
            class="submenu-item"
            :class="{ active: activeKey === 'articleReview' }"
            @click="handleMenuClick('articleReview', '文章审核')"
          >
            <el-icon class="submenu-icon"><DocumentChecked /></el-icon>
            <span class="submenu-title">文章审核</span>
          </div>
          <div
            class="submenu-item"
            :class="{ active: activeKey === 'articleManage' }"
            @click="handleMenuClick('articleManage', '文章管理')"
          >
            <el-icon class="submenu-icon"><Document /></el-icon>
            <span class="submenu-title">文章管理</span>
          </div>
        </div>
      </div>

      <!-- 智能对话 -->
      <div 
        class="menu-item" 
        :class="{ active: activeKey === 'dialogue' }"
        @click="handleMenuClick('dialogue', '智能对话')"
      >
        <el-icon class="menu-icon"><ChatLineRound /></el-icon>
        <span class="menu-title">智能对话</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { 
  House, Setting, UserFilled, ChatLineRound,
  OfficeBuilding, ArrowRight,
  Tools, TrendCharts, Tickets,
  Collection, Document, DocumentChecked
} from '@element-plus/icons-vue'

const props = defineProps({
  activeKey: {
    type: String,
    default: 'home'
  },
  sidebarOpen: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['menu-click'])

const userOpen = ref(false)
const systemOpen = ref(false)
const communityOpen = ref(false)

const isUserActive = computed(() => {
  return props.activeKey === 'role'
})

const isSystemActive = computed(() => {
  return ['menu', 'settings', 'settingsTest', 'statistics', 'logs'].includes(props.activeKey)
})

const isCommunityActive = computed(() => {
  return ['articleReview', 'articleManage'].includes(props.activeKey)
})

const toggleUserMenu = () => {
  userOpen.value = !userOpen.value
}

const toggleSystemMenu = () => {
  systemOpen.value = !systemOpen.value
}

const toggleCommunityMenu = () => {
  communityOpen.value = !communityOpen.value
}

const handleMenuClick = (key, title) => {
  emit('menu-click', key, title)
}
</script>

<style scoped lang="scss">
.admin-sidebar {
  width: 210px;
  background: #304156;
  color: white;
  display: flex;
  flex-direction: column;
  height: 100vh;
  flex-shrink: 0;
  transition: width 0.35s cubic-bezier(0.4, 0, 0.2, 1);

  &.collapsed {
    width: 64px;

    .logo-text {
      opacity: 0;
      width: 0;
      overflow: hidden;
      transition: opacity 0.3s ease-in-out, width 0.3s ease-in-out;
    }

    .menu-title {
      opacity: 0;
      width: 0;
      overflow: hidden;
      transition: opacity 0.3s ease-in-out, width 0.3s ease-in-out;
    }

    .submenu-arrow {
      opacity: 0;
      width: 0;
      overflow: hidden;
      transition: opacity 0.3s ease-in-out, width 0.3s ease-in-out;
    }

    .submenu {
      max-height: 0 !important;
      opacity: 0;
      transition: max-height 0.35s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.3s ease-in-out;
    }
  }

  .logo-text {
    opacity: 1;
    width: auto;
    transition: opacity 0.35s ease-in-out 0.1s, width 0.35s ease-in-out 0.1s;
  }

  .menu-title {
    opacity: 1;
    width: auto;
    transition: opacity 0.35s ease-in-out 0.1s, width 0.35s ease-in-out 0.1s;
  }

  .submenu-arrow {
    opacity: 1;
    width: auto;
    transition: opacity 0.35s ease-in-out 0.1s, width 0.35s ease-in-out 0.1s, transform 0.3s ease-in-out;
  }

  .sidebar-logo {
    height: 50px;
    background: #2b2f3a;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: bold;
    font-size: 16px;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
    z-index: 10;
    transition: all 0.3s ease-in-out;

    .logo-content {
      display: flex;
      align-items: center;
      gap: 8px;
      transition: gap 0.3s ease-in-out;

      .logo-icon {
        width: 24px;
        height: 24px;
        background: #10b981;
        border-radius: 4px;
        display: flex;
        align-items: center;
        justify-content: center;
        color: white;
        flex-shrink: 0;
        transition: all 0.3s ease-in-out;
      }

      .logo-text {
        color: white;
        font-size: 16px;
        white-space: nowrap;
      }
    }
  }

  .sidebar-menu {
    flex: 1;
    overflow-y: auto;
    padding: 8px 0;

    .menu-item {
      padding: 12px 16px;
      display: flex;
      align-items: center;
      cursor: pointer;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      color: rgb(191, 203, 217);
      position: relative;

      &:hover {
        background: #263445;
        color: white;
      }

      &.active {
        color: #409EFF;
        background: #001528;

        .menu-icon {
          color: #409EFF;
        }
      }

      .menu-icon {
        margin-right: 12px;
        font-size: 18px;
        flex-shrink: 0;
        transition: all 0.3s ease-in-out;
      }

      .menu-title {
        font-size: 14px;
        flex: 1;
        white-space: nowrap;
      }

      &.has-submenu {
        .submenu-arrow {
          font-size: 14px;
          margin-left: auto;
          flex-shrink: 0;
          transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.3s ease-in-out;

          &.rotated {
            transform: rotate(90deg);
          }
        }
      }
    }

    .menu-group {
      .submenu {
        background: #1f2d3d;
        max-height: 0;
        overflow: hidden;
        opacity: 0;
        transition: max-height 0.35s cubic-bezier(0.4, 0, 0.2, 1), 
                    opacity 0.3s ease-in-out,
                    padding 0.3s ease-in-out;

        &.open {
          max-height: 500px;
          opacity: 1;
        }

        .submenu-item {
          padding: 12px 48px;
          display: flex;
          align-items: center;
          cursor: pointer;
          transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
          color: rgb(191, 203, 217);
          font-size: 14px;
          border-left: 2px solid transparent;
          transform: translateX(0);
          opacity: 1;

          &:hover {
            background: #001528;
            color: white;
            transform: translateX(4px);
          }

          &.active {
            color: #409EFF;
            background: #001528;
            border-left-color: #409EFF;
            font-weight: 500;
          }

          .submenu-icon {
            margin-right: 12px;
            font-size: 16px;
            flex-shrink: 0;
            transition: all 0.3s ease-in-out;
          }

          .submenu-title {
            font-weight: 500;
            white-space: nowrap;
          }
        }
      }
    }
  }
}

.scrollbar-hide {
  &::-webkit-scrollbar {
    display: none;
  }
  -ms-overflow-style: none;
  scrollbar-width: none;
}
</style>

