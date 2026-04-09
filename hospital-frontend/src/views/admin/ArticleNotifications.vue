<template>
  <div class="notify-page">
    <el-card class="section-card" v-loading="loading">
      <template #header>
        <div class="header-row">
          <div>
            <div class="page-title">管理员通知中心</div>
            <div class="page-subtitle">聚合待审核、驳回与下架动态，支持快速处理</div>
          </div>
          <div class="actions">
            <el-button @click="markAllRead" :disabled="unreadCount === 0">全部已读</el-button>
          <el-button @click="markFilteredRead" :disabled="selectedIds.length === 0">标记选中已读</el-button>
          <el-button type="danger" plain @click="deleteSelected" :disabled="selectedIds.length === 0">删除选中</el-button>
            <el-button type="primary" @click="fetchEvents">刷新</el-button>
          </div>
        </div>
      </template>

      <div class="stat-row">
        <el-tag type="warning">待审核 {{ pendingCount }}</el-tag>
        <el-tag type="danger">已驳回 {{ rejectedCount }}</el-tag>
        <el-tag type="info">已下架 {{ offlineCount }}</el-tag>
      </div>

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

      <el-table :data="filteredEvents" border>
        <el-table-column label="选择" width="70">
          <template #default="{ row }">
            <el-checkbox :model-value="selectedIds.includes(row.id)" @change="(checked) => toggleSelect(row.id, checked)" />
          </template>
        </el-table-column>
        <el-table-column prop="timeText" label="时间" width="170" />
        <el-table-column prop="title" label="通知标题" min-width="180" />
        <el-table-column prop="content" label="内容" min-width="280" />
        <el-table-column prop="statusText" label="状态" width="100" />
        <el-table-column label="已读" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.read ? 'info' : 'danger'">{{ row.read ? '已读' : '未读' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goHandle(row)">处理</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && events.length === 0" description="暂无通知事件" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { deleteBatchArticleNotification, getAdminArticleNotifications, markAllArticleNotificationRead, markArticleNotificationRead, markBatchArticleNotificationRead } from '@/api/article'
import message from '@/plugins/message'
import { useUserStore } from '@/stores/user'
import { createArticleNotificationSocket } from '@/utils/articleNotificationSocket'

const loading = ref(false)
const events = ref([])
const router = useRouter()
const userStore = useUserStore()
let articleSocket = null
const selectedIds = ref([])
const filters = reactive({
  type: '',
  onlyUnread: false,
  range: 'all'
})
const pendingCount = computed(() => events.value.filter(item => item.status === 0).length)
const rejectedCount = computed(() => events.value.filter(item => item.status === 3).length)
const offlineCount = computed(() => events.value.filter(item => item.status === 2).length)
const unreadCount = computed(() => events.value.filter(item => !item.read).length)
const filteredEvents = computed(() => {
  return events.value.filter(item => {
    const typeOk = !filters.type || item.notificationType === filters.type
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

const buildEvent = (record) => {
  const status = record.notificationType === 'REVIEW' && record.content.includes('驳回')
    ? 3
    : record.notificationType === 'OFFLINE'
      ? 2
      : 0
  const event = {
    id: record.id,
    articleId: record.bizId,
    status,
    timeText: dayjs(record.createdAt).format('YYYY-MM-DD HH:mm'),
    title: record.title,
    statusText: status === 0 ? '待审核' : status === 2 ? '已下架' : status === 3 ? '已驳回' : '已发布',
    content: record.content,
    read: Number(record.isRead) === 1,
    notificationType: record.notificationType || 'SYSTEM'
  }
  return event
}

const addRealtimeEvent = (payload) => {
  if (!payload || !payload.id) return
  const item = buildEvent(payload)
  const exists = events.value.some(event => String(event.id) === String(item.id))
  if (exists) return
  events.value = [item, ...events.value].slice(0, 200)
}

const fetchEvents = async () => {
  loading.value = true
  try {
    const res = await getAdminArticleNotifications({ pageNum: 1, pageSize: 200 })
    const records = res.data?.records || []
    events.value = records
      .map(buildEvent)
      .sort((a, b) => dayjs(b.timeText).valueOf() - dayjs(a.timeText).valueOf())
  } catch (e) {
    message.error(e?.message || '管理员通知加载异常')
  } finally {
    loading.value = false
  }
}

const goHandle = async (row) => {
  if (!row.read) {
    await markArticleNotificationRead(row.id)
    row.read = true
  }
  if (row.articleId) {
    router.push(`/admin/article/review/${row.articleId}`)
  }
}

const markAllRead = async () => {
  await markAllArticleNotificationRead()
  events.value = events.value.map(item => ({ ...item, read: true }))
  message.success('管理员通知已全部设为已读')
}

const markFilteredRead = async () => {
  if (selectedIds.value.length === 0) return
  await markBatchArticleNotificationRead(selectedIds.value)
  events.value.forEach(item => {
    if (selectedIds.value.includes(item.id)) item.read = true
  })
  selectedIds.value = []
  message.success('选中通知已标记为已读')
}

const deleteSelected = async () => {
  if (selectedIds.value.length === 0) return
  await deleteBatchArticleNotification(selectedIds.value)
  events.value = events.value.filter(item => !selectedIds.value.includes(item.id))
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

onMounted(async () => {
  await fetchEvents()
  articleSocket = createArticleNotificationSocket({
    token: userStore.token,
    onMessage: (payload) => {
      addRealtimeEvent(payload)
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
.notify-page { max-width: 1280px; margin: 0 auto; }
.section-card { border-radius: 12px; }
.header-row { display: flex; justify-content: space-between; align-items: center; }
.actions { display: flex; gap: 8px; }
.page-title { font-size: 20px; font-weight: 600; }
.page-subtitle { margin-top: 4px; color: #909399; }
.stat-row { margin-bottom: 12px; display: flex; gap: 8px; }
.filter-form { margin-bottom: 6px; }
</style>
