<template>
  <el-row :gutter="20" class="hsp-stat-grid">
    <el-col :xs="24" :sm="12" :md="8" v-for="stat in stats" :key="stat.key">
      <div 
        class="stat-card" 
        :class="stat.color"
        @click="handleClick(stat.route)"
        v-animate-on-scroll
      >
        <div class="stat-content">
          <div class="stat-icon">
            <el-icon>
              <component :is="stat.icon" />
            </el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-number">{{ stat.value || 0 }}</div>
            <div class="stat-label">{{ stat.label }}</div>
          </div>
        </div>
      </div>
    </el-col>
  </el-row>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Document, Food, CircleCheck } from '@element-plus/icons-vue'

const props = defineProps({
  stats: {
    type: Object,
    required: true,
    default: () => ({
      testCount: 0,
      recipeCount: 0,
      checkinDays: 0
    })
  }
})

const router = useRouter()

// 统计卡片配置
const statConfigs = [
  {
    key: 'testCount',
    label: '体质测试次数',
    icon: Document,
    color: 'green',
    route: '/patient/constitution/history'
  },
  {
    key: 'recipeCount',
    label: '收藏药膳',
    icon: Food,
    color: 'orange',
    route: '/patient/recipe/my'
  },
  {
    key: 'checkinDays',
    label: '连续打卡天数',
    icon: CircleCheck,
    color: 'red',
    route: '/patient/health/checkin'
  }
]

// 处理点击
const handleClick = (route) => {
  if (route) {
    router.push(route)
  }
}

// 计算显示的统计数据
const stats = computed(() => {
  return statConfigs.map(config => ({
    ...config,
    value: props.stats[config.key] || 0
  }))
})
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

.hsp-stat-grid {
  margin-bottom: $spacing-lg;
}

.stat-card {
  padding: $spacing-xl;
  border-radius: 12px;
  cursor: pointer;
  transition: all $transition-normal;
  box-shadow: $elevation-1;
  opacity: 0;
  transform: translateY(20px);

  &.animate-in {
    opacity: 1;
    transform: translateY(0);
    transition: all $transition-normal $ease-out;
  }

  &:hover {
    transform: translateY(-5px);
    box-shadow: $elevation-2;
  }

  &.green {
    background: linear-gradient(135deg, #f6ffed 0%, #d9f7be 100%);
    border: 1px solid #b7eb8f;

    .stat-icon {
      background: $stat-green;
    }
  }

  &.orange {
    background: linear-gradient(135deg, #fff7e6 0%, #ffe7ba 100%);
    border: 1px solid #ffd591;

    .stat-icon {
      background: $stat-orange;
    }
  }

  &.blue {
    background: linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%);
    border: 1px solid #91d5ff;

    .stat-icon {
      background: $stat-blue;
    }
  }

  &.red {
    background: linear-gradient(135deg, #fff1f0 0%, #ffccc7 100%);
    border: 1px solid #ffa39e;

    .stat-icon {
      background: $stat-red;
    }
  }

  .stat-content {
    display: flex;
    align-items: center;
    gap: $spacing-lg;

    .stat-icon {
      width: 60px;
      height: 60px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      font-size: 28px;
      flex-shrink: 0;
    }

    .stat-info {
      flex: 1;

      .stat-number {
        font-size: 32px;
        font-weight: bold;
        color: $text-primary;
        margin-bottom: 5px;
        line-height: 1;
      }

      .stat-label {
        color: $text-secondary;
        font-size: 14px;
      }
    }
  }
}

@media (max-width: $breakpoint-sm) {
  .stat-card {
    .stat-content {
      .stat-info {
        .stat-number {
          font-size: 24px;
        }
      }
    }
  }
}
</style>

