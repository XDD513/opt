<template>
  <div class="smart-constitution-test">
    <div class="container">
      <el-row :gutter="20" class="workspace-layout">
        <!-- 左：上下文区（稳定） -->
        <el-col :xs="24" :lg="6">
          <el-card class="card context-card" shadow="never">
            <template #header>
              <div class="card-header">
                <span>舌象采集与信息补充</span>
              </div>
            </template>

            <div class="panel">
              <!-- 通过 key 强制重置上传组件内部缓存，确保“重置测试”后回到初始状态 -->
              <TongueDiagnosisUpload
                :key="uploadKey"
                :initial-result="state.tongueAnalysisRaw"
                @analysis-complete="handleTongueAnalysis"
              />

              <div v-if="state.tongueResult" class="self-desc-section">
                <div class="section-title">补充您的情况（选填）</div>
                <el-input
                  v-model="state.userSelfDescription"
                  type="textarea"
                  :rows="4"
                  maxlength="2000"
                  show-word-limit
                  :disabled="state.isSubmitted"
                  placeholder="例如：最近经常熬夜，口干，怕冷…"
                />
                <div v-if="state.isSubmitted" class="self-desc-locked-hint">
                  已提交测试：如需修改，请先点击「重置测试」重新提交。
                </div>
              </div>

              <div v-if="state.tongueResult" class="submit-action">
                <div class="submit-actions">
                  <el-button
                    type="primary"
                    size="large"
                    icon="Document"
                    :loading="state.submitting"
                    :disabled="state.isSubmitted"
                    @click="handleSubmit"
                  >
                    {{ state.isSubmitted ? '已提交' : '提交测试' }}
                  </el-button>
                  <el-button size="large" icon="Refresh" @click="handleReset">
                    重置测试
                  </el-button>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>

        <!-- 右：主舞台（核心） -->
        <el-col :xs="24" :lg="18">
          <el-card class="card main-stage-card" shadow="never">
            <template #header>
              <div class="card-header">
                <span>体质诊断工作台</span>
                <div class="stage-header-right">
                  <el-tag v-if="stageTag.type" :type="stageTag.type" effect="light">{{ stageTag.text }}</el-tag>
                </div>
              </div>
            </template>

            <!-- 控制条（顶层动作） -->
            <div class="stage-controls">
              <div class="controls-left">
                <div class="controls-title">生成与操作</div>
                <div class="controls-hint">
                  点击「开始诊断」后将在后台依次生成：深度分析、健康计划、药膳列表；离开本页也会继续，返回后可流式查看已生成内容。
                </div>
              </div>
              <div class="controls-right">
                <el-button
                  type="primary"
                  size="default"
                  :loading="state.isAiLoading || pipelineCtx.running"
                  :disabled="!state.testResult?.id || state.isAiLoading || pipelineCtx.running"
                  @click="handleStartDiagnosis"
                >
                  {{ pipelineCtx.running ? '后台诊断进行中…' : '开始诊断' }}
                </el-button>
              </div>
            </div>

            <div v-if="state.isSubmitted && state.testResult" class="main-stage-content">
              <TestResult
                :test-result="state.testResult"
                :streaming-content="state.streamingAiContent"
                :is-ai-loading="state.isAiLoading"
                :stream-phase="state.streamPhase"
                :analysis-ready="hasAiAnalysis"
                :plans-ready="hasAiPlans"
                :recipe-ready="hasAiRecipe"
                :latest-recipe="state.latestGeneratedRecipe"
                :latest-recipe-text="state.latestGeneratedRecipeText"
                :unpersisted-recipes="state.unpersistedRecipes"
                :batch-recipes-saved="batchRecipesSaved"
                :reveal-replay-nonce="revealReplayNonce"
                mode="full"
                :show-header="false"
                embedded
                variant="workspace"
                actions-placement="top"
                @view-history="handleViewHistory"
                @recipe-saved="handleRecipeSaved"
                @batch-recipes-saved="handleBatchRecipesSaved"
              />
            </div>
            <el-empty v-else class="empty-main-stage" description="请先完成舌象采集与提交" />
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { useConstitutionTest } from '@/composables/useConstitutionTest'
import TestResult from '@/components/patient/constitution/TestResult.vue'
import TongueDiagnosisUpload from '@/components/patient/constitution/TongueDiagnosisUpload.vue'
import message from '@/plugins/message'
import { parseAiHealthSuggestion } from '@/utils/parseAiHealthJson'
import { collectRecipeNamesFromHealthSuggestion } from '@/utils/constitutionRecipeExtract'

