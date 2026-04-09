# `yggaame.cn` 生产部署指南（Alibaba Cloud Linux）

> 适用当前项目结构：  
> - 后端：`hospital-appointment-system`  
> - 前端：`hospital-frontend`  
> - AI 服务：`ai-service`（Python Flask）  
> - 配置中心：`Nacos`  
> - 反向代理：`Nginx`（前端容器内）

## 1. 部署目标与访问路径

- 前端入口：`https://yggaame.cn`
- 后端 API：`https://yggaame.cn/api/...`
- AI 接口：`https://yggaame.cn/api/ai/...`
- Nacos 控制台：`http://<ECS_IP>:8848/nacos`

## 2. 服务器准备

### 2.1 ECS 安全组放通端口

- `22`（SSH）
- `80`（HTTP）
- `443`（HTTPS）
- `8848`（Nacos 控制台）

### 2.2 安装 Docker 与 Compose（Alibaba Cloud Linux）

```bash
sudo dnf -y update
sudo dnf -y install dnf-plugins-core
sudo dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo dnf -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
```

> 执行 `usermod` 后重新登录 SSH 一次，避免后续命令都要 `sudo docker ...`。

## 3. 项目目录落盘

建议统一放在 `/opt/hospital`：

```bash
sudo mkdir -p /opt
cd /opt
git clone <你的仓库地址> hospital
```

部署后关键目录应存在：

- `/opt/hospital/deploy`
- `/opt/hospital/hospital-appointment-system`
- `/opt/hospital/hospital-frontend`
- `/opt/hospital/ai-service`（建议放这里）

## 4. 处理 `ai-service` 路径（重要）

当前 `deploy/docker-compose.yml` 中，`ai-service` 的 build context 是：

- `../../新建文件夹/ai-service`（相对 `deploy`）

因此服务器上需要对应路径 `/opt/新建文件夹/ai-service`。  
如果实际代码在 `/opt/hospital/ai-service`，创建软链即可：

```bash
sudo mkdir -p "/opt/新建文件夹"
sudo ln -s /opt/hospital/ai-service "/opt/新建文件夹/ai-service"
```

## 5. 启动容器服务

```bash
cd /opt/hospital/deploy
cp .env.example .env
docker compose up -d --build
docker compose ps
```

查看后端日志：

```bash
docker compose logs -f backend
```

## 6. 导入 Nacos 生产配置（prod）

访问：

- `http://<ECS_IP>:8848/nacos`

在 Nacos 中创建 `prod` 命名空间，然后导入以下 3 个配置：

1. **后端配置**
   - Group: `HOSPITAL_BACKEND`
   - DataId: `hospital-appointment-system-prod.yaml`
   - Type: `yaml`
   - 内容来源：`/opt/hospital/deploy/nacos/hospital-appointment-system-prod.yaml`

2. **前端运行时配置**
   - Group: `HOSPITAL_FRONTEND`
   - DataId: `hospital-frontend-prod.yaml`
   - Type: `yaml`
   - 内容来源：`/opt/hospital/deploy/nacos/hospital-frontend-prod.yaml`

3. **系统设置**
   - Group: `HOSPITAL_BACKEND`
   - DataId: `hospital-system-settings-prod.json`
   - Type: `json`
   - 内容来源：`/opt/hospital/deploy/nacos/hospital-system-settings-prod.json`

## 7. 初始化 MySQL

你提供的 `初始mysql.md` 已包含完整建表 SQL。  
建议先提取成 `init.sql`，然后执行：

```bash
mysql -h <DB_HOST> -P 3306 -u <DB_USER> -p -e "CREATE DATABASE IF NOT EXISTS tcm_health_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -h <DB_HOST> -P 3306 -u <DB_USER> -p tcm_health_system < /opt/hospital/init.sql
```

## 8. 让配置生效（重启业务）

Nacos 配置导入 + MySQL 初始化后，重启后端和前端：

```bash
cd /opt/hospital/deploy
docker compose restart backend frontend
```

## 9. 域名解析配置（`yggaame.cn`）

在域名解析控制台添加：

- `A @ -> <ECS公网IP>`
- `A www -> <ECS公网IP>`

等待解析生效（通常几分钟到几十分钟）。

## 10. 配置 HTTPS（Certbot）

```bash
sudo dnf -y install epel-release
sudo dnf -y install certbot python3-certbot-nginx
sudo certbot --nginx -d yggaame.cn -d www.yggaame.cn
sudo certbot renew --dry-run
```

证书签发后访问：

- `https://yggaame.cn`

## 11. 验收检查清单

- `docker compose ps` 全部为 `Up`
- 前端首页可打开：`https://yggaame.cn`
- 后端接口可通：`https://yggaame.cn/api/...`
- AI 接口可通：`https://yggaame.cn/api/ai/...`
- Nacos 可登录：`http://<ECS_IP>:8848/nacos`
- 后端日志无持续报错（DB/Redis/RabbitMQ 连接正常）

## 12. 常用运维命令

### 查看服务状态

```bash
cd /opt/hospital/deploy
docker compose ps
```

### 查看日志

```bash
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f ai-service
docker compose logs -f nacos
```

### 重启服务

```bash
docker compose restart backend frontend ai-service nacos
```

### 更新代码后重建

```bash
cd /opt/hospital
git pull
cd deploy
docker compose up -d --build
```

### 停止服务

```bash
cd /opt/hospital/deploy
docker compose down
```

## 13. 常见问题排查

- **502/网关错误**：先看 `backend` 是否启动成功，再看反代目标是否可达。
- **前端空白/接口 404**：检查 `hospital-frontend-prod.yaml` 的 `frontend.api-base-url` 是否为 `https://yggaame.cn/api`。
- **后端启动失败**：通常是 Nacos 配置未导入，或数据库/Redis/RabbitMQ 参数不对。
- **AI 接口 404**：确认 `ai-service` 容器已启动，且 Nginx 中 `/api/ai/` 已反代到 `ai-service:5000`。
- **域名无法访问**：检查 DNS 是否生效、安全组是否放行 `80/443`。

## 14. 上线顺序建议

1. 启动 compose（先让基础容器起来）
2. 导入 Nacos `prod` 配置
3. 初始化 MySQL
4. 重启后端/前端
5. 配置 DNS
6. 验证 HTTP
7. 配置 HTTPS
8. 最终验收
