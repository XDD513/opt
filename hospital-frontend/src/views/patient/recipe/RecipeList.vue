<template>
  <div class="recipe-list">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="搜索药膳名称或功效" clearable @clear="handleSearch">
            <template #prefix>
              <el-icon>
                <Search />
              </el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="季节">
          <el-select v-model="searchForm.season" placeholder="选择季节" clearable @change="handleSearch"
            style="width: 150px;">
            <el-option label="春季" value="SPRING" />
            <el-option label="夏季" value="SUMMER" />
            <el-option label="秋季" value="AUTUMN" />
            <el-option label="冬季" value="WINTER" />
            <el-option label="四季皆宜" value="ALL" />
          </el-select>
        </el-form-item>

        <el-form-item label="体质">
          <el-select v-model="searchForm.constitutionType" placeholder="选择体质类型" clearable @change="handleSearch"
            style="width: 180px;">
            <el-option v-for="type in constitutionTypes" :key="type.typeCode" :label="type.typeName"
              :value="type.typeCode" />
          </el-select>
        </el-form-item>

        <el-form-item label="功效">
          <el-select
            v-model="searchForm.effect"
            placeholder="选择或输入功效"
            clearable
            filterable
            allow-create
            default-first-option
            style="width: 200px;"
            @change="handleSearch"
          >
            <el-option
              v-for="effect in effectOptions"
              :key="effect"
              :label="effect"
              :value="effect"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon>
              <Search />
            </el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 药膳列表 -->
    <el-card class="list-card">
      <template #header>
        <div class="card-header">
          <span>药膳列表</span>
          <el-button type="primary" @click="loadRecommendedList">刷新推荐</el-button>
        </div>
      </template>

      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="5" animated />
      </div>

      <div v-else-if="recipeList.length > 0">
        <el-row :gutter="20">
          <el-col
            :xs="24"
            :sm="12"
            :md="8"
            :lg="6"
            :xl="6"
            v-for="recipe in recipeList"
            :key="recipe.id"
            style="margin-bottom: 20px;"
          >
            <el-card class="recipe-card" :body-style="{ padding: '0px' }" shadow="hover">
              <div class="recipe-image">
                <el-image :src="resolveRecipeCoverUrl(recipe.image || recipe.imageUrl, recipe.id, recipe.recipeName)" :alt="recipe.recipeName" fit="cover"
                  :lazy="true">
                  <template #error>
                    <div class="image-slot">
                      <el-icon>
                        <Picture />
                      </el-icon>
                    </div>
                  </template>
                </el-image>
                <div class="recipe-tags">
                  <el-tag v-for="season in recipe.season?.split(',')" :key="season" size="small">
                    {{ getSeasonName(season) }}
                  </el-tag>
                  <el-tag v-for="type in recipe.constitutionType?.split(',')" :key="type" size="small" type="primary">
                    {{ getConstitutionName(type) }}
                  </el-tag>
                </div>
              </div>

              <div class="recipe-content">
                <h3>{{ recipe.recipeName }}</h3>
                <p class="recipe-efficacy">{{ recipe.efficacy }}</p>
                
                <!-- AI推荐理由 -->
                <div v-if="recipe.recommendationReason" class="recommendation-reason">
                  <el-alert
                    :title="recipe.recommendationReason"
                    type="info"
                    :closable="false"
                    show-icon
                    :effect="'light'"
                  />
                </div>

                <div class="recipe-meta">
                  <span>
                    <el-icon>
                      <User />
                    </el-icon>
                    {{ recipe.servings }}人份
                  </span>
                  <span>
                    <el-icon>
                      <Clock />
                    </el-icon>
                    {{ recipe.cookingTime }}分钟
                  </span>
                  <span>
                    <el-icon>
                      <StarFilled />
                    </el-icon>
                    {{ recipe.favoriteCount || 0 }}收藏
                  </span>
                </div>

                <div class="recipe-actions">
                  <el-button type="primary" size="small" @click="viewDetail(recipe.id)">
                    查看详情
                  </el-button>
                  <el-button :type="recipe.isFavorited ? 'warning' : 'default'" size="small"
                    :icon="recipe.isFavorited ? 'StarFilled' : 'Star'" @click="toggleFavorite(recipe)">
                    {{ recipe.isFavorited ? '已收藏' : '收藏' }}
                  </el-button>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 分页 -->
        <el-pagination v-model:current-page="pagination.page" :page-size="pagination.size"
          :total="pagination.total" layout="total, prev, pager, next, jumper"
          @current-change="handlePageChange" />
      </div>

      <el-empty v-else description="暂无药膳数据" />
    </el-card>
  </div>
