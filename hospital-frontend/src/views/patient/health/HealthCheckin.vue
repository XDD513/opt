<template>
  <div class="health-checkin-container">
    <div class="page-header">
      <h2>健康打卡</h2>
    </div>

    <!-- 今日打卡 -->
    <el-card class="checkin-card">
      <template #header>
        <div class="card-header">
          <span>今日打卡</span>
          <span class="date">{{ dayjs().format('YYYY-MM-DD') }}</span>
        </div>
      </template>

      <div v-if="!todayCheckin">
        <el-form ref="formRef" :model="formData" :rules="rules" label-width="120px">
          <el-form-item label="体重(kg)" prop="weight">
            <el-input-number v-model="formData.weight" :min="20" :max="300" :precision="1" @blur="handleFieldBlur('weight')" />
          </el-form-item>

          <el-form-item label="血压">
            <el-row :gutter="10">
              <el-col :span="12">
                <el-input-number v-model="formData.bloodPressureHigh" placeholder="高压" :min="60" :max="200" />
              </el-col>
              <el-col :span="12">
                <el-input-number v-model="formData.bloodPressureLow" placeholder="低压" :min="40" :max="150" />
              </el-col>
            </el-row>
          </el-form-item>

          <el-form-item label="心率(次/分)">
            <el-input-number v-model="formData.heartRate" :min="40" :max="200" />
          </el-form-item>

          <el-form-item label="睡眠时长(小时)" prop="sleepDuration">
            <el-input-number v-model="formData.sleepDuration" :min="0" :max="24" :precision="1" @blur="handleFieldBlur('sleepDuration')" />
          </el-form-item>

          <el-form-item label="睡眠质量" prop="sleepQuality">
            <el-rate v-model="formData.sleepQuality" :max="5" @change="handleFieldBlur('sleepQuality')" />
          </el-form-item>

          <el-form-item label="运动时长(分钟)">
            <el-input-number v-model="formData.exerciseDuration" :min="0" :max="1440" />
          </el-form-item>

          <el-form-item label="饮水量(ml)">
            <el-input-number v-model="formData.waterIntake" :min="0" :max="10000" :step="100" />
          </el-form-item>

          <el-form-item label="情绪状态" prop="mood">
            <el-select v-model="formData.mood" placeholder="请选择情绪状态" @change="handleFieldBlur('mood')">
              <el-option label="😊 很好" value="GOOD" />
              <el-option label="🙂 一般" value="NORMAL" />
              <el-option label="😔 不好" value="BAD" />
            </el-select>
          </el-form-item>

          <el-form-item label="备注">
            <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="记录今天的健康状况、饮食、运动等" />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" @click="handleCheckin" :loading="submitting">
              <el-icon>
                <Check />
              </el-icon>
              打卡
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <div v-else class="checkin-success">
        <el-result icon="success" title="当日已打卡" :sub-title="`打卡时间：${formatTime(todayCheckin.createdAt)}`">
          <template #extra>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="体重">{{ todayCheckin.weight }}kg</el-descriptions-item>
              <el-descriptions-item label="血压">
                {{ todayCheckin.bloodPressure || '/' }}
              </el-descriptions-item>
              <el-descriptions-item label="心率">{{ todayCheckin.heartRate }}次/分</el-descriptions-item>
              <el-descriptions-item label="睡眠时长">{{ todayCheckin.sleepDuration }}小时</el-descriptions-item>
              <el-descriptions-item label="睡眠质量">
                <el-rate v-model="todayCheckin.sleepQuality" disabled />
              </el-descriptions-item>
              <el-descriptions-item label="运动时长">{{ todayCheckin.exerciseDuration }}分钟</el-descriptions-item>
              <el-descriptions-item label="饮水量">{{ todayCheckin.waterIntake }}ml</el-descriptions-item>
              <el-descriptions-item label="情绪状态">{{ formatMoodScore(todayCheckin.moodScore) }}</el-descriptions-item>
              <el-descriptions-item label="备注" :span="2">{{ todayCheckin.remark || '无' }}</el-descriptions-item>
            </el-descriptions>
          </template>
        </el-result>
      </div>
    </el-card>

    <!-- 打卡记录 -->
    <el-card class="history-card">
      <template #header>
        <div class="card-header">
          <span>打卡记录</span>
          <div class="header-actions">
            <el-button size="small" @click="prevMonth">
              <el-icon>
                <ArrowLeft />
              </el-icon>
              上月
            </el-button>
            <el-date-picker v-model="selectedDate" type="date" placeholder="选择日期" format="YYYY年MM月DD日"
              value-format="YYYY-MM-DD" @change="handleDateChange" size="small" clearable
              style="width: 200px; margin: 0 10px;" />
            <el-button size="small" @click="nextMonth">
              下月
              <el-icon>
                <ArrowRight />
              </el-icon>
            </el-button>
          </div>
        </div>
      </template>

      <!-- 卡片网格布局 -->
      <div class="checkin-grid" v-if="checkinList.length > 0">
        <div class="checkin-card" :class="{ 'highlight': isHighlighted(checkin.checkinDate) }"
          :ref="el => setCardRef(checkin.checkinDate, el)" v-for="checkin in checkinList" :key="checkin.id"
          @click="showCheckinDetail(checkin)">
          <div class="checkin-card-header">
            <div class="date-info">
              <div class="date-day">{{ formatDay(checkin.checkinDate) }}</div>
              <div class="date-month">{{ formatMonth(checkin.checkinDate) }}</div>
            </div>
            <div class="weekday">{{ formatWeekday(checkin.checkinDate) }}</div>
          </div>

          <div class="checkin-card-body">
            <div class="data-row" v-if="checkin.weight">
              <span class="data-icon">⚖️</span>
              <span class="data-label">体重</span>
              <span class="data-value">{{ checkin.weight }}kg</span>
            </div>

            <div class="data-row" v-if="checkin.bloodPressure">
              <span class="data-icon">❤️</span>
              <span class="data-label">血压</span>
              <span class="data-value">{{ checkin.bloodPressure }}</span>
            </div>

            <div class="data-row" v-if="checkin.heartRate">
              <span class="data-icon">💓</span>
              <span class="data-label">心率</span>
              <span class="data-value">{{ checkin.heartRate }}次/分</span>
            </div>

            <div class="data-row" v-if="checkin.sleepDuration">
              <span class="data-icon">😴</span>
              <span class="data-label">睡眠</span>
              <span class="data-value">
                {{ checkin.sleepDuration }}h
                <el-rate v-if="checkin.sleepQuality" v-model="checkin.sleepQuality" disabled size="small"
                  style="display: inline-block; margin-left: 5px;" />
              </span>
            </div>

            <div class="data-row" v-if="checkin.exerciseDuration">
              <span class="data-icon">🏃</span>
              <span class="data-label">运动</span>
              <span class="data-value">{{ checkin.exerciseDuration }}min</span>
            </div>

            <div class="data-row" v-if="checkin.waterIntake">
              <span class="data-icon">💧</span>
              <span class="data-label">饮水</span>
              <span class="data-value">{{ checkin.waterIntake }}ml</span>
            </div>

            <div class="data-row" v-if="checkin.moodScore">
              <span class="data-icon">{{ getMoodIcon(checkin.moodScore) }}</span>
              <span class="data-label">心情</span>
              <span class="data-value">{{ formatMoodScore(checkin.moodScore) }}</span>
            </div>
          </div>

          <div class="checkin-card-footer" v-if="checkin.remark">
            <div class="remark-text">💬 {{ checkin.remark }}</div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <el-empty v-else description="暂无打卡记录" :image-size="120" />
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="打卡详情" width="500px">
      <div v-if="selectedCheckin" class="detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="日期">{{ formatDate(selectedCheckin.checkinDate) }}</el-descriptions-item>
          <el-descriptions-item label="星期">{{ formatWeekday(selectedCheckin.checkinDate) }}</el-descriptions-item>
          <el-descriptions-item label="体重" v-if="selectedCheckin.weight">{{ selectedCheckin.weight }}kg
          </el-descriptions-item>
          <el-descriptions-item label="血压" v-if="selectedCheckin.bloodPressure">{{ selectedCheckin.bloodPressure }}
          </el-descriptions-item>
          <el-descriptions-item label="心率" v-if="selectedCheckin.heartRate">{{ selectedCheckin.heartRate }}次/分
          </el-descriptions-item>
          <el-descriptions-item label="睡眠时长" v-if="selectedCheckin.sleepDuration">{{ selectedCheckin.sleepDuration
          }}小时</el-descriptions-item>
          <el-descriptions-item label="睡眠质量" v-if="selectedCheckin.sleepQuality">
            <el-rate v-model="selectedCheckin.sleepQuality" disabled />
          </el-descriptions-item>
          <el-descriptions-item label="运动时长" v-if="selectedCheckin.exerciseDuration">{{
            selectedCheckin.exerciseDuration }}分钟</el-descriptions-item>
          <el-descriptions-item label="饮水量" v-if="selectedCheckin.waterIntake">{{ selectedCheckin.waterIntake }}ml
          </el-descriptions-item>
          <el-descriptions-item label="心情状态" v-if="selectedCheckin.moodScore">{{ getMoodIcon(selectedCheckin.moodScore)
          }} {{ formatMoodScore(selectedCheckin.moodScore) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2" v-if="selectedCheckin.remark">{{ selectedCheckin.remark }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import message from '@/plugins/message'
import { ref, onMounted, nextTick } from 'vue'

import { Check, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import dayjs from 'dayjs'
import {
  healthCheckin,
  getCheckinList,
  getCheckinByDate
} from '@/api/health'
import { useFormValidation } from '@/composables/useFormValidation'

const userStore = useUserStore()

const todayCheckin = ref(null)
const checkinList = ref([])
const total = ref(0)
const submitting = ref(false)
const formRef = ref(null)

// 创建字段失焦处理函数
const { handleFieldBlur } = useFormValidation(formRef)

const selectedDate = ref(dayjs().format('YYYY-MM-DD'))
const highlightedDate = ref(null)
const cardRefs = ref({})

const queryParams = ref({
  userId: null,
  startDate: '',
  endDate: '',
  pageNum: 1,
  pageSize: 100
})

const detailDialogVisible = ref(false)
const selectedCheckin = ref(null)

const formData = ref({
  userId: null,
  weight: null,
  bloodPressureHigh: null,
  bloodPressureLow: null,
  heartRate: null,
  sleepDuration: null,
  sleepQuality: 3,
  exerciseDuration: 0,
  waterIntake: 0,
  mood: 'NORMAL',
  remark: ''
})

const rules = {
  weight: [{ required: true, message: '请输入体重', trigger: 'blur' }],
  sleepDuration: [{ required: true, message: '请输入睡眠时长', trigger: 'blur' }],
  sleepQuality: [{ required: true, message: '请选择睡眠质量', trigger: 'change' }],
  mood: [{ required: true, message: '请选择情绪状态', trigger: 'change' }]
}

// 情绪映射
const moodMap = {
  'GOOD': '😊 很好',
  'NORMAL': '🙂 一般',
  'BAD': '😔 不好'
}

const checkTodayCheckin = async () => {
  const userId = userStore.userInfo?.id
  if (!userId) return

  try {
    const res = await getCheckinByDate(userId, dayjs().format('YYYY-MM-DD'))
    if (res.code === 200 && res.data) {
      todayCheckin.value = res.data
    } else {
      todayCheckin.value = null
    }
  } catch (error) {

  }
}

// 加载打卡记录
const loadCheckinList = async () => {
  const userId = userStore.userInfo?.id
  if (!userId) {
    message.warning('请先登录')
    return
  }

  try {
    // 从选择的日期中提取月份
    const currentMonth = dayjs(selectedDate.value).format('YYYY-MM')
    const startDate = dayjs(currentMonth).startOf('month').format('YYYY-MM-DD')
    const endDate = dayjs(currentMonth).endOf('month').format('YYYY-MM-DD')

    queryParams.value.userId = userId
    queryParams.value.startDate = startDate
    queryParams.value.endDate = endDate

    const res = await getCheckinList(queryParams.value)
    if (res.code === 200) {
      checkinList.value = res.data.records || []
      total.value = res.data.total || 0

      // 如果选择了具体日期，加载完成后定位到该日期
      if (selectedDate.value) {
        nextTick(() => {
          scrollToDate(selectedDate.value)
        })
      }
    }
  } catch (error) {

    message.error('加载打卡记录失败')
  }
}

// 打卡
const handleCheckin = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    const userId = userStore.userInfo?.id
    if (!userId) {
      message.warning('请先登录')
      return
    }

    submitting.value = true
    try {
      // 合并血压数据
      let bloodPressure = null
      if (formData.value.bloodPressureHigh && formData.value.bloodPressureLow) {
        bloodPressure = `${formData.value.bloodPressureHigh}/${formData.value.bloodPressureLow}`
      }

      // 转换情绪状态为评分
      const moodScoreMap = {
        'GOOD': 5,
        'NORMAL': 3,
        'BAD': 1
      }
      const moodScore = moodScoreMap[formData.value.mood] || 3

      const data = {
        userId: userId,
        checkinDate: dayjs().format('YYYY-MM-DD'),
        weight: formData.value.weight,
        bloodPressure: bloodPressure,
        heartRate: formData.value.heartRate,
        sleepDuration: formData.value.sleepDuration,
        sleepQuality: formData.value.sleepQuality,
        exerciseDuration: formData.value.exerciseDuration,
        waterIntake: formData.value.waterIntake,
        moodScore: moodScore,
        remark: formData.value.remark
      }

      await healthCheckin(data)
      message.success('打卡成功')
      checkTodayCheckin()
      loadCheckinList()
    } catch (error) {

      message.error('打卡失败')
    } finally {
      submitting.value = false
    }
  })
}

