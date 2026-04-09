<template>
  <el-card class="hsp-recipe-list" shadow="never" v-animate-on-scroll>
    <template #header>
      <div class="card-header">
        <span>推荐药膳</span>
        <el-button text type="success" @click="goToRecipes">
          查看更多 <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
    </template>
    <el-row :gutter="20" v-loading="loading">
      <el-col :xs="24" :sm="12" :md="8" v-for="recipe in recipes" :key="recipe.id">
        <el-card class="recipe-card" shadow="hover" @click="goToRecipeDetail(recipe.id)">
          <div class="recipe-image">
            <el-image :src="getRecipeImage(recipe.image)" fit="cover" :lazy="true">
              <template #error>
                <div class="image-slot">
                  <el-icon><Picture /></el-icon>
                </div>
              </template>
            </el-image>
          </div>
          <div class="recipe-info">
            <h4>{{ recipe.name }}</h4>
            <p class="recipe-effect">{{ recipe.effect }}</p>
            <p v-if="recipe.recommendationReason" class="recipe-recommendation">
              <el-icon><InfoFilled /></el-icon>
              {{ recipe.recommendationReason }}
            </p>
            <div class="recipe-meta">
              <el-tag size="small">{{ getSeasonName(recipe.season) }}</el-tag>
              <el-tag size="small" type="success">{{ getConstitutionName(recipe.constitution) }}</el-tag>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" v-if="recipes.length === 0 && !loading">
        <el-empty description="暂无推荐药膳" />
      </el-col>
    </el-row>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Picture, InfoFilled } from '@element-plus/icons-vue'

const props = defineProps({
  recipes: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const router = useRouter()

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

// 获取药膳图片URL
const getRecipeImage = (imagePath) => {
  if (!imagePath) {
    return 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjAwIiBoZWlnaHQ9IjE1MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMjAwIiBoZWlnaHQ9IjE1MCIgZmlsbD0iI2Y1ZjVmNSIvPjx0ZXh0IHg9IjUwJSIgeT0iNTAlIiBmb250LXNpemU9IjE4IiBmaWxsPSIjY2NjIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBkeT0iLjNlbSI+6I2v6IaA5Zu+54mHPC90ZXh0Pjwvc3ZnPg=='
  }
  if (imagePath.startsWith('http://') || imagePath.startsWith('https://')) {
    return imagePath
  }
  return `/uploads/${imagePath}`
}

// 获取季节名称
const getSeasonName = (season) => {
  if (!season) return '四季'
  return seasonNames[season] || season
}

// 获取体质名称
const getConstitutionName = (type) => {
  if (!type) return ''
  if (type.includes(',')) {
    return type.split(',').map(t => constitutionNames[t.trim()] || t.trim()).join('、')
  }
  return constitutionNames[type] || type
}

// 跳转药膳列表
const goToRecipes = () => {
  router.push('/patient/recipe')
}

// 跳转药膳详情
const goToRecipeDetail = (id) => {
  router.push(`/patient/recipe/${id}`)
}
</script>

<script>
// 滚动动画指令
export default {
  directives: {
    'animate-on-scroll': {
      mounted(el) {
        const observer = new IntersectionObserver(
          (entries) => {
            entries.forEach((entry) => {
              if (entry.isIntersecting) {
                entry.target.classList.add('animate-in')
                observer.unobserve(entry.target)
              }
            })
          },
          { threshold: 0.1 }
        )
        observer.observe(el)
      }
    }
  }
}
</script>

<style scoped lang="scss">
@import '@/styles/home-variables.scss';

.hsp-recipe-list {
  margin-bottom: $spacing-lg;
  opacity: 0;
  transform: translateY(20px);

  &.animate-in {
    opacity: 1;
    transform: translateY(0);
    transition: all $transition-normal $ease-out;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: bold;
    font-size: 16px;
  }

  .recipe-card {
    margin-bottom: $spacing-lg;
    cursor: pointer;
    transition: all $transition-normal;

    &:hover {
      transform: translateY(-5px);
      box-shadow: $elevation-2;
    }

    .recipe-image {
      height: 180px;
      overflow: hidden;
      border-radius: 4px;
      margin-bottom: $spacing-md;

      .el-image {
        width: 100%;
        height: 100%;
      }

      .image-slot {
        display: flex;
        justify-content: center;
        align-items: center;
        width: 100%;
        height: 100%;
        background: #f5f7fa;
        color: #909399;
        font-size: 30px;
      }
    }

    .recipe-info {
      h4 {
        margin: 0 0 $spacing-xs 0;
        color: $text-primary;
        font-size: 16px;
        font-weight: 600;
      }

      .recipe-recommendation {
        font-size: 12px;
        color: #409EFF;
        line-height: 1.6;
        margin: $spacing-xs 0;
        padding: $spacing-xs;
        background: #ecf5ff;
        border-radius: 4px;
        border-left: 3px solid #409EFF;
        display: flex;
        align-items: flex-start;
        gap: 6px;
      }

      .recipe-recommendation .el-icon {
        margin-top: 2px;
        flex-shrink: 0;
      }

      .recipe-effect {
        margin: 0 0 $spacing-sm 0;
        color: $text-secondary;
        font-size: 14px;
        line-height: 1.5;
      }

      .recipe-meta {
        display: flex;
        gap: $spacing-xs;
      }
    }
  }
}

@media (max-width: $breakpoint-sm) {
  .recipe-card {
    .recipe-image {
      height: 150px;
    }
  }
}
</style>

