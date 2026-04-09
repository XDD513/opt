<template>
  <div class="article-page">
    <el-card class="section-card">
      <template #header>
        <div>
          <div class="page-title">{{ isEdit ? '编辑文章' : '发布文章' }}</div>
          <div class="page-subtitle">提交后需管理员审核，建议填写完整摘要与标签提升通过率</div>
        </div>
      </template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标题"><el-input v-model="form.title" maxlength="200" show-word-limit /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width: 220px">
            <el-option label="体质养生" value="CONSTITUTION" />
            <el-option label="饮食养生" value="DIET" />
            <el-option label="运动养生" value="EXERCISE" />
            <el-option label="穴位养生" value="ACUPOINT" />
            <el-option label="时令养生" value="SEASON" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="封面">
          <el-input v-model="form.coverImage" placeholder="可填写图片URL（可选）" />
        </el-form-item>
        <el-form-item label="摘要"><el-input v-model="form.summary" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="正文"><el-input v-model="form.content" type="textarea" :rows="12" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="多个标签用逗号分隔" /></el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">{{ isEdit ? '保存并提交' : '提交审核' }}</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import message from '@/plugins/message'
import { useRoute, useRouter } from 'vue-router'
import { getArticleDetail, publishArticle, updateArticle } from '@/api/article'
const router = useRouter()
const route = useRoute()
const submitting = ref(false)
const formRef = ref()
const form = reactive({
  title: '',
  category: 'CONSTITUTION',
  coverImage: '',
  summary: '',
  content: '',
  tags: ''
})

const rules = {
  title: [{ required: true, message: '请填写标题', trigger: 'blur' }],
  content: [{ required: true, message: '请填写正文', trigger: 'blur' }]
}

const id = computed(() => route.params.id)
const isEdit = computed(() => !!id.value)

const loadForEdit = async () => {
  if (!isEdit.value) return
  const res = await getArticleDetail(id.value)
  const data = res.data || {}
  form.title = data.title || ''
  form.category = data.category || 'CONSTITUTION'
  form.coverImage = data.coverImage || ''
  form.summary = data.summary || ''
  form.content = data.content || ''
  form.tags = data.tags || ''
}

const submit = async () => {
  const ok = await formRef.value?.validate?.().catch(() => false)
  if (ok === false) return
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateArticle(id.value, form)
      message.success('保存成功，文章已提交审核，请耐心等待')
    } else {
      await publishArticle(form)
      message.success('提交成功，已进入待审核队列')
    }
    router.push('/patient/article/my')
  } finally { submitting.value = false }
}

onMounted(loadForEdit)
</script>

<style scoped>
.article-page { max-width: 1000px; margin: 0 auto; }
.section-card { border-radius: 12px; }
.page-title { font-size: 20px; font-weight: 600; }
.page-subtitle { color: #909399; margin-top: 4px; }
</style>
