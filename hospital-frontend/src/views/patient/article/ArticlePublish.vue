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
        <el-form-item label="分类" prop="category">
          <el-select
            v-model="form.category"
            class="category-select"
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="选择常用分类"
          >
            <el-option v-for="opt in ARTICLE_CATEGORY_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="封面">
          <div class="cover-upload-wrap">
            <el-upload
              v-if="!form.coverImage"
              class="cover-upload"
              drag
              action="#"
              :show-file-list="false"
              :http-request="coverUpload"
              :before-upload="beforeCoverUpload"
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">
                拖拽图片到此处或 <em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                  支持 jpg/png 文件，大小不超过 5MB。
                </div>
              </template>
            </el-upload>
            <div v-else class="cover-preview">
              <el-image :src="coverPreviewUrl" fit="contain" class="cover-preview-img" :preview-src-list="coverPreviewUrl ? [coverPreviewUrl] : []" preview-teleported />
              <div class="cover-preview-actions">
                <el-button type="primary" link @click="form.coverImage = ''">重新上传</el-button>
              </div>
            </div>
          </div>
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import message from '@/plugins/message'
import { useRoute, useRouter } from 'vue-router'
import { getArticleDetail, publishArticle, updateArticle } from '@/api/article'
import { ARTICLE_CATEGORY_OPTIONS } from '@/utils/articleCategory'
import { uploadArticleCover } from '@/api/upload'
import { resolveOssPreviewUrl, sanitizeStoredMediaUrl } from '@/utils/ossPreview'
const router = useRouter()
const route = useRoute()
const submitting = ref(false)
const formRef = ref()
const form = reactive({
  title: '',
  category: '',
  coverImage: '',
  summary: '',
  content: '',
  tags: ''
})

const rules = {
  title: [{ required: true, message: '请填写标题', trigger: 'blur' }],
  category: [
    {
      validator: (_, val, cb) => {
        const s = (val == null ? '' : String(val)).trim()
        if (!s) cb(new Error('请选择或输入分类'))
        else if (s.length > 64) cb(new Error('分类不超过64个字符'))
        else cb()
      },
      trigger: 'change'
    }
  ],
  content: [{ required: true, message: '请填写正文', trigger: 'blur' }]
}

const id = computed(() => route.params.id)
const isEdit = computed(() => !!id.value)

/** 与头像一致：持久化存无签名 URL，展示用预签名或解析后的地址 */
const coverPreviewUrl = ref('')
let coverPreviewSeq = 0
watch(
  () => form.coverImage,
  async (url) => {
    const seq = ++coverPreviewSeq
    if (!url) {
      coverPreviewUrl.value = ''
      return
    }
    const preview = await resolveOssPreviewUrl(url, 60)
    if (seq !== coverPreviewSeq) return
    coverPreviewUrl.value = preview
  },
  { immediate: true }
)

const beforeCoverUpload = (raw) => {
  const name = (raw && raw.name) || ''
  const okType = raw.type === 'image/jpeg' || raw.type === 'image/png' || /\.(jpe?g|png)$/i.test(name)
  if (!okType) {
    message.error('只支持 jpg、png 图片')
    return false
  }
  if (raw.size > 5 * 1024 * 1024) {
    message.error('文件大小不能超过 5MB')
    return false
  }
  return true
}

const coverUpload = async (options) => {
  const file = options.file
  try {
    const res = await uploadArticleCover(file)
    if (res.code === 200 && res.data) {
      form.coverImage = sanitizeStoredMediaUrl(res.data)
      message.success('上传成功')
    }
  } catch (e) {
    message.error((e && e.message) || '上传失败')
  }
}

const loadForEdit = async () => {
  if (!isEdit.value) return
  const res = await getArticleDetail(id.value)
  const data = res.data || {}
  form.title = data.title || ''
  form.category = data.category || ''
  form.coverImage = sanitizeStoredMediaUrl(data.coverImage || '')
  form.summary = data.summary || ''
  form.content = data.content || ''
  form.tags = data.tags || ''
}

const submit = async () => {
  const ok = await formRef.value?.validate?.().catch(() => false)
  if (ok === false) return
  form.category = String(form.category || '').trim()
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
.category-select { width: 100%; max-width: 420px; }

.cover-upload-wrap {
  width: 100%;
  max-width: 420px;
}

.cover-upload :deep(.el-upload) {
  width: 100%;
}

.cover-upload :deep(.el-upload-dragger) {
  width: 100%;
  padding: 28px 16px;
  border-radius: 12px;
  border-color: #dcdfe6;
}

.cover-upload :deep(.el-icon--upload) {
  font-size: 48px;
  color: #409eff;
  margin-bottom: 12px;
}

.cover-upload :deep(.el-upload__text) {
  color: #606266;
  font-size: 14px;
}

.cover-upload :deep(.el-upload__text em) {
  color: #409eff;
  font-style: normal;
}

.cover-upload :deep(.el-upload__tip) {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.cover-preview {
  border: 1px dashed #dcdfe6;
  border-radius: 12px;
  padding: 12px;
  background: #fafafa;
}

.cover-preview-img {
  display: block;
  max-height: 200px;
  width: 100%;
  border-radius: 8px;
}

.cover-preview-actions {
  margin-top: 10px;
  text-align: center;
}
</style>
