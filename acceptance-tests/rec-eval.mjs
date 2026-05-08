/**
 * 药膳推荐离线评测：个性化 vs 热门 vs 随机基线
 * 输出 JSON / Markdown 汇总表 + 论文用 SVG 图（图 6.7～6.9）
 *
 * 用法：
 *   cd acceptance-tests && npm run rec-eval
 *
 * 环境变量（可与验收测试共用 .env）：
 *   BASE_URL              默认 http://localhost:8000
 *   TEST_USERNAME        单用户评测（无 batch 文件时）
 *   TEST_PASSWORD
 *   EVAL_BATCH_JSON      batch-fullflow/output/batch-fullflow-result.json
 *   EVAL_PASSWORD        批量用户默认密码，默认 123456
 *   EVAL_MAX_USERS       从 batch 结果中取的成功用户数上限，默认 80
 *   EVAL_LIST_POOL       随机基线用的药膳池大小，默认 200
 *   REC_EVAL_TOKEN       可选：浏览器复制 JWT，跳过登录验证码（仅适合单账号；批量评测勿用）
 *   TEST_TOKEN           同 REC_EVAL_TOKEN
 */
import axios from 'axios';
import { writeFileSync, mkdirSync, readFileSync, existsSync } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';

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

function esc(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

const BASE_URL = (process.env.BASE_URL || 'http://localhost:8000').replace(/\/$/, '');
const EVAL_BATCH_JSON =
  process.env.EVAL_BATCH_JSON || join(__dirname, '..', 'batch-fullflow', 'output', 'batch-fullflow-result.json');
const EVAL_PASSWORD = process.env.EVAL_PASSWORD || '123456';
const EVAL_MAX_USERS = Math.max(1, parseInt(process.env.EVAL_MAX_USERS || '50', 10) || 50);
const EVAL_LIST_POOL = Math.max(50, parseInt(process.env.EVAL_LIST_POOL || '200', 10) || 200);
const TEST_USERNAME = process.env.TEST_USERNAME || process.env.ACC_USERNAME || '';
const TEST_PASSWORD = process.env.TEST_PASSWORD || process.env.ACC_PASSWORD || '';
const REC_EVAL_TOKEN = (process.env.REC_EVAL_TOKEN || process.env.TEST_TOKEN || '').trim();

function containsIgnoreCase(source, target) {
  if (!source || !target) return false;
  return String(source).toUpperCase().includes(String(target).toUpperCase());
}

/** 相关度：主适 1，次适 0.5，否则 0 */
function relevance(recipe, primary, secondary) {
  const ct = recipe?.constitutionType;
  if (primary && containsIgnoreCase(ct, primary)) return 1;
  if (secondary && containsIgnoreCase(ct, secondary)) return 0.5;
  return 0;
}

function dcgAtK(rels, k) {
  let d = 0;
  for (let i = 0; i < Math.min(k, rels.length); i++) {
    const rank = i + 1;
    d += rels[i] / Math.log2(rank + 1);
  }
  return d;
}

function idealDcgAtK(rels, k) {
  const sorted = [...rels].sort((a, b) => b - a);
  return dcgAtK(sorted, k);
}

function ndcgAtK(recipes, primary, secondary, k) {
  const rels = recipes.map((r) => relevance(r, primary, secondary));
  const d = dcgAtK(rels, k);
  const id = idealDcgAtK(rels, k);
  return id > 0 ? d / id : 0;
}

function precisionAtK(recipes, primary, secondary, k) {
  const slice = recipes.slice(0, k);
  if (!slice.length) return 0;
  const hits = slice.reduce((acc, r) => acc + (relevance(r, primary, secondary) > 0 ? 1 : 0), 0);
  return hits / k;
}

function hitRateAtK(recipes, primary, secondary, k) {
  const slice = recipes.slice(0, k);
  return slice.some((r) => relevance(r, primary, secondary) > 0) ? 1 : 0;
}

function hashSeed(str) {
  let h = 2166136261;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

/** 可复现洗牌 */
function seededShuffle(arr, seedStr) {
  let seed = hashSeed(seedStr);
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    seed = (seed * 1103515245 + 12345) >>> 0;
    const j = seed % (i + 1);
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

const CAPTCHA_WHITELIST = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
/** 与后端 CaptchaService 验证码位数一致 */
const CAPTCHA_CODE_LEN = 5;

async function ocrCaptchaBuffer(buf) {
  const { createWorker } = await import('tesseract.js');
  const worker = await createWorker('eng');
  await worker.setParameters({
    tessedit_char_whitelist: CAPTCHA_WHITELIST,
    tessedit_pageseg_mode: '7',
  });
  try {
    const {
      data: { text },
    } = await worker.recognize(buf);
    return String(text || '').replace(/\s+/g, '').toUpperCase();
  } finally {
    await worker.terminate();
  }
}

/**
 * 登录须验证码：拉取图片 OCR 后 POST /api/user/login，失败则刷新验证码重试。
 */
async function loginWithCaptcha(anon, username, password, maxAttempts = 20) {
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const capRes = await anon.get('/api/captcha/image');
    if (capRes.status !== 200 || capRes.data?.code !== 200) {
      throw new Error(capRes.data?.message || '获取验证码失败');
    }
    const { captchaId, imageBase64 } = capRes.data?.data || {};
    if (!captchaId || !imageBase64) throw new Error('验证码接口返回缺少 captchaId/imageBase64');

    const buf = Buffer.from(imageBase64, 'base64');
    let raw = await ocrCaptchaBuffer(buf);
    raw = [...raw].filter((ch) => CAPTCHA_WHITELIST.includes(ch)).join('');
    let captchaCode = raw.slice(0, CAPTCHA_CODE_LEN);
    if (captchaCode.length < CAPTCHA_CODE_LEN) {
      continue;
    }

    const loginRes = await anon.post('/api/user/login', {
      username,
      password,
      captchaId,
      captchaCode,
    });

    if (loginRes.status === 200 && loginRes.data?.code === 200 && loginRes.data?.data?.token) {
      return {
        token: loginRes.data.data.token,
        userId: loginRes.data.data.id ?? null,
      };
    }

    const msg = loginRes.data?.message || '';
    if (msg && !msg.includes('验证码') && !msg.includes('captcha')) {
      throw new Error(msg);
    }
  }
  throw new Error(
    `验证码识别失败（已重试 ${maxAttempts} 次）。请在浏览器登录后从开发者工具复制 Authorization Bearer Token，` +
      `设置环境变量 REC_EVAL_TOKEN 后重试（单账号模式）。`
  );
}

async function fetchLatestTest(http) {
  const res = await http.get('/api/constitution/test/latest');
  if (res.status !== 200 || res.data?.code !== 200) return null;
  return res.data?.data || null;
}

async function fetchPersonalized(http, limit) {
  const res = await http.get('/api/recipe/recommend/personalized', { params: { limit } });
  if (res.status !== 200 || res.data?.code !== 200) throw new Error(res.data?.message || 'personalized failed');
  return Array.isArray(res.data?.data) ? res.data.data : [];
}

async function fetchPopular(http, limit) {
  const res = await http.get('/api/recipe/popular', { params: { limit } });
  if (res.status !== 200 || res.data?.code !== 200) throw new Error(res.data?.message || 'popular failed');
  return Array.isArray(res.data?.data) ? res.data.data : [];
}

async function fetchRecipePool(http, targetSize) {
  const pool = [];
  let pageNum = 1;
  const pageSize = 100;
  while (pool.length < targetSize) {
    const res = await http.get('/api/recipe/list', { params: { pageNum, pageSize } });
    if (res.status !== 200 || res.data?.code !== 200) break;
    const page = res.data?.data;
    const records = page?.records || [];
    if (!records.length) break;
    for (const r of records) pool.push(r);
    const total = Number(page?.total ?? page?.pages * pageSize);
    if (pool.length >= total || records.length < pageSize) break;
    pageNum += 1;
    if (pageNum > 50) break;
  }
  return pool;
}

function randomBaselineFromPool(pool, username, limit) {
  const shuffled = seededShuffle(pool, `rand:${username}`);
  const seen = new Set();
  const out = [];
  for (const r of shuffled) {
    if (!r?.id || seen.has(r.id)) continue;
    seen.add(r.id);
    out.push(r);
    if (out.length >= limit) break;
  }
  return out;
}

function aggregateMetrics(rows, k) {
  const keys = ['personalized', 'popular', 'random'];
  const out = {};
  for (const key of keys) {
    const p = rows.map((r) => r[`p_${key}_${k}`]).filter((x) => x != null);
    const n = rows.map((r) => r[`n_${key}_${k}`]).filter((x) => x != null);
    const h = rows.map((r) => r[`h_${key}_${k}`]).filter((x) => x != null);
    out[key] = {
      precisionAtK: p.length ? p.reduce((a, b) => a + b, 0) / p.length : 0,
      ndcgAtK: n.length ? n.reduce((a, b) => a + b, 0) / n.length : 0,
      hitRateAtK: h.length ? h.reduce((a, b) => a + b, 0) / h.length : 0,
    };
  }
  return out;
}

function svgGroupedPrecisionNdcg(metrics5, metrics10, title) {
  const W = 880;
  const H = 420;
  const padL = 56;
  const padR = 24;
  const padT = 52;
  const padB = 100;
  const chartW = W - padL - padR;
  const chartH = H - padT - padB;
  const groups = ['K=5', 'K=10'];
  const strategies = [
    { key: 'personalized', label: '个性化', fill: '#1565c0' },
    { key: 'popular', label: '热门基线', fill: '#ef6c00' },
    { key: 'random', label: '随机基线', fill: '#6a1b9a' },
  ];
  const innerGap = 4;
  const groupW = chartW / groups.length;
  const barW = (groupW - innerGap * 4) / 3;
  const parts = [];
  parts.push(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">`);
  parts.push(`<rect width="100%" height="100%" fill="#fafafa"/>`);
  parts.push(
    `<text x="${W / 2}" y="28" text-anchor="middle" font-size="15" font-family="Microsoft YaHei, Segoe UI, sans-serif">${esc(title)}</text>`
  );
  parts.push(
    `<text x="${W / 2}" y="46" text-anchor="middle" font-size="10" fill="#546e7a">Precision 与 NDCG 为样本平均；匹配规则与后端内容画像一致（体质字段包含判定）</text>`
  );
  parts.push(`<line x1="${padL}" y1="${padT + chartH}" x2="${padL + chartW}" y2="${padT + chartH}" stroke="#333"/>`);
  parts.push(`<line x1="${padL}" y1="${padT}" x2="${padL}" y2="${padT + chartH}" stroke="#333"/>`);
  for (let i = 0; i <= 5; i++) {
    const ty = padT + chartH - (chartH * i) / 5;
    parts.push(`<line x1="${padL}" y1="${ty}" x2="${padL + chartW}" y2="${ty}" stroke="#e0e0e0"/>`);
    parts.push(`<text x="${padL - 8}" y="${ty + 4}" text-anchor="end" font-size="10" fill="#666">${(i * 0.2).toFixed(1)}</text>`);
  }

  const drawGroup = (gx, label, m) => {
    parts.push(`<text x="${gx + groupW / 2}" y="${padT + chartH + 22}" text-anchor="middle" font-size="11" font-family="Microsoft YaHei, sans-serif">${esc(label)}</text>`);
    strategies.forEach((s, si) => {
      const x = gx + innerGap + si * (barW + innerGap);
      const vP = m[s.key].precisionAtK;
      const vN = m[s.key].ndcgAtK;
      const hP = vP * chartH;
      const hN = vN * chartH;
      const xP = x;
      const xN = x + barW * 0.52;
      const wN = barW * 0.45;
      parts.push(`<rect x="${xP}" y="${padT + chartH - hP}" width="${barW * 0.45}" height="${hP}" fill="${s.fill}" opacity="0.85" rx="2"/>`);
      parts.push(`<rect x="${xN}" y="${padT + chartH - hN}" width="${wN}" height="${hN}" fill="${s.fill}" opacity="0.45" rx="2"/>`);
    });
  };

  const m5 = {
    personalized: metrics5.personalized,
    popular: metrics5.popular,
    random: metrics5.random,
  };
  const m10 = {
    personalized: metrics10.personalized,
    popular: metrics10.popular,
    random: metrics10.random,
  };
  drawGroup(padL, 'K=5', m5);
  drawGroup(padL + groupW, 'K=10', m10);

  let lx = padL + chartW + 8;
  let ly = padT + 10;
  parts.push(`<text x="${lx}" y="${ly}" font-size="11" font-weight="bold">图例</text>`);
  parts.push(`<rect x="${lx}" y="${ly + 8}" width="12" height="12" fill="#1565c0" opacity="0.85"/><text x="${lx + 18}" y="${ly + 18}" font-size="10">Precision@K（深色）</text>`);
  parts.push(`<rect x="${lx}" y="${ly + 28}" width="12" height="12" fill="#1565c0" opacity="0.45"/><text x="${lx + 18}" y="${ly + 38}" font-size="10">NDCG@K（浅色）</text>`);
  strategies.forEach((s, i) => {
    parts.push(`<rect x="${lx}" y="${ly + 48 + i * 16}" width="10" height="10" fill="${s.fill}"/><text x="${lx + 16}" y="${ly + 58 + i * 16}" font-size="10">${esc(s.label)}</text>`);
  });

  parts.push('</svg>');
  return parts.join('\n');
}

function svgByConstitution(byPrimary, title) {
  const entries = Object.entries(byPrimary).sort((a, b) => a[0].localeCompare(b[0], 'zh-CN'));
  const W = 920;
  const H = 420;
  const padL = 56;
  const padT = 50;
  const padB = 120;
  const chartW = W - padL - 24;
  const chartH = H - padT - padB;
  const n = entries.length || 1;
  const gap = 8;
  const barW = Math.max(14, (chartW - gap * (n + 1)) / n);
  let x = padL + gap;
  const parts = [];
  parts.push(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">`);
  parts.push(`<rect width="100%" height="100%" fill="#fafafa"/>`);
  parts.push(
    `<text x="${W / 2}" y="26" text-anchor="middle" font-size="15" font-family="Microsoft YaHei, sans-serif">${esc(title)}</text>`
  );
  parts.push(`<line x1="${padL}" y1="${padT + chartH}" x2="${padL + chartW}" y2="${padT + chartH}" stroke="#333"/>`);
  parts.push(`<line x1="${padL}" y1="${padT}" x2="${padL}" y2="${padT + chartH}" stroke="#333"/>`);
  for (let i = 0; i <= 5; i++) {
    const ty = padT + chartH - (chartH * i) / 5;
    parts.push(`<line x1="${padL}" y1="${ty}" x2="${padL + chartW}" y2="${ty}" stroke="#e0e0e0"/>`);
    parts.push(`<text x="${padL - 8}" y="${ty + 4}" text-anchor="end" font-size="10" fill="#666">${(i * 0.2).toFixed(1)}</text>`);
  }
  for (const [code, st] of entries) {
    const v = st.sumP / st.n;
    const h = v * chartH;
    const y = padT + chartH - h;
    parts.push(`<rect x="${x}" y="${y}" width="${barW}" height="${h}" fill="#2e7d32" rx="3"/>`);
    parts.push(`<text x="${x + barW / 2}" y="${y - 6}" text-anchor="middle" font-size="9" fill="#333">${v.toFixed(2)}</text>`);
    const lx = x + barW / 2;
    const ly = padT + chartH + 12;
    const lab = st.label || code;
    parts.push(
      `<text transform="rotate(-35 ${lx} ${ly})" x="${lx}" y="${ly}" text-anchor="end" font-size="9" font-family="Microsoft YaHei, sans-serif">${esc(lab)}</text>`
    );
    x += barW + gap;
  }
  parts.push('</svg>');
  return parts.join('\n');
}

function svgRadar(metrics10, avgDiversity, title) {
  const dims = [
    { key: 'precision', label: 'Precision@10', val: metrics10.personalized.precisionAtK },
    { key: 'ndcg', label: 'NDCG@10', val: metrics10.personalized.ndcgAtK },
    { key: 'hit', label: 'Hit Rate@10', val: metrics10.personalized.hitRateAtK },
    {
      key: 'div',
      label: '多样性(类目)',
      val: avgDiversity,
    },
  ];
  const W = 480;
  const H = 440;
  const cx = W / 2;
  const cy = H / 2 + 10;
  const R = 120;
  const n = dims.length;
  const angle = (i) => (-Math.PI / 2) + (i * 2 * Math.PI) / n;
  const pts = dims.map((_, i) => {
    const t = angle(i);
    return [cx + R * Math.cos(t), cy + R * Math.sin(t)];
  });
  const parts = [];
  parts.push(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">`);
  parts.push(`<rect width="100%" height="100%" fill="#fafafa"/>`);
  parts.push(
    `<text x="${cx}" y="22" text-anchor="middle" font-size="14" font-family="Microsoft YaHei, sans-serif">${esc(title)}</text>`
  );
  parts.push(
    `<text x="${cx}" y="40" text-anchor="middle" font-size="9" fill="#78909c">各轴已按 0～1 归一化；多样性为 Top-10 药膳类目分散度</text>`
  );
  for (let g = 1; g <= 4; g++) {
    const r = (R * g) / 4;
    const ring = pts
      .map(([px, py], i) => {
        const t = angle(i);
        return [cx + r * Math.cos(t), cy + r * Math.sin(t)];
      })
      .map(([x, y]) => `${x},${y}`)
      .join(' ');
    parts.push(`<polygon points="${ring}" fill="none" stroke="#cfd8dc" stroke-width="1"/>`);
  }
  for (let i = 0; i < n; i++) {
    parts.push(`<line x1="${cx}" y1="${cy}" x2="${pts[i][0]}" y2="${pts[i][1]}" stroke="#cfd8dc" stroke-width="1"/>`);
    const tx = cx + (R + 28) * Math.cos(angle(i));
    const ty = cy + (R + 28) * Math.sin(angle(i));
    parts.push(`<text x="${tx}" y="${ty}" text-anchor="middle" font-size="10" font-family="Microsoft YaHei, sans-serif">${esc(dims[i].label)}</text>`);
  }
  const poly = dims
    .map((d, i) => {
      const t = angle(i);
      const rr = R * Math.min(1, Math.max(0, d.val));
      return `${cx + rr * Math.cos(t)},${cy + rr * Math.sin(t)}`;
    })
    .join(' ');
  parts.push(`<polygon points="${poly}" fill="#1565c0" fill-opacity="0.25" stroke="#1565c0" stroke-width="2"/>`);
  parts.push('</svg>');
  return parts.join('\n');
}

function createHttp(token) {
  return axios.create({
    baseURL: BASE_URL,
    timeout: 60000,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
}

/** Top-K 药膳类目分散度：不同类目数 / 6 封顶归一化 */
function categoryDiversityNormalized(recipes) {
  const cats = new Set(recipes.map((r) => r.category).filter(Boolean));
  return Math.min(1, cats.size / 6);
}

async function evaluateUser(username, password, pool, fixedToken) {
  let token;
  let userId = null;
  if (fixedToken) {
    token = fixedToken;
    const httpProbe = createHttp(token);
    const info = await httpProbe.get('/api/user/info');
    if (info.status !== 200 || info.data?.code !== 200) {
      throw new Error(info.data?.message || 'Token 无效或已过期');
    }
    userId = info.data?.data?.id ?? info.data?.data?.userId ?? null;
  } else {
    const anon = createHttp('');
    const logged = await loginWithCaptcha(anon, username, password);
    token = logged.token;
    userId = logged.userId;
  }
  const http = createHttp(token);
  const test = await fetchLatestTest(http);
  if (!test?.primaryConstitution) {
    return { skip: true, username, reason: 'no_constitution_test' };
  }
  const primary = test.primaryConstitution;
  const secondary = test.secondaryConstitution || '';
  const label = test.primaryConstitutionName || primary;

  const pers10 = await fetchPersonalized(http, 10);
  const pop10 = await fetchPopular(http, 10);
  const rand10 = randomBaselineFromPool(pool, username, 10);
  const diversity = categoryDiversityNormalized(pers10);

  const row = {
    username,
    userId: userId ?? null,
    primaryConstitution: primary,
    primaryLabel: label,
    secondaryConstitution: secondary || null,
    diversity_personalized_10: diversity,
  };

  for (const k of [5, 10]) {
    for (const [key, list] of [
      ['personalized', pers10],
      ['popular', pop10],
      ['random', rand10],
    ]) {
      row[`p_${key}_${k}`] = precisionAtK(list, primary, secondary, k);
      row[`n_${key}_${k}`] = ndcgAtK(list, primary, secondary, k);
      row[`h_${key}_${k}`] = hitRateAtK(list, primary, secondary, k);
    }
  }
  return row;
}

function loadAccountsFromBatch() {
  if (!existsSync(EVAL_BATCH_JSON)) return [];
  const raw = JSON.parse(readFileSync(EVAL_BATCH_JSON, 'utf8'));
  const results = raw.results || [];
  const ok = results.filter((r) => r.success && r.username);
  return ok.slice(0, EVAL_MAX_USERS).map((r) => ({ username: r.username, password: EVAL_PASSWORD }));
}

/** 无后端时生成可复现的演示数据（论文占位图，不可作为真实评测结论） */
function runDemoMode(outDir) {
  mkdirSync(outDir, { recursive: true });
  const rows = [];
  const primaries = ['PING_HE', 'QI_XU', 'YANG_XU', 'YIN_XU', 'TAN_SHI', 'SHI_RE', 'XUE_YU', 'QI_YU', 'TE_BING'];
  for (let i = 0; i < 36; i++) {
    const primary = primaries[i % primaries.length];
    const secondary = i % 7 === 0 ? 'QI_XU' : '';
    const noise = (seed) => {
      let h = seed * 2654435761;
      return () => ((h = Math.imul(h ^ (h >>> 15), h | 1)) >>> 0) / 4294967296;
    };
    const rnd = noise(i + 1);
    const base = 0.55 + rnd() * 0.15;
    rows.push({
      username: `demo_user_${i + 1}`,
      primaryConstitution: primary,
      primaryLabel: primary,
      secondaryConstitution: secondary || null,
      diversity_personalized_10: 0.45 + rnd() * 0.35,
      p_personalized_5: Math.min(1, base + 0.18 + rnd() * 0.08),
      n_personalized_5: Math.min(1, base + 0.15 + rnd() * 0.08),
      h_personalized_5: rnd() > 0.12 ? 1 : 0,
      p_popular_5: Math.min(1, base + rnd() * 0.1),
      n_popular_5: Math.min(1, base - 0.05 + rnd() * 0.1),
      h_popular_5: rnd() > 0.35 ? 1 : 0,
      p_random_5: Math.min(1, base - 0.22 + rnd() * 0.12),
      n_random_5: Math.min(1, base - 0.25 + rnd() * 0.1),
      h_random_5: rnd() > 0.55 ? 1 : 0,
      p_personalized_10: Math.min(1, base + 0.2 + rnd() * 0.06),
      n_personalized_10: Math.min(1, base + 0.17 + rnd() * 0.06),
      h_personalized_10: rnd() > 0.06 ? 1 : 0,
      p_popular_10: Math.min(1, base + 0.05 + rnd() * 0.08),
      n_popular_10: Math.min(1, base - 0.02 + rnd() * 0.08),
      h_popular_10: rnd() > 0.25 ? 1 : 0,
      p_random_10: Math.min(1, base - 0.2 + rnd() * 0.1),
      n_random_10: Math.min(1, base - 0.22 + rnd() * 0.1),
      h_random_10: rnd() > 0.45 ? 1 : 0,
    });
  }

  const m5 = aggregateMetrics(rows, 5);
  const m10 = aggregateMetrics(rows, 10);
  const byPrimary = {};
  for (const r of rows) {
    const code = r.primaryConstitution;
    if (!byPrimary[code]) byPrimary[code] = { n: 0, sumP: 0, label: r.primaryLabel };
    byPrimary[code].n += 1;
    byPrimary[code].sumP += r.p_personalized_10;
  }
  const avgDiversity =
    rows.reduce((a, r) => a + (r.diversity_personalized_10 || 0), 0) / rows.length;

  const summary = {
    generatedAt: new Date().toISOString(),
    mode: 'demo',
    note: '演示数据：请启动后端后去掉 --demo 重新运行以获取真实评测结果',
    sampleSize: rows.length,
    metricsAt5: m5,
    metricsAt10: m10,
    avgCategoryDiversity10: avgDiversity,
    rows,
  };

  writeFileSync(join(outDir, 'rec-eval-summary.json'), JSON.stringify(summary, null, 2), 'utf8');
  writeFileSync(
    join(outDir, 'fig67-precision-ndcg.svg'),
    svgGroupedPrecisionNdcg(m5, m10, '图 6.7　不同推荐策略 Precision@K / NDCG@K 对比（演示数据）'),
    'utf8'
  );
  writeFileSync(
    join(outDir, 'fig68-precision-by-constitution.svg'),
    svgByConstitution(byPrimary, '图 6.8　各主体质类型下个性化推荐 Precision@10（演示数据）'),
    'utf8'
  );
  writeFileSync(
    join(outDir, 'fig69-radar.svg'),
    svgRadar(m10, avgDiversity, '图 6.9　个性化推荐多维指标雷达图（演示数据）'),
    'utf8'
  );

  const md = [];
  md.push('# 药膳推荐离线评测结果（演示模式）');
  md.push('');
  md.push('**注意**：当前为 `--demo` 生成的占位数据，用于排版与插图预览。正式结论请在后端可用时运行 `npm run rec-eval`。');
  md.push('');
  md.push(`- 样本量 N=${rows.length}`);
  md.push('');
  md.push('| 策略 | Precision@5 | NDCG@5 | Hit@5 | Precision@10 | NDCG@10 | Hit@10 |');
  md.push('| --- | --- | --- | --- | --- | --- | --- |');
  for (const key of ['personalized', 'popular', 'random']) {
    const a = key === 'personalized' ? '个性化' : key === 'popular' ? '热门基线' : '随机基线';
    const x = m5[key];
    const y = m10[key];
    md.push(
      `| ${a} | ${x.precisionAtK.toFixed(4)} | ${x.ndcgAtK.toFixed(4)} | ${x.hitRateAtK.toFixed(4)} | ${y.precisionAtK.toFixed(4)} | ${y.ndcgAtK.toFixed(4)} | ${y.hitRateAtK.toFixed(4)} |`
    );
  }
  writeFileSync(join(outDir, 'rec-eval-summary.md'), md.join('\n'), 'utf8');
  console.log(`[rec-eval] 演示模式完成：输出 ${outDir}（非真实测评）`);
}

async function main() {
  const outDir = join(__dirname, 'reports', 'rec-eval');
  if (process.argv.includes('--demo')) {
    runDemoMode(outDir);
    return;
  }

  let accounts = loadAccountsFromBatch();
  if (!accounts.length && TEST_USERNAME && TEST_PASSWORD) {
    accounts = [{ username: TEST_USERNAME, password: TEST_PASSWORD }];
    console.warn('[rec-eval] 未找到 batch 结果，使用单账号 TEST_USERNAME（样本数 N=1，图表可供演示）');
  }
  if (!accounts.length) {
    console.error(
      '[rec-eval] 无评测账号：请配置 TEST_USERNAME/TEST_PASSWORD，或生成 batch-fullflow/output/batch-fullflow-result.json'
    );
    process.exit(1);
  }

  if (REC_EVAL_TOKEN && accounts.length > 1) {
    console.warn(
      '[rec-eval] 已设置 REC_EVAL_TOKEN 且存在多个账号：将忽略 Token，改为对每个账号执行验证码登录（批量较慢）'
    );
  }

  let firstTok = REC_EVAL_TOKEN;
  if (!firstTok || accounts.length > 1) {
    const anon = createHttp('');
    const logged = await loginWithCaptcha(anon, accounts[0].username, accounts[0].password);
    firstTok = logged.token;
  }
  const httpFirst = createHttp(firstTok);
  const recipePool = await fetchRecipePool(httpFirst, EVAL_LIST_POOL);
  if (recipePool.length < 20) {
    console.warn(`[rec-eval] 药膳池仅 ${recipePool.length} 条，随机基线可能不稳定`);
  }

  const rows = [];
  const singleTokenMode = Boolean(REC_EVAL_TOKEN) && accounts.length === 1;
  for (const acc of accounts) {
    try {
      const tok = singleTokenMode ? REC_EVAL_TOKEN : undefined;
      const r = await evaluateUser(acc.username, acc.password, recipePool, tok);
      if (!r.skip) rows.push(r);
      else console.warn(`[rec-eval] 跳过 ${acc.username}: ${r.reason}`);
    } catch (e) {
      console.warn(`[rec-eval] 失败 ${acc.username}: ${e.message}`);
    }
  }

  if (!rows.length) {
    console.error('[rec-eval] 没有有效样本（均需已完成体质测试）');
    process.exit(1);
  }

  const m5 = aggregateMetrics(rows, 5);
  const m10 = aggregateMetrics(rows, 10);

  const byPrimary = {};
  for (const r of rows) {
    const code = r.primaryConstitution;
    if (!byPrimary[code]) byPrimary[code] = { n: 0, sumP: 0, label: r.primaryLabel };
    byPrimary[code].n += 1;
    byPrimary[code].sumP += r.p_personalized_10;
  }

  const avgDiversity =
    rows.reduce((a, r) => a + (r.diversity_personalized_10 || 0), 0) / Math.max(1, rows.length);

  const summary = {
    generatedAt: new Date().toISOString(),
    baseUrl: BASE_URL,
    authNote: singleTokenMode ? 'REC_EVAL_TOKEN' : 'login+captcha_ocr',
    sampleSize: rows.length,
    batchSource: existsSync(EVAL_BATCH_JSON) ? EVAL_BATCH_JSON : null,
    metricsAt5: m5,
    metricsAt10: m10,
    avgCategoryDiversity10: avgDiversity,
    rows,
  };

  writeFileSync(join(outDir, 'rec-eval-summary.json'), JSON.stringify(summary, null, 2), 'utf8');

  const md = [];
  md.push('# 药膳推荐离线评测结果');
  md.push('');
  md.push(`- 生成时间：${summary.generatedAt}`);
  md.push(`- 样本量 N：**${rows.length}**（均已存在最新体质测试）`);
  md.push(`- 后端：\`${BASE_URL}\``);
  md.push('');
  md.push('## 表：总体指标对比（样本平均）');
  md.push('');
  md.push('| 策略 | Precision@5 | NDCG@5 | Hit@5 | Precision@10 | NDCG@10 | Hit@10 |');
  md.push('| --- | --- | --- | --- | --- | --- | --- |');
  for (const key of ['personalized', 'popular', 'random']) {
    const a = key === 'personalized' ? '个性化' : key === 'popular' ? '热门基线' : '随机基线';
    const x = m5[key];
    const y = m10[key];
    md.push(
      `| ${a} | ${x.precisionAtK.toFixed(4)} | ${x.ndcgAtK.toFixed(4)} | ${x.hitRateAtK.toFixed(4)} | ${y.precisionAtK.toFixed(4)} | ${y.ndcgAtK.toFixed(4)} | ${y.hitRateAtK.toFixed(4)} |`
    );
  }
  md.push('');
  md.push('## 输出图表（SVG）');
  md.push('');
  md.push('| 文件 | 对应论文图号（建议） |');
  md.push('| --- | --- |');
  md.push('| `fig67-precision-ndcg.svg` | 图 6.7 不同推荐策略 Precision@K / NDCG@K |');
  md.push('| `fig68-precision-by-constitution.svg` | 图 6.8 各主体质 Precision@10（个性化） |');
  md.push('| `fig69-radar.svg` | 图 6.9 多维质量雷达（演示轴含多样性） |');
  md.push('');

  writeFileSync(join(outDir, 'rec-eval-summary.md'), md.join('\n'), 'utf8');

  writeFileSync(
    join(outDir, 'fig67-precision-ndcg.svg'),
    svgGroupedPrecisionNdcg(m5, m10, '图 6.7　不同推荐策略 Precision@K / NDCG@K 对比'),
    'utf8'
  );
  writeFileSync(
    join(outDir, 'fig68-precision-by-constitution.svg'),
    svgByConstitution(byPrimary, '图 6.8　各主体质类型下个性化推荐 Precision@10（样本平均）'),
    'utf8'
  );
  writeFileSync(
    join(outDir, 'fig69-radar.svg'),
    svgRadar(m10, avgDiversity, '图 6.9　个性化推荐多维指标雷达图（归一化）'),
    'utf8'
  );

  console.log(`[rec-eval] 完成：N=${rows.length}，输出目录 ${outDir}`);
}

main().catch((e) => {
  const msg = e?.cause?.code || e?.code || e?.message || String(e);
  if (msg === 'ECONNREFUSED') {
    console.error(
      `[rec-eval] 无法连接 ${BASE_URL}。请先启动后端，或运行演示出图：npm run rec-eval:demo`
    );
  }
  console.error(e);
  process.exit(1);
});
