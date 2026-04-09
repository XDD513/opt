<template>
  <div class="admin-dashboard">
    <!-- 欢迎横幅 -->
    <el-card class="welcome-card" shadow="never">
      <div class="welcome-content">
        <div>
          <h1>管理后台</h1>
          <p>{{ currentDate }} | 系统运行正常</p>
        </div>
        <el-icon :size="100" color="#e6a23c"><DataAnalysis /></el-icon>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import dayjs from 'dayjs'

const currentDate = computed(() => {
  const weekDays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  const dayOfWeek = dayjs().day()
  return dayjs().format('YYYY年MM月DD日') + ' ' + weekDays[dayOfWeek]
})
</script>

<style scoped lang="scss">
.admin-dashboard {
  max-width: 1400px;
  margin: 0 auto;

  .welcome-card {
    margin-bottom: 20px;

    .welcome-content {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 20px;

      h1 {
        font-size: 32px;
        color: #333;
        margin-bottom: 10px;
      }

      p {
        color: #666;
        font-size: 16px;
      }
    }
  }

  h3 {
    margin: 0;
    font-size: 16px;
  }
}
</style>

