import { writeFileSync } from 'fs';
import { join } from 'path';

export function esc(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

export function aggregateByDimension(rows) {
  const map = new Map();
  for (const r of rows) {
    const d = r.dimension || '未分类';
    if (!map.has(d)) map.set(d, { pass: 0, fail: 0, skip: 0, total: 0 });
    const m = map.get(d);
    m.total++;
    if (r.status === '通过') m.pass++;
    else if (r.status === '失败') m.fail++;
    else m.skip++;
  }
  return map;
}

export function aggregateByModule(rows) {
  const map = new Map();
  for (const r of rows) {
    if (!map.has(r.module)) map.set(r.module, { pass: 0, fail: 0, skip: 0, total: 0 });
    const m = map.get(r.module);
    m.total++;
    if (r.status === '通过') m.pass++;
    else if (r.status === '失败') m.fail++;
    else m.skip++;
  }
  return map;
}

function percentile(sorted, p) {
  if (!sorted.length) return 0;
  const idx = Math.min(sorted.length - 1, Math.max(0, Math.ceil((p / 100) * sorted.length) - 1));
  return sorted[idx];
}

export function summarizePerf(perfRows) {
  return perfRows.map((p) => {
    const times = [...p.samplesMs].sort((a, b) => a - b);
    const n = times.length;
    const sum = times.reduce((a, b) => a + b, 0);
    return {
      ...p,
      avgMs: n ? sum / n : 0,
      minMs: n ? times[0] : 0,
      maxMs: n ? times[n - 1] : 0,
      p50Ms: percentile(times, 50),
      p95Ms: percentile(times, 95),
    };
  });
}

export function svgBarChartByModule(moduleStats, title) {
  const entries = [...moduleStats.entries()].sort((a, b) => a[0].localeCompare(b[0], 'zh-CN'));
  const W = 920;
  const H = 400;
  const padL = 52;
  const padR = 24;
  const padT = 52;
  const padB = 120;
  const chartW = W - padL - padR;
  const chartH = H - padT - padB;
  const barGap = 6;
  const n = entries.length || 1;
  const barW = Math.max(12, (chartW - barGap * (n + 1)) / n);
  let x = padL + barGap;
  const parts = [];
  parts.push(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">`);
  parts.push(`<rect width="100%" height="100%" fill="#fafafa"/>`);
  parts.push(
    `<text x="${W / 2}" y="26" text-anchor="middle" font-size="15" font-family="Segoe UI, Microsoft YaHei, sans-serif">${esc(title)}</text>`
  );
  parts.push(
    `<text x="${W / 2}" y="44" text-anchor="middle" font-size="10" fill="#546e7a">仅统计「已执行」用例；全跳过的模块显示为「—」</text>`
  );
  parts.push(`<line x1="${padL}" y1="${padT + chartH}" x2="${padL + chartW}" y2="${padT + chartH}" stroke="#333"/>`);
  parts.push(`<line x1="${padL}" y1="${padT}" x2="${padL}" y2="${padT + chartH}" stroke="#333"/>`);
  for (let i = 0; i <= 4; i++) {
    const ty = padT + chartH - (chartH * i) / 4;
    parts.push(`<line x1="${padL}" y1="${ty}" x2="${padL + chartW}" y2="${ty}" stroke="#e0e0e0"/>`);
    parts.push(`<text x="${padL - 6}" y="${ty + 4}" text-anchor="end" font-size="11" fill="#666">${(i * 25)}%</text>`);
  }
  for (const [mod, st] of entries) {
    const executed = st.total - st.skip;
    const allSkipped = executed <= 0;
    const rate = !allSkipped ? (st.pass / executed) * 100 : null;
    const h = allSkipped ? 0 : (rate / 100) * chartH;
    const y = padT + chartH - h;
    if (allSkipped) {
      parts.push(`<rect x="${x}" y="${padT}" width="${barW}" height="${chartH}" fill="#eceff1" stroke="#b0bec5" stroke-width="1" rx="3"/>`);
      parts.push(
        `<text x="${x + barW / 2}" y="${padT + chartH / 2 + 4}" text-anchor="middle" font-size="10" fill="#607d8b">—</text>`
      );
    } else {
      parts.push(`<rect x="${x}" y="${y}" width="${barW}" height="${h}" fill="#1565c0" rx="3"/>`);
      parts.push(
        `<text x="${x + barW / 2}" y="${y - 5}" text-anchor="middle" font-size="10" fill="#333">${rate.toFixed(0)}%</text>`
      );
    }
    const lx = x + barW / 2;
    const ly = padT + chartH + 14;
    parts.push(
      `<text transform="rotate(-38 ${lx} ${ly})" x="${lx}" y="${ly}" text-anchor="end" font-size="9" font-family="Microsoft YaHei, sans-serif">${esc(mod)}</text>`
    );
    parts.push(
      `<title>${esc(mod)} ${allSkipped ? '全部跳过（不计通过率）' : `执行通过率 ${rate.toFixed(1)}%（${st.pass}/${executed}）`}</title>`
    );
    x += barW + barGap;
  }
  parts.push('</svg>');
  return parts.join('\n');
}

/** 各模块通过/失败/跳过构成：每柱总高相同，段高 = 该模块内占比（避免「跳过」被全局最大值压扁） */
export function svgStackedPassFailByModule(moduleStats, title) {
  const entries = [...moduleStats.entries()].sort((a, b) => a[0].localeCompare(b[0], 'zh-CN'));
  const W = 920;
  const H = 420;
  const padL = 44;
  const padT = 50;
  const padB = 128;
  const chartW = W - padL - 20;
  const chartH = H - padT - padB;
  const n = entries.length || 1;
  const gap = 6;
  const barW = Math.max(14, (chartW - gap * (n + 1)) / n);
  let x = padL + gap;
  const parts = [];
  parts.push(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">`);
  parts.push(`<rect width="100%" height="100%" fill="#fafafa"/>`);
  parts.push(
    `<text x="${W / 2}" y="26" text-anchor="middle" font-size="15" font-family="Microsoft YaHei, sans-serif">${esc(title)}</text>`
  );
  parts.push(
    `<text x="${W / 2}" y="42" text-anchor="middle" font-size="10" fill="#546e7a">每柱总高=100%；绿/红/灰=该模块内通过/失败/跳过占比（悬停可看具体条数）</text>`
  );

  parts.push(`<line x1="${padL}" y1="${padT + chartH}" x2="${padL + chartW}" y2="${padT + chartH}" stroke="#333"/>`);
  parts.push(`<line x1="${padL}" y1="${padT}" x2="${padL}" y2="${padT + chartH}" stroke="#333"/>`);
  for (let i = 0; i <= 4; i++) {
    const ty = padT + chartH - (chartH * i) / 4;
    parts.push(`<line x1="${padL}" y1="${ty}" x2="${padL + chartW}" y2="${ty}" stroke="#e0e0e0"/>`);
    parts.push(`<text x="${padL - 6}" y="${ty + 4}" text-anchor="end" font-size="10" fill="#666">${(i * 25)}%</text>`);
  }
  parts.push(`<text x="10" y="${padT + chartH / 2}" font-size="11" fill="#666" transform="rotate(-90 10 ${padT + chartH / 2})">模块内占比</text>`);

  for (const [mod, st] of entries) {
    const total = st.pass + st.fail + st.skip;
    let y = padT + chartH;
    if (total === 0) {
      parts.push(`<rect x="${x}" y="${padT}" width="${barW}" height="${chartH}" fill="#f5f5f5" stroke="#ccc" rx="2"/>`);
    } else {
      const hPass = (st.pass / total) * chartH;
      const hFail = (st.fail / total) * chartH;
      const hSkip = (st.skip / total) * chartH;
      if (hSkip > 0) {
        y -= hSkip;
        parts.push(`<rect x="${x}" y="${y}" width="${barW}" height="${hSkip}" fill="#78909c"/>`);
      }
      if (hFail > 0) {
        y -= hFail;
        parts.push(`<rect x="${x}" y="${y}" width="${barW}" height="${hFail}" fill="#c62828"/>`);
      }
      if (hPass > 0) {
        y -= hPass;
        parts.push(`<rect x="${x}" y="${y}" width="${barW}" height="${hPass}" fill="#2e7d32"/>`);
      }
    }
    const lx = x + barW / 2;
    const ly = padT + chartH + 12;
    parts.push(
      `<text transform="rotate(-36 ${lx} ${ly})" x="${lx}" y="${ly}" text-anchor="end" font-size="9" font-family="Microsoft YaHei, sans-serif">${esc(mod)}</text>`
    );
    parts.push(`<title>${esc(mod)} 通过 ${st.pass} · 失败 ${st.fail} · 跳过 ${st.skip} · 合计 ${total}</title>`);
    x += barW + gap;
  }

  const ly = H - 28;
  [
    ['#2e7d32', '通过'],
    ['#c62828', '失败'],
    ['#78909c', '跳过'],
  ].forEach((t, i) => {
    parts.push(`<rect x="${40 + i * 92}" y="${ly}" width="12" height="12" fill="${t[0]}"/>`);
    parts.push(`<text x="${56 + i * 92}" y="${ly + 11}" font-size="11" font-family="Microsoft YaHei, sans-serif">${t[1]}</text>`);
  });
  parts.push('</svg>');
  return parts.join('\n');
}

export function svgOutcomeStacked(pass, fail, skip, title) {
  const W = 560;
  const H = 220;
  const total = pass + fail + skip;
  const parts = [];
  parts.push(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}">`);
  parts.push(`<rect width="100%" height="100%" fill="#fafafa"/>`);
  parts.push(
    `<text x="${W / 2}" y="28" text-anchor="middle" font-size="15" font-family="Microsoft YaHei, sans-serif">${esc(title)}</text>`
  );
  if (total === 0) {
    parts.push(`<text x="20" y="100">无数据</text></svg>`);
    return parts.join('\n');
  }
  const barX = 40;
  const barY = 58;
  const barW = W - 80;
  const barH = 40;
  let x = barX;
  const seg = [
    [pass, '#2e7d32'],
    [fail, '#c62828'],
    [skip, '#78909c'],
  ];
  for (const [n, color] of seg) {
    const w = (n / total) * barW;
    if (w > 0) {
      parts.push(`<rect x="${x}" y="${barY}" width="${w}" height="${barH}" fill="${color}" stroke="#fff" stroke-width="1"/>`);
      if (w > 40) {
        parts.push(
          `<text x="${x + w / 2}" y="${barY + barH / 2 + 5}" text-anchor="middle" fill="#fff" font-size="13" font-family="Microsoft YaHei, sans-serif">${n}</text>`
        );
      }
      x += w;
    }
  }
  const exec = pass + fail;
  parts.push(
    `<text x="${barX}" y="${barY + barH + 22}" font-size="12" fill="#333">合计 ${total} 项 · 执行 ${exec} 项 · 通过率 ${exec ? ((pass / exec) * 100).toFixed(1) : '0'}%</text>`
  );
  const legY = barY + barH + 46;
  [
    ['#2e7d32', `通过 ${pass}`],
    ['#c62828', `失败 ${fail}`],
    ['#78909c', `跳过 ${skip}`],
  ].forEach((t, i) => {
    parts.push(`<rect x="${40 + i * 130}" y="${legY}" width="12" height="12" fill="${t[0]}"/>`);
    parts.push(`<text x="${56 + i * 130}" y="${legY + 11}" font-size="12" font-family="Microsoft YaHei, sans-serif">${esc(t[1])}</text>`);
  });
  parts.push('</svg>');
  return parts.join('\n');
}

/** 响应时间分布直方图：对极端大值封顶分箱，避免「一条慢请求压扁」整张图 */
export function svgDurationHistogram(durationsMs, title) {
  const valid = durationsMs.filter((n) => typeof n === 'number' && n >= 0);
  if (!valid.length) {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="600" height="120"><text x="20" y="40">无耗时数据</text></svg>`;
  }
  const sorted = [...valid].sort((a, b) => a - b);
  const p95v = percentile(sorted, 95);
  const cap = Math.min(Math.max(p95v * 2, 800), Math.max(...valid));
  const overflow = valid.filter((t) => t > cap).length;
  const forBins = valid.map((t) => Math.min(t, cap));
  const min = Math.min(...forBins);
  const maxB = Math.max(...forBins);
  const bins = 12;
  const w = 720;
  const h = 300;
  const padL = 48;
  const padR = 24;
  const padT = 48;
  const padB = 72;
  const cw = w - padL - padR;
  const ch = h - padT - padB;
  const binW = cw / bins;
  const counts = new Array(bins).fill(0);
  const span = Math.max(1, maxB - min);
  for (const t of forBins) {
    const b = Math.min(bins - 1, Math.floor(((t - min) / span) * bins));
    counts[b]++;
  }
  const maxC = Math.max(1, ...counts);
  const parts = [];
  parts.push(`<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">`);
  parts.push(`<rect width="100%" height="100%" fill="#fafafa"/>`);
  parts.push(
    `<text x="${w / 2}" y="26" text-anchor="middle" font-size="14" font-family="Microsoft YaHei, sans-serif">${esc(title)}</text>`
  );
  parts.push(
    `<text x="${w / 2}" y="42" text-anchor="middle" font-size="10" fill="#546e7a">横轴为耗时区间(ms)；纵轴为落入该区间的用例条数${
      overflow ? esc(`；>${cap.toFixed(0)}ms 的 ${overflow} 条归入最右箱`) : ''
    }</text>`
  );

  parts.push(`<line x1="${padL}" y1="${padT + ch}" x2="${padL + cw}" y2="${padT + ch}" stroke="#333"/>`);
  parts.push(`<line x1="${padL}" y1="${padT}" x2="${padL}" y2="${padT + ch}" stroke="#333"/>`);
  for (let k = 0; k <= 4; k++) {
    const ty = padT + ch - (ch * k) / 4;
    parts.push(`<line x1="${padL}" y1="${ty}" x2="${padL + cw}" y2="${ty}" stroke="#eee"/>`);
    const cv = Math.round((maxC * k) / 4);
    parts.push(`<text x="${padL - 6}" y="${ty + 4}" text-anchor="end" font-size="10" fill="#666">${cv}</text>`);
  }
  parts.push(`<text x="14" y="${padT + ch / 2}" font-size="11" fill="#666" transform="rotate(-90 14 ${padT + ch / 2})">用例条数</text>`);

  for (let i = 0; i < bins; i++) {
    const bh = (counts[i] / maxC) * ch;
    const x = padL + i * binW + 2;
    const y = padT + ch - bh;
    parts.push(`<rect x="${x}" y="${y}" width="${binW - 4}" height="${bh}" fill="#5c6bc0" rx="2"/>`);
    const lo = min + (span * i) / bins;
    const hi = min + (span * (i + 1)) / bins;
    const isLast = i === bins - 1;
    const label = isLast && overflow ? `>${lo.toFixed(0)}` : `${lo.toFixed(0)}-${hi.toFixed(0)}`;
    parts.push(
      `<text x="${x + binW / 2 - 2}" y="${padT + ch + 22}" text-anchor="end" transform="rotate(-55 ${x + binW / 2 - 2} ${padT + ch + 22})" font-size="8" fill="#455a64">${esc(label)}</text>`
    );
  }
  parts.push(
    `<text x="${padL}" y="${h - 10}" font-size="10" fill="#555">${esc(`原始 min=${sorted[0].toFixed(0)} max=${sorted[sorted.length - 1].toFixed(0)} ms · n=${valid.length} · 分箱上限=${cap.toFixed(0)}ms`)}</text>`
  );
  parts.push('</svg>');
  return parts.join('\n');
}

/** 各场景 P95 横向对比：条形区域与文字分区，避免右侧被裁切 */
export function svgPerfBars(perfSummary, title) {
  const rowH = 52;
  const labelCol = 200;
  const padT = 44;
  const W = 960;
  const barAreaW = 360;
  const H = padT + perfSummary.length * rowH + 40;
  const maxMs = Math.max(1, ...perfSummary.map((p) => p.p95Ms || p.avgMs));
  const barX = labelCol + 8;

  const parts = [];
  parts.push(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">`);
  parts.push(`<rect width="100%" height="100%" fill="#fafafa"/>`);
  parts.push(
    `<text x="${W / 2}" y="24" text-anchor="middle" font-size="14" font-family="Microsoft YaHei, sans-serif">${esc(title)}</text>`
  );
  parts.push(
    `<text x="${W / 2}" y="40" text-anchor="middle" font-size="10" fill="#546e7a">蓝色条形长度 ∝ P95（相对本组最大 P95）；右侧为 avg / p50 / p95 数值</text>`
  );
  perfSummary.forEach((p, i) => {
    const y = padT + i * rowH;
    const label = `${p.id} ${p.module}`;
    parts.push(`<text x="8" y="${y + 18}" font-size="10" font-family="Microsoft YaHei, sans-serif">${esc(label)}</text>`);
    parts.push(`<text x="8" y="${y + 32}" font-size="9" fill="#607d8b">${esc(p.name)}</text>`);
    const w = (p.p95Ms / maxMs) * barAreaW;
    parts.push(`<rect x="${barX}" y="${y + 6}" width="${barAreaW}" height="18" fill="#e3f2fd" stroke="#90caf9" rx="2"/>`);
    parts.push(`<rect x="${barX}" y="${y + 6}" width="${Math.max(w, 2)}" height="18" fill="#1565c0" rx="2"/>`);
    const statX = barX + barAreaW + 14;
    parts.push(
      `<text x="${statX}" y="${y + 16}" font-size="10" fill="#333" font-family="Consolas, monospace">avg ${p.avgMs.toFixed(0)} ms</text>`
    );
    parts.push(
      `<text x="${statX}" y="${y + 30}" font-size="10" fill="#333" font-family="Consolas, monospace">p50 ${p.p50Ms.toFixed(0)} · p95 ${p.p95Ms.toFixed(0)} ms</text>`
    );
  });
  parts.push(
    `<text x="8" y="${H - 12}" font-size="10" fill="#546e7a">每组连续请求 ${perfSummary[0]?.rounds ?? 'N'} 次；浅蓝底为满刻度参考。</text>`
  );
  parts.push('</svg>');
  return parts.join('\n');
}

/** 1000用户全流程：总耗时直方图（ms） */
export function svgBatchUserDurationHistogram(durationsMs, title) {
  const valid = (durationsMs || []).filter((n) => typeof n === 'number' && n >= 0);
  if (!valid.length) {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="640" height="120"><text x="20" y="40">无批量耗时数据</text></svg>`;
  }
  // 复用现有直方图，但标题区分
  return svgDurationHistogram(valid, title);
}

export function writeMarkdownReport(outPath, meta, rows, moduleStats, dimensionStats, perfSummary, extras = {}) {
  const pass = rows.filter((r) => r.status === '通过').length;
  const fail = rows.filter((r) => r.status === '失败').length;
  const skip = rows.filter((r) => r.status === '跳过').length;
  const exec = pass + fail;
  const passRate = exec > 0 ? ((pass / exec) * 100).toFixed(1) : '0';

  const lines = [
    '## 验收测试报告（脚本自动生成）',
    '',
    '### 1. 测试概要',
    '',
    '| 项目 | 值 |',
    '| --- | --- |',
    `| 服务基地址 | ${meta.baseUrl} |`,
    `| 执行时间 | ${meta.executedAt} |`,
    `| 脚本总耗时 | ${meta.totalDurationMs} ms |`,
    `| 用例总数 | ${rows.length} |`,
    `| 通过 | ${pass} |`,
    `| 失败 | ${fail} |`,
    `| 跳过 | ${skip} |`,
    `| **执行通过率**（不含跳过） | **${passRate}%** |`,
    '',
    '### 2. 按验收维度统计',
    '',
    '| 验收维度 | 用例数 | 通过 | 失败 | 跳过 | 执行通过率 |',
    '| --- | ---: | ---: | ---: | ---: | --- |',
  ];

  for (const [name, st] of [...dimensionStats.entries()].sort((a, b) => a[0].localeCompare(b[0], 'zh-CN'))) {
    const e = st.total - st.skip;
    const rate = e > 0 ? `${((st.pass / e) * 100).toFixed(1)}%` : '-';
    lines.push(`| ${name} | ${st.total} | ${st.pass} | ${st.fail} | ${st.skip} | ${rate} |`);
  }

  lines.push('', '### 3. 按功能模块统计', '', '| 功能模块 | 用例数 | 通过 | 失败 | 跳过 | 执行通过率 |', '| --- | ---: | ---: | ---: | ---: | --- |');
  for (const [name, st] of [...moduleStats.entries()].sort((a, b) => a[0].localeCompare(b[0], 'zh-CN'))) {
    const e = st.total - st.skip;
    const rate = e > 0 ? `${((st.pass / e) * 100).toFixed(1)}%` : '-';
    lines.push(`| ${name} | ${st.total} | ${st.pass} | ${st.fail} | ${st.skip} | ${rate} |`);
  }

  if (perfSummary?.length) {
    lines.push(
      '',
      '### 4. 接口性能抽样',
      '',
      '每个场景连续请求多次，统计平均耗时、P50、P95（可用于论文「响应时间」描述）。',
      '',
      '| 场景编号 | 模块 | 说明 | 次数 | 平均(ms) | P50(ms) | P95(ms) | 最小 | 最大 |',
      '| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |'
    );
    for (const p of perfSummary) {
      lines.push(
        `| ${p.id} | ${p.module} | ${p.name} | ${p.rounds} | ${p.avgMs.toFixed(1)} | ${p.p50Ms.toFixed(1)} | ${p.p95Ms.toFixed(1)} | ${p.minMs.toFixed(0)} | ${p.maxMs.toFixed(0)} |`
      );
    }
  }

  lines.push('', '### 5. 用例明细', '', '| 编号 | 验收维度 | 模块 | 业务场景 | 用例名称 | 结果 | 耗时(ms) | HTTP | 业务码 | 说明 |');
  lines.push('| --- | --- | --- | --- | --- | --- | ---: | ---: | ---: | --- |');
  for (const r of rows) {
    const note = String(r.note || r.detail || '').replace(/\|/g, '\\|');
    lines.push(
      `| ${r.id} | ${r.dimension} | ${r.module} | ${r.scene} | ${r.name} | ${r.status} | ${r.durationMs} | ${r.httpStatus ?? ''} | ${r.bodyCode ?? ''} | ${note} |`
    );
  }

  lines.push('', `> 原始 JSON：\`reports/${meta.jsonFileName}\``);

  const br = extras.batchReport;
  if (br && typeof br === 'object') {
    const all = Array.isArray(br.results) ? br.results : [];
    const okUsers = all.filter((r) => r && r.success).length;
    const failUsers = all.filter((r) => r && !r.success).length;
    const durs = all.map((r) => r?.totalDurationMs).filter((n) => typeof n === 'number');
    const sorted = [...durs].sort((a, b) => a - b);
    const p50 = sorted.length ? sorted[Math.max(0, Math.ceil(sorted.length * 0.5) - 1)] : 0;
    const p95 = sorted.length ? sorted[Math.max(0, Math.ceil(sorted.length * 0.95) - 1)] : 0;
    const avg = durs.length ? durs.reduce((a, b) => a + b, 0) / durs.length : 0;

    lines.push(
      '',
      '### 6. 关联：批量造数（batch-fullflow）',
      '',
      '| 项目 | 值 |',
      '| --- | --- |',
      `| 批量开始 | ${br.startedAt || '—'} |`,
      `| 批量结束 | ${br.endedAt || '—'} |`,
      `| 配置 userCount | ${br.config?.userCount ?? '—'} |`,
      `| 并发（concurrency） | ${br.config?.concurrency ?? '—'} |`,
      `| 成功用户数 | ${br.successCount ?? okUsers} |`,
      `| 失败用户数 | ${br.failedCount ?? failUsers} |`,
      `| 用户总耗时 平均(ms) | ${avg ? avg.toFixed(1) : '—'} |`,
      `| 用户总耗时 P50(ms) | ${p50 || '—'} |`,
      `| 用户总耗时 P95(ms) | ${p95 || '—'} |`
    );
  }

  const sr = extras.stressReport;
  if (sr && typeof sr === 'object') {
    lines.push(
      '',
      '### 7. 关联：抗压测试（stress-load）',
      '',
      '| 项目 | 值 |',
      '| --- | --- |',
      `| 并发请求数 | ${sr.config?.concurrent ?? '—'} |`,
      `| 接口 | ${sr.config?.path ?? '—'} |`,
      `| 成功率 | ${sr.successRatePercent ?? '—'}% |`,
      `| P95(ms) | ${sr.latencyMs?.p95 != null ? Number(sr.latencyMs.p95).toFixed(1) : '—'} |`,
      `| P99(ms) | ${sr.latencyMs?.p99 != null ? Number(sr.latencyMs.p99).toFixed(1) : '—'} |`
    );
  }

  lines.push(
    '',
    '### 8. 论文插图文件',
    '',
    '- `acceptance-by-module.svg`：各模块通过率',
    '- `acceptance-stacked-by-module.svg`：各模块通过/失败/跳过构成',
    '- `acceptance-outcome-stacked.svg`：总体结果构成',
    '- `acceptance-duration-histogram.svg`：用例响应时间分布',
    '- `acceptance-perf-p95.svg`：性能抽样 P95 对比',
    '- `acceptance-report.html`：汇总页（可整页截图）'
  );

  writeFileSync(outPath, lines.join('\n'), 'utf8');
}

export function writeHtmlReport(path, meta, rows, moduleStats, dimensionStats, perfSummary, svgs, extras = {}) {
  const modRows = [...moduleStats.entries()]
    .sort((a, b) => a[0].localeCompare(b[0], 'zh-CN'))
    .map(([name, st]) => {
      const exec = st.total - st.skip;
      const rate = exec > 0 ? ((st.pass / exec) * 100).toFixed(1) : '-';
      return `<tr><td>${esc(name)}</td><td>${st.total}</td><td>${st.pass}</td><td>${st.fail}</td><td>${st.skip}</td><td>${rate}${rate === '-' ? '' : '%'}</td></tr>`;
    })
    .join('');

  const dimRows = [...dimensionStats.entries()]
    .sort((a, b) => a[0].localeCompare(b[0], 'zh-CN'))
    .map(([name, st]) => {
      const exec = st.total - st.skip;
      const rate = exec > 0 ? ((st.pass / exec) * 100).toFixed(1) : '-';
      return `<tr><td>${esc(name)}</td><td>${st.total}</td><td>${st.pass}</td><td>${st.fail}</td><td>${st.skip}</td><td>${rate}${rate === '-' ? '' : '%'}</td></tr>`;
    })
    .join('');

  const detailRows = rows
    .map(
      (r) =>
        `<tr><td>${esc(r.id)}</td><td>${esc(r.dimension)}</td><td>${esc(r.module)}</td><td>${esc(r.scene)}</td><td>${esc(r.name)}</td><td class="${r.status === '通过' ? 'ok' : r.status === '失败' ? 'fail' : ''}">${esc(r.status)}</td><td>${esc(r.durationMs)}</td><td>${esc(r.httpStatus ?? '')}</td><td>${esc(r.bodyCode ?? '')}</td><td>${esc((r.note || r.detail || '').slice(0, 200))}</td></tr>`
    )
    .join('');

  let perfTable = '';
  if (perfSummary?.length) {
    const pr = perfSummary
      .map(
        (p) =>
          `<tr><td>${esc(p.id)}</td><td>${esc(p.module)}</td><td>${esc(p.name)}</td><td>${p.rounds}</td><td>${p.avgMs.toFixed(1)}</td><td>${p.p50Ms.toFixed(1)}</td><td>${p.p95Ms.toFixed(1)}</td><td>${p.minMs.toFixed(0)}</td><td>${p.maxMs.toFixed(0)}</td></tr>`
      )
      .join('');
    perfTable = `<h2>接口性能抽样</h2><table><thead><tr><th>编号</th><th>模块</th><th>接口说明</th><th>次数</th><th>平均</th><th>P50</th><th>P95</th><th>最小</th><th>最大</th></tr></thead><tbody>${pr}</tbody></table>`;
  }

  const html = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8"/>
  <title>验收测试报告</title>
  <style>
    body { font-family: "Segoe UI", "Microsoft YaHei", sans-serif; margin: 28px; color: #1a1a1a; max-width: 1200px; }
    h1 { font-size: 22px; border-bottom: 2px solid #1565c0; padding-bottom: 8px; }
    h2 { font-size: 17px; margin-top: 28px; color: #0d47a1; }
    .meta { background: #f5f9ff; padding: 12px 16px; border-radius: 6px; margin: 16px 0; font-size: 14px; }
    table { border-collapse: collapse; width: 100%; margin-bottom: 20px; font-size: 13px; }
    th, td { border: 1px solid #cfd8dc; padding: 8px 10px; vertical-align: top; }
    th { background: #eceff1; text-align: left; }
    .ok { color: #1b5e20; font-weight: 600; }
    .fail { color: #b71c1c; font-weight: 600; }
    .charts { display: flex; flex-direction: column; gap: 24px; align-items: stretch; margin: 20px 0; }
    .charts > div { overflow-x: auto; max-width: 100%; }
    svg { min-width: 720px; width: 100%; height: auto; border: 1px solid #e0e0e0; background: #fafafa; display: block; }
    .hint { font-size: 12px; color: #546e7a; margin-top: 8px; }
  </style>
</head>
<body>
  <h1>中医养生系统 — 验收测试报告</h1>
  <div class="meta">
    <strong>基地址</strong> <code>${esc(meta.baseUrl)}</code><br/>
    <strong>执行时间</strong> ${esc(meta.executedAt)}<br/>
    <strong>总耗时</strong> ${meta.totalDurationMs} ms &nbsp;|&nbsp;
    <strong>用例</strong> ${rows.length} 条 &nbsp;|&nbsp;
    <strong>执行通过率</strong> ${esc(meta.passRate)}%（不含跳过）
  </div>

  <h2>按验收维度汇总</h2>
  <table>
    <thead><tr><th>验收维度</th><th>用例数</th><th>通过</th><th>失败</th><th>跳过</th><th>执行通过率</th></tr></thead>
    <tbody>${dimRows}</tbody>
  </table>

  <h2>按功能模块汇总</h2>
  <table>
    <thead><tr><th>功能模块</th><th>用例数</th><th>通过</th><th>失败</th><th>跳过</th><th>执行通过率</th></tr></thead>
    <tbody>${modRows}</tbody>
  </table>

  ${perfTable}

  ${extras.batchHtml || ''}
  ${extras.stressHtml || ''}

  <h2>结果图（可截图插入论文）</h2>
  <p class="hint">以下为矢量图内嵌预览；亦可直接使用同目录下 .svg 文件插入 Word。</p>
  <div class="charts">
    <div>${svgs.byModule}</div>
    <div>${svgs.stacked}</div>
    <div>${svgs.outcome}</div>
    <div>${svgs.histogram}</div>
    <div>${svgs.perf}</div>
  </div>

  <h2>用例明细</h2>
  <table>
    <thead><tr><th>编号</th><th>维度</th><th>模块</th><th>场景</th><th>名称</th><th>结果</th><th>ms</th><th>HTTP</th><th>业务码</th><th>说明</th></tr></thead>
    <tbody>${detailRows}</tbody>
  </table>
</body>
</html>`;
  writeFileSync(path, html, 'utf8');
}
