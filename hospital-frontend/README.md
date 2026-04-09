# 医院预约挂号系统 - 前端应用

## 📖 项目简介

基于 Vue 3 + Element Plus 的现代化医院预约挂号系统前端应用，提供流畅的用户体验和完整的预约挂号、医患对话、健康管理等功能。

## ✨ 核心功能

### 1. 患者端功能
- 🔐 **用户认证** - 注册、登录、密码找回
- 🏥 **预约挂号** - 在线预约、号源查询、预约管理
- 💬 **医患对话** - 实时聊天、消息推送、文件上传
- 🧪 **体质测试** - 中医九种体质在线测试
- 📝 **健康档案** - 健康信息管理、打卡记录
- 📚 **养生知识** - 文章浏览、药膳推荐、穴位指导
- ⭐ **评价反馈** - 医生评价、满意度调查

### 2. 医生端功能
- 📅 **排班管理** - 排班设置、号源管理
- 💼 **患者管理** - 患者信息查看、咨询记录
- 💬 **在线咨询** - 与患者实时对话
- 📊 **数据统计** - 预约统计、评价统计
- ⭐ **评价管理** - 查看患者评价、回复评价

### 3. 管理员端功能
- 👥 **用户管理** - 用户信息管理、权限分配
- 🏥 **科室管理** - 科室信息维护
- 👨‍⚕️ **医生管理** - 医生信息、资格审核
- 📅 **排班管理** - 排班审核、号源管理
- 📋 **预约管理** - 预约审核、状态管理
- 📊 **数据统计** - 系统数据统计分析
- ⚙️ **系统设置** - 系统配置、数据字典
- 📜 **操作日志** - 系统操作记录查询

## 🏗️ 技术架构

### 核心技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.3.4 | 渐进式JavaScript框架 |
| Vite | 4.4.9 | 下一代前端构建工具 |
| Element Plus | 2.4.1 | Vue 3 UI组件库 |
| Vue Router | 4.2.4 | 官方路由管理器 |
| Pinia | 2.1.6 | 新一代状态管理库 |
| Axios | 1.5.0 | HTTP客户端 |
| ECharts | 5.4.3 | 数据可视化图表库 |
| SockJS + STOMP | - | WebSocket实时通信 |

### 主要依赖

```json
{
  "dependencies": {
    "vue": "^3.3.4",
    "vue-router": "^4.2.4",
    "pinia": "^2.1.6",
    "element-plus": "^2.4.1",
    "@element-plus/icons-vue": "^2.1.0",
    "axios": "^1.5.0",
    "echarts": "^5.4.3",
    "dayjs": "^1.11.10",
    "@stomp/stompjs": "^7.2.1",
    "sockjs-client": "^1.6.1"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^4.3.4",
    "vite": "^4.4.9",
    "sass": "^1.80.0"
  }
}
```

## 📁 项目结构

