<template>
  <button 
    class="admin-button" 
    :class="`button-${variant}`"
    @click="$emit('click')"
  >
    <slot />
  </button>
</template>

<script setup>
defineProps({
  variant: {
    type: String,
    default: 'primary',
    validator: (value) => ['primary', 'warning', 'danger'].includes(value)
  }
})

defineEmits(['click'])
</script>

<style scoped lang="scss">
.admin-button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 3px;
  font-size: 12px;
  color: white;
  border: none;
  cursor: pointer;
  transition: opacity 0.2s;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);

  &:hover {
    opacity: 0.9;
  }

  &.button-primary {
    background: #409EFF;
    border: 1px solid #409EFF;
  }

  &.button-warning {
    background: #E6A23C;
    border: 1px solid #E6A23C;
  }

  &.button-danger {
    background: #F56C6C;
    border: 1px solid #F56C6C;
  }
}
</style>

















