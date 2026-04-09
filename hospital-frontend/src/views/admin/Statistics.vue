<template>
  <div class="statistics-container">
    <!-- 概览统计 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card primary">
          <div class="stat-icon">
            <el-icon>
              <UserFilled />
            </el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-title">体质测试覆盖用户</div>
            <div class="stat-value">{{ stats.totalPatients }}</div>
            <div class="stat-suffix">人</div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card success">
          <div class="stat-icon">
            <el-icon>
              <CircleCheck />
            </el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-title">完成体质测试</div>
            <div class="stat-value">{{ stats.testedPatients }}</div>
            <div class="stat-suffix">人</div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card warning">
          <div class="stat-icon">
            <el-icon>
              <WarningFilled />
            </el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-title">未完成</div>
            <div class="stat-value">{{ notTestedPatients }}</div>
            <div class="stat-suffix">人</div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card danger">
          <div class="stat-icon">
            <el-icon>
              <DataAnalysis />
            </el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-title">完成率</div>
            <div class="stat-value">{{ completionRateText }}</div>
            <div class="stat-suffix">%</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="stats-row" style="margin-top: 20px">
      <el-col :xs="24" :sm="12" :md="6">
        <div class="stat-card primary">
          <div class="stat-icon">
            <el-icon>
              <DataAnalysis />
            </el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-title">总测试次数</div>
            <div class="stat-value">{{ stats.totalTestRecords || 0 }}</div>
            <div class="stat-suffix">次</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <!-- 主要体质分布（饼图） -->
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <h3>主要体质分布（按体质代码统计）</h3>
              <el-tag type="info" size="small">主体质</el-tag>
            </div>
          </template>
          <div ref="pieChartRef" style="width: 100%; height: 360px"></div>
        </el-card>
      </el-col>

      <!-- Top 5 主要体质 -->
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <h3>Top 5 主要体质</h3>
              <el-tag type="primary" size="small">Top5</el-tag>
            </div>
          </template>
          <div ref="topBarChartRef" style="width: 100%; height: 360px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近30天趋势 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :xs="24">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <h3>最近30天体质测试趋势</h3>
              <el-tag type="info" size="small">按测试日期</el-tag>
            </div>
          </template>
          <div ref="trendChartRef" style="width: 100%; height: 380px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <h3>推荐穴位 Top 10</h3>
              <el-tag type="primary" size="small">按出现次数</el-tag>
            </div>
          </template>
          <div v-if="stats.topAcupoints && stats.topAcupoints.length" class="top-list">
            <div v-for="p in stats.topAcupoints" :key="p.item" class="top-row">
              <span class="top-name">{{ p.item }}</span>
              <span class="top-count">{{ p.count }} 次</span>
            </div>
          </div>
          <el-empty v-else description="暂无数据" />
        </el-card>
      </el-col>

      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <h3>宜食 Top 10</h3>
              <el-tag type="success" size="small">按出现次数</el-tag>
            </div>
          </template>
          <div v-if="stats.topDietRecommend && stats.topDietRecommend.length" class="top-list">
            <div v-for="p in stats.topDietRecommend" :key="p.item" class="top-row">
              <span class="top-name">{{ p.item }}</span>
              <span class="top-count">{{ p.count }} 次</span>
            </div>
          </div>
          <el-empty v-else description="暂无数据" />
        </el-card>
      </el-col>

      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <h3>忌食 Top 10</h3>
              <el-tag type="danger" size="small">按出现次数</el-tag>
            </div>
          </template>
          <div v-if="stats.topDietAvoid && stats.topDietAvoid.length" class="top-list">
            <div v-for="p in stats.topDietAvoid" :key="p.item" class="top-row">
              <span class="top-name">{{ p.item }}</span>
              <span class="top-count">{{ p.count }} 次</span>
            </div>
          </div>
          <el-empty v-else description="暂无数据" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import message from '@/plugins/message'
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { getAdminUserTestStats } from '@/api/statistics'
import * as echarts from 'echarts'

import { UserFilled, CircleCheck, WarningFilled, DataAnalysis } from '@element-plus/icons-vue'
import { CONSTITUTION_TYPE_MAP } from '@/utils/constitution'

const stats = reactive({
  totalPatients: 0,
  testedPatients: 0,
  totalTestRecords: 0,
  completionRate: 0,
  primaryConstitutionCounts: {},
  last30DaysTrend: [],
  topAcupoints: [],
  topDietRecommend: [],
  topDietAvoid: []
})

const pieChartRef = ref(null)
const topBarChartRef = ref(null)
const trendChartRef = ref(null)

let pieChart = null
let topBarChart = null
let trendChart = null

const notTestedPatients = computed(() => {
  return Math.max(0, (stats.totalPatients || 0) - (stats.testedPatients || 0))
})

const completionRateText = computed(() => {
  const rate = Number(stats.completionRate || 0)
  return rate.toFixed(1)
})

const formatConstitutionName = (code) => {
  return CONSTITUTION_TYPE_MAP[code] || code || '未知'
}

const buildPieData = () => {
  const counts = stats.primaryConstitutionCounts || {}
  const entries = Object.entries(counts).filter(([, v]) => typeof v === 'number' && v > 0)
  // 兼容后端可能返回的非 number 情况
  const normalized = entries.map(([code, v]) => ({ code, value: Number(v) || 0 })).filter(i => i.value > 0)
  return normalized.map(i => ({
    name: formatConstitutionName(i.code),
    value: i.value,
    code: i.code
  }))
}

