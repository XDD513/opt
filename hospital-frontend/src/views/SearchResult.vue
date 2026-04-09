<template>
  <div class="search-result-page">
    <div class="search-header">
      <SearchBox v-model="searchKeyword" @search="handleSearch" style="max-width: 600px; margin: 0 auto;" />
    </div>

    <div class="result-container">
      <div class="type-tabs">
        <el-radio-group v-model="activeType" @change="handleTypeChange" size="default">
          <el-radio-button label="all">鍏ㄩ儴</el-radio-button>
          <el-radio-button label="recipe">鑽喅</el-radio-button>
          <el-radio-button label="article">鏂囩珷</el-radio-button>
          <el-radio-button label="acupoint">绌翠綅</el-radio-button>
        </el-radio-group>
      </div>

      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="5" animated />
      </div>

      <div v-else-if="hasResults" class="results-content">
        <!-- 鑽喅缁撴灉 -->
        <div v-if="activeType === 'all' || activeType === 'recipe'" class="result-section">
          <h3 class="section-title">
            <el-icon><Food /></el-icon>
            鑽喅 ({{ recipeTotal }})
          </h3>

          <el-row :gutter="20">
            <el-col v-for="recipe in recipeResults" :key="recipe.id" :xs="24" :sm="12" :md="8" :lg="6">
              <el-card class="result-card" @click="goToRecipeDetail(recipe.id)" shadow="hover">
                <div class="card-content">
                  <el-image :src="recipe.imageUrl" class="card-image" fit="cover" :lazy="true">
                    <template #error>
                      <div class="image-slot">
                        <el-icon><Picture /></el-icon>
                      </div>
                    </template>
                  </el-image>
                  <div class="card-info">
                    <div class="card-title">{{ recipe.recipeName }}</div>
                    <div class="card-desc">{{ recipe.effect || '鏆傛棤鎻忚堪' }}</div>
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <el-pagination
            v-if="activeType === 'recipe' && recipeTotal > pageSize"
            v-model:current-page="recipePage"
            :page-size="pageSize"
            :total="recipeTotal"
            layout="prev, pager, next"
            @current-change="loadRecipeResults"
            style="margin-top: 20px; justify-content: center;"
          />
        </div>

        <!-- 鏂囩珷缁撴灉 -->
        <div v-if="activeType === 'all' || activeType === 'article'" class="result-section">
          <h3 class="section-title">
            <el-icon><Reading /></el-icon>
            鏂囩珷 ({{ articleTotal }})
          </h3>

          <el-row :gutter="20">
            <el-col v-for="article in articleResults" :key="article.id" :xs="24" :sm="12" :md="8">
              <el-card class="result-card article-card" @click="goToArticleDetail(article.id)" shadow="hover">
                <div class="card-content horizontal">
                  <el-image v-if="article.coverImage" :src="article.coverImage" class="card-image-small" fit="cover">
                    <template #error>
                      <div class="image-slot">
                        <el-icon><Picture /></el-icon>
                      </div>
                    </template>
                  </el-image>
                  <div class="card-info">
                    <div class="card-title">{{ article.title }}</div>
                    <div class="card-desc">{{ article.summary || '鏆傛棤鎽樿' }}</div>
                    <div class="card-meta">
                      <el-tag size="small">{{ article.categoryName || '未分类' }}</el-tag>
                      <span class="meta-text">{{ formatTime(article.createdAt) }}</span>
                    </div>
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <el-pagination
            v-if="activeType === 'article' && articleTotal > pageSize"
            v-model:current-page="articlePage"
            :page-size="pageSize"
            :total="articleTotal"
            layout="prev, pager, next"
            @current-change="loadArticleResults"
            style="margin-top: 20px; justify-content: center;"
          />
        </div>

        <!-- 绌翠綅缁撴灉 -->
        <div v-if="activeType === 'all' || activeType === 'acupoint'" class="result-section">
          <h3 class="section-title">
            <el-icon><Location /></el-icon>
            绌翠綅 ({{ acupointTotal }})
          </h3>

          <el-row :gutter="20">
            <el-col v-for="acupoint in acupointResults" :key="acupoint.id" :xs="24" :sm="12" :md="8">
              <el-card class="result-card" @click="goToAcupointDetail(acupoint.id)" shadow="hover">
                <div class="card-content">
                  <div class="card-info">
                    <div class="card-title">{{ acupoint.acupointName }}</div>
                    <div class="card-desc">{{ acupoint.location || '鏆傛棤鎻忚堪' }}</div>
                    <div class="card-meta">
                      <el-tag size="small" v-for="type in acupoint.constitutionTypes" :key="type" style="margin-right: 5px;">
                        {{ type }}
                      </el-tag>
                    </div>
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <el-pagination
            v-if="activeType === 'acupoint' && acupointTotal > pageSize"
            v-model:current-page="acupointPage"
            :page-size="pageSize"
            :total="acupointTotal"
            layout="prev, pager, next"
            @current-change="loadAcupointResults"
            style="margin-top: 20px; justify-content: center;"
          />
        </div>
      </div>

      <el-empty v-else description="鏆傛棤鎼滅储缁撴灉" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Food, Reading, Location, Picture } from '@element-plus/icons-vue'
import SearchBox from '@/components/SearchBox.vue'
import { searchRecipes } from '@/api/recipe'
import { getArticleList } from '@/api/article'
import { searchAcupoints } from '@/api/acupoint'
import dayjs from 'dayjs'

const route = useRoute()
const router = useRouter()

