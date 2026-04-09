import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getAppConfig } from '@/config/runtimeConfig'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册' }
  },
  {
    path: '/search',
    name: 'SearchResult',
    component: () => import('@/views/SearchResult.vue'),
    meta: { title: '搜索结果' }
  },

  // ==================== 患者端路由 ====================
  {
    path: '/patient',
    component: () => import('@/layout/PatientLayout.vue'),
    redirect: '/patient/home',
    meta: { roles: [0] }, // 患者端仅允许患者（roleType=0）
    children: [
      {
        path: 'home',
        name: 'PatientHome',
        component: () => import('@/views/patient/Home.vue'),
        meta: { title: '首页', roles: [0] },
      },
      {
        path: 'profile',
        name: 'PatientProfile',
        component: () => import('@/views/patient/Profile.vue'),
        meta: { title: '个人中心', roles: [0] }
      },
      {
        path: 'dialogue',
        name: 'PatientDialogue',
        component: () => import('@/views/dialogue/DialogPage.vue'),
        meta: { title: '智能对话', roles: [0] }
      },

      // ==================== 体质测试模块 ====================
      // 传统测试路由重定向到智能测试
      {
        path: 'constitution/test',
        redirect: to => {
          // 保留查询参数（如appointmentId）
          return {
            path: '/patient/constitution/smart-test',
            query: to.query
          }
        }
      },
      {
        path: 'constitution/result/:id?',
        name: 'TestResult',
        component: () => import('@/views/patient/constitution/TestResult.vue'),
        meta: { title: '测试结果', roles: [0] }
      },
      {
        path: 'constitution/history',
        name: 'TestHistory',
        component: () => import('@/views/patient/constitution/TestHistory.vue'),
        meta: { title: '测试历史', roles: [0] }
      },
      {
        path: 'constitution/history/detail/:id',
        name: 'TestHistoryDetail',
        component: () => import('@/views/patient/constitution/TestHistoryDetail.vue'),
        meta: { title: '测试历史详情', roles: [0] }
      },
      {
        path: 'constitution/smart-test',
        name: 'SmartConstitutionTest',
        component: () => import('@/views/patient/constitution/SmartConstitutionTest.vue'),
        meta: { title: '智能体质测试', roles: [0], noAuth: true }
      },

      // ==================== 药膳推荐模块 ====================
      {
        path: 'recipe',
        name: 'RecipeList',
        component: () => import('@/views/patient/recipe/RecipeList.vue'),
        meta: { title: '药膳推荐', roles: [0] }
      },
      {
        path: 'recipe/detail/:id',
        name: 'RecipeDetail',
        component: () => import('@/views/patient/recipe/RecipeDetail.vue'),
        meta: { title: '药膳详情', roles: [0] }
      },
      {
        path: 'recipe/my',
        name: 'MyRecipes',
        component: () => import('@/views/patient/recipe/MyRecipes.vue'),
        meta: { title: '我的药膳', roles: [0] }
      },
      {
        path: 'article',
        name: 'ArticleList',
        component: () => import('@/views/patient/article/ArticleList.vue'),
        meta: { title: '养生社区', roles: [0] }
      },
      {
        path: 'article/detail/:id',
        name: 'ArticleDetail',
        component: () => import('@/views/patient/article/ArticleDetail.vue'),
        meta: { title: '文章详情', roles: [0] }
      },
      {
        path: 'article/publish',
        name: 'ArticlePublish',
        component: () => import('@/views/patient/article/ArticlePublish.vue'),
        meta: { title: '发布文章', roles: [0] }
      },
      {
        path: 'article/edit/:id',
        name: 'ArticleEdit',
        component: () => import('@/views/patient/article/ArticlePublish.vue'),
        meta: { title: '编辑文章', roles: [0] }
      },
      {
        path: 'article/my',
        name: 'MyArticles',
        component: () => import('@/views/patient/article/MyArticles.vue'),
        meta: { title: '我的文章', roles: [0] }
      },
      {
        path: 'article/my-favorites',
        name: 'MyArticleFavorites',
        component: () => import('@/views/patient/article/MyFavorites.vue'),
        meta: { title: '我的收藏文章', roles: [0] }
      },
      {
        path: 'article/notifications',
        name: 'ArticleNotifications',
        component: () => import('@/views/patient/article/ArticleNotifications.vue'),
        meta: { title: '文章通知中心', roles: [0] }
      },

      // ==================== 健康档案模块 ====================
      {
        path: 'health/profile',
        name: 'HealthProfile',
        component: () => import('@/views/patient/health/HealthProfile.vue'),
        meta: { title: '健康档案', roles: [0] }
      },
      {
        path: 'health/plan',
        name: 'HealthPlanHistory',
        component: () => import('@/views/patient/health/HealthPlanHistory.vue'),
        meta: { title: '健康计划', roles: [0] }
      },
      {
        path: 'health/plan/detail',
        name: 'HealthPlanDetail',
        component: () => import('@/views/patient/health/HealthPlan.vue'),
        meta: { title: '计划详情', roles: [0] }
      },
      {
        path: 'health/checkin',
        name: 'HealthCheckin',
        component: () => import('@/views/patient/health/HealthCheckin.vue'),
        meta: { title: '健康打卡', roles: [0] }
      },
      {
        path: 'health/statistics',
        name: 'HealthStatistics',
        component: () => import('@/views/patient/health/HealthStatistics.vue'),
        meta: { title: '健康统计', roles: [0] }
      },
    ]
  },

  // ==================== 管理员端路由 ====================
  {
    path: '/admin',
    component: () => import('@/layout/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    meta: { roles: [1] },
    children: [
      {
        path: 'dashboard',
        name: 'AdminDashboardV2',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: '首页', roles: [1] }
      },
      {
        path: 'role',
        name: 'AdminRoleManage',
        component: () => import('@/views/admin/RoleManage.vue'),
        meta: { title: '角色管理', roles: [1] }
      },
      {
        path: 'menu',
        name: 'AdminMenuManage',
        component: () => import('@/views/admin/MenuManage.vue'),
        meta: { title: '菜单管理', roles: [1] }
      },
      // 系统设置相关
      {
        path: 'settings',
        name: 'AdminSettingsV2',
        component: () => import('@/views/admin/SystemSettings.vue'),
        meta: { title: '系统设置', roles: [1] }
      },
      {
        path: 'settings/test',
        name: 'AdminSettingsTestV2',
        component: () => import('@/views/admin/SystemSettingsTest.vue'),
        meta: { title: '设置生效检测', roles: [1] }
      },
      {
        path: 'statistics',
        name: 'AdminStatisticsV2',
        component: () => import('@/views/admin/Statistics.vue'),
        meta: { title: '数据统计', roles: [1] }
      },
      {
        path: 'logs',
        name: 'AdminLogsV2',
        component: () => import('@/views/admin/OperationLogs.vue'),
        meta: { title: '操作日志', roles: [1] }
      },
      {
        path: 'dialogue',
        name: 'AdminDialogueV2',
        component: () => import('@/views/dialogue/DialogPage.vue'),
        meta: { title: '智能对话', roles: [1] }
      },
      {
        path: 'profile',
        name: 'AdminProfileV2',
        component: () => import('@/views/admin/Profile.vue'),
        meta: { title: '个人信息', roles: [1] }
      },
      {
        path: 'article/review',
        name: 'AdminArticleReview',
        component: () => import('@/views/admin/ArticleReview.vue'),
        meta: { title: '文章审核', roles: [1] }
      },
      {
        path: 'article/review/:id',
        name: 'AdminArticleReviewDetail',
        component: () => import('@/views/admin/ArticleReviewDetail.vue'),
        meta: { title: '文章审核详情', roles: [1] }
      },
      {
        path: 'article/manage',
        name: 'AdminArticleManage',
        component: () => import('@/views/admin/ArticleManage.vue'),
        meta: { title: '文章管理', roles: [1] }
      },
      {
        path: 'article/notifications',
        name: 'AdminArticleNotifications',
        component: () => import('@/views/admin/ArticleNotifications.vue'),
        meta: { title: '文章通知中心', roles: [1] }
      }
    ]
  },

  // 默认跳转
  {
    path: '/',
    redirect: '/login'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 根据角色获取首页路径
 */
function getHomeByRole(roleType) {
  // 当前仅保留：管理员（roleType=1）与患者（roleType=0）
  if (roleType === 1) return '/admin/dashboard'
  return '/patient/home'
}

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const appConfig = getAppConfig()
  const systemTitle = appConfig?.systemInfo?.name || '中医体质辨识系统'
  document.title = to.meta.title ? `${to.meta.title} - ${systemTitle}` : systemTitle

  const userStore = useUserStore()
  const roleType = userStore.userInfo?.roleType

  // 白名单路由（不需要登录）
  const whiteList = ['/login', '/register']

  // 1. 未登录
  if (!userStore.token) {
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next('/login')
    }
    return
  }

  // 2. 已登录但角色类型无效，清除登录状态并跳转到登录页
  if (roleType === null || roleType === undefined || ![0, 1].includes(roleType)) {
    if (whiteList.includes(to.path)) {
      // 允许访问登录页，但清除无效token
      userStore.logout()
      next()
    } else {
      userStore.logout()
      next('/login')
    }
    return
  }

  // 3. 已登录，访问登录/注册页，跳转到对应角色首页
  if (to.path === '/login' || to.path === '/register') {
    next(getHomeByRole(roleType))
    return
  }

  // 4. 已登录，访问根路径，跳转到对应角色首页
  if (to.path === '/') {
    next(getHomeByRole(roleType))
    return
  }

  // 5. 权限验证：检查当前路由是否允许该角色访问
  if (to.meta.roles && !to.meta.roles.includes(roleType)) {
    // 无权限，跳转到对应角色的首页
    next(getHomeByRole(roleType))
    return
  }

  // 6. 智能体质测试路由：显式放行
  if (to.path.includes('/patient/constitution/smart-test')) {
    next()
    return
  }

  next()
})

export default router


