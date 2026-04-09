/**
 * 从 AI 返回的文本中解析养生报告 JSON（支持 ```json 围栏、首尾杂讯）。
 * 使用括弧深度匹配，避免 lastIndexOf('}') 在嵌套对象时截断错误。
 */
export function extractBalancedJsonObject(text) {
  if (!text || typeof text !== 'string') return null
  const start = text.indexOf('{')
  if (start === -1) return null
  let depth = 0
  let inString = false
  let escapeNext = false
  for (let i = start; i < text.length; i++) {
    const ch = text[i]
    if (inString) {
      if (escapeNext) {
        escapeNext = false
      } else if (ch === '\\') {
        escapeNext = true
      } else if (ch === '"') {
        inString = false
      }
      continue
    }
    if (ch === '"') {
      inString = true
      continue
    }
    if (ch === '{') depth++
    else if (ch === '}') {
      depth--
      if (depth === 0) return text.substring(start, i + 1)
    }
  }
  return null
}

/**
 * 去掉 Markdown 代码块围栏，取其中主体；若无围栏则返回原文 trim 后内容。
 */
export function stripMarkdownJsonFence(raw) {
  if (!raw || typeof raw !== 'string') return ''
  let text = raw.trim().replace(/^\uFEFF/, '')
  const fence = /```(?:json)?\s*([\s\S]*?)```/i
  const m = text.match(fence)
  if (m && m[1] != null) {
    return m[1].trim()
  }
  return text
}

/**
 * 解析健康建议字符串为对象；失败返回 null。
 */
export function parseAiHealthSuggestion(raw) {
  if (!raw || typeof raw !== 'string') return null
  let text = stripMarkdownJsonFence(raw)

  let jsonStr = extractBalancedJsonObject(text)
  if (!jsonStr) {
    const s = text.indexOf('{')
    const e = text.lastIndexOf('}')
    if (s !== -1 && e > s) jsonStr = text.slice(s, e + 1)
  }
  if (!jsonStr) return null

  try {
    return JSON.parse(jsonStr)
  } catch (_) {
    try {
      return JSON.parse(jsonStr.replace(/,\s*([}\]])/g, '$1'))
    } catch (_) {
      // 进一步兼容：无法解析为 JSON 时，尝试提取“【药膳建议】”段落
      const idx = text.indexOf('【药膳建议】')
      if (idx >= 0) {
        const tail = text.slice(idx + '【药膳建议】'.length)
        const breakers = ['【', '总体原则', '饮食', '起居', '穴位', '健康计划']
        let cut = tail.length
        for (const b of breakers) {
          const p = tail.indexOf(b)
          if (p > 0) cut = Math.min(cut, p)
        }
        const recipeText = tail.slice(0, cut).trim()
        if (recipeText) return { recipeText }
      }
      return null
    }
  }
}

/**
 * 统一穴位列表展示：AI 常返回 { name, location, effect }，也可能返回「穴位名+灸法/按摩说明」的纯字符串数组。
 */
export function normalizeAcupointsList(raw) {
  if (!raw || !Array.isArray(raw)) return []
  return raw
    .map((item, index) => {
      if (item == null) return null
      if (typeof item === 'string') {
        const text = item.trim()
        if (!text) return null
        let name = `穴位建议 ${index + 1}`
        const bracket = text.match(/^【([^】]{1,14})】/)
        if (bracket) {
          name = bracket[1]
        } else {
          const head = text.match(/^([\u4e00-\u9fa5·]{2,10}穴?)/)
          if (head) name = head[1]
        }
        return { name, location: '', effect: text }
      }
      if (typeof item === 'object') {
        const name = item.name || item.穴位 || `穴位 ${index + 1}`
        const location = item.location || item.定位 || ''
        const effect =
          item.effect ||
          item.功效 ||
          item.description ||
          item.detail ||
          ''
        const pieces = [location, effect].filter(Boolean)
        const text =
          pieces.length > 0 ? pieces.join('；') : (name !== `穴位 ${index + 1}` ? '（见上方辨证方案）' : '')
        return {
          name,
          location,
          effect: text || JSON.stringify(item)
        }
      }
      return { name: `条目 ${index + 1}`, location: '', effect: String(item) }
    })
    .filter(Boolean)
}

