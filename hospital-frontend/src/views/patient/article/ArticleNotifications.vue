<template>
  <div class="notify-page">
    <el-card class="section-card" v-loading="loading">
      <template #header>
        <div class="header-row">
          <div>
            <div class="page-title">文章通知中心</div>
            <div class="page-subtitle">审核结果、下架提醒与互动摘要会汇总在这里</div>
          </div>
          <div class="actions">
            <el-button @click="markAllRead" :disabled="unreadCount === 0">全部标记已读</el-button>
          <el-button @click="markFilteredRead" :disabled="selectedIds.length === 0">标记选中已读</el-button>
          <el-button type="danger" plain @click="deleteSelected" :disabled="selectedIds.length === 0">删除选中</el-button>
            <el-button type="primary" @click="fetchNotifications">刷新</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" class="filter-form">
        <el-form-item label="通知类型">
          <el-select v-model="filters.type" clearable placeholder="全部类型" style="width: 180px">
            <el-option label="审核通知" value="REVIEW" />
            <el-option label="互动通知" value="INTERACTION" />
            <el-option label="下架通知" value="OFFLINE" />
            <el-option label="系统通知" value="SYSTEM" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-switch v-model="filters.onlyUnread" active-text="仅看未读" />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-select v-model="filters.range" style="width: 140px">
            <el-option label="全部" value="all" />
            <el-option label="今天" value="today" />
            <el-option label="近7天" value="7d" />
            <el-option label="近30天" value="30d" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-timeline v-if="filteredNotifications.length > 0">
        <el-timeline-item
          v-for="item in filteredNotifications"
          :key="item.id"
          :timestamp="item.timeText"
          :type="item.type"
          hollow
        >
          <div class="notify-item" :class="{ unread: !item.read }">
            <div class="notify-title">{{ item.title }}</div>
            <div class="notify-content">{{ item.content }}</div>
            <div class="notify-footer">
              <el-checkbox :model-value="selectedIds.includes(item.id)" @change="(checked) => toggleSelect(item.id, checked)" />
              <el-tag size="small">{{ item.levelText }}</el-tag>
              <el-button link type="primary" @click="viewArticle(item)">查看文章</el-button>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>

      <el-empty v-else description="暂无通知" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { deleteBatchArticleNotification, getMyArticleNotifications, markAllArticleNotificationRead, markArticleNotificationRead, markBatchArticleNotificationRead } from '@/api/article'
import message from '@/plugins/message'
import { useUserStore } from '@/stores/user'
import { createArticleNotificationSocket } from '@/utils/articleNotificationSocket'
const loading = ref(false)
const notifications = ref([])
const router = useRouter()
const userStore = useUserStore()
let articleSocket = null
const selectedIds = ref([])
const filters = reactive({
  type: '',
  onlyUnread: false,
  range: 'all'
})

const unreadCount = computed(() => notifications.value.filter(item => !item.read).length)
const filteredNotifications = computed(() => {
  return notifications.value.filter(item => {
    const typeOk = !filters.type || item.levelText === filters.type
    const unreadOk = !filters.onlyUnread || !item.read
    const time = dayjs(item.timeText)
    const now = dayjs()
    const rangeOk = filters.range === 'all'
      || (filters.range === 'today' && time.isSame(now, 'day'))
      || (filters.range === '7d' && time.isAfter(now.subtract(7, 'day')))
      || (filters.range === '30d' && time.isAfter(now.subtract(30, 'day')))
    return typeOk && unreadOk && rangeOk
  })
})

const toViewModel = (item) => {
  const type = item.notificationType === 'REVIEW'
    ? 'warning'
    : item.notificationType === 'INTERACTION'
      ? 'primary'
      : item.notificationType === 'OFFLINE'
        ? 'danger'
        : 'info'
  return {
    id: item.id,
    articleId: item.bizId,
    type,
    levelText: item.notificationType || 'SYSTEM',
    title: item.title,
    content: item.content,
    timeText: dayjs(item.createdAt).format('YYYY-MM-DD HH:mm'),
    read: Number(item.isRead) === 1
  }
}

const addRealtimeNotification = (payload) => {
  if (!payload || !payload.id) return
  const incoming = toViewModel(payload)
  const exists = notifications.value.some(item => String(item.id) === String(incoming.id))
  if (exists) return
  notifications.value = [incoming, ...notifications.value].slice(0, 30)
}

const fetchNotifications = async () => {
  loading.value = true
  try {
    const res = await getMyArticleNotifications({ pageNum: 1, pageSize: 30 })
    notifications.value = (res.data?.records || []).map(toViewModel)
  } catch (e) {
    message.error(e?.message || '通知加载异常')
  } finally {
    loading.value = false
  }
}

const markAllRead = async () => {
  await markAllArticleNotificationRead()
  notifications.value.forEach(item => { item.read = true })
  message.success('全部通知已设为已读')
}

const markFilteredRead = async () => {
  if (selectedIds.value.length === 0) return
  await markBatchArticleNotificationRead(selectedIds.value)
  notifications.value.forEach(item => {
    if (selectedIds.value.includes(item.id)) item.read = true
  })
  selectedIds.value = []
  message.success('选中通知已标记为已读')
}

const deleteSelected = async () => {
  if (selectedIds.value.length === 0) return
  await deleteBatchArticleNotification(selectedIds.value)
  notifications.value = notifications.value.filter(item => !selectedIds.value.includes(item.id))
  selectedIds.value = []
  message.success('选中通知已删除')
}

const toggleSelect = (id, checked) => {
  if (checked) {
    if (!selectedIds.value.includes(id)) selectedIds.value.push(id)
  } else {
    selectedIds.value = selectedIds.value.filter(item => item !== id)
  }
}

const viewArticle = async (item) => {
  if (!item.read) {
    await markArticleNotificationRead(item.id)
    item.read = true
  }
  if (item.articleId) {
    router.push(`/patient/article/detail/${item.articleId}`)
  }
}

onMounted(async () => {
  await fetchNotifications()
  articleSocket = createArticleNotificationSocket({
    token: userStore.token,
    onMessage: (payload) => {
      addRealtimeNotification(payload)
      message.success('收到新的文章通知')
    }
  })
  articleSocket.connect()
})

onUnmounted(() => {
  articleSocket?.disconnect()
})
</script>

<style scoped>
.notify-page { max-width: 1200px; margin: 0 auto; }
.section-card { border-radius: 12px; }
.header-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.page-title { font-size: 20px; font-weight: 600; }
.page-subtitle { margin-top: 4px; color: #909399; }
.actions { display: flex; gap: 8px; }
.mb12 { margin-bottom: 12px; }
.filter-form { margin-bottom: 4px; }
.notify-item { padding: 8px 10px; border-radius: 8px; background: #fafafa; }
.notify-item.unread { background: #f0f9ff; border-left: 3px solid #409eff; }
.notify-title { font-size: 15px; font-weight: 600; margin-bottom: 4px; }
.notify-content { color: #606266; line-height: 1.6; }
.notify-footer { margin-top: 8px; display: flex; justify-content: space-between; align-items: center; }
</style>
