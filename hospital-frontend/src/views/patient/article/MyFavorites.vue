<template>
  <div class="article-page">
    <el-card class="section-card">
      <template #header>
        <div>
          <div class="page-title">我的收藏</div>
          <div class="page-subtitle">快速回看你标记过的重要内容</div>
        </div>
      </template>
      <div class="table-responsive">
      <el-table :data="list" v-loading="loading" border class="table-wrap">
        <el-table-column prop="title" label="标题" min-width="260">
          <template #default="{ row }">
            <el-link type="primary" @click="$router.push(`/patient/article/detail/${row.id}`)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="authorName" label="作者" width="120" />
        <el-table-column prop="viewCount" label="浏览" width="90" />
        <el-table-column prop="likeCount" label="点赞" width="90" />
        <el-table-column prop="favoriteCount" label="收藏数" width="100" />
      </el-table>
      </div>
      <el-empty v-if="!loading && list.length === 0" description="暂无收藏文章">
        <el-button type="primary" @click="$router.push('/patient/article/list')">去逛逛</el-button>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import message from '@/plugins/message'
import { getMyFavorites } from '@/api/article'
const loading = ref(false)
const list = ref([])
const fetchList = async () => {
  loading.value = true
  try { list.value = (await getMyFavorites({ pageNum: 1, pageSize: 50 })).data?.records || [] }
  catch (e) { message.error(e?.message || '收藏列表加载异常') }
  finally { loading.value = false }
}
onMounted(fetchList)
</script>

<style scoped>
.article-page { max-width: 1200px; margin: 0 auto; }
.section-card { border-radius: 12px; }
.page-title { font-size: 20px; font-weight: 600; }
.page-subtitle { color: #909399; margin-top: 4px; }
.table-wrap { margin-top: 12px; }
</style>
