#!/usr/bin/env node
/**
 * 抗压测试：同一 Token 下并发发起大量 GET，统计成功率与延迟分布。
 * 用于论文 7.2.2 非功能指标中的「并发」「响应时间」描述。
 *
 * 用法：
 *   node stress-load.js --base-url=http://127.0.0.1:8000 --concurrent=1000
 *   node stress-load.js --concurrent=200 --waves=5   # 5 波 × 200 = 1000 次总并发量（分波降低瞬时连接压力）
 *
 * 环境变量：BASE_URL, STRESS_USER, STRESS_PASSWORD（或 TEST_USERNAME / TEST_PASSWORD）, CONCURRENT, WAVES
 */
/* eslint-disable no-console */
const fs = require("fs");
const path = require("path");

function parseArgs() {
  const args = process.argv.slice(2);
  const kv = {};
  for (const a of args) {
    if (!a.startsWith("--")) continue;
    const [k, v] = a.slice(2).split("=");
    kv[k] = v ?? "";
  }
  return {
    baseUrl: kv["base-url"] || process.env.BASE_URL || "http://127.0.0.1:8000",
    concurrent: Number(kv.concurrent || process.env.CONCURRENT || 1000),
    waves: Math.max(1, Number(kv.waves || process.env.WAVES || 1)),
    path:
      kv.path ||
      process.env.STRESS_PATH ||
      "/api/recipe/list?pageNum=1&pageSize=10",
    user:
      kv.user ||
      process.env.STRESS_USER ||
      process.env.TEST_USERNAME ||
      "",
    password:
      kv.password ||
      process.env.STRESS_PASSWORD ||
      process.env.TEST_PASSWORD ||
      "123456",
    timeoutMs: Number(kv.timeout || process.env.STRESS_TIMEOUT_MS || 120000)
  };
}

function normalizeBase(baseUrl) {
  return String(baseUrl || "").replace(/\/+$/, "");
}

function percentile(sorted, p) {
  if (!sorted.length) return 0;
  const idx = Math.min(sorted.length - 1, Math.max(0, Math.ceil((p / 100) * sorted.length) - 1));
  return sorted[idx];
}

async function login(base, user, password, timeoutMs) {
  const url = `${base}/api/user/login`;
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: user, password }),
      signal: controller.signal
    });
    const data = await res.json();
    if (!res.ok || data.code !== 200 || !data.data?.token) {
      throw new Error(data.message || `login HTTP ${res.status}`);
    }
    return data.data.token;
  } finally {
    clearTimeout(t);
  }
}

