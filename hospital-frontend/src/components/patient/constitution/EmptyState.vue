<template>
  <div class="empty-state">
    <el-empty :description="description">
      <template #image>
        <el-icon :size="80" style="color: #cbd5e1;">
          <Document v-if="type === 'no-questions'" />
          <Warning v-else-if="type === 'error'" />
          <InfoFilled v-else />
        </el-icon>
      </template>
      <template #description>
        <p class="empty-title">{{ title }}</p>
        <p class="empty-subtitle" v-if="subtitle">{{ subtitle }}</p>
        <div class="empty-actions" v-if="showActions">
          <el-button 
            v-if="primaryAction" 
            type="primary" 
            size="small" 
            @click="handlePrimaryAction"
          >
            {{ primaryActionText }}
          </el-button>
          <el-button 
            v-if="secondaryAction" 
            size="small" 
            @click="handleSecondaryAction"
          >
            {{ secondaryActionText }}
          </el-button>
        </div>
      </template>
    </el-empty>
  </div>
</template>

<script setup>
import { Document, Warning, InfoFilled } from '@element-plus/icons-vue'

/**
 * @typedef {Object} Props
 * @property {string} type - 空状态类型：'no-questions' | 'error' | 'info'
 * @property {string} title - 标题
 * @property {string} subtitle - 副标题
 * @property {string} description - 描述（el-empty使用）
 * @property {boolean} showActions - 是否显示操作按钮
 * @property {boolean} primaryAction - 是否显示主要操作
 * @property {string} primaryActionText - 主要操作文本
 * @property {boolean} secondaryAction - 是否显示次要操作
 * @property {string} secondaryActionText - 次要操作文本
 */

const props = defineProps({
  type: {
    type: String,
    default: 'no-questions',
    validator: (value) => ['no-questions', 'error', 'info'].includes(value)
  },
  title: {
    type: String,
    default: '暂无数据'
  },
  subtitle: {
    type: String,
    default: ''
  },
  description: {
    type: String,
    default: ''
  },
  showActions: {
    type: Boolean,
    default: true
  },
  primaryAction: {
    type: Boolean,
    default: true
  },
  primaryActionText: {
    type: String,
    default: '返回我的预约'
  },
  secondaryAction: {
    type: Boolean,
    default: false
  },
  secondaryActionText: {
    type: String,
    default: '刷新'
  }
})

const emit = defineEmits(['primary-action', 'secondary-action'])

const handlePrimaryAction = () => {
  emit('primary-action')
}

const handleSecondaryAction = () => {
  emit('secondary-action')
}
</script>

<style scoped lang="scss">
.empty-state {
  padding: 60px 20px;
  text-align: center;
  
  .empty-title {
    color: #64748b;
    font-size: 14px;
    margin-bottom: 8px;
    font-weight: 500;
  }
  
  .empty-subtitle {
    color: #94a3b8;
    font-size: 12px;
    margin-bottom: 16px;
    line-height: 1.6;
  }
  
  .empty-actions {
    display: flex;
    gap: 12px;
    justify-content: center;
    margin-top: 16px;
  }
}
</style>

