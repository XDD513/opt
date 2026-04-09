/**
 * 搜索历史工具函数
 */

const SEARCH_HISTORY_KEY = 'search_history'
const MAX_HISTORY_COUNT = 10

/**
 * 保存搜索历史
 */
export function saveSearchHistory(keyword) {
  if (!keyword || keyword.trim() === '') {
    return
  }
  
  try {
    const history = getSearchHistory()
    
    // 移除重复项
    const filtered = history.filter(item => item !== keyword.trim())
    
    // 添加到开头
    filtered.unshift(keyword.trim())
    
    // 限制数量
    const limited = filtered.slice(0, MAX_HISTORY_COUNT)
    
    // 保存到LocalStorage
    localStorage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(limited))
  } catch (error) {
    console.error('保存搜索历史失败', error)
  }
}

/**
 * 获取搜索历史
 */
export function getSearchHistory() {
  try {
    const historyStr = localStorage.getItem(SEARCH_HISTORY_KEY)
    if (!historyStr) {
      return []
    }
    return JSON.parse(historyStr)
  } catch (error) {
    console.error('获取搜索历史失败', error)
    return []
  }
}

/**
 * 清除搜索历史
 */
export function clearSearchHistory() {
  try {
    localStorage.removeItem(SEARCH_HISTORY_KEY)
  } catch (error) {
    console.error('清除搜索历史失败', error)
  }
}

/**
 * 删除单条搜索历史
 */
export function removeSearchHistory(keyword) {
  try {
    const history = getSearchHistory()
    const filtered = history.filter(item => item !== keyword)
    localStorage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(filtered))
  } catch (error) {
    console.error('删除搜索历史失败', error)
  }
}