const BG_DIAG_SESSION_KEY = 'hospital_diagnosis_bg_pipeline'

const route = useRoute()
const router = useRouter()
const uploadKey = ref(0)
const batchRecipesSaved = ref(false)
const revealReplayNonce = ref(0)

// 使用 composable
const {
  state,
  initAppointmentId,
  loadSeasonDictionary,
  loadQuestions,
  submitTest,
  resetForm,
  streamAiSuggestion,
  streamRecipeByPrompt,
  streamRecipesBatch,
  refreshTestResultFromServer,
  resetAiExecutionFlags,
  pipelineCtx,
  runBackgroundDiagnosisPipeline
} = useConstitutionTest()

/**
 * 处理舌诊分析结果
 */
const handleTongueAnalysis = (data) => {
  // data 包含 feature, features_list, image_url 等
  if (data) {
    state.tongueAnalysisRaw = data
    if (data.is_fallback === true) {
      state.tongueResult = null
      state.tongueFeatures = []
      state.tongueImageUrl = data.image_url || null
      state.featuresDetail = []
      state.mlScores = null
      sessionStorage.setItem('hospital_tongue_unavailable', '1')
      return
    }
    state.tongueResult = data.feature || 'AnalysisCompleted'
    state.tongueFeatures = data.features_list || []
    state.tongueImageUrl = data.image_url || null
    state.featuresDetail = data.features_detail || []
    state.mlScores = data.ml_scores || null
    sessionStorage.removeItem('hospital_tongue_unavailable')
  }
}

/**
 * 处理提交
 */
const handleSubmit = async () => {
  const success = await submitTest()
  if (success && state.testResult?.id) {
    // 仅当结果区域不在视口内时才滚动，避免“提交后页面抖动/莫名下滑”
    await scrollToResultIfNeeded()
  }
}

const runAnalysisStream = () => {
  if (!state.testResult?.id) {
    message.info('请先完成舌象采集并提交测试')
    return
  }
  if (hasAiAnalysis.value) {
    message.info('深度分析已生成，无需重复操作')
    return
  }
  streamAiSuggestion(state.testResult.id, 'analysis')
  scrollToResultIfNeeded()
}

const runPlansStream = () => {
  if (!state.testResult?.id) {
    message.info('请先完成舌象采集并提交测试')
    return
  }
  if (!hasAiAnalysis.value) {
    message.warning('请先生成深度分析，再生成健康计划')
    return
  }
  if (hasAiPlans.value) {
    message.info('健康计划已生成，无需重复操作')
    return
  }
  streamAiSuggestion(state.testResult.id, 'plans')
  scrollToResultIfNeeded()
}

// 生成药膳：按提示词调用异步任务（工作台内单独触发时使用）
const handleGenerateRecipe = () => {
  if (!state.testResult?.id) {
    message.info('请先完成舌象采集并提交测试')
    return
  }
  if (!hasAiAnalysis.value) {
    message.warning('请先生成深度分析')
    return
  }
  if (!hasAiPlans.value) {
    message.warning('请先生成健康计划，再生成药膳列表')
    return
  }
  batchRecipesSaved.value = false
  const recipeNames = collectRecipeNamesFromHealthSuggestion(state.testResult?.healthSuggestion || '')
  if (recipeNames.length === 0) {
    message.info('DIET 计划未提供可用药膳名')
    return
  }
  const p = streamRecipesBatch(recipeNames)
  scrollToResultIfNeeded()
  return p
}

