<template>
  <article
    class="article-card"
    :class="{ 'article-card--text': !row.coverImage }"
    @click="$emit('click')"
  >
    <template v-if="row.coverImage">
      <div class="card-cover">
        <el-image
          v-if="coverUrl"
          :src="coverUrl"
          fit="cover"
          class="cover-img"
          loading="lazy"
        >
          <template #error>
            <div class="cover-fallback"><el-icon :size="36"><Picture /></el-icon></div>
          </template>
        </el-image>
        <div v-else class="cover-fallback muted">加载中…</div>
        <el-tag v-if="showFeaturedBadge && Number(row.isFeatured) === 1" class="cover-badge" type="danger" size="small">推荐</el-tag>
      </div>
      <div class="card-body">
        <h3 class="card-title">{{ row.title || '未命名' }}</h3>
        <div class="card-stats">
          <span>浏览 {{ row.viewCount ?? 0 }}</span>
          <span class="dot">·</span>
          <span>评论 {{ row.commentCount ?? 0 }}</span>
          <span class="dot">·</span>
          <span>收藏 {{ row.favoriteCount ?? 0 }}</span>
        </div>
        <div class="card-footer">
          <slot name="footer">
            <div class="author">
              <el-avatar :size="32" class="author-avatar">{{ authorInitial(row.authorName) }}</el-avatar>
              <span class="author-name">{{ row.authorName || '匿名' }}</span>
            </div>
            <button
              v-if="showLike"
              type="button"
              class="like-btn"
              :class="{ 'like-btn--active': liked }"
              :disabled="likeBusy || !canLike"
              @click.stop="$emit('like')"
            >
              <span class="heart-wrap" aria-hidden="true">
                <svg v-if="liked" class="heart heart--filled" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path d="M12 21s-6.716-4.576-9.5-8.5C-.5 8.5 1.5 4 6 4c2.28 0 4.5 1.54 6 3.82C13.5 5.54 15.72 4 18 4c4.5 0 6.5 4.5 3.5 8.5C18.716 16.424 12 21 12 21z" fill="currentColor" />
                </svg>
                <svg v-else class="heart heart--outline" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                  <path d="M12 21s-6.716-4.576-9.5-8.5C-.5 8.5 1.5 4 6 4c2.28 0 4.5 1.54 6 3.82C13.5 5.54 15.72 4 18 4c4.5 0 6.5 4.5 3.5 8.5C18.716 16.424 12 21 12 21z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
                </svg>
              </span>
              <span class="like-num">{{ row.likeCount ?? 0 }}</span>
            </button>
          </slot>
        </div>
      </div>
    </template>

    <div v-else class="card-body card-body--plain">
      <div class="plain-title-row">
        <h3 class="card-title card-title--plain">{{ row.title || '未命名' }}</h3>
        <el-tag v-if="showFeaturedBadge && Number(row.isFeatured) === 1" size="small" type="danger">推荐</el-tag>
      </div>
      <div class="card-summary">{{ row.summary || '暂无摘要' }}</div>
      <div class="card-meta-line">
        <span>浏览 {{ row.viewCount ?? 0 }}</span>
        <span>评论 {{ row.commentCount ?? 0 }}</span>
        <span>收藏 {{ row.favoriteCount ?? 0 }}</span>
      </div>
      <div class="card-footer">
        <slot name="footer">
          <div class="author">
            <el-avatar :size="32" class="author-avatar">{{ authorInitial(row.authorName) }}</el-avatar>
            <span class="author-name">{{ row.authorName || '匿名' }}</span>
          </div>
          <button
            v-if="showLike"
            type="button"
            class="like-btn"
            :class="{ 'like-btn--active': liked }"
            :disabled="likeBusy || !canLike"
            @click.stop="$emit('like')"
          >
            <span class="heart-wrap" aria-hidden="true">
              <svg v-if="liked" class="heart heart--filled" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 21s-6.716-4.576-9.5-8.5C-.5 8.5 1.5 4 6 4c2.28 0 4.5 1.54 6 3.82C13.5 5.54 15.72 4 18 4c4.5 0 6.5 4.5 3.5 8.5C18.716 16.424 12 21 12 21z" fill="currentColor" />
              </svg>
              <svg v-else class="heart heart--outline" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 21s-6.716-4.576-9.5-8.5C-.5 8.5 1.5 4 6 4c2.28 0 4.5 1.54 6 3.82C13.5 5.54 15.72 4 18 4c4.5 0 6.5 4.5 3.5 8.5C18.716 16.424 12 21 12 21z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
              </svg>
            </span>
            <span class="like-num">{{ row.likeCount ?? 0 }}</span>
          </button>
        </slot>
      </div>
    </div>
  </article>
</template>

<script setup>
import { Picture } from '@element-plus/icons-vue'

defineProps({
  row: { type: Object, required: true },
  coverUrl: { type: String, default: '' },
  liked: { type: Boolean, default: false },
  likeBusy: { type: Boolean, default: false },
  canLike: { type: Boolean, default: true },
  showLike: { type: Boolean, default: true },
  showFeaturedBadge: { type: Boolean, default: true }
})

defineEmits(['click', 'like'])

function authorInitial(name) {
  const s = (name || '').trim()
  if (!s) return '?'
  return s.slice(0, 1)
}
</script>

<style scoped>
.article-card {
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}
.article-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.article-card--text {
  display: flex;
  flex-direction: column;
}

.card-body--plain {
  padding: 14px 16px 16px;
  flex: 1;
  display: flex;
  flex-direction: column;
}
.plain-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}
.card-title--plain {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
  margin: 0;
  color: var(--el-text-color-primary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-summary {
  margin-top: 8px;
  min-height: 44px;
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-meta-line {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: #909399;
}
.card-body--plain .card-footer {
  margin-top: auto;
  padding-top: 12px;
}

.card-cover {
  position: relative;
  aspect-ratio: 4 / 3;
  background: var(--el-fill-color-light);
  overflow: hidden;
}
.cover-img {
  width: 100%;
  height: 100%;
  display: block;
}
.cover-img :deep(.el-image__inner) {
  width: 100% !important;
  height: 100% !important;
  object-fit: cover;
}
.cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #c0c4cc;
  font-size: 13px;
}
.cover-badge {
  position: absolute;
  top: 8px;
  right: 8px;
}

.card-body {
  padding: 12px 14px 14px;
}
.card-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-stats {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
.card-stats .dot {
  margin: 0 4px;
  opacity: 0.7;
}

.card-footer {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.author {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.author-avatar {
  flex-shrink: 0;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 13px;
}
.author-name {
  font-size: 13px;
  color: var(--el-text-color-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.like-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  padding: 4px 6px;
  border-radius: 8px;
  cursor: pointer;
  color: #909399;
  font-size: 13px;
  line-height: 1;
}
.like-btn:hover:not(:disabled) {
  background: var(--el-fill-color-light);
  color: var(--el-color-danger);
}
.like-btn--active {
  color: #f56c6c;
}
.like-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.heart-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
}
.heart {
  width: 20px;
  height: 20px;
}
.like-num {
  font-variant-numeric: tabular-nums;
}

.muted {
  color: var(--el-text-color-placeholder);
}
</style>
