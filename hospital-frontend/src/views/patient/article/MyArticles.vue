<template>
  <div class="article-page article-page--feed">
    <el-card shadow="never" class="hero-card">
      <template #header>
        <div class="header-row">
          <div>
            <div class="page-title">我的文章</div>
            <div class="page-subtitle">管理你发布的所有内容与审核进度</div>
          </div>
          <el-button type="primary" @click="$router.push('/patient/article/publish')">新建文章</el-button>
        </div>
      </template>

      <div class="card-grid" v-loading="loading">
        <ArticleFeedCard
          v-for="row in list"
          :key="row.id"
          :row="row"
          :cover-url="coverUrlMap[row.id]"
          :show-like="false"
          @click="$router.push(`/patient/article/detail/${row.id}`)"
        >
          <template #footer>
            <div class="mine-actions-wrap" @click.stop>
              <el-tag :type="statusTagType(row)" size="small">{{ statusText(row) }}</el-tag>
              <div class="mine-actions">
                <el-button link type="warning" @click="$router.push(`/patient/article/edit/${row.id}`)">编辑</el-button>
                <el-button link type="danger" @click="remove(row.id)">删除</el-button>
              </div>
            </div>
          </template>
        </ArticleFeedCard>
      </div>
      <el-empty v-if="!loading && list.length === 0" description="你还没有发布文章">
        <el-button type="primary" @click="$router.push('/patient/article/publish')">立即发布</el-button>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import message from '@/plugins/message'
import { deleteArticle, getMyArticles } from '@/api/article'
import { useArticleCoverUrls } from '@/composables/useArticleCoverUrls'
import ArticleFeedCard from '@/components/patient/article/ArticleFeedCard.vue'

const loading = ref(false)
const list = ref([])
const { coverUrlMap, loadCoverUrls } = useArticleCoverUrls()

const statusText = (row) => {
  const s = Number(row.status)
  if (s === 1) return '已发布'
  if (s === 2) return '已下架'
  if (s === 3) return '已驳回'
  return '待审核'
}
const statusTagType = (row) => {
  const s = Number(row.status)
  if (s === 1) return 'success'
  if (s === 2) return 'info'
  if (s === 3) return 'danger'
  return 'warning'
}

const fetchList = async () => {
  loading.value = true
  try {
    list.value = (await getMyArticles({ pageNum: 1, pageSize: 50 })).data?.records || []
    await loadCoverUrls(list.value)
  } catch (e) {
    message.error(e?.message || '我的文章加载异常')
  } finally {
    loading.value = false
  }
}
const remove = async (id) => {
  await ElMessageBox.confirm('确认删除该文章吗？', '提示')
  await deleteArticle(id)
  message.success('删除成功，文章及关联数据已清理')
  await fetchList()
}
onMounted(fetchList)
</script>

<style scoped>
.article-page--feed {
  min-height: 100%;
  background: var(--el-bg-color-page, #f5f7fa);
  padding: 20px;
  box-sizing: border-box;
}
.article-page--feed .hero-card {
  max-width: 1200px;
  margin: 0 auto;
}
.hero-card {
  border-radius: 12px;
}
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
}
.page-subtitle {
  color: #909399;
  margin-top: 4px;
  font-size: 13px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
  min-height: 120px;
}

.mine-actions-wrap {
  width: 100%;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.mine-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .article-page--feed {
    padding: 12px;
  }
  .card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
