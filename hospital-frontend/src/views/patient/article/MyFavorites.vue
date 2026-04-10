<template>
  <div class="article-page article-page--feed">
    <el-card shadow="never" class="hero-card">
      <template #header>
        <div class="header-row">
          <div>
            <div class="page-title">我的收藏</div>
            <div class="page-subtitle">快速回看你标记过的重要内容</div>
          </div>
          <el-button @click="$router.push('/patient/article/list')">去逛逛</el-button>
        </div>
      </template>

      <div class="card-grid" v-loading="loading">
        <ArticleFeedCard
          v-for="row in list"
          :key="row.id"
          :row="row"
          :cover-url="coverUrlMap[row.id]"
          :liked="isLiked(row.id)"
          :like-busy="likeBusyId === row.id"
          :can-like="canLikeArticle(row)"
          @click="goDetail(row.id)"
          @like="toggleLike(row)"
        />
      </div>
      <el-empty v-if="!loading && list.length === 0" description="暂无收藏文章">
        <el-button type="primary" @click="$router.push('/patient/article/list')">去逛逛</el-button>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import message from '@/plugins/message'
import { getMyFavorites, getArticleStatus, likeArticle, unlikeArticle } from '@/api/article'
import { useArticleCoverUrls } from '@/composables/useArticleCoverUrls'
import ArticleFeedCard from '@/components/patient/article/ArticleFeedCard.vue'

const router = useRouter()
const loading = ref(false)
const list = ref([])
const { coverUrlMap, loadCoverUrls } = useArticleCoverUrls()
const likedMap = reactive({})
const likeBusyId = ref(null)

const canLikeArticle = (row) => Number(row?.status) === 1
const isLiked = (id) => !!likedMap[id]

const goDetail = (id) => {
  router.push(`/patient/article/detail/${id}`)
}

async function loadLikeStatuses(rows) {
  Object.keys(likedMap).forEach((k) => delete likedMap[k])
  await Promise.all(
    (rows || []).map(async (row) => {
      try {
        const res = await getArticleStatus(row.id)
        likedMap[row.id] = !!res?.data?.liked
      } catch {
        likedMap[row.id] = false
      }
    })
  )
}

const fetchList = async () => {
  loading.value = true
  try {
    list.value = (await getMyFavorites({ pageNum: 1, pageSize: 50 })).data?.records || []
    await Promise.all([loadCoverUrls(list.value), loadLikeStatuses(list.value)])
  } catch (e) {
    message.error(e?.message || '收藏列表加载异常')
  } finally {
    loading.value = false
  }
}

const toggleLike = async (row) => {
  if (!canLikeArticle(row)) {
    message.info('仅已发布文章可点赞')
    return
  }
  likeBusyId.value = row.id
  const id = row.id
  const was = !!likedMap[id]
  try {
    if (was) await unlikeArticle(id)
    else await likeArticle(id)
    likedMap[id] = !was
    const n = Number(row.likeCount || 0)
    row.likeCount = was ? Math.max(0, n - 1) : n + 1
    message.success(was ? '已取消点赞' : '点赞成功')
  } catch (e) {
    message.error(e?.message || '操作失败')
  } finally {
    likeBusyId.value = null
  }
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

@media (max-width: 768px) {
  .article-page--feed {
    padding: 12px;
  }
  .card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