const searchKeyword = ref('')
const activeType = ref('all')
const loading = ref(false)

const pageSize = ref(12)
const recipePage = ref(1)
const articlePage = ref(1)
const acupointPage = ref(1)

const recipeResults = ref([])
const articleResults = ref([])
const acupointResults = ref([])

const recipeTotal = ref(0)
const articleTotal = ref(0)
const acupointTotal = ref(0)

const hasResults = computed(() => {
  return recipeResults.value.length > 0 || articleResults.value.length > 0 || acupointResults.value.length > 0
})

const loadRecipeResults = async () => {
  if (!searchKeyword.value || !searchKeyword.value.trim()) {
    recipeResults.value = []
    recipeTotal.value = 0
    return
  }

  try {
    const res = await searchRecipes({
      keyword: searchKeyword.value.trim(),
      pageNum: recipePage.value,
      pageSize: pageSize.value
    })

    if (res.code === 200) {
      recipeResults.value = res.data?.records || res.data?.list || []
      recipeTotal.value = Number(res.data?.total) || 0
    }
  } catch (e) {
    console.error('鎼滅储鑽喅澶辫触', e)
  }
}

const loadArticleResults = async () => {
  if (!searchKeyword.value || !searchKeyword.value.trim()) {
    articleResults.value = []
    articleTotal.value = 0
    return
  }

  try {
    const res = await getArticleList({
      keyword: searchKeyword.value.trim(),
      pageNum: articlePage.value,
      pageSize: pageSize.value
    })

    if (res.code === 200) {
      articleResults.value = res.data?.records || res.data?.list || []
      articleTotal.value = Number(res.data?.total) || 0
    }
  } catch (e) {
    console.error('鎼滅储鏂囩珷澶辫触', e)
  }
}

const loadAcupointResults = async () => {
  if (!searchKeyword.value || !searchKeyword.value.trim()) {
    acupointResults.value = []
    acupointTotal.value = 0
    return
  }

  try {
    const res = await searchAcupoints({
      keyword: searchKeyword.value.trim(),
      pageNum: acupointPage.value,
      pageSize: pageSize.value
    })

    if (res.code === 200) {
      acupointResults.value = res.data?.records || res.data?.list || []
      acupointTotal.value = Number(res.data?.total) || 0
    }
  } catch (e) {
    console.error('鎼滅储绌翠綅澶辫触', e)
  }
}

const performSearch = async () => {
  if (!searchKeyword.value || !searchKeyword.value.trim()) return

  loading.value = true
  try {
    recipePage.value = 1
    articlePage.value = 1
    acupointPage.value = 1

    if (activeType.value === 'all' || activeType.value === 'recipe') await loadRecipeResults()
    if (activeType.value === 'all' || activeType.value === 'article') await loadArticleResults()
    if (activeType.value === 'all' || activeType.value === 'acupoint') await loadAcupointResults()
  } finally {
    loading.value = false
  }
}

const handleSearch = (keyword) => {
  searchKeyword.value = keyword
  performSearch()
}

const handleTypeChange = () => performSearch()

const goToRecipeDetail = (id) => router.push(`/patient/recipe/detail/${id}`)
const goToArticleDetail = (id) => router.push(`/patient/community/article/${id}`)

const goToAcupointDetail = () => {
  router.push({
    path: '/patient/health/plan/detail',
    query: { planType: 'ACUPOINT' }
  })
}

const formatTime = (time) => {
  if (!time) return ''
  return dayjs(time).format('YYYY-MM-DD')
}

watch(
  () => route.query,
  (newQuery) => {
    if (newQuery.keyword) {
      searchKeyword.value = newQuery.keyword
      if (newQuery.type && ['all', 'recipe', 'article', 'acupoint'].includes(String(newQuery.type))) {
        activeType.value = newQuery.type
      } else {
        activeType.value = 'all'
      }
      performSearch()
    }
  },
  { immediate: true }
)

onMounted(() => {
  if (route.query.keyword) {
    searchKeyword.value = route.query.keyword
    if (route.query.type && ['all', 'recipe', 'article', 'acupoint'].includes(String(route.query.type))) {
      activeType.value = route.query.type
    }
    performSearch()
  }
})
</script>

<style scoped>
.search-result-page {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}
.search-header {
  margin-bottom: 30px;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 8px;
}
.type-tabs {
  margin-bottom: 20px;
  padding: 15px;
  background: #fff;
  border-radius: 4px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}
.loading-container { padding: 40px; }
.results-content { margin-top: 20px; }
.result-section { margin-bottom: 40px; }
.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 2px solid #409eff;
}
.result-card { margin-bottom: 20px; cursor: pointer; transition: all 0.3s; }
.result-card:hover { transform: translateY(-5px); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15); }
.card-content { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 10px; }
.card-content.horizontal { flex-direction: row; align-items: flex-start; text-align: left; }
.card-image { width: 100%; height: 150px; border-radius: 4px; margin-bottom: 10px; }
.card-image-small { width: 120px; height: 80px; border-radius: 4px; flex-shrink: 0; }
.image-slot { display: flex; justify-content: center; align-items: center; width: 100%; height: 100%; background: #f5f7fa; color: #909399; }
.card-info { flex: 1; width: 100%; }
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-desc {
  font-size: 14px;
  color: #606266;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.card-meta { display: flex; align-items: center; gap: 10px; margin-top: 10px; font-size: 12px; color: #909399; }
.article-card .card-content { min-height: 100px; }
</style>
