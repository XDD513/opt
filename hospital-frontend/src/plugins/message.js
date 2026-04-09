import { ElMessage } from 'element-plus'
import { markRaw } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { getAppConfig } from '@/config/runtimeConfig'

const resolveDefaultDuration = () => {
  const duration = getAppConfig()?.messageDuration
  if (!duration) return 3000
  const parsed = parseInt(duration, 10)
  return Number.isNaN(parsed) ? 3000 : parsed
}

const buildDefaultOptions = () => ({
  duration: resolveDefaultDuration(),
  offset: 24,
  showClose: false,
  grouping: false // 禁用分组，确保每个消息独立管理
})

const normalizeOptions = (options) => {
  if (typeof options === 'string') {
    return { message: options }
  }
  return options || {}
}

// 消息队列，用于限制最多显示2个消息
const messageQueue = []

const MAX_MESSAGE_COUNT = 2
// 每个消息的高度（包括间距）
const MESSAGE_HEIGHT = 80
// 基础偏移量（设置为 100px，避免挡住顶部导航栏，出现在内容区域）
const BASE_OFFSET = 50

// MutationObserver 用于监听消息元素的添加
let messageObserver = null
let updateTimer = null

// 初始化 MutationObserver
const initMessageObserver = () => {
  if (typeof document === 'undefined' || messageObserver) return
  
  messageObserver = new MutationObserver(() => {
    // 防抖处理，避免频繁更新
    if (updateTimer) {
      clearTimeout(updateTimer)
    }
    updateTimer = setTimeout(() => {
      updateMessagePositions()
    }, 10)
  })
  
  // 监听 body 的变化
  messageObserver.observe(document.body, {
    childList: true,
    subtree: true
  })
}

// 更新所有消息的位置（类似 Framer Motion 的 layout 动画）
const updateMessagePositions = (skipTransition = false) => {
  if (typeof document === 'undefined') return
  
  // 使用 requestAnimationFrame 确保在下一帧更新
  requestAnimationFrame(() => {
    const allMessages = Array.from(document.querySelectorAll('.el-message'))
    
    // 过滤掉正在消失的消息（类似 AnimatePresence 的 popLayout）
    const visibleMessages = allMessages.filter(el => {
      const style = window.getComputedStyle(el)
      const opacity = parseFloat(style.opacity)
      const transform = style.transform || 'none'
      // 过滤掉透明度为0或正在向上滑出的消息
      return opacity > 0.1 && !transform.includes('translateY(-100%)')
    })
    
    // 只保留最多2个消息，按 DOM 顺序（创建时间）
    const messagesToKeep = visibleMessages.slice(0, MAX_MESSAGE_COUNT)
    
    // 如果超过2个，立即关闭多余的（从第3个开始）
    if (visibleMessages.length > MAX_MESSAGE_COUNT) {
      for (let i = MAX_MESSAGE_COUNT; i < visibleMessages.length; i++) {
        const el = visibleMessages[i]
        // 立即从文档流中移除（类似 popLayout）
        el.style.setProperty('position', 'absolute', 'important')
        el.style.setProperty('transition', 'transform 0.3s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.3s cubic-bezier(0.4, 0, 0.2, 1)', 'important')
        el.style.setProperty('transform', 'translateY(-100%)', 'important')
        el.style.setProperty('opacity', '0', 'important')
        el.style.setProperty('pointer-events', 'none', 'important')
        
        // 动画完成后移除元素
        setTimeout(() => {
          if (el.parentNode) {
            el.parentNode.removeChild(el)
          }
        }, 300)
      }
    }
    
    // 按照顺序更新位置，使用平滑的布局过渡（类似 Framer Motion 的 layout）
    messagesToKeep.forEach((el, index) => {
      const targetOffset = BASE_OFFSET + index * MESSAGE_HEIGHT
      const currentTop = parseInt(el.style.top) || BASE_OFFSET
      
      // 设置位置
      el.style.setProperty('top', `${targetOffset}px`, 'important')
      el.style.setProperty('position', 'fixed', 'important')
      
      // 只有在需要动画时才设置过渡（skipTransition 为 false）
      if (!skipTransition && Math.abs(currentTop - targetOffset) > 1) {
        el.style.setProperty('transition', 'top 0.4s cubic-bezier(0.4, 0, 0.2, 1)', 'important')
      }
    })
  })
}

const enhanceByType = (type, options) => {
  if (type === 'error' || type === 'warning') {
    return {
      showClose: true,
      duration: Math.max(options.duration || 0, 5000)
    }
  }
  if (type === 'success') {
    return {
      duration: options.duration || 2500
    }
  }
  return {}
}

// 移除队列中的消息
const removeFromQueue = (handler) => {
  const index = messageQueue.findIndex(item => item.handler === handler)
  if (index !== -1) {
    messageQueue.splice(index, 1)
    // 更新剩余消息的位置
    updateMessagePositions()
  }
}

