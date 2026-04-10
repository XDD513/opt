<template>
  <div class="register-container">
    <div class="register-box">
      <!-- 头部 -->
      <div class="register-header">
        <h1>用户注册</h1>
        <p>创建您的账号，开启健康养生之旅</p>
      </div>

      <!-- 注册表单 -->
      <div class="register-form-wrapper">
        <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" label-width="100px"
          @submit.prevent="handleRegister">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="registerForm.username" placeholder="请输入用户名（字母、数字、下划线）" clearable
              @blur="() => handleFieldBlur('username')">
              <template #prefix>
                <el-icon>
                  <User />
                </el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input v-model="registerForm.password" type="password" placeholder="请输入密码" show-password clearable
              @blur="handleFieldBlur('password')">
              <template #prefix>
                <el-icon>
                  <Lock />
                </el-icon>
              </template>
            </el-input>
            <div class="pwd-strength" v-if="registerForm.password">
              <div :class="['bar', strengthLevel >= 1 ? 'active' : '']"></div>
              <div :class="['bar', strengthLevel >= 2 ? 'active' : '']"></div>
              <div :class="['bar', strengthLevel >= 3 ? 'active' : '']"></div>
              <span class="label">{{ strengthText }}</span>
            </div>
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="registerForm.confirmPassword" type="password" placeholder="请再次输入密码" show-password
              clearable @blur="handleFieldBlur('confirmPassword')">
              <template #prefix>
                <el-icon>
                  <Lock />
                </el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="真实姓名" prop="realName">
            <el-input v-model="registerForm.realName" placeholder="请输入真实姓名" clearable
              @blur="handleFieldBlur('realName')">
              <template #prefix>
                <el-icon>
                  <Avatar />
                </el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="手机号" prop="phone">
            <el-input v-model="registerForm.phone" placeholder="请输入手机号" maxlength="11" clearable
              @blur="() => handleFieldBlur('phone')">
              <template #prefix>
                <el-icon>
                  <Iphone />
                </el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="身份证号" prop="idCard">
            <el-input v-model="registerForm.idCard" placeholder="请输入身份证号（选填）" maxlength="18" clearable
              @blur="handleFieldBlur('idCard')">
              <template #prefix>
                <el-icon>
                  <Postcard />
                </el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="性别" prop="gender">
            <el-radio-group v-model="registerForm.gender" @change="handleFieldBlur('gender')">
              <el-radio :label="1">男</el-radio>
              <el-radio :label="2">女</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="出生日期" prop="birthDate" required>
            <el-date-picker v-model="registerForm.birthDate" type="date" placeholder="请选择出生日期" format="YYYY-MM-DD"
              value-format="YYYY-MM-DD" style="width: 100%" @blur="handleFieldBlur('birthDate')" />
          </el-form-item>

          <el-form-item>
            <el-checkbox v-model="agreed">我已阅读并同意《用户服务协议》和《隐私政策》</el-checkbox>
          </el-form-item>

          <el-form-item>
            <div class="button-group">
              <el-button type="primary" size="large" :loading="loading" :disabled="!agreed" @click="handleRegister">
                {{ loading ? '注册中...' : '立即注册' }}
              </el-button>
              <el-button size="large" @click="goToLogin">返回登录</el-button>
            </div>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import message from '@/plugins/message'
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'

import { User, Lock, Avatar, Iphone, Postcard } from '@element-plus/icons-vue'
import { register, checkUsername, checkPhone } from '@/api/user'
import { useFormValidation } from '@/composables/useFormValidation'

const router = useRouter()

// 表单数据
const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  phone: '',
  idCard: '',
  gender: 0,
  birthDate: ''
})

// 自定义验证：两次密码一致
const validatePassword = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

// 表单验证规则
const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在3-20个字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在6-20个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validatePassword, trigger: 'blur' }
  ],
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
    {
      pattern: /^[\u4e00-\u9fa5]+(·[\u4e00-\u9fa5]+)*$/,
      message: '姓名格式不正确',
      trigger: 'blur'
    }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  idCard: [
    {
      validator: (rule, value, callback) => {
        const v = (value || '').trim()
        if (!v) {
          callback()
          return
        }
        if (/^(\d{15}|\d{17}([0-9]|X|x))$/.test(v)) {
          callback()
        } else {
          callback(new Error('身份证号格式不正确'))
        }
      },
      trigger: 'blur'
    }
  ],
  birthDate: [
    { required: true, message: '请填写出生日期', trigger: 'change' }
  ]
}

