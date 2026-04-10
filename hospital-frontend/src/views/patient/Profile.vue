<template>
  <div class="profile-container">
    <el-row :gutter="20">
      <!-- 左侧个人信息 -->
      <el-col :xs="24" :md="8">
        <el-card shadow="never">
          <div class="profile-card">
            <el-avatar :size="100" :src="userInfo.avatar">
              <el-icon :size="50"><User /></el-icon>
            </el-avatar>
            
            <h2>{{ userInfo.realName || userInfo.username }}</h2>
            <p class="user-role">{{ getRoleText(userInfo.roleType) }}</p>
            
            <el-descriptions :column="1" class="user-info">
              <el-descriptions-item label="用户名">
                {{ userInfo.username }}
              </el-descriptions-item>
              <el-descriptions-item label="手机号">
                {{ userInfo.phone }}
              </el-descriptions-item>
              <el-descriptions-item label="注册时间">
                {{ formatDate(userInfo.createTime) }}
              </el-descriptions-item>
            </el-descriptions>

            <el-button type="primary" style="width: 100%; margin-top: 20px" @click="openEditDialog">
              编辑资料
            </el-button>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧功能区 -->
      <el-col :xs="24" :md="16">
        <!-- 账号设置 -->
        <el-card shadow="never" style="margin-top: 20px">
          <template #header>
            <h3>账号设置</h3>
          </template>
          <el-form label-width="100px">
            <el-form-item label="消息通知">
              <el-switch v-model="settings.notification" @change="saveSettings" />
              <span class="setting-desc">接收预约提醒通知</span>
            </el-form-item>
            <el-form-item label="短信提醒">
              <el-switch v-model="settings.smsReminder" @change="saveSettings" />
              <span class="setting-desc">就诊前短信提醒</span>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <!-- 编辑资料弹窗 -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑个人资料"
      width="500px"
    >
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="头像">
          <div class="avatar-edit-wrapper">
            <el-avatar :size="80" :src="avatarPreview || editForm.avatar || userInfo.avatar">
              <el-icon :size="40"><User /></el-icon>
            </el-avatar>
            <el-upload
              class="avatar-uploader-inline"
              :action="uploadAction"
              :show-file-list="false"
              :before-upload="beforeAvatarUpload"
              :on-success="handleAvatarSuccess"
              :on-error="handleAvatarError"
              :headers="uploadHeaders"
            >
              <el-button type="primary" size="small">上传头像</el-button>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="真实姓名">
          <el-input v-model="editForm.realName" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="editForm.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="editForm.gender">
            <el-radio :label="1">男</el-radio>
            <el-radio :label="2">女</el-radio>
            <el-radio :label="0">未知</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="出生日期">
          <el-date-picker
            v-model="editForm.birthDate"
            type="date"
            placeholder="选择日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码弹窗 -->
    <el-dialog
      v-model="changePasswordVisible"
      title="修改密码"
      width="450px"
    >
      <el-form :model="passwordForm" label-width="90px">
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="changePasswordVisible = false">取消</el-button>
        <el-button type="primary" @click="handleChangePassword">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import message from '@/plugins/message'
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

import dayjs from 'dayjs'
import { updateUserInfo, getUserInfo, changePassword, getUserSettings, updateUserSettings } from '@/api/user'
import request from '@/api/request'
import { resolveOssPreviewUrl, sanitizeStoredMediaUrl } from '@/utils/ossPreview'
import { useFormValidation } from '@/composables/useFormValidation'
import { useAvatarUpload } from '@/composables/useAvatarUpload'

const router = useRouter()
const userStore = useUserStore()

// 用户信息
const userInfo = ref(userStore.userInfo || {})

// 设置
const settings = reactive({
  notification: true,
  smsReminder: true
})

// 弹窗状态
const editDialogVisible = ref(false)
const changePasswordVisible = ref(false)

// 头像预览
const avatarPreview = ref('')

// 上传配置
const uploadAction = computed(() => {
  // 使用axios实例的baseURL配置
  const baseURL = request.defaults.baseURL || '/api'
  return baseURL + '/upload/avatar'
})

const uploadHeaders = computed(() => {
  const token = userStore.token
  return {
    'Authorization': `Bearer ${token}`
  }
})

// 编辑表单
const editForm = reactive({
  realName: '',
  phone: '',
  gender: 0,
  birthDate: '',
  avatar: ''
})

// 密码表单
const passwordForm = reactive({
  newPassword: '',
  confirmPassword: ''
})

// 获取角色文本
const getRoleText = (roleType) => {
  const roleMap = {
    '0': '患者',
    '1': '管理员'
  }
  return roleMap[roleType] || '未知'
}

