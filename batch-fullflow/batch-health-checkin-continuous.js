#!/usr/bin/env node
/* eslint-disable no-console */
const mysql = require("mysql2/promise");

const DEFAULTS = {
  baseUrl: process.env.BASE_URL || "http://127.0.0.1:8000",
  dbHost: process.env.DB_HOST || "127.0.0.1",
  dbPort: Number(process.env.DB_PORT || 3306),
  dbUser: process.env.DB_USER || "root",
  dbPassword: process.env.DB_PASSWORD || "5845201314",
  dbName: process.env.DB_NAME || "tcm_health_system",
  password: process.env.USER_PASSWORD || "123456",
  startDate: process.env.CHECKIN_START_DATE || "2026-03-20",
  endDate: process.env.CHECKIN_END_DATE || "2026-04-08",
  timeoutMs: Number(process.env.TIMEOUT_MS || 30000),
  retryTimes: Number(process.env.RETRY_TIMES || 2),
  concurrency: Number(process.env.CONCURRENCY || 10)
};

function parseArgs() {
  const args = process.argv.slice(2);
  const kv = {};
  for (const a of args) {
    if (!a.startsWith("--")) continue;
    const [k, v] = a.slice(2).split("=");
    kv[k] = v ?? "";
  }
  return {
    baseUrl: kv["base-url"] || DEFAULTS.baseUrl,
    dbHost: kv["db-host"] || DEFAULTS.dbHost,
    dbPort: Number(kv["db-port"] || DEFAULTS.dbPort),
    dbUser: kv["db-user"] || DEFAULTS.dbUser,
    dbPassword: kv["db-password"] || DEFAULTS.dbPassword,
    dbName: kv["db-name"] || DEFAULTS.dbName,
    password: kv.password || DEFAULTS.password,
    startDate: kv["start-date"] || DEFAULTS.startDate,
    endDate: kv["end-date"] || DEFAULTS.endDate,
    timeoutMs: Number(kv.timeout || DEFAULTS.timeoutMs),
    retryTimes: Number(kv.retry || DEFAULTS.retryTimes),
    concurrency: Number(kv.concurrency || DEFAULTS.concurrency)
  };
}

