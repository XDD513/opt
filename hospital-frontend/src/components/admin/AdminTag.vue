<template>
  <span class="admin-tag" :class="[`tag-${type}`, { closable }]">
    {{ text }}
    <el-icon 
      v-if="closable" 
      class="tag-close"
      :size="12"
      @click.stop="$emit('close')"
    >
      <Close />
    </el-icon>
  </span>
</template>

<script setup>
import { Close } from '@element-plus/icons-vue'

defineProps({
  type: {
    type: String,
    default: 'blue',
    validator: (value) => ['blue', 'red'].includes(value)
  },
  text: {
    type: String,
    required: true
  },
  closable: {
    type: Boolean,
    default: true
  }
})

defineEmits(['close'])
</script>

<style scoped lang="scss">
.admin-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 2px;
  font-weight: 500;

  &.tag-blue {
    background: rgba(64, 158, 255, 0.1);
    color: #409EFF;
    border: 1px solid rgba(64, 158, 255, 0.2);
  }

  &.tag-red {
    background: rgba(245, 108, 108, 0.1);
    color: #F56C6C;
    border: 1px solid rgba(245, 108, 108, 0.2);
  }

  .tag-close {
    cursor: pointer;
    transition: opacity 0.2s;

    &:hover {
      opacity: 0.7;
    }
  }
}
</style>
















