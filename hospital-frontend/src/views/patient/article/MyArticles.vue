<template>
  <div class="article-page">
    <el-card class="section-card">
      <template #header>
        <div class="header-row">
          <div>
            <div class="page-title">我的文章</div>
            <div class="page-subtitle">管理你发布的所有内容与审核进度</div>
          </div>
          <el-button type="primary" @click="$router.push('/patient/article/publish')">新建文章</el-button>
        </div>
      </template>
      <div class="stat-row">
        <el-tag type="success">已发布 {{ publishedCount }}</el-tag>
        <el-tag type="warning">待审核 {{ pendingCount }}</el-tag>
        <el-tag type="danger">已驳回 {{ rejectedCount }}</el-tag>
        <el-tag type="info">已下架 {{ offlineCount }}</el-tag>
      </div>
      <div class="table-responsive">
      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="viewCount" label="浏览" width="80" />
        <el-table-column prop="likeCount" label="点赞" width="80" />
        <el-table-column prop="favoriteCount" label="收藏" width="80" />
        <el-table-column prop="commentCount" label="评论" width="80" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'info' : row.status === 3 ? 'danger' : 'warning'">
              {{ row.status === 1 ? '已发布' : row.status === 2 ? '已下架' : row.status === 3 ? '已驳回' : '待审核' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/patient/article/detail/${row.id}`)">查看</el-button>
            <el-button link type="warning" @click="$router.push(`/patient/article/edit/${row.id}`)">编辑</el-button>
            <el-button link @click="openTimeline(row)">进度</el-button>
            <el-button link type="danger" @click="remove(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>
      <el-empty v-if="!loading && list.length === 0" description="你还没有发布文章">
        <el-button type="primary" @click="$router.push('/patient/article/publish')">立即发布</el-button>
      </el-empty>
    </el-card>

    <el-drawer v-model="timelineOpen" title="审核进度时间线" size="420px">
      <el-timeline v-if="currentArticle">
        <el-timeline-item :timestamp="currentArticle.createdAt || '-'" type="primary">
          已创建文章《{{ currentArticle.title }}》
        </el-timeline-item>
        <el-timeline-item v-if="Number(currentArticle.status) !== 0" :timestamp="currentArticle.updatedAt || '-'" :type="Number(currentArticle.status) === 1 ? 'success' : 'danger'">
          {{ Number(currentArticle.status) === 1 ? '审核通过并发布' : Number(currentArticle.status) === 2 ? '管理员下架' : '审核驳回' }}
        </el-timeline-item>
        <el-timeline-item v-if="currentArticle.rejectReason" :timestamp="currentArticle.updatedAt || '-'" type="warning">
          处理意见：{{ currentArticle.rejectReason }}
        </el-timeline-item>
      </el-timeline>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import message from '@/plugins/message'
import { deleteArticle, getMyArticles } from '@/api/article'
const loading = ref(false)
const list = ref([])
const timelineOpen = ref(false)
const currentArticle = ref(null)
const publishedCount = computed(() => list.value.filter(item => Number(item.status) === 1).length)
const pendingCount = computed(() => list.value.filter(item => Number(item.status) === 0).length)
const rejectedCount = computed(() => list.value.filter(item => Number(item.status) === 3).length)
const offlineCount = computed(() => list.value.filter(item => Number(item.status) === 2).length)
const fetchList = async () => {
  loading.value = true
  try { list.value = (await getMyArticles({ pageNum: 1, pageSize: 50 })).data?.records || [] }
  catch (e) { message.error(e?.message || '我的文章加载异常') }
  finally { loading.value = false }
}
const remove = async (id) => {
  await ElMessageBox.confirm('确认删除该文章吗？', '提示')
  await deleteArticle(id)
  message.success('删除成功，文章及关联数据已清理')
  await fetchList()
}
const openTimeline = (row) => {
  currentArticle.value = row
  timelineOpen.value = true
}
onMounted(fetchList)
</script>

<style scoped>
.article-page { max-width: 1200px; margin: 0 auto; }
.section-card { border-radius: 12px; }
.header-row { display: flex; justify-content: space-between; align-items: center; }
.page-title { font-size: 20px; font-weight: 600; }
.page-subtitle { color: #909399; margin-top: 4px; }
.stat-row { margin-bottom: 12px; display: flex; gap: 8px; }
</style>
