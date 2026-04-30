/**
 * 验收测试主入口：执行用例 + 性能抽样 + 生成论文级报告（表 / 多图 / HTML）。
 */
import axios from 'axios';
import { writeFileSync, mkdirSync, readFileSync, existsSync } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';
import { buildCases, buildPerfScenarios } from './cases.mjs';
import {
  aggregateByDimension,
  aggregateByModule,
  summarizePerf,
  svgBarChartByModule,
  svgStackedPassFailByModule,
  svgOutcomeStacked,
  svgDurationHistogram,
  svgBatchUserDurationHistogram,
  svgPerfBars,
  writeMarkdownReport,
  writeHtmlReport,
} from './lib/report.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));

function loadDotEnv() {
  const p = join(__dirname, '.env');
  if (!existsSync(p)) return;
  const raw = readFileSync(p, 'utf8');
  for (const line of raw.split(/\r?\n/)) {
    const t = line.trim();
    if (!t || t.startsWith('#')) continue;
    const i = t.indexOf('=');
    if (i === -1) continue;
    const k = t.slice(0, i).trim();
    let v = t.slice(i + 1).trim();
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
      v = v.slice(1, -1);
    }
    if (!process.env[k]) process.env[k] = v;
  }
}

loadDotEnv();

function loadJsonMaybe(p) {
  try {
    if (p && existsSync(p)) {
      return JSON.parse(readFileSync(p, 'utf8'));
    }
  } catch (e) {
    console.warn('无法读取 JSON:', p, e.message);
  }
  return null;
}

function buildBatchHtml(br) {
  if (!br) return '';
  const ok = br.successCount ?? 0;
  const fail = br.failedCount ?? 0;
  return `<h2>关联：批量造数（batch-fullflow）</h2>
  <table>
    <thead><tr><th>批量开始</th><th>批量结束</th><th>成功用户</th><th>失败用户</th><th>计划数量(count)</th></tr></thead>
    <tbody><tr>
      <td>${br.startedAt || '—'}</td>
      <td>${br.endedAt || '—'}</td>
      <td>${ok}</td>
      <td>${fail}</td>
      <td>${br.config?.userCount ?? '—'}</td>
    </tr></tbody>
  </table>`;
}

function buildStressHtml(sr) {
  if (!sr) return '';
  const p95 = sr.latencyMs?.p95 != null ? Number(sr.latencyMs.p95).toFixed(1) : '—';
  const p99 = sr.latencyMs?.p99 != null ? Number(sr.latencyMs.p99).toFixed(1) : '—';
  return `<h2>关联：抗压测试（stress-load）</h2>
  <table>
    <thead><tr><th>并发请求</th><th>接口</th><th>成功率</th><th>P95(ms)</th><th>P99(ms)</th></tr></thead>
    <tbody><tr>
      <td>${sr.config?.concurrent ?? '—'}</td>
      <td><code>${sr.config?.path ?? '—'}</code></td>
      <td>${sr.successRatePercent ?? '—'}%</td>
      <td>${p95}</td>
      <td>${p99}</td>
    </tr></tbody>
  </table>`;
}

const BASE_URL = (process.env.BASE_URL || 'http://localhost:8000').replace(/\/$/, '');
const TEST_USERNAME = process.env.TEST_USERNAME || process.env.ACC_USERNAME || '';
const TEST_PASSWORD = process.env.TEST_PASSWORD || process.env.ACC_PASSWORD || '';
const TEST_TOKEN = process.env.TEST_TOKEN || '';
const TEST_USER_ID = process.env.TEST_USER_ID || '';
const ADMIN_USERNAME = process.env.ADMIN_USERNAME || '';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || '';
const PERF_ROUNDS = Math.max(3, parseInt(process.env.PERF_ROUNDS || '10', 10) || 10);

function decodeJwtPayload(token) {
  try {
    if (!token || token.split('.').length < 2) return null;
    const payload = token.split('.')[1]
      .replace(/-/g, '+')
      .replace(/_/g, '/');
    const padLen = (4 - (payload.length % 4)) % 4;
    const padded = payload + '='.repeat(padLen);
    const json = Buffer.from(padded, 'base64').toString('utf8');
    return JSON.parse(json);
  } catch {
    return null;
  }
}

