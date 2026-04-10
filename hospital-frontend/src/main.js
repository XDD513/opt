import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'

// 全局补丁 & 自定义样式
import './utils/passiveEventPatch'
import './styles/admin-common.scss'
import './styles/responsive-global.scss'
import './styles/patient.scss'
import './styles/message.scss'

import message from '@/plugins/message'
import { loadAppConfig } from '@/config/runtimeConfig'
import { configureRequestClient } from '@/api/request'

if (typeof window !== 'undefined') {
  window.global = window.global || window
  window.process = window.process || { env: {} }
}

import { getAppConfig } from '@/config/runtimeConfig'

const bootstrap = async () => {
  // 启动时使用默认配置，不调用 config 接口（需要登录后才能访问）
  const runtimeConfig = getAppConfig()
  configureRequestClient(runtimeConfig)

  if (typeof window !== 'undefined') {
    window.__APP_CONFIG__ = runtimeConfig
  }

  const app = createApp(App)
  const pinia = createPinia()

  // 注册所有图标
  for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
  }

  app.use(pinia)
  app.use(router)
  app.use(ElementPlus, { locale: zhCn })
  app.config.globalProperties.$message = message
  app.provide('appConfig', runtimeConfig)

  app.mount('#app')
}

bootstrap().catch((error) => {
  console.error('[bootstrap] 应用启动失败', error)
})


