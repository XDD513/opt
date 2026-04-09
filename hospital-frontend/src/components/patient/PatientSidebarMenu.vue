<template>
  <el-menu
    :default-active="activeMenu"
    :collapse="collapsed"
    :collapse-transition="false"
    router
    class="patient-menu"
    @select="handleSelect"
  >
    <el-menu-item index="/patient/home">
      <el-icon>
        <HomeFilled />
      </el-icon>
      <span>首页</span>
    </el-menu-item>

    <el-sub-menu index="constitution">
      <template #title>
        <el-icon>
          <Document />
        </el-icon>
        <span>体质测试</span>
      </template>
      <el-menu-item index="/patient/constitution/smart-test">
        <el-icon>
          <Document />
        </el-icon>
        <span>智能体质测试</span>
      </el-menu-item>
      <el-menu-item index="/patient/constitution/history">
        <el-icon>
          <Document />
        </el-icon>
        <span>测试历史</span>
      </el-menu-item>
    </el-sub-menu>

    <el-sub-menu index="recipe">
      <template #title>
        <el-icon>
          <Food />
        </el-icon>
        <span>药膳推荐</span>
      </template>
      <el-menu-item index="/patient/recipe">
        <el-icon>
          <List />
        </el-icon>
        <span>药膳列表</span>
      </el-menu-item>
      <el-menu-item index="/patient/recipe/my">
        <el-icon>
          <Star />
        </el-icon>
        <span>我的收藏</span>
      </el-menu-item>
    </el-sub-menu>

    <el-sub-menu index="article">
      <template #title>
        <el-icon>
          <Reading />
        </el-icon>
        <span>养生社区</span>
      </template>
      <el-menu-item index="/patient/article">
        <el-icon>
          <CollectionTag />
        </el-icon>
        <span>文章广场</span>
      </el-menu-item>
      <el-menu-item index="/patient/article/publish">
        <el-icon>
          <EditPen />
        </el-icon>
        <span>发布文章</span>
      </el-menu-item>
      <el-menu-item index="/patient/article/my">
        <el-icon>
          <Document />
        </el-icon>
        <span>我的文章</span>
      </el-menu-item>
      <el-menu-item index="/patient/article/my-favorites">
        <el-icon>
          <Star />
        </el-icon>
        <span>收藏文章</span>
      </el-menu-item>
      <el-menu-item index="/patient/article/notifications">
        <el-icon>
          <Bell />
        </el-icon>
        <span>通知中心</span>
      </el-menu-item>
    </el-sub-menu>

    <el-menu-item index="/patient/dialogue">
      <el-icon>
        <ChatLineRound />
      </el-icon>
      <span>智能对话</span>
    </el-menu-item>

    <el-sub-menu index="health">
      <template #title>
        <el-icon>
          <Notebook />
        </el-icon>
        <span>健康档案</span>
      </template>
      <el-menu-item index="/patient/health/profile">
        <el-icon>
          <User />
        </el-icon>
        <span>健康档案</span>
      </el-menu-item>
      <el-menu-item index="/patient/health/plan">
        <el-icon>
          <Calendar />
        </el-icon>
        <span>养生计划</span>
      </el-menu-item>
      <el-menu-item index="/patient/health/checkin">
        <el-icon>
          <CircleCheck />
        </el-icon>
        <span>健康打卡</span>
      </el-menu-item>
      <el-menu-item index="/patient/health/statistics">
        <el-icon>
          <DataAnalysis />
        </el-icon>
        <span>数据统计</span>
      </el-menu-item>
    </el-sub-menu>

    <el-menu-item index="/patient/profile">
      <el-icon>
        <Setting />
      </el-icon>
      <span>个人中心</span>
    </el-menu-item>
  </el-menu>
</template>

<script setup>
import {
  Calendar,
  Bell,
  ChatDotRound,
  ChatLineRound,
  CircleCheck,
  CollectionTag,
  DataAnalysis,
  Document,
  EditPen,
  Food,
  HomeFilled,
  List,
  Notebook,
  Reading,
  Setting,
  Star,
  Tickets,
  User,
  Plus
} from '@element-plus/icons-vue'

const props = defineProps({
  activeMenu: {
    type: String,
    default: ''
  },
  collapsed: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['select'])

const handleSelect = (index) => {
  emit('select', index)
}
</script>

<style scoped>
.patient-menu {
  border-right: none;
}
</style>


