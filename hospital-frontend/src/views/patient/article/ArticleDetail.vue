<template>
  <div class="article-detail">
    <el-card v-loading="loading" class="detail-card">
      <div class="detail-header">
        <div>
          <h2>{{ article.title }}</h2>
          <div class="meta">
            <span>作者：{{ article.authorName || '-' }}</span>
            <span>浏览：{{ article.viewCount || 0 }}</span>
            <span>点赞：{{ article.likeCount || 0 }}</span>
            <span>收藏：{{ article.favoriteCount || 0 }}</span>
            <span>评论：{{ article.commentCount || 0 }}</span>
          </div>
        </div>
        <el-tag v-if="Number(article.isFeatured) === 1" type="danger">推荐文章</el-tag>
      </div>
      <el-alert
        v-if="(article.status === 3 || article.status === 2) && article.rejectReason"
        class="mb12"
        type="warning"
        show-icon
        :closable="false"
        :title="`${article.status === 2 ? '文章下架原因' : '审核未通过'}：${article.rejectReason}`"
      />
      <div class="actions">
        <el-button type="primary" plain :disabled="!canInteract" @click="toggleLike">{{ status.liked ? '取消点赞' : '点赞' }}</el-button>
        <el-button type="warning" plain :disabled="!canInteract" @click="toggleFavorite">{{ status.favorited ? '取消收藏' : '收藏' }}</el-button>
        <el-button @click="$router.back()">返回列表</el-button>
      </div>
      <div class="content">{{ article.content }}</div>
    </el-card>
    <el-card class="mt12">
      <template #header>评论区</template>
      <el-alert
        v-if="!canInteract"
        class="mb12"
        type="info"
        show-icon
        :closable="false"
        title="当前文章未发布，仅支持查看详情，暂不可点赞、收藏、评论。"
      />
      <el-input v-model="commentText" type="textarea" :rows="3" placeholder="说点什么..." :disabled="!canInteract" />
      <el-button class="mt8" type="primary" :disabled="!canInteract" @click="submitComment">发表评论</el-button>
      <el-empty v-if="comments.length === 0" description="暂无评论" />
      <div v-for="item in comments" :key="item.id" class="comment-item">
        <el-avatar class="comment-avatar" :size="44" :src="commentAvatar(item)">
          {{ userInitial(commentDisplayName(item)) }}
        </el-avatar>
        <div class="comment-main">
          <div class="comment-name">{{ commentDisplayName(item) }}</div>
          <div class="comment-content">{{ item.content }}</div>
          <div class="comment-time">{{ formatCommentTime(item.createTime || item.createdAt || item.createAt || item.time) }}</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import message from '@/plugins/message'
import { favoriteArticle, getArticleDetail, getArticleStatus, likeArticle, unfavoriteArticle, unlikeArticle } from '@/api/article'
import { getCommentList, publishComment } from '@/api/comment'
import { getUserInfo } from '@/api/user'
const route = useRoute()
const id = route.params.id
const loading = ref(false)
const article = ref({})
const status = ref({ liked: false, favorited: false })
const comments = ref([])
const commentText = ref('')
const currentUser = ref(null)
const canInteract = computed(() => Number(article.value?.status) === 1)
const loadDetail = async () => {
  loading.value = true
  try {
    const [detailRes, statusRes, commentRes, userRes] = await Promise.all([
      getArticleDetail(id),
      getArticleStatus(id),
      getCommentList({ articleId: id, pageNum: 1, pageSize: 50 }),
      getUserInfo().catch(() => null)
    ])
    article.value = detailRes.data || {}
    status.value = statusRes.data || { liked: false, favorited: false }
    comments.value = commentRes.data?.records || []
    currentUser.value = userRes?.data || null
  } finally { loading.value = false }
}
const toggleLike = async () => {
  if (!canInteract.value) return
  const wasLiked = !!status.value.liked
  if (wasLiked) await unlikeArticle(id)
  else await likeArticle(id)
  status.value.liked = !wasLiked
  const current = Number(article.value.likeCount || 0)
  article.value.likeCount = wasLiked ? Math.max(0, current - 1) : current + 1
  message.success(wasLiked ? '已取消点赞，你的反馈已记录' : '点赞成功，你的反馈已记录')
}
const toggleFavorite = async () => {
  if (!canInteract.value) return
  const wasFavorited = !!status.value.favorited
  if (wasFavorited) await unfavoriteArticle(id)
  else await favoriteArticle(id)
  status.value.favorited = !wasFavorited
  const current = Number(article.value.favoriteCount || 0)
  article.value.favoriteCount = wasFavorited ? Math.max(0, current - 1) : current + 1
  message.success(wasFavorited ? '已取消收藏' : '收藏成功，可在我的收藏中查看')
}
const submitComment = async () => {
  if (!canInteract.value) return
  if (!commentText.value.trim()) return
  await publishComment({ articleId: id, content: commentText.value, parentId: 0 })
  commentText.value = ''
  message.success('评论成功，感谢你的参与')
  await loadDetail()
}

const userInitial = (nameOrId) => {
  const s = String(nameOrId || '').trim()
  if (!s) return '?'
  return s.slice(0, 1)
}

const commentDisplayName = (item) => {
  const name = (item && (item.userName || item.realName || item.username)) || ''
  if (String(name).trim()) return String(name).trim()
  const uid = item?.userId
  const me = currentUser.value
  if (uid && me && String(uid) === String(me.id)) return me.realName || me.username || String(uid)
  return uid ? String(uid) : '匿名'
}

const commentAvatar = (item) => {
  const url = item?.userAvatar || item?.avatarUrl || item?.avatar || ''
  if (String(url).trim()) return String(url).trim()
  const uid = item?.userId
  const me = currentUser.value
  if (uid && me && String(uid) === String(me.id)) return me.avatar || ''
  return ''
}

const formatCommentTime = (raw) => {
  if (!raw) return ''
  const d = raw instanceof Date ? raw : new Date(raw)
  if (Number.isNaN(d.getTime())) return ''
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}
onMounted(loadDetail)
</script>

<style scoped>
.article-detail { max-width: 1200px; margin: 0 auto; }
.detail-card { border-radius: 12px; }
.detail-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.meta { color: #999; display: flex; gap: 16px; margin: 8px 0 12px; }
.mb12 { margin-bottom: 12px; }
.actions { margin-bottom: 12px; }
.content { white-space: pre-wrap; line-height: 1.8; }
.mt12 { margin-top: 12px; }
.mt8 { margin-top: 8px; }
.comment-item {
  border-top: 1px solid #f0f0f0;
  padding: 14px 0;
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.comment-avatar {
  flex: 0 0 auto;
}
.comment-main {
  flex: 1;
  min-width: 0;
}
.comment-name {
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}
.comment-content {
  margin-top: 6px;
  color: #606266;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.comment-time {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
</style>
