<template>
  <div class="health-plan-container">
    <div class="page-header">
      <div class="header-left" style="display: flex; align-items: center; gap: 10px;">
        <el-button v-if="testId" circle @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <h2>{{ testId ? '计划详情' : '健康计划' }}</h2>
      </div>
      <el-button type="primary" @click="showAddDialog">
        <el-icon><Plus /></el-icon>
        创建计划
      </el-button>
    </div>

    <el-card class="list-card" v-loading="loading">
      <el-tabs v-if="!testId" v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="进行中" name="active"></el-tab-pane>
        <el-tab-pane label="已完成" name="completed"></el-tab-pane>
        <el-tab-pane label="全部" name="all"></el-tab-pane>
      </el-tabs>

      <div class="plan-list" v-if="!loading && planList.length > 0">
        <el-row :gutter="20">
          <el-col
            v-for="plan in planList"
            :key="plan.id"
            :xs="24"
            :sm="12"
            :md="8"
          >
            <div class="plan-card">
              <div class="plan-header">
                <h3>{{ plan.planName }}</h3>
                <el-tag :type="getPlanTypeTag(plan.planType)" size="small">
                  {{ getPlanTypeLabel(plan.planType) }}
                </el-tag>
              </div>
              <div class="plan-body">
                <p class="description">{{ plan.description }}</p>
                <p class="target" v-if="plan.targetContent">
                  <el-icon><Aim /></el-icon>
                  目标：{{ plan.targetContent }}
                </p>
                <div class="plan-meta">
                  <span>
                    <el-icon><Calendar /></el-icon>
                    {{ formatDate(plan.startDate) }} ~ {{ formatDate(plan.endDate) }}
                  </span>
                  <span>
                    <el-icon><Clock /></el-icon>
                    {{ formatFrequency(plan.frequency) }}
                  </span>
                </div>
                <el-progress
                  :percentage="calculateProgress(plan)"
                  :status="plan.status === 2 ? 'success' : ''"
                />
              </div>
              <div class="plan-footer">
                <el-button size="small" @click="viewPlanDetail(plan)">查看</el-button>
                <el-button size="small" type="primary" @click="handleEdit(plan)">编辑</el-button>
                <el-button size="small" type="danger" @click="handleDelete(plan.id)">删除</el-button>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>

      <!-- 空状态 -->
      <el-empty v-if="!loading && planList.length === 0" description="暂无健康计划" />

      <!-- 分页 -->
      <div class="pagination-container" v-if="total > 0">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="loadPlans"
        />
      </div>
    </el-card>

    <!-- 添加/编辑/查看对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isView ? '计划详情' : (isEdit ? '编辑计划' : '创建计划')"
      width="600px"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-width="100px"
        :disabled="isView"
      >
        <el-form-item label="计划名称" prop="planName">
          <el-input v-model="formData.planName" placeholder="请输入计划名称" @blur="handleFieldBlur('planName')" />
        </el-form-item>

        <el-form-item label="计划类型" prop="planType">
          <el-select v-model="formData.planType" placeholder="请选择计划类型" @change="handleFieldBlur('planType')">
            <el-option label="饮食计划" value="DIET" />
            <el-option label="运动计划" value="EXERCISE" />
            <el-option label="穴位按摩" value="ACUPOINT" />
            <el-option label="药膳调理" value="HERBAL" />
            <el-option label="睡眠调理" value="SLEEP" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>

        <el-form-item label="计划描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="请输入计划描述"
            @blur="handleFieldBlur('description')"
          />
        </el-form-item>

        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker
            v-model="formData.startDate"
            type="date"
            placeholder="选择开始日期"
            value-format="YYYY-MM-DD"
            @change="handleFieldBlur('startDate')"
          />
        </el-form-item>

        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker
            v-model="formData.endDate"
            type="date"
            placeholder="选择结束日期"
            value-format="YYYY-MM-DD"
            @change="handleFieldBlur('endDate')"
          />
        </el-form-item>

        <el-form-item label="执行频率" prop="frequency">
          <el-input v-model="formData.frequency" placeholder="如：每天、每周3次等" @blur="handleFieldBlur('frequency')" />
        </el-form-item>

        <el-form-item label="目标" prop="targetContent">
          <el-input v-model="formData.targetContent" placeholder="请输入目标" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ isView ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!isView" type="primary" @click="handleSubmit" :loading="submitting">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import message from '@/plugins/message'
