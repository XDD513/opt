<template>
  <div class="health-profile-container">
    <div class="page-header">
      <h2>健康档案</h2>
      <el-button type="primary" @click="handleEdit" v-if="!isEditing">
        <el-icon><Edit /></el-icon>
        编辑档案
      </el-button>
    </div>

    <el-card class="profile-card" v-loading="loading">
      <div v-if="!isEditing && profile">
        <!-- 查看模式 -->
        <el-descriptions :column="2" border>
          <el-descriptions-item label="姓名">{{ displayText(profile.userName) }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ getGenderLabel(profile.gender) }}</el-descriptions-item>
          <el-descriptions-item label="年龄">{{ displayWithUnit(profile.age, '岁') }}</el-descriptions-item>
          <el-descriptions-item label="身高">{{ displayWithUnit(profile.height, 'cm') }}</el-descriptions-item>
          <el-descriptions-item label="体重">{{ displayWithUnit(profile.weight, 'kg') }}</el-descriptions-item>
          <el-descriptions-item label="BMI">
            <el-tag :type="getBMIType(profile.bmi)">{{ displayText(profile.bmi) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="体质类型">
            <el-tag type="success">{{ getConstitutionLabel(profile.constitutionType) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="血型">{{ profile.bloodType || '未填写' }}</el-descriptions-item>
          <el-descriptions-item label="过敏史" :span="2">
            {{ profile.allergyHistory || '无' }}
          </el-descriptions-item>
          <el-descriptions-item label="既往病史" :span="2">
            {{ profile.medicalHistory || '无' }}
          </el-descriptions-item>
          <el-descriptions-item label="家族病史" :span="2">
            {{ profile.familyHistory || '无' }}
          </el-descriptions-item>
          <el-descriptions-item label="生活习惯" :span="2">
            {{ profile.lifestyle || '未填写' }}
          </el-descriptions-item>
          <el-descriptions-item label="更新时间" :span="2">
            {{ formatTime(profile.updatedAt) }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <div v-else>
        <!-- 编辑模式 -->
        <el-form
          ref="formRef"
          :model="formData"
          :rules="rules"
          label-width="120px"
        >
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="姓名" prop="userName">
                <el-input v-model="formData.userName" disabled />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="性别" prop="gender">
                <el-radio-group v-model="formData.gender" @change="handleFieldBlur('gender')">
                  <el-radio :label="1">男</el-radio>
                  <el-radio :label="2">女</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="年龄" prop="age">
                <el-input-number v-model="formData.age" :min="1" :max="150" @blur="handleFieldBlur('age')" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="血型">
                <el-select v-model="formData.bloodType" placeholder="请选择血型" @blur="handleFieldBlur('bloodType')">
                  <el-option label="A型" value="A" />
                  <el-option label="B型" value="B" />
                  <el-option label="AB型" value="AB" />
                  <el-option label="O型" value="O" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="身高(cm)" prop="height">
                <el-input-number v-model="formData.height" :min="50" :max="250" @blur="handleFieldBlur('height')" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="体重(kg)" prop="weight">
                <el-input-number v-model="formData.weight" :min="20" :max="300" @blur="handleFieldBlur('weight')" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="过敏史">
            <el-input
              v-model="formData.allergyHistory"
              type="textarea"
              :rows="2"
              placeholder="请输入过敏史"
            />
          </el-form-item>

          <el-form-item label="既往病史">
            <el-input
              v-model="formData.medicalHistory"
              type="textarea"
              :rows="2"
              placeholder="请输入既往病史"
            />
          </el-form-item>

          <el-form-item label="家族病史">
            <el-input
              v-model="formData.familyHistory"
              type="textarea"
              :rows="2"
              placeholder="请输入家族病史"
            />
          </el-form-item>

          <el-form-item label="生活习惯">
            <el-input
              v-model="formData.lifestyle"
              type="textarea"
              :rows="3"
              placeholder="请输入生活习惯（如饮食、运动、睡眠等）"
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" @click="handleSubmit" :loading="submitting">
              保存
            </el-button>
            <el-button @click="handleCancel">取消</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import message from '@/plugins/message'
import { ref, onMounted } from 'vue'

import { Edit } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import dayjs from 'dayjs'
import { getHealthProfile, updateHealthProfile } from '@/api/health'
import { useFormValidation } from '@/composables/useFormValidation'

const userStore = useUserStore()

const profile = ref(null)
const loading = ref(false)
const isEditing = ref(false)
const submitting = ref(false)
const formRef = ref(null)

// 创建字段失焦处理函数
const { handleFieldBlur } = useFormValidation(formRef)

const formData = ref({
  userId: null,
  userName: '',
  gender: 1,
  age: null,
  height: null,
  weight: null,
  bloodType: '',
  constitutionType: '',
  allergyHistory: '',
  medicalHistory: '',
  familyHistory: '',
  lifestyle: ''
})

const rules = {
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  age: [{ required: true, message: '请输入年龄', trigger: 'blur' }],
  height: [{ required: true, message: '请输入身高', trigger: 'blur' }],
  weight: [{ required: true, message: '请输入体重', trigger: 'blur' }]
}

// 体质类型映射
const constitutionMap = {
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

// 加载健康档案
const loadProfile = async () => {
  const userId = userStore.userInfo?.id
  if (!userId) {
    message.warning('请先登录')
    return
  }

  loading.value = true
  try {
    const res = await getHealthProfile(userId)
    if (res.code === 200) {
      profile.value = res.data
      if (!res.data) {
        // 如果没有档案，进入编辑模式
        isEditing.value = true
        formData.value.userId = userId
        formData.value.userName = userStore.userInfo.username
      }
    }
  } catch (error) {

    message.error('加载健康档案失败')
  } finally {
    loading.value = false
  }
}

// 编辑档案
const handleEdit = () => {
  isEditing.value = true
  formData.value = {
    userId: profile.value.userId,
    userName: profile.value.userName,
    gender: profile.value.gender,
    age: profile.value.age,
    height: profile.value.height,
    weight: profile.value.weight,
    bloodType: profile.value.bloodType,
    constitutionType: profile.value.constitutionType,
    allergyHistory: profile.value.allergyHistory,
    medicalHistory: profile.value.medicalHistory,
    familyHistory: profile.value.familyHistory,
    lifestyle: profile.value.lifestyle
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      await updateHealthProfile(formData.value)
      message.success('保存成功')
      isEditing.value = false
      loadProfile()
    } catch (error) {

      message.error('保存失败')
    } finally {
      submitting.value = false
    }
  })
}

// 取消编辑
const handleCancel = () => {
  isEditing.value = false
  if (!profile.value) {
    loadProfile()
  }
}

// 获取BMI类型
const getBMIType = (bmi) => {
  if (!bmi) return 'info'
  if (bmi < 18.5) return 'warning'
  if (bmi < 24) return 'success'
  if (bmi < 28) return 'warning'
  return 'danger'
}

// 获取体质标签
const getConstitutionLabel = (type) => {
  return constitutionMap[type] || type || '未测试'
}

const hasValue = (value) => value !== null && value !== undefined && String(value).trim() !== ''

const displayText = (value) => hasValue(value) ? value : '未填写'

const displayWithUnit = (value, unit) => hasValue(value) ? `${value}${unit}` : '未填写'

const getGenderLabel = (gender) => {
  if (gender === 1) return '男'
  if (gender === 2) return '女'
  return '未填写'
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

onMounted(() => {
  loadProfile()
})
</script>

<style scoped>
.health-profile-container {
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

.profile-card {
  max-width: 1000px;
  margin: 0 auto;
}
</style>

