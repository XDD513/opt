<template>
  <div class="admin-page">
    <el-card class="section-card">
      <template #header>
        <div class="header-row">
          <div>
            <div class="page-title">文章管理</div>
            <div class="page-subtitle">统一处理上下架、推荐和内容质量</div>
          </div>
          <div class="header-actions">
            <el-button @click="$router.push('/admin/article/notifications')">通知中心</el-button>
            <el-button @click="fetchList">刷新</el-button>
          </div>
        </div>
      </template>
      <div class="stat-row">
        <el-tag type="success">已发布 {{ publishedCount }}</el-tag>
        <el-tag type="warning">待审核 {{ pendingCount }}</el-tag>
        <el-tag type="danger">已驳回 {{ rejectedCount }}</el-tag>
        <el-tag type="info">已下架 {{ offlineCount }}</el-tag>
      </div>
      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item>
          <el-select v-model="query.status" clearable placeholder="状态筛选" style="width: 160px">
            <el-option label="待审核" :value="0" />
            <el-option label="已发布" :value="1" />
            <el-option label="已下架" :value="2" />
            <el-option label="已驳回" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-input v-model="query.keyword" clearable placeholder="搜索标题/摘要" style="width: 280px" />
        </el-form-item>
        <el-form-item><el-button type="primary" @click="fetchList">查询</el-button></el-form-item>
      </el-form>
      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="title" label="标题" min-width="260" />
        <el-table-column prop="authorName" label="作者" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'info' : row.status === 3 ? 'danger' : 'warning'">
              {{ row.status === 1 ? '已发布' : row.status === 2 ? '已下架' : row.status === 3 ? '已驳回' : '待审核' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="commentCount" label="评论" width="80" />
        <el-table-column prop="favoriteCount" label="收藏" width="80" />
        <el-table-column prop="likeCount" label="点赞" width="80" />
        <el-table-column prop="viewCount" label="浏览" width="80" />
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/admin/article/review/${row.id}`)">查看</el-button>
            <el-button link @click="toggleOnline(row)">{{ row.status === 2 ? '上架' : '下架' }}</el-button>
            <el-button v-if="row.status === 1" link type="warning" @click="toggleRecommend(row)">{{ row.isFeatured === 1 ? '取消推荐' : '推荐' }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && list.length === 0" description="暂无符合条件的文章" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import message from '@/plugins/message'
import { getAdminArticleList, offlineArticle, onlineArticle, recommendArticle, unrecommendArticle } from '@/api/article'
const loading = ref(false)
const list = ref([])
const query = reactive({ pageNum: 1, pageSize: 100, status: undefined, keyword: '' })
const publishedCount = computed(() => list.value.filter(item => Number(item.status) === 1).length)
const pendingCount = computed(() => list.value.filter(item => Number(item.status) === 0).length)
const rejectedCount = computed(() => list.value.filter(item => Number(item.status) === 3).length)
const offlineCount = computed(() => list.value.filter(item => Number(item.status) === 2).length)
const fetchList = async () => {
  loading.value = true
  try { list.value = (await getAdminArticleList(query)).data?.records || [] }
  catch (e) { message.error(e?.message || '管理列表获取失败') }
  finally { loading.value = false }
}
const toggleOnline = async (row) => {
  if (row.status === 2) {
    await onlineArticle(row.id)
  } else {
    const { value } = await ElMessageBox.prompt('请输入下架原因', '下架文章', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValue: row.rejectReason || '',
      inputValidator: (val) => (val && val.trim() ? true : '下架原因不能为空')
    })
    await offlineArticle(row.id, value.trim())
  }
  message.success(row.status === 2 ? '操作成功，文章已上架' : '操作成功，文章已下架')
  await fetchList()
}
const toggleRecommend = async (row) => {
  if (row.status !== 1) return
  if (row.isFeatured === 1) await unrecommendArticle(row.id)
  else await recommendArticle(row.id)
  message.success(row.isFeatured === 1 ? '操作成功，已取消推荐' : '操作成功，已设为推荐')
  await fetchList()
}
onMounted(fetchList)
</script>

<style scoped>
.admin-page { max-width: 1280px; margin: 0 auto; }
.section-card { border-radius: 12px; }
.header-row { display: flex; justify-content: space-between; align-items: center; }
.header-actions { display: flex; gap: 8px; }
.page-title { font-size: 20px; font-weight: 600; }
.page-subtitle { margin-top: 4px; color: #909399; }
.stat-row { margin-bottom: 12px; display: flex; gap: 8px; }
.query-form { margin-bottom: 12px; }
</style>
