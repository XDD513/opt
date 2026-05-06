<template>
  <div class="tongue-diagnosis-upload">
    <!-- 与 video 同尺寸的离屏 canvas，用于 drawImage + toBlob -->
    <canvas ref="captureCanvasRef" class="capture-canvas" />

    <!-- 上传 / 拍照：同一区域切换，无底栏抽屉 -->
    <div v-if="!result" class="tongue-upload-zone">
      <!-- 无 getUserMedia 时（如 HTTP 非安全上下文）用原生 capture 调起系统相机 / 相册 -->
      <!-- accept 用 image/*：配合 capture 在多数手机上优先调起相机；窄 accept 易变成「仅相册」 -->
      <input
        ref="nativeCaptureInputRef"
        type="file"
        accept="image/*"
        capture="user"
        class="native-capture-input"
        aria-hidden="true"
        tabindex="-1"
        @change="onNativeCaptureChange"
      />
      <el-upload
        v-if="!cameraInlineActive"
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
          <p v-if="showHttpsCameraHint" class="https-camera-hint">
            网页实时取景需要 HTTPS 或 localhost；当前环境可使用下方「拍照上传」调起系统相机，或拖拽 / 点击上传已有照片。
          </p>
        </template>
      </el-upload>
      <div
        v-if="!cameraInlineActive"
        class="upload-extra-actions upload-extra-actions--below"
        @click.stop
      >
        <el-button
          type="primary"
          plain
          size="small"
          :icon="Camera"
          @click="onPhotoUploadClick"
        >
          拍照上传
        </el-button>
      </div>

      <div v-else class="camera-inline-panel">
        <div class="camera-inline-header">
          <span class="camera-inline-title">拍照上传舌象</span>
          <el-button type="info" link size="small" @click="closeCameraInline">返回上传</el-button>
        </div>
        <div class="camera-inline-body">
          <div class="camera-stage">
            <template v-if="cameraPhase === 'live'">
              <div class="camera-viewport">
                <video
                  ref="cameraVideoRef"
                  class="camera-video"
                  autoplay
                  playsinline
                  muted
                />
                <div
                  v-if="cameraStarting"
                  class="camera-loading camera-overlay"
                  role="status"
                  aria-busy="true"
                />
                <p v-else-if="cameraError" class="camera-error camera-overlay camera-overlay--solid">{{ cameraError }}</p>
              </div>
            </template>
            <template v-else-if="cameraPhase === 'preview' && capturedPreviewUrl">
              <div class="camera-viewport camera-viewport--preview">
                <img :src="capturedPreviewUrl" class="camera-preview" alt="舌象预览" />
              </div>
            </template>
          </div>

          <div class="camera-toolbar">
            <template v-if="cameraPhase === 'live' && !cameraStarting && !cameraError">
              <el-button type="primary" size="large" round :disabled="!streamReady" @click="capturePhoto">
                拍照
              </el-button>
            </template>
            <template v-else-if="cameraPhase === 'preview'">
              <el-button size="large" round @click="retakePhoto">重拍</el-button>
              <el-button type="primary" size="large" round :loading="loading" @click="confirmCapturedPhoto">
                使用此照片
              </el-button>
            </template>
          </div>
        </div>
      </div>
    </div>

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
        :title="alertTitle"
        :type="alertType"
        :description="alertDescription"
        show-icon
        :closable="false"
      />
      
      <div v-if="isFallback" class="fallback-hint">
        当前识别服务处于降级状态，已保留图片上传结果，请稍后重试舌诊分析。
      </div>

      <div class="features-list-container" v-else-if="result.features_detail && result.features_detail.length > 0">
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
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { UploadFilled, Camera } from '@element-plus/icons-vue'
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

/** 为 true 时在同一上传区域展示网页拍照，替代 el-upload */
const cameraInlineActive = ref(false)
const cameraVideoRef = ref(null)
const captureCanvasRef = ref(null)
const cameraStarting = ref(false)
const cameraPhase = ref('live')
const cameraError = ref('')
const capturedBlob = ref(null)
const capturedPreviewUrl = ref(null)
const mediaStream = ref(null)
const nativeCaptureInputRef = ref(null)

function hasGetUserMedia() {
  return typeof navigator !== 'undefined' && !!navigator.mediaDevices?.getUserMedia
}

