<template>
  <div class="suggestion-wrapper">
    <el-autocomplete
      v-model="inputValue"
      :fetch-suggestions="querySuggestions"
      :placeholder="placeholder"
      :trigger-on-focus="triggerOnFocus"
      @select="handleSelect"
      class="suggestion-input"
      clearable
      :debounce="300"
    >
      <template #default="{ item }">
        <div class="suggestion-item">
          <span class="keyword">{{ item.value }}</span>
          <el-tag v-if="item.type" size="small" type="info">
            {{ item.type }}
          </el-tag>
        </div>
      </template>
    </el-autocomplete>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { suggestSymptoms } from '@/api/suggestion'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: '请输入症状关键词'
  },
  triggerOnFocus: {
    type: Boolean,
    default: true
  },
  type: {
    type: String,
    default: 'symptom' // symptom, department, question
  }
})

const emit = defineEmits(['update:modelValue', 'select'])

const inputValue = ref(props.modelValue || '')

// 监听外部值变化
watch(() => props.modelValue, (newVal) => {
  inputValue.value = newVal || ''
})

// 监听内部值变化
watch(inputValue, (newVal) => {
  emit('update:modelValue', newVal)
})

const querySuggestions = async (queryString, cb) => {
  if (!queryString || queryString.length < 1) {
    cb([])
    return
  }

  try {
    const response = await suggestSymptoms(queryString, 10)
    const suggestions = (response.data || []).map(item => ({
      value: item,
      type: '症状'
    }))
    cb(suggestions)
  } catch (error) {
    console.error('提词器查询失败:', error)
    cb([])
  }
}

const handleSelect = (item) => {
  emit('update:modelValue', item.value)
  emit('select', item.value)
}
</script>

<style scoped>
.suggestion-wrapper {
  width: 100%;
}

.suggestion-input {
  width: 100%;
}

.suggestion-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
}

.keyword {
  font-weight: 500;
  margin-right: 10px;
  flex: 1;
}
</style>