const handleStartDiagnosis = () => {
  if (!state.testResult?.id) {
    message.info('请先完成舌象采集并提交测试')
    return
  }
  if (state.isAiLoading || pipelineCtx.running) {
    message.info('诊断流程正在执行（含后台），请稍候')
    return
  }
  runBackgroundDiagnosisPipeline()
}

/**
 * 主舞台控制条的状态（轻量启发式：尽量不引入额外依赖，不影响原逻辑）
 */
const hasAiAnalysis = computed(() => {
  const s = state.testResult?.healthSuggestion
  if (!s) return false
  // analysis 字段一般为较长文本；用长度阈值避免误判
  const m = String(s).match(/"analysis"\s*:\s*"([^"]+)"/)
  if (m && m[1] && m[1].length >= 40) return true
  // 兼容非严格 JSON / 输出中包含 “体质深度分析” 文案
  return /体质深度分析/.test(String(s)) && String(s).length >= 80
})

const hasAiPlans = computed(() => {
  const s = state.testResult?.healthSuggestion
  if (!s) return false
  return /"plans"\s*:/.test(String(s)) || /"planType"\s*:/.test(String(s))
})

// 是否已经生成药膳列表：依赖批量未入库药膳数组是否有内容
const hasAiRecipe = computed(() => {
  return Array.isArray(state.unpersistedRecipes) && state.unpersistedRecipes.length > 0
})

const stageTag = computed(() => {
  if (!state.testResult?.id) return { type: 'info', text: '等待提交' }
  if (pipelineCtx.running) {
    return { type: 'primary', text: '后台诊断进行中…' }
  }
  if (state.isAiLoading) {
    return { type: 'primary', text: state.streamPhase === 'plans' ? 'AI 正在生成计划…' : 'AI 正在生成分析…' }
  }
  if (hasAiPlans.value) return { type: 'success', text: '计划已生成' }
  if (hasAiAnalysis.value) return { type: 'success', text: '分析已生成' }
  return { type: 'warning', text: '待生成分析' }
})

async function scrollToResultIfNeeded() {
  await nextTick()
  const resultEl = document.querySelector('.test-result')
  if (!resultEl) return
  const rect = resultEl.getBoundingClientRect()
  const topOut = rect.top < 0
  const bottomOut = rect.bottom > (window.innerHeight || 0)
  if (topOut || bottomOut) {
    resultEl.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

/**
 * 处理重置
 */
const handleReset = () => {
  // 硬重置：全部清空并回到第一步
  resetForm()
  batchRecipesSaved.value = false
  uploadKey.value += 1
}

/**
 * 处理查看历史
 */
const handleViewHistory = () => {
  router.push('/patient/constitution/history')
}

/**
 * 处理返回我的预约
 */
const handleGoToAppointments = () => {
  router.push('/patient/my-appointments')
}

// 监听路由参数变化
watch(() => route.query.appointmentId, (newAppointmentId) => {
  if (newAppointmentId) {
    const idStr = String(newAppointmentId).trim()
    if (idStr && idStr !== state.appointmentId) {
      const hasDraft = Boolean(state.tongueResult || state.testResult || state.userSelfDescription)
      state.appointmentId = idStr
      state.appointmentInfo = null
      // 如果已经初始化完成，重新加载题目
      if (!hasDraft && state.isInitialized && !state.loadingRecommendations) {
        loadQuestions()
      }
    }
  } else {
    // 离开页面/跳转到不带 appointmentId 的路由时，不清空 appointmentId，
    // 否则返回页面会被当成“切换预约”而触发重置，导致调养信息丢失。
  }
})

const clearUnsubmittedDraft = () => {
  if (!state.isSubmitted) {
    resetForm()
    uploadKey.value += 1
  }
}

onBeforeRouteLeave(() => {
  clearUnsubmittedDraft()
})

onBeforeUnmount(() => {
  clearUnsubmittedDraft()
})

// 初始化
onMounted(async () => {
  initAppointmentId()
  await loadSeasonDictionary()
  if (!state.isSubmitted) {
    resetForm()
    uploadKey.value += 1
  } else {
    // 已提交会话：切勿调用 loadQuestions()，否则会清空 testResult / isSubmitted，导致“又要重新提交”
    resetAiExecutionFlags()
    await refreshTestResultFromServer()
  }
  state.isInitialized = true
  if (state && state.latestGeneratedRecipe == null) {
    state.latestGeneratedRecipe = null
  }
  if (sessionStorage.getItem('hospital_tongue_unavailable') === '1') {
    message.warning('识别服务暂时不可用')
    sessionStorage.removeItem('hospital_tongue_unavailable')
  }
})

/**
 * 药膳保存成功：更新“最近一次已入库的药膳”以驱动卡片展示
 */
const handleRecipeSaved = (recipe) => {
  try {
    state.latestGeneratedRecipe = recipe || null
  } catch (_) {
    state.latestGeneratedRecipe = null
  }
}

/**
 * 批量药膳保存成功后：保留展示内容，仅更新保存状态
 */
const handleBatchRecipesSaved = ({ lastSaved } = {}) => {
  batchRecipesSaved.value = true
  if (lastSaved) state.latestGeneratedRecipe = lastSaved
}
</script>

<style scoped lang="scss">
.smart-constitution-test {
  min-height: 100vh;
  background: #f8fafc;
  
  .container {
    /* 用满内容区宽度，避免大屏出现大片空白 */
    max-width: none;
    width: 100%;
    margin: 0;
    padding: 18px 18px 28px;
  }
}

.card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.06);
  
  .card-header {
    padding: 16px;
    border-bottom: 1px solid #e2e8f0;
    background: linear-gradient(135deg, #f0f9ff 0%, #ffffff 100%);
    font-weight: 700;
    font-size: 16px;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
}

.workspace-layout {
  padding-top: 8px;
}

.panel {
  padding: 8px 8px 4px;
}

.section-title {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 8px;
}

.section-desc {
  font-size: 14px;
  color: #909399;
  margin-bottom: 16px;
}

.self-desc-section {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #f0f2f5;
}

.self-desc-locked-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.5;
}

.submit-action {
  margin-top: 28px;
  padding-bottom: 4px;
}

.submit-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
}

