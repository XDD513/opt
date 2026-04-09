<template>
  <el-card class="hsp-health-trend" shadow="never" v-animate-on-scroll>
    <template #header>
      <div class="card-header">
        <span>健康趋势（最近7天）</span>
        <el-button text type="success" @click="goToStatistics">
          查看详情 <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
    </template>
    <div ref="chartRef" class="chart-container" v-loading="loading"></div>
  </el-card>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import { getCheckinList } from '@/api/health'
import { useUserStore } from '@/stores/user'
import dayjs from 'dayjs'

const props = defineProps({
  userId: {
    type: Number,
    required: true
  }
})

const router = useRouter()
const userStore = useUserStore()
const chartRef = ref(null)
const loading = ref(false)
let chartInstance = null
let resizeHandler = null

// 动态导入 ECharts
const initChart = async () => {
  if (!chartRef.value) return

  try {
    loading.value = true
    
    // 动态导入 ECharts
    const echarts = await import('echarts')
    
    // 获取最近7天的打卡数据
    const endDate = dayjs().format('YYYY-MM-DD')
    const startDate = dayjs().subtract(6, 'day').format('YYYY-MM-DD')

    const res = await getCheckinList({
      userId: props.userId,
      startDate: startDate,
      endDate: endDate,
      pageNum: 1,
      pageSize: 100
    })

    if (res.code === 200) {
      chartInstance = echarts.init(chartRef.value)

      const checkinData = res.data?.records || res.data?.list || []

      // 生成最近7天的日期数组
      const dates = []
      for (let i = 6; i >= 0; i--) {
        dates.push(dayjs().subtract(i, 'day').format('YYYY-MM-DD'))
      }

      // 创建日期到数据的映射
      const dataMap = {}
      checkinData.forEach(item => {
        const dateKey = dayjs(item.checkinDate).format('YYYY-MM-DD')
        dataMap[dateKey] = item
      })

      // 提取各项数据
      const weights = dates.map(date => dataMap[date]?.weight || null)
      const sleepHours = dates.map(date => dataMap[date]?.sleepDuration || null)
      const exerciseMinutes = dates.map(date => dataMap[date]?.exerciseDuration || null)
      const moodScores = dates.map(date => dataMap[date]?.moodScore || null)

      const option = {
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'cross'
          }
        },
        legend: {
          data: ['体重(kg)', '睡眠(小时)', '运动(分钟)', '心情(分)'],
          bottom: 0
        },
        grid: {
          left: '3%',
          right: '4%',
          bottom: '15%',
          containLabel: true
        },
        xAxis: {
          type: 'category',
          boundaryGap: false,
          data: dates.map(date => dayjs(date).format('MM-DD')),
          axisLabel: {
            color: '#666'
          }
        },
        yAxis: {
          type: 'value',
          axisLabel: {
            color: '#666'
          }
        },
        series: [
          {
            name: '体重(kg)',
            type: 'line',
            data: weights,
            smooth: true,
            itemStyle: { color: '#1890ff' },
            areaStyle: {
              color: {
                type: 'linear',
                x: 0, y: 0, x2: 0, y2: 1,
                colorStops: [
                  { offset: 0, color: 'rgba(24, 144, 255, 0.3)' },
                  { offset: 1, color: 'rgba(24, 144, 255, 0.05)' }
                ]
              }
            }
          },
          {
            name: '睡眠(小时)',
            type: 'line',
            data: sleepHours,
            smooth: true,
            itemStyle: { color: '#52c41a' }
          },
          {
            name: '运动(分钟)',
            type: 'line',
            data: exerciseMinutes,
            smooth: true,
            itemStyle: { color: '#faad14' }
          },
          {
            name: '心情(分)',
            type: 'line',
            data: moodScores,
            smooth: true,
            itemStyle: { color: '#f5222d' }
          }
        ]
      }

      chartInstance.setOption(option)

      // 响应式调整
      resizeHandler = () => {
        chartInstance?.resize()
      }
      window.addEventListener('resize', resizeHandler)
    }
  } catch (error) {
    console.error('初始化健康趋势图表失败:', error)
  } finally {
    loading.value = false
  }
}

const goToStatistics = () => {
  router.push('/patient/health/statistics')
}

onMounted(async () => {
  await nextTick()
  // 延迟加载图表，等待元素进入视口
  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting && !chartInstance) {
          initChart()
          observer.disconnect()
        }
      })
    },
    { threshold: 0.1 }
  )
  
  if (chartRef.value) {
    observer.observe(chartRef.value)
  }
})

onBeforeUnmount(() => {
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
  }
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
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

.hsp-health-trend {
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

  .chart-container {
    width: 100%;
    height: 300px;
  }
}
</style>

