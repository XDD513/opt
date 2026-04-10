<template>
  <div class="test-history-detail">
    <el-page-header @back="goBack" content="测试历史详情" />

    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="10" animated />
    </div>

    <div v-else-if="result" class="result-content">
      <el-card class="constitution-card">
        <template #header>
          <div class="card-header">
            <span>您的体质类型</span>
            <el-tag type="info">{{ result.testDate }}</el-tag>
          </div>
        </template>

        <el-row :gutter="20">
          <el-col :span="result.secondaryConstitution ? 12 : 24">
            <div class="constitution-type primary">
              <div class="type-badge">主要体质</div>
              <div class="type-icon" :style="{ backgroundColor: result.primaryConstitutionDetail?.color || '#409EFF' }">
                {{ result.primaryConstitutionDetail?.icon || result.primaryConstitutionName?.charAt(0) }}
              </div>
              <h2>{{ result.primaryConstitutionName }}</h2>
              <div class="type-score">
                得分: {{ result.scores?.[result.primaryConstitution]?.toFixed(1) || '0.0' }}
              </div>
              <p class="type-description">{{ result.primaryConstitutionDetail?.description }}</p>
            </div>
          </el-col>

          <el-col :span="12" v-if="result.secondaryConstitution">
            <div class="constitution-type secondary">
              <div class="type-badge secondary-badge">次要体质</div>
              <div class="type-icon" :style="{ backgroundColor: result.secondaryConstitutionDetail?.color || '#67C23A' }">
                {{ result.secondaryConstitutionDetail?.icon || result.secondaryConstitutionName?.charAt(0) }}
              </div>
              <h2>{{ result.secondaryConstitutionName }}</h2>
              <div class="type-score">
                得分: {{ result.scores?.[result.secondaryConstitution]?.toFixed(1) || '0.0' }}
              </div>
              <p class="type-description">{{ result.secondaryConstitutionDetail?.description }}</p>
            </div>
          </el-col>
        </el-row>
      </el-card>

      <el-card class="score-card">
        <template #header>
          <div class="card-header">
            <span>体质得分分布雷达图</span>
            <el-tooltip content="中心为均衡状态，向外延伸代表该体质倾向性越强">
              <el-icon><InfoFilled /></el-icon>
            </el-tooltip>
          </div>
        </template>
        <div ref="chartRef" class="chart-container"></div>
      </el-card>

      <!-- 复制 SmartConstitutionTest 的展示结构（不复用组件），保证样式一致 -->
      <div class="test-result embedded variant-workspace mode-full">
        <div class="embedded-content">
          <div class="analysis-section workspace-section">
            <div v-if="parsedAiSuggestion" class="workspace-cards">
              <!-- 深度分析 + 总体原则 -->
              <div v-if="parsedAiSuggestion.analysis" class="ws-card">
                <div class="ws-card-title">体质深度分析</div>
                <div class="ws-card-body">
                  <p class="analysis-paragraph">{{ parsedAiSuggestion.analysis }}</p>
                </div>
              </div>
              <div v-if="parsedAiSuggestion.summary" class="ws-card">
                <div class="ws-card-title">总体原则</div>
                <div class="ws-card-body">
                  <p class="analysis-paragraph">{{ parsedAiSuggestion.summary }}</p>
                </div>
              </div>

              <!-- 饮食宜忌 -->
              <div v-if="parsedAiSuggestion.diet" class="ws-card">
                <div class="ws-card-title">饮食宜忌</div>
                <div class="ws-card-body">
                  <div class="diet-content">
                    <div v-if="parsedAiSuggestion.diet.recommend && parsedAiSuggestion.diet.recommend.length">
                      <span class="label suitable">宜：</span>
                      <span
                        v-for="(item, i) in parsedAiSuggestion.diet.recommend"
                        :key="'rec_ws_'+i"
                        class="diet-item"
                      >{{ item }}<span v-if="i < parsedAiSuggestion.diet.recommend.length-1">、</span></span>
                    </div>
                    <div v-if="parsedAiSuggestion.diet.avoid && parsedAiSuggestion.diet.avoid.length" style="margin-top: 8px;">
                      <span class="label avoid">忌：</span>
                      <span
                        v-for="(item, i) in parsedAiSuggestion.diet.avoid"
                        :key="'avoid_ws_'+i"
                        class="diet-item"
                      >{{ item }}<span v-if="i < parsedAiSuggestion.diet.avoid.length-1">、</span></span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 起居调养 -->
              <div v-if="parsedAiSuggestion.lifestyle && parsedAiSuggestion.lifestyle.length" class="ws-card">
                <div class="ws-card-title">起居调养</div>
                <div class="ws-card-body">
                  <ul class="simple-list">
                    <li v-for="(item, i) in parsedAiSuggestion.lifestyle" :key="'life_ws_'+i">{{ item }}</li>
                  </ul>
                </div>
              </div>

              <!-- 穴位保健：整行 -->
              <div v-if="displayAcupoints.length" class="ws-card ws-card-wide">
                <div class="ws-card-title">穴位保健</div>
                <div class="ws-card-body">
                  <div class="ws-acupoint-grid">
                    <div v-for="(pt, i) in displayAcupoints" :key="'ap_ws_'+i" class="ws-acupoint-card">
                      <div class="ws-acupoint-header">
                        <div class="ws-acupoint-badge">穴位</div>
                        <div class="ws-acupoint-name">{{ pt.name }}</div>
                      </div>
                      <div class="ws-acupoint-meta">
                        <div v-if="pt.location" class="ws-acupoint-row">
                          <span class="k">定位</span>
                          <span class="v">{{ pt.location }}</span>
                        </div>
                        <div class="ws-acupoint-row">
                          <span class="k">方法/功效</span>
                          <span class="v">{{ pt.effect }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 药膳列表（按 testId 拉取） -->
              <div v-if="testRecipes.length" class="ws-card ws-card-wide">
                <div class="ws-card-title">药膳列表</div>
                <div class="ws-card-body">
                  <div class="ws-recipe-list">
                    <div
                      v-for="r in testRecipes"
                      :key="String(r.id || r.recipeName)"
                      class="ws-recipe-item"
                    >
                      <div class="ws-recipe-item-header">
                        <div class="ws-recipe-item-title">【{{ r.recipeName }}】</div>
                        <el-tag size="small" type="success" effect="plain">{{ r.category || '药膳' }}</el-tag>
                      </div>
                      <div v-if="formatIngredientsBrief(r).length" class="ws-recipe-line">
                        <strong>食材：</strong>
                        <span>
                          <span v-for="(it, i) in formatIngredientsBrief(r)" :key="String(r.id || r.recipeName) + '_ing_' + i">
                            {{ it }}<span v-if="i < formatIngredientsBrief(r).length - 1">、</span>
                          </span>
                        </span>
                      </div>

                      <div v-if="formatSteps(r).length" class="ws-recipe-line">
                        <strong>做法：</strong>
                        <ol class="ws-recipe-steps">
                          <li v-for="(s, i) in formatSteps(r)" :key="String(r.id || r.recipeName) + '_st_' + i">{{ s }}</li>
                        </ol>
                      </div>

                      <div v-if="r.efficacy" class="ws-recipe-line">
                        <strong>功效：</strong>{{ r.efficacy }}
                      </div>

                      <div v-if="formatContraindicationsText(r)" class="ws-recipe-line">
                        <strong>禁忌：</strong>{{ formatContraindicationsText(r) }}
                      </div>

                      <div class="ws-recipe-item-meta">
                        <span><strong>体质：</strong>{{ r.constitutionType || 'ALL' }}</span>
                        <span class="sep"> </span>
                        <span><strong>季节：</strong>{{ r.season || 'ALL' }}</span>
                        <span class="sep"> </span>
                        <span><strong>用时/份量：</strong>{{ (r.cookingTime || 30) + '分钟 / ' + (r.servings || 2) + '人份' }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 药膳建议（文本保存） -->
              <div v-if="recipeText" class="ws-card ws-card-wide">
                <div class="ws-card-title" style="display:flex;justify-content:space-between;align-items:center;">
                  <span>药膳建议（本次批量）</span>
                  <el-button
                    type="success"
                    size="small"
                    :loading="savingRecipes"
                    @click="handleSaveRecipes"
                  >
                    一键保存药膳
                  </el-button>
                </div>
                <div class="ws-card-body">
                  <div style="white-space: pre-wrap;">{{ recipeText }}</div>
                </div>
              </div>

              <!-- 健康计划 -->
              <div v-if="parsedAiSuggestion.plans && parsedAiSuggestion.plans.length" class="ws-card ws-card-wide">
                <div class="ws-card-title" style="display:flex;justify-content:space-between;align-items:center;">
                  <span>健康计划</span>
                  <el-button
                    v-if="!plansSaved"
                    type="success"
                    size="small"
                    :loading="savingPlans"
                    @click="handleSavePlans"
                  >
                    一键保存计划
                  </el-button>
                  <el-tag v-else type="success">计划已保存</el-tag>
                </div>
                <div class="ws-card-body">
                  <div class="plans-list">
                    <div v-for="(plan, i) in parsedAiSuggestion.plans" :key="'hplan'+i" class="plan-card">
                      <div class="plan-card-header">
                        <span class="plan-card-title">{{ plan.planName || plan.name }}</span>
                        <el-tag
                          size="small"
                          :type="plan.planType === 'DIET' ? 'success' : (plan.planType === 'EXERCISE' ? 'warning' : '')"
                          effect="light"
                        >
                          {{ plan.planType === 'DIET' ? '饮食' : (plan.planType === 'EXERCISE' ? '运动' : (plan.planType === 'ACUPOINT' ? '穴位' : '起居')) }}计划
                        </el-tag>
                      </div>
                      <div class="plan-card-desc">{{ plan.description }}</div>
                      <div class="plan-card-info">
                        <div class="info-row target">
                          <span class="label">目标:</span>
                          <span class="value">{{ plan.targetContent || '无特定目标' }}</span>
                        </div>
                        <div class="info-row date">
                          <span class="value">{{ getPlanDateRange(plan.duration) }}</span>
                        </div>
                        <div class="info-row freq">
                          <span class="value">{{ formatFrequency(plan.frequency) }}</span>
                        </div>
                      </div>
                      <div class="plan-card-progress">
                        <el-progress :percentage="0" :stroke-width="6" :show-text="false" color="#409eff" />
                        <div class="progress-labels">
                          <span>0%</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div v-else class="workspace-cards-empty">
              <el-empty description="暂无结构化内容" />
            </div>
          </div>
        </div>
      </div>

    </div>

    <el-empty v-else description="未找到测试结果" />
  </div>
</template>

<script setup>
import message from '@/plugins/message'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { InfoFilled } from '@element-plus/icons-vue'
import { getTestReport } from '@/api/constitution'
import { createHealthPlan } from '@/api/health'
import { favoriteRecipe, saveRecipeFromSuggestion, getRecipesByTestId } from '@/api/recipe'
import { CONSTITUTION_TYPE_MAP } from '@/utils/constitution'
import { parseAiHealthSuggestion, normalizeAcupointsList } from '@/utils/parseAiHealthJson'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const result = ref(null)
const chartRef = ref(null)
const savingPlans = ref(false)
const plansSaved = ref(false)
const savingRecipes = ref(false)
const recipesSaved = ref(false)
let chartInstance = null
const testRecipes = ref([])

// 将后端入库的 ingredients(JSON字符串) 解析为简短展示文本：name（amount unit）
const formatIngredientsBrief = (recipe) => {
  try {
    const raw = recipe?.ingredients
    if (!raw) return []
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!Array.isArray(arr)) return []
    return arr
      .map(it => {
        const n = String(it?.name || '').trim()
        if (!n) return ''
        const a = it?.amount != null ? String(it.amount).trim() : ''
        const u = it?.unit != null ? String(it.unit).trim() : ''
        // 统一为：名称（数量 单位），例如：羊肉（300 g）
        const suffix = (a || u) ? `（${[a, u].filter(Boolean).join(' ')}）` : ''
        return n + suffix
      })
      .filter(Boolean)
      .slice(0, 8)
  } catch {
    return []
  }
}

// 将后端 steps(JSON字符串/数组) 转为展示用的字符串数组
const formatSteps = (recipe) => {
  try {
    const raw = recipe?.steps
    if (!raw) return []
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (Array.isArray(arr)) {
      return arr.map(s => String(s || '').trim()).filter(Boolean)
    }
    return []
  } catch {
    // 如果不是合法 JSON，则按行/分号/顿号做一个弱拆分兜底
    try {
      const raw = recipe?.steps
      if (typeof raw === 'string') {
        return raw.split(/\n+|；|。/).map(s => String(s).trim()).filter(Boolean).slice(0, 15)
      }
      return []
    } catch {
      return []
    }
  }
}

// 将后端 contraindications(JSON字符串/数组/普通字符串) 统一为文本展示
const formatContraindicationsText = (recipe) => {
  try {
    const raw = recipe?.contraindications ?? recipe?.contraindicationsText
    if (!raw) return ''
    if (typeof raw === 'string') {
      const s = raw.trim()
      if (!s) return ''
      // 如果是 JSON 数组形态，解析后拼接
      if (s.startsWith('[')) {
        const arr = JSON.parse(s)
        if (Array.isArray(arr)) return arr.map(x => String(x || '').trim()).filter(Boolean).join('、')
      }
      return s
    }
    if (Array.isArray(raw)) {
      return raw.map(x => String(x || '').trim()).filter(Boolean).join('、')
    }
    return String(raw)
  } catch {
    return ''
  }
}

const parsedAiSuggestion = computed(() => {
  const text = String(result.value?.healthSuggestion || '').trim()
  if (!text) return null
  return parseAiHealthSuggestion(text)
})

const displayAcupoints = computed(() => normalizeAcupointsList(parsedAiSuggestion.value?.acupoints))

const recipeText = computed(() => {
  const parsed = parsedAiSuggestion.value
  if (!parsed) return ''
  if (parsed.recipeText) return String(parsed.recipeText)
  if (Array.isArray(parsed.recipes) && parsed.recipes.length) {
    return parsed.recipes.map((r) => (typeof r === 'string' ? r : JSON.stringify(r))).join('\n\n')
  }
  return ''
})

const formatFrequency = (freq) => {
  const map = { DAILY: '每天', WEEKLY: '每周', MONTHLY: '每月' }
  if (!freq) return '每天'
  if (/[\u4e00-\u9fa5]/.test(freq)) return freq
  return map[String(freq).toUpperCase()] || String(freq)
}

const getPlanDateRange = (duration) => {
  const start = new Date()
  const end = new Date()
  end.setDate(end.getDate() + (duration || 30))
  const formatDate = (d) => {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  }
  return `${formatDate(start)} ~ ${formatDate(end)}`
}

const handleSavePlans = async () => {
  if (!parsedAiSuggestion.value?.plans?.length || !result.value?.id) return
  savingPlans.value = true
  try {
    let successCount = 0
    for (const plan of parsedAiSuggestion.value.plans) {
      const now = new Date()
      const startDate = now.toISOString().split('T')[0]
      const endDate = new Date(now.getTime() + (Number(plan.duration || 30) * 24 * 60 * 60 * 1000)).toISOString().split('T')[0]
      const payload = {
        testId: result.value.id,
        planName: plan.planName || plan.name || '健康计划',
        planType: plan.planType || plan.type || 'SLEEP',
        description: plan.description || '',
        targetContent: plan.targetContent || plan.description || '',
        frequency: plan.frequency || 'DAILY',
        startDate,
        endDate,
        targetCount: Number(plan.duration || 30),
        status: 1
      }
      const res = await createHealthPlan(payload)
      if (res.code === 200) successCount++
    }
    if (successCount > 0) {
      plansSaved.value = true
      message.success(`成功保存 ${successCount} 个健康计划`)
    } else {
      message.warning('未保存成功，请稍后重试')
    }
  } catch (error) {
    message.error('保存健康计划失败')
  } finally {
    savingPlans.value = false
  }
}

const handleSaveRecipes = async () => {
  if (!recipeText.value || !result.value?.id) return
  savingRecipes.value = true
  try {
    // 1) 简单提取菜名，若本次已存在相同菜名则提示并跳过入库
    const title = parseRecipeTitle(recipeText.value)
    if (title && Array.isArray(testRecipes.value) && testRecipes.value.some(r => String(r?.recipeName || '').trim() === title)) {
      recipesSaved.value = true
      message.info('该药膳已存在，无需重复保存')
      return
    }

    // 2) 调用后端入库
    const res = await saveRecipeFromSuggestion({
      testId: result.value.id,
      text: recipeText.value
    })
    if (res.code === 200) {
      recipesSaved.value = true
      // 保存后自动收藏，确保在“药膳列表/我的药膳收藏”都可见
      let favorited = false
      try {
        if (res?.data?.id != null) {
          await favoriteRecipe(res.data.id)
          favorited = true
        }
      } catch (_) {}
      message.success(favorited ? '药膳建议保存成功（已自动收藏）' : '药膳建议保存成功')
      // 3) 刷新“本次生成的药膳”列表，用于后续再次点击时能正确判断已存在
      try {
        const rr = await getRecipesByTestId(result.value.id)
        if (rr?.code === 200) {
          testRecipes.value = Array.isArray(rr.data) ? rr.data : []
          // 若后端未直接返回 id，这里尝试按标题匹配并补一次收藏
          if (!favorited) {
            const title2 = parseRecipeTitle(recipeText.value)
            const matched = title2 ? testRecipes.value.find(r => String(r?.recipeName || '').trim() == String(title2).trim()) : null
            const id2 = matched?.id
            if (id2 != null) {
              try { await favoriteRecipe(id2) } catch (_) {}
            }
          }
        }
      } catch (_) {}
    } else {
      message.warning(res.message || '药膳建议保存失败')
    }
  } catch (error) {
    message.error('药膳建议保存失败')
  } finally {
    savingRecipes.value = false
  }
}

// 基于后端相同规则的轻量级标题抽取：优先匹配【...】或行首短标题
const parseRecipeTitle = (text) => {
  try {
    const s = String(text || '').trim()
    if (!s) return ''
    const m = s.match(/【?([\u4e00-\u9fa5A-Za-z0-9_\-]{2,20})】?/)
    return m ? m[1] : ''
  } catch {
    return ''
  }
}

const loadDetail = async () => {
  try {
    loading.value = true
    const id = route.params.id
    if (!id) return
    const res = await getTestReport(id)
    if (res.code === 200) {
      result.value = res.data
      // 按 testId 拉取“本次生成的药膳”
      try {
        const rr = await getRecipesByTestId(result.value.id)
        if (rr?.code === 200) {
          testRecipes.value = Array.isArray(rr.data) ? rr.data : []
        }
      } catch (_) {}
      setTimeout(initChart, 80)
    }
  } catch (error) {
    message.error('加载历史详情失败')
  } finally {
    loading.value = false
  }
}

const initChart = () => {
  if (!chartRef.value || !result.value) return
  if (chartInstance) chartInstance.dispose()
  chartInstance = echarts.init(chartRef.value)

  const codes = Object.keys(CONSTITUTION_TYPE_MAP)
  const indicator = codes.map((code) => ({ name: CONSTITUTION_TYPE_MAP[code], max: 100 }))
  const values = codes.map((code) => Number(result.value?.scores?.[code] || 0))

  chartInstance.setOption({
    tooltip: { trigger: 'item' },
    radar: { indicator, radius: '70%' },
    series: [{
      type: 'radar',
      data: [{ value: values, name: '体质得分分布' }],
      areaStyle: { color: 'rgba(64, 158, 255, 0.25)' },
      lineStyle: { color: '#409EFF', width: 2 }
    }]
  })

  window.removeEventListener('resize', handleResize)
  window.addEventListener('resize', handleResize)
}

const handleResize = () => {
  chartInstance?.resize()
}

const goBack = () => router.back()

onMounted(loadDetail)
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) chartInstance.dispose()
})
</script>