/** 手机端「拍照上传」优先走系统相机意图；桌面端仍用网页内 getUserMedia，体验更稳定 */
function shouldPreferNativeCameraOnPhotoClick() {
  if (typeof navigator === 'undefined') return false
  const ua = navigator.userAgent || ''
  const padOs =
    navigator.platform === 'MacIntel' && typeof navigator.maxTouchPoints === 'number' && navigator.maxTouchPoints > 1
  return (
    /iPhone|iPod|iPad|Android|webOS|BlackBerry|IEMobile|Opera Mini/i.test(ua) ||
    padOs
  )
}

function triggerNativeCaptureInput() {
  const input = nativeCaptureInputRef.value
  if (input) {
    input.value = ''
    input.click()
  }
}

const showHttpsCameraHint = computed(() => {
  if (typeof window === 'undefined') return false
  const { protocol, hostname } = window.location
  return protocol !== 'https:' && hostname !== 'localhost' && hostname !== '127.0.0.1'
})

const streamReady = computed(() => {
  const v = cameraVideoRef.value
  return !!(mediaStream.value && v && v.videoWidth > 0 && v.videoHeight > 0)
})

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

const isFallback = computed(() => result.value?.is_fallback === true)
const alertTitle = computed(() => (isFallback.value ? '舌诊识别服务暂时不可用' : '舌诊分析完成'))
const alertType = computed(() => (isFallback.value ? 'warning' : 'success'))
const alertDescription = computed(() => {
  if (isFallback.value) {
    return result.value?.feature || '识别服务暂时不可用'
  }
  const features = result.value?.features_list
  const featureText = Array.isArray(features) && features.length > 0
    ? features.join(', ')
    : (result.value?.feature || '未检测到明显特征')
  return `主特征：${featureText}`
})

const MAX_BYTES = 5 * 1024 * 1024

function validateImageFile(file) {
  const isJPGOrPNG = file.type === 'image/jpeg' || file.type === 'image/png'
  if (!isJPGOrPNG) {
    ElMessage.error('只能上传 JPG/PNG 文件!')
    return false
  }
  const isLt5M = file.size < MAX_BYTES
  if (!isLt5M) {
    ElMessage.error('上传图片大小不能超过 5MB!')
    return false
  }
  return true
}

/**
 * 统一入口：校验 jpg/png、5MB 后走上传（文件选择 / 网页拍照均调用此处）
 * @param {File} file
 */
