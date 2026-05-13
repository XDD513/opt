import { reactive, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import message from '@/plugins/message'
import { useUserStore } from '@/stores/user'
import { submitTest as submitTestApi, checkTestByAppointment, getTestReport } from '@/api/constitution'
import { getRecipePromptStreamUrl } from '@/api/recipe'
import { getDictionaryByType } from '@/api/system'
import { getHealthProfile } from '@/api/health'
import { checkIsNewUser, refreshToken as refreshTokenApi } from '@/api/user'
import { isHealthProfileMandatoryComplete } from '@/utils/healthProfileMandatory'
import { getErrorMessage, handleApiError } from '@/utils/errorHandler'
import {
  getCurrentSeasonValueByMonth,
  stripMarkdownJsonFence,
  extractBalancedJsonObject,
  normalizeRecipeJson
} from '@/utils/parseAiHealthJson'
import { collectRecipeNamesFromHealthSuggestion } from '@/utils/constitutionRecipeExtract'

const createDefaultState = () => ({
  appointmentId: null,
  appointmentInfo: null,
  isNewUser: false,
  recommendedQuestions: [],
  selectedAnswers: {},
  loadingRecommendations: false,
  submitting: false,
  testResult: null,
  noQuestionsAvailable: false,
  isInitialized: false,
  isSubmitted: false, // 标记测试是否已提交
  tongueResult: null, // 舌诊分析主特征
  tongueFeatures: [], // 舌诊分析详细特征列表
  featuresDetail: [], // 舌诊详细结果(含置信度)
  mlScores: null, // 模型预测分数
  tongueImageUrl: null, // 舌诊图片 URL (OSS)
  tongueAnalysisRaw: null, // 舌诊原始结果（用于页面恢复显示）
  /** 舌诊后用户自述（提交时写入 AI 提示词） */
  userSelfDescription: '',
  streamingAiContent: '', // 实时生成的 AI 内容
  isAiLoading: false, // AI 生成加载状态
  /** 当前任务阶段：analysis | plans | recipe | null */
  streamPhase: null,
  /** 最近一次按提示词生成的药膳原始文本（不入库） */
  latestGeneratedRecipeText: '',
  /** 季节字典与当前季节 */
  seasonDictionary: [],
  currentSeasonValue: getCurrentSeasonValueByMonth(),
  /** 本次未入库的药膳列表（批量生成用，统一结构） */
  unpersistedRecipes: [],
  /** 生成任务运行中标记：用于防重复点击并支持跨页面 */
  runningJobs: {
    analysis: false,
    plans: false,
    recipe: false
  }
})

// 模块级单例：离开页面后保留草稿状态，返回页面可继续
const state = reactive(createDefaultState())

/** 深度分析/计划 SSE：用于在「重置测试」等场景主动关闭，避免后台悬挂 */
let activeConstitutionEs = null

/**
 * 与路由/组件无关的后台诊断流水线（离开体质页仍可继续跑完）
 */
const pipelineCtx = reactive({
  running: false,
  testId: null,
  abortRequested: false
})

let pipelinePollTimer = null
const clearPipelinePoll = () => {
  if (pipelinePollTimer) {
    clearInterval(pipelinePollTimer)
    pipelinePollTimer = null
  }
}

/**
 * 体质类型映射
 */
const CONSTITUTION_TYPE_MAP = {
  'PINGHE': '平和质',
  'QIXU': '气虚质',
  'YANGXU': '阳虚质',
  'YINXU': '阴虚质',
  'TANSHI': '痰湿质',
  'SHIRE': '湿热质',
  'XUEYU': '血瘀质',
  'QIYU': '气郁质',
  'TEBING': '特禀质'
}

/**
 * 使用体质测试功能的 Composable
 * @returns {Object} 状态和方法
 */
export function useConstitutionTest() {
  const route = useRoute()
  const router = useRouter()
  const userStore = useUserStore()
  const normalizeApiBase = (base) => {
    let b = String(base || '').trim()
    if (!b) return ''
    b = b.replace(/\/+$/, '')
    // 兼容配置为 ".../api" 或直接 "/api" 的场景，避免拼接出 "/api/api/..."
    if (b.endsWith('/api')) b = b.slice(0, -4)
    return b
  }

  // 防并发刷新：SSE 场景不走 axios 拦截器，需要这里兜底刷新并重连
  let sseRefreshing = false
  let sseRefreshWaiters = []
  const waitForSseRefresh = () =>
    new Promise((resolve, reject) => sseRefreshWaiters.push({ resolve, reject }))
  const notifySseRefresh = (err) => {
    const q = sseRefreshWaiters
    sseRefreshWaiters = []
    q.forEach(({ resolve, reject }) => (err ? reject(err) : resolve()))
  }

  const ensureValidTokenForSse = async () => {
    if (!userStore.refreshToken) {
      throw new Error('No refresh token')
    }
    if (sseRefreshing) {
      await waitForSseRefresh()
      return
    }
    sseRefreshing = true
    try {
      const res = await refreshTokenApi({ refreshToken: userStore.refreshToken })
      if (res?.code !== 200 || !res?.data?.token) {
        throw new Error(res?.message || 'Refresh failed')
      }
      userStore.setToken(res.data.token)
      userStore.setRefreshToken(res.data.refreshToken || '')
      notifySseRefresh(null)
    } catch (e) {
      notifySseRefresh(e)
      throw e
    } finally {
      sseRefreshing = false
    }
  }

  /**
   * 从路由获取appointmentId的辅助函数
   * @returns {string|null} 预约ID（字符串格式）
   */
  const getAppointmentIdFromRoute = () => {
    const id = route.query.appointmentId
    if (!id) {
      return null
    }
    // 直接使用字符串，避免大整数精度丢失
    const idStr = String(id).trim()
    if (!idStr) {
      return null
    }
    return idStr
  }

  /**
   * 初始化appointmentId
   */
  const initAppointmentId = () => {
    const id = getAppointmentIdFromRoute()
    if (id) {
      state.appointmentId = id
    }
  }

  /**
   * 验证预约ID格式
   * @param {string} id - 预约ID
   * @returns {boolean} 是否有效
   */
  const validateAppointmentId = (id) => {
    if (!id) return false
    const idStr = String(id).trim()
    return /^\d+$/.test(idStr)
  }

  /**
   * 加载预约信息
   * @returns {Promise<boolean>} 是否加载成功
   */
  const loadAppointmentInfo = async () => {
    if (!state.appointmentId) {
      return false
    }

    // Appointment module offline: keep appointmentId only as an optional compatibility field.
    if (!validateAppointmentId(state.appointmentId)) {
      ElMessage.error('Appointment id format error')
      state.appointmentId = null
      return false
    }

    state.appointmentInfo = null
    return true
  }

  /**
   * 获取新用户状态
   * @returns {Promise<void>}
   */
  const checkNewUserStatus = async () => {
    try {
      const res = await checkIsNewUser()
      if (res.code === 200) {
        state.isNewUser = res.data === true
      }
    } catch (error) {
      // 静默失败，默认认为不是新用户
      state.isNewUser = false
    }
  }

  /**
   * 自动查找适合进行体质测试的预约（优先"就诊中"，其次"待就诊"）
   * @returns {Promise<Object|null>} 预约信息
   */
  const findInProgressAppointment = async () => {
    // Appointment module offline: no auto appointment lookup.
    return null
  }

  /**
   * 选择选项
   * @param {number} questionId - 题目ID
   * @param {number} optionId - 选项ID
   */
  const selectOption = (questionId, optionId) => {
    state.selectedAnswers[questionId] = optionId
  }

  /**
   * 验证答案格式
   * @param {Object} answers - 答案对象
   * @returns {boolean} 是否有效
   */
  const validateAnswers = (answers) => {
    if (!answers || typeof answers !== 'object') {
      return false
    }
    // 检查每个答案是否为数字
    for (const [questionId, optionId] of Object.entries(answers)) {
      if (!/^\d+$/.test(String(questionId)) || !/^\d+$/.test(String(optionId))) {
        return false
      }
    }
    return true
  }

  /**
   * 当后端未返回 secondaryConstitution 时，基于 scores 推导次要体质（第二高分）
   * @param {Record<string, number>} scores
   * @param {string|null|undefined} primaryCode
   * @returns {{ code: string|null, score: number|null }}
   */
  const deriveSecondaryFromScores = (scores, primaryCode) => {
    if (!scores || typeof scores !== 'object') return { code: null, score: null }
    const primary = primaryCode ? String(primaryCode).toUpperCase() : null
    let bestCode = null
    let bestScore = null

    for (const [codeRaw, scoreRaw] of Object.entries(scores)) {
      const code = String(codeRaw).toUpperCase()
      if (primary && code === primary) continue
      const score = Number(scoreRaw)
      if (Number.isNaN(score)) continue
      if (bestScore === null || score > bestScore) {
        bestScore = score
        bestCode = code
      }
    }

    // 若全为 0 或无有效分数，则视为无明显次要体质
    if (!bestCode || bestScore == null || bestScore <= 0) {
      return { code: null, score: null }
    }
    return { code: bestCode, score: bestScore }
  }

  const isTongueAnalysisRecognized = (data) => {
    if (!data || data.is_fallback === true) return false
    if (Array.isArray(data.features_list) && data.features_list.length > 0) return true
    if (Array.isArray(data.features_detail) && data.features_detail.length > 0) return true
    const feature = String(data.feature || '').trim()
    return Boolean(feature) && feature !== '识别服务暂时不可用'
  }

  /**
   * 提交测试
   * @returns {Promise<boolean>} 是否提交成功
   */
  const submitTest = async () => {
    // 检查是否已提交
    if (state.isSubmitted) {
      ElMessage.warning('测试已提交，无法重复提交')
      return false
    }

    const userId = userStore.userInfo?.id
    if (userId) {
      try {
        const hpRes = await getHealthProfile(userId)
        if (hpRes.code === 200 && !isHealthProfileMandatoryComplete(hpRes.data)) {
          message.warning('请进行健康档案填写')
          return false
        }
      } catch {
        // 由后端再次校验
      }
    }

    if (state.tongueAnalysisRaw?.is_fallback === true) {
      message.warning('识别服务暂时不可用')
      return false
    }

    // 舌诊测试：必须是有效识别结果
    if (!isTongueAnalysisRecognized(state.tongueAnalysisRaw) || !state.tongueResult) {
      ElMessage.warning('请先完成舌诊分析')
      return false
    }

    // 新一轮提交：清理上一轮“药膳/流式内容”等缓存，避免提交后误显示“药膳列表已生成”
    state.latestGeneratedRecipeText = ''
    state.unpersistedRecipes = []
    state.streamingAiContent = ''
    state.isAiLoading = false
    state.streamPhase = null

    state.submitting = true
    try {
      // 4. 构建提交数据（问卷答案为空，仅依赖舌诊特征）
      // 后端解析 tongueResult 时期望包含 features_list 的 JSON 字符串
      const tongueResultJson = JSON.stringify({
        feature: state.tongueResult,
        features_list: state.tongueFeatures || [],
        features_detail: state.featuresDetail || [],
        ml_scores: state.mlScores || null,
        image_url: state.tongueImageUrl, // 包含 OSS 图片链接
        is_fallback: false
      })

      const submitData = {
        answers: {}, // 问卷部分留空
        appointmentId: state.appointmentId ? String(state.appointmentId).trim() : null,
        questionIds: [], // 题目列表为空
        tongueResult: tongueResultJson,
        tongueFeatures: state.tongueFeatures || [],
        userSelfDescription: (state.userSelfDescription || '').trim()
      }

      const res = await submitTestApi(submitData)

      if (res.code === 200 && res.data) {
        const primaryCode = res.data.primaryConstitution
        // 若后端未给 secondaryConstitution，则用 scores 推导第二高分体质
        const derivedSecondary = deriveSecondaryFromScores(res.data.scores || {}, primaryCode)
        const secondaryCode = res.data.secondaryConstitution || derivedSecondary.code
        state.testResult = {
          id: res.data.id,
          primaryConstitution: primaryCode,
          primaryConstitutionName:
            res.data.primaryConstitutionName ||
            CONSTITUTION_TYPE_MAP[String(primaryCode || '').toUpperCase()] ||
            primaryCode,
          primaryScore: res.data.scores?.[res.data.primaryConstitution] || 0,
          secondaryConstitution: secondaryCode,
          secondaryConstitutionName:
            res.data.secondaryConstitutionName ||
            (secondaryCode
              ? (CONSTITUTION_TYPE_MAP[String(secondaryCode).toUpperCase()] || secondaryCode)
              : null),
          secondaryScore: secondaryCode
            ? (res.data.scores?.[secondaryCode] ?? derivedSecondary.score ?? 0)
            : null,
          analysis: res.data.report || '',
          suggestions: res.data.healthSuggestion ? res.data.healthSuggestion.split('；') : []
        }

        // 标记为已提交
        state.isSubmitted = true

        ElMessage.success('智能体质辨识成功！')
        
        return true
      } else {
        const errorMsg = getErrorMessage(res, { defaultMessage: '生成报告失败' })
        if (errorMsg === '请进行健康档案填写') {
          message.warning('请进行健康档案填写')
        } else {
          ElMessage.error(errorMsg)
        }
        return false
      }
    } catch (error) {
      const errorMsg = getErrorMessage(error, { defaultMessage: '生成报告异常' })
      if (errorMsg === '请进行健康档案填写') {
        message.warning('请进行健康档案填写')
      } else {
        ElMessage.error(errorMsg)
      }
      return false
    } finally {
      state.submitting = false
    }
  }

  /**
   * 加载题目（已移除问卷逻辑，仅保留状态重置）
   * @returns {Promise<void>}
   */
  const loadQuestions = async () => {
    state.loadingRecommendations = false
    state.noQuestionsAvailable = false
    state.recommendedQuestions = [] // 强制为空
    state.selectedAnswers = {}
    state.testResult = null
    state.isSubmitted = false
    // 清理“上一轮生成结果”，避免新一轮提交后误显示“已生成药膳/计划”
    state.latestGeneratedRecipeText = ''
    state.unpersistedRecipes = []
    state.streamingAiContent = ''
    state.isAiLoading = false
    state.streamPhase = null
  }

  /**
   * 加载季节字典并设置当前季节值
   */
  const loadSeasonDictionary = async () => {
    try {
      const res = await getDictionaryByType('season')
      if (res?.code === 200 && Array.isArray(res.data)) {
        state.seasonDictionary = res.data
      } else {
        state.seasonDictionary = []
      }
    } catch {
      state.seasonDictionary = []
    } finally {
      // 无论接口是否可用，都以月份直接设定当前季节值
      state.currentSeasonValue = getCurrentSeasonValueByMonth()
    }
  }

  /**
   * 中止后台诊断流水线并关闭体质分析/计划的 SSE（重置测试或切换会话时用）
   */
  const abortBackgroundDiagnosisAndStreams = () => {
    pipelineCtx.abortRequested = true
    clearPipelinePoll()
    pipelineCtx.running = false
    pipelineCtx.testId = null
    try {
      if (activeConstitutionEs) {
        activeConstitutionEs.close()
      }
    } catch {
      // ignore
    }
    activeConstitutionEs = null
    state.runningJobs.analysis = false
    state.runningJobs.plans = false
    state.runningJobs.recipe = false
    state.isAiLoading = false
    state.streamPhase = null
    state.streamingAiContent = ''
  }

  /**
   * 重置表单
   */
  const resetForm = (options = {}) => {
    abortBackgroundDiagnosisAndStreams()
    const { keepTongue = false } = options || {}
    state.selectedAnswers = {}
    state.testResult = null
    state.isSubmitted = false
    state.userSelfDescription = ''
    state.streamingAiContent = ''
    state.isAiLoading = false
    state.streamPhase = null
    state.latestGeneratedRecipeText = ''
    state.unpersistedRecipes = []

    // 软重置：保留舌象上下文，不回到“第一步”
    if (!keepTongue) {
      state.tongueResult = null
      state.tongueFeatures = []
      state.featuresDetail = []
      state.mlScores = null
      state.tongueImageUrl = null
      state.tongueAnalysisRaw = null
    }
    loadQuestions()
  }

  /**
   * 报告中的 healthSuggestion 是否含有效深度分析（与 SmartConstitutionTest 判定一致）
   */
  const reportHasValidAnalysis = (hs) => {
    const s = String(hs || '')
    if (/"error"\s*:\s*"AI HTTP (401|403)/.test(s)) return false
    if (/Authentication Fails|invalid_request_error|api key.*invalid/i.test(s)) return false
    const m = s.match(/"analysis"\s*:\s*"([^"]+)"/)
    if (m && m[1] && m[1].length >= 40) return true
    return /体质深度分析/.test(s) && s.length >= 80
  }

  const reportHasValidPlans = (hs) => {
    const s = String(hs || '')
    if (/"error"\s*:\s*"AI HTTP (401|403)/.test(s)) return false
    if (/Authentication Fails|invalid_request_error|api key.*invalid/i.test(s)) return false
    return /"plans"\s*:/.test(s) || /"planType"\s*:/.test(s)
  }

  /**
   * 流式获取 AI 建议
   * @param {string|number} testId
   * @param {'analysis'|'plans'} phase - analysis：深度报告；plans：健康计划（需先完成 analysis）
   */
  const streamAiSuggestion = (testId, phase = 'analysis') => {
    if (!testId) return

    if (phase === 'analysis' && state.runningJobs.analysis) {
      message.titled.info({ title: '深度分析', message: '正在生成中，请稍候…' })
      return
    }
    if (phase === 'plans' && state.runningJobs.plans) {
      message.titled.info({ title: '健康计划', message: '正在生成中，请稍候…' })
      return
    }
    if (phase === 'analysis') state.runningJobs.analysis = true
    if (phase === 'plans') state.runningJobs.plans = true

    state.isAiLoading = true
    state.streamPhase = phase
    state.streamingAiContent = ''

    const baseUrl = import.meta.env.VITE_API_URL || ''
    const base = normalizeApiBase(baseUrl)
    const token = userStore.token || localStorage.getItem('token')
    const encToken = encodeURIComponent(token || '')
    const url = `${base || ''}/api/constitution/test/ai-suggestion/stream/${testId}?token=${encToken}&phase=${encodeURIComponent(phase)}`
    const eventSource = new EventSource(url)
    activeConstitutionEs = eventSource
    /** 正常收到 finish 后浏览器仍会触发 EventSource.onerror，禁止据此无限重连 */
    let sseFinished = false

    const cleanup = () => {
      state.isAiLoading = false
      state.streamPhase = null
      if (phase === 'analysis') state.runningJobs.analysis = false
      if (phase === 'plans') state.runningJobs.plans = false
      try {
        eventSource.close()
      } catch {
        // ignore
      }
      if (activeConstitutionEs === eventSource) {
        activeConstitutionEs = null
      }
    }

    eventSource.onopen = () => {
      state.isAiLoading = true
    }

    eventSource.onmessage = (event) => {
      if (event.data) {
        state.streamingAiContent += event.data
      }
    }

    eventSource.addEventListener('finish', async () => {
      sseFinished = true
      cleanup()
      setTimeout(async () => {
        try {
          if (!state.testResult || String(state.testResult?.id || '') !== String(testId)) {
            return
          }
          const res = await getTestReport(testId)
          if (res.code === 200 && res.data) {
            const primaryCode = res.data.primaryConstitution || state.testResult?.primaryConstitution
            const derivedSecondary = deriveSecondaryFromScores(res.data.scores || {}, primaryCode)
            const secondaryCode = res.data.secondaryConstitution || state.testResult?.secondaryConstitution || derivedSecondary.code
            state.testResult = {
              ...state.testResult,
              primaryConstitution: primaryCode,
              secondaryConstitution: secondaryCode,
              secondaryConstitutionName:
                res.data.secondaryConstitutionName ||
                (secondaryCode
                  ? (CONSTITUTION_TYPE_MAP[String(secondaryCode).toUpperCase()] || secondaryCode)
                  : state.testResult?.secondaryConstitutionName),
              secondaryScore: secondaryCode
                ? (res.data.scores?.[secondaryCode] ?? derivedSecondary.score ?? state.testResult?.secondaryScore ?? 0)
                : null,
              healthSuggestion: res.data.healthSuggestion,
              analysis: res.data.report || state.testResult.analysis,
              suggestions: res.data.healthSuggestion ? res.data.healthSuggestion.split('；') : state.testResult.suggestions
            }
            state.streamingAiContent = ''
            const hs = state.testResult?.healthSuggestion
            if (phase === 'analysis') {
              if (reportHasValidAnalysis(hs)) {
                message.titled.success({ title: '深度分析生成完成', message: '已完成体质深度分析，可继续生成健康计划。' })
              } else {
                message.titled.warning({
                  title: '深度分析未完成',
                  message: '未生成有效深度分析（常见原因：服务端 DeepSeek API Key 无效或未配置）。请修正配置后重新点击「开始诊断」。'
                })
              }
            } else if (phase === 'plans') {
              if (reportHasValidPlans(hs)) {
                message.titled.success({ title: '健康计划生成完成', message: '已生成健康计划与调养建议。' })
              } else {
                message.titled.warning({
                  title: '健康计划未完成',
                  message: '未生成有效健康计划。请确认深度分析已成功且 DeepSeek 配置正确后重试。'
                })
              }
            }
          }
        } catch (error) {
          console.error('拉取最终报告失败:', error)
          message.titled.error({ title: '生成结果拉取失败', message: error?.message || '请稍后重试' })
        }
      }, 500)
    })

    eventSource.onerror = async () => {
      if (sseFinished) return
      const buf = state.streamingAiContent || ''
      cleanup()
      if (pipelineCtx.abortRequested || String(state.testResult?.id || '') !== String(testId)) {
        return
      }
      if (buf.includes('AI HTTP 401') || buf.includes('AI HTTP 403')) {
        message.titled.error({
          title: phase === 'plans' ? '健康计划生成失败' : '深度分析生成失败',
          message: 'AI 上游鉴权失败（如 DeepSeek API Key）。请检查服务端配置后重试，无需反复刷新页面。'
        })
        return
      }
      try {
        await ensureValidTokenForSse()
        streamAiSuggestion(testId, phase)
      } catch {
        message.titled.error({ title: phase === 'plans' ? '健康计划生成失败' : '深度分析生成失败', message: '连接中断或鉴权失败，请稍后重试。' })
      }
    }

    return () => cleanup()
  }

  /**
   * 基于提示词的药膳流式生成（SSE）
   * 不入库，仅供前端展示/控制台输出
   */
  const streamRecipeByPrompt = (prompt) => {
    if (!prompt || !prompt.trim()) {
      ElMessage.warning('缺少提示词，无法生成药膳')
      return
    }
    if (state.runningJobs.recipe) {
      message.titled.info({ title: '药膳生成', message: '正在生成中，请稍候…' })
      return
    }
    state.runningJobs.recipe = true
    state.isAiLoading = true
    state.streamPhase = 'recipe'
    state.streamingAiContent = ''
    const url = getRecipePromptStreamUrl(prompt)
    const es = new EventSource(url)
    let recipeSseFinished = false
    const cleanup = () => { state.isAiLoading = false; state.streamPhase = null; state.runningJobs.recipe = false; es.close() }
    message.titled.info({ title: '药膳生成', message: '正在生成中，请稍候…' })
    es.onmessage = (e) => { if (e.data) state.streamingAiContent += e.data }
    es.addEventListener('finish', () => {
      recipeSseFinished = true
      try { state.latestGeneratedRecipeText = (state.streamingAiContent || '').trim() } catch (_) {}
      state.streamingAiContent = ''
      cleanup()
      message.titled.success({ title: '药膳生成完成', message: '已生成药膳建议。' })
    })
    es.onerror = async () => {
      if (recipeSseFinished) return
      const buf = state.streamingAiContent || ''
      cleanup()
      if (buf.includes('AI HTTP 401') || buf.includes('AI HTTP 403')) {
        message.titled.error({ title: '药膳生成失败', message: 'AI 上游鉴权失败，请检查服务端 DeepSeek 等配置。' })
        return
      }
      try {
        await ensureValidTokenForSse()
        streamRecipeByPrompt(prompt)
        return
      } catch {}
      message.titled.error({ title: '药膳生成失败', message: '连接中断或鉴权失败，请稍后重试。' })
    }
    return () => cleanup()
  }

  /**
   * 批量按菜名队列化流式生成药膳（不入库）
   * 顺序执行，避免并发带来的限流/打断
   */
  const streamRecipesBatch = async (names = []) => {
    if (!Array.isArray(names) || names.length === 0) {
      ElMessage.warning('没有可用的药膳名称')
      return
    }
    if (state.runningJobs.recipe) {
      message.titled.info({ title: '药膳列表生成', message: '正在生成中，请稍候…' })
      return
    }
    state.runningJobs.recipe = true
    // 去重并清空本次列表
    const queue = Array.from(new Set(names.filter(Boolean)))
    state.unpersistedRecipes = []
    for (let i = 0; i < queue.length; i++) {
      const name = queue[i]
      // 为单个菜名构建提示词：沿用 SmartConstitutionTest 的规则（季节、体质）
      const constitution = state.testResult?.primaryConstitutionName || ''
      const seasonValue = state.currentSeasonValue || ''
      const dict = Array.isArray(state.seasonDictionary) ? state.seasonDictionary : []
      const seasonItem = dict.find(d => (d.dictValue || d.dict_value) === seasonValue)
      const seasonLabel = seasonItem?.dictName || seasonItem?.dict_label || seasonValue
      const rotationHints = ['汤羹类优先', '粥饮类优先', '清炒炖煮类优先']
      const rotationHint = rotationHints[i % rotationHints.length]
      const ask = [
        `请基于中医体质“${constitution}”生成以下药膳的标准化JSON：${name}。`,
        `当前季节：${seasonLabel}（${seasonValue}）。请优先选用当季相宜食材、烹饪方法与禁忌。`,
        `本批次已生成药膳：${queue.slice(0, i).join('、') || '无'}。请避免与已生成菜名同义重复。`,
        `本条轮换偏好：${rotationHint}（用于降低跨次模板重复，可在满足体质适配前提下灵活处理）。`,
        '请返回字段：recipeName, constitutionType, season, category, difficulty, cookingTime, servings, ingredients[{name,amount,unit,note}], steps[string[]], efficacy, suitableSymptoms, contraindications, nutritionInfo{calorie,protein_g,fat_g,carb_g}, tips。',
        '字段不可省略，键名必须完全一致。constitutionType取值：PINGHE|QIXU|YANGXU|YINXU|TANSHI|SHIRE|XUEYU|QIYU|TEBING|ALL；season取值：SPRING|SUMMER|AUTUMN|WINTER|ALL；difficulty取值1-5。',
        '去重约束：recipeName 不能与本批次已生成药膳重名或同义；主料与上一道药膳不得完全相同；同一主料在本批次最多出现2次。',
        '内容约束：必须给出可执行烹饪步骤；禁忌需具体，不可只写“忌辛辣”等空泛词。',
        `个体化约束：efficacy 与 suitableSymptoms 必须明确说明为何匹配“${constitution}”体质与“${seasonLabel}”时令；contraindications 需给出明确风险人群或症状。`,
        '仅输出 JSON。'
      ].join(' ')

      ElMessage.info(`正在生成第 ${i + 1}/${queue.length} 道：${name}`)
      state.isAiLoading = true
      state.streamPhase = 'recipe'
      state.streamingAiContent = ''
      const url = getRecipePromptStreamUrl(ask)
      const es = new EventSource(url)
      let buf = ''
      es.onmessage = (e) => {
        if (e.data) {
          buf += e.data
          state.streamingAiContent = buf
        }
      }
      await new Promise((resolve) => {
        const cleanup = () => { try { es.close() } catch(_) {} }
        es.addEventListener('finish', () => {
          cleanup()
          try { state.latestGeneratedRecipeText = (buf || '').trim() } catch (_) {}
          try {
            const body = stripMarkdownJsonFence(buf || '')
            const jsonStr = extractBalancedJsonObject(body) || body
            let unified = null
            try { unified = normalizeRecipeJson(JSON.parse(jsonStr)) } catch {}
            if (unified) {
              state.unpersistedRecipes.push(unified)
              ElMessage.success(`已生成：${unified.name || name}`)
            } else {
              state.unpersistedRecipes.push({ name, raw: (buf || '').trim() })
              ElMessage.warning(`JSON 解析失败，已以原文形式保留：${name}`)
            }
          } catch (e) {
            state.unpersistedRecipes.push({ name, raw: (buf || '').trim() })
            ElMessage.warning(`解析异常，已以原文形式保留：${name}`)
          }
          resolve()
        })
        es.onerror = async () => {
          cleanup()
          try {
            await ensureValidTokenForSse()
            queue.splice(i, 0, name)
            ElMessage.warning(`令牌已刷新，正在重试：${name}`)
          } catch {
            state.unpersistedRecipes.push({ name, raw: (buf || '').trim() })
            ElMessage.error(`生成失败：${name}`)
          }
          resolve()
        }
      })
      state.streamingAiContent = ''
    }
    state.isAiLoading = false
    state.streamPhase = null
    state.runningJobs.recipe = false
    message.titled.success({ title: '药膳列表生成完成', message: `已完成 ${state.unpersistedRecipes?.length || 0} 道药膳生成。` })
  }

  /**
   * 离开页面后 SSE 已断开：清理“生成中”标记，避免返回后无法再次触发流式任务
   */
  const resetAiExecutionFlags = () => {
    state.runningJobs.analysis = false
    state.runningJobs.plans = false
    state.runningJobs.recipe = false
    state.isAiLoading = false
    state.streamPhase = null
    state.streamingAiContent = ''
  }

  /**
   * 从服务端拉取最新报告并合并到当前 testResult（用于返回页面后与会话内状态对齐）
   * @returns {Promise<boolean>}
   */
  const refreshTestResultFromServer = async () => {
    const testId = state.testResult?.id
    if (!testId) return false
    try {
      const res = await getTestReport(testId)
      if (res.code !== 200 || !res.data) return false
      const primaryCode = res.data.primaryConstitution || state.testResult?.primaryConstitution
      const derivedSecondary = deriveSecondaryFromScores(res.data.scores || {}, primaryCode)
      const secondaryCode = res.data.secondaryConstitution || state.testResult?.secondaryConstitution || derivedSecondary.code
      state.testResult = {
        ...state.testResult,
        primaryConstitution: primaryCode,
        primaryConstitutionName:
          res.data.primaryConstitutionName ||
          state.testResult?.primaryConstitutionName ||
          CONSTITUTION_TYPE_MAP[String(primaryCode || '').toUpperCase()] ||
          primaryCode,
        secondaryConstitution: secondaryCode,
        secondaryConstitutionName:
          res.data.secondaryConstitutionName ||
          (secondaryCode
            ? (CONSTITUTION_TYPE_MAP[String(secondaryCode).toUpperCase()] || secondaryCode)
            : state.testResult?.secondaryConstitutionName),
        secondaryScore: secondaryCode
          ? (res.data.scores?.[secondaryCode] ?? derivedSecondary.score ?? state.testResult?.secondaryScore ?? 0)
          : null,
        healthSuggestion: res.data.healthSuggestion,
        analysis: res.data.report || state.testResult?.analysis,
        suggestions: res.data.healthSuggestion
          ? res.data.healthSuggestion.split('；')
          : state.testResult?.suggestions
      }
      return true
    } catch {
      return false
    }
  }

  const healthTextHasAnalysis = (s) => {
    if (!s) return false
    const m = String(s).match(/"analysis"\s*:\s*"([^"]+)"/)
    if (m && m[1] && m[1].length >= 40) return true
    return /体质深度分析/.test(String(s)) && String(s).length >= 80
  }

  const healthTextHasPlans = (s) => {
    if (!s) return false
    return /"plans"\s*:/.test(String(s)) || /"planType"\s*:/.test(String(s))
  }

  const schedulePipelineReportPoll = () => {
    clearPipelinePoll()
    pipelinePollTimer = setInterval(() => {
      if (!pipelineCtx.running || pipelineCtx.abortRequested) {
        clearPipelinePoll()
        return
      }
      void refreshTestResultFromServer()
    }, 2500)
  }

  const stillSamePipelineTest = (startedId) =>
    String(state.testResult?.id || '') === String(startedId || '')

  const waitPipelinePhaseDone = async (timeoutMs = 180000) => {
    const t0 = Date.now()
    while (state.isAiLoading) {
      if (pipelineCtx.abortRequested || !stillSamePipelineTest(pipelineCtx.testId)) return false
      if (Date.now() - t0 > timeoutMs) return false
      await new Promise((r) => setTimeout(r, 200))
    }
    return true
  }

  const waitPipelineUntil = async (predicate, timeoutMs = 90000) => {
    const t0 = Date.now()
    while (!predicate()) {
      if (pipelineCtx.abortRequested || !stillSamePipelineTest(pipelineCtx.testId)) return false
      if (Date.now() - t0 > timeoutMs) return false
      await refreshTestResultFromServer()
      await new Promise((r) => setTimeout(r, 400))
    }
    return true
  }

  /**
   * 启动与页面无关的全量诊断流水线（分析→计划→药膳），换页后仍继续执行
   */
  const runBackgroundDiagnosisPipeline = () => {
    const testId = state.testResult?.id
    if (!testId) return
    if (pipelineCtx.running) {
      message.titled.info({ title: '体质诊断', message: '诊断流程已在运行（含后台），请稍候…' })
      return
    }
    if (state.isAiLoading && (state.runningJobs.analysis || state.runningJobs.plans)) {
      message.titled.info({ title: '体质诊断', message: 'AI 正在输出，请稍候…' })
      return
    }

    if (state.runningJobs.recipe) {
      message.titled.info({ title: '体质诊断', message: '药膳生成尚未结束，请稍后再启动全流程' })
      return
    }

    pipelineCtx.abortRequested = false
    pipelineCtx.running = true
    pipelineCtx.testId = testId
    schedulePipelineReportPoll()

    const startedId = testId
    const run = async () => {
      try {
        await refreshTestResultFromServer()
        const id = String(startedId)
        const hs = () => state.testResult?.healthSuggestion || ''

        if (!healthTextHasAnalysis(hs())) {
          message.titled.info({ title: '体质诊断', message: '第1步：后台生成深度分析（离开本页也会继续）' })
          streamAiSuggestion(id, 'analysis')
          await waitPipelinePhaseDone(180000)
          await waitPipelineUntil(
            () => healthTextHasAnalysis(state.testResult?.healthSuggestion || ''),
            90000
          )
          await refreshTestResultFromServer()
          if (pipelineCtx.abortRequested || !stillSamePipelineTest(startedId)) return
          if (!healthTextHasAnalysis(hs())) {
            message.titled.warning({
              title: '深度分析',
              message: '暂未从服务器同步到分析结果，轮询会继续；您可稍后回到本页查看。'
            })
          }
        }

        if (pipelineCtx.abortRequested || !stillSamePipelineTest(startedId)) return

        if (!healthTextHasPlans(hs())) {
          if (!healthTextHasAnalysis(hs())) {
            message.titled.warning({ title: '健康计划', message: '缺少深度分析，已暂停后台计划生成' })
            return
          }
          message.titled.info({ title: '体质诊断', message: '第2步：后台生成健康计划' })
          streamAiSuggestion(id, 'plans')
          await waitPipelinePhaseDone(180000)
          await waitPipelineUntil(
            () => healthTextHasPlans(state.testResult?.healthSuggestion || ''),
            90000
          )
          await refreshTestResultFromServer()
          if (pipelineCtx.abortRequested || !stillSamePipelineTest(startedId)) return
        }

        if (pipelineCtx.abortRequested || !stillSamePipelineTest(startedId)) return

        const recipeDone =
          Array.isArray(state.unpersistedRecipes) && state.unpersistedRecipes.length > 0
        if (!recipeDone) {
          if (!healthTextHasPlans(hs())) {
            message.titled.warning({ title: '药膳列表', message: '缺少健康计划，已跳过后台药膳步骤' })
            return
          }
          message.titled.info({ title: '体质诊断', message: '第3步：后台生成药膳列表' })
          const names = collectRecipeNamesFromHealthSuggestion(hs())
          if (names.length === 0) {
            message.info('DIET 计划未提供可用药膳名')
          } else {
            await streamRecipesBatch(names)
          }
        }

        await refreshTestResultFromServer()
        if (stillSamePipelineTest(startedId)) {
          message.titled.success({ title: '体质诊断', message: '诊断流程已执行完成' })
        }
      } catch (e) {
        console.error(e)
        message.titled.error({ title: '体质诊断', message: e?.message || '后台诊断流程异常' })
      } finally {
        clearPipelinePoll()
        pipelineCtx.running = false
        pipelineCtx.testId = null
      }
    }
    void run()
  }

  return {
    state,
    CONSTITUTION_TYPE_MAP,
    initAppointmentId,
    loadAppointmentInfo,
    loadSeasonDictionary,
    checkNewUserStatus,
    findInProgressAppointment,
    selectOption,
    submitTest,
    resetForm,
    loadQuestions,
    router,
    streamAiSuggestion,
    streamRecipeByPrompt
    ,
    streamRecipesBatch,
    refreshTestResultFromServer,
    resetAiExecutionFlags,
    pipelineCtx,
    runBackgroundDiagnosisPipeline,
    abortBackgroundDiagnosisAndStreams
  }
}