import { ref, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import { Plus, Calendar, Clock, Aim, ArrowLeft } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import {
  getHealthPlanList,
  getHealthPlansByTestId,
  createHealthPlan,
  updateHealthPlan,
  deleteHealthPlan
} from '@/api/health'
import { useFormValidation } from '@/composables/useFormValidation'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const testId = ref(route.query.testId)
const planTypeFilter = ref(route.query.planType || '')

const planList = ref([])
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const isView = ref(false)
const submitting = ref(false)
const formRef = ref(null)

// 创建字段失焦处理函数
const { handleFieldBlur } = useFormValidation(formRef)
const activeTab = ref('active')

const queryParams = ref({
  pageNum: 1,
  pageSize: 9,
  userId: null,
  status: 1
})

const formData = ref({
  userId: null,
  planName: '',
  planType: '',
  description: '',
  startDate: '',
  endDate: '',
  frequency: '',
    targetContent: '',
    status: 1
  })

const rules = {
  planName: [{ required: true, message: '请输入计划名称', trigger: 'blur' }],
  planType: [{ required: true, message: '请选择计划类型', trigger: 'change' }],
  description: [{ required: true, message: '请输入计划描述', trigger: 'blur' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }],
  frequency: [{ required: true, message: '请输入执行频率', trigger: 'blur' }]
}

// 频率类型映射
const frequencyMap = {
  'DAILY': '每天',
  'WEEKLY': '每周',
  'MONTHLY': '每月'
}

const planTypeMap = {
  'DIET': '饮食计划',
  'EXERCISE': '运动计划',
  'ACUPOINT': '穴位按摩',
  'HERBAL': '药膳调理',
  'SLEEP': '睡眠调理',
  'OTHER': '其他'
}

// 格式化频率显示
const formatFrequency = (freq) => {
  if (!freq) return ''
  // 如果已经是中文，直接返回
  if (/[\u4e00-\u9fa5]/.test(freq)) return freq
  // 如果是标准代码，尝试转换
  return frequencyMap[freq.toUpperCase()] || freq
}

const planTypeTagMap = {
  'DIET': 'success',
  'EXERCISE': 'warning',
  'ACUPOINT': 'primary',
  'HERBAL': 'danger',
  'SLEEP': 'info',
  'OTHER': ''
}

// 加载计划列表
const loadPlans = async () => {
  const userId = userStore.userInfo?.id
  if (!userId) {
    message.warning('请先登录')
    return
  }

  loading.value = true
  // 请求开始时清空旧数据，避免页面切换时短暂闪现过期/空字段卡片
  planList.value = []
  total.value = 0
  try {
    if (testId.value) {
      const res = await getHealthPlansByTestId(userId, testId.value)
      if (res.code === 200) {
        // 兼容后端“删除成功”可能是把 status 标记为 3 而不是物理删除
        // 这里统一前端过滤掉删除态记录，避免页面删除后仍显示
        planList.value = (res.data || []).filter(p => p?.status !== 3)
        if (planTypeFilter.value) {
          const filterType = String(planTypeFilter.value).toUpperCase()
          planList.value = planList.value.filter(p => {
            const t = (p?.planType || p?.type || '').toString().toUpperCase()
            return t === filterType
          })
          total.value = planList.value.length
        }
        total.value = planList.value.length
      }
    } else {
      queryParams.value.userId = userId
      const res = await getHealthPlanList(queryParams.value)
      if (res.code === 200) {
        planList.value = (res.data.records || []).filter(p => p?.status !== 3)
        if (planTypeFilter.value) {
          const filterType = String(planTypeFilter.value).toUpperCase()
          planList.value = planList.value.filter(p => {
            const t = (p?.planType || p?.type || '').toString().toUpperCase()
            return t === filterType
          })
          total.value = planList.value.length
        } else {
          total.value = Number(res.data.total) || 0
        }
      }
    }
  } catch (error) {
    console.error(error)
    message.error('加载计划列表失败')
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  router.back()
}

// 标签页切换
const handleTabChange = (tab) => {
  if (tab === 'active') {
    queryParams.value.status = 1
  } else if (tab === 'completed') {
    queryParams.value.status = 2
  } else {
    queryParams.value.status = null
  }
  queryParams.value.pageNum = 1
  loadPlans()
}

// 显示添加对话框
const showAddDialog = () => {
  isEdit.value = false
  isView.value = false
  dialogVisible.value = true
  formData.value = {
    userId: userStore.userInfo?.id,
    testId: (testId.value && String(testId.value) !== '-1') ? testId.value : null,
    planName: '',
    planType: '',
    description: '',
    startDate: '',
    endDate: '',
    frequency: '',
    targetContent: '',
    status: 1
  }
}

// 编辑计划
const handleEdit = (plan) => {
  isEdit.value = true
  isView.value = false
  dialogVisible.value = true
  formData.value = { ...plan }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      if (isEdit.value) {
        await updateHealthPlan(formData.value.id, formData.value)
        message.success('更新成功')
      } else {
        await createHealthPlan(formData.value)
        message.success('创建成功')
      }
      dialogVisible.value = false
      loadPlans()
    } catch (error) {

      message.error('提交失败')
    } finally {
      submitting.value = false
    }
  })
}

