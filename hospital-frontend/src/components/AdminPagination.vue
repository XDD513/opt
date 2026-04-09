<template>
  <div class="admin-pagination">
    <el-pagination v-model:current-page="localCurrentPage" v-model:page-size="localPageSize" :total="total"
      :page-sizes="pageSizes" :hide-on-single-page="hideOnSinglePage" :layout="layout" @size-change="onSizeChange"
      @current-change="onCurrentChange" />
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  // 放宽要求，提供合理默认以避免父组件传值异常导致分页不渲染
  currentPage: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 },
  total: { type: Number, default: 0 },
  pageSizes: { type: Array, default: () => [10, 20, 50, 100] },
  layout: { type: String, default: 'total, sizes, prev, pager, next, jumper' },
  hideOnSinglePage: { type: Boolean, default: false }
})

const emit = defineEmits([
  'update:current-page',
  'update:page-size',
  'size-change',
  'current-change'
])

// 双向绑定代理到内部 el-pagination
const localCurrentPage = computed({
  get: () => props.currentPage,
  set: (val) => emit('update:current-page', val)
})

const localPageSize = computed({
  get: () => props.pageSize,
  set: (val) => emit('update:page-size', val)
})

const onSizeChange = (size) => {
  emit('size-change', size)
}

const onCurrentChange = (page) => {
  emit('current-change', page)
}
</script>

<style scoped>
.admin-pagination {
  margin-top: 15px;
  display: flex;
  justify-content: center;
}
</style>