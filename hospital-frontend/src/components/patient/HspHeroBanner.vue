<template>
  <el-card class="hsp-hero-banner" shadow="never">
    <div class="hero-content">
      <div class="hero-left">
        <h1>欢迎使用{{ systemName }}，{{ userName }}！</h1>
        <p>{{ currentDate }} · {{ healthTip }}</p>
        <div class="action-buttons">
          <el-button type="success" size="large" @click="goToConstitutionHistory">
            <el-icon>
              <Document />
            </el-icon>
            查看测试历史
          </el-button>
          <el-button type="primary" size="large" plain @click="goToRecipes">
            <el-icon>
              <Food />
            </el-icon>
            浏览药膳推荐
          </el-button>
        </div>
      </div>
      <div class="hero-right" v-if="!isMobile">
        <el-icon :size="120" color="#52c41a">
          <Van />
        </el-icon>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Document, Van, Food } from '@element-plus/icons-vue'
import dayjs from 'dayjs'

const props = defineProps({
  systemName: {
    type: String,
    default: '中医体质辨识系统'
  },
  userName: {
    type: String,
    required: true
  }
})

const router = useRouter()

// 当前日期
const currentDate = computed(() => {
  const weekDays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  const dayOfWeek = dayjs().day()
  return dayjs().format('YYYY年MM月DD日') + ' ' + weekDays[dayOfWeek]
})

// 养生小贴士
const healthTips = [
  '春养肝，夏养心，秋养肺，冬养肾',
  '早睡早起，顺应自然',
  '饮食有节，起居有常',
  '心平气和，情志调畅',
  '适度运动，强身健体'
]

const healthTip = computed(() => {
  const index = dayjs().day()
  return healthTips[index % healthTips.length]
})

// 响应式检测
const isMobile = computed(() => {
  return window.innerWidth < 768
})

// 跳转测试历史
const goToConstitutionHistory = () => {
  router.push('/patient/constitution/history')
}

// 跳转药膳列表
const goToRecipes = () => {
  router.push('/patient/recipe')
}
</script>

<style scoped lang="scss">
@import '@/styles/home-variables.scss';

.hsp-hero-banner {
  margin-bottom: $spacing-lg;
  background: linear-gradient(135deg, #f0f9ff 0%, #e6f7ff 100%);

  .hero-content {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: $spacing-lg;

    .hero-left {
      flex: 1;

      h1 {
        font-size: 28px;
        color: $text-primary;
        margin-bottom: $spacing-sm;
        font-weight: 600;
      }

      p {
        color: $primary-color;
        margin-bottom: $spacing-lg;
        font-size: 16px;
        font-weight: 500;
      }

      .action-buttons {
        display: flex;
        gap: $spacing-md;
        flex-wrap: wrap;
      }
    }

    .hero-right {
      opacity: 0.2;
    }
  }
}

// 响应式设计
@media (max-width: $breakpoint-sm) {
  .hsp-hero-banner {
    .hero-content {
      .hero-left {
        h1 {
          font-size: 22px;
        }

        .action-buttons {
          flex-direction: column;

          .el-button {
            width: 100%;
          }
        }
      }

      .hero-right {
        display: none;
      }
    }
  }
}
</style>

