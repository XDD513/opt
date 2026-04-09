<template>
  <div class="settings-test-page">
    <el-page-header title="返回" content="系统设置生效检测" @back="$router.back()" />

    <el-alert
      type="info"
      class="mt16"
      show-icon
      description="该页面用于快速对比数据库 system_config 与前端运行时配置 /api/config 的数据差异，以确认管理员保存配置后是否已经在各端生效。"
    />

    <el-row :gutter="16" class="mt16">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>运行时配置（/api/config）</span>
              <div class="actions">
                <el-button type="primary" size="small" :loading="runtimeLoading" @click="refreshRuntimeConfig">
                  <el-icon><Refresh /></el-icon>
                  刷新
                </el-button>
              </div>
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="系统名称">{{ runtimeName }}</el-descriptions-item>
            <el-descriptions-item label="系统版本">{{ runtimeVersion }}</el-descriptions-item>
            <el-descriptions-item label="维护模式">
              <el-tag :type="runtimeMaintenance ? 'warning' : 'success'">
                {{ runtimeMaintenance ? '开启' : '关闭' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="最近刷新">{{ formatTime(runtimeFetchedAt) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>数据库配置（system_config）</span>
              <div class="actions">
                <el-button type="primary" size="small" :loading="dbLoading" @click="refreshDbConfig">
                  <el-icon><Refresh /></el-icon>
                  刷新
                </el-button>
              </div>
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="系统名称">{{ dbBasic.systemName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="系统版本">{{ dbBasic.systemVersion || '—' }}</el-descriptions-item>
            <el-descriptions-item label="维护模式">
              <el-tag :type="dbBasic.maintenanceMode ? 'warning' : 'success'">
                {{ dbBasic.maintenanceMode ? '开启' : '关闭' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="最近刷新">{{ formatTime(dbFetchedAt) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="mt16">
      <template #header>
        <div class="card-header">
          <span>差异对比</span>
          <el-button type="primary" size="small" :loading="isRefreshing" @click="refreshAll">
            <el-icon><Refresh /></el-icon>
            同步刷新
          </el-button>
        </div>
      </template>
      <el-table :data="comparisonRows" border>
        <el-table-column prop="label" label="配置项" width="160" />
        <el-table-column label="运行时配置">
          <template #default="{ row }">{{ row.runtimeDisplay }}</template>
        </el-table-column>
        <el-table-column label="数据库配置">
          <template #default="{ row }">{{ row.dbDisplay }}</template>
        </el-table-column>
        <el-table-column label="状态" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="row.inSync ? 'success' : 'warning'">
              {{ row.inSync ? '已同步' : '未同步' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="mt16">
      <template #header>
        <span>原始数据</span>
      </template>
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <h4>运行时配置 JSON</h4>
          <pre class="json-viewer">{{ prettify(runtimeData) }}</pre>
        </el-col>
        <el-col :xs="24" :md="12">
          <h4>系统设置 JSON</h4>
          <pre class="json-viewer">{{ prettify(dbData) }}</pre>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref } from 'vue'
import dayjs from 'dayjs'
import message from '@/plugins/message'
import { Refresh } from '@element-plus/icons-vue'
import { getRuntimeConfig, getSystemSettings } from '@/api/system'
import { getAppConfig } from '@/config/runtimeConfig'

const runtimeData = ref(getAppConfig())
const runtimeLoading = ref(false)
const runtimeFetchedAt = ref(null)

const dbData = ref(null)
const dbLoading = ref(false)
const dbFetchedAt = ref(null)

const isRefreshing = computed(() => runtimeLoading.value || dbLoading.value)

const runtimeName = computed(() => runtimeData.value?.systemInfo?.name || '—')
const runtimeVersion = computed(() => runtimeData.value?.systemInfo?.version || '—')
const runtimeMaintenance = computed(() => Boolean(runtimeData.value?.systemInfo?.maintenanceMode))
const runtimeBasic = computed(() => runtimeData.value?.basic || {})
const runtimeNotification = computed(() => runtimeData.value?.notification || {})
const runtimeSecurity = computed(() => runtimeData.value?.security || {})
const runtimeEmail = computed(() => runtimeData.value?.email || {})

const dbBasic = computed(() => dbData.value?.basic || {})
const dbNotification = computed(() => dbData.value?.notification || {})
const dbSecurity = computed(() => dbData.value?.security || {})
const dbEmail = computed(() => dbData.value?.email || {})

const comparisonRows = computed(() => {
  return [
    {
      key: 'systemName',
      label: '系统名称',
      runtimeDisplay: runtimeName.value,
      dbDisplay: dbBasic.value.systemName || '—',
      inSync: normalizeValue(runtimeName.value) === normalizeValue(dbBasic.value.systemName)
    },
    {
      key: 'systemVersion',
      label: '系统版本',
      runtimeDisplay: runtimeVersion.value,
      dbDisplay: dbBasic.value.systemVersion || '—',
      inSync: runtimeVersion.value === (dbBasic.value.systemVersion || '')
    },
    {
      key: 'maintenanceMode',
      label: '维护模式',
      runtimeDisplay: runtimeMaintenance.value ? '开启' : '关闭',
      dbDisplay: dbBasic.value.maintenanceMode ? '开启' : '关闭',
      inSync: Boolean(runtimeMaintenance.value) === Boolean(dbBasic.value.maintenanceMode)
    },
    {
      key: 'advanceDays',
      label: '可提前预约天数',
      runtimeDisplay: runtimeBasic.value.advanceDays ?? '—',
      dbDisplay: dbBasic.value.advanceDays ?? '—',
      inSync: Number(runtimeBasic.value.advanceDays ?? NaN) === Number(dbBasic.value.advanceDays ?? NaN)
    },
    {
      key: 'cancelHours',
      label: '取消预约提前小时数',
      runtimeDisplay: runtimeBasic.value.cancelHours ?? '—',
      dbDisplay: dbBasic.value.cancelHours ?? '—',
      inSync: Number(runtimeBasic.value.cancelHours ?? NaN) === Number(dbBasic.value.cancelHours ?? NaN)
    },
    {
      key: 'paymentTimeout',
      label: '支付超时时间(分钟)',
      runtimeDisplay: runtimeBasic.value.paymentTimeout ?? '—',
      dbDisplay: dbBasic.value.paymentTimeout ?? '—',
      inSync: Number(runtimeBasic.value.paymentTimeout ?? NaN) === Number(dbBasic.value.paymentTimeout ?? NaN)
    },
    {
      key: 'notificationReminder',
      label: '预约提醒开关',
      runtimeDisplay: formatBool(runtimeNotification.value.appointmentReminder),
      dbDisplay: formatBool(dbNotification.value.appointmentReminder),
      inSync: Boolean(runtimeNotification.value.appointmentReminder) === Boolean(dbNotification.value.appointmentReminder)
    },
    {
      key: 'reminderHours',
      label: '提醒提前小时',
      runtimeDisplay: runtimeNotification.value.reminderHours ?? '—',
      dbDisplay: dbNotification.value.reminderHours ?? '—',
      inSync: Number(runtimeNotification.value.reminderHours ?? NaN) === Number(dbNotification.value.reminderHours ?? NaN)
    },
    {
      key: 'smsNotification',
      label: '短信通知',
      runtimeDisplay: formatBool(runtimeNotification.value.smsEnabled),
      dbDisplay: formatBool(dbNotification.value.smsNotification),
      inSync: Boolean(runtimeNotification.value.smsEnabled) === Boolean(dbNotification.value.smsNotification)
    },
    {
      key: 'emailNotification',
      label: '邮件通知',
      runtimeDisplay: formatBool(runtimeNotification.value.emailEnabled),
      dbDisplay: formatBool(dbNotification.value.emailNotification),
      inSync: Boolean(runtimeNotification.value.emailEnabled) === Boolean(dbNotification.value.emailNotification)
    },
    {
      key: 'systemNotification',
      label: '系统内通知',
      runtimeDisplay: formatBool(runtimeNotification.value.systemEnabled),
      dbDisplay: formatBool(dbNotification.value.systemNotification),
      inSync: Boolean(runtimeNotification.value.systemEnabled) === Boolean(dbNotification.value.systemNotification)
    },
    {
      key: 'minPasswordLength',
      label: '密码最小长度',
      runtimeDisplay: runtimeSecurity.value.minPasswordLength ?? '—',
      dbDisplay: dbSecurity.value.minPasswordLength ?? '—',
      inSync: Number(runtimeSecurity.value.minPasswordLength ?? NaN) === Number(dbSecurity.value.minPasswordLength ?? NaN)
    },
    {
      key: 'loginLockEnabled',
      label: '登录失败锁定',
      runtimeDisplay: formatBool(runtimeSecurity.value.loginLockEnabled),
      dbDisplay: formatBool(dbSecurity.value.loginLockEnabled),
      inSync: Boolean(runtimeSecurity.value.loginLockEnabled) === Boolean(dbSecurity.value.loginLockEnabled)
    },
    {
      key: 'maxLoginAttempts',
      label: '最大失败次数',
      runtimeDisplay: runtimeSecurity.value.maxLoginAttempts ?? '—',
      dbDisplay: dbSecurity.value.maxLoginAttempts ?? '—',
      inSync: Number(runtimeSecurity.value.maxLoginAttempts ?? NaN) === Number(dbSecurity.value.maxLoginAttempts ?? NaN)
    },
    {
      key: 'lockDuration',
      label: '锁定时间(分钟)',
      runtimeDisplay: runtimeSecurity.value.lockDurationMinutes ?? '—',
      dbDisplay: dbSecurity.value.lockDuration ?? '—',
      inSync: Number(runtimeSecurity.value.lockDurationMinutes ?? NaN) === Number(dbSecurity.value.lockDuration ?? NaN)
    },
    {
      key: 'sessionTimeout',
      label: '会话超时(分钟)',
      runtimeDisplay: runtimeSecurity.value.sessionTimeoutMinutes ?? '—',
      dbDisplay: dbSecurity.value.sessionTimeout ?? '—',
      inSync: Number(runtimeSecurity.value.sessionTimeoutMinutes ?? NaN) === Number(dbSecurity.value.sessionTimeout ?? NaN)
    },
    {
      key: 'smtpHost',
      label: 'SMTP 服务器',
      runtimeDisplay: runtimeEmail.value.smtpHost || '—',
      dbDisplay: dbEmail.value.smtpHost || '—',
      inSync: normalizeValue(runtimeEmail.value.smtpHost) === normalizeValue(dbEmail.value.smtpHost)
    },
    {
      key: 'smtpPort',
      label: 'SMTP 端口',
      runtimeDisplay: runtimeEmail.value.smtpPort ?? '—',
      dbDisplay: dbEmail.value.smtpPort ?? '—',
      inSync: Number(runtimeEmail.value.smtpPort ?? NaN) === Number(dbEmail.value.smtpPort ?? NaN)
    },
    {
      key: 'fromEmail',
      label: '发送邮箱',
      runtimeDisplay: runtimeEmail.value.fromEmail || '—',
      dbDisplay: dbEmail.value.fromEmail || '—',
      inSync: normalizeValue(runtimeEmail.value.fromEmail) === normalizeValue(dbEmail.value.fromEmail)
    },
    {
      key: 'sslEnabled',
      label: '启用 SSL',
      runtimeDisplay: formatBool(runtimeEmail.value.sslEnabled),
      dbDisplay: formatBool(dbEmail.value.sslEnabled),
      inSync: Boolean(runtimeEmail.value.sslEnabled) === Boolean(dbEmail.value.sslEnabled)
    }
  ]
})

const refreshRuntimeConfig = async () => {
  runtimeLoading.value = true
  try {
    const res = await getRuntimeConfig()
    runtimeData.value = res.data
    runtimeFetchedAt.value = new Date()
    message.success('运行时配置已刷新')
  } catch (error) {
    message.error('获取运行时配置失败')
  } finally {
    runtimeLoading.value = false
  }
}

const refreshDbConfig = async () => {
  dbLoading.value = true
  try {
    const res = await getSystemSettings()
    dbData.value = res.data
    dbFetchedAt.value = new Date()
    message.success('系统设置已刷新')
  } catch (error) {
    message.error('获取系统设置失败')
  } finally {
    dbLoading.value = false
  }
}

const refreshAll = async () => {
  await Promise.all([refreshRuntimeConfig(), refreshDbConfig()])
}

const formatTime = (time) => {
  if (!time) return '—'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

const prettify = (value) => {
  if (!value) {
    return '无数据'
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch (error) {
    return String(value)
  }
}

const normalizeValue = (value) => {
  if (value === undefined || value === null) return ''
  return String(value).trim()
}

const formatBool = (val) => (Boolean(val) ? '开启' : '关闭')

onMounted(() => {
  refreshAll()
})
</script>

<style scoped lang="scss">
.settings-test-page {
  padding: 16px;
}

.mt16 {
  margin-top: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.actions {
  display: flex;
  gap: 8px;
}

.json-viewer {
  background: #111827;
  color: #e5e7eb;
  padding: 16px;
  border-radius: 8px;
  min-height: 200px;
  overflow: auto;
  font-size: 12px;
}
</style>

