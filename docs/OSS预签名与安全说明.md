# OSS 预签名 URL 与安全说明

## 1. 签名 URL 中的 OSSAccessKeyId

阿里云 **经典 URL 签名（V1）** 会在查询参数中包含 **`OSSAccessKeyId`**（或同类字段）。这是 **AccessKey 的公标识**，用于服务端校验签名；**不是 AccessKey Secret**。  
无法在不改变接入方式的前提下从 URL 中去掉该字段；若必须隐藏标识符，需改用 **服务端代理下载**、**CDN 私有鉴权** 或 **STS 临时凭证** 等架构。

## 2. 运维建议（降低风险）

| 措施 | 说明 |
|------|------|
| **RAM 子账号 + 最小权限** | 仅用具备该 Bucket **读**权限的子账号生成预签名，勿使用主账号 Key。 |
| **缩短有效期** | 签名仅在有效期内可用；业务侧头像等已由服务端传入较短 TTL。 |
| **接口 TTL 上限** | `GET /api/oss/presigned-url` 的 `expirationMinutes` 会被服务端 **cap** 到配置项 **`aliyun.oss.presigned-url-max-minutes`**（默认 **120**），再与 1440 分钟硬上限取较小值。 |
| **勿记录完整签名 URL** | 应用日志中对 OSS URL **去掉查询串** 再输出，避免 `Signature` 进入日志（本项目控制器与服务实现已做脱敏）。 |

## 3. 配置示例（Spring / Nacos）

与 `OssConfig` 前缀 **`aliyun.oss`** 一致（若当前环境使用顶层 `oss:`，请与运维约定是否与 `aliyun.oss` 等价绑定）：

```yaml
aliyun:
  oss:
    presigned-url-max-minutes: 120   # 客户端请求的最长预签名有效期（分钟）
```

## 4. 进阶方向（可选）

- **STS**：业务服务端签发 **临时 AccessKey**，前端或短期链接专用；轮换与吊销更灵活。  
- **Bucket 私有 + 仅后端换签名**：前端永远不持有长期有效的下载链接。

---

## 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05 | 与代码：`OssConfig.presignedUrlMaxMinutes`、日志脱敏、`OssController` TTL cap 对齐 |