async function oneRequest(base, token, relPath, timeoutMs) {
  const url = `${base}${relPath.startsWith("/") ? "" : "/"}${relPath}`;
  const controller = new AbortController();
  const t = setTimeout(() => controller.abort(), timeoutMs);
  const t0 = Date.now();
  try {
    const res = await fetch(url, {
      method: "GET",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`
      },
      signal: controller.signal
    });
    const text = await res.text();
    let data = {};
    try {
      data = text ? JSON.parse(text) : {};
    } catch {
      return { ok: false, ms: Date.now() - t0, err: "invalid json" };
    }
    const ms = Date.now() - t0;
    const ok = res.ok && data.code === 200;
    return { ok, ms, err: ok ? "" : `${res.status} ${data.code} ${data.message || ""}` };
  } catch (e) {
    return { ok: false, ms: Date.now() - t0, err: e.message || String(e) };
  } finally {
    clearTimeout(t);
  }
}

async function runWave(base, token, relPath, n, timeoutMs) {
  const tasks = Array.from({ length: n }, () => oneRequest(base, token, relPath, timeoutMs));
  return Promise.all(tasks);
}

async function main() {
  const opts = parseArgs();
  const base = normalizeBase(opts.baseUrl);
  if (!opts.user) {
    console.error("请指定抗压登录账号：--user=xxx 或环境变量 STRESS_USER / TEST_USERNAME");
    process.exit(1);
  }

  const outDir = path.join(__dirname, "output");
  if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true });
  const docsDir = path.join(__dirname, "..", "docs");
  if (!fs.existsSync(docsDir)) fs.mkdirSync(docsDir, { recursive: true });

  console.log(`Login ${opts.user} ...`);
  const token = await login(base, opts.user, opts.password, opts.timeoutMs);
  const perWave = Math.ceil(opts.concurrent / opts.waves);
  const allResults = [];
  const startedAt = new Date().toISOString();
  const tAll = Date.now();
  let remaining = opts.concurrent;

  for (let w = 0; w < opts.waves && remaining > 0; w += 1) {
    const n = Math.min(perWave, remaining);
    console.log(`Wave ${w + 1}/${opts.waves}: ${n} concurrent GET ${opts.path}`);
    const batch = await runWave(base, token, opts.path, n, opts.timeoutMs);
    allResults.push(...batch);
    remaining -= n;
  }

  const wallMs = Date.now() - tAll;
  const endedAt = new Date().toISOString();
  const times = allResults.filter((r) => r.ok).map((r) => r.ms).sort((a, b) => a - b);
  const failed = allResults.filter((r) => !r.ok);
  const successRate = allResults.length ? ((times.length / allResults.length) * 100).toFixed(2) : "0";

  const report = {
    config: {
      baseUrl: base,
      path: opts.path,
      concurrent: opts.concurrent,
      waves: opts.waves,
      totalRequests: allResults.length
    },
    startedAt,
    endedAt,
    wallClockMs: wallMs,
    successCount: times.length,
    failCount: failed.length,
    successRatePercent: Number(successRate),
    latencyMs: {
      min: times.length ? times[0] : 0,
      p50: percentile(times, 50),
      p95: percentile(times, 95),
      p99: percentile(times, 99),
      max: times.length ? times[times.length - 1] : 0,
      avg: times.length ? times.reduce((a, b) => a + b, 0) / times.length : 0
    },
    sampleErrors: failed.slice(0, 20).map((r) => r.err || "unknown")
  };

  const jsonPath = path.join(outDir, "stress-report.json");
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2), "utf-8");

  const mdPath = path.join(outDir, "stress-report.md");
  const md = [
    "## 抗压测试（stress-load.js）",
    "",
    "| 项目 | 值 |",
    "| --- | --- |",
    `| 基地址 | ${base} |`,
    `| 接口 | \`${opts.path}\` |`,
    `| 并发请求数 | ${opts.concurrent} |`,
    `| 分波数 | ${opts.waves} |`,
    `| 成功 | ${times.length} |`,
    `| 失败 | ${failed.length} |`,
    `| **成功率** | **${successRate}%** |`,
    `| 总耗时(墙钟) | ${wallMs} ms |`,
    "",
    "| 延迟(ms) | 值 |",
    "| --- | ---: |",
    `| 最小 | ${report.latencyMs.min.toFixed(0)} |`,
    `| 平均 | ${report.latencyMs.avg.toFixed(1)} |`,
    `| P50 | ${report.latencyMs.p50.toFixed(1)} |`,
    `| P95 | ${report.latencyMs.p95.toFixed(1)} |`,
    `| P99 | ${report.latencyMs.p99.toFixed(1)} |`,
    `| 最大 | ${report.latencyMs.max.toFixed(0)} |`,
    "",
    `> 原始 JSON：\`batch-fullflow/output/stress-report.json\``,
    ""
  ].join("\n");
  fs.writeFileSync(mdPath, md, "utf-8");

  const thesisPath = path.join(docsDir, "thesis-7.2.2-metrics.md");
  const thesis = [
    "<!-- 本文件由 batch-fullflow/stress-load.js 自动生成，可粘贴入论文 7.2.2 节 -->",
    "",
    "### 抗压测试（脚本实测摘要）",
    "",
    `在测试环境（${base}）下，使用同一认证会话对接口 \`${opts.path}\` 发起 **${opts.concurrent}** 次并发请求（分 **${opts.waves}** 波执行），请求成功率为 **${successRate}%**；`,
    `响应时间：平均 **${report.latencyMs.avg.toFixed(1)} ms**，P95 **${report.latencyMs.p95.toFixed(1)} ms**，P99 **${report.latencyMs.p99.toFixed(1)} ms**，最大 **${report.latencyMs.max.toFixed(0)} ms**（墙钟总耗时 ${wallMs} ms）。`,
    "",
    "（可与 `acceptance-tests/reports/` 中接口抽样数据对照使用。）",
    ""
  ].join("\n");
  fs.writeFileSync(thesisPath, thesis, "utf-8");

  console.log(`Done. success=${times.length}/${allResults.length} (${successRate}%)`);
  console.log(`Report: ${jsonPath}`);
  console.log(`Thesis snippet: ${thesisPath}`);
  if (failed.length) process.exitCode = 1;
}

main().catch((e) => {
  console.error("Fatal:", e.message);
  process.exit(1);
});