/**
 * 根据月份直接映射季节（不做复杂推断）
 * 3-5: SPRING, 6-8: SUMMER, 9-11: AUTUMN, 12/1/2: WINTER
 */
export function getCurrentSeasonValueByMonth(date = new Date()) {
  try {
    const month = (date instanceof Date ? date : new Date(date)).getMonth() + 1
    if (month >= 3 && month <= 5) return 'SPRING'
    if (month >= 6 && month <= 8) return 'SUMMER'
    if (month >= 9 && month <= 11) return 'AUTUMN'
    return 'WINTER'
  } catch {
    return 'WINTER'
  }
}

/**
 * 统一药膳结构到前端所需字段，并兼容后端入库字段。
 */
export function normalizeRecipeJson(raw) {
  if (!raw || typeof raw !== 'object') return null

  const recipeName = raw.recipeName || raw.name || raw.菜名 || ''
  const steps = Array.isArray(raw.steps) ? raw.steps : (Array.isArray(raw.做法) ? raw.做法 : [])
  const efficacy = raw.efficacy || raw.功效 || raw.effect || ''
  const suitableSymptoms = raw.suitableSymptoms || raw.suitable_symptoms || raw.适用症状 || ''
  const nutrition = raw.nutritionInfo || raw.nutrition || {}

  let ingredients = []
  if (Array.isArray(raw.ingredients)) {
    ingredients = raw.ingredients
      .map((it) => {
        const name = it?.name || it?.食材 || ''
        if (!name) return null
        const amountRaw = it?.amount != null ? it.amount : (it?.用量 || '')
        const unit = it?.unit != null ? String(it.unit).trim() : ''
        const amount = amountRaw != null ? String(amountRaw).trim() : ''
        const note = it?.note || it?.remark || it?.备注 || ''
        return { name, amount, unit, note }
      })
      .filter(Boolean)
  }

  const contraindRaw = raw.contraindications
  const contraindications = Array.isArray(contraindRaw)
    ? contraindRaw.filter(Boolean).map(String)
    : (typeof contraindRaw === 'string' && contraindRaw.trim()
        ? contraindRaw.split(/[、，,；;]/).map(s => s.trim()).filter(Boolean)
        : [])
  const contraindicationsText = contraindications.join('、')

  const nutritionInfo = {
    calorie: nutrition.calorie ?? nutrition.kcal ?? nutrition.calories ?? null,
    protein_g: nutrition.protein_g ?? nutrition.protein ?? null,
    fat_g: nutrition.fat_g ?? nutrition.fat ?? null,
    carb_g: nutrition.carb_g ?? nutrition.carb ?? nutrition.carbohydrate ?? nutrition.carbs ?? null
  }

  return {
    // 前端展示兼容字段
    name: recipeName,
    ingredients,
    steps,
    efficacy,
    contraindications,
    nutrition: nutritionInfo,
    // 后端入库字段
    recipeName,
    constitutionType: raw.constitutionType || raw.constitution_type || 'ALL',
    season: raw.season || 'ALL',
    category: raw.category || '',
    difficulty: Number.isFinite(Number(raw.difficulty)) ? Number(raw.difficulty) : null,
    cookingTime: Number.isFinite(Number(raw.cookingTime)) ? Number(raw.cookingTime) : null,
    servings: Number.isFinite(Number(raw.servings)) ? Number(raw.servings) : null,
    suitableSymptoms,
    contraindicationsText,
    nutritionInfo,
    tips: raw.tips || ''
  }
}