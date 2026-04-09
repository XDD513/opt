<template>
  <div class="admin-tags-view">
    <div class="tags-container scrollbar-hide">
      <div 
        v-for="tab in tabs" 
        :key="tab.key"
        class="tag-item"
        :class="{ active: tab.key === activeKey }"
        @click="handleTabClick(tab.key)"
      >
        <span v-if="tab.key === activeKey" class="tag-dot"></span>
        <span class="tag-title">{{ tab.title }}</span>
        <span 
          v-if="tab.closable"
          class="tag-close"
          @click.stop="handleTabClose(tab.key)"
        >
          <el-icon :size="10">
            <Close />
          </el-icon>
        </span>
      </div>
    </div>

    <div class="tags-actions" @click="handleCloseAll">
      <div class="close-all-icon">
        <el-icon :size="10">
          <Close />
        </el-icon>
      </div>
      <span>关闭</span>
    </div>
  </div>
</template>

<script setup>
import { Close } from '@element-plus/icons-vue'

const props = defineProps({
  tabs: {
    type: Array,
    default: () => []
  },
  activeKey: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['tab-click', 'tab-close', 'close-all'])

const handleTabClick = (key) => {
  emit('tab-click', key)
}

const handleTabClose = (key) => {
  emit('tab-close', key)
}

const handleCloseAll = () => {
  emit('close-all')
}
</script>

<style scoped lang="scss">
.admin-tags-view {
  height: 34px;
  background: white;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  padding: 0 16px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  flex-shrink: 0;

  .tags-container {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 8px;
    overflow-x: auto;
    white-space: nowrap;

    .tag-item {
      display: inline-flex;
      align-items: center;
      padding: 4px 12px;
      border: 1px solid #e5e7eb;
      border-radius: 3px;
      cursor: pointer;
      transition: all 0.2s;
      user-select: none;
      font-size: 12px;
      background: white;
      color: #606266;

      &:hover {
        color: #303133;
      }

      &.active {
        background: #009688;
        color: white;
        border-color: #009688;

        .tag-dot {
          width: 8px;
          height: 8px;
          border-radius: 50%;
          background: white;
          margin-right: 8px;
        }
      }

      .tag-title {
        white-space: nowrap;
      }

      .tag-close {
        margin-left: 8px;
        padding: 2px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: all 0.2s;

        &:hover {
          background: rgba(0, 0, 0, 0.2);
        }
      }
    }
  }

  .tags-actions {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: #009688;
    cursor: pointer;
    padding: 4px 8px;
    border-radius: 4px;
    transition: all 0.2s;
    white-space: nowrap;
    flex-shrink: 0;

    &:hover {
      text-decoration: underline;
      background: rgba(0, 150, 136, 0.1);
    }

    .close-all-icon {
      width: 16px;
      height: 16px;
      border: 1px solid #009688;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
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















