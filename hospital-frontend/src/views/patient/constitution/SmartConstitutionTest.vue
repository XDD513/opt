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
                  点击“开始诊断”后将自动依次生成：深度分析、健康计划、药膳列表。
                </div>
              </div>
              <div class="controls-right">
                <el-button
                  type="primary"
                  size="default"
                  :loading="state.isAiLoading || isDiagnosisRunning"
                  :disabled="!state.testResult?.id || state.isAiLoading || isDiagnosisRunning"
                  @click="handleStartDiagnosis"
                >
                  {{ isDiagnosisRunning ? '诊断进行中…' : '开始诊断' }}
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

const route = useRoute()
const router = useRouter()
const uploadKey = ref(0)
const batchRecipesSaved = ref(false)
const isDiagnosisRunning = ref(false)

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
  streamRecipesBatch
} = useConstitutionTest()

/**
 * 处理舌诊分析结果
 */
const handleTongueAnalysis = (data) => {
  // data 包含 feature, features_list, image_url 等
  // 为了显示提交按钮，我们需要确保 tongueResult 有值。
  if (data) {
    state.tongueAnalysisRaw = data
    state.tongueResult = data.feature || 'AnalysisCompleted'
    state.tongueFeatures = data.features_list || []
    state.tongueImageUrl = data.image_url || null
    state.featuresDetail = data.features_detail || []
    state.mlScores = data.ml_scores || null
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

const waitForPhaseFinished = async (timeoutMs = 120000) => {
  const startedAt = Date.now()
  while (state.isAiLoading) {
    if (Date.now() - startedAt > timeoutMs) return false
    await new Promise((resolve) => setTimeout(resolve, 200))
  }
  return true
}

const waitForCondition = async (checker, timeoutMs = 120000) => {
  const startedAt = Date.now()
  while (!checker()) {
    if (Date.now() - startedAt > timeoutMs) return false
    await new Promise((resolve) => setTimeout(resolve, 200))
  }
  return true
}

/**
 * 提取“体质深度分析”文本用于提示词上下文
 * 兼容 JSON 与半结构化文本
 */
const getDeepAnalysisForPrompt = () => {
  const raw = String(state.testResult?.healthSuggestion || '').trim()
  if (!raw) return ''
  try {
    const m = raw.match(/"analysis"\s*:\s*"([^"]+)"/)
    if (m && m[1]) return m[1]
  } catch {}
  const idx = raw.indexOf('体质深度分析')
  if (idx >= 0) {
    const sub = raw.slice(idx)
    const breakers = ['总体原则', '饮食', '起居', '穴位', 'plans', 'planType', '健康计划']
    let cut = sub.length
    for (const b of breakers) {
      const p = sub.indexOf(b)
      if (p > 0) cut = Math.min(cut, p)
    }
    return sub.slice(0, Math.min(cut, 300)).replace(/^体质深度分析[:：]?\s*/, '')
  }
  return ''
}

// 从 DIET 计划的 targetContent 抽取疑似药膳名（仅 targetContent，不使用 description）
const extractRecipesFromText = (raw) => {
  if (!raw) return []
  let text = String(raw)
    .replace(/(目标|早餐|午餐|晚餐|加餐|全天|建议|注意)[:：]/g, ' ')
    .replace(/[()（）]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  const hits = []
  // 规则1：典型药膳结尾关键词（粥/汤/羹/饮/茶）
  const hardRegex = /[\u4e00-\u9fa5·]{2,30}(粥|汤|羹|饮|茶)/g
  let m
  while ((m = hardRegex.exec(text)) !== null) {
    hits.push(m[0].trim())
  }
  const pieces = text.split(/[，、。；;,.]/).map(s => s.trim()).filter(Boolean)
  const KEY = /(粥|汤|羹|饮|茶|药膳)/
  for (const p of pieces) {
    if (KEY.test(p)) {
      let t = p.replace(/^(如|或者|或|搭配|加入|建议|宜|可选|例如)[:：]?/g, '').trim()
      const cut = t.match(/^[\u4e00-\u9fa5·]{2,30}(粥|汤|羹|饮|茶)/)
      if (cut) t = cut[0]
      if (t.length >= 2) hits.push(t)
    }
  }
  // 规则2：常见家常菜式（炒/炖/煮/蒸/焖/烩/拌），例如“韭菜炒鸡蛋”“生姜炖鸡肉”“清炒山药”
  const cookRegex = /[\u4e00-\u9fa5·]{1,12}(清炒|炒|炖|煮|蒸|焖|烩|拌)[\u4e00-\u9fa5·]{1,12}/g
  while ((m = cookRegex.exec(text)) !== null) {
    hits.push(m[0].trim())
  }
  // 规则3：带“配/搭配”后跟菜名片段时的抓取
  const pairRegex = /(配|搭配)[\u4e00-\u9fa5·]{2,16}/g
  while ((m = pairRegex.exec(text)) !== null) {
    const cand = m[0].replace(/^(配|搭配)/, '').trim()
    if (cand && cand.length >= 2) hits.push(cand)
  }
  return Array.from(new Set(hits))
}

const buildRecipePrompt = (names) => {
  const constitution = state.testResult?.primaryConstitutionName || ''
  const list = Array.isArray(names) ? names.slice(0, 5).join('、') : ''
  const seasonValue = state.currentSeasonValue || ''
  // 在字典中查找中文名；若没有，退回英文值
  const dict = Array.isArray(state.seasonDictionary) ? state.seasonDictionary : []
  const seasonItem = dict.find(d => (d.dictValue || d.dict_value) === seasonValue)
  const seasonLabel = seasonItem?.dictName || seasonItem?.dict_label || seasonValue
  const ask = [
    `请基于中医体质“${constitution}”生成以下药膳的标准化JSON：${list}。`,
    `当前季节：${seasonLabel}（${seasonValue}）。请优先选用当季相宜食材、烹饪方法与禁忌。`,
    '请返回字段：recipeName, constitutionType, season, category, difficulty, cookingTime, servings, ingredients[{name,amount,unit,note}], steps[string[]], efficacy, suitableSymptoms, contraindications, nutritionInfo{calorie,protein_g,fat_g,carb_g}, tips。',
    '字段不可省略，键名必须完全一致。constitutionType取值：PINGHE|QIXU|YANGXU|YINXU|TANSHI|SHIRE|XUEYU|QIYU|TEBING|ALL；season取值：SPRING|SUMMER|AUTUMN|WINTER|ALL；difficulty取值1-5。',
    '仅输出 JSON。'
  ].join(' ')
  return ask
}

// 生成药膳：按提示词调用异步任务
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
  // 新一轮生成时重置“批量已保存”标记
  batchRecipesSaved.value = false
  const raw = state.testResult?.healthSuggestion || ''
  const parsed = parseAiHealthSuggestion(String(raw))
  const plans = parsed?.plans || []
  const dietPlans = plans.filter(p => (p.planType || p.type) === 'DIET')
  const names = []
  for (const p of dietPlans) {
    const src = p?.targetContent || ''
    names.push(...extractRecipesFromText(src))
  }
  const recipeNames = Array.from(new Set(names))
  if (recipeNames.length === 0) {
    message.info('DIET 计划未提供可用药膳名')
    return
  }
  // 批量逐条生成，统一汇总展示（不入库）
  return streamRecipesBatch(recipeNames)
  scrollToResultIfNeeded()
}

const handleStartDiagnosis = async () => {
  if (!state.testResult?.id) {
    message.info('请先完成舌象采集并提交测试')
    return
  }
  if (state.isAiLoading || isDiagnosisRunning.value) {
    message.info('诊断流程正在执行，请稍候')
    return
  }
  isDiagnosisRunning.value = true
  try {
    if (!hasAiAnalysis.value) {
      message.info('第1步：生成深度分析')
      streamAiSuggestion(state.testResult.id, 'analysis')
      scrollToResultIfNeeded()
      const phaseDone = await waitForPhaseFinished()
      const analysisReady = phaseDone
        ? await waitForCondition(() => hasAiAnalysis.value, 30000)
        : false
      if (!analysisReady) {
        message.warning('深度分析生成未完成，请稍后重试')
        return
      }
    }

    if (!hasAiPlans.value) {
      message.info('第2步：生成健康计划')
      streamAiSuggestion(state.testResult.id, 'plans')
      scrollToResultIfNeeded()
      const phaseDone = await waitForPhaseFinished()
      const plansReady = phaseDone
        ? await waitForCondition(() => hasAiPlans.value, 30000)
        : false
      if (!plansReady) {
        message.warning('健康计划生成未完成，请稍后重试')
        return
      }
    }

    if (!hasAiRecipe.value) {
      message.info('第3步：生成药膳列表')
      // 复用现有药膳生成逻辑，不新增接口
      await handleGenerateRecipe()
    }
    message.success('诊断流程执行完成')
  } finally {
    isDiagnosisRunning.value = false
  }
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
    await loadQuestions()
  }
  state.isInitialized = true
  if (state && state.latestGeneratedRecipe == null) {
    state.latestGeneratedRecipe = null
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
