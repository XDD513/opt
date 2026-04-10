<template>
  <div class="review-detail" v-loading="loading">
    <div class="page-inner">
      <div class="toolbar">
        <el-button text type="primary" class="back-btn" @click="router.push('/admin/article/review')">
          <el-icon class="back-icon"><ArrowLeft /></el-icon>
          返回审核列表
        </el-button>
      </div>

      <el-card class="main-card" shadow="never">
        <template #header>
          <div class="card-top">
            <div class="title-block">
              <h1 class="article-title">{{ article.title || '—' }}</h1>
              <p class="page-hint">审核详情 · 请核对封面、摘要与正文</p>
            </div>
            <div class="card-top-actions">
              <el-tag :type="statusTagType" effect="light" round size="large">{{ statusText }}</el-tag>
            </div>
          </div>
        </template>

        <div class="card-body-inner">
          <el-alert
            v-if="article.rejectReason"
            class="reject-alert"
            type="warning"
            show-icon
            :closable="false"
            :title="`${article.status === 2 ? '下架原因' : '驳回原因'}：${article.rejectReason}`"
          />

          <div class="review-layout">
            <aside class="cover-col">
              <div class="cover-label">封面</div>
              <div v-if="article.coverImage" class="cover-box">
                <el-image
                  v-if="coverDisplayUrl"
                  :src="coverDisplayUrl"
                  fit="contain"
                  class="cover-img"
                  :preview-src-list="coverDisplayUrl ? [coverDisplayUrl] : []"
                  preview-teleported
                />
                <div v-else class="cover-placeholder muted">加载中…</div>
              </div>
              <div v-else class="cover-box cover-box--empty muted">暂无封面</div>
            </aside>

            <div class="detail-col">
              <dl class="meta-list">
                <div class="meta-row">
                  <dt>作者</dt>
                  <dd>{{ article.authorName || '—' }}</dd>
                </div>
                <div class="meta-row">
                  <dt>分类</dt>
                  <dd>{{ categoryLabel }}</dd>
                </div>
                <div class="meta-row meta-row--tags">
                  <dt>标签</dt>
                  <dd>
                    <template v-if="tagList.length">
                      <el-tag v-for="t in tagList" :key="t" size="small" effect="plain" class="tag-chip">{{ t }}</el-tag>
                    </template>
                    <span v-else class="muted">—</span>
                  </dd>
                </div>
              </dl>

              <section class="text-section">
                <h2 class="text-section__title">摘要</h2>
                <div class="prose summary-text">{{ article.summary || '暂无摘要' }}</div>
              </section>

              <section class="text-section text-section--body">
                <h2 class="text-section__title">正文</h2>
                <div class="prose body-scroll">{{ article.content || '暂无正文' }}</div>
              </section>
            </div>
          </div>
        </div>
      </el-card>

      <div v-if="article.status === 0" class="sticky-bar">
        <div class="sticky-inner">
          <span class="sticky-hint">待审核：确认无误后可通过，有问题请驳回并填写原因</span>
          <div class="sticky-actions">
            <el-button type="success" :loading="submitting" @click="approve">通过</el-button>
            <el-button type="danger" plain :loading="submitting" @click="openReject">驳回</el-button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="rejectDialogOpen" title="驳回文章" width="520px" class="reject-dialog" destroy-on-close>
      <el-form :model="rejectForm" label-width="90px">
        <el-form-item label="驳回原因" required>
          <el-input v-model="rejectForm.reason" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="快捷意见">
          <div class="quick-reasons">
            <el-button v-for="reason in quickReasons" :key="reason" size="small" @click="applyQuickReason(reason)">{{ reason }}</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogOpen = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="reject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import message from '@/plugins/message'
import { getArticleDetail, reviewArticle } from '@/api/article'
import { formatArticleCategory } from '@/utils/articleCategory'
import { resolveOssPreviewUrl, sanitizeStoredMediaUrl } from '@/utils/ossPreview'

const route = useRoute()
const router = useRouter()
const id = route.params.id

const loading = ref(false)
const submitting = ref(false)
const article = ref({})

const tagList = computed(() => {
  const raw = article.value?.tags
  if (raw == null || raw === '') return []
  const s = String(raw)
  return s.split(/[,，、\s]+/).map((x) => x.trim()).filter(Boolean)
})

const categoryLabel = computed(() => formatArticleCategory(article.value?.category))

const coverDisplayUrl = ref('')
let coverPreviewSeq = 0
watch(
  () => article.value?.coverImage,
  async (url) => {
    const seq = ++coverPreviewSeq
    if (!url) {
      coverDisplayUrl.value = ''
      return
    }
    const clean = sanitizeStoredMediaUrl(String(url))
    const preview = await resolveOssPreviewUrl(clean, 60)
    if (seq !== coverPreviewSeq) return
    coverDisplayUrl.value = preview
  },
  { immediate: true }
)

const statusText = computed(() => {
  const s = article.value?.status
  if (s === 1) return '已发布'
  if (s === 2) return '已下架'
  if (s === 3) return '已驳回'
  return '待审核'
})

const statusTagType = computed(() => {
  const s = article.value?.status
  if (s === 1) return 'success'
  if (s === 2) return 'info'
  if (s === 3) return 'danger'
  return 'warning'
})

