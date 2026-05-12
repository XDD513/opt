import { parseAiHealthSuggestion } from '@/utils/parseAiHealthJson'

/**
 * 从 DIET 计划的 targetContent 抽取疑似药膳名（与体质工作台规则一致）
 * @param {string} raw
 * @returns {string[]}
 */
export function extractRecipesFromText(raw) {
  if (!raw) return []
  let text = String(raw)
    .replace(/(目标|早餐|午餐|晚餐|加餐|全天|建议|注意)[:：]/g, ' ')
    .replace(/[()（）]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  const hits = []
  const hardRegex = /[\u4e00-\u9fa5·]{2,30}(粥|汤|羹|饮|茶)/g
  let m
  while ((m = hardRegex.exec(text)) !== null) {
    hits.push(m[0].trim())
  }
  const pieces = text.split(/[，、。；;,.]/).map((s) => s.trim()).filter(Boolean)
  const KEY = /(粥|汤|羹|饮|茶|药膳)/
  for (const p of pieces) {
    if (KEY.test(p)) {
      let t = p.replace(/^(如|或者|或|搭配|加入|建议|宜|可选|例如)[:：]?/g, '').trim()
      const cut = t.match(/^[\u4e00-\u9fa5·]{2,30}(粥|汤|羹|饮|茶)/)
      if (cut) t = cut[0]
      if (t.length >= 2) hits.push(t)
    }
  }
  const cookRegex = /[\u4e00-\u9fa5·]{1,12}(清炒|炒|炖|煮|蒸|焖|烩|拌)[\u4e00-\u9fa5·]{1,12}/g
  while ((m = cookRegex.exec(text)) !== null) {
    hits.push(m[0].trim())
  }
  const pairRegex = /(配|搭配)[\u4e00-\u9fa5·]{2,16}/g
  while ((m = pairRegex.exec(text)) !== null) {
    const cand = m[0].replace(/^(配|搭配)/, '').trim()
    if (cand && cand.length >= 2) hits.push(cand)
  }
  return Array.from(new Set(hits))
}

/**
 * 从 healthSuggestion 解析 DIET 计划并得到药膳名列表
 * @param {string} healthSuggestion
 * @returns {string[]}
 */
export function collectRecipeNamesFromHealthSuggestion(healthSuggestion) {
  const raw = String(healthSuggestion || '')
  const parsed = parseAiHealthSuggestion(raw)
  const plans = parsed?.plans || []
  const dietPlans = plans.filter((p) => (p.planType || p.type) === 'DIET')
  const names = []
  for (const p of dietPlans) {
    const src = p?.targetContent || ''
    names.push(...extractRecipesFromText(src))
  }
  return Array.from(new Set(names))
}
