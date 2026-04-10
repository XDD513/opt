# Nacos 配置文件说明

## 重要提示

⚠️ **请勿将包含真实密钥的配置文件提交到 Git 仓库！**

## 配置文件

- `hospital-appointment-system-dev.example.yaml` - 后端开发环境配置模板
- `hospital-frontend-dev.example.yaml` - 前端开发环境配置模板

## 使用方法

1. 复制示例文件并重命名：
   ```bash
   cp hospital-appointment-system-dev.example.yaml hospital-appointment-system-dev.yaml
   cp hospital-frontend-dev.example.yaml hospital-frontend-dev.yaml
   ```

2. 编辑配置文件，替换所有 `${变量名:默认值}` 为实际值

3. 确保 `.gitignore` 已配置，排除 `*-dev.yaml` 和 `*-prod.yaml` 文件

## 环境变量

配置文件支持使用环境变量，格式：`${ENV_VAR:default_value}`

例如：
- `${DB_PASSWORD:your_db_password}` - 数据库密码
- `${OSS_ACCESS_KEY_ID:your_access_key_id}` - OSS AccessKey ID
- `${JWT_SECRET:your_jwt_secret_key}` - JWT 密钥

## 安全建议

1. 所有包含敏感信息的配置文件都应添加到 `.gitignore`
2. 使用环境变量或配置中心（如 Nacos）管理敏感配置
3. 定期轮换密钥和密码
4. 不要在代码中硬编码敏感信息