// 获取情绪标签
const getMoodLabel = (mood) => {
  return moodMap[mood] || mood
}

// 格式化情绪评分
const formatMoodScore = (score) => {
  if (!score) return ''
  if (score >= 4) return '很好'
  if (score >= 3) return '一般'
  return '不好'
}

// 获取心情图标
const getMoodIcon = (score) => {
  if (!score) return '😐'
  if (score >= 4) return '😊'
  if (score >= 3) return '🙂'
  return '😔'
}

// 格式化日期 - 日
const formatDay = (date) => {
  return dayjs(date).format('DD')
}

// 格式化日期 - 月
const formatMonth = (date) => {
  return dayjs(date).format('MM月')
}

// 格式化星期
const formatWeekday = (date) => {
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return weekdays[dayjs(date).day()]
}

// 显示打卡详情
const showCheckinDetail = (checkin) => {
  selectedCheckin.value = checkin
  detailDialogVisible.value = true
}

// 设置卡片引用
const setCardRef = (date, el) => {
  if (el) {
    cardRefs.value[date] = el
  }
}

// 判断是否高亮
const isHighlighted = (date) => {
  return highlightedDate.value === date
}

// 滚动到指定日期
const scrollToDate = (date) => {
  highlightedDate.value = date

  nextTick(() => {
    const targetCard = cardRefs.value[date]
    if (targetCard) {
      targetCard.scrollIntoView({
        behavior: 'smooth',
        block: 'center'
      })
    }

    // 3秒后取消高亮
    setTimeout(() => {
      highlightedDate.value = null
    }, 3000)
  })
}

