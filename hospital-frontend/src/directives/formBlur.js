/**
 * 表单失焦清除验证提示指令
 * 
 * 使用方式：v-form-blur="formRef"
 * 会自动为所有表单项添加失焦清除验证提示的处理
 */

export default {
  mounted(el, binding) {
    const formRef = binding.value
    
    if (!formRef || !formRef.value) return
    
    // 查找所有带有 prop 属性的表单项（即有验证规则的字段）
    const formItems = el.querySelectorAll('.el-form-item[class*="prop"]')
    
    formItems.forEach(item => {
      // 获取 prop 属性值（字段名）
      const prop = item.getAttribute('prop')
      if (!prop) return
      
      // 查找该表单项中的输入控件
      const input = item.querySelector('input, textarea, .el-select, .el-date-picker, .el-input-number')
      if (!input) return
      
      // 为输入控件添加失焦事件
      const inputEl = input.tagName === 'INPUT' || input.tagName === 'TEXTAREA' 
        ? input 
        : input.querySelector('input') || input
        
      if (inputEl) {
        inputEl.addEventListener('blur', async () => {
          // 等待一下让表单验证先执行完成
          await new Promise(resolve => setTimeout(resolve, 50))
          
          // 验证该字段，如果验证通过，清除验证提示
          try {
            await formRef.value.validateField(prop)
            // 验证通过，清除该字段的验证提示
            formRef.value.clearValidate(prop)
          } catch (error) {
            // 验证失败，保留错误提示（不做处理）
          }
        })
      }
    })
  },
  updated(el, binding) {
    // 当表单更新时，重新绑定事件
    // 这里可以添加更新逻辑，但通常 mounted 就足够了
  }
}

