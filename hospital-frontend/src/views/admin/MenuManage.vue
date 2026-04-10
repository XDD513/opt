<template>
  <div class="menu-manage-container">
    <div class="admin-card">
      <div class="card-header">
        <h2>
          <el-icon>
            <Grid />
          </el-icon>
          菜单管理
        </h2>
        <div class="header-actions">
          <el-button @click="handleRefresh">
            <el-icon>
              <Refresh />
            </el-icon>
            刷新
          </el-button>
          <el-button type="primary" @click="showAddDialog">
            <el-icon>
              <Plus />
            </el-icon>
            添加菜单
          </el-button>
        </div>
      </div>
      <div class="card-body">
        <div class="table-responsive">
        <el-table :data="menus" v-loading="loading" class="admin-table" stripe row-key="id" :tree-props="{ children: 'children' }" empty-text="暂无数据">
          <el-table-column type="index" label="序号" width="70" align="center" />
          <el-table-column prop="name" label="菜单名称" min-width="150" align="center" />
          <el-table-column prop="path" label="路由路径" min-width="150" />
          <el-table-column prop="icon" label="图标" width="100" align="center" />
          <el-table-column prop="sort" label="排序" width="80" align="center" />
          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'">
                {{ row.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right" align="center">
            <template #default="{ row }">
              <el-button size="small" @click="editMenu(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="deleteMenu(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Grid, Refresh, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const menus = ref([])

const loadMenus = async () => {
  loading.value = true
  try {
    // TODO: 调用API获取菜单列表
    // const res = await getMenus()
    // menus.value = res.data || []
    menus.value = []
  } catch (error) {
    console.error('加载菜单列表失败:', error)
    ElMessage.error('加载菜单列表失败')
  } finally {
    loading.value = false
  }
}

const handleRefresh = () => {
  loadMenus()
}

const showAddDialog = () => {
  // TODO: 显示添加菜单对话框
}

const editMenu = (row) => {
  // TODO: 编辑菜单
}

const deleteMenu = (row) => {
  // TODO: 删除菜单
}

onMounted(() => {
  loadMenus()
})
</script>

<style scoped lang="scss">
.menu-manage-container {
  // 使用全局admin样式
}
</style>