async function handleImageFile(file) {
  if (!validateImageFile(file)) return

  loading.value = true
  result.value = null

  const formData = new FormData()
  formData.append('file', file)

  try {
    const res = await request.post('/constitution/tongue-diagnosis', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })

    if (res.code === 200 && res.data) {
      result.value = res.data
      emit('analysis-complete', res.data)
      if (res.data.is_fallback === true) {
        ElMessage.warning(res.data.feature || '识别服务暂时不可用')
      } else {
        ElMessage.success('舌诊分析成功')
      }
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

const beforeUpload = (file) => validateImageFile(file)

const uploadFile = async (options) => {
  await handleImageFile(options.file)
}

function stopMediaStream() {
  if (mediaStream.value) {
    mediaStream.value.getTracks().forEach((t) => t.stop())
    mediaStream.value = null
  }
  const v = cameraVideoRef.value
  if (v) v.srcObject = null
}

function handleGetUserMediaError(err) {
  const name = err?.name
  if (name === 'NotAllowedError' || name === 'PermissionDeniedError') {
    cameraError.value = '相机权限被拒绝。请在浏览器或系统设置中允许访问摄像头，或使用拖拽 / 点击上传。'
    ElMessage.error('无法使用摄像头：权限被拒绝')
  } else if (name === 'NotFoundError' || name === 'DevicesNotFoundError') {
    cameraError.value = '未检测到可用摄像头，请使用拖拽或点击上传。'
    ElMessage.error('未检测到摄像头')
  } else if (name === 'NotReadableError' || name === 'TrackStartError') {
    cameraError.value = '摄像头可能被其他应用占用，请关闭后重试。'
    ElMessage.error('摄像头不可用')
  } else if (name === 'OverconstrainedError' || name === 'ConstraintNotSatisfiedError') {
    cameraError.value = '无法满足当前相机参数，请使用拖拽或点击上传。'
    ElMessage.warning('相机参数不满足，请换种方式上传')
  } else {
    cameraError.value = err?.message ? `无法打开相机：${err.message}` : '无法打开相机，请使用文件上传。'
    ElMessage.error(cameraError.value)
  }
}

async function tryGetUserMedia(constraints) {
  return navigator.mediaDevices.getUserMedia(constraints)
}

async function startCameraStream() {
  cameraError.value = ''
  stopMediaStream()
  await nextTick()

  if (!navigator.mediaDevices?.getUserMedia) {
    cameraError.value = '当前浏览器不支持网页调起摄像头，请使用拖拽或点击上传。'
    return false
  }

  let stream = null
  try {
    stream = await tryGetUserMedia({ video: { facingMode: 'user' } })
  } catch (e1) {
    try {
      stream = await tryGetUserMedia({ video: { facingMode: 'environment' } })
    } catch (e2) {
      try {
        stream = await tryGetUserMedia({ video: true })
      } catch (e3) {
        handleGetUserMediaError(e3)
        return false
      }
    }
  }

  mediaStream.value = stream
  await nextTick()
  const video = cameraVideoRef.value
  if (video) {
    video.srcObject = stream
    try {
      await video.play()
    } catch {
      /* autoplay 策略下可能需用户手势，抽屉已算交互 */
    }
  }
  return true
}

watch(cameraInlineActive, async (open) => {
  if (!open) {
    stopMediaStream()
    if (capturedPreviewUrl.value) {
      URL.revokeObjectURL(capturedPreviewUrl.value)
      capturedPreviewUrl.value = null
    }
    capturedBlob.value = null
    cameraPhase.value = 'live'
    cameraStarting.value = false
    cameraError.value = ''
    return
  }
  cameraPhase.value = 'live'
  cameraError.value = ''
  capturedBlob.value = null
  if (capturedPreviewUrl.value) {
    URL.revokeObjectURL(capturedPreviewUrl.value)
    capturedPreviewUrl.value = null
  }
  cameraStarting.value = true
  await nextTick()
  await startCameraStream()
  cameraStarting.value = false
})

function openCameraInline() {
  if (!navigator.mediaDevices?.getUserMedia) {
    ElMessage.warning('当前环境不支持网页实时取景，请使用下方拍照上传（系统相机）或文件上传。')
    return
  }
  cameraInlineActive.value = true
}

function onPhotoUploadClick() {
  if (shouldPreferNativeCameraOnPhotoClick()) {
    triggerNativeCaptureInput()
    return
  }
  if (hasGetUserMedia()) {
    openCameraInline()
    return
  }
  triggerNativeCaptureInput()
}

function onNativeCaptureChange(ev) {
  const input = ev.target
  const file = input?.files?.[0]
  if (input) input.value = ''
  if (!file) return
  handleImageFile(file)
}

function closeCameraInline() {
  cameraInlineActive.value = false
}

function blobToJpegUnderLimit(videoEl, canvasEl) {
  const srcW = videoEl.videoWidth
  const srcH = videoEl.videoHeight
  if (!srcW || !srcH) return Promise.resolve(null)

  const canvas = canvasEl
  const ctx = canvas.getContext('2d')
  let quality = 0.92
  let scale = 1

  const tryOnce = () =>
    new Promise((resolve) => {
      const w = Math.max(1, Math.round(srcW * scale))
      const h = Math.max(1, Math.round(srcH * scale))
      canvas.width = w
      canvas.height = h
      ctx.drawImage(videoEl, 0, 0, w, h)
      canvas.toBlob((blob) => resolve(blob), 'image/jpeg', quality)
    })

  return (async () => {
    for (let i = 0; i < 28; i++) {
      const blob = await tryOnce()
      if (!blob) return null
      if (blob.size <= MAX_BYTES) return blob
      quality -= 0.06
      if (quality < 0.42) {
        quality = 0.88
        scale *= 0.86
      }
      if (scale < 0.12) break
    }
    return null
  })()
}

async function capturePhoto() {
  const video = cameraVideoRef.value
  const canvas = captureCanvasRef.value
  if (!video || !canvas || !mediaStream.value) {
    ElMessage.warning('相机未就绪')
    return
  }
  if (!video.videoWidth) {
    ElMessage.warning('画面尚未就绪，请稍候再试')
    return
  }

  const blob = await blobToJpegUnderLimit(video, canvas)
  if (!blob) {
    ElMessage.error('无法生成符合大小要求的照片，请重试或使用文件上传')
    return
  }
  if (blob.size > MAX_BYTES) {
    ElMessage.error('照片仍超过 5MB，请缩短距离或改用文件上传')
    return
  }

  stopMediaStream()
  capturedBlob.value = blob
  if (capturedPreviewUrl.value) URL.revokeObjectURL(capturedPreviewUrl.value)
  capturedPreviewUrl.value = URL.createObjectURL(blob)
  cameraPhase.value = 'preview'
}

async function retakePhoto() {
  if (capturedPreviewUrl.value) {
    URL.revokeObjectURL(capturedPreviewUrl.value)
    capturedPreviewUrl.value = null
  }
  capturedBlob.value = null
  cameraPhase.value = 'live'
  cameraStarting.value = true
  await nextTick()
  await startCameraStream()
  cameraStarting.value = false
}

async function confirmCapturedPhoto() {
  const blob = capturedBlob.value
  if (!blob) return
  const file = new File([blob], 'tongue.jpg', { type: 'image/jpeg' })
  if (capturedPreviewUrl.value) {
    URL.revokeObjectURL(capturedPreviewUrl.value)
    capturedPreviewUrl.value = null
  }
  capturedBlob.value = null
  cameraInlineActive.value = false
  await handleImageFile(file)
}

onBeforeUnmount(() => {
  stopMediaStream()
  if (capturedPreviewUrl.value) {
    URL.revokeObjectURL(capturedPreviewUrl.value)
    capturedPreviewUrl.value = null
  }
})
</script>

<style scoped>
.tongue-diagnosis-upload {
  width: 100%;
}

.capture-canvas {
  position: fixed;
  left: -9999px;
  top: 0;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.upload-extra-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.upload-extra-actions--below {
  justify-content: center;
  margin-top: 12px;
}

.native-capture-input {
  position: absolute;
  width: 0;
  height: 0;
  opacity: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  pointer-events: none;
}

.https-camera-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}

.tongue-upload-zone {
  width: 100%;
}

/* 与 el-upload 拖拽区视觉一致，内嵌拍照 */
.camera-inline-panel {
  width: 100%;
  box-sizing: border-box;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
  overflow: hidden;
  transition: border-color 0.2s ease;
}

.camera-inline-panel:hover {
  border-color: var(--el-color-primary);
}

.camera-inline-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
}

.camera-inline-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.camera-inline-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 12px 16px 16px;
  padding-bottom: calc(16px + env(safe-area-inset-bottom, 0));
  box-sizing: border-box;
}

