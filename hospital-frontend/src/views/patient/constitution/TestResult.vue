<template>
  <div style="padding: 24px;">
    <el-empty description="正在跳转..." />
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

onMounted(() => {
  const id = route.params?.id
  if (id) {
    router.replace(`/patient/constitution/history/detail/${id}`)
  } else {
    router.replace({ path: '/patient/constitution/smart-test', query: route.query || {} })
  }
})
</script>