// 处理日期变化
const handleDateChange = (date) => {
  if (!date) {
    // 如果清除了日期，设置为当前日期
    selectedDate.value = dayjs().format('YYYY-MM-DD')
  }
  loadCheckinList()
}

// 上一月
const prevMonth = () => {
  selectedDate.value = dayjs(selectedDate.value).subtract(1, 'month').format('YYYY-MM-DD')
  highlightedDate.value = null
  loadCheckinList()
}

// 下一月
const nextMonth = () => {
  selectedDate.value = dayjs(selectedDate.value).add(1, 'month').format('YYYY-MM-DD')
  highlightedDate.value = null
  loadCheckinList()
}

// 格式化日期
const formatDate = (date) => {
  if (!date) return ''
  return dayjs(date).format('YYYY-MM-DD')
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

onMounted(() => {
  checkTodayCheckin()
  loadCheckinList()
})
</script>

<style scoped>
.health-checkin-container {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 24px;
  color: #333;
  margin: 0;
}

.checkin-card {
  margin-bottom: 20px;
  max-width: 800px;
  margin-left: auto;
  margin-right: auto;
}

.history-card {
  margin-bottom: 20px;
  min-height: 600px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.date {
  font-size: 14px;
  color: #999;
}

.checkin-success {
  text-align: center;
}

.checkin-item {
  padding: 10px;
}

.checkin-data {
  display: flex;
  flex-wrap: wrap;
  gap: 15px;
  font-size: 14px;
  color: #666;
  margin-bottom: 10px;
}

.checkin-remark {
  font-size: 13px;
  color: #999;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 卡片网格布局 */
.checkin-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
  margin-bottom: 20px;
}

/* 单个打卡卡片 */
.checkin-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
  cursor: pointer;
  overflow: hidden;
  border: 1px solid #f0f0f0;
}

.checkin-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  border-color: #409eff;
}

