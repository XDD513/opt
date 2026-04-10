<template>
  <div class="review-page">
    <el-card class="section-card">
      <template #header>
        <div class="header-row">
          <div>
            <div class="page-title">文章审核</div>
            <div class="page-subtitle">待审核文章会优先展示在这里</div>
          </div>
          <el-button @click="fetchList">刷新</el-button>
        </div>
      </template>
      <div class="table-responsive">
      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="title" label="标题" min-width="260" />
        <el-table-column prop="authorName" label="作者" width="120" />
        <el-table-column prop="createdAt" label="提交时间" width="180" />
        <el-table-column label="状态" width="120">
          <template #default><el-tag type="warning">待审核</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/admin/article/review/${row.id}`)">审核</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>
      <el-empty v-if="!loading && list.length === 0" description="暂无待审核文章" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import message from '@/plugins/message'
import { getAdminArticleList } from '@/api/article'
const loading = ref(false)
const list = ref([])
const fetchList = async () => {
  loading.value = true
  try { list.value = (await getAdminArticleList({ status: 0, pageNum: 1, pageSize: 50 })).data?.records || [] }
  catch (e) { message.error(e?.message || '审核列表加载异常') }
  finally { loading.value = false }
}
onMounted(fetchList)
</script>

<style scoped>
.review-page { max-width: 1280px; margin: 0 auto; }
.section-card { border-radius: 12px; }
.header-row { display: flex; justify-content: space-between; align-items: center; }
.page-title { font-size: 20px; font-weight: 600; }
.page-subtitle { color: #909399; margin-top: 4px; }
</style>
