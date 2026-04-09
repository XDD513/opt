<template>
  <div 
    class="admin-toggle-switch"
    :class="{ checked: checked }"
    @click="handleToggle"
  >
    <div class="switch-slider"></div>
  </div>
</template>

<script setup>
const props = defineProps({
  checked: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['change'])

const handleToggle = () => {
  emit('change', !props.checked)
}
</script>

<style scoped lang="scss">
.admin-toggle-switch {
  width: 40px;
  height: 20px;
  border-radius: 10px;
  background: #cbd5e1;
  position: relative;
  cursor: pointer;
  transition: background-color 0.2s;

  &.checked {
    background: #13ce66;
  }

  .switch-slider {
    position: absolute;
    top: 2px;
    left: 2px;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    background: white;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
    transition: transform 0.2s;
  }

  &.checked .switch-slider {
    transform: translateX(20px);
  }
}
</style>

















