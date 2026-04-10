# Batch Fullflow

批量用户全流程脚本（注册 → 登录 → 健康档案 → 舌诊上传 → 提交测试 → SSE analysis/plans → 保存计划 → 药膳生成与保存 → 校验收藏 → 退出登录）。

默认 **1000** 名用户（可用 `--count=` 调整）；`data-generator.js` 为每名用户生成唯一账号、手机、身份证与随机健康档案文案。

## 目录

| 文件 | 说明 |
| --- | --- |
| `batch-fullflow-users.js` | 主流程 |
| `stress-load.js` | **抗压测试**：同一账号并发 GET，输出 `output/stress-report.json` 与 `docs/thesis-7.2.2-metrics.md` |
| `run-pipeline.js` | 依次执行 **stress-load → acceptance-tests**，生成合并验收报告 |
| `data-generator.js` | 随机数据与唯一值生成（过敏史/既往史/家族史/生活方式等池已整体换新） |
| `output/batch-fullflow-result.json` | 批量执行结果（运行后生成） |

## 前置条件

- Node.js 18+（`fetch` / `FormData` / `Blob`）
- `npm install`（依赖 `mysql2` 供其他脚本使用；主流程仅 HTTP）
- 后端可访问
- 舌象图片目录存在且含 `.jpg/.jpeg/.png`（`--image-dir=`）

## 舌象图片：轮询使用「全部示例」

默认按文件名排序后 **轮询** 分配图片：第 `i` 个用户使用 `files[i % files.length]`，从而在用户量大于图片数时仍覆盖目录内全部样本。

若需恢复「每用户随机选图」：

```bash
node batch-fullflow-users.js --random-image=true
```

## 运行示例

```bash
cd D:\Desktop\hospital\batch-fullflow
npm run fullflow:1000
```

或：

```bash
node batch-fullflow-users.js --username-prefix=boot001 --start-seq=1 --count=1000 --concurrency=5 --base-url=http://127.0.0.1:8000
```

### 抗压测试（论文 7.2.2）

使用与验收测试相同的测试账号（`acceptance-tests/.env` 中 `TEST_USERNAME` / `TEST_PASSWORD`）：

```bash
node stress-load.js --base-url=http://127.0.0.1:8000 --user=你的用户 --password=123456 --concurrent=1000 --waves=5
```

### 验收报告（合并 batch / stress 摘要）

先完成批量或至少存在 `output/stress-report.json`（抗压），再：

```bash
cd D:\Desktop\hospital\acceptance-tests
npm run acceptance
```

`acceptance-summary.md` / `acceptance-report.html` 会自动附带 `batch-fullflow/output/batch-fullflow-result.json` 与 `stress-report.json` 中的摘要（若文件存在）。

或一键（不跑 1000 用户批量，只跑抗压 + 验收）：

```bash
cd D:\Desktop\hospital\batch-fullflow
npm run pipeline
```

## 可选参数（batch-fullflow-users.js）

| 参数 | 说明 |
| --- | --- |
| `--base-url=` | 默认 `http://127.0.0.1:8000` |
| `--count=` | 用户数，默认 **1000** |
| `--concurrency=` | 并行用户数，默认 10 |
| `--username-prefix=` | 用户名前缀，默认 `boot4` |
| `--start-seq=` | 起始序号，默认 1 |
| `--image-dir=` | 舌象目录 |
| `--random-image=true` | 随机选图（默认轮询全目录） |
| `--timeout=` | 单请求超时 ms |
| `--retry=` | 失败重试次数 |

环境变量等价：`BASE_URL`、`USER_COUNT`、`CONCURRENCY`、`USERNAME_PREFIX`、`USER_START_SEQ`、`IMAGE_DIR`、`TIMEOUT_MS`、`RETRY_TIMES`、`RANDOM_IMAGE=1`。

## 成功判定（单用户）

需完成：analysis SSE、plans SSE、save-plans、药膳流式生成与 save-recipes、收藏校验、logout。

## 输出

- 控制台日志
- `output/batch-fullflow-result.json`
- 抗压：`output/stress-report.json`、`output/stress-report.md`、`docs/thesis-7.2.2-metrics.md`（相对仓库根目录）
