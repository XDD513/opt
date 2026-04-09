<template>
  <el-empty
    :image="image"
    :image-size="imageSize"
    class="app-empty-state"
  >
    <template #description>
      <div class="empty-description">
        <p>{{ description }}</p>
        <small v-if="tip">{{ tip }}</small>
      </div>
    </template>
    <slot />
    <template #bottom>
      <slot name="bottom" />
    </template>
  </el-empty>
</template>

<script setup>
defineProps({
  description: {
    type: String,
    default: '暂无内容'
  },
  tip: {
    type: String,
    default: ''
  },
  image: {
    type: String,
    default: ''
  },
  imageSize: {
    type: Number,
    default: 160
  }
})
</script>

<style scoped>
.app-empty-state {
  padding: 40px 0;
}

.empty-description {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: #666;
}

.empty-description p {
  font-size: 15px;
  margin: 0;
  font-weight: 500;
}

.empty-description small {
  font-size: 12px;
  color: #909399;
}
</style>


