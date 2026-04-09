<template>
  <div class="health-plan-history">
    <div class="page-header">
      <div class="header-left">
        <h2>健康计划归档</h2>
        <p class="subtitle">按体质测试记录归档您的健康计划</p>
      </div>
    </div>

    <el-row v-loading="loading" :gutter="20" v-if="!loading && historyList.length > 0">
      <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="item in historyList" :key="item.testId">
        <el-card class="history-card" shadow="hover" @click="goToDetail(item.testId)">
          <div class="card-header">
            <span class="date">{{ item.testDate ? formatDate(item.testDate) : '手动创建/其他' }}</span>
            <el-tag v-if="item.testId !== -1" size="small" :type="getConstitutionColorType(item.primaryConstitution)">
              {{ getConstitutionName(item.primaryConstitution) }}
            </el-tag>
            <el-tag v-else size="small" type="info">自定义</el-tag>
          </div>
          <div class="card-content">
            <div class="timeline-panel">
              <div class="timeline-item">
                <span class="dot dot-blue"></span>
                <span class="t-label">归档时间</span>
                <span class="t-value">{{ item.testDate ? formatDate(item.testDate) : '手动创建/其他' }}</span>
              </div>

              <div class="timeline-item">
                <span class="dot dot-green"></span>
                <span class="t-label">体质类型</span>
                <span class="t-value">
                  {{ item.testId === -1 ? '自定义' : getConstitutionName(item.primaryConstitution) }}
                </span>
              </div>

              <div class="timeline-item">
                <span class="dot dot-orange"></span>
                <span class="t-label">计划数量</span>
                <span class="t-value strong">{{ item.planCount }} 项</span>
              </div>

              <div class="timeline-item">
                <span class="dot dot-purple"></span>
                <span class="t-label">类型分布</span>
                <div class="type-tags">
                  <el-tag size="small" effect="plain">饮食 {{ item.typeCount?.DIET || 0 }}</el-tag>
                  <el-tag size="small" effect="plain" type="warning">运动 {{ item.typeCount?.EXERCISE || 0 }}</el-tag>
                  <el-tag size="small" effect="plain" type="success">穴位 {{ item.typeCount?.ACUPOINT || 0 }}</el-tag>
                  <el-tag size="small" effect="plain" type="info">起居 {{ item.typeCount?.SLEEP || 0 }}</el-tag>
                </div>
              </div>
            </div>
            <div class="info">
              <el-button text type="primary" size="small">查看详情 <el-icon><ArrowRight /></el-icon></el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!loading && historyList.length === 0" description="暂无历史记录">
      <el-button type="primary" @click="goToDetail(-1)">创建新计划</el-button>
    </el-empty>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getHealthPlanHistory, getHealthPlanList } from '@/api/health'
import { useUserStore } from '@/stores/user'
import { ArrowRight } from '@element-plus/icons-vue'
import { getConstitutionName, getConstitutionColorType } from '@/utils/constitution'
import dayjs from 'dayjs'

const router = useRouter()
const userStore = useUserStore()
const historyList = ref([])
const loading = ref(false)

const formatDate = (date) => {
  return date ? dayjs(date).format('YYYY-MM-DD HH:mm') : ''
}

const fetchHistory = async () => {
  loading.value = true
  historyList.value = []
  try {
    const userId = userStore.userInfo?.id
    if (!userId) {
      console.warn('User ID not found')
      return
    }

    // 1) 先获取归档的基础信息（testDate / primaryConstitution / thumbnail / testId 等）
    const res = await getHealthPlanHistory(userId)
    if (res.code === 200) {
      // Sort by date desc
      historyList.value = (res.data || [])
        .filter(item => item && (item.testId !== undefined && item.testId !== null))
        .sort((a, b) => {
          if (a.testId === -1) return 1; 
          if (b.testId === -1) return -1;
          return new Date(b.testDate) - new Date(a.testDate)
        })
    }

    // 2) 由于后端可能仅把“删除”标记成 status=3 而不是物理删除，
    //    导致 /health/plan/history 的 planCount 仍包含删除态。
    //    这里再拉取计划列表，按 status !== 3 重新计算每个 testId 的计划数量。
    try {
      const planRes = await getHealthPlanList({
        userId,
        status: null,
        pageNum: 1,
        pageSize: 1000
      })

      if (planRes.code === 200) {
        const plans = planRes.data?.records || planRes.data || []
        const activePlans = (plans || []).filter(p => p && p.status !== 3)

        const statMap = activePlans.reduce((acc, p) => {
          const tId = p.testId
          if (tId == null) return acc

          if (!acc[tId]) {
            acc[tId] = {
              planCount: 0,
              typeCount: { DIET: 0, EXERCISE: 0, ACUPOINT: 0, SLEEP: 0, OTHER: 0 }
            }
          }

          const group = acc[tId]
          group.planCount += 1

          const type = String(p.planType || p.type || 'OTHER').toUpperCase()
          if (group.typeCount[type] === undefined) group.typeCount.OTHER += 1
          else group.typeCount[type] += 1

          return acc
        }, {})

        historyList.value = historyList.value
          .map(item => {
            const stat = statMap[item.testId] || {
              planCount: 0,
              typeCount: { DIET: 0, EXERCISE: 0, ACUPOINT: 0, SLEEP: 0, OTHER: 0 }
            }

            return {
              ...item,
              planCount: stat.planCount,
              typeCount: stat.typeCount
            }
          })
          // 删除完所有计划后，归档页应进入空状态
          .filter(item => Number(item.planCount) > 0)
      }
    } catch (e) {
      // 兜底：如果二次拉取失败，就仍然使用后端返回的 planCount
    }
  } catch (error) {
    console.error('Failed to fetch history:', error)
  } finally {
    loading.value = false
  }
}

const goToDetail = (testId) => {
  router.push({
    path: '/patient/health/plan/detail',
    query: { testId }
  })
}

onMounted(() => {
  fetchHistory()
})
</script>

<style scoped>
.health-plan-history {
  padding: 20px;
}
.page-header {
  margin-bottom: 30px;
}
.header-left h2 {
  margin: 0 0 10px 0;
  font-size: 24px;
  color: #303133;
}
.subtitle {
  margin: 0;
  color: #909399;
  font-size: 14px;
}
.history-card {
  cursor: pointer;
  margin-bottom: 20px;
  transition: all 0.3s;
  border: 1px solid #ebeef5;
}
.history-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 16px rgba(0,0,0,0.1);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f2f5;
}
.date {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}
.timeline-panel {
  background: #f8fbff;
  border: 1px solid #edf2fc;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 12px;
}
.timeline-item {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  min-height: 24px;
}
.timeline-item:last-child {
  margin-bottom: 0;
  align-items: flex-start;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 8px;
  margin-top: 1px;
  flex-shrink: 0;
}
.dot-blue { background: #409EFF; }
.dot-green { background: #67C23A; }
.dot-orange { background: #E6A23C; }
.dot-purple { background: #9B59B6; }
.t-label {
  width: 60px;
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}
.t-value {
  font-size: 12px;
  color: #606266;
  word-break: break-all;
}
.t-value.strong {
  color: #409EFF;
  font-weight: 600;
}
.type-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.info {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}
</style>
