// 全局被动事件补丁：针对 ECharts 等库绑定的滚轮事件默认开启 passive，避免控制台报警告
if (typeof window !== 'undefined' && typeof EventTarget !== 'undefined') {
  const originalAddEventListener = EventTarget.prototype.addEventListener
  const NEEDS_PASSIVE = new Set(['wheel', 'mousewheel', 'touchstart', 'touchmove'])

  EventTarget.prototype.addEventListener = function patchedAddEventListener(type, listener, options) {
    let finalOptions = options
    if (listener && NEEDS_PASSIVE.has(type)) {
      if (options === undefined) {
        finalOptions = { passive: true }
      } else if (typeof options === 'boolean') {
        finalOptions = { capture: options, passive: true }
      } else if (typeof options === 'object' && options.passive === undefined) {
        finalOptions = { ...options, passive: true }
      }
    }
    return originalAddEventListener.call(this, type, listener, finalOptions)
  }
}


