import { reactive, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import notify from '@/plugins/notify'
import { useUserStore } from '@/stores/user'
import { submitTest as submitTestApi, checkTestByAppointment, getTestReport } from '@/api/constitution'
import { getRecipePromptStreamUrl } from '@/api/recipe'
import { getDictionaryByType } from '@/api/system'
import { checkIsNewUser, refreshToken as refreshTokenApi } from '@/api/user'
import { getErrorMessage, handleApiError } from '@/utils/errorHandler'
import {
  getCurrentSeasonValueByMonth,
  stripMarkdownJsonFence,
  extractBalancedJsonObject,
  normalizeRecipeJson
} from '@/utils/parseAiHealthJson'

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

    // 舌诊测试：必须有舌诊结果
    if (!state.tongueResult) {
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
        image_url: state.tongueImageUrl // 包含 OSS 图片链接
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
        ElMessage.error(errorMsg)
        return false
      }
    } catch (error) {
      const errorMsg = getErrorMessage(error, { defaultMessage: '生成报告异常' })
      ElMessage.error(errorMsg)
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
   * 重置表单
   */
  const resetForm = (options = {}) => {
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
   * 流式获取 AI 建议
   * @param {string|number} testId
   * @param {'analysis'|'plans'} phase - analysis：深度报告；plans：健康计划（需先完成 analysis）
   */
  const streamAiSuggestion = (testId, phase = 'analysis') => {
    if (!testId) return

    if (phase === 'analysis' && state.runningJobs.analysis) {
      notify.info({ title: '深度分析', message: '正在生成中，请稍候…' })
      return
    }
    if (phase === 'plans' && state.runningJobs.plans) {
      notify.info({ title: '健康计划', message: '正在生成中，请稍候…' })
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

    const cleanup = () => {
      state.isAiLoading = false
      state.streamPhase = null
      if (phase === 'analysis') state.runningJobs.analysis = false
      if (phase === 'plans') state.runningJobs.plans = false
      eventSource.close()
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
      cleanup()
      setTimeout(async () => {
        try {
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
          }
          if (phase === 'analysis') {
            notify.success({ title: '深度分析生成完成', message: '已完成体质深度分析，可继续生成健康计划。' })
          } else if (phase === 'plans') {
            notify.success({ title: '健康计划生成完成', message: '已生成健康计划与调养建议。' })
          }
        } catch (error) {
          console.error('拉取最终报告失败:', error)
          notify.error({ title: '生成结果拉取失败', message: error?.message || '请稍后重试' })
        }
      }, 500)
    })

    eventSource.onerror = async () => {
      cleanup()
      try {
        await ensureValidTokenForSse()
        streamAiSuggestion(testId, phase)
      } catch {
        // ignore
        notify.error({ title: phase === 'plans' ? '健康计划生成失败' : '深度分析生成失败', message: '连接中断或鉴权失败，请稍后重试。' })
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
      notify.info({ title: '药膳生成', message: '正在生成中，请稍候…' })
      return
    }
    state.runningJobs.recipe = true
    state.isAiLoading = true
    state.streamPhase = 'recipe'
    state.streamingAiContent = ''
    const url = getRecipePromptStreamUrl(prompt)
    const es = new EventSource(url)
    const cleanup = () => { state.isAiLoading = false; state.streamPhase = null; state.runningJobs.recipe = false; es.close() }
    notify.info({ title: '药膳生成', message: '正在生成中，请稍候…' })
    es.onmessage = (e) => { if (e.data) state.streamingAiContent += e.data }
    es.addEventListener('finish', () => {
      try { state.latestGeneratedRecipeText = (state.streamingAiContent || '').trim() } catch (_) {}
      state.streamingAiContent = ''
      cleanup()
      notify.success({ title: '药膳生成完成', message: '已生成药膳建议。' })
    })
    es.onerror = async () => {
      cleanup()
      try {
        await ensureValidTokenForSse()
        streamRecipeByPrompt(prompt)
        return
      } catch {}
      notify.error({ title: '药膳生成失败', message: '连接中断或鉴权失败，请稍后重试。' })
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
      notify.info({ title: '药膳列表生成', message: '正在生成中，请稍候…' })
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
      const ask = [
        `请基于中医体质“${constitution}”生成以下药膳的标准化JSON：${name}。`,
        `当前季节：${seasonLabel}（${seasonValue}）。请优先选用当季相宜食材、烹饪方法与禁忌。`,
        '请返回字段：recipeName, constitutionType, season, category, difficulty, cookingTime, servings, ingredients[{name,amount,unit,note}], steps[string[]], efficacy, suitableSymptoms, contraindications, nutritionInfo{calorie,protein_g,fat_g,carb_g}, tips。',
        '字段不可省略，键名必须完全一致。constitutionType取值：PINGHE|QIXU|YANGXU|YINXU|TANSHI|SHIRE|XUEYU|QIYU|TEBING|ALL；season取值：SPRING|SUMMER|AUTUMN|WINTER|ALL；difficulty取值1-5。',
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
    notify.success({ title: '药膳列表生成完成', message: `已完成 ${state.unpersistedRecipes?.length || 0} 道药膳生成。` })
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
    streamRecipesBatch
  }
}