/* 高亮卡片 */
.checkin-card.highlight {
  border: 2px solid #409eff;
  box-shadow: 0 0 20px rgba(64, 158, 255, 0.3);
  animation: highlight-pulse 0.6s ease-in-out;
}

@keyframes highlight-pulse {

  0%,
  100% {
    transform: scale(1);
  }

  50% {
    transform: scale(1.02);
  }
}

/* 卡片头部 */
.checkin-card-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  padding: 15px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.date-info {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.date-day {
  font-size: 32px;
  font-weight: bold;
  line-height: 1;
}

.date-month {
  font-size: 14px;
  opacity: 0.9;
  margin-top: 4px;
}

.weekday {
  font-size: 14px;
  opacity: 0.9;
  background: rgba(255, 255, 255, 0.2);
  padding: 4px 12px;
  border-radius: 12px;
}

/* 卡片主体 */
.checkin-card-body {
  padding: 15px;
}

.data-row {
  display: flex;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f5f5f5;
}

.data-row:last-child {
  border-bottom: none;
}

.data-icon {
  font-size: 20px;
  margin-right: 10px;
  width: 24px;
  text-align: center;
}

.data-label {
  font-size: 14px;
  color: #909399;
  min-width: 50px;
  margin-right: 10px;
}

.data-value {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
  flex: 1;
  text-align: right;
}

/* 卡片底部 */
.checkin-card-footer {
  padding: 10px 15px;
  background: #f9f9f9;
  border-top: 1px solid #f0f0f0;
}

.remark-text {
  font-size: 13px;
  color: #606266;
  /* 允许备注多行展示，避免信息被截断 */
  white-space: normal;
  overflow: visible;
  text-overflow: initial;
  line-height: 1.6;
}

/* 详情弹窗 */
.detail-content {
  padding: 10px 0;
}

/* 响应式布局 */
@media (max-width: 1200px) {
  .checkin-grid {
    grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  }
}

@media (max-width: 768px) {
  .checkin-grid {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 15px;
  }

  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .header-actions {
    width: 100%;
    flex-direction: column;
    gap: 10px;
  }

  .header-actions .el-date-picker,
  .header-actions .el-select {
    width: 100% !important;
  }
}

@media (max-width: 480px) {
  .checkin-grid {
    grid-template-columns: 1fr;
  }
}
</style>
