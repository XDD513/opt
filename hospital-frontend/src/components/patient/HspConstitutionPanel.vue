<template>
  <el-card class="hsp-constitution-panel" shadow="never" v-if="constitution" v-animate-on-scroll>
    <template #header>
      <div class="card-header">
        <span>我的体质</span>
        <el-button text type="success" @click="goToHistory">
          查看详情 <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
    </template>
    <div class="constitution-content">
      <div class="constitution-main">
        <el-tag size="large" type="success" effect="dark">{{ constitution.type }}</el-tag>
        <p class="constitution-desc">{{ constitution.description }}</p>
        <div class="constitution-date">测试时间：{{ constitution.testDate }}</div>
      </div>
      <div class="constitution-suggestions" v-if="constitution.tips && constitution.tips.length > 0">
        <h4>养生建议</h4>
        <ul>
          <li v-for="(tip, index) in constitution.tips" :key="index">{{ tip }}</li>
        </ul>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'

const props = defineProps({
  constitution: {
    type: Object,
    default: null
  }
})

const router = useRouter()

const goToHistory = () => {
  const id = props.constitution?.id || props.constitution?.testHistoryId || props.constitution?.testId
  if (id) {
    router.push({ name: 'TestResult', params: { id } })
    return
  }
  // 兼容：如果没有测试记录ID，回退到历史列表页
  router.push('/patient/constitution/history')
}
</script>

<script>
// 滚动动画指令
export default {
  directives: {
    'animate-on-scroll': {
      mounted(el) {
        const observer = new IntersectionObserver(
          (entries) => {
            entries.forEach((entry) => {
              if (entry.isIntersecting) {
                entry.target.classList.add('animate-in')
                observer.unobserve(entry.target)
              }
            })
          },
          { threshold: 0.1 }
        )
        observer.observe(el)
      }
    }
  }
}
</script>

<style scoped lang="scss">
@import '@/styles/home-variables.scss';

.hsp-constitution-panel {
  margin-bottom: $spacing-lg;
  opacity: 0;
  transform: translateY(20px);

  &.animate-in {
    opacity: 1;
    transform: translateY(0);
    transition: all $transition-normal $ease-out;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: bold;
    font-size: 16px;
  }

  .constitution-content {
    .constitution-main {
      margin-bottom: $spacing-lg;
      padding: $spacing-lg;
      background: $primary-light;
      border-radius: 8px;
      border: 1px solid $primary-border;

      .constitution-desc {
        margin: $spacing-md 0;
        color: $text-primary;
        font-size: 15px;
        line-height: 1.6;
      }

      .constitution-date {
        color: $text-tertiary;
        font-size: 13px;
      }
    }

    .constitution-suggestions {
      h4 {
        margin: 0 0 $spacing-md 0;
        color: $text-primary;
        font-size: 15px;
      }

      ul {
        margin: 0;
        padding-left: $spacing-lg;

        li {
          color: $text-secondary;
          line-height: 2;
          font-size: 14px;
        }
      }
    }
  }
}
</style>

