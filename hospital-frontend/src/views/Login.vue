<template>
  <div class="login-container">
    <div class="login-box">
      <!-- 登录表单 -->
      <div class="login-right single">
        <div class="login-form-wrapper">
          <h2>体质辨识</h2>
          <p class="subtitle">探索您的先天体质与健康平衡</p>

          <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" @submit.prevent="handleLogin">
            <el-form-item prop="username">
              <el-input v-model="loginForm.username" placeholder="手机号或用户名" size="large" :prefix-icon="User" clearable
                @blur="handleFieldBlur('username')" />
            </el-form-item>

            <el-form-item prop="password">
              <el-input v-model="loginForm.password" type="password" placeholder="密码" size="large"
                :prefix-icon="Lock" show-password clearable @keyup.enter="handleLogin" @blur="handleFieldBlur('password')" />
            </el-form-item>

            <el-form-item label-width="0" class="captcha-row">
              <div class="captcha-line">
                <el-input
                  v-model="loginForm.captchaCode"
                  placeholder="验证码"
                  size="large"
                  maxlength="6"
                  clearable
                  class="captcha-input"
                  @keyup.enter="handleLogin"
                />
                <img
                  v-if="captchaImageSrc"
                  :src="captchaImageSrc"
                  alt=""
                  class="captcha-img"
                  @click="refreshCaptcha"
                />
              </div>
              <el-link type="primary" :underline="false" class="captcha-refresh" @click="refreshCaptcha">换一张</el-link>
            </el-form-item>

          <el-form-item>
            <div class="options-row">
              <el-checkbox v-model="loginForm.rememberMe">记住用户名</el-checkbox>
              <el-link type="info" :underline="false">找回密码</el-link>
            </div>
          </el-form-item>

            <el-form-item>
              <el-button
                type="primary"
                size="large"
                :loading="loading"
                :disabled="!canSubmitLogin"
                @click="handleLogin"
                class="login-button"
              >
                {{ loading ? '登录中...' : '登 录' }}
              </el-button>
            </el-form-item>

            <div class="divider"></div>
            <div class="form-footer">
              <span>新用户？</span>
              <el-link type="primary" @click="goToRegister">立即加入</el-link>
            </div>
          </el-form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, inject, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import message from '@/plugins/message'
import { User, Lock } from '@element-plus/icons-vue'
import { login, getUserInfo, getCaptchaImage } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { getAppConfig, loadAppConfig } from '@/config/runtimeConfig'
import { configureRequestClient } from '@/api/request'
import { useFormValidation } from '@/composables/useFormValidation'

const router = useRouter()
const userStore = useUserStore()
const appConfig = inject('appConfig', getAppConfig())
const systemTitle = computed(() => appConfig?.systemInfo?.name || '中医体质辨识系统')

// 表单数据
const loginForm = reactive({
  username: '',
  password: '',
  captchaId: '',
  captchaCode: '',
  rememberMe: true
})

const captchaImageSrc = ref('')

const canSubmitLogin = computed(() => String(loginForm.captchaCode || '').trim().length > 0)

const refreshCaptcha = async () => {
  loginForm.captchaCode = ''
  loginForm.captchaId = ''
  captchaImageSrc.value = ''
  try {
    const res = await getCaptchaImage()
    if (res.code === 200 && res.data) {
      loginForm.captchaId = res.data.captchaId || ''
      const b64 = res.data.imageBase64 || ''
      captchaImageSrc.value = b64 ? `data:image/png;base64,${b64}` : ''
    }
  } catch {
    captchaImageSrc.value = ''
  }
}

// 表单验证规则
const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ]
}

const loginFormRef = ref(null)
const loading = ref(false)

// 创建字段失焦处理函数
const { handleFieldBlur } = useFormValidation(loginFormRef)

// 初始化记住的用户名
if (typeof window !== 'undefined') {
  const savedUsername = localStorage.getItem('hospital_saved_username')
  if (savedUsername) {
    loginForm.username = savedUsername
  }
}

onMounted(() => {
  refreshCaptcha()
})