// 格式化日期
const formatDate = (dateStr) => {
  if (!dateStr) return '未知'
  return dayjs(dateStr).format('YYYY-MM-DD')
}

// 使用头像上传composable
const { beforeAvatarUpload, handleAvatarSuccess, handleAvatarError } = useAvatarUpload(avatarPreview, editForm)

// 保存资料
const saveProfile = async () => {
  try {
    const updateData = {
      realName: editForm.realName,
      phone: editForm.phone,
      gender: editForm.gender,
      birthDate: editForm.birthDate
    }
    
    // 如果上传了新头像，添加到更新数据中
    if (editForm.avatar) {
      updateData.avatar = editForm.avatar
    }

    const response = await updateUserInfo(updateData)
    
    if (response.code === 200) {
      message.success('保存成功')
      editDialogVisible.value = false
      
      // 清空预览
      avatarPreview.value = ''
      
      // 重新获取用户信息以确保数据同步（包含带签名的头像URL）
      await refreshUserInfo()
    } else {
      message.error(response.message || '保存失败')
    }
  } catch (error) {

    message.error(error.message || '保存失败')
  }
}

// 修改密码
const handleChangePassword = async () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    message.warning('请填写完整')
    return
  }
  
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    message.warning('两次密码输入不一致')
    return
  }
  
  if (passwordForm.newPassword.length < 6) {
    message.warning('新密码长度至少6位')
    return
  }
  
  try {
    const response = await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    
    if (response.code === 200) {
      message.success('密码修改成功，请重新登录')
      changePasswordVisible.value = false
      
      // 清空表单
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
      
      // 清除登录信息，跳转登录页
      setTimeout(() => {
        userStore.clearUserInfo()
        router.push('/login')
      }, 1500)
    } else {
      message.error(response.message || '修改失败')
    }
  } catch (error) {
    message.error(error?.response?.data?.message || error?.message || '修改失败')
  }
}

// 初始化编辑表单
const initEditForm = async () => {
  editForm.realName = userInfo.value.realName || ''
  editForm.phone = userInfo.value.phone || ''
  editForm.gender = userInfo.value.gender || 0
  editForm.birthDate = userInfo.value.birthDate || ''
  const rawAvatarUrl = sanitizeStoredMediaUrl(userInfo.value.avatar)
  editForm.avatar = rawAvatarUrl
  avatarPreview.value = rawAvatarUrl ? await resolveOssPreviewUrl(rawAvatarUrl, 60) : ''
}

// 打开编辑对话框
const openEditDialog = async () => {
  await initEditForm()
  editDialogVisible.value = true
}

// 刷新用户信息（获取带签名的头像URL）
const refreshUserInfo = async () => {
  try {
    const response = await getUserInfo()
    if (response.code === 200 && response.data) {
      userInfo.value = response.data
      userStore.setUserInfo(userInfo.value)
    }
  } catch (error) {

  }
}

// 加载用户设置
const loadSettings = async () => {
  try {
    const response = await getUserSettings()
    if (response.code === 200 && response.data) {
      settings.notification = response.data.notification !== false
      settings.smsReminder = response.data.smsReminder !== false
    }
  } catch (error) {

  }
}

// 保存设置
const saveSettings = async () => {
  try {
    await updateUserSettings({
      notification: settings.notification,
      smsReminder: settings.smsReminder
    })
    message.success('设置已保存')
  } catch (error) {
    message.error('保存设置失败')
  }
}

onMounted(() => {
  // 页面加载时刷新用户信息以获取带签名的头像URL
  refreshUserInfo()
  // 加载用户设置
  loadSettings()
})
</script>

<style scoped lang="scss">
.profile-container {
  max-width: 1200px;
  margin: 0 auto;

  .profile-card {
    text-align: center;

    .el-avatar {
      margin-bottom: 20px;
    }

    h2 {
      margin: 0 0 10px 0;
      font-size: 24px;
      color: #333;
    }

    .user-role {
      margin: 0 0 20px 0;
      color: #909399;
    }

    .user-info {
      margin: 20px 0;
    }
  }

  .stats-row {
    margin-bottom: 20px;

    .stat-card {
      cursor: pointer;
      transition: all 0.3s;

      &:hover {
        transform: translateY(-5px);
      }
    }
  }

  h3 {
    margin: 0;
    font-size: 16px;
    color: #333;
  }

  .setting-desc {
    margin-left: 10px;
    color: #909399;
    font-size: 13px;
  }

  .avatar-edit-wrapper {
    display: flex;
    align-items: center;
    gap: 20px;

    .avatar-uploader-inline {
      :deep(.el-upload) {
        border: none;
      }
    }
  }
}
</style>

