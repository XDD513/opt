# Nacos 配置中心接入指南

本项目已完成 `.env` → Nacos 的迁移，请按照以下步骤在不同环境中接入配置中心。

## 1. 启动 Nacos

1. 使用官方 `docker-compose` 或二进制方式启动 Nacos（推荐 2.3.x）。
2. 创建命名空间，例如：
   - `dev`：本地/测试
   - `staging`
   - `prod`

## 2. 创建数据 ID

| 作用域 | dataId 模板 | Group | 格式 | 说明 |
| --- | --- | --- | --- | --- |
| 后端主配置 | `hospital-appointment-system-${profile}.yaml` | `HOSPITAL_BACKEND` | YAML | 取代原 `.env`，包含数据库、Redis、RabbitMQ、OSS 等配置；`${profile}` 对应 `spring.profiles.active`。 |
| 前端运行时配置 | `hospital-frontend-${profile}.yaml` | `HOSPITAL_FRONTEND` | YAML | 提供给 `/api/config` 接口，驱动前端运行时取值。 |

> 其中 `profile` 建议与 `application-{profile}.yml` 保持一致，如 `dev`、`prod`。

## 3. 导入示例配置

项目提供了默认样例，可直接上传到对应命名空间：

```
docs/nacos/hospital-appointment-system-dev.yaml   # 后端
docs/nacos/hospital-frontend-dev.yaml             # 前端运行时
```

根据实际环境修改数据库、Redis、RabbitMQ、阿里云密钥等敏感字段。

## 4. 后端配置说明

1. `pom.xml` 已引入 `spring-cloud-starter-alibaba-nacos-config`，`bootstrap.yml` 负责连接 Nacos。
2. 运行应用时需提供以下最小环境变量（或通过 JVM 参数）：

```
NACOS_SERVER_ADDR=127.0.0.1:8848
NACOS_NAMESPACE=dev
```

3. `application-dev.yml` 中的 `${db.host}`、`${redis.host}` 等占位符由 Nacos 注入。缺失时会回落到写在 YAML 中的默认值。
4. `extension-configs` 将 `hospital-frontend-${profile}.yaml` 合并到同一上下文，供 `FrontendConfigService` 读取。

## 5. 前端运行时配置

1. Vue 应用在 `src/main.js` 启动前调用 `/api/config` 获取配置，包括：
   - `apiBaseUrl`、`wsBaseUrl`
   - `requestTimeout`
   - `messageDuration`
   - `defaultAvatars`
2. `docs/nacos/hospital-frontend-dev.yaml` 中的 `frontend` 区块即上述字段，可按环境独立调整。
3. 运行失败时会回退到代码内的安全默认值，但建议确保 Nacos 可用以便集中管理。

## 6. 验证与回退

1. 启动 Nacos → 导入配置 → `mvn spring-boot:run`，确认 `/api/config` 返回期望内容。
2. 前端 `npm run dev` 后访问页面，检查 axios 请求、消息提示时长、默认头像等是否随 Nacos 修改而更新。
3. 如需回退，可暂时在 `application-*.yml` 写死对应值，但仍建议尽快恢复 Nacos 作为单一配置源。

至此，前后端均已通过 Nacos 实现集中管理。更多细节可参考 `bootstrap.yml` 与 `FrontendConfigController` 的实现。