const registerFormRef = ref(null)
const loading = ref(false)
const agreed = ref(false)

// 密码强度计算
const strengthLevel = computed(() => {
  const pwd = registerForm.password || ''
  let level = 0
  if (pwd.length >= 6) level++
  if (/[A-Z]/.test(pwd) && /[a-z]/.test(pwd)) level++
  if (/\d/.test(pwd) || /[^A-Za-z0-9]/.test(pwd)) level++
  return Math.min(level, 3)
})
const strengthText = computed(() => {
  return ['弱', '中', '强'][Math.max(0, strengthLevel.value - 1)] || '弱'
})

// 检查用户名是否存在
const checkUsernameExists = async () => {
  if (!registerForm.username) return

  try {
    const res = await checkUsername(registerForm.username)
    if (res.data) {
      message.warning('该用户名已被注册')
    }
  } catch (error) {

  }
}

// 检查手机号是否存在
const checkPhoneExists = async () => {
  if (!registerForm.phone) return

  try {
    const res = await checkPhone(registerForm.phone)
    if (res.data) {
      message.warning('该手机号已被注册')
    }
  } catch (error) {

  }
}

// 创建字段失焦处理函数
const { handleFieldBlur } = useFormValidation(registerFormRef, async (fieldName) => {
  // 根据字段名称执行额外的处理（如检查用户名是否存在）
  if (fieldName === 'username' && registerForm.username) {
    await checkUsernameExists()
  } else if (fieldName === 'phone' && registerForm.phone) {
    await checkPhoneExists()
  }
})

// 处理注册
const handleRegister = async () => {
  if (!registerFormRef.value) return
  if (!agreed.value) {
    message.warning('请先阅读并同意相关协议')
    return
  }

  await registerFormRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true

    try {
      const res = await register(registerForm)

      if (res.code === 200) {
        message.success('注册成功！请登录')

        setTimeout(() => {
          router.push('/login')
        }, 1000)
      }
    } catch (error) {

    } finally {
      loading.value = false
    }
  })
}

// 返回登录
const goToLogin = () => {
  router.push('/login')
}
</script>

<style scoped lang="scss">
.register-container {
  width: 100%;
  min-height: 100vh;
  background: #f5f7fa;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 32px 20px;
}

.register-box {
  width: 720px;
  background: white;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.register-header {
  background: #ffffff;
  color: #1f2937;
  text-align: center;
  padding: 28px 20px;
  border-bottom: 1px solid #e5e7eb;

  h1 {
    font-size: 24px;
    margin-bottom: 6px;
    font-weight: 600;
  }

  p {
    font-size: 13px;
    color: #6b7280;
  }
}

.register-form-wrapper {
  padding: 32px 48px;
}

.button-group {
  display: flex;
  gap: 15px;
  width: 100%;

  .el-button {
    flex: 1;
    height: 44px;
    font-size: 15px;
    font-weight: 600;
  }
}

:deep(.el-form-item) {
  margin-bottom: 22px;
}

:deep(.el-input__wrapper) {
  padding: 10px 15px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: #333;
}
.pwd-strength {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;

  .bar {
    width: 36px;
    height: 6px;
    border-radius: 4px;
    background: #e5e7eb;
    transition: background-color 0.3s ease;
  }
  .bar.active:nth-child(1) { background: #f59e0b; }
  .bar.active:nth-child(2) { background: #10b981; }
  .bar.active:nth-child(3) { background: #3b82f6; }

  .label {
    margin-left: 6px;
    font-size: 12px;
    color: #666;
  }
}

@media (max-width: 768px) {
  .register-box {
    width: 100%;
    max-width: 100%;
  }
  .register-form-wrapper {
    padding: 24px 16px;
  }
}
</style>