function normalizeBase(baseUrl) {
  return String(baseUrl || "").replace(/\/+$/, "");
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function withRetry(fn, retries, stageName) {
  let lastErr = null;
  for (let i = 0; i <= retries; i += 1) {
    try {
      return await fn();
    } catch (err) {
      lastErr = err;
      if (i < retries) {
        console.warn(`[retry] ${stageName} attempt ${i + 1} failed: ${err.message}`);
        await sleep(300 * (i + 1));
      }
    }
  }
  throw lastErr;
}

function toDateRange(startDate, endDate) {
  const arr = [];
  const cur = new Date(`${startDate}T00:00:00`);
  const end = new Date(`${endDate}T00:00:00`);
  while (cur <= end) {
    const y = cur.getFullYear();
    const m = String(cur.getMonth() + 1).padStart(2, "0");
    const d = String(cur.getDate()).padStart(2, "0");
    arr.push(`${y}-${m}-${d}`);
    cur.setDate(cur.getDate() + 1);
  }
  return arr;
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function randomFixed(min, max, precision = 1) {
  const n = Math.random() * (max - min) + min;
  return Number(n.toFixed(precision));
}

function pickMoodScore() {
  const arr = [1, 2, 3, 4, 5];
  return arr[randomInt(0, arr.length - 1)];
}

// 生成确定性用户基线（不同用户之间分布不同，同一用户每天围绕基线波动）
function hashToSeed(text) {
  const s = String(text || "");
  let h = 2166136261 >>> 0;
  for (let i = 0; i < s.length; i += 1) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

function mulberry32(seed) {
  let t = seed >>> 0;
  return function rand() {
    t += 0x6D2B79F5;
    let x = t;
    x = Math.imul(x ^ (x >>> 15), 1 | x);
    x ^= x + Math.imul(x ^ (x >>> 7), 61 | x);
    return ((x ^ (x >>> 14)) >>> 0) / 4294967296;
  };
}

function randFrom(prng, min, max, precision = 0) {
  const v = min + (max - min) * prng();
  return precision > 0 ? Number(v.toFixed(precision)) : Math.round(v);
}

function buildUserBaseline(user) {
  const seed = hashToSeed(`${user.id}:${user.username || ""}`);
  const r = mulberry32(seed);
  const baseline = {
    weight: randFrom(r, 48, 85, 1), // kg
    heartRate: randFrom(r, 58, 88, 0),
    systolic: randFrom(r, 105, 132, 0),
    diastolic: randFrom(r, 65, 86, 0),
    sleepDuration: randFrom(r, 6.0, 8.5, 1),
    exercise: randFrom(r, 10, 60, 0), // min
    water: randFrom(r, 1200, 2800, 0), // ml
    mood: randFrom(r, 2, 4, 0),
    health: randFrom(r, 2, 4, 0),
    energy: randFrom(r, 2, 4, 0)
  };
  return baseline;
}

function jitterAround(prng, base, span, precision = 0) {
  // 在 [base - span, base + span] 内轻微波动
  const v = base + (prng() * 2 - 1) * span;
  return precision > 0 ? Number(v.toFixed(precision)) : Math.round(v);
}

function choice(list) {
  return list[randomInt(0, list.length - 1)];
}

function generateRandomRemark(checkinDate, ctx) {
  const moods = ["状态不错", "精神一般", "有点疲惫", "心情很好", "略有压力"];
  const foods = ["清淡饮食", "荤素搭配", "少盐少油", "晚饭较早", "水果加餐"];
  const sports = [
    "快走20分钟",
    "拉伸放松",
    "瑜伽练习",
    "室内骑行",
    "慢跑",
    "八段锦",
    "广场舞"
  ];
  const sleeps = ["午休15分钟", "按时入睡", "晚睡半小时", "夜里易醒", "睡前冥想"];
  const waters = ["多喝温水", "饮水充足", "今天水少了", "绿茶一杯", "姜枣茶"];
  const extras = ["注意腰背放松", "避免久坐", "适度活动", "少吃甜食", "控制咖啡因"];

  const parts = [
    `${checkinDate}`,
    choice(moods),
    choice(foods),
    choice(sports),
    choice(sleeps),
    choice(waters),
    choice(extras)
  ];
  // 组装为简短自然语句
  return parts.join("，");
}

function buildCheckinPayload(userId, checkinDate, baseline, prng) {
  // 每天在个人基线附近随机波动
  const systolic = jitterAround(prng, baseline.systolic, 10, 0);
  const diastolic = jitterAround(prng, baseline.diastolic, 8, 0);
  const moodScore = Math.min(5, Math.max(1, jitterAround(prng, baseline.mood, 1, 0)));
  const healthScore = Math.min(5, Math.max(1, jitterAround(prng, baseline.health, 1, 0)));
  const mood = Math.min(5, Math.max(1, jitterAround(prng, baseline.mood, 1, 0)));
  const energyLevel = Math.min(5, Math.max(1, jitterAround(prng, baseline.energy, 1, 0)));
  const remark = generateRandomRemark(checkinDate, {});
  const payload = {
    userId,
    planId: null,
    checkinDate, // yyyy-MM-dd
    checkinType: "OTHER",
    weight: jitterAround(prng, baseline.weight, 2.2, 1), // decimal(5,2)
    bloodPressure: `${systolic}/${diastolic}`, // varchar(20)
    heartRate: jitterAround(prng, baseline.heartRate, 8, 0), // int
    sleepDuration: jitterAround(prng, baseline.sleepDuration, 1.2, 1), // decimal(4,2)
    sleepQuality: Math.min(5, Math.max(1, jitterAround(prng, 3, 2, 0))), // tinyint
    exerciseDuration: Math.max(0, jitterAround(prng, baseline.exercise, 25, 0)), // int
    waterIntake: Math.max(300, jitterAround(prng, baseline.water, 600, 0)), // int
    moodScore, // tinyint
    healthScore, // tinyint
    mood, // tinyint
    energyLevel, // tinyint
    images: null,
    remark,
    content: JSON.stringify({
      source: "batch-health-checkin-continuous",
      checkinDate,
      moodScore,
      healthScore,
      energyLevel
    })
  };
  return payload;
}

async function requestJson({ method = "GET", url, token, body, timeoutMs }) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const headers = { "Content-Type": "application/json" };
    if (token) headers.Authorization = `Bearer ${token}`;
    const res = await fetch(url, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
      signal: controller.signal
    });
    const text = await res.text();
    let data = {};
    try {
      data = text ? JSON.parse(text) : {};
    } catch {
      throw new Error(`Invalid JSON response ${url}: ${String(text).slice(0, 120)}`);
    }
    if (!res.ok || data.code !== 200) {
      throw new Error(data.message || `HTTP ${res.status}`);
    }
    return data;
  } finally {
    clearTimeout(timeout);
  }
}

