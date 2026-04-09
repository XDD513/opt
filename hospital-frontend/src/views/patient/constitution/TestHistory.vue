<template>
  <div class="test-history">
    <el-page-header @back="goBack" content="测试历史记录" />

    <el-card class="history-card">
      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="5" animated />
      </div>

      <div v-else-if="historyList.length > 0">
        <div class="toolbar">
          <el-checkbox v-model="selectAll" @change="handleSelectAll">全选</el-checkbox>
          <el-button 
            type="primary" 
            :disabled="selectedTestIds.length < 2"
            @click="showComparison"
          >
            对比选中记录 ({{ selectedTestIds.length }})
          </el-button>
        </div>
        <el-timeline>
          <el-timeline-item
            v-for="item in historyList"
            :key="item.id"
            :timestamp="item.testDate"
            placement="top"
          >
            <el-card>
              <div class="history-item">
                <div class="item-header">
                  <el-checkbox 
                    :model-value="selectedTestIds.includes(item.id)"
                    @change="(val) => handleSelectChange(item.id, val)"
                    style="margin-right: 10px;"
                  />
                  <div class="constitution-info">
                    <el-tag type="primary" size="large">
                      主要体质: {{ item.primaryConstitutionName }}
                    </el-tag>
                    <el-tag 
                      v-if="item.secondaryConstitution" 
                      type="success" 
                      size="large"
                      style="margin-left: 10px;"
                    >
                      次要体质: {{ item.secondaryConstitutionName }}
                    </el-tag>
                  </div>
                  <div class="item-actions">
                    <el-button 
                      type="primary" 
                      size="small" 
                      @click="viewDetail(item.id)"
                    >
                      查看详情
                    </el-button>
                  </div>
                </div>

                <div class="item-scores">
                  <el-row :gutter="10">
                    <el-col :span="6" v-for="(score, code) in item.scores" :key="code">
                      <div class="score-item">
                        <div class="score-label">{{ getConstitutionName(code) }}</div>
                        <el-progress 
                          :percentage="score" 
                          :color="getScoreColor(score)"
                          :stroke-width="8"
                        />
                      </div>
                    </el-col>
                  </el-row>
                </div>

                <div class="item-summary">
                  <el-descriptions :column="2" size="small">
                    <el-descriptions-item label="测试日期">
                      {{ item.testDate }}
                    </el-descriptions-item>
                    <el-descriptions-item label="主要体质得分">
                      {{ item.scores[item.primaryConstitution]?.toFixed(1) }}
                    </el-descriptions-item>
                  </el-descriptions>
                </div>
              </div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>

      <el-empty v-else description="暂无测试记录">
        <el-button type="primary" @click="goToTest">开始测试</el-button>
      </el-empty>
    </el-card>

    <!-- 对比对话框 -->
    <el-dialog
      v-model="comparisonDialogVisible"
      title="体质测试对比"
      width="800px"
      @opened="handleComparisonOpened"
      @close="handleComparisonDialogClose"
    >
      <div v-if="comparisonLoading" style="text-align: center; padding: 40px;">
        <el-icon class="is-loading" style="font-size: 24px;">
          <Loading />
        </el-icon>
        <p>加载对比数据中...</p>
      </div>
      <div v-else-if="comparisonData.length > 0">
        <div ref="comparisonChartRef" style="width: 100%; height: 500px;"></div>
      </div>
      <div v-else style="text-align: center; padding: 40px;">
        <p>暂无对比数据</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import message from '@/plugins/message'
import { ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

import { getTestHistory } from '@/api/constitution'

const router = useRouter()

// 数据
const loading = ref(false)
const historyList = ref([])
const selectedTestIds = ref([])
const selectAll = ref(false)
const comparisonDialogVisible = ref(false)
const comparisonLoading = ref(false)
const comparisonData = ref([])
const comparisonChartRef = ref(null)
let comparisonChart = null

// 体质名称映射
const constitutionNames = {
  'PINGHE': '平和质',
  'QIXU': '气虚质',
  'YANGXU': '阳虚质',
  'YINXU': '阴虚质',
  'TANSHI': '痰湿质',
  'SHIRE': '湿热质',
  'XUEYU': '血瘀质',
  'QIYU': '气郁质',
  'TEBING': '特禀质'
}

// 获取体质名称
const getConstitutionName = (code) => {
  return constitutionNames[code] || code
}

// 获取得分颜色
const getScoreColor = (score) => {
  if (score >= 80) return '#67C23A'
  if (score >= 60) return '#E6A23C'
  return '#909399'
}

// 加载历史记录
const loadHistory = async () => {
  try {
    loading.value = true
    const res = await getTestHistory()
    if (res.code === 200) {
      historyList.value = res.data
    }
  } catch (error) {

    message.error('加载历史记录失败')
  } finally {
    loading.value = false
  }
}

// 查看详情
const viewDetail = (id) => {
  router.push({
    name: 'TestHistoryDetail',
    params: { id }
  })
}

// 返回
const goBack = () => {
  router.back()
}

// 去测试
const goToTest = () => {
  router.push('/patient/constitution/smart-test')
}

// 处理选择变化
const handleSelectChange = (testId, checked) => {
  if (checked) {
    if (!selectedTestIds.value.includes(testId)) {
      selectedTestIds.value.push(testId)
    }
  } else {
    const index = selectedTestIds.value.indexOf(testId)
    if (index > -1) {
      selectedTestIds.value.splice(index, 1)
    }
  }
  updateSelectAll()
}

// 处理全选
const handleSelectAll = (checked) => {
  if (checked) {
    selectedTestIds.value = historyList.value.map(item => item.id)
  } else {
    selectedTestIds.value = []
  }
}

// 更新全选状态
const updateSelectAll = () => {
  selectAll.value = historyList.value.length > 0 && 
    selectedTestIds.value.length === historyList.value.length
}

// 显示对比
const showComparison = async () => {
  if (selectedTestIds.value.length < 2) {
    message.warning('请至少选择2条记录进行对比')
    return
  }
  
  comparisonDialogVisible.value = true
  comparisonLoading.value = true
  
  try {
    // 从历史记录中获取选中的测试数据
    const selectedTests = historyList.value.filter(item => 
      selectedTestIds.value.includes(item.id)
    )
    
    if (selectedTests.length < 2) {
      message.error('无法获取完整的测试结果进行对比')
      return
    }
    
    comparisonData.value = selectedTests
  } catch (error) {
    console.error('加载对比数据失败', error)
    message.error('加载对比数据失败')
  } finally {
    comparisonLoading.value = false
  }
}

const handleComparisonOpened = async () => {
  if (comparisonLoading.value || comparisonData.value.length === 0) return
  await nextTick()
  initComparisonChart()
  window.addEventListener('resize', handleChartResize)
}

// 初始化对比雷达图
const initComparisonChart = () => {
  if (!comparisonChartRef.value || comparisonData.value.length === 0) return
  
  if (comparisonChart) {
    comparisonChart.dispose()
  }
  
  comparisonChart = echarts.init(comparisonChartRef.value)
  
  // 获取所有体质类型（9种）
  const constitutionTypes = ['PINGHE', 'QIXU', 'YANGXU', 'YINXU', 'TANSHI', 'SHIRE', 'XUEYU', 'QIYU', 'TEBING']
  const constitutionNames = constitutionTypes.map(code => getConstitutionName(code))
  
  // 构建系列数据
  const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#9C27B0', '#FF9800', '#00BCD4']
  const series = comparisonData.value.map((item, index) => {
    const data = constitutionTypes.map(code => {
      return item.scores && item.scores[code] ? item.scores[code] : 0
    })
    
    return {
      name: `${item.testDate || '未知日期'} - ${item.primaryConstitutionName || ''}`,
      type: 'radar',
      data: [{
        value: data,
        name: `${item.testDate || '未知日期'} - ${item.primaryConstitutionName || ''}`
      }],
      itemStyle: {
        color: colors[index % colors.length]
      },
      lineStyle: {
        width: 2
      },
      areaStyle: {
        opacity: 0.1
      }
    }
  })
  
  const option = {
    tooltip: {
      trigger: 'item'
    },
    legend: {
      data: comparisonData.value.map(item => 
        `${item.testDate || '未知日期'} - ${item.primaryConstitutionName || ''}`
      ),
      bottom: 10
    },
    radar: {
      indicator: constitutionNames.map(name => ({
        name: name,
        max: 100
      })),
      center: ['50%', '50%'],
      radius: '65%',
      name: {
        textStyle: {
          color: '#333',
          fontSize: 12
        }
      },
      splitArea: {
        show: true,
        areaStyle: {
          color: ['rgba(250, 250, 250, 0.3)', 'rgba(200, 200, 200, 0.3)']
        }
      }
    },
    series: series
  }
  
  comparisonChart.setOption(option)
}

const handleChartResize = () => {
  comparisonChart?.resize()
}

// 处理对比对话框关闭
const handleComparisonDialogClose = () => {
  window.removeEventListener('resize', handleChartResize)
  if (comparisonChart) {
    comparisonChart.dispose()
    comparisonChart = null
  }
  comparisonData.value = []
}

// 监听历史列表变化，更新全选状态
watch(historyList, () => {
  updateSelectAll()
}, { deep: true })

// 页面加载
onMounted(() => {
  loadHistory()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleChartResize)
  if (comparisonChart) {
    comparisonChart.dispose()
    comparisonChart = null
  }
})
</script>

<style scoped>
.test-history {
  padding: 20px;
}

.el-page-header {
  margin-bottom: 20px;
}

.history-card {
  max-width: 1200px;
  margin: 0 auto;
}

.loading-container {
  padding: 40px;
}

.history-item {
  padding: 10px 0;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.constitution-info {
  flex: 1;
}

.item-scores {
  margin: 20px 0;
}

.score-item {
  margin-bottom: 15px;
}

.score-label {
  font-size: 13px;
  color: #606266;
  margin-bottom: 5px;
}

.item-summary {
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px solid #EBEEF5;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 4px;
}
</style>