// 处理登录
const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true

    try {
      const res = await login(loginForm)

      if (res.code === 200) {
        // 记住用户名
        try {
          if (loginForm.rememberMe) {
            localStorage.setItem('hospital_saved_username', loginForm.username || '')
          } else {
            localStorage.removeItem('hospital_saved_username')
          }
        } catch (e) {
          // 本地存储异常忽略
        }
        // 保存Token和用户信息
        userStore.setToken(res.data.token)
        userStore.setRefreshToken(res.data.refreshToken || '')
        userStore.setUserInfo(res.data)

        // 登录成功后加载配置
        try {
          const runtimeConfig = await loadAppConfig()
          configureRequestClient(runtimeConfig)
          if (typeof window !== 'undefined') {
            window.__APP_CONFIG__ = runtimeConfig
          }
        } catch (error) {
          console.warn('加载运行时配置失败，使用默认配置', error)
        }

        // 登录后立即刷新一次用户信息，获取签名后的头像等动态字段
        try {
          const infoRes = await getUserInfo()
          if (infoRes.code === 200 && infoRes.data) {
            userStore.setUserInfo(infoRes.data)
          }
        } catch (error) {

        }

        message.success('登录成功！')

        // 根据角色跳转到对应首页（当前仅保留：管理员=1 / 患者=0）
        const roleType = res.data.roleType
        let homePath = '/patient/home'

        // roleType=1 为管理员
        if (roleType === 1) {
          homePath = '/admin/dashboard'
        }

        // 立即跳转，不使用setTimeout
        router.push(homePath)
      }
    } catch (error) {
      const msg = (error && error.message) || '登录失败，请重试'
      message.error(msg)
      await refreshCaptcha()
    } finally {
      loading.value = false
    }
  })
}

// 跳转注册页
const goToRegister = () => {
  router.push('/register')
}
</script>

<style scoped lang="scss">
.login-container {
  width: 100%;
  min-height: 100vh;
  background: #f5f7fa;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 32px 20px;
}

.login-box {
  width: 440px;
  min-height: 520px;
  background: white;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.06);
  display: flex;
  overflow: hidden;
}

.login-left {
  flex: 1;
  background: #f0f6ff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 56px 40px;
  color: #1f2937;
  border-right: 1px solid #e5e7eb;
}

.hospital-info {
  h1 {
    font-size: 30px;
    margin-bottom: 12px;
    font-weight: 600;
    color: #1f2937;
  }

  p {
    font-size: 14px;
    margin-bottom: 36px;
    color: #6b7280;
  }
}

.features {
  .feature-item {
    display: flex;
    align-items: center;
    margin-bottom: 14px;
    font-size: 14px;
    color: #374151;

    .el-icon {
      font-size: 20px;
      margin-right: 10px;
      color: #3b82f6;
    }
  }
}

.login-right {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 56px 48px;
}
.login-right.single { width: 100%; }

.login-form-wrapper {
  width: 100%;
  max-width: 360px;

  h2 {
    font-size: 24px;
    color: #1f2937;
    margin-bottom: 8px;
    font-weight: 600;
    text-align: center;
  }

  .subtitle {
    color: #6b7280;
    margin-bottom: 28px;
    font-size: 13px;
    text-align: center;
  }
}

.login-button {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  margin-top: 6px;
  background-color: #0f3e36;
  border-color: #0f3e36;
}
.login-button:hover, .login-button:focus { background-color: #0d362f; border-color: #0d362f; }

.form-footer {
  text-align: center;
  color: #6b7280;
  margin-top: 14px;

  .el-link {
    margin-left: 5px;
    font-weight: 600;
  }
}

:deep(.el-input__wrapper) {
  padding: 12px 15px;
}

:deep(.el-form-item) {
  margin-bottom: 24px;
}
.options-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  font-size: 13px;
  color: #666;
}

.captcha-line {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.captcha-input {
  flex: 1;
  min-width: 0;
}
.captcha-img {
  height: 40px;
  width: 110px;
  object-fit: cover;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid #e5e7eb;
}
.captcha-refresh {
  margin-top: 6px;
  font-size: 13px;
}
</style>