function client(token) {
  return axios.create({
    baseURL: BASE_URL,
    timeout: 120000,
    validateStatus: () => true,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
}

async function login(username, password) {
  const c = client();
  // 文档口径：POST /api/auth/login（username、password、roleType）
  const tryRoles = [process.env.LOGIN_ROLE_TYPE, 0, 1, 2].filter((v, i, a) => a.indexOf(v) === i);
  let lastErr = null;
  for (const roleType of tryRoles) {
    try {
      const { data, status } = await c.post('/api/auth/login', { username, password, roleType });
      if (status === 200 && data?.code === 200 && data?.data?.token) {
        // 兼容不同字段命名：id / userId
        const id = data.data.id ?? data.data.userId ?? null;
        return { token: data.data.token, userId: id != null ? String(id) : null };
      }
      lastErr = new Error(data?.message || `登录失败 HTTP ${status}`);
    } catch (e) {
      lastErr = e;
    }
  }
  throw lastErr || new Error('登录失败：未知错误');
}

async function registerIfNeeded(username, password) {
  const c = client();
  // 文档口径：POST /api/auth/register（默认患者）
  const payload = {
    username,
    password,
    confirmPassword: password,
    phone: `1${Math.floor(Math.random() * 1e10)
      .toString()
      .padStart(10, '0')}`,
  };
  const { data, status } = await c.post('/api/auth/register', payload);
  // 允许“已存在”也视为可继续登录（不同实现可能返回 400/409/业务码）
  if (status === 200 && data?.code === 200) return { ok: true, created: true };
  return { ok: false, created: false, status, code: data?.code, message: data?.message };
}

async function main() {
  // 若未提供账号，自动注册一个一次性测试用户，降低复现门槛
  const autoUser =
    !TEST_USERNAME || !TEST_PASSWORD
      ? {
          username: `acc_user_${Date.now()}`,
          password: `Acc#${Math.random().toString(36).slice(2, 10)}8`,
        }
      : null;

  const outDir = join(__dirname, 'reports');
  mkdirSync(outDir, { recursive: true });
  const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const jsonFileName = `acceptance-${stamp}.json`;

  const t0 = Date.now();
  const anonHttp = client(null);

  let userToken = TEST_TOKEN;
  let userId = TEST_USER_ID || null;
  if (!userToken) {
    const username = autoUser?.username || TEST_USERNAME;
    const password = autoUser?.password || TEST_PASSWORD;
    if (autoUser) {
      await registerIfNeeded(username, password);
    }
    const r = await login(username, password);
    userToken = r.token;
    userId = r.userId;
  } else if (!userId) {
    const payload = decodeJwtPayload(userToken);
    if (payload?.userId != null) {
      userId = String(payload.userId);
    }
  }
  let adminToken = null;
  if (ADMIN_USERNAME && ADMIN_PASSWORD) {
    try {
      const adm = await login(ADMIN_USERNAME, ADMIN_PASSWORD);
      adminToken = adm.token;
    } catch (e) {
      console.warn('管理员登录失败，管理端用例将跳过:', e.message);
    }
  }

  const userHttp = client(userToken);
  const adminHttp = adminToken ? client(adminToken) : null;

  const cache = { userId };
  const ctx = { userHttp, anonHttp, adminHttp, cache };

  let cases = buildCases();
  for (const c of cases) {
    if (c.id.startsWith('TC-ACC-ADM-')) {
      c.skip = !adminHttp;
      c.skipReason = '未配置或无法登录管理员账号';
    }
  }

  /** @type {any[]} */
  const rows = [];
  for (const c of cases) {
    if (c.skip) {
      rows.push({
        id: c.id,
        dimension: c.dimension,
        module: c.module,
        scene: c.scene,
        name: c.name,
        status: '跳过',
        durationMs: 0,
        httpStatus: '',
        bodyCode: '',
        detail: '',
        note: c.skipReason || '—',
      });
      continue;
    }
    const c0 = Date.now();
    try {
      const r = await c.run(ctx);
      const durationMs = Date.now() - c0;
      rows.push({
        id: c.id,
        dimension: c.dimension,
        module: c.module,
        scene: c.scene,
        name: c.name,
        status: r.ok ? '通过' : '失败',
        durationMs,
        httpStatus: r.httpStatus ?? '',
        bodyCode: r.bodyCode ?? '',
        detail: r.detail || '',
        note: r.note || r.detail || '',
      });
    } catch (e) {
      rows.push({
        id: c.id,
        dimension: c.dimension,
        module: c.module,
        scene: c.scene,
        name: c.name,
        status: '失败',
        durationMs: Date.now() - c0,
        httpStatus: '',
        bodyCode: '',
        detail: e?.message || String(e),
        note: e?.message || String(e),
      });
    }
  }

  const totalDurationMs = Date.now() - t0;
  const pass = rows.filter((r) => r.status === '通过').length;
  const fail = rows.filter((r) => r.status === '失败').length;
  const skip = rows.filter((r) => r.status === '跳过').length;
  const executed = pass + fail;
  const passRate = executed > 0 ? ((pass / executed) * 100).toFixed(1) : '0';

  const moduleStats = aggregateByModule(rows);
  const dimensionStats = aggregateByDimension(rows);

  /** 性能抽样 */
  const perfScenarios = buildPerfScenarios();
  /** @type {any[]} */
  const perfRows = [];
  for (const ps of perfScenarios) {
    const samplesMs = [];
    let lastErr = '';
    for (let i = 0; i < PERF_ROUNDS; i++) {
      const t1 = Date.now();
      const res = await ps.request(userHttp);
      const ms = Date.now() - t1;
      samplesMs.push(ms);
      if (res.status !== 200 || res.data?.code !== 200) {
        lastErr = `http=${res.status} code=${res.data?.code} ${res.data?.message || ''}`;
      }
    }
    perfRows.push({
      id: ps.id,
      module: ps.module,
      name: ps.name,
      rounds: PERF_ROUNDS,
      samplesMs,
      error: lastErr,
    });
  }
  const perfSummary = summarizePerf(perfRows);

  const batchReport = loadJsonMaybe(
    process.env.BATCH_RESULT_JSON || join(__dirname, '../batch-fullflow/output/batch-fullflow-result.json')
  );
  const stressReport = loadJsonMaybe(
    process.env.STRESS_REPORT_JSON || join(__dirname, '../batch-fullflow/output/stress-report.json')
  );

  const meta = {
    baseUrl: BASE_URL,
    executedAt: new Date().toISOString(),
    totalDurationMs,
    pass,
    fail,
    skip,
    passRate,
    perfRounds: PERF_ROUNDS,
    jsonFileName,
    batchFullflow: batchReport
      ? {
          successCount: batchReport.successCount,
          failedCount: batchReport.failedCount,
          startedAt: batchReport.startedAt,
          endedAt: batchReport.endedAt,
          userCount: batchReport.config?.userCount,
        }
      : null,
    stressLoad: stressReport
      ? {
          concurrent: stressReport.config?.concurrent,
          path: stressReport.config?.path,
          successRatePercent: stressReport.successRatePercent,
          p95Ms: stressReport.latencyMs?.p95,
          p99Ms: stressReport.latencyMs?.p99,
        }
      : null,
  };

  const durationsForHist = rows.filter((r) => r.status !== '跳过').map((r) => r.durationMs);

  const svgs = {
    byModule: svgBarChartByModule(moduleStats, '各功能模块验收用例执行通过率'),
    stacked: svgStackedPassFailByModule(moduleStats, '各功能模块用例结果构成（通过 / 失败 / 跳过）'),
    outcome: svgOutcomeStacked(pass, fail, skip, '验收用例总体结果构成'),
    histogram: svgDurationHistogram(durationsForHist, '功能验收用例响应时间分布（单次请求耗时，ms）'),
    perf: svgPerfBars(perfSummary, `性能抽样：各接口 ${PERF_ROUNDS} 次请求（条形=P95）`),
  };

  // 批量全流程（1000用户）附加图：用户总耗时分布
  let batchDurationSvg = null;
  if (batchReport?.results?.length) {
    const durs = batchReport.results
      .map((r) => r?.totalDurationMs)
      .filter((n) => typeof n === 'number' && n >= 0);
    if (durs.length) {
      batchDurationSvg = svgBatchUserDurationHistogram(durs, '1000用户全流程：单用户总耗时分布（ms）');
    }
  }

  writeFileSync(
    join(outDir, jsonFileName),
    JSON.stringify(
      {
        meta,
        rows,
        modules: Object.fromEntries(moduleStats),
        dimensions: Object.fromEntries(dimensionStats),
        perf: perfSummary,
        batchFullflowRaw: batchReport,
        stressLoadRaw: stressReport,
      },
      null,
      2
    ),
    'utf8'
  );

  const reportExtras = { batchReport, stressReport };
  writeMarkdownReport(
    join(outDir, 'acceptance-summary.md'),
    meta,
    rows,
    moduleStats,
    dimensionStats,
    perfSummary,
    reportExtras
  );

  const csvHeader = '用例编号,验收维度,模块,业务场景,用例名称,结果,耗时_ms,HTTP,业务码,说明';
  const csvBody = rows.map((r) =>
    [
      r.id,
      r.dimension,
      r.module,
      r.scene,
      r.name,
      r.status,
      r.durationMs,
      r.httpStatus,
      r.bodyCode,
      `"${String(r.note || '').replace(/"/g, '""')}"`,
    ].join(',')
  );
  writeFileSync(join(outDir, 'acceptance-summary.csv'), [csvHeader, ...csvBody].join('\n'), 'utf8');

  if (perfSummary.length) {
    const ph = [
      '场景编号,模块,说明,次数,平均_ms,P50_ms,P95_ms,最小_ms,最大_ms',
      ...perfSummary.map((p) =>
        [p.id, p.module, `"${String(p.name).replace(/"/g, '""')}"`, p.rounds, p.avgMs.toFixed(2), p.p50Ms.toFixed(2), p.p95Ms.toFixed(2), p.minMs.toFixed(0), p.maxMs.toFixed(0)].join(',')
      ),
    ].join('\n');
    writeFileSync(join(outDir, 'acceptance-performance.csv'), ph, 'utf8');
  }

  writeFileSync(join(outDir, 'acceptance-by-module.svg'), svgs.byModule, 'utf8');
  writeFileSync(join(outDir, 'acceptance-stacked-by-module.svg'), svgs.stacked, 'utf8');
  writeFileSync(join(outDir, 'acceptance-outcome-stacked.svg'), svgs.outcome, 'utf8');
  writeFileSync(join(outDir, 'acceptance-duration-histogram.svg'), svgs.histogram, 'utf8');
  writeFileSync(join(outDir, 'acceptance-perf-p95.svg'), svgs.perf, 'utf8');
  if (batchDurationSvg) {
    writeFileSync(join(outDir, 'batch-1000-user-duration-histogram.svg'), batchDurationSvg, 'utf8');
  }
  const htmlExtras = {
    batchHtml: buildBatchHtml(batchReport),
    stressHtml: buildStressHtml(stressReport),
  };
  writeHtmlReport(
    join(outDir, 'acceptance-report.html'),
    meta,
    rows,
    moduleStats,
    dimensionStats,
    perfSummary,
    svgs,
    htmlExtras
  );

  console.log(`验收测试完成。执行通过率（不含跳过）: ${passRate}%`);
  console.log(`报告: ${outDir}`);
  console.log(`- acceptance-summary.md / .csv / acceptance-performance.csv / acceptance-report.html / *.svg`);
  console.log(`- ${jsonFileName}`);
  if (fail > 0) process.exitCode = 1;
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
