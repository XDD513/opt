<template>
  <div class="health-statistics-container">
    <div class="page-header">
      <h2>健康统计</h2>
      <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期"
        end-placeholder="结束日期" value-format="YYYY-MM-DD" @change="loadStatistics" />
    </div>

    <el-row :gutter="20">
      <!-- 统计卡片 -->
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-item">
            <div class="stat-icon" style="background: #409eff;">
              <el-icon>
                <TrendCharts />
              </el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ statistics.checkinDays || 0 }}</div>
              <div class="stat-label">打卡天数</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-item">
            <div class="stat-icon" style="background: #67c23a;">
              <el-icon>
                <Odometer />
              </el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ statistics.avgWeight || 0 }}</div>
              <div class="stat-label">平均体重(kg)</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-item">
            <div class="stat-icon" style="background: #e6a23c;">
              <el-icon>
                <Moon />
              </el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ statistics.avgSleepDuration || 0 }}</div>
              <div class="stat-label">平均睡眠(小时)</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-item">
            <div class="stat-icon" style="background: #f56c6c;">
              <el-icon>
                <Bicycle />
              </el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ statistics.avgExerciseDuration || 0 }}</div>
              <div class="stat-label">平均运动时长(分钟)</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表 -->
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :xs="24" :md="12">
        <el-card>
          <template #header>
            <span>体重趋势</span>
          </template>
          <div ref="weightChartRef" style="height: 300px;"></div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12">
        <el-card>
          <template #header>
            <span>睡眠趋势</span>
          </template>
          <div ref="sleepChartRef" style="height: 300px;"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :xs="24" :md="12">
        <el-card>
          <template #header>
            <span>运动统计</span>
          </template>
          <div ref="exerciseChartRef" style="height: 300px;"></div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12">
        <el-card>
          <template #header>
            <span>情绪分布</span>
          </template>
          <div ref="moodChartRef" style="height: 300px;"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import message from '@/plugins/message'
import { ref, onMounted, nextTick } from 'vue'

import { TrendCharts, Odometer, Moon, Bicycle } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import dayjs from 'dayjs'
import * as echarts from 'echarts'
import { getHealthStatistics } from '@/api/health'

const userStore = useUserStore()

const statistics = ref({})
const dateRange = ref([
  dayjs().subtract(30, 'day').format('YYYY-MM-DD'),
  dayjs().format('YYYY-MM-DD')
])

const weightChartRef = ref(null)
const sleepChartRef = ref(null)
const exerciseChartRef = ref(null)
const moodChartRef = ref(null)

let weightChart = null
let sleepChart = null
let exerciseChart = null
let moodChart = null

// 加载统计数据
const loadStatistics = async () => {
  const userId = userStore.userInfo?.id
  if (!userId) {
    message.warning('请先登录')
    return
  }

  try {
    const params = {
      userId: userId,
      startDate: dateRange.value[0],
      endDate: dateRange.value[1]
    }
    const res = await getHealthStatistics(params)
    if (res.code === 200) {
      statistics.value = res.data
      await nextTick()
      initCharts(res.data)
    }
  } catch (error) {

    message.error('加载统计数据失败')
  }
}

// 初始化图表
const initCharts = (data) => {
  initWeightChart(data.weightTrend || [])
  initSleepChart(data.sleepTrend || [])
  initExerciseChart(data.exerciseTrend || [])
  initMoodChart(data.moodDistribution || {})
}

// 体重趋势图
const initWeightChart = (data) => {
  if (!weightChartRef.value) return

  if (weightChart) {
    weightChart.dispose()
  }

  weightChart = echarts.init(weightChartRef.value)
  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const param = params[0]
        return `${param.axisValue}<br/>体重: ${param.value !== null && param.value !== undefined ? param.value + ' kg' : '无数据'}`
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data.map(item => item.date),
      axisLabel: {
        rotate: 45
      }
    },
    yAxis: {
      type: 'value',
      name: '体重(kg)',
      min: (value) => {
        return Math.max(0, value.min - 5)
      }
    },
    series: [{
      name: '体重',
      data: data.map(item => item.weight || null),
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      itemStyle: {
        color: '#409eff'
      },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.1)' }
          ]
        }
      }
    }]
  }
  weightChart.setOption(option)
}

