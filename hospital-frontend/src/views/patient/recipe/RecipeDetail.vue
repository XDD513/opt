<template>
  <div class="recipe-detail">
    <el-page-header @back="goBack" content="药膳详情" />

    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="10" animated />
    </div>

    <div v-else-if="recipe" class="detail-content">
      <!-- 基本信息 -->
      <el-card class="info-card">
        <el-row :gutter="20">
          <el-col :span="10">
            <div class="recipe-image">
              <el-image
                :src="getRecipeImage(recipe.image || recipe.imageUrl)"
                :alt="recipe.recipeName"
                fit="cover"
                :lazy="true"
              >
                <template #error>
                  <div class="image-slot">
                    <el-icon><Picture /></el-icon>
                  </div>
                </template>
              </el-image>
            </div>
          </el-col>
          
          <el-col :span="14">
            <div class="recipe-info">
              <h1>{{ recipe.recipeName }}</h1>
              
              <div class="recipe-tags">
                <el-tag 
                  v-for="season in recipe.season?.split(',')" 
                  :key="season"
                  type="success"
                >
                  {{ getSeasonName(season) }}
                </el-tag>
                <el-tag 
                  v-for="type in recipe.constitutionType?.split(',')" 
                  :key="type"
                  type="primary"
                >
                  {{ getConstitutionName(type) }}
                </el-tag>
              </div>

              <el-descriptions :column="2" border>
                <el-descriptions-item label="分类">
                  {{ recipe.category || '未分类' }}
                </el-descriptions-item>
                <el-descriptions-item label="份量">
                  {{ recipe.servings }}人份
                </el-descriptions-item>
                <el-descriptions-item label="烹饪时间">
                  {{ recipe.cookingTime }}分钟
                </el-descriptions-item>
                <el-descriptions-item label="难度">
                  <el-rate 
                    v-model="recipe.difficulty" 
                    disabled 
                    show-score
                    text-color="#ff9900"
                  />
                </el-descriptions-item>
                <el-descriptions-item label="浏览次数">
                  {{ recipe.viewCount || 0 }}次
                </el-descriptions-item>
                <el-descriptions-item label="收藏次数">
                  {{ recipe.favoriteCount || 0 }}次
                </el-descriptions-item>
              </el-descriptions>

              <div class="recipe-actions">
                <el-button 
                  :type="recipe.isFavorited ? 'warning' : 'primary'" 
                  size="large"
                  @click="toggleFavorite"
                >
                  <el-icon><Star /></el-icon>
                  {{ recipe.isFavorited ? '已收藏' : '收藏' }}
                </el-button>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-card>

      <!-- AI推荐理由 -->
      <el-card class="recommendation-card" v-if="recipe.recommendationReason">
        <template #header>
          <div class="card-header">
            <span>为您推荐</span>
            <el-tag type="success" size="small">AI智能推荐</el-tag>
          </div>
        </template>
        <div class="recommendation-content">
          {{ recipe.recommendationReason }}
        </div>
      </el-card>

      <!-- 功效说明 -->
      <el-card class="efficacy-card">
        <template #header>
          <span>功效说明</span>
        </template>
        <div class="efficacy-content">
          {{ recipe.efficacy }}
        </div>
      </el-card>

      <!-- 食材清单 -->
      <el-card class="ingredients-card">
        <template #header>
          <span>食材清单</span>
        </template>
        <el-table :data="ingredients" border>
          <el-table-column prop="name" label="食材名称" width="200" />
          <el-table-column prop="amount" label="用量" width="150" />
          <el-table-column prop="note" label="备注" />
        </el-table>
      </el-card>

      <!-- 制作步骤 -->
      <el-card class="steps-card">
        <template #header>
          <span>制作步骤</span>
        </template>
        <el-timeline>
          <el-timeline-item
            v-for="(step, index) in steps"
            :key="index"
            :timestamp="`步骤 ${index + 1}`"
            placement="top"
          >
            <el-card>
              <p>{{ typeof step === 'string' ? step : step.description || step }}</p>
              <div v-if="typeof step === 'object' && step.image" class="step-image">
                <img :src="step.image" :alt="`步骤${index + 1}`" />
              </div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </el-card>

      <!-- 营养成分 -->
      <el-card class="nutrition-card" v-if="nutrition">
        <template #header>
          <span>营养成分（每份）</span>
        </template>
        <el-row :gutter="20">
          <el-col :span="6">
            <div class="nutrition-item">
              <div class="nutrition-value">{{ nutrition.calories || nutrition.calorie || '--' }}</div>
              <div class="nutrition-label">热量（千卡）</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="nutrition-item">
              <div class="nutrition-value">{{ nutrition.protein || '--' }}</div>
              <div class="nutrition-label">蛋白质（克）</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="nutrition-item">
              <div class="nutrition-value">{{ nutrition.fat || '--' }}</div>
              <div class="nutrition-label">脂肪（克）</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="nutrition-item">
              <div class="nutrition-value">{{ nutrition.carbohydrate || nutrition.carbs || '--' }}</div>
              <div class="nutrition-label">碳水化合物（克）</div>
            </div>
          </el-col>
        </el-row>
      </el-card>

      <!-- 适用症状 -->
      <el-card class="suitable-symptoms-card" v-if="recipe.suitableSymptoms">
        <template #header>
          <span>适用症状</span>
        </template>
        <div class="suitable-symptoms-content">
          {{ recipe.suitableSymptoms }}
        </div>
      </el-card>

      <!-- 禁忌说明 -->
      <el-card class="contraindications-card" v-if="recipe.contraindications">
        <template #header>
          <span>禁忌说明</span>
        </template>
        <el-alert
          :title="recipe.contraindications"
          type="warning"
          :closable="false"
          show-icon
        />
      </el-card>

      <!-- 烹饪小贴士 -->
      <el-card class="tips-card" v-if="recipe.tips">
        <template #header>
          <span>烹饪小贴士</span>
        </template>
        <div class="tips-content">
          <el-icon><InfoFilled /></el-icon>
          {{ recipe.tips }}
        </div>
      </el-card>

      <!-- 注意事项 -->
      <el-card class="notes-card" v-if="recipe.notes">
        <template #header>
          <span>注意事项</span>
        </template>
        <el-alert
          :title="recipe.notes"
          type="warning"
          :closable="false"
          show-icon
        />
      </el-card>
    </div>

    <el-empty v-else description="未找到药膳信息" />
  </div>