</template>

<script setup>
import message from '@/plugins/message'
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'

import { Search, User, Clock, Picture, StarFilled } from '@element-plus/icons-vue'
import { getRecipeList, getRecommendedRecipes, favoriteRecipe, unfavoriteRecipe, searchRecipes } from '@/api/recipe'
import { resolveRecipeCoverUrl } from '@/utils/mediaUrl'
import { getConstitutionTypes } from '@/api/constitution'

const router = useRouter()

// 数据
const loading = ref(false)
const recipeList = ref([])
const constitutionTypes = ref([])
const effectOptions = [
  '补气养血',
  '健脾养胃',
  '安神助眠',
  '清热解毒',
  '润肺止咳',
  '调理肠胃',
  '增强免疫力',
  '美容养颜',
  '改善体质',
  '滋阴补肾'
]

const searchForm = reactive({
  keyword: '',
  season: '',
  constitutionType: '',
  effect: ''
})

const pagination = reactive({
  page: 1,
  size: 12,
  total: 0
})

// 排序规则：已收藏优先；未收藏内按收藏人数倒序；相同则保持原顺序
const sortRecipes = (list) => {
  const arr = Array.isArray(list) ? list : []
  return arr
    .map((item, idx) => ({ item, idx }))
    .sort((a, b) => {
      const aFav = a.item?.isFavorited ? 1 : 0
      const bFav = b.item?.isFavorited ? 1 : 0
      if (aFav !== bFav) return bFav - aFav

      const aCount = Number(a.item?.favoriteCount || 0)
      const bCount = Number(b.item?.favoriteCount || 0)
      if (aCount !== bCount) return bCount - aCount

      return a.idx - b.idx
    })
    .map(({ item }) => item)
}

// 季节名称映射
const seasonNames = {
  'SPRING': '春季',
  'SUMMER': '夏季',
  'AUTUMN': '秋季',
  'WINTER': '冬季',
  'ALL': '四季'
}

// 体质名称映射
const constitutionNames = {
  'PINGHE': '平和质',
  'QIXU': '气虚质',
  'YANGXU': '阳虚质',
  'YINXU': '阴虚质',
  'TANSHI': '痰湿质',
  'SHIRE': '湿热质',
  'XUEYU': '血瘀质',
  'QIYU': '气郁质',
  'TEBING': '特禀质'
}

// 获取季节名称
const getSeasonName = (season) => {
  return seasonNames[season] || season
}

// 获取体质名称
const getConstitutionName = (type) => {
  return constitutionNames[type] || type
}

// 加载体质类型
const loadConstitutionTypes = async () => {
  try {
    const res = await getConstitutionTypes()
    if (res.code === 200) {
      constitutionTypes.value = res.data
    }
  } catch (error) {

  }
}

// 加载推荐药膳列表（包含AI推荐理由）
const loadRecommendedList = async () => {
  try {
    loading.value = true
    const params = {
      pageNum: pagination.page,
      pageSize: pagination.size,
      season: searchForm.season || undefined
    }

    const res = await getRecommendedRecipes(params)
    if (res.code === 200) {
      recipeList.value = sortRecipes(res.data.records || res.data || [])
      pagination.total = res.data.total != null ? Number(res.data.total) : (res.data.length || 0)
      message.success('推荐药膳已刷新')
    }
  } catch (error) {
    console.error('加载推荐药膳列表失败', error)
    message.error('加载推荐药膳失败')
  } finally {
    loading.value = false
  }
}

const hasAdvancedFilters = () => {
  return Boolean(
    (searchForm.keyword && searchForm.keyword.trim() !== '') ||
    (searchForm.season && searchForm.season !== '') ||
    (searchForm.constitutionType && searchForm.constitutionType !== '') ||
    (searchForm.effect && searchForm.effect !== '')
  )
}

