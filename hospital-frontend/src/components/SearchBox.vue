<template>
  <div class="search-box">
    <div class="search-box-row">
      <el-autocomplete
        v-model="keyword"
        :fetch-suggestions="handleSearch"
        :placeholder="placeholder"
        :trigger-on-focus="false"
        clearable
        @select="handleSelect"
        @keyup.enter="handleEnter"
        @focus="handleFocus"
        class="search-input"
        popper-class="search-suggestions"
      >
        <template #prefix>
          <el-icon>
            <Search />
          </el-icon>
        </template>
        <template #default="{ item }">
          <div class="suggestion-item">
            <el-icon class="suggestion-icon">
              <component :is="getTypeIcon(item.type)" />
            </el-icon>
            <span class="suggestion-text">{{ item.title || item.name }}</span>
            <el-tag :type="getTypeTagType(item.type)" size="small" class="suggestion-tag">
              {{ getTypeName(item.type) }}
            </el-tag>
          </div>
        </template>
      </el-autocomplete>
      
      <el-button type="primary" @click="handleSearchClick" :icon="Search">
        搜索
      </el-button>
    </div>

    <!-- 热门搜索词 -->
    <div v-if="showHotKeywords && hotKeywords.length > 0" class="hot-keywords">
      <span class="hot-keywords-label">热门搜索：</span>
      <el-tag
        v-for="(keyword, index) in hotKeywords"
        :key="index"
        class="hot-keyword-tag"
        @click="handleHotKeywordClick(keyword)"
        effect="plain"
        size="small"
      >
        {{ keyword }}
      </el-tag>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, User, Food, Reading, Location } from '@element-plus/icons-vue'
import { saveSearchHistory, getSearchHistory } from '@/utils/searchHistory'
import { getHotKeywords, recordSearchKeyword } from '@/api/search'

const props = defineProps({
  placeholder: {
    type: String,
    default: '搜索用户、药膳、文章、穴位...'
  },
  modelValue: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue', 'search'])

const router = useRouter()
const keyword = ref(props.modelValue)
const hotKeywords = ref([])
const showHotKeywords = ref(false)

// 监听外部值变化
watch(() => props.modelValue, (newVal) => {
  keyword.value = newVal
})

// 监听内部值变化
watch(keyword, (newVal) => {
  emit('update:modelValue', newVal)
})

// 获取类型图标
const getTypeIcon = (type) => {
  const iconMap = {
    'recipe': Food,
    'article': Reading,
    'acupoint': Location
  }
  return iconMap[type] || Search
}

// 获取类型名称
const getTypeName = (type) => {
  const nameMap = {
    'recipe': '药膳',
    'article': '文章',
    'acupoint': '穴位'
  }
  return nameMap[type] || '未知'
}

// 获取类型标签颜色
const getTypeTagType = (type) => {
  const typeMap = {
    'recipe': 'success',
    'article': 'warning',
    'acupoint': 'info'
  }
  return typeMap[type] || 'info'
}

// 加载热门搜索词
const loadHotKeywords = async () => {
  try {
    const res = await getHotKeywords(10)
    if (res.code === 200 && res.data) {
      hotKeywords.value = res.data
    }
  } catch (error) {
    console.error('加载热门搜索词失败', error)
  }
}

// 搜索建议
const handleSearch = (queryString, cb) => {
  if (!queryString || queryString.trim() === '') {
    // 显示搜索历史和热门搜索词
    const history = getSearchHistory()
    const suggestions = history.slice(0, 5).map(item => ({
      value: item,
      title: item,
      type: 'history'
    }))
    cb(suggestions)
    return
  }
  
  // 这里可以调用后端接口获取搜索建议
  // 暂时返回空数组
  cb([])
}

// 输入框获得焦点
const handleFocus = () => {
  showHotKeywords.value = true
}

// 选择建议项
const handleSelect = (item) => {
  if (item.type === 'history') {
    keyword.value = item.value
    handleSearchClick()
  }
}

// 回车搜索
const handleEnter = () => {
  handleSearchClick()
}

// 点击搜索按钮
const handleSearchClick = () => {
  if (!keyword.value || keyword.value.trim() === '') {
    return
  }
  
  const trimmedKeyword = keyword.value.trim()
  
  // 保存搜索历史
  saveSearchHistory(trimmedKeyword)
  
  // 记录搜索关键词（用于统计热门搜索词）
  recordSearchKeyword(trimmedKeyword).catch(err => {
    console.error('记录搜索关键词失败', err)
  })
  
  // 隐藏热门搜索词
  showHotKeywords.value = false
  
  // 触发搜索事件
  emit('search', trimmedKeyword)
  
  // 跳转到搜索结果页
  router.push({
    name: 'SearchResult',
    query: {
      keyword: trimmedKeyword,
      type: 'all'
    }
  })
}

// 点击热门搜索词
const handleHotKeywordClick = (kw) => {
  keyword.value = kw
  handleSearchClick()
}

// 页面加载时获取热门搜索词
onMounted(() => {
  loadHotKeywords()
})
</script>

<style scoped>
.search-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.search-box-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.search-input {
  flex: 1;
}

:deep(.search-input .el-input__inner) {
  height: 40px;
  font-size: 14px;
}

.suggestion-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
}

.suggestion-icon {
  font-size: 18px;
  color: #909399;
}

.suggestion-text {
  flex: 1;
  color: #303133;
}

.suggestion-tag {
  margin-left: auto;
}

.hot-keywords {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 0;
}

.hot-keywords-label {
  font-size: 14px;
  color: #606266;
  white-space: nowrap;
}

.hot-keyword-tag {
  cursor: pointer;
  transition: all 0.3s;
}

.hot-keyword-tag:hover {
  transform: translateY(-2px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}
</style>

<style>
.search-suggestions {
  max-height: 400px;
  overflow-y: auto;
}
</style>