const rejectDialogOpen = ref(false)
const rejectForm = reactive({ reason: '' })
const quickReasons = [
  '内容与养生主题关联度不足，请补充专业依据。',
  '文章结构不完整，请补充摘要与分段说明。',
  '存在明显错别字或语句不通顺，请修订后重提。',
  '建议补充可执行的养生建议，增强实用性。'
]

const fetchDetail = async () => {
  loading.value = true
  try {
    const res = await getArticleDetail(id)
    article.value = res.data || {}
  } finally {
    loading.value = false
  }
}

const approve = async () => {
  submitting.value = true
  try {
    await reviewArticle(id, true)
    message.success('审核通过，文章已发布上线')
    await fetchDetail()
    router.push('/admin/article/review')
  } finally {
    submitting.value = false
  }
}

const openReject = () => {
  rejectForm.reason = ''
  rejectDialogOpen.value = true
}

const applyQuickReason = (reason) => {
  rejectForm.reason = rejectForm.reason ? `${rejectForm.reason}\n${reason}` : reason
}

const reject = async () => {
  if (!rejectForm.reason.trim()) return ElMessage.warning('请填写驳回原因')
  submitting.value = true
  try {
    await reviewArticle(id, false, rejectForm.reason)
    message.success('驳回成功，作者可根据意见修改后重提')
    rejectDialogOpen.value = false
    await fetchDetail()
    router.push('/admin/article/review')
  } finally {
    submitting.value = false
  }
}

onMounted(fetchDetail)
</script>

<style scoped>
.review-detail {
  min-height: 100%;
  background: var(--el-bg-color-page, #f5f7fa);
  padding: 20px 20px 32px;
  box-sizing: border-box;
}

.page-inner {
  max-width: 1100px;
  margin: 0 auto;
  padding-bottom: 88px;
}

.toolbar {
  margin-bottom: 12px;
}

.back-btn {
  padding-left: 4px;
  font-weight: 500;
}

.back-icon {
  margin-right: 4px;
  vertical-align: middle;
}

.main-card {
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
}

.main-card :deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.main-card :deep(.el-card__body) {
  padding: 0;
}

.card-top {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.title-block {
  min-width: 0;
  flex: 1;
}

.article-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  line-height: 1.4;
  word-break: break-word;
}

.page-hint {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.card-top-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.card-body-inner {
  padding: 20px;
}

.reject-alert {
  margin-bottom: 20px;
  border-radius: 8px;
}

.review-layout {
  display: grid;
  grid-template-columns: minmax(200px, 280px) 1fr;
  gap: 24px 28px;
  align-items: start;
}

.cover-col {
  position: sticky;
  top: 16px;
}

.cover-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  margin-bottom: 10px;
}

.cover-box {
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: 10px;
  overflow: hidden;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}

.cover-box--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}

.cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 14px;
}

.cover-img {
  width: 100%;
  height: 100%;
  display: block;
}

.cover-img :deep(.el-image__wrapper),
.cover-img :deep(.el-image__inner) {
  width: 100% !important;
  height: 100% !important;
}

.cover-img :deep(.el-image__inner) {
  object-fit: contain;
}

.detail-col {
  min-width: 0;
}

.meta-list {
  margin: 0 0 20px;
  padding: 14px 16px;
  background: var(--el-fill-color-blank, #fafafa);
  border-radius: 10px;
  border: 1px solid var(--el-border-color-lighter);
}

.meta-row {
  display: grid;
  grid-template-columns: 52px 1fr;
  gap: 12px;
  font-size: 14px;
  line-height: 1.6;
  padding: 6px 0;
}

.meta-row:not(:last-child) {
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.meta-row dt {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-weight: 500;
}

.meta-row dd {
  margin: 0;
  color: var(--el-text-color-primary);
  word-break: break-word;
}

.meta-row--tags dd {
  line-height: 1.8;
}

.tag-chip {
  margin: 0 8px 4px 0;
}

.muted {
  color: var(--el-text-color-placeholder);
}

.text-section {
  margin-bottom: 20px;
}

.text-section:last-child {
  margin-bottom: 0;
}

.text-section__title {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  padding-left: 10px;
  border-left: 3px solid var(--el-color-primary);
}

.text-section--body .text-section__title {
  border-left-color: #67c23a;
}

.prose {
  color: var(--el-text-color-regular);
  font-size: 15px;
  line-height: 1.85;
  letter-spacing: 0.01em;
}

.summary-text {
  padding: 0 2px;
}

.body-scroll {
  white-space: pre-wrap;
  word-break: break-word;
  max-height: min(52vh, 560px);
  overflow-y: auto;
  padding: 12px 14px;
  background: var(--el-fill-color-blank, #fafafa);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-extra-light);
}

.sticky-bar {
  position: sticky;
  bottom: 16px;
  z-index: 10;
  margin-top: 16px;
}

.sticky-inner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 20px;
  background: var(--el-bg-color-overlay, #fff);
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.06);
}

.sticky-hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.sticky-actions {
  display: flex;
  gap: 10px;
}

.quick-reasons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 900px) {
  .review-layout {
    grid-template-columns: 1fr;
  }

  .cover-col {
    position: static;
    max-width: 360px;
    margin: 0 auto;
  }
}

@media (max-width: 768px) {
  .review-detail {
    padding: 12px 12px 24px;
  }

  .article-title {
    font-size: 18px;
  }

  .sticky-inner {
    flex-direction: column;
    align-items: stretch;
  }

  .sticky-actions .el-button {
    flex: 1;
  }
}
</style>
