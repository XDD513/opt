import { ElNotification } from 'element-plus'
import { getAppConfig } from '@/config/runtimeConfig'

const resolveDefaultDuration = () => {
  const duration = getAppConfig()?.messageDuration
  if (!duration) return 4500
  const parsed = parseInt(duration, 10)
  return Number.isNaN(parsed) ? 4500 : parsed
}

const buildDefaultOptions = () => ({
  duration: resolveDefaultDuration(),
  offset: 24,
  position: 'top-right'
})

const normalizeOptions = (options) => {
  if (typeof options === 'string') return { message: options }
  return options || {}
}

const createNotify = (type) => (options) => {
  const normalized = normalizeOptions(options)
  return ElNotification({
    ...buildDefaultOptions(),
    ...normalized,
    type
  })
}

const notify = (options) => ElNotification({ ...buildDefaultOptions(), ...normalizeOptions(options) })

;['success', 'warning', 'info', 'error'].forEach((type) => {
  notify[type] = createNotify(type)
})

export default notify