// 加载药膳列表（默认显示全部，不包含AI推荐）
const loadRecipeList = async () => {
  try {
    loading.value = true
    const useSearch = hasAdvancedFilters()
    if (useSearch) {
      const res = await searchRecipes({
        keyword: searchForm.keyword || undefined,
        season: searchForm.season || undefined,
        constitutionType: searchForm.constitutionType || undefined,
        effect: searchForm.effect || undefined,
        pageNum: pagination.page,
        pageSize: pagination.size
      })
      if (res.code === 200) {
        recipeList.value = sortRecipes(res.data.records || res.data || [])
        pagination.total = res.data.total != null ? Number(res.data.total) : (res.data.length || 0)
      }
    } else {
      // 否则使用获取全部药膳列表接口
      const params = {
        pageNum: pagination.page,
        pageSize: pagination.size
      }
      const res = await getRecipeList(params)
      if (res.code === 200) {
        recipeList.value = sortRecipes(res.data.records || res.data || [])
        pagination.total = res.data.total != null ? Number(res.data.total) : (res.data.length || 0)
      }
    }
  } catch (error) {
    console.error('加载药膳列表失败', error)
    message.error('加载药膳列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pagination.page = 1
  loadRecipeList()
}

// 重置
const handleReset = () => {
  searchForm.keyword = ''
  searchForm.season = ''
  searchForm.constitutionType = ''
  searchForm.effect = ''
  handleSearch()
}

// 页码改变
const handlePageChange = () => {
  loadRecipeList()
}

// 查看详情
const viewDetail = (id) => {
  router.push(`/patient/recipe/detail/${id}`)
}

// 切换收藏
const toggleFavorite = async (recipe) => {
  try {
    if (recipe.isFavorited) {
      const res = await unfavoriteRecipe(recipe.id)
      if (res.code === 200) {
        message.success('取消收藏成功')
        recipe.isFavorited = false
        recipeList.value = sortRecipes(recipeList.value)
      } else {
        message.error(res.message || '取消收藏失败')
      }
    } else {
      const res = await favoriteRecipe(recipe.id)
      if (res.code === 200) {
        message.success('收藏成功')
        recipe.isFavorited = true
        recipe.favoriteCount = Number(recipe.favoriteCount || 0) + 1
        recipeList.value = sortRecipes(recipeList.value)
      } else {
        message.error(res.message || '收藏失败')
      }
    }
  } catch (error) {

    message.error(error.response?.data?.message || '操作失败')
  }
}

// 页面加载
onMounted(() => {
  loadConstitutionTypes()
  loadRecipeList()
})
</script>

<style scoped>
.recipe-list {
  padding: 20px;
}

.recipe-list .el-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-card .el-form {
  margin-bottom: 0;
}

.loading-container {
  padding: 40px;
}

.recipe-card {
  height: 100%;
  transition: transform 0.3s;
}

.recipe-card:hover {
  transform: translateY(-5px);
}

.recipe-image {
  position: relative;
  height: 200px;
  overflow: hidden;
}

.recipe-image :deep(.el-image) {
  width: 100%;
  height: 100%;
}

.recipe-image :deep(.el-image__inner) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.recipe-image :deep(.image-slot) {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 100%;
  background: #f5f7fa;
  color: #909399;
  font-size: 30px;
}

.recipe-tags {
  position: absolute;
  top: 10px;
  right: 10px;
}

.recipe-tags .el-tag {
  margin-left: 5px;
}

.recipe-content {
  padding: 15px;
}

.recipe-content h3 {
  font-size: 16px;
  margin-bottom: 10px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recipe-efficacy {
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
  height: 40px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  margin-bottom: 10px;
}

.recommendation-reason {
  margin-bottom: 10px;
}

.recommendation-reason :deep(.el-alert) {
  padding: 8px 12px;
}

.recommendation-reason :deep(.el-alert__title) {
  font-size: 12px;
  line-height: 1.5;
}

.recipe-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  margin-bottom: 15px;
}

.recipe-meta span {
  display: flex;
  align-items: center;
  gap: 5px;
}

.recipe-actions {
  display: flex;
  gap: 10px;
}

.recipe-actions .el-button {
  flex: 1;
}

.el-pagination {
  margin-top: 20px;
  justify-content: center;
}
</style>