// 删除计划
const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除这个计划吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const userId = userStore.userInfo?.id
    await deleteHealthPlan(id, userId)
    message.success('删除成功')
    loadPlans()
  } catch (error) {
    if (error !== 'cancel') {

      message.error('删除失败')
    }
  }
}

// 查看计划详情
const viewPlanDetail = (plan) => {
  isView.value = true
  isEdit.value = false
  dialogVisible.value = true
  formData.value = { ...plan }
}

// 计算进度
const calculateProgress = (plan) => {
  const now = dayjs()
  const start = dayjs(plan.startDate)
  const end = dayjs(plan.endDate)
  
  if (now.isBefore(start)) return 0
  if (now.isAfter(end)) return 100
  
  const total = end.diff(start, 'day')
  const passed = now.diff(start, 'day')
  return Math.round((passed / total) * 100)
}

// 获取计划类型标签
const getPlanTypeLabel = (type) => {
  return planTypeMap[type] || type
}

const getPlanTypeTag = (type) => {
  return planTypeTagMap[type] || ''
}

// 格式化日期
const formatDate = (date) => {
  if (!date) return ''
  return dayjs(date).format('YYYY-MM-DD')
}

onMounted(() => {
  loadPlans()
})
</script>

<style scoped>
.health-plan-container {
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

.list-card {
  margin-bottom: 20px;
}

.plan-list {
  margin-top: 20px;
}

.plan-card {
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
  transition: all 0.3s;
}

.plan-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.plan-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.plan-header h3 {
  font-size: 18px;
  color: #333;
  margin: 0;
}

.plan-body {
  margin-bottom: 15px;
}

.description {
  font-size: 14px;
  color: #666;
  line-height: 1.6;
  margin-bottom: 10px;
  min-height: 40px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.target {
  font-size: 13px;
  color: #409EFF;
  margin-bottom: 15px;
  display: flex;
  align-items: center;
  gap: 5px;
}

.plan-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
  color: #999;
  margin-bottom: 15px;
}

.plan-meta span {
  display: flex;
  align-items: center;
  gap: 5px;
}

.plan-footer {
  display: flex;
  gap: 10px;
  padding-top: 15px;
  border-top: 1px solid #f0f0f0;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
</style>

