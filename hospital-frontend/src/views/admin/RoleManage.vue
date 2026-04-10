<template>
  <div class="role-manage-container">
    <div class="admin-card">
      <div class="card-header">
        <h2>
          <el-icon>
            <UserFilled />
          </el-icon>
          角色管理
        </h2>
        <div class="header-actions">
          <div class="admin-search">
            <el-input v-model="searchText" placeholder="搜索用户名或手机号" clearable @input="handleSearch">
              <template #prefix>
                <el-icon>
                  <Search />
                </el-icon>
              </template>
            </el-input>
          </div>
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
            添加用户
          </el-button>
        </div>
      </div>
      <div class="card-body">

        <!-- 筛选条件 -->
        <div class="admin-filter">
          <el-form inline>
            <el-form-item label="账号状态">
              <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 150px" @change="loadUsers">
                <el-option label="启用" :value="1" />
                <el-option label="禁用" :value="0" />
              </el-select>
            </el-form-item>
          </el-form>
        </div>

        <!-- 用户列表 -->
        <div class="table-responsive">
        <el-table :data="users" v-loading="loading" class="admin-table" stripe empty-text="暂无数据">
          <el-table-column type="index" label="序号" width="70" align="center" fixed="left"/>
          
          <!-- 头像列 -->
          <el-table-column label="头像" width="80" align="center" fixed="left">
            <template #default="{ row }">
              <el-avatar 
                :size="50" 
                :src="getAvatarUrl(row)" 
                @error="handleAvatarError($event, row)"
              >
                <el-icon><UserFilled /></el-icon>
              </el-avatar>
            </template>
          </el-table-column>
          
          <el-table-column prop="username" label="用户名" min-width="120" align="center" fixed="left">
            <template #default="{ row }">
              <div style="font-weight: 600; color: #303133;">{{ row.username || '未设置' }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="realName" label="真实姓名" min-width="110" align="center">
            <template #default="{ row }">
              <span style="font-weight: 500;">{{ row.realName || '未设置' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="phone" label="手机号" width="140" align="center">
            <template #default="{ row }">
              <span style="font-family: monospace;">{{ row.phone || '未设置' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span style="color: #409eff;">{{ row.email || '未设置' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="gender" label="性别" width="80" align="center">
            <template #default="{ row }">
              <span :class="['status-tag', row.gender === 1 ? 'primary' : row.gender === 2 ? 'danger' : 'info']">
                <span class="status-dot"></span>
                {{ getGenderText(row.gender) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="birthDate" label="出生日期" width="120" align="center">
            <template #default="{ row }">
              <span style="font-size: 12px; color: #666;">{{ row.birthDate || '未设置' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="roleType" label="角色" width="110" align="center">
            <template #default="{ row }">
              <span :class="['status-tag', getRoleTypeClass(row.roleType)]">
                <span class="status-dot"></span>
                {{ getRoleText(row.roleType) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="90" align="center">
            <template #default="{ row }">
              <span :class="['status-tag', row.status === 1 ? 'success' : 'danger']">
                <span class="status-dot"></span>
                {{ row.status === 1 ? '启用' : '停用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="loginCount" label="登录" width="70" align="center" >
            <template #default="{ row }">
              <span style="color: #409eff; font-weight: 600;">{{ row.loginCount || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="420" fixed="right" align="center">
            <template #default="{ row }">
              <el-button size="small" @click="viewDetail(row)">
                <el-icon>
                  <View />
                </el-icon>
                详情
              </el-button>
              <el-button size="small" type="primary" @click="editUser(row)">
                <el-icon>
                  <Edit />
                </el-icon>
                编辑
              </el-button>
              <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(row)">
                <el-icon>
                  <Switch />
                </el-icon>
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
              <el-button size="small" type="danger" @click="resetPassword(row)">
                <el-icon>
                  <Key />
                </el-icon>
                重置密码
              </el-button>
              <el-button size="small" type="danger" @click="forceOffline(row)">
                <el-icon>
                  <SwitchButton />
                </el-icon>
                强制下线
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        </div>

        <!-- 分页 -->
        <div class="admin-pagination" v-if="total > 0">
          <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.pageSize" :total="total"
            :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next" @size-change="loadUsers"
            @current-change="loadUsers" />
        </div>
      </div>
    </div>

    <!-- 添加/编辑用户弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" placeholder="请输入用户名" @blur="handleFieldBlur('username')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="真实姓名" prop="realName">
              <el-input v-model="form.realName" placeholder="请输入真实姓名" @blur="handleFieldBlur('realName')" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入手机号" @blur="handleFieldBlur('phone')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="form.email" placeholder="请输入邮箱" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="性别">
              <el-radio-group v-model="form.gender">
                <el-radio :label="1">男</el-radio>
                <el-radio :label="2">女</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出生日期">
              <el-date-picker v-model="form.birthday" type="date" placeholder="选择出生日期" style="width: 100%"
                format="YYYY-MM-DD" value-format="YYYY-MM-DD" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="角色类型" prop="roleType">
          <el-select v-model="form.roleType" placeholder="选择角色" style="width: 100%">
            <el-option label="患者" :value="0" />
            <el-option label="管理员" :value="1" />
          </el-select>
          <div style="font-size: 12px; color: #909399; margin-top: 4px;">当前系统仅保留患者/管理员两种角色</div>
        </el-form-item>

        <el-form-item label="身份证号">
          <el-input v-model="form.idCard" placeholder="请输入身份证号" />
        </el-form-item>

        <el-form-item label="所在地区">
          <el-cascader v-model="form.region" :props="cascaderProps" placeholder="请选择省/市/区" style="width: 100%"
            clearable @change="handleRegionChange" />
        </el-form-item>

        <el-form-item label="详细地址">
          <el-input v-model="form.detailAddress" type="textarea" :rows="2" placeholder="请输入详细地址" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>

    <!-- 用户详情弹窗 -->
    <el-dialog v-model="detailVisible" title="用户详情" width="700px">
      <el-descriptions :column="2" border v-if="selectedUser">
        <el-descriptions-item label="用户ID" :span="2">
          <span>{{ selectedUser.id }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="用户名">{{ selectedUser.username }}</el-descriptions-item>
        <el-descriptions-item label="真实姓名">{{ selectedUser.realName }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ selectedUser.phone }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ selectedUser.email || '未设置' }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ getGenderText(selectedUser.gender) }}</el-descriptions-item>
        <el-descriptions-item label="出生日期">{{ selectedUser.birthDate || '未设置' }}</el-descriptions-item>
        <el-descriptions-item label="身份证号">{{ selectedUser.idCard || '未设置' }}</el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">{{ selectedUser.address || '未设置' }}</el-descriptions-item>
        <el-descriptions-item label="角色类型">
          <el-tag :type="getRoleTypeClass(selectedUser.roleType)">
            {{ getRoleText(selectedUser.roleType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="账号状态">
          <el-tag :type="selectedUser.status === 1 ? 'success' : 'danger'">
            {{ selectedUser.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="登录次数">{{ selectedUser.loginCount || 0 }}次</el-descriptions-item>
        <el-descriptions-item label="最后登录">{{ selectedUser.lastLoginTime || '从未登录' }}</el-descriptions-item>
        <el-descriptions-item label="注册时间" :span="2">{{ selectedUser.createTime }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="editUser(selectedUser)">编辑用户</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import message from '@/plugins/message'
import { ref, reactive, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import { Search, Plus, Refresh, UserFilled, View, Edit, Switch, Key, SwitchButton } from '@element-plus/icons-vue'
import { getUserList, addUser, updateUser, resetUserPassword, updateUserStatus, forceLogout } from '@/api/system'
import { refreshUserCache } from '@/api/cache'
import { getProvinceList, getCityList, getCountyList } from '@/api/area'
import { useFormValidation } from '@/composables/useFormValidation'

const loading = ref(false)
const detailVisible = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('添加用户')
const isEdit = ref(false)
const searchText = ref('')
const selectedUser = ref(null)
const formRef = ref(null)

// 创建字段失焦处理函数
const { handleFieldBlur } = useFormValidation(formRef)

// 查询参数 - 仅显示患者(0)和管理员(1)
const queryParams = reactive({
  page: 1,
  pageSize: 10,
  roleType: '0,1', // 固定为患者(0)和管理员(1)
  status: null
})

// 数据
const users = ref([])
const total = ref(0)

// 表单数据
const form = reactive({
  id: null,
  username: '',
  realName: '',
  phone: '',
  email: '',
  gender: 0,
  birthday: '',
  roleType: 0, // 默认为患者
  status: 1,
  idCard: '',
  region: [], // 省市区级联选择 [provinceId, cityId, countyId]
  regionNames: [], // 省市区名称 [provinceName, cityName, countyName]，用于保存地址
  detailAddress: '' // 详细地址
})

// 级联选择器配置（懒加载）
const cascaderProps = {
  lazy: true,
  lazyLoad: async (node, resolve) => {
    try {
      let data = []
      const { level, value } = node

      if (level === 0) {
        // 加载省份列表
        const res = await getProvinceList()
        if (res.code === 200 && res.data) {
          data = res.data.map(item => ({
            value: item.id,
            label: item.name,
            leaf: false // 省份不是叶子节点，还有城市
          }))
        }
      } else if (level === 1) {
        // 加载城市列表
        const res = await getCityList(value)
        if (res.code === 200 && res.data) {
          data = res.data.map(item => ({
            value: item.id,
            label: item.name,
            leaf: false // 城市不是叶子节点，还有区县
          }))
        }
      } else if (level === 2) {
        // 加载区县列表
        const res = await getCountyList(value)
        if (res.code === 200 && res.data) {
          data = res.data.map(item => ({
            value: item.id,
            label: item.name,
            leaf: true // 区县是叶子节点（通常只需要省市区三级）
          }))
        }
      }

      resolve(data)
    } catch (error) {
      console.error('加载区域数据失败:', error)
      message.error('加载区域数据失败')
      resolve([])
    }
  }
}

// 处理区域选择变化
const handleRegionChange = async (value) => {
  // 当选择变化时，更新region和regionNames
  form.region = value || []
  
  // 根据选择的ID获取名称，用于显示和保存
  if (value && value.length > 0) {
    try {
      form.regionNames = []
      
      // 获取省份名称
      if (value[0]) {
        const provinceRes = await getProvinceList()
        if (provinceRes.code === 200 && provinceRes.data) {
          const province = provinceRes.data.find(p => p.id === value[0])
          if (province) {
            form.regionNames.push(province.name)
          }
        }
      }
      
      // 获取城市名称
      if (value[1]) {
        const cityRes = await getCityList(value[0])
        if (cityRes.code === 200 && cityRes.data) {
          const city = cityRes.data.find(c => c.id === value[1])
          if (city) {
            form.regionNames.push(city.name)
          }
        }
      }
      
      // 获取区县名称
      if (value[2]) {
        const countyRes = await getCountyList(value[1])
        if (countyRes.code === 200 && countyRes.data) {
          const county = countyRes.data.find(c => c.id === value[2])
          if (county) {
            form.regionNames.push(county.name)
          }
        }
      }
    } catch (error) {
      console.error('获取区域名称失败:', error)
      form.regionNames = []
    }
  } else {
    form.regionNames = []
  }
}

// 表单验证规则
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在3到20个字符', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  roleType: [{ required: true, message: '请选择角色类型', trigger: 'change' }]
}

// 加载用户列表
const loadUsers = async () => {
  loading.value = true
  try {
    const params = {
      ...queryParams,
      searchText: searchText.value
    }
    const res = await getUserList(params)
    if (res.code === 200) {
      users.value = res.data?.records || []
      // 兜底：将接口 total 强制为数字，若缺失则使用 records.length（保证分页条在有数据时可见）
      const apiTotal = Number(res.data?.total)
      total.value = apiTotal > 0 ? apiTotal : (Array.isArray(res.data?.records) ? res.data.records.length : 0)
    }
  } catch (error) {
    message.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索处理
const handleSearch = () => {
  queryParams.page = 1
  loadUsers()
}

// 查看详情
const viewDetail = (row) => {
  selectedUser.value = row
  detailVisible.value = true
}

// 切换状态
const toggleStatus = async (row) => {
  const action = row.status === 1 ? '禁用' : '启用'
  const newStatus = row.status === 1 ? 0 : 1

  try {
    await ElMessageBox.confirm(
      `确定要${action}用户 ${row.realName} 吗？`,
      '确认操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    loading.value = true
    // 调用专门的状态更新API
    const res = await updateUserStatus(row.id, newStatus)

    if (res.code === 200) {
      // 先清除缓存，确保获取最新数据
      try {
        await refreshUserCache()
      } catch (e) {
        // 静默失败
      }
      
      // 立即更新本地数据，确保UI立即响应
      row.status = newStatus
      message.success(`${action}成功`)
      
      // 重新加载列表以确保数据同步（从服务器获取最新数据）
      await loadUsers()
    } else {
      message.error(res.message || `${action}失败`)
    }
  } catch (error) {
    if (error !== 'cancel') {
      message.error(error.response?.data?.message || `${action}失败`)
    }
  } finally {
    loading.value = false
  }
}

// 重置密码
const resetPassword = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要重置用户 ${row.realName} 的密码吗？密码将重置为：123456`,
      '重置密码',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const res = await resetUserPassword(row.id)
    if (res.code === 200) {
      message.success(`密码重置成功，新密码为：${res.data.newPassword || '123456'}`)
      // 清除缓存并重新加载数据（虽然密码重置不影响列表显示，但保持数据同步）
      try {
        await refreshUserCache()
        await loadUsers()
      } catch (e) {
        // 静默失败
      }
    } else {
      message.error(res.message || '密码重置失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      message.error('密码重置失败')
    }
  }
}

// 强制下线
const forceOffline = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要强制下线用户 ${row.realName || row.username} 吗？`,
      '强制下线',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    loading.value = true
    const res = await forceLogout(row.id)
    if (res.code === 200) {
      message.success('已强制下线该用户')
      // 清除缓存并重新加载数据
      try {
        await refreshUserCache()
      } catch (e) {
        // 静默失败
      }
      await loadUsers()
    } else {
      message.error(res.message || '强制下线失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      message.error(error.response?.data?.message || '强制下线失败')
    }
  } finally {
    loading.value = false
  }
}

// 获取性别文本
const getGenderText = (gender) => {
  const textMap = { 1: '男', 2: '女' }
  return textMap[gender] || '未知'
}

// 获取角色类型样式类
const getRoleTypeClass = (roleType) => {
  const typeMap = {
    0: 'warning',   // 患者
    1: 'danger'     // 管理员
  }
  return typeMap[roleType] || ''
}

// 获取角色文本
const getRoleText = (roleType) => {
  const textMap = {
    0: '患者',
    1: '管理员'
  }
  return textMap[roleType] || '未知'
}

// 获取默认头像
const getDefaultAvatar = (row) => {
  if (row.avatar) return row.avatar
  // 使用用户ID生成默认头像
  const seed = row.id || 'user'
  const roleType = row.roleType
  if (roleType === 1) {
    return `https://api.dicebear.com/7.x/shapes/svg?seed=${seed}`
  }
  return `https://api.dicebear.com/7.x/thumbs/svg?seed=${seed}`
}

// 获取头像URL（优先使用row.avatar，如果为空则使用默认头像）
const getAvatarUrl = (row) => {
  if (row.avatar && row.avatar.trim() !== '') {
    return row.avatar
  }
  return getDefaultAvatar(row)
}

// 处理头像加载错误
const handleAvatarError = (event, row) => {
  if (!event?.target) return
  const defaultAvatar = getDefaultAvatar(row)
  // 如果当前src不是默认头像，则替换为默认头像
  if (event.target.src !== defaultAvatar) {
    event.target.src = defaultAvatar
  }
}

// 显示添加弹窗
const showAddDialog = () => {
  isEdit.value = false
  dialogTitle.value = '添加用户'
  resetForm()
  dialogVisible.value = true
}

// 编辑用户
const editUser = async (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑用户'

  // 拆分地址为省市区和详细地址
  let region = []
  let detailAddress = ''
  if (row.address) {
    // 尝试从地址中提取省市区
    const addressParts = await parseAddress(row.address)
    region = addressParts.region
    detailAddress = addressParts.detail
    
    // 如果成功解析出省市区ID，获取名称用于显示
    if (region.length > 0) {
      try {
        form.regionNames = []
        if (region[0]) {
          const provinceRes = await getProvinceList()
          if (provinceRes.code === 200 && provinceRes.data) {
            const province = provinceRes.data.find(p => p.id === region[0])
            if (province) form.regionNames.push(province.name)
          }
        }
        if (region[1]) {
          const cityRes = await getCityList(region[0])
          if (cityRes.code === 200 && cityRes.data) {
            const city = cityRes.data.find(c => c.id === region[1])
            if (city) form.regionNames.push(city.name)
          }
        }
        if (region[2]) {
          const countyRes = await getCountyList(region[1])
          if (countyRes.code === 200 && countyRes.data) {
            const county = countyRes.data.find(c => c.id === region[2])
            if (county) form.regionNames.push(county.name)
          }
        }
      } catch (error) {
        console.error('获取区域名称失败:', error)
      }
    }
  }

  Object.assign(form, {
    id: row.id,
    username: row.username,
    realName: row.realName,
    phone: row.phone,
    email: row.email || '',
    gender: row.gender || 0,
    birthday: row.birthDate || '',
    roleType: row.roleType,
    status: row.status,
    idCard: row.idCard || '',
    region: region,
    detailAddress: detailAddress
  })
  dialogVisible.value = true
}

// 解析地址字符串，尝试从地址中提取省市区ID
// 地址格式：省名+市名+区名+详细地址
const parseAddress = async (address) => {
  if (!address) return { region: [], detail: '' }

  try {
    // 获取所有省份列表
    const provinceRes = await getProvinceList()
    if (provinceRes.code !== 200 || !provinceRes.data) {
      return { region: [], detail: address }
    }

    let regionIds = []
    let remainingAddress = address

    // 1. 尝试匹配省份
    for (const province of provinceRes.data) {
      if (address.startsWith(province.name)) {
        regionIds.push(province.id)
        remainingAddress = address.substring(province.name.length)
        
        // 2. 尝试匹配城市
        const cityRes = await getCityList(province.id)
        if (cityRes.code === 200 && cityRes.data) {
          for (const city of cityRes.data) {
            if (remainingAddress.startsWith(city.name)) {
              regionIds.push(city.id)
              remainingAddress = remainingAddress.substring(city.name.length)
              
              // 3. 尝试匹配区县
              const countyRes = await getCountyList(city.id)
              if (countyRes.code === 200 && countyRes.data) {
                for (const county of countyRes.data) {
                  if (remainingAddress.startsWith(county.name)) {
                    regionIds.push(county.id)
                    remainingAddress = remainingAddress.substring(county.name.length)
                    break
                  }
                }
              }
              break
            }
          }
        }
        break
      }
    }

    // 如果成功解析出省市区，返回ID数组和剩余地址
    if (regionIds.length > 0) {
      return { region: regionIds, detail: remainingAddress.trim() }
    }
  } catch (error) {
    console.error('解析地址失败:', error)
  }

  // 如果解析失败，返回空数组，让用户重新选择
  return { region: [], detail: address }
}

// 重置表单
const resetForm = () => {
  Object.assign(form, {
    id: null,
    username: '',
    realName: '',
    phone: '',
    email: '',
    gender: 0,
    birthday: '',
  roleType: 0, // 默认为患者
    status: 1,
    idCard: '',
    region: [],
    regionNames: [],
    detailAddress: ''
  })
  formRef.value?.clearValidate()
}

// 保存用户
const saveUser = async () => {
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    try {
      // 合并地址：省市区名称 + 详细地址
      // form.region存储的是ID数组 [provinceId, cityId, countyId]
      let fullAddress = ''
      if (form.region && form.region.length > 0) {
        try {
          // 如果已有regionNames，直接使用；否则根据ID获取名称
          let regionNames = form.regionNames || []
          
          // 如果名称数组为空或不完整，根据ID获取名称
          if (regionNames.length !== form.region.length) {
            regionNames = []
            
            // 获取省份名称
            if (form.region[0]) {
              const provinceRes = await getProvinceList()
              if (provinceRes.code === 200 && provinceRes.data) {
                const province = provinceRes.data.find(p => p.id === form.region[0])
                if (province) {
                  regionNames.push(province.name)
                }
              }
            }
            
            // 获取城市名称
            if (form.region[1]) {
              const cityRes = await getCityList(form.region[0])
              if (cityRes.code === 200 && cityRes.data) {
                const city = cityRes.data.find(c => c.id === form.region[1])
                if (city) {
                  regionNames.push(city.name)
                }
              }
            }
            
            // 获取区县名称
            if (form.region[2]) {
              const countyRes = await getCountyList(form.region[1])
              if (countyRes.code === 200 && countyRes.data) {
                const county = countyRes.data.find(c => c.id === form.region[2])
                if (county) {
                  regionNames.push(county.name)
                }
              }
            }
          }
          
          // 拼接完整地址
          const regionStr = regionNames.join('')
          fullAddress = regionStr + (form.detailAddress || '')
        } catch (error) {
          console.error('获取区域名称失败:', error)
          // 如果获取失败，只保存详细地址
          fullAddress = form.detailAddress || ''
        }
      } else {
        fullAddress = form.detailAddress || ''
      }

      const userData = {
        username: form.username,
        realName: form.realName,
        phone: form.phone,
        email: form.email,
        gender: form.gender,
        birthDate: form.birthday, // 注意：前端form用birthday，后端用birthDate
        roleType: form.roleType,
        status: form.status,
        idCard: form.idCard,
        address: fullAddress
      }

      if (isEdit.value) {
        userData.id = form.id
        await updateUser(userData)
        message.success('更新成功')
      } else {
        await addUser(userData)
        message.success('添加成功')
      }

      dialogVisible.value = false
      
      // 清除缓存并重新加载数据
      try {
        await refreshUserCache()
      } catch (e) {
        // 静默失败
      }
      await loadUsers()
    } catch (error) {
      message.error(error.response?.data?.message || '保存失败')
    }
  })
}

// 刷新缓存并重新加载数据
const handleRefresh = async () => {
  try {
    loading.value = true
    await refreshUserCache()
    await loadUsers()
    message.success('刷新成功')
  } catch (error) {
    message.error('刷新失败')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  // 先加载数据，确保页面快速显示
  await loadUsers()
  
  // 异步刷新缓存，确保数据最新（不阻塞页面显示）
  setTimeout(async () => {
    try {
      await refreshUserCache()
      await loadUsers()
    } catch (error) {
      // 静默失败，不影响用户体验
    }
  }, 100)
})
</script>

<style scoped lang="scss">
  // 使用全局admin样式
</style>
