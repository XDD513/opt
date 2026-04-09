<template>
  <div class="tongue-diagnosis-upload">
    <!-- 上传区域：只在未有分析结果时显示，避免与下方结果重复占位 -->
    <el-upload
      v-if="!result"
      class="upload-demo"
      drag
      action="#"
      :http-request="uploadFile"
      :show-file-list="false"
      :before-upload="beforeUpload"
    >
      <el-icon class="el-icon--upload"><upload-filled /></el-icon>
      <div class="el-upload__text">
        拖拽图片到此处或 <em>点击上传</em>
      </div>
      <template #tip>
        <div class="el-upload__tip">
          支持 jpg/png 文件，大小不超过 5MB。请在自然光下拍摄舌头照片。
        </div>
      </template>
    </el-upload>

    <div v-if="loading" class="loading-result">
      <el-skeleton :rows="3" animated />
      <p>正在分析舌象...</p>
    </div>

    <div v-if="result" class="analysis-result">
      <div class="result-image-container" v-if="result.image_base64">
        <p class="sub-title">识别结果图：</p>
        <div class="image-wrapper">
          <el-image 
            :src="result.image_base64" 
            :preview-src-list="[result.image_base64]"
            :preview-teleported="true"
            fit="contain"
            class="result-image"
          />
        </div>
      </div>

      <el-alert
        title="舌诊分析完成"
        type="success"
        :description="`主特征：${(result.features_list && result.features_list.length > 0) ? result.features_list.join(', ') : (result.feature || '未检测到明显特征')}`"
        show-icon
        :closable="false"
      />
      
      <div class="features-list-container" v-if="result.features_detail && result.features_detail.length > 0">
        <p class="sub-title">详细特征置信度：</p>
        <div class="feature-item" v-for="item in result.features_detail" :key="item.name">
          <span class="feature-name">{{ item.name }}</span>
          <el-progress 
            :percentage="Math.round(item.confidence * 100)" 
            :color="customColors"
            class="feature-progress"
          />
        </div>
      </div>
      
      <!-- 添加一个调试显示，如果 features_detail 为空但 visual_features 不为空 -->
      <div class="features-debug-container" v-else-if="result.visual_features && Object.keys(result.visual_features).length > 0">
        <p class="sub-title">特征检测结果 (原始数据)：</p>
        <div class="feature-item" v-for="(score, name) in result.visual_features" :key="name">
          <span class="feature-name">{{ name }}</span>
          <el-progress 
            :percentage="Math.round(score * 100)" 
            :color="customColors"
            class="feature-progress"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const props = defineProps({
  initialResult: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['analysis-complete'])

const loading = ref(false)
const result = ref(null)

watch(
  () => props.initialResult,
  (val) => {
    result.value = val || null
  },
  { immediate: true }
)

const customColors = [
  { color: '#909399', percentage: 40 },
  { color: '#e6a23c', percentage: 70 },
  { color: '#67c23a', percentage: 100 },
]

const beforeUpload = (file) => {
  const isJPGOrPNG = file.type === 'image/jpeg' || file.type === 'image/png'
  if (!isJPGOrPNG) {
    ElMessage.error('只能上传 JPG/PNG 文件!')
    return false
  }
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isLt5M) {
    ElMessage.error('上传图片大小不能超过 5MB!')
    return false
  }
  return true
}

const uploadFile = async (options) => {
  const formData = new FormData()
  formData.append('file', options.file)

  loading.value = true
  result.value = null

  try {
    const res = await request.post('/constitution/tongue-diagnosis', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    if (res.code === 200 && res.data) {
      result.value = res.data
      // 发送完整的分析结果给父组件，包含 feature, features_list, image_url 等
      emit('analysis-complete', res.data)
      ElMessage.success('舌诊分析成功')
    } else {
      ElMessage.error(res.message || '分析失败')
    }
  } catch (error) {
    console.error('舌诊上传错误:', error)
    ElMessage.error('分析失败，请确认后端及AI服务已启动')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.tongue-diagnosis-upload {
  width: 100%;
}

.loading-result {
  margin-top: 20px;
  text-align: center;
}

.analysis-result {
  margin-top: 14px;
  padding: 16px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.result-image-container {
  margin-bottom: 16px;
  text-align: center;
}

.image-wrapper {
  display: inline-block;
  max-width: 100%;
  max-height: 360px; /* 限制最大显示高度，避免占满屏 */
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  background: #000; /* 黑色背景对比度更好 */
}

.result-image {
  display: block;
  max-width: 100%;
  max-height: 360px; /* 必须与 wrapper 一致 */
  object-fit: contain;
}

.sub-title {
  font-size: 14px;
  font-weight: bold;
  color: #475569;
  margin-bottom: 6px;
}

.features-list-container {
  margin-top: 16px;
}

.feature-item {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.feature-name {
  width: 100px;
  font-size: 13px;
  color: #64748b;
}

.feature-progress {
    flex: 1;
  }
</style>
