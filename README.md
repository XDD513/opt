# 基于 AI 与多模态融合的中医体质辨识与医院预约推荐系统

## 📖 项目简介

本项目是一款结合了**传统中医理论**与**现代人工智能技术**的综合性医疗健康平台。作为毕业设计项目，系统不仅实现了基础的**医院预约挂号**、**医患实时对话**与**健康管理**功能，更核心地引入了**基于 YOLOv8 的 AI 舌诊识别**与 **DeepSeek 大模型智能咨询**，通过多模态融合算法为用户提供精准的体质辨识与个性化的辨证施养建议。

## 🏗️ 项目架构

系统采用前后端分离架构，核心组件包括：

- **后端 (Java)**：Spring Boot 2.7.18 + MyBatis-Plus + Nacos (配置中心) + Redis (二级缓存) + RabbitMQ (消息驱动)
- **前端 (Vue3)**：Vue 3 + Vite + Element Plus + Pinia + ECharts (数据可视化)
- **AI 服务 (Python)**：Flask + YOLOv8 (舌象特征检测) + ONNX Runtime (高性能推理)
- **智能大脑 (LLM)**：接入 DeepSeek API，实现体质报告的专业解读与异步任务生成建议

## ✨ 核心创新点

1. **多模态体质辨识**：结合 66 题标准量表问卷与 AI 舌象特征识别，通过加权融合算法提升判定准确率。
2. **AI 智能健康助理**：集成 LLM 大模型，针对用户的特定体质、季节、地域提供“千人千面”的养生方案。
3. **混合推荐算法**：基于内容的推荐 (CB) 融合中医辨证规则加分项，实现精准的药膳与穴位推荐。
4. **异步任务体验**：AI 建议生成采用任务化执行与状态轮询机制，保障稳定性与可恢复性。

```
hospital/
├── hospital-frontend/              # 前端项目（Vue 3 + Element Plus）
│   ├── src/                        # 源代码
│   ├── public/                     # 静态资源
│   ├── package.json                # 前端依赖配置
│   └── README.md                   # 前端项目说明
│
├── hospital-appointment-system/    # 后端项目（Spring Boot + MyBatis-Plus）
│   ├── src/                        # 源代码
│   ├── sql/                        # 数据库脚本
│   ├── pom.xml                     # Maven 配置
│   └── README.md                   # 后端项目说明
│
└── docs/                           # 项目文档
    ├── 需求分析文档.md
    ├── 系统架构设计.md
    ├── 数据库设计.md
    ├── 接口设计文档.md
    └── ...
```

## 📦 子项目仓库

- **前端仓库**: [hospital-frontend](https://github.com/XDD513/hospital-frontend)
- **后端仓库**: [hospital-appointment-system](https://github.com/XDD513/hospital-appointment-system)

## 🚀 快速开始

### 前端项目

```bash
cd hospital-frontend
npm install
npm run dev
```

详细说明请参考 [前端 README](hospital-frontend/README.md)

### 后端项目

```bash
cd hospital-appointment-system
mvn clean package -DskipTests
java -jar target/hospital-appointment-system-1.0.0.jar
```

详细说明请参考 [后端 README](hospital-appointment-system/README.md)

## 🛠️ 技术栈

### 前端技术栈
- Vue 3.3.4
- Vite 4.4.9
- Element Plus 2.4.1
- Vue Router 4.2.4
- Pinia 2.1.6
- Axios 1.5.0
- ECharts 5.4.3

### 后端技术栈
- Java 17
- Spring Boot 2.7.18
- MyBatis-Plus 3.5.5
- Spring Security 2.7.18
- MySQL 8.0+
- Redis 6.0+
- RabbitMQ 3.8+
- Nacos 2.0+

## ✨ 核心功能

### 患者端
- 🔐 用户认证（注册、登录）
- 🏥 预约挂号（在线预约、号源查询）
- 💬 医患对话（实时聊天、消息推送）
- 🧪 体质测试（中医九种体质测试）
- 📝 健康档案（健康信息管理、打卡记录）
- 📚 养生知识（文章浏览、药膳推荐）
- ⭐ 评价反馈（医生评价、满意度调查）

### 医生端
- 📅 排班管理（排班设置、号源管理）
- 💼 患者管理（患者信息查看、咨询记录）
- 💬 在线咨询（与患者实时对话）
- 📊 数据统计（预约统计、评价统计）
- ⭐ 评价管理（查看患者评价、回复评价）

### 管理员端
- 👥 用户管理（用户信息管理、权限分配）
- 🏥 科室管理（科室信息维护）
- 👨‍⚕️ 医生管理（医生信息、资格审核）
- 📅 排班管理（排班审核、号源管理）
- 📋 预约管理（预约审核、状态管理）
- 📊 数据统计（系统数据统计分析）
- ⚙️ 系统设置（系统配置、数据字典）
- 📜 操作日志（系统操作记录查询）

## 📁 项目文档

详细的项目文档位于 `docs/` 目录：

- [需求分析文档](docs/需求分析文档.md)
- [系统架构设计](docs/系统架构设计.md)
- [数据库设计](docs/数据库设计.md)
- [接口设计文档](docs/接口设计文档.md)
- [用例图文档](docs/用例图文档.md)
- [环境搭建指南](docs/环境搭建指南.md)
- [部署文档](docs/部署文档.md)
- [Nacos 配置说明](docs/nacos/NACOS_SETUP.md)

## 🔧 开发环境

### 前端开发环境
- Node.js 16+
- npm 8+ 或 yarn 1.22+

### 后端开发环境
- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+
- RabbitMQ 3.8+（可选）
- Nacos 2.0+（可选）

## 📦 部署

### 前端部署
参考 [前端 README - 部署章节](hospital-frontend/README.md#-部署)

### 后端部署
参考 [后端 README - 部署章节](hospital-appointment-system/README.md#-部署)

## 🤝 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📄 许可证

MIT License

## 👥 作者

医院预约挂号系统开发团队

## 📧 联系方式

- 项目地址: [GitHub Repository](https://github.com/XDD513/hospital)
- 前端仓库: [hospital-frontend](https://github.com/XDD513/hospital-frontend)
- 后端仓库: [hospital-appointment-system](https://github.com/XDD513/hospital-appointment-system)
- 问题反馈: [GitHub Issues](https://github.com/XDD513/hospital/issues)
