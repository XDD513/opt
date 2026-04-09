<template>
  <div class="review-detail">
    <el-card v-loading="loading">
      <template #header>
        <div class="header">
          <div>
            <div class="title">文章审核详情</div>
            <div class="subtitle">查看内容详情并进行审核决策</div>
          </div>
          <div class="actions" v-if="article.status === 0">
            <el-button type="success" :loading="submitting" @click="approve">通过</el-button>
            <el-button type="danger" :loading="submitting" @click="openReject">驳回</el-button>
          </div>
        </div>
      </template>

      <div class="meta">
        <div><span class="label">标题：</span>{{ article.title || '-' }}</div>
        <div><span class="label">作者：</span>{{ article.authorName || '-' }}</div>
        <div><span class="label">分类：</span>{{ article.category || '-' }}</div>
        <div><span class="label">标签：</span>{{ article.tags || '-' }}</div>
        <div>
          <span class="label">状态：</span>
          <el-tag :type="article.status === 1 ? 'success' : article.status === 2 ? 'info' : article.status === 3 ? 'danger' : 'warning'">
            {{ article.status === 1 ? '已发布' : article.status === 2 ? '已下架' : article.status === 3 ? '已驳回' : '待审核' }}
          </el-tag>
        </div>
      </div>

      <el-alert
        v-if="article.rejectReason"
        class="mb12"
        type="warning"
        show-icon
        :closable="false"
        :title="`${article.status === 2 ? '下架原因' : '驳回原因'}：${article.rejectReason}`"
      />

      <div class="content">
        <div class="section-title">摘要</div>
        <div class="text">{{ article.summary || '-' }}</div>

        <div class="section-title">正文</div>
        <div class="text pre">{{ article.content || '-' }}</div>
      </div>
    </el-card>

    <el-dialog v-model="rejectDialogOpen" title="驳回文章" width="520px">
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
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import message from '@/plugins/message'
import { getArticleDetail, reviewArticle } from '@/api/article'

const route = useRoute()
const router = useRouter()
const id = route.params.id

const loading = ref(false)
const submitting = ref(false)
const article = ref({})

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
.review-detail { max-width: 1200px; margin: 0 auto; }
.header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.title { font-weight: 600; }
.subtitle { color: #909399; font-size: 13px; margin-top: 4px; }
.meta { display: grid; grid-template-columns: 1fr 1fr; gap: 10px 16px; margin-bottom: 12px; }
.quick-reasons { display: flex; flex-wrap: wrap; gap: 8px; }
.label { color: #666; }
.mb12 { margin-bottom: 12px; }
.content .section-title { font-weight: 600; margin: 12px 0 6px; }
.text { color: #333; line-height: 1.8; }
.pre { white-space: pre-wrap; }
</style>