const updateCharts = () => {
  nextTick(() => {
    // Pie chart
    const pieData = buildPieData()
    if (pieChartRef.value) {
      if (!pieChart) pieChart = echarts.init(pieChartRef.value, null, { useDirtyRect: true })
      pieChart.setOption({
        tooltip: {
          trigger: 'item',
          formatter: (params) => {
            const value = params.value || 0
            const name = params.name || ''
            const total = pieData.reduce((sum, x) => sum + (x.value || 0), 0) || 1
            const percent = ((value / total) * 100).toFixed(2)
            return `${name}<br/>${value} 次（${percent}%）`
          }
        },
        legend: { top: '8%', left: 'center' },
        series: [
          {
            name: '主要体质分布',
            type: 'pie',
            radius: ['35%', '70%'],
            center: ['50%', '55%'],
            avoidLabelOverlap: true,
            label: { formatter: '{b}: {d}%' },
            data: pieData.map(d => ({ name: d.name, value: d.value }))
          }
        ]
      })
    }

    // Top 5 bar chart
    const counts = stats.primaryConstitutionCounts || {}
    const topEntries = Object.entries(counts)
      .map(([code, v]) => ({ code, value: Number(v) || 0 }))
      .filter(i => i.value > 0)
      .sort((a, b) => b.value - a.value)
      .slice(0, 5)

    if (topBarChartRef.value) {
      if (!topBarChart) topBarChart = echarts.init(topBarChartRef.value, null, { useDirtyRect: true })
      topBarChart.setOption({
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        xAxis: { type: 'value' },
        yAxis: {
          type: 'category',
          data: topEntries.map(i => formatConstitutionName(i.code))
        },
        grid: { left: 60, right: 20, top: 20, bottom: 30 },
        series: [
          {
            name: '次数',
            type: 'bar',
            barWidth: '45%',
            data: topEntries.map(i => i.value),
            itemStyle: {
              color: '#409eff'
            }
          }
        ]
      })
    }

    // Trend chart
    if (trendChartRef.value) {
      if (!trendChart) trendChart = echarts.init(trendChartRef.value, null, { useDirtyRect: true })

      const trend = Array.isArray(stats.last30DaysTrend) ? stats.last30DaysTrend : []
      const sorted = trend.slice().sort((a, b) => String(a.date).localeCompare(String(b.date)))
      const xData = sorted.map(p => p.date)
      const yData = sorted.map(p => Number(p.testCount || 0))

      trendChart.setOption({
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: xData },
        yAxis: { type: 'value' },
        grid: { left: 40, right: 20, top: 30, bottom: 30 },
        series: [
          {
            name: '测试次数',
            type: 'line',
            smooth: true,
            symbol: 'circle',
            symbolSize: 6,
            data: yData,
            lineStyle: { width: 2 },
            areaStyle: { opacity: 0.15 }
          }
        ]
      })
    }
  })
}

const resizeAll = () => {
  pieChart?.resize?.()
  topBarChart?.resize?.()
  trendChart?.resize?.()
}

onMounted(async () => {
  try {
    const res = await getAdminUserTestStats()
    if (res.code === 200 && res.data) {
      Object.assign(stats, res.data)
    }
    updateCharts()
  } catch (e) {
    message.error('加载用户体质测试统计失败')
  }
  window.addEventListener('resize', resizeAll)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeAll)
  pieChart?.dispose?.()
  topBarChart?.dispose?.()
  trendChart?.dispose?.()
  pieChart = null
  topBarChart = null
  trendChart = null
})
</script>

<style scoped lang="scss">
.statistics-container {
  .stats-row {
    margin-bottom: 0;
  }

  .stat-card {
    height: 110px;
    padding: 18px;
    border-radius: 12px;
    background: #ffffff;
    border: 1px solid #e5e7eb;
    display: flex;
    gap: 14px;
    align-items: center;

    .stat-icon {
      width: 42px;
      height: 42px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
      flex-shrink: 0;
    }

    .stat-content {
      flex: 1;
      min-width: 0;

      .stat-title {
        font-size: 14px;
        color: #606266;
        margin-bottom: 8px;
      }

      .stat-value {
        font-size: 26px;
        color: #303133;
        font-weight: 700;
        line-height: 1;
      }

      .stat-suffix {
        font-size: 12px;
        color: #909399;
        margin-top: 6px;
      }
    }

    &.primary {
      .stat-icon {
        background: #409eff;
      }
    }
    &.success {
      .stat-icon {
        background: #67c23a;
      }
    }
    &.warning {
      .stat-icon {
        background: #e6a23c;
      }
    }
    &.danger {
      .stat-icon {
        background: #f56c6c;
      }
    }
  }

  .top-list {
    padding: 6px 0;
    max-height: 220px;
    overflow: auto;
  }

  .top-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    padding: 6px 0;
    border-bottom: 1px dashed #f0f0f0;
  }

  .top-row:last-child {
    border-bottom: none;
  }

  .top-name {
    color: #606266;
    font-size: 14px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .top-count {
    flex-shrink: 0;
    color: #909399;
    font-size: 13px;
  }
}
</style>