```
hospital-frontend/
├── public/                          # 静态资源
│   └── uploads/                     # 上传文件目录
├── src/
│   ├── api/                         # API接口定义（25个API文件）
│   │   ├── request.js               # Axios请求封装
│   │   ├── user.js                  # 用户相关接口
│   │   ├── appointment.js           # 预约相关接口
│   │   ├── doctor.js                # 医生相关接口
│   │   ├── conversation.js          # 对话相关接口
│   │   ├── constitution.js          # 体质相关接口
│   │   ├── health.js                # 健康相关接口
│   │   └── ...
│   ├── components/                  # 公共组件
│   │   ├── ChatWidget.vue           # 聊天组件
│   │   ├── AdminPagination.vue      # 分页组件
│   │   ├── doctor/                  # 医生端组件
│   │   │   ├── ConsultationDialog.vue
│   │   │   ├── PatientDetailDialog.vue
│   │   │   └── RecordDetailDialog.vue
│   │   └── patient/                 # 患者端组件
│   │       └── ReviewDialog.vue
│   ├── layout/                      # 布局组件
│   │   ├── PatientLayout.vue        # 患者端布局
│   │   ├── DoctorLayout.vue         # 医生端布局
│   │   └── AdminLayout.vue          # 管理员端布局
│   ├── router/                      # 路由配置
│   │   └── index.js                 # 路由定义
│   ├── stores/                      # Pinia状态管理（5个store）
│   │   ├── user.js                  # 用户状态
│   │   ├── patient.js               # 患者状态
│   │   ├── doctor.js                # 医生状态
│   │   ├── admin.js                 # 管理员状态
│   │   └── notification.js          # 通知状态
│   ├── styles/                      # 样式文件
│   │   ├── admin-common.scss        # 管理员端样式
│   │   ├── admin-variables.scss     # 管理员端变量
│   │   ├── doctor.scss              # 医生端样式
│   │   └── patient.scss             # 患者端样式
│   ├── utils/                       # 工具函数
│   │   ├── region-data.js           # 地区数据
│   │   └── passiveEventPatch.js     # 事件处理补丁
│   ├── views/                       # 页面组件
│   │   ├── Login.vue                # 登录页
│   │   ├── Register.vue             # 注册页
│   │   ├── dialogue/                # 对话相关页面
│   │   │   └── DialogPage.vue       # 对话页面
│   │   ├── patient/                 # 患者端页面（26个页面）
│   │   │   ├── Home.vue             # 首页
│   │   │   ├── Appointment.vue      # 预约挂号
│   │   │   ├── MyAppointments.vue   # 我的预约
│   │   │   ├── ConstitutionTest.vue # 体质测试
│   │   │   ├── HealthProfile.vue    # 健康档案
│   │   │   └── ...
│   │   ├── doctor/                  # 医生端页面（6个页面）
│   │   │   ├── Dashboard.vue        # 工作台
│   │   │   ├── Schedule.vue         # 排班管理
│   │   │   ├── Patients.vue         # 患者管理
│   │   │   ├── ConsultationRecords.vue # 咨询记录
│   │   │   ├── Reviews.vue          # 评价管理
│   │   │   └── Settings.vue         # 个人设置
│   │   └── admin/                   # 管理员端页面（8个页面）
│   │       ├── Dashboard.vue        # 数据统计
│   │       ├── UserManage.vue       # 用户管理
│   │       ├── DoctorManage.vue     # 医生管理
│   │       ├── DepartmentManage.vue # 科室管理
│   │       ├── AppointmentManage.vue # 预约管理
│   │       ├── ScheduleManage.vue   # 排班管理
│   │       ├── Statistics.vue       # 统计分析
│   │       ├── SystemSettings.vue   # 系统设置
│   │       └── OperationLogs.vue    # 操作日志
│   ├── App.vue                      # 根组件
│   └── main.js                      # 入口文件
├── index.html                       # HTML模板
├── vite.config.js                   # Vite配置
├── package.json                     # 项目配置
└── README.md                        # 项目说明
```

## 🚀 快速开始

### 环境要求

- Node.js 16+
- npm 8+ 或 yarn 1.22+

### 1. 安装依赖

```bash
cd hospital-frontend
npm install
# 或使用 yarn
yarn install
```

### 2. 开发环境运行

```bash
npm run dev
# 或
yarn dev
```

访问：http://localhost:5173

### 3. 生产环境构建

```bash
npm run build
# 或
yarn build
```

构建产物在 `dist/` 目录

### 4. 预览生产构建

```bash
npm run preview
# 或
yarn preview
```

## ⚙️ 配置说明

### 运行时配置 & 环境变量

- 构建后的运行时配置由后端 `/api/config` 提供，配置内容在 Nacos 中维护（详见 `docs/NACOS_SETUP.md`）。
- Vite 仅需少量编译期变量（如端口、代理），可按需在 `.env.development.local` 中覆盖：

```bash
# 开发服务器端口
VITE_PORT=5173

# 若需自定义代理目标
VITE_API_BASE_URL=http://localhost:8080
VITE_WS_BASE_URL=ws://localhost:8080
```

其余如 API 地址、请求超时、默认头像、消息时长等均可通过 Nacos 配置并在运行时动态生效。

### Vite 配置

`vite.config.js` 主要配置：

```javascript
export default {
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
}
```

### API 请求配置

`src/api/request.js` 中配置了：
- 请求拦截器（添加Token）
- 响应拦截器（统一错误处理）
- 请求/响应转换

## 🎨 UI 组件

### Element Plus 组件库