<style scoped>
.test-history-detail { padding: 20px; }
.el-page-header { margin-bottom: 20px; }
.loading-container { padding: 40px; }
.result-content { max-width: 1320px; margin: 0 auto; }
.result-content .el-card { margin-bottom: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; gap: 10px; }

.constitution-type {
  text-align: center;
  padding: 30px 20px;
  border-radius: 8px;
  background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%);
}
.constitution-type.primary { border: 2px solid #409EFF; }
.constitution-type.secondary { border: 2px solid #67C23A; }
.type-badge {
  display: inline-block;
  padding: 4px 12px;
  background: #409EFF;
  color: #fff;
  border-radius: 12px;
  font-size: 12px;
  margin-bottom: 15px;
}
.secondary-badge { background: #67C23A; }
.type-icon {
  width: 80px;
  height: 80px;
  line-height: 80px;
  margin: 0 auto 15px;
  border-radius: 50%;
  color: #fff;
  font-size: 32px;
  font-weight: bold;
}
.constitution-type h2 { margin: 10px 0; font-size: 24px; color: #303133; }
.type-score { font-size: 18px; color: #409EFF; font-weight: bold; margin: 10px 0; }
.type-description { color: #606266; line-height: 1.8; margin-top: 15px; }

.chart-container { height: 420px; }

/* ===== 从 components/patient/constitution/TestResult.vue 迁移的“workspace”样式（确保与 SmartConstitutionTest 一致） ===== */
.test-result.embedded { margin-top: 0; }
.embedded-content { padding: 0; }

.test-result.variant-workspace .workspace-section { margin-top: 12px; }

.test-result.variant-workspace .workspace-cards {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
@media (max-width: 992px) {
  .test-result.variant-workspace .workspace-cards { grid-template-columns: 1fr; }
}

.test-result.variant-workspace .ws-card {
  background: #ffffff;
  border: 1px solid #eef2f7;
  border-radius: 12px;
  padding: 14px;
}
.test-result.variant-workspace .ws-card-wide { grid-column: 1 / -1; }

.test-result.variant-workspace .ws-card-title {
  font-weight: 800;
  color: #0f172a;
  font-size: 13px;
  margin-bottom: 10px;
}

.test-result.variant-workspace .ws-card-body {
  color: #334155;
  font-size: 13px;
  line-height: 1.85;
}

.analysis-paragraph { white-space: pre-wrap; }

.diet-content {
  background: #fdf6ec;
  padding: 12px;
  border-radius: 6px;
}

.diet-content .label {
  font-weight: bold;
  margin-right: 4px;
}
.diet-content .label.suitable { color: #67C23A; }
.diet-content .label.avoid { color: #F56C6C; }

.diet-content .diet-item {
  color: #606266;
  font-size: 14px;
}

.simple-list {
  padding-left: 20px;
  margin: 0;
  color: #606266;
  line-height: 1.8;
}
.simple-list li { margin-bottom: 4px; }

.test-result.variant-workspace .ws-acupoint-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
@media (min-width: 1400px) {
  .test-result.variant-workspace .ws-acupoint-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}
@media (max-width: 992px) {
  .test-result.variant-workspace .ws-acupoint-grid { grid-template-columns: 1fr; }
}

.test-result.variant-workspace .ws-acupoint-card {
  border: 1px solid #eef2f7;
  background: #ffffff;
  border-radius: 12px;
  padding: 12px;
  box-shadow: 0 1px 0 rgba(15, 23, 42, 0.02);
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}
.test-result.variant-workspace .ws-acupoint-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.10);
  border-color: rgba(59, 130, 246, 0.25);
}

.test-result.variant-workspace .ws-acupoint-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  min-width: 0;
}
.test-result.variant-workspace .ws-acupoint-badge {
  font-size: 12px;
  font-weight: 800;
  color: #1d4ed8;
  background: rgba(59, 130, 246, 0.10);
  border: 1px solid rgba(59, 130, 246, 0.22);
  padding: 2px 8px;
  border-radius: 999px;
  flex-shrink: 0;
}
.test-result.variant-workspace .ws-acupoint-name {
  font-size: 14px;
  font-weight: 900;
  color: #0f172a;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.test-result.variant-workspace .ws-acupoint-meta {
  background: #f8fafc;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  padding: 10px;
}
.test-result.variant-workspace .ws-acupoint-row {
  display: grid;
  grid-template-columns: 64px 1fr;
  gap: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #334155;
  margin-bottom: 8px;
}
.test-result.variant-workspace .ws-acupoint-row:last-child { margin-bottom: 0; }
.test-result.variant-workspace .ws-acupoint-row .k {
  color: #475569;
  font-weight: 800;
  white-space: nowrap;
}
.test-result.variant-workspace .ws-acupoint-row .v { color: #334155; }

/* plans-list / plan-card：沿用当前页面已有定义即可；这里仅补空态容器 */
.test-result.variant-workspace .workspace-cards-empty { margin-top: 14px; }

/* 计划卡片样式（与 SmartConstitutionTest 一致） */
.plans-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 16px;
}

.plan-card {
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 10px rgba(15, 23, 42, 0.06);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  display: flex;
  flex-direction: column;
}

.plan-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.10);
}

.plan-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 10px;
}

.plan-card-title {
  font-size: 15px;
  font-weight: 800;
  color: #0f172a;
  line-height: 1.35;
  flex: 1;
  min-width: 0;
}

.plan-card-desc {
  font-size: 13px;
  color: #475569;
  line-height: 1.6;
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.plan-card-info {
  background: #f8fafc;
  border: 1px solid #eef2f7;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 12px;
}

.plan-card-info .info-row {
  display: flex;
  align-items: flex-start;
  font-size: 12px;
  margin-bottom: 8px;
  line-height: 1.45;
}
.plan-card-info .info-row:last-child { margin-bottom: 0; }

.plan-card-info .info-row.target { color: #409EFF; }
.plan-card-info .info-row.date,
.plan-card-info .info-row.freq { color: #64748b; }

.plan-card-info .info-row .label {
  margin-right: 4px;
  white-space: nowrap;
  color: #475569;
  font-weight: 600;
}

.plan-card-progress { margin-bottom: 12px; }
.plan-card-progress .progress-labels {
  display: flex;
  justify-content: flex-end;
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}

/* ===== 药膳列表（图1样式：单列、简洁行卡片） ===== */
.ws-recipe-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.ws-recipe-item {
  background: #ffffff;
  border: 1px solid #eef2f7;
  border-radius: 12px;
  padding: 14px;
}

@media (max-width: 992px) {
  .ws-recipe-list {
    grid-template-columns: 1fr;
  }
}

.ws-recipe-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.ws-recipe-item-title {
  font-weight: 800;
  color: #0f172a;
  font-size: 14px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ws-recipe-line {
  font-size: 13px;
  color: #334155;
  line-height: 1.85;
  margin-top: 4px;
}

.ws-recipe-steps {
  margin: 6px 0 0 18px;
  padding: 0;
}

.ws-recipe-steps li {
  margin: 4px 0;
  line-height: 1.8;
  color: #334155;
}

.ws-recipe-item-meta {
  margin-top: 8px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
</style>