async function queryUsers(opts) {
  const conn = await mysql.createConnection({
    host: opts.dbHost,
    port: opts.dbPort,
    user: opts.dbUser,
    password: opts.dbPassword,
    database: opts.dbName,
    charset: "utf8mb4",
    supportBigNumbers: true,
    bigNumberStrings: true
  });
  try {
    const [rows] = await conn.execute(
      `
      SELECT id, username, real_name AS realName
      FROM tcm_user
      WHERE role_type <> 1
      ORDER BY id ASC
      `
    );
    return rows;
  } finally {
    await conn.end();
  }
}

async function checkinExists(base, token, userId, checkinDate, timeoutMs) {
  const url = `${base}/api/health/checkin/date?userId=${encodeURIComponent(userId)}&date=${encodeURIComponent(checkinDate)}`;
  const res = await requestJson({
    method: "GET",
    url,
    token,
    timeoutMs
  });
  return Boolean(res?.data);
}

async function runForUser(base, user, opts, dates, idx, total) {
  let token = "";
  const username = user.username;
  const realName = user.realName || "";
  // MySQL bigint -> JS Number 会丢精度，必须全程按字符串处理
  const userId = String(user.id);
  const userBaseline = buildUserBaseline(user);
  const dayPrng = mulberry32(hashToSeed(`${user.id}:${dates[0]}`));

  await withRetry(
    async () => {
      const login = await requestJson({
        method: "POST",
        url: `${base}/api/user/login`,
        body: { username, password: opts.password },
        timeoutMs: opts.timeoutMs
      });
      token = login?.data?.token;
      if (!token) throw new Error("login token missing");
    },
    opts.retryTimes,
    `login:${username}`
  );

  let successCount = 0;
  let skipCount = 0;
  let failCount = 0;
  for (const d of dates) {
    // 每天进位 seed，保证同一用户不同日期也随机
    const payload = buildCheckinPayload(userId, d, userBaseline, dayPrng);
    try {
      const existed = await withRetry(
        async () => checkinExists(base, token, userId, d, opts.timeoutMs),
        opts.retryTimes,
        `exists:${username}:${d}`
      );
      if (existed) {
        skipCount += 1;
        continue;
      }

      await withRetry(
        async () =>
          requestJson({
            method: "POST",
            url: `${base}/api/health/checkin`,
            token,
            body: payload,
            timeoutMs: opts.timeoutMs
          }),
        opts.retryTimes,
        `checkin:${username}:${d}`
      );

      successCount += 1;
      console.log(`[${idx}/${total}] ${username} ${realName} ${JSON.stringify(payload)}`);
    } catch (err) {
      failCount += 1;
      console.error(`[${idx}/${total}] ${username} ${realName} ${d} 打卡失败: ${err.message}`);
    }
  }

  await withRetry(
    async () =>
      requestJson({
        method: "POST",
        url: `${base}/api/user/logout`,
        token,
        timeoutMs: opts.timeoutMs
      }),
    opts.retryTimes,
    `logout:${username}`
  );

  return { username, realName, userId, successCount, skipCount, failCount };
}

async function main() {
  const opts = parseArgs();
  const base = normalizeBase(opts.baseUrl);
  const dates = toDateRange(opts.startDate, opts.endDate);
  if (!dates.length) throw new Error("date range is empty");

  const users = await queryUsers(opts);
  if (!users.length) {
    console.log("未查询到非管理员用户，脚本结束。");
    return;
  }

  console.log(`用户数=${users.length}, 日期数=${dates.length}, 并发=${opts.concurrency}, 总任务=${users.length * dates.length}`);
  const tasks = users.map((u, idx) => async () => {
    try {
      return await runForUser(base, u, opts, dates, idx + 1, users.length);
    } catch (err) {
      console.error(`[${idx + 1}/${users.length}] ${u.username} ${u.realName || ""} 用户流程失败: ${err.message}`);
      return {
        username: u.username,
        realName: u.realName || "",
        userId: String(u.id),
        successCount: 0,
        skipCount: 0,
        failCount: dates.length,
        error: err.message
      };
    }
  });

  // 简单协程池
  const results = [];
  let cursor = 0;
  const workers = Array.from({ length: Math.max(1, opts.concurrency) }).map(async () => {
    while (true) {
      const i = cursor;
      cursor += 1;
      if (i >= tasks.length) break;
      results[i] = await tasks[i]();
    }
  });
  await Promise.all(workers);

  const summary = results.reduce(
    (acc, it) => {
      acc.success += it.successCount || 0;
      acc.skip += it.skipCount || 0;
      acc.fail += it.failCount || 0;
      return acc;
    },
    { success: 0, skip: 0, fail: 0 }
  );
  console.log(`完成: success=${summary.success}, skip=${summary.skip}, fail=${summary.fail}`);
}

main().catch((err) => {
  console.error("Fatal:", err.message);
  process.exit(1);
});