.camera-stage {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 0 0 4px;
}

/* 竖屏 3:4 取景；内嵌于上传区时略缩小，避免占满一屏 */
.camera-viewport {
  position: relative;
  width: min(100%, calc(min(44dvh, 360px) * 0.75));
  height: min(44dvh, 360px);
  max-width: 100%;
  max-height: min(44dvh, 360px);
  margin: 0 auto;
  border-radius: 14px;
  overflow: hidden;
  background: #020617;
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.06),
    0 12px 40px rgba(15, 23, 42, 0.35);
}

.camera-viewport--preview {
  background: #0f172a;
}

.camera-video {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 42%;
}

.camera-preview {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #0f172a;
}

.camera-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 16px;
  font-size: 14px;
  line-height: 1.6;
  color: #e2e8f0;
  background: rgba(15, 23, 42, 0.72);
  z-index: 5;
}

.camera-overlay--solid {
  flex-direction: column;
  color: #b45309;
  background: #fff7ed;
}

.camera-loading {
  color: #e2e8f0;
}

.camera-error {
  margin: 0;
}

.camera-toolbar {
  flex-shrink: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  width: 100%;
  margin-top: 4px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.camera-toolbar :deep(.el-button--large.is-round) {
  min-width: 132px;
  padding-left: 28px;
  padding-right: 28px;
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
  max-height: 360px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  background: #000;
}

.result-image {
  display: block;
  max-width: 100%;
  max-height: 360px;
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

.fallback-hint {
  margin-top: 12px;
  color: #92400e;
  background: #fffbeb;
  border: 1px solid #fcd34d;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.5;
}
</style>
