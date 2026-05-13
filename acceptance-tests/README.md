# 验收测试脚本（论文用表 / 多图）

面向毕业设计「验收测试」：脚本自动调用后端 **REST 接口**，按 **验收维度**（功能 / 安全与鉴权 / 业务规则）组织用例，并输出 **汇总表、明细表、性能抽样表、多种 SVG 图、可截图 HTML**。

## 输出文件（`reports/`）

| 文件 | 说明 |
| --- | --- |
| `acceptance-summary.md` | 测试概要、**按验收维度**统计、按模块统计、**性能抽样表**、用例明细（含场景、HTTP、业务码） |
| `acceptance-summary.csv` | 同上明细，Excel 可打开 |
| `acceptance-performance.csv` | 各接口多次请求的 avg / P50 / P95 / min / max（ms） |
| `acceptance-report.html` | 维度表 + 模块表 + 性能表 + **5 张图内嵌**，适合整页截图插入论文 |
| `acceptance-by-module.svg` | 各功能模块**执行通过率**柱状图 |
| `acceptance-stacked-by-module.svg` | 各模块 **通过 / 失败 / 跳过** 堆叠条 |
| `acceptance-outcome-stacked.svg` | 总体结果构成 |
| `acceptance-duration-histogram.svg` | 各用例**响应时间**分布直方图 |
| `acceptance-perf-p95.svg` | 性能抽样 **P95** 对比条 |
| `acceptance-*.json` | 完整原始数据 |

## 用例覆盖概览

- **安全与鉴权**：无 Token 访问受保护接口（期望 401）、匿名访问 `/api/config`。
- **功能验收**：用户、体质（含报告链路）、首页聚合、药膳多策略推荐与检索、健康档案/计划/打卡/统计（带 `userId`）、文章与评论与通知、搜索热词、会话与消息、穴位、系统设置等。
- **业务规则校验**：未舌诊提交体测等（与后端策略一致即通过）。
- **性能抽样**：对核心 GET 接口连续请求 `PERF_ROUNDS` 次（默认 10），生成 avg / P50 / P95。

可选 **管理员** 账号：执行 `/api/statistics/admin` 等管理端用例；未配置则记为「跳过」。

## 配置

```powershell
cd D:\Desktop\hospital\acceptance-tests
Copy-Item config.example.env .env
# TEST_USERNAME / TEST_PASSWORD 必填；ADMIN_* 可选
# 可选：PERF_ROUNDS=15  性能抽样每场景请求次数
```

勿使用环境变量名 `USERNAME`（Windows 已占用），请用 `TEST_USERNAME`。

### 合并 batch-fullflow / 抗压数据

若存在以下文件，报告会自动增加「批量造数」「抗压测试」摘要表：

- `batch-fullflow/output/batch-fullflow-result.json`
- `batch-fullflow/output/stress-report.json`

也可用环境变量指定路径：`BATCH_RESULT_JSON`、`STRESS_REPORT_JSON`。

一键生成抗压 + 验收：`cd batch-fullflow && npm run pipeline`。

## 运行

```powershell
npm run acceptance
```

若存在失败用例，退出码为 `1`。

## 药膳推荐离线评测（论文图 6.7～6.9）

对个性化推荐与「热门」「随机」基线对比 **Precision@K、NDCG@K、Hit Rate**（体质字段匹配规则与后端一致），并导出 SVG。

```powershell
cd D:\Desktop\hospital\acceptance-tests
# 本地后端
npm run rec-eval
# 远程服务器（HTTP， nginx 反代根路径，无需写端口）
$env:BASE_URL="http://YOUR_SERVER_IP"; npm run rec-eval
```

- **账号来源**：优先读取 `batch-fullflow/output/batch-fullflow-result.json` 中成功用户（默认密码 `EVAL_PASSWORD=123456`，上限 `EVAL_MAX_USERS`）；若无则使用 `.env` 中 `TEST_USERNAME`（仅 N=1，说服力弱）。
- **登录与验证码**：`/api/user/login` 需要验证码。脚本已集成 **tesseract.js** 自动识别验证码（依赖 `npm install`）；若多次失败，可在浏览器登录后在开发者工具复制 JWT，设置 **`REC_EVAL_TOKEN`**（或 **`TEST_TOKEN`**）后重跑——**仅在使用单个评测账号时生效**；批量账号将对每个用户依次 OCR 登录。
- **前置条件**：服务可访问（默认 `BASE_URL=http://localhost:8000`）；样本须已完成体质测试。
- **无后端占位出图**（不可写入论文为真实结论）：`npm run rec-eval:demo`

输出目录：`reports/rec-eval/`（`rec-eval-summary.md`、`rec-eval-summary.json`、`fig67-precision-ndcg.svg`、`fig68-precision-by-constitution.svg`、`fig69-radar.svg`）。

## 论文表述示例

「验收阶段采用自动化脚本对系统 REST 接口进行分层验证：在功能层面覆盖用户、体质辨识、药膳推荐、健康档案、内容社区、医患沟通与辅助模块；在安全层面验证未授权访问拦截；在规则层面校验关键业务前置条件；并对核心接口进行多次抽样以统计响应时间分布。测试结果以表格与图表形式归档（见附录）。」