项目使用 Element Plus 作为UI组件库，主要使用的组件：
- `el-button` - 按钮
- `el-form` - 表单
- `el-table` - 表格
- `el-dialog` - 对话框
- `el-pagination` - 分页
- `el-upload` - 文件上传
- `el-message` - 消息提示
- `el-notification` - 通知

### 自定义组件

- `ChatWidget` - 聊天组件（WebSocket实时通信）
- `AdminPagination` - 管理员端分页组件
- `ReviewDialog` - 评价对话框
- `PatientDetailDialog` - 患者详情对话框

## 🔌 WebSocket 实时通信

### 连接配置

使用 SockJS + STOMP 协议实现WebSocket通信：

```javascript
import SockJS from 'sockjs-client'
import { Stomp } from '@stomp/stompjs'

// 建立连接
const socket = new SockJS('http://localhost:8080/ws')
const stompClient = Stomp.over(socket)

// 订阅消息
stompClient.subscribe('/user/queue/notification', (message) => {
  // 处理消息
})
```

### 消息类型

- 会话消息推送
- 系统通知
- 预约提醒
- 评价提醒

## 📊 状态管理（Pinia）

### Store 结构

```javascript
// stores/user.js
export const useUserStore = defineStore('user', {
  state: () => ({
    userInfo: null,
    token: null,
  }),
  actions: {
    login() { /* ... */ },
    logout() { /* ... */ },
  },
})
```

### 主要 Store

- `user` - 用户信息和认证状态
- `patient` - 患者端数据状态
- `doctor` - 医生端数据状态
- `admin` - 管理员端数据状态
- `notification` - 通知消息状态

## 🛣️ 路由配置

### 路由守卫

- 登录验证
- 角色权限验证
- 路由懒加载

### 路由结构

```javascript
{
  path: '/patient',
  component: PatientLayout,
  meta: { requiresAuth: true, role: 'PATIENT' },
  children: [
    { path: 'home', component: Home },
    { path: 'appointment', component: Appointment },
    // ...
  ],
}
```

## 📱 响应式设计

项目支持响应式布局，适配：
- 桌面端（1920px+）
- 平板端（768px - 1919px）
- 移动端（< 768px）

## 🎯 功能模块详解

### 1. 预约挂号流程

1. 选择科室/医生
2. 查看排班信息
3. 选择时间段
4. 填写预约信息
5. 确认预约
6. 支付（如需要）
7. 预约成功

### 2. 医患对话流程

1. 创建会话
2. 发送消息
3. 接收实时推送
4. 文件上传
5. 消息历史查询

### 3. 体质测试流程

1. 开始测试
2. 填写问卷（66题）
3. 提交测试
4. 查看结果（九种体质评分）
5. 查看个性化建议

## 🔧 开发工具

### 代码规范

- ESLint 代码检查
- Prettier 代码格式化
- 遵循 Vue 3 官方风格指南

### 浏览器支持

- Chrome (推荐)
- Firefox
- Safari
- Edge

## 📦 部署

### Nginx 配置示例

```nginx
server {
    listen 80;
    server_name your-domain.com;
    
    root /var/www/hospital-frontend/dist;
    index index.html;
    
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
    
    location /ws {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

### Docker 部署

```dockerfile
FROM node:18-alpine as builder
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

## 🐛 常见问题

### 1. 跨域问题

开发环境已在 `vite.config.js` 中配置代理，生产环境需在 Nginx 中配置。

### 2. Token 过期

Token 过期后自动跳转登录页，需重新登录。

### 3. WebSocket 连接失败

检查后端 WebSocket 服务是否启动，以及网络防火墙配置。

## 📚 开发文档

- [Vue 3 文档](https://cn.vuejs.org/)
- [Element Plus 文档](https://element-plus.org/zh-CN/)
- [Vite 文档](https://cn.vitejs.dev/)
- [Pinia 文档](https://pinia.vuejs.org/zh/)

## 🤝 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📄 许可证

MIT License

## 👥 作者

医院预约挂号系统前端开发团队

## 📧 联系方式

- 项目地址: [GitHub Repository](https://github.com/XDD513/hospital-frontend)
- 问题反馈: [GitHub Issues](https://github.com/XDD513/hospital-frontend/issues)