</template>

<script setup>
import message from '@/plugins/message'
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { Star, Picture, InfoFilled } from '@element-plus/icons-vue'
import { getRecipeDetail, favoriteRecipe, unfavoriteRecipe } from '@/api/recipe'

const route = useRoute()
const router = useRouter()

// 数据
const loading = ref(false)
const recipe = ref(null)

// 计算属性
const ingredients = computed(() => {
  if (!recipe.value?.ingredients) return []
  try {
    const parsed = JSON.parse(recipe.value.ingredients)
    if (!Array.isArray(parsed)) return []
    return parsed.map((it) => ({
      ...it,
      note: it?.note || it?.remark || ''
    }))
  } catch (error) {
    return []
  }
})

const steps = computed(() => {
  if (!recipe.value?.steps) return []
  try {
    const parsed = JSON.parse(recipe.value.steps)
    // 如果解析后是字符串数组，直接返回
    if (Array.isArray(parsed) && parsed.length > 0 && typeof parsed[0] === 'string') {
      return parsed
    }
    // 如果是对象数组，返回对象数组
    return parsed
  } catch (error) {
    // 如果解析失败，尝试按字符串处理
    if (typeof recipe.value.steps === 'string') {
      try {
        // 尝试按逗号分割
        return recipe.value.steps.split(',').map(s => s.trim())
      } catch {
        return [recipe.value.steps]
      }
    }
    return []
  }
})

const nutrition = computed(() => {
  if (!recipe.value?.nutritionInfo) return null
  try {
    const raw = JSON.parse(recipe.value.nutritionInfo)
    if (!raw || typeof raw !== 'object') return null
    return {
      ...raw,
      calories: raw.calories ?? raw.calorie ?? null,
      protein: raw.protein ?? raw.protein_g ?? null,
      fat: raw.fat ?? raw.fat_g ?? null,
      carbohydrate: raw.carbohydrate ?? raw.carbs ?? raw.carb_g ?? null
    }
  } catch (error) {
    return null
  }
})

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

