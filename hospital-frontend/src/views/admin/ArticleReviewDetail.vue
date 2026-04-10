<template>
  <div class="review-detail" v-loading="loading">
    <div class="page-inner">
      <div class="toolbar">
        <el-button text type="primary" class="back-btn" @click="router.push('/admin/article/review')">
          <el-icon class="back-icon"><ArrowLeft /></el-icon>
          返回审核列表
        </el-button>
      </div>

      <el-card class="surface hero-card" shadow="never">
        <div class="hero">
          <div class="hero-text">
            <h1 class="hero-title">文章审核详情</h1>
            <p class="hero-desc">查看内容详情并进行审核决策</p>
          </div>
          <div v-if="article.status === 0" class="hero-actions">
            <el-button type="success" size="large" :loading="submitting" @click="approve">通过</el-button>
            <el-button type="danger" size="large" plain :loading="submitting" @click="openReject">驳回</el-button>
          </div>
        </div>
      </el-card>

      <el-card class="surface meta-card" shadow="never">
        <template #header>
          <div class="card-header-inner">
            <span class="card-title">基本信息</span>
            <el-tag :type="statusTagType" effect="light" round>{{ statusText }}</el-tag>
          </div>
        </template>
        <el-descriptions :column="2" border class="info-desc" size="default">
          <el-descriptions-item label="标题" :span="2">
            <span class="value-strong">{{ article.title || '—' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="作者">{{ article.authorName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ categoryLabel }}</el-descriptions-item>
          <el-descriptions-item label="标签" :span="2">
            <template v-if="tagList.length">
              <el-tag v-for="t in tagList" :key="t" size="small" effect="plain" class="tag-chip">{{ t }}</el-tag>
            </template>
            <span v-else class="muted">—</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-alert
        v-if="article.rejectReason"
        class="reject-alert"
        type="warning"
        show-icon
        :closable="false"
        :title="`${article.status === 2 ? '下架原因' : '驳回原因'}：${article.rejectReason}`"
      />

      <div class="content-grid">
        <el-card class="surface content-card" shadow="never">
          <template #header>
            <div class="section-head">
              <span class="section-bar" aria-hidden="true" />
              <span class="section-title">摘要</span>
            </div>
          </template>
          <div class="prose summary-body">{{ article.summary || '暂无摘要' }}</div>
        </el-card>

        <el-card class="surface content-card content-card--body" shadow="never">
          <template #header>
            <div class="section-head">
              <span class="section-bar section-bar--body" aria-hidden="true" />
              <span class="section-title">正文</span>
            </div>
          </template>
          <div class="prose body-text">{{ article.content || '暂无正文' }}</div>
        </el-card>
      </div>

      <div v-if="article.status === 0" class="sticky-bar">
        <div class="sticky-inner">
          <span class="sticky-hint">待审核：请通读摘要与正文后作出决定</span>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import message from '@/plugins/message'
import { getArticleDetail, reviewArticle } from '@/api/article'
import { formatArticleCategory } from '@/utils/articleCategory'

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
  max-width: 1040px;
  margin: 0 auto;
  padding-bottom: 88px;
}

.toolbar {
  margin-bottom: 16px;
}

.back-btn {
  padding-left: 4px;
  font-weight: 500;
}

.back-icon {
  margin-right: 4px;
  vertical-align: middle;
}

.surface {
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
  margin-bottom: 16px;
}

.surface :deep(.el-card__header) {
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding: 14px 20px;
}

.surface :deep(.el-card__body) {
  padding: 20px;
}

.hero-card :deep(.el-card__body) {
  padding: 22px 24px;
}

.hero {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.hero-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  letter-spacing: 0.02em;
}

.hero-desc {
  margin: 8px 0 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.card-header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.info-desc {
  --el-descriptions-item-bordered-label-background: var(--el-fill-color-light);
}

.info-desc :deep(.el-descriptions__label) {
  width: 88px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
}

.value-strong {
  font-weight: 500;
  color: var(--el-text-color-primary);
  line-height: 1.6;
}

.tag-chip {
  margin: 0 8px 6px 0;
}

.muted {
  color: var(--el-text-color-placeholder);
}

.reject-alert {
  margin-bottom: 16px;
  border-radius: 10px;
}

.content-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.section-bar {
  width: 4px;
  height: 16px;
  border-radius: 2px;
  background: linear-gradient(180deg, var(--el-color-primary) 0%, var(--el-color-primary-light-3) 100%);
  flex-shrink: 0;
}

.section-bar--body {
  background: linear-gradient(180deg, #67c23a 0%, #95d475 100%);
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.prose {
  color: var(--el-text-color-regular);
  font-size: 15px;
  line-height: 1.85;
  letter-spacing: 0.01em;
}

.summary-body {
  min-height: 48px;
}

.body-text {
  white-space: pre-wrap;
  word-break: break-word;
  min-height: 120px;
}

.content-card--body :deep(.el-card__body) {
  max-height: min(60vh, 640px);
  overflow-y: auto;
}

.sticky-bar {
  position: sticky;
  bottom: 16px;
  z-index: 10;
  margin-top: 8px;
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
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
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

@media (max-width: 768px) {
  .review-detail {
    padding: 12px 12px 24px;
  }

  .hero-title {
    font-size: 18px;
  }

  .hero-actions {
    width: 100%;
  }

  .hero-actions .el-button {
    flex: 1;
  }

  .sticky-inner {
    flex-direction: column;
    align-items: stretch;
  }

  .sticky-actions {
    justify-content: stretch;
  }

  .sticky-actions .el-button {
    flex: 1;
  }
}
</style>
