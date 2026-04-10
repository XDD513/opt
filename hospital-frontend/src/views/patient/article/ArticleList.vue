<template>
  <div class="article-page">
    <el-card shadow="never" class="hero-card">
      <template #header>
        <div class="header-row">
          <div>
            <div class="page-title">养生社区</div>
            <div class="page-subtitle">发现优质养生内容，关注你的健康日常</div>
          </div>
          <div class="header-actions">
            <el-button @click="$router.push('/patient/article/my-favorites')">我的收藏</el-button>
            <el-button @click="$router.push('/patient/article/my')">我的文章</el-button>
            <el-button type="primary" @click="$router.push('/patient/article/publish')">发布文章</el-button>
          </div>
        </div>
      </template>
      <div class="stat-grid">
        <el-card shadow="never" class="stat-item">
          <div class="stat-label">文章总数</div>
          <div class="stat-value">{{ total }}</div>
        </el-card>
        <el-card shadow="never" class="stat-item">
          <div class="stat-label">推荐文章</div>
          <div class="stat-value">{{ featuredCount }}</div>
        </el-card>
        <el-card shadow="never" class="stat-item">
          <div class="stat-label">高互动文章</div>
          <div class="stat-value">{{ hotCount }}</div>
        </el-card>
      </div>
      <el-form :inline="true" :model="query" class="query-form" @submit.prevent>
        <el-form-item><el-input v-model="query.keyword" placeholder="搜索标题/摘要/标签" clearable style="width: 260px" /></el-form-item>
        <el-form-item>
          <el-select v-model="query.category" placeholder="分类" clearable style="width: 160px">
            <el-option label="体质养生" value="CONSTITUTION" />
            <el-option label="饮食养生" value="DIET" />
            <el-option label="运动养生" value="EXERCISE" />
            <el-option label="穴位养生" value="ACUPOINT" />
            <el-option label="时令养生" value="SEASON" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="fetchList">查询</el-button></el-form-item>
        <el-form-item><el-button @click="resetQuery">重置</el-button></el-form-item>
        <el-form-item>
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button label="table">表格</el-radio-button>
            <el-radio-button label="card">卡片</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div v-if="viewMode === 'table'" class="table-responsive">
      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="title" label="标题" min-width="220">
          <template #default="{ row }">
            <el-link type="primary" @click="$router.push(`/patient/article/detail/${row.id}`)">{{ row.title }}</el-link>
            <el-tag v-if="row.isFeatured === 1" size="small" type="danger" class="ml8">推荐</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="authorName" label="作者" width="120" />
        <el-table-column prop="commentCount" label="评论" width="80" />
        <el-table-column prop="favoriteCount" label="收藏" width="80" />
        <el-table-column prop="likeCount" label="点赞" width="80" />
        <el-table-column prop="viewCount" label="浏览" width="80" />
      </el-table>
      </div>
      <div v-else class="card-grid" v-loading="loading">
        <el-card v-for="row in list" :key="row.id" shadow="hover" class="article-card">
          <div class="card-title-row">
            <el-link type="primary" @click="$router.push(`/patient/article/detail/${row.id}`)">{{ row.title }}</el-link>
            <el-tag v-if="row.isFeatured === 1" size="small" type="danger">推荐</el-tag>
          </div>
          <div class="card-summary">{{ row.summary || '暂无摘要' }}</div>
          <div class="card-meta">
            <span>{{ row.authorName || '-' }}</span>
            <span>浏览 {{ row.viewCount || 0 }}</span>
            <span>点赞 {{ row.likeCount || 0 }}</span>
            <span>收藏 {{ row.favoriteCount || 0 }}</span>
          </div>
        </el-card>
      </div>
      <el-empty v-if="!loading && list.length === 0" description="暂无文章，换个筛选条件试试">
        <el-button type="primary" @click="$router.push('/patient/article/publish')">发布第一篇文章</el-button>
      </el-empty>
      <div class="pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :current-page="query.pageNum"
          :page-size="query.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @size-change="onSizeChange"
          @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import message from '@/plugins/message'
import { getArticleList } from '@/api/article'
const loading = ref(false)
const total = ref(0)
const list = ref([])
const viewMode = ref('table')
const query = reactive({ keyword: '', category: '', pageNum: 1, pageSize: 10 })
const featuredCount = computed(() => list.value.filter(item => Number(item.isFeatured) === 1).length)
const hotCount = computed(() => list.value.filter(item => Number(item.viewCount || 0) + Number(item.likeCount || 0) + Number(item.favoriteCount || 0) >= 20).length)
const fetchList = async () => {
  loading.value = true
  try {
    const res = await getArticleList(query)
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    message.error(e?.message || '文章列表加载异常，请稍后重试')
  } finally { loading.value = false }
}
const onPageChange = (page) => { query.pageNum = page; fetchList() }
const onSizeChange = (size) => { query.pageSize = size; query.pageNum = 1; fetchList() }
const resetQuery = () => {
  query.keyword = ''
  query.category = ''
  query.pageNum = 1
  query.pageSize = 10
  fetchList()
}
onMounted(fetchList)
</script>

<style scoped>
.article-page { max-width: 1200px; margin: 0 auto; }
.hero-card { border-radius: 12px; }
.header-row { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.page-title { font-size: 20px; font-weight: 600; }
.page-subtitle { margin-top: 4px; color: #909399; font-size: 13px; }
.header-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.stat-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 16px; }
.stat-item { border-radius: 10px; background: #fafafa; }
.stat-label { color: #909399; font-size: 13px; }
.stat-value { margin-top: 6px; font-size: 22px; font-weight: 700; color: #303133; }
.query-form { margin-bottom: 12px; }
.card-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 8px; }
.article-card { border-radius: 10px; }
.card-title-row { display: flex; justify-content: space-between; gap: 8px; }
.card-summary { margin-top: 8px; min-height: 44px; color: #606266; line-height: 1.6; }
.card-meta { margin-top: 10px; display: flex; gap: 10px; color: #909399; font-size: 12px; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
.ml8 { margin-left: 8px; }
@media (max-width: 768px) {
  .stat-grid { grid-template-columns: 1fr; }
  .card-grid { grid-template-columns: 1fr; }
  .query-form :deep(.el-form-item) { margin-right: 0; width: 100%; }
  .query-form :deep(.el-input), .query-form :deep(.el-select) { width: 100% !important; max-width: 100%; }
}
</style>