// 加载药膳详情
const loadRecipeDetail = async () => {
  try {
    loading.value = true
    const res = await getRecipeDetail(route.params.id)
    if (res.code === 200) {
      recipe.value = res.data
    }
  } catch (error) {

    message.error('加载药膳详情失败')
  } finally {
    loading.value = false
  }
}

// 切换收藏
const toggleFavorite = async () => {
  try {
    const recipeId = recipe.value?.id ?? recipe.value?.recipeId ?? route.params.id
    if (!recipeId) {
      message.error('缺少药膳ID')
      return
    }

    if (recipe.value.isFavorited) {
      const res = await unfavoriteRecipe(recipeId)
      if (res.code === 200) {
        message.success('取消收藏成功')
        recipe.value.isFavorited = false
      } else {
        message.error(res.message || '取消收藏失败')
      }
    } else {
      const res = await favoriteRecipe(recipeId)
      if (res.code === 200) {
        message.success('收藏成功')
        recipe.value.isFavorited = true
      } else {
        message.error(res.message || '收藏失败')
      }
    }
  } catch (error) {

    message.error(error.response?.data?.message || '操作失败')
  }
}

// 获取药膳图片URL
const getRecipeImage = (imagePath) => {
  // 如果没有图片路径，返回占位图（使用data URL避免404请求）
  if (!imagePath) {
    return 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjAwIiBoZWlnaHQ9IjE1MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMjAwIiBoZWlnaHQ9IjE1MCIgZmlsbD0iI2Y1ZjVmNSIvPjx0ZXh0IHg9IjUwJSIgeT0iNTAlIiBmb250LXNpemU9IjE4IiBmaWxsPSIjY2NjIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBkeT0iLjNlbSI+6I2v6IaA5Zu+54mHPC90ZXh0Pjwvc3ZnPg=='
  }

  // 如果是完整URL，直接返回
  if (imagePath.startsWith('http://') || imagePath.startsWith('https://')) {
    return imagePath
  }

  // 如果是相对路径，从public/uploads目录加载
  return `/uploads/${imagePath}`
}

// 返回
const goBack = () => {
  router.back()
}

// 页面加载
onMounted(() => {
  loadRecipeDetail()
})
</script>

<style scoped>
.recipe-detail {
  padding: 20px;
}

.el-page-header {
  margin-bottom: 20px;
}

.loading-container {
  padding: 40px;
}

.detail-content {
  max-width: 1200px;
  margin: 0 auto;
}

.detail-content .el-card {
  margin-bottom: 20px;
}

.recipe-image {
  width: 100%;
  height: 400px;
  border-radius: 8px;
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
  font-size: 50px;
}

.recipe-info h1 {
  font-size: 28px;
  margin-bottom: 15px;
  color: #303133;
}

.recipe-tags {
  margin-bottom: 20px;
}

.recipe-tags .el-tag {
  margin-right: 10px;
  margin-bottom: 10px;
}

.recipe-actions {
  margin-top: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.recommendation-content {
  padding: 20px;
  line-height: 2;
  color: #606266;
  background: linear-gradient(135deg, #e3f2fd 0%, #f3e5f5 100%);
  border-radius: 4px;
  border-left: 4px solid #409EFF;
  font-size: 15px;
}

.efficacy-content {
  padding: 20px;
  line-height: 2;
  color: #606266;
  background: #f5f7fa;
  border-radius: 4px;
}

.step-image {
  margin-top: 10px;
}

.step-image img {
  max-width: 100%;
  border-radius: 4px;
}

.nutrition-item {
  text-align: center;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 8px;
}

.nutrition-value {
  font-size: 32px;
  font-weight: bold;
  color: #409EFF;
  margin-bottom: 10px;
}

.nutrition-label {
  font-size: 14px;
  color: #909399;
}

.suitable-symptoms-content {
  padding: 20px;
  line-height: 2;
  color: #606266;
  background: #f0f9ff;
  border-radius: 4px;
  border-left: 4px solid #67C23A;
}

.contraindications-card {
  margin-bottom: 20px;
}

.tips-content {
  padding: 20px;
  line-height: 2;
  color: #606266;
  background: #fff7e6;
  border-radius: 4px;
  border-left: 4px solid #E6A23C;
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.tips-content .el-icon {
  color: #E6A23C;
  font-size: 20px;
  margin-top: 2px;
  flex-shrink: 0;
}
</style>

