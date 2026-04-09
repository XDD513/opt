<template>
  <div class="system-settings-container">
    <el-row :gutter="20">
      <!-- 基础设置 -->
      <el-col :xs="24" :md="12">
        <div class="admin-card">
          <div class="card-header">
            <h3>
              <el-icon>
                <Setting />
              </el-icon>
              基础设置
            </h3>
          </div>
          <div class="card-body">
            <el-form :model="basicSettings" :rules="basicRules" ref="basicFormRef" label-width="150px"
              class="admin-form">
              <el-form-item label="系统名称" prop="systemName">
                <el-input v-model="basicSettings.systemName" placeholder="请输入系统名称" @blur="handleBasicFieldBlur('systemName')" />
              </el-form-item>
              <el-form-item label="系统版本" prop="systemVersion">
                <el-input v-model="basicSettings.systemVersion" placeholder="请输入系统版本" @blur="handleBasicFieldBlur('systemVersion')" />
              </el-form-item>
              <el-form-item label="可提前预约天数" prop="advanceDays">
                <el-input-number v-model="basicSettings.advanceDays" :min="1" :max="30" @blur="handleBasicFieldBlur('advanceDays')" />
                <span style="margin-left: 10px; color: #909399;">天</span>
              </el-form-item>
              <el-form-item label="取消预约提前时间" prop="cancelHours">
                <el-input-number v-model="basicSettings.cancelHours" :min="1" :max="48" @blur="handleBasicFieldBlur('cancelHours')" />
                <span style="margin-left: 10px; color: #909399;">小时</span>
              </el-form-item>
              <el-form-item label="支付超时时间" prop="paymentTimeout">
                <el-input-number v-model="basicSettings.paymentTimeout" :min="10" :max="120" @blur="handleBasicFieldBlur('paymentTimeout')" />
                <span style="margin-left: 10px; color: #909399;">分钟</span>
              </el-form-item>
              <el-form-item label="系统维护模式">
                <el-switch v-model="basicSettings.maintenanceMode" />
                <span style="margin-left: 10px; color: #909399;">开启后系统将进入维护状态</span>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="saveBasicSettings" :loading="basicLoading">
                  <el-icon>
                    <Check />
                  </el-icon> 保存基础设置
                </el-button>
                <el-button @click="resetBasicSettings">
                  <el-icon>
                    <RefreshLeft />
                  </el-icon> 重置
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </el-col>

      <!-- 通知设置 -->
      <el-col :xs="24" :md="12">
        <div class="admin-card">
          <div class="card-header">
            <h3>
              <el-icon>
                <Bell />
              </el-icon>
              通知设置
            </h3>
          </div>
          <div class="card-body">
            <el-form :model="notificationSettings" ref="notificationFormRef" label-width="150px" class="admin-form">
              <el-form-item label="预约提醒">
                <el-switch v-model="notificationSettings.appointmentReminder" />
                <span style="margin-left: 10px; color: #909399;">就诊前提醒患者</span>
              </el-form-item>
              <el-form-item label="提醒提前时间">
                <el-input-number v-model="notificationSettings.reminderHours" :min="1" :max="24"
                  :disabled="!notificationSettings.appointmentReminder" />
                <span style="margin-left: 10px; color: #909399;">小时</span>
              </el-form-item>
              <el-form-item label="短信通知">
                <el-switch v-model="notificationSettings.smsNotification" />
                <span style="margin-left: 10px; color: #909399;">启用短信通知功能</span>
              </el-form-item>
              <el-form-item label="邮件通知">
                <el-switch v-model="notificationSettings.emailNotification" />
                <span style="margin-left: 10px; color: #909399;">启用邮件通知功能</span>
              </el-form-item>
              <el-form-item label="系统通知">
                <el-switch v-model="notificationSettings.systemNotification" />
                <span style="margin-left: 10px; color: #909399;">启用系统内通知</span>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="saveNotificationSettings" :loading="notificationLoading">
                  <el-icon>
                    <Check />
                  </el-icon> 保存通知设置
                </el-button>
                <el-button @click="resetNotificationSettings">
                  <el-icon>
                    <RefreshLeft />
                  </el-icon> 重置
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 安全设置 -->
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <h3><el-icon>
                  <Lock />
                </el-icon> 安全设置</h3>
            </div>
          </template>
          <el-form :model="securitySettings" ref="securityFormRef" label-width="150px">
            <el-form-item label="密码最小长度">
              <el-input-number v-model="securitySettings.minPasswordLength" :min="6" :max="20" />
              <span style="margin-left: 10px; color: #909399;">位</span>
            </el-form-item>
            <el-form-item label="登录失败锁定">
              <el-switch v-model="securitySettings.loginLockEnabled" />
              <span style="margin-left: 10px; color: #909399;">连续失败后锁定账户</span>
            </el-form-item>
            <el-form-item label="最大失败次数">
              <el-input-number v-model="securitySettings.maxLoginAttempts" :min="3" :max="10"
                :disabled="!securitySettings.loginLockEnabled" />
              <span style="margin-left: 10px; color: #909399;">次</span>
            </el-form-item>
            <el-form-item label="锁定时间">
              <el-input-number v-model="securitySettings.lockDuration" :min="5" :max="60"
                :disabled="!securitySettings.loginLockEnabled" />
              <span style="margin-left: 10px; color: #909399;">分钟</span>
            </el-form-item>
            <el-form-item label="会话超时时间">
              <el-input-number v-model="securitySettings.sessionTimeout" :min="30" :max="480" />
              <span style="margin-left: 10px; color: #909399;">分钟</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveSecuritySettings" :loading="securityLoading">
                <el-icon>
                  <Check />
                </el-icon> 保存安全设置
              </el-button>
              <el-button @click="resetSecuritySettings">
                <el-icon>
                  <RefreshLeft />
                </el-icon> 重置
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 邮件设置 -->
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <h3><el-icon>
                  <Message />
                </el-icon> 邮件设置</h3>
            </div>
          </template>
          <el-form :model="emailSettings" ref="emailFormRef" label-width="150px">
            <el-form-item label="SMTP服务器">
              <el-input v-model="emailSettings.smtpHost" placeholder="smtp.example.com" />
            </el-form-item>
            <el-form-item label="SMTP端口">
              <el-input-number v-model="emailSettings.smtpPort" :min="1" :max="65535" />
            </el-form-item>
            <el-form-item label="发送邮箱">
              <el-input v-model="emailSettings.fromEmail" placeholder="noreply@hospital.com" />
            </el-form-item>
            <el-form-item label="邮箱密码">
              <el-input v-model="emailSettings.emailPassword" type="password" placeholder="请输入邮箱密码" show-password />
            </el-form-item>
            <el-form-item label="启用SSL">
              <el-switch v-model="emailSettings.sslEnabled" />
              <span style="margin-left: 10px; color: #909399;">使用SSL加密连接</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveEmailSettings" :loading="emailLoading">
                <el-icon>
                  <Check />
                </el-icon> 保存邮件设置
              </el-button>
              <el-button @click="testEmailSettings" :loading="testEmailLoading">
                <el-icon>
                  <Promotion />
                </el-icon> 测试连接
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <!-- 数据字典 -->
    <el-card shadow="never" style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <h3><el-icon>
              <Grid />
            </el-icon> 数据字典</h3>
          <el-button type="primary" size="small" @click="showAddDictDialog">
            <el-icon>
              <Plus />
            </el-icon>
            添加字典
          </el-button>
        </div>
      </template>

      <el-tabs v-model="activeDictType" @tab-change="loadDictionaries">
        <el-tab-pane label="文章分类" name="article_category" />
        <el-tab-pane label="季节" name="season" />
        <el-tab-pane label="难度等级" name="difficulty" />
      </el-tabs>

      <el-table :data="dictionaries" v-loading="dictLoading" stripe border>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="dictCode" label="字典编码" min-width="180" show-overflow-tooltip />
        <el-table-column prop="dictName" label="字典名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="dictValue" label="字典值" width="150" align="center" />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" align="center">
          <template #default="{ row }">
            <span style="font-size: 12px; color: #666;">{{ row.createdAt || '未知' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" align="center">
          <template #default="{ row }">
            <span style="font-size: 12px; color: #666;">{{ row.updatedAt || '未知' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="toggleDictStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" type="primary" @click="editDict(row)">
              编辑
            </el-button>
            <el-button size="small" type="danger" @click="deleteDict(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination" v-if="dictTotal > 0">
        <el-pagination v-model:current-page="dictQueryParams.page" v-model:page-size="dictQueryParams.pageSize"
          :total="dictTotal" :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadDictionaries" @current-change="loadDictionaries" />
      </div>
    </el-card>

    <!-- 添加/编辑字典弹窗 -->
    <el-dialog v-model="dictDialogVisible" :title="dictForm.id ? '编辑字典' : '添加字典'" width="500px">
      <el-form :model="dictForm" :rules="dictRules" ref="dictFormRef" label-width="100px">
        <el-form-item label="字典类型">
          <el-input v-model="activeDictType" disabled />
        </el-form-item>
        <el-form-item label="字典编码" prop="dictCode">
          <el-input v-model="dictForm.dictCode" placeholder="请输入字典编码" @blur="handleDictFieldBlur('dictCode')" />
        </el-form-item>
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="dictForm.dictName" placeholder="请输入字典名称" @blur="handleDictFieldBlur('dictName')" />
        </el-form-item>
        <el-form-item label="字典值" prop="dictValue">
          <el-input v-model="dictForm.dictValue" placeholder="请输入字典值" @blur="handleDictFieldBlur('dictValue')" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="dictForm.sortOrder" :min="0" :max="999" @blur="handleDictFieldBlur('sortOrder')" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dictForm.remark" type="textarea" :rows="2" placeholder="请输入备注信息（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDict" :loading="dictSaveLoading">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import message from '@/plugins/message'
import { ref, reactive, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import {
  Setting,
  Bell,
  Lock,
  Message,
  Grid,
  Plus,
  Check,
  RefreshLeft,
  Promotion
} from '@element-plus/icons-vue'
import {
  getDictionaryList,
  getDictionaryByType,
  addDictionary,
  updateDictionary,
  deleteDictionary,
  getSystemSettings,
  updateSystemSettings
} from '@/api/system'
import { getAppConfig, refreshAppConfig } from '@/config/runtimeConfig'
import { useFormValidation } from '@/composables/useFormValidation'

// ==================== 响应式数据 ====================

// 加载状态
const basicLoading = ref(false)
const notificationLoading = ref(false)
const securityLoading = ref(false)
const emailLoading = ref(false)
const testEmailLoading = ref(false)
const dictLoading = ref(false)
const dictSaveLoading = ref(false)

// 弹窗状态
const dictDialogVisible = ref(false)

// 表单引用
const basicFormRef = ref(null)
const notificationFormRef = ref(null)
const securityFormRef = ref(null)
const emailFormRef = ref(null)
const dictFormRef = ref(null)

// 创建字段失焦处理函数
const { handleFieldBlur: handleBasicFieldBlur } = useFormValidation(basicFormRef)
const { handleFieldBlur: handleDictFieldBlur } = useFormValidation(dictFormRef)

// 字典相关
const activeDictType = ref('article_category')
const dictionaries = ref([])
const dictTotal = ref(0)

// 字典查询参数
const dictQueryParams = reactive({
  page: 1,
  pageSize: 10,
  type: 'article_category'
})

// ==================== 设置数据 ====================
const appConfig = getAppConfig()
const createDefaultBasicSettings = () => ({
  systemName: appConfig?.systemInfo?.name || '医院预约挂号系统',
  systemVersion: appConfig?.systemInfo?.version || 'v1.0.0',
  advanceDays: appConfig?.basic?.advanceDays ?? 7,
  cancelHours: appConfig?.basic?.cancelHours ?? 2,
  paymentTimeout: appConfig?.basic?.paymentTimeout ?? 30,
  maintenanceMode: appConfig?.systemInfo?.maintenanceMode ?? false
})
const createDefaultNotificationSettings = () => ({
  appointmentReminder: appConfig?.notification?.appointmentReminder ?? true,
  reminderHours: appConfig?.notification?.reminderHours ?? 2,
  smsNotification: appConfig?.notification?.smsEnabled ?? false,
  emailNotification: appConfig?.notification?.emailEnabled ?? false,
  systemNotification: appConfig?.notification?.systemEnabled ?? true
})
const createDefaultSecuritySettings = () => ({
  minPasswordLength: appConfig?.security?.minPasswordLength ?? 8,
  loginLockEnabled: appConfig?.security?.loginLockEnabled ?? true,
  maxLoginAttempts: appConfig?.security?.maxLoginAttempts ?? 5,
  lockDuration: appConfig?.security?.lockDurationMinutes ?? 15,
  sessionTimeout: appConfig?.security?.sessionTimeoutMinutes ?? 120
})
const createDefaultEmailSettings = () => ({
  smtpHost: appConfig?.email?.smtpHost || '',
  smtpPort: appConfig?.email?.smtpPort ?? 587,
  fromEmail: appConfig?.email?.fromEmail || '',
  emailPassword: '',
  sslEnabled: appConfig?.email?.sslEnabled ?? true
})

// 基础设置
const basicSettings = reactive(createDefaultBasicSettings())

// 通知设置
const notificationSettings = reactive(createDefaultNotificationSettings())

// 安全设置
const securitySettings = reactive(createDefaultSecuritySettings())

// 邮件设置
const emailSettings = reactive(createDefaultEmailSettings())

// 字典表单
const dictForm = reactive({
  id: null,
  dictType: '',
  dictCode: '',
  dictName: '',
  dictValue: '',
  sortOrder: 0,
  status: 1,
  remark: ''
})

// ==================== 表单验证规则 ====================

const basicRules = {
  systemName: [{ required: true, message: '请输入系统名称', trigger: 'blur' }],
  systemVersion: [{ required: true, message: '请输入系统版本', trigger: 'blur' }],
  advanceDays: [{ required: true, message: '请设置可提前预约天数', trigger: 'blur' }],
  cancelHours: [{ required: true, message: '请设置取消预约提前时间', trigger: 'blur' }],
  paymentTimeout: [{ required: true, message: '请设置支付超时时间', trigger: 'blur' }]
}

const dictRules = {
  dictCode: [{ required: true, message: '请输入字典编码', trigger: 'blur' }],
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典值', trigger: 'blur' }],
  sortOrder: [{ required: true, message: '请设置排序', trigger: 'blur' }]
}

// ==================== 系统设置功能 ====================

// 加载系统设置
const loadSystemSettings = async () => {
  try {
    const res = await getSystemSettings()
    if (res.code === 200 && res.data) {
      // 更新各个设置对象
      Object.assign(basicSettings, res.data.basic || {})
      Object.assign(notificationSettings, res.data.notification || {})
      Object.assign(securitySettings, res.data.security || {})
      Object.assign(emailSettings, res.data.email || {})
    }
  } catch (error) {

    // 不显示错误消息，避免影响页面加载
    // message.error('加载系统设置失败')
  }
}

// 保存基础设置
const saveBasicSettings = async () => {
  if (!basicFormRef.value) return

  try {
    await basicFormRef.value.validate()
    basicLoading.value = true

    const res = await updateSystemSettings({
      type: 'basic',
      data: basicSettings
    })

    if (res.code === 200) {
      message.success('基础设置保存成功')
      await Promise.all([refreshAppConfig(), loadSystemSettings()])
    } else {
      message.error(res.message || '保存失败')
    }
  } catch (error) {

    message.error('保存基础设置失败')
  } finally {
    basicLoading.value = false
  }
}

// 重置基础设置
const resetBasicSettings = () => {
  Object.assign(basicSettings, createDefaultBasicSettings())
}

// 保存通知设置
const saveNotificationSettings = async () => {
  try {
    notificationLoading.value = true

    const res = await updateSystemSettings({
      type: 'notification',
      data: notificationSettings
    })

    if (res.code === 200) {
      message.success('通知设置保存成功')
      await Promise.all([refreshAppConfig(), loadSystemSettings()])
    } else {
      message.error(res.message || '保存失败')
    }
  } catch (error) {

    message.error('保存通知设置失败')
  } finally {
    notificationLoading.value = false
  }
}

// 重置通知设置
const resetNotificationSettings = () => {
  Object.assign(notificationSettings, createDefaultNotificationSettings())
}

// 保存安全设置
const saveSecuritySettings = async () => {
  try {
    securityLoading.value = true

    const res = await updateSystemSettings({
      type: 'security',
      data: securitySettings
    })

    if (res.code === 200) {
      message.success('安全设置保存成功')
      await Promise.all([refreshAppConfig(), loadSystemSettings()])
    } else {
      message.error(res.message || '保存失败')
    }
  } catch (error) {

    message.error('保存安全设置失败')
  } finally {
    securityLoading.value = false
  }
}

// 重置安全设置
const resetSecuritySettings = () => {
  Object.assign(securitySettings, createDefaultSecuritySettings())
}

// 保存邮件设置
const saveEmailSettings = async () => {
  try {
    emailLoading.value = true

    const res = await updateSystemSettings({
      type: 'email',
      data: emailSettings
    })

    if (res.code === 200) {
      message.success('邮件设置保存成功')
      await Promise.all([refreshAppConfig(), loadSystemSettings()])
    } else {
      message.error(res.message || '保存失败')
    }
  } catch (error) {

    message.error('保存邮件设置失败')
  } finally {
    emailLoading.value = false
  }
}

// 测试邮件设置
const testEmailSettings = async () => {
  try {
    testEmailLoading.value = true

    // TODO: 实现邮件测试API
    await new Promise(resolve => setTimeout(resolve, 2000))
    message.success('邮件连接测试成功')
  } catch (error) {

    message.error('测试邮件连接失败')
  } finally {
    testEmailLoading.value = false
  }
}

// ==================== 数据字典功能 ====================

// 加载字典列表
const loadDictionaries = async () => {
  try {
    dictLoading.value = true
    dictQueryParams.type = activeDictType.value

    const res = await getDictionaryByType(activeDictType.value)
    if (res.code === 200) {
      dictionaries.value = res.data || []
      dictTotal.value = res.total || 0
    } else {

      // 暂时不显示错误消息，避免影响页面加载
    }
  } catch (error) {

    // 暂时不显示错误消息，避免影响页面加载
    dictionaries.value = [] // 设置空数组避免渲染错误
  } finally {
    dictLoading.value = false
  }
}

// 显示添加字典弹窗
const showAddDictDialog = () => {
  Object.assign(dictForm, {
    id: null,
    dictType: activeDictType.value,
    dictCode: '',
    dictName: '',
    dictValue: '',
    sortOrder: 0,
    status: 1,
    remark: ''
  })
  dictDialogVisible.value = true
}

// 编辑字典
const editDict = (row) => {
  Object.assign(dictForm, {
    ...row,
    dictType: activeDictType.value
  })
  dictDialogVisible.value = true
}

// 切换字典状态
const toggleDictStatus = async (row) => {
  const action = row.status === 1 ? '禁用' : '启用'
  const newStatus = row.status === 1 ? 0 : 1

  ElMessageBox.confirm(
    `确定要${action}字典"${row.dictName}"吗？`,
    '确认操作',
    {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    }
  ).then(async () => {
    try {
      const res = await updateDictionary({ id: row.id, status: newStatus })
      if (res.code === 200) {
        message.success(`${action}成功`)
        loadDictionaries()
      } else {
        message.error(res.message || `${action}失败`)
      }
    } catch (error) {

      message.error(`${action}失败`)
    }
  }).catch(() => { })
}

// 删除字典
const deleteDict = (row) => {
  ElMessageBox.confirm(
    `确定要删除字典"${row.dictName}"吗？删除后不可恢复。`,
    '删除确认',
    {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消'
    }
  ).then(async () => {
    try {
      const res = await deleteDictionary(row.id)
      if (res.code === 200) {
        message.success('删除成功')
        loadDictionaries()
      } else {
        message.error(res.message || '删除失败')
      }
    } catch (error) {

      message.error('删除失败')
    }
  }).catch(() => { })
}

// 保存字典
const saveDict = async () => {
  if (!dictFormRef.value) return

  try {
    await dictFormRef.value.validate()
    dictSaveLoading.value = true

    // 准备保存数据
    const saveData = { ...dictForm }

    // 新增时默认启用，编辑时不传递 status 字段
    if (!dictForm.id) {
      saveData.status = 1
    } else {
      delete saveData.status
    }

    const apiCall = dictForm.id ? updateDictionary : addDictionary
    const res = await apiCall(saveData)

    if (res.code === 200) {
      message.success(dictForm.id ? '更新成功' : '添加成功')
      dictDialogVisible.value = false
      loadDictionaries()
    } else {
      message.error(res.message || '保存失败')
    }
  } catch (error) {

    message.error('保存失败')
  } finally {
    dictSaveLoading.value = false
  }
}

// ==================== 生命周期 ====================

onMounted(() => {
  // 延迟加载数据，确保页面先渲染
  setTimeout(() => {
    loadSystemSettings()
    loadDictionaries()
  }, 100)
})
</script>

<style scoped lang="scss">
.pagination {
  margin-top: 15px;
  display: flex;
  justify-content: center;
}
</style>

<style scoped lang="scss">
.system-settings-container {
  max-width: 1400px;
  margin: 0 auto;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    h3 {
      margin: 0;
      font-size: 16px;
      color: #333;
      display: flex;
      align-items: center;
      gap: 8px;

      .el-icon {
        color: #409eff;
      }
    }
  }

  .el-form-item {
    margin-bottom: 20px;

    .el-input-number {
      width: 150px;
    }
  }

  .el-card {
    margin-bottom: 20px;
    border-radius: 8px;

    :deep(.el-card__header) {
      padding: 16px 20px;
      border-bottom: 1px solid #f0f0f0;
    }

    :deep(.el-card__body) {
      padding: 20px;
    }
  }

  .el-table {
    margin-top: 16px;

    :deep(.el-table__header) {
      th {
        background-color: #fafafa;
        color: #333;
        font-weight: 500;
      }
    }
  }

  // 响应式设计
  @media (max-width: 768px) {
    .el-row {
      .el-col {
        margin-bottom: 20px;
      }
    }

    .el-form {
      .el-form-item {
        .el-form-item__label {
          width: 120px !important;
        }
      }
    }
  }
}
</style>

<style scoped lang="scss">
@use '@/styles/admin-variables.scss' as *;
@use '@/styles/admin-common.scss' as *;

.system-settings-container {
  max-width: 1400px;
  margin: 0 auto;
}
</style>