// 睡眠趋势图
const initSleepChart = (data) => {
  if (!sleepChartRef.value) return

  if (sleepChart) {
    sleepChart.dispose()
  }

  sleepChart = echarts.init(sleepChartRef.value)
  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        let result = params[0].axisValue + '<br/>'
        params.forEach(param => {
          if (param.seriesName === '睡眠时长') {
            result += `${param.marker}${param.seriesName}: ${param.value !== null && param.value !== undefined ? param.value + ' 小时' : '无数据'}<br/>`
          } else {
            result += `${param.marker}${param.seriesName}: ${param.value !== null && param.value !== undefined ? param.value + ' 分' : '无数据'}<br/>`
          }
        })
        return result
      }
    },
    legend: {
      data: ['睡眠时长', '睡眠质量'],
      top: 10
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: true,
      data: data.map(item => item.date),
      axisLabel: {
        rotate: 45
      }
    },
    yAxis: [
      {
        type: 'value',
        name: '时长(小时)',
        position: 'left',
        min: 0
      },
      {
        type: 'value',
        name: '质量(分)',
        position: 'right',
        min: 0,
        max: 5
      }
    ],
    series: [
      {
        name: '睡眠时长',
        data: data.map(item => item.duration || null),
        type: 'bar',
        itemStyle: {
          color: '#67c23a'
        },
        barWidth: '50%'
      },
      {
        name: '睡眠质量',
        data: data.map(item => item.quality || null),
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        itemStyle: {
          color: '#e6a23c'
        }
      }
    ]
  }
  sleepChart.setOption(option)
}

// 运动统计图
const initExerciseChart = (data) => {
  if (!exerciseChartRef.value) return

  if (exerciseChart) {
    exerciseChart.dispose()
  }

  exerciseChart = echarts.init(exerciseChartRef.value)
  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const param = params[0]
        return `${param.axisValue}<br/>运动时长: ${param.value !== null && param.value !== undefined ? param.value + ' 分钟' : '无数据'}`
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: true,
      data: data.map(item => item.date),
      axisLabel: {
        rotate: 45
      }
    },
    yAxis: {
      type: 'value',
      name: '时长(分钟)',
      min: 0
    },
    series: [{
      name: '运动时长',
      data: data.map(item => item.duration || null),
      type: 'bar',
      itemStyle: {
        color: '#f56c6c'
      },
      barWidth: '50%'
    }]
  }
  exerciseChart.setOption(option)
}

// 情绪分布图
const initMoodChart = (data) => {
  if (!moodChartRef.value) return

  if (moodChart) {
    moodChart.dispose()
  }

  moodChart = echarts.init(moodChartRef.value)
  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left',
      top: 'middle'
    },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['60%', '50%'],
      avoidLabelOverlap: false,
      itemStyle: {
        borderRadius: 10,
        borderColor: '#fff',
        borderWidth: 2
      },
      label: {
        show: true,
        formatter: '{b}\n{d}%'
      },
      emphasis: {
        label: {
          show: true,
          fontSize: 16,
          fontWeight: 'bold'
        },
        itemStyle: {
          shadowBlur: 10,
          shadowOffsetX: 0,
          shadowColor: 'rgba(0, 0, 0, 0.5)'
        }
      },
      data: [
        { value: data.GOOD || 0, name: '很好', itemStyle: { color: '#67c23a' } },
        { value: data.NORMAL || 0, name: '一般', itemStyle: { color: '#e6a23c' } },
        { value: data.BAD || 0, name: '不好', itemStyle: { color: '#f56c6c' } }
      ]
    }]
  }
  moodChart.setOption(option)
}

// 窗口大小改变时重新渲染图表
const handleResize = () => {
  weightChart?.resize()
  sleepChart?.resize()
  exerciseChart?.resize()
  moodChart?.resize()
}

onMounted(() => {
  loadStatistics()
  window.addEventListener('resize', handleResize)
})
</script>

<style scoped>
.health-statistics-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 24px;
  color: #333;
  margin: 0;
}

.stat-card {
  margin-bottom: 20px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 15px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: white;
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #333;
  margin-bottom: 5px;
}

.stat-label {
  font-size: 14px;
  color: #999;
}
</style>