.context-card {
  position: sticky;
  top: 20px;
  align-self: flex-start;
}

.main-stage-card {
  min-height: 640px;
}

.stage-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.stage-controls {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid #eef2f7;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.08) 0%, rgba(255, 255, 255, 1) 55%);
  margin: 8px 8px 14px;
}

.controls-left {
  min-width: 0;
}

.controls-title {
  font-weight: 800;
  color: #0f172a;
  font-size: 14px;
  margin-bottom: 4px;
}

.controls-hint {
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
}

.controls-right {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
  flex-shrink: 0;
}

.main-stage-content {
  padding: 0 8px 10px;
}

.empty-main-stage {
  padding: 32px 0;
}

/* 收紧 card 默认 body padding，让信息密度更“工作台” */
.context-card,
.main-stage-card {
  :deep(.el-card__body) {
    padding: 12px;
  }
}

/* 大屏下稍微增加左右留白，避免贴边 */
@media (min-width: 1400px) {
  .smart-constitution-test {
    .container {
      padding-left: 24px;
      padding-right: 24px;
    }
  }
}

/* 中小屏优化：当页面变窄时自动重排控件并取消左侧吸顶，避免遮挡与溢出 */
@media (max-width: 1200px) {
  .context-card {
    position: static; /* 取消吸顶，便于纵向滚动 */
    top: auto;
  }
  .stage-controls {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }
  .controls-right {
    justify-content: flex-start;
    gap: 8px;
  }
  .main-stage-card {
    min-height: auto; /* 允许内容自适应高度，减少空白 */
  }
}

/* 超小屏（手机）优化：进一步压缩间距与字号，保证功能可达 */
@media (max-width: 768px) {
  .controls-title {
    font-size: 13px;
  }
  .controls-hint {
    font-size: 11px;
  }
  .stage-controls {
    padding: 10px 12px;
    margin: 6px 6px 12px;
  }
  .submit-actions {
    gap: 8px;
  }
}
</style>
