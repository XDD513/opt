<template>
  <div class="my-recipes">
    <el-page-header @back="goBack" content="我的药膳收藏" />

    <el-card class="favorites-card">
      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="5" animated />
      </div>

      <div v-else-if="favoriteList.length > 0">
        <el-row :gutter="20">
          <el-col 
            :span="8" 
            v-for="item in favoriteList" 
            :key="item.id"
            style="margin-bottom: 20px;"
          >
            <el-card
              class="recipe-card"
              :body-style="{ padding: '0px' }"
              shadow="hover"
            >
              <div class="recipe-image">
                <el-image
                  :src="getRecipeImage(getRecipeData(item).image || getRecipeData(item).imageUrl)"
                  :alt="getRecipeData(item).recipeName"
                  fit="cover"
                  :lazy="true"
                >
                  <template #error>
                    <div class="image-slot">
                      <el-icon><Picture /></el-icon>
                    </div>
                  </template>
                </el-image>
                <div class="favorite-date">
                  收藏于 {{ item.createdAt }}
                </div>
              </div>
              
              <div class="recipe-content">
                <h3>{{ getRecipeData(item).recipeName }}</h3>
                <p class="recipe-efficacy">{{ getRecipeData(item).efficacy }}</p>
                
                <div class="recipe-remark" v-if="item.remark">
                  <el-icon><Document /></el-icon>
                  <span>{{ item.remark }}</span>
                </div>

                <div class="recipe-meta">
                  <span>
                    <el-icon><User /></el-icon>
                    {{ getRecipeData(item).servings }}人份
                  </span>
                  <span>
                    <el-icon><Clock /></el-icon>
                    {{ getRecipeData(item).cookingTime }}分钟
                  </span>
                </div>

                <div class="recipe-actions">
                  <el-button 
                    type="primary" 
                    size="small" 
                    @click="viewDetail(item)"
                  >
                    查看详情
                  </el-button>
                  <el-button 
                    type="danger" 
                    size="small"
                    @click="removeFavorite(item)"
                  >
                    取消收藏
                  </el-button>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 分页 -->
        <el-pagination
          v-model:current-page="pagination.page"
          :page-size="pagination.size"
          :total="pagination.total"
          layout="total, prev, pager, next, jumper"
          @current-change="handlePageChange"
        />
      </div>

      <el-empty v-else description="暂无收藏的药膳">
        <el-button type="primary" @click="goToRecipeList">去发现药膳</el-button>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup>
import message from '@/plugins/message'
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Document, User, Clock, Picture } from '@element-plus/icons-vue'
import { getFavoriteRecipes, unfavoriteRecipe } from '@/api/recipe'

const router = useRouter()

// 数据
const loading = ref(false)
const favoriteList = ref([])

const pagination = reactive({
  page: 1,
  size: 12,
  total: 0
})

// 加载收藏列表
const loadFavorites = async () => {
  try {
    loading.value = true
    const params = {
      pageNum: pagination.page,
      pageSize: pagination.size
    }
    
    const res = await getFavoriteRecipes(params)
    if (res.code === 200) {
      favoriteList.value = res.data.records || res.data
      pagination.total = res.data.total != null ? Number(res.data.total) : (res.data.length || 0)
    }
  } catch (error) {

    message.error('加载收藏列表失败')
  } finally {
    loading.value = false
  }
}

// 页码改变
const handlePageChange = () => {
  loadFavorites()
}

// 查看详情
const viewDetail = (item) => {
  const recipeData = getRecipeData(item)
  const recipeId =
    item?.recipeId ??
    item?.id ??
    item?.targetId ??
    recipeData?.id ??
    recipeData?.recipeId ??
    recipeData?.targetId ??
    item?.recipe?.id ??
    item?.recipe?.recipeId

  if (!recipeId || String(recipeId) === 'undefined') {
    message.error('缺少药膳ID，无法打开详情')
    return
  }

  router.push(`/patient/recipe/detail/${recipeId}`)
}

// 取消收藏
const removeFavorite = async (item) => {
  try {
    await ElMessageBox.confirm('确认取消收藏该药膳？', '提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const recipeData = getRecipeData(item)
    const recipeId =
      item.recipeId ??
      item.id ??
      item.targetId ??
      recipeData?.id ??
      recipeData?.recipeId ??
      recipeData?.targetId ??
      item.recipe?.id ??
      item.recipe?.recipeId ??
      item.recipe?.recipe?.id

    if (!recipeId || String(recipeId) === 'undefined') {
      message.error('无法取消收藏：缺少药膳ID')
      return
    }

    await unfavoriteRecipe(recipeId)
    message.success('取消收藏成功')
    loadFavorites()
  } catch (error) {
    if (error !== 'cancel') {

      message.error('取消收藏失败')
    }
  }
}

// 兼容收藏列表两种数据结构：
// 1) 旧结构：{ recipe: {...} }
// 2) 新结构：直接返回 HerbalRecipe 扁平对象
const getRecipeData = (item) => item?.recipe || item || {}

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

// 去药膳列表
const goToRecipeList = () => {
  router.push('/patient/recipe')
}

// 页面加载
onMounted(() => {
  loadFavorites()
})
</script>

<style scoped>
.my-recipes {
  padding: 20px;
}

.el-page-header {
  margin-bottom: 20px;
}

.favorites-card {
  max-width: 1200px;
  margin: 0 auto;
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

.favorite-date {
  position: absolute;
  bottom: 10px;
  right: 10px;
  background: rgba(0, 0, 0, 0.6);
  color: white;
  padding: 5px 10px;
  border-radius: 4px;
  font-size: 12px;
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

.recipe-remark {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #E6A23C;
  margin-bottom: 10px;
  padding: 8px;
  background: #fdf6ec;
  border-radius: 4px;
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