// 如果队列已满，关闭第一个消息（向上滑出，类似 Framer Motion 的 exit 动画）
const ensureQueueLimit = () => {
  // 如果队列已满，关闭第一个消息
  if (messageQueue.length >= MAX_MESSAGE_COUNT) {
    const firstMessage = messageQueue.shift()
    if (firstMessage && firstMessage.handler) {
      // 立即获取所有可见消息
      const allMessages = Array.from(document.querySelectorAll('.el-message'))
        .filter(el => {
          const style = window.getComputedStyle(el)
          return parseFloat(style.opacity) > 0.1
        })
      
      if (allMessages.length > 0) {
        const firstEl = allMessages[0]
        const remainingMessages = allMessages.slice(1) // 除了第一个之外的所有消息
        
        // 第一步：立即更新其他消息的位置，让它们开始上移动画
        // 这必须在第一个消息退出之前完成，确保动画同步
        remainingMessages.forEach((el, index) => {
          const targetOffset = BASE_OFFSET + index * MESSAGE_HEIGHT
          // 立即设置新的目标位置和过渡动画
          el.style.setProperty('top', `${targetOffset}px`, 'important')
          el.style.setProperty('transition', 'top 0.4s cubic-bezier(0.4, 0, 0.2, 1)', 'important')
          el.style.setProperty('position', 'fixed', 'important')
        })
        
        // 第二步：在下一帧让第一个消息开始退出动画
        requestAnimationFrame(() => {
          // 立即从文档流中移除（类似 popLayout），让其他元素立即上移
          firstEl.style.setProperty('position', 'absolute', 'important')
          
          // 添加向上滑出的退出动画（类似 exit: { y: '-100%' }）
          firstEl.style.setProperty('transition', 'transform 0.3s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.3s cubic-bezier(0.4, 0, 0.2, 1)', 'important')
          firstEl.style.setProperty('transform', 'translateY(-100%)', 'important')
          firstEl.style.setProperty('opacity', '0', 'important')
          firstEl.style.setProperty('pointer-events', 'none', 'important')
        })
        
        // 延迟关闭，让退出动画完成
        setTimeout(() => {
          if (typeof firstMessage.handler.close === 'function') {
            firstMessage.handler.close()
          }
          // 最终更新位置，确保所有消息位置正确
          updateMessagePositions()
        }, 300)
      }
    }
  }
}

const createMessage = (method, type = 'default') => (options) => {
  // 初始化 Observer
  if (typeof document !== 'undefined') {
    initMessageObserver()
  }

  const normalized = {
    ...buildDefaultOptions(),
    ...normalizeOptions(options)
  }

  // 确保队列不超过最大数量（在创建新消息前）
  ensureQueueLimit()

  // 计算当前消息的位置（队列中的索引）
  const queueIndex = messageQueue.length
  const offset = BASE_OFFSET + queueIndex * MESSAGE_HEIGHT

  const merged = {
    ...normalized,
    ...enhanceByType(type, normalized),
    offset: offset // 设置消息的垂直位置
  }

  const handler = method({
    ...merged,
    onClose: () => {
      // 从队列中移除
      removeFromQueue(handler)
      merged.onClose?.()
    }
  })

  // 添加到队列
  messageQueue.push({ handler })

  // 立即设置初始位置（类似 initial 动画）
  requestAnimationFrame(() => {
    const messages = Array.from(document.querySelectorAll('.el-message'))
    if (messages.length > 0) {
      const newMessage = messages[messages.length - 1]
      const queueIndex = messageQueue.length - 1
      const offset = BASE_OFFSET + queueIndex * MESSAGE_HEIGHT
      
      // 设置初始位置（从右侧滑入，类似 initial: { x: 30 }）
      newMessage.style.setProperty('top', `${offset}px`, 'important')
      newMessage.style.setProperty('position', 'fixed', 'important')
    }
    
    // 更新所有消息的位置（类似 layout 动画）
    updateMessagePositions()
  })
  
  // 使用双重 requestAnimationFrame 确保在 DOM 完全渲染后更新
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      updateMessagePositions()
    })
  })
  
  // 延迟更新，确保消息已完全渲染
  setTimeout(() => updateMessagePositions(), 0)
  setTimeout(() => updateMessagePositions(), 16) // 一帧的时间
  setTimeout(() => updateMessagePositions(), 50)

  return handler
}

const message = createMessage(ElMessage)

;['success', 'warning', 'info', 'error'].forEach((type) => {
  message[type] = createMessage(ElMessage[type], type)
})

message.close = ElMessage.close
message.closeAll = ElMessage.closeAll

message.loading = (options = {}) => {
  const normalized = normalizeOptions(options)
  return ElMessage({
    ...buildDefaultOptions(),
    ...normalized,
    icon: normalized.icon || markRaw(Loading),
    duration: normalized.duration ?? 0,
    showClose: true,
    type: 'info',
    message: normalized.message || '加载中...'
  })
}

export default message

