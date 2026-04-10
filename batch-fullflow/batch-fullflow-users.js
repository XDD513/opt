#!/usr/bin/env node
/* eslint-disable no-console */
const fs = require("fs");
const path = require("path");
const { createUserSeeds, createHealthProfile } = require("./data-generator");

const DEFAULTS = {
  baseUrl: process.env.BASE_URL || "http://127.0.0.1:8000",
  userCount: Number(process.env.USER_COUNT || 1000),
  concurrency: Number(process.env.CONCURRENCY || 10),
  imageDir: process.env.IMAGE_DIR || "D:\\Desktop\\新建文件夹\\train\\images",
  timeoutMs: Number(process.env.TIMEOUT_MS || 300000),
  retryTimes: Number(process.env.RETRY_TIMES || 3),
  usernamePrefix: process.env.USERNAME_PREFIX || "boot4",
  startSeq: Number(process.env.USER_START_SEQ || 1),
  /** 默认按舌象目录**轮询**使用全部图片；true 则每用户随机选图 */
  randomImage: process.env.RANDOM_IMAGE === "1" || process.env.RANDOM_IMAGE === "true"
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
    userCount: Number(kv.count || DEFAULTS.userCount),
    concurrency: Number(kv.concurrency || DEFAULTS.concurrency),
    imageDir: kv["image-dir"] || DEFAULTS.imageDir,
    timeoutMs: Number(kv.timeout || DEFAULTS.timeoutMs),
    retryTimes: Number(kv.retry || DEFAULTS.retryTimes),
    usernamePrefix: String(kv["username-prefix"] || DEFAULTS.usernamePrefix || "").trim(),
    startSeq: Number(kv["start-seq"] || DEFAULTS.startSeq),
    randomImage: kv["random-image"] === "true" || DEFAULTS.randomImage
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
        await sleep(500 * (i + 1));
      }
    }
  }
  throw lastErr;
}

async function requestJson({ method = "GET", url, token, body, timeoutMs = 30000 }) {
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
      const snippet = String(text || "").replace(/\s+/g, " ").slice(0, 220);
      throw new Error(`Invalid JSON response from ${url}: ${snippet}`);
    }
    if (!res.ok || data.code !== 200) {
      throw new Error(data.message || `HTTP ${res.status}`);
    }
    return data;
  } finally {
    clearTimeout(timeout);
  }
}

async function uploadTongueImage({ url, token, imagePath, timeoutMs }) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const buffer = fs.readFileSync(imagePath);
    const form = new FormData();
    const ext = path.extname(imagePath).toLowerCase();
    const type = ext === ".png" ? "image/png" : "image/jpeg";
    const blob = new Blob([buffer], { type });
    form.append("file", blob, path.basename(imagePath));

    const headers = {};
    if (token) headers.Authorization = `Bearer ${token}`;
    const res = await fetch(url, {
      method: "POST",
      headers,
      body: form,
      signal: controller.signal
    });
    const data = await res.json();
    if (!res.ok || data.code !== 200) {
      throw new Error(data.message || `upload failed ${res.status}`);
    }
    return data;
  } finally {
    clearTimeout(timeout);
  }
}

async function consumeSse(url, timeoutMs, collectData = false) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, {
      method: "GET",
      headers: { Accept: "text/event-stream" },
      signal: controller.signal
    });
    if (!res.ok || !res.body) {
      throw new Error(`SSE request failed: HTTP ${res.status}`);
    }
    const reader = res.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buf = "";
    let finished = false;
    let chunks = "";
    while (!finished) {
      const { value, done } = await reader.read();
      if (done) break;
      buf += decoder.decode(value, { stream: true });
      let idx;
      while ((idx = buf.indexOf("\n\n")) >= 0) {
        const block = buf.slice(0, idx);
        buf = buf.slice(idx + 2);
        const lines = block.split("\n");
        let evt = "message";
        const dataLines = [];
        for (const line of lines) {
          if (line.startsWith("event:")) evt = line.slice(6).trim();
          if (line.startsWith("data:")) dataLines.push(line.slice(5).trimStart());
        }
        if (collectData && evt === "message" && dataLines.length) {
          chunks += dataLines.join("\n");
        }
        if (evt === "finish") {
          finished = true;
          break;
        }
      }
    }
    if (!finished) {
      throw new Error("SSE finished event not received");
    }
    return chunks;
  } finally {
    clearTimeout(timeout);
  }
}

function stripMarkdownJsonFence(s) {
  const text = String(s || "").trim();
  if (!text.startsWith("```")) return text;
  return text.replace(/^```[a-zA-Z]*\s*/m, "").replace(/```$/m, "").trim();
}

function extractBalancedJsonObject(s) {
  const text = String(s || "");
  const start = text.indexOf("{");
  if (start < 0) return "";
  let depth = 0;
  for (let i = start; i < text.length; i += 1) {
    const ch = text[i];
    if (ch === "{") depth += 1;
    if (ch === "}") depth -= 1;
    if (depth === 0) return text.slice(start, i + 1);
  }
  return "";
}

function normalizeAiSuggestionForPlanSave(rawSuggestion) {
  const text = String(rawSuggestion || "").trim();
  if (!text) return "";

  let parsed;
  try {
    const cleaned = stripMarkdownJsonFence(text);
    const jsonText = extractBalancedJsonObject(cleaned) || cleaned;
    parsed = JSON.parse(jsonText);
  } catch {
    return text;
  }

  if (!parsed || !Array.isArray(parsed.plans)) {
    return text;
  }

  parsed.plans = parsed.plans.map((p) => {
    if (!p || typeof p !== "object") return p;
    return {
      ...p,
      type: p.type || p.planType || "",
      name: p.name || p.planName || ""
    };
  });

  return JSON.stringify(parsed);
}

function buildSubmitTestPayload(tongueRes) {
  return {
    // 与前端 SmartConstitutionTest 提交结构保持一致
    answers: {},
    appointmentId: null,
    questionIds: [],
    tongueResult: JSON.stringify({
      feature: tongueRes?.data?.feature || "AnalysisCompleted",
      features_list: tongueRes?.data?.features_list || [],
      features_detail: tongueRes?.data?.features_detail || [],
      ml_scores: tongueRes?.data?.ml_scores || null,
      image_url: tongueRes?.data?.image_url || null
    }),
    tongueFeatures: tongueRes?.data?.features_list || [],
    userSelfDescription: "批量流程自动化测试用户"
  };
}

function buildCreatePlansPayload({ userId, testId, healthSuggestion }) {
  return {
    // 与前端 createHealthPlansFromAiResult 保持一致
    userId,
    testId,
    aiSuggestion: normalizeAiSuggestionForPlanSave(healthSuggestion || "")
  };
}

function extractRecipeNames(healthSuggestion) {
  const names = new Set();
  if (!healthSuggestion) return [];
  let plans = [];
  try {
    const parsed = JSON.parse(healthSuggestion);
    plans = Array.isArray(parsed.plans) ? parsed.plans : [];
  } catch {
    return [];
  }
  const dietPlans = plans.filter((p) => (p.planType || p.type) === "DIET");
  for (const p of dietPlans) {
    const src = String(p.targetContent || "");
    const hardRegex = /[\u4e00-\u9fa5·]{2,30}(粥|汤|羹|饮|茶)/g;
    let m;
    while ((m = hardRegex.exec(src)) !== null) names.add(m[0]);
  }
  return Array.from(names).slice(0, 5);
}

function buildRecipePrompt(constitutionName, recipeName) {
  return [
    `请基于中医体质“${constitutionName || "平和质"}”生成以下药膳的标准化JSON：${recipeName}。`,
    "请返回字段：recipeName, constitutionType, season, category, difficulty, cookingTime, servings, ingredients[{name,amount,unit,note}], steps[string[]], efficacy, suitableSymptoms, contraindications, nutritionInfo{calorie,protein_g,fat_g,carb_g}, tips。",
    "字段不可省略，键名必须完全一致。constitutionType取值：PINGHE|QIXU|YANGXU|YINXU|TANSHI|SHIRE|XUEYU|QIYU|TEBING|ALL；season取值：SPRING|SUMMER|AUTUMN|WINTER|ALL；difficulty取值1-5。",
    "仅输出 JSON。"
  ].join(" ");
}

function randomImageFromDir(imageDir, imageIndexZeroBased) {
  const files = fs
    .readdirSync(imageDir)
    .filter((f) => /\.(jpg|jpeg|png)$/i.test(f))
    .map((f) => path.join(imageDir, f))
    .sort();
  if (!files.length) throw new Error(`No images found in ${imageDir}`);
  if (typeof imageIndexZeroBased === "number" && Number.isFinite(imageIndexZeroBased)) {
    return files[imageIndexZeroBased % files.length];
  }
  return files[Math.floor(Math.random() * files.length)];
}

async function runSingleUser(base, seedUser, opts, idx, total, imageIdx0) {
  const stages = [];
  const stageTimings = {};
  const stageStartedAt = {};
  const mark = (s) => {
    stages.push(s);
    stageStartedAt[s] = Date.now();
    console.log(`[${idx}/${total}] ${seedUser.username} -> ${s}`);
  };
  const markDone = (s) => {
    const start = stageStartedAt[s];
    if (typeof start === "number") {
      stageTimings[s] = Date.now() - start;
    }
  };
  let token = "";
  let userId = null;
  let testId = null;
  const flowStartedAt = Date.now();

  await withRetry(
    async () => {
      mark("register");
      await requestJson({
        method: "POST",
        url: `${base}/api/user/register`,
        body: seedUser,
        timeoutMs: opts.timeoutMs
      });
      markDone("register");
    },
    opts.retryTimes,
    "register"
  );

  await withRetry(
    async () => {
      mark("login");
      const res = await requestJson({
        method: "POST",
        url: `${base}/api/user/login`,
        body: { username: seedUser.username, password: seedUser.password },
        timeoutMs: opts.timeoutMs
      });
      token = res.data.token;
      userId = res.data.id;
      if (!token || !userId) throw new Error("login token/id missing");
      markDone("login");
    },
    opts.retryTimes,
    "login"
  );

  const profile = createHealthProfile(userId, seedUser.realName, seedUser.gender, seedUser.birthDate);
  await withRetry(
    async () => {
      mark("health-profile");
      await requestJson({
        method: "PUT",
        url: `${base}/api/health/profile`,
        token,
        body: profile,
        timeoutMs: opts.timeoutMs
      });
      markDone("health-profile");
    },
    opts.retryTimes,
    "health-profile"
  );

  const imagePath = randomImageFromDir(opts.imageDir, opts.randomImage ? undefined : imageIdx0);
  const tongueRes = await withRetry(
    async () => {
      mark("tongue-upload");
      const r = await uploadTongueImage({
        url: `${base}/api/constitution/tongue-diagnosis`,
        token,
        imagePath,
        timeoutMs: opts.timeoutMs
      });
      markDone("tongue-upload");
      return r;
    },
    opts.retryTimes,
    "tongue-upload"
  );

  await withRetry(
    async () => {
      mark("submit-test");
      const payload = buildSubmitTestPayload(tongueRes);
      const res = await requestJson({
        method: "POST",
        url: `${base}/api/constitution/test/submit`,
        token,
        body: payload,
        timeoutMs: opts.timeoutMs
      });
      testId = res.data.id;
      if (!testId) throw new Error("testId missing");
      markDone("submit-test");
    },
    opts.retryTimes,
    "submit-test"
  );

  const sseToken = encodeURIComponent(token);
  await withRetry(
    async () => {
      mark("analysis");
      const url = `${base}/api/constitution/test/ai-suggestion/stream/${testId}?token=${sseToken}&phase=analysis`;
      await consumeSse(url, opts.timeoutMs);
      markDone("analysis");
    },
    opts.retryTimes,
    "analysis"
  );

  await withRetry(
    async () => {
      mark("plans");
      const url = `${base}/api/constitution/test/ai-suggestion/stream/${testId}?token=${sseToken}&phase=plans`;
      await consumeSse(url, opts.timeoutMs);
      markDone("plans");
    },
    opts.retryTimes,
    "plans"
  );

  let report = null;
  await withRetry(
    async () => {
      mark("get-report");
      report = await requestJson({
        method: "GET",
        url: `${base}/api/constitution/test/report/${testId}`,
        token,
        timeoutMs: opts.timeoutMs
      });
      markDone("get-report");
    },
    opts.retryTimes,
    "get-report"
  );

  await withRetry(
    async () => {
      mark("save-plans");
      const payload = buildCreatePlansPayload({
        userId,
        testId,
        healthSuggestion: report?.data?.healthSuggestion || ""
      });
      await requestJson({
        method: "POST",
        url: `${base}/api/health/plan/create-from-ai`,
        token,
        body: payload,
        timeoutMs: opts.timeoutMs
      });
      markDone("save-plans");
    },
    opts.retryTimes,
    "save-plans"
  );

  const names = extractRecipeNames(report?.data?.healthSuggestion || "");
  if (!names.length) {
    throw new Error("recipe names not found from DIET plans");
  }
  const recipeJsonList = [];
  const savedRecipeIds = [];
  for (const n of names) {
    await withRetry(
      async () => {
        mark(`recipe:${n}`);
        const prompt = buildRecipePrompt(report?.data?.primaryConstitutionName || "", n);
        const url = `${base}/api/recipe/generate-json/stream?token=${sseToken}&prompt=${encodeURIComponent(prompt)}`;
        const raw = await consumeSse(url, opts.timeoutMs, true);
        const text = stripMarkdownJsonFence(raw);
        const jsonText = extractBalancedJsonObject(text) || text;
        let recipeJson = null;
        try {
          recipeJson = JSON.parse(jsonText);
        } catch {
          throw new Error(`recipe json parse failed for "${n}"`);
        }
        recipeJsonList.push(recipeJson);
        markDone(`recipe:${n}`);
      },
      opts.retryTimes,
      "recipe"
    );
  }

  await withRetry(
    async () => {
      mark("save-recipes");
      for (const recipeJson of recipeJsonList) {
        const saveRes = await requestJson({
          method: "POST",
          url: `${base}/api/recipe/save`,
          token,
          body: { json: recipeJson, test_id: testId },
          timeoutMs: opts.timeoutMs
        });
        const rid = saveRes?.data?.id;
        if (!rid) {
          throw new Error("save recipe success but recipe id missing");
        }
        savedRecipeIds.push(Number(rid));
      }
      markDone("save-recipes");
    },
    opts.retryTimes,
    "save-recipes"
  );

  await withRetry(
    async () => {
      mark("verify-favorites");
      const favRes = await requestJson({
        method: "GET",
        url: `${base}/api/recipe/favorites?pageNum=1&pageSize=100`,
        token,
        timeoutMs: opts.timeoutMs
      });
      const records = Array.isArray(favRes?.data?.records) ? favRes.data.records : [];
      const favoriteIdSet = new Set(
        records
          .map((r) => Number(r?.id))
          .filter((id) => Number.isFinite(id) && id > 0)
      );
      const missing = savedRecipeIds.filter((id) => !favoriteIdSet.has(id));
      if (missing.length) {
        throw new Error(`saved recipes are not favorited yet: ${missing.join(",")}`);
      }
      markDone("verify-favorites");
    },
    opts.retryTimes,
    "verify-favorites"
  );

  await withRetry(
    async () => {
      mark("logout");
      await requestJson({
        method: "POST",
        url: `${base}/api/user/logout`,
        token,
        timeoutMs: opts.timeoutMs
      });
      markDone("logout");
    },
    opts.retryTimes,
    "logout"
  );

  return {
    success: true,
    username: seedUser.username,
    userId,
    testId,
    imagePath,
    recipeCount: names.length,
    savedRecipeIds,
    favoritedVerified: true,
    stages,
    stageTimings,
    totalDurationMs: Date.now() - flowStartedAt
  };
}

async function runPool(tasks, concurrency) {
  const results = [];
  let index = 0;
  const workers = Array.from({ length: Math.max(1, concurrency) }).map(async () => {
    while (true) {
      const current = index;
      index += 1;
      if (current >= tasks.length) break;
      results[current] = await tasks[current]();
    }
  });
  await Promise.all(workers);
  return results;
}

async function main() {
  const opts = parseArgs();
  const base = normalizeBase(opts.baseUrl);
  if (!fs.existsSync(opts.imageDir)) {
    throw new Error(`Image dir not found: ${opts.imageDir}`);
  }
  const outputDir = path.join(__dirname, "output");
  if (!fs.existsSync(outputDir)) fs.mkdirSync(outputDir, { recursive: true });

  const seeds = createUserSeeds(opts.userCount, {
    usernamePrefix: opts.usernamePrefix,
    startSeq: opts.startSeq
  });
  const tasks = seeds.map((seed, idx) => async () => {
    try {
      return await runSingleUser(base, seed, opts, idx + 1, seeds.length, idx);
    } catch (err) {
      return {
        success: false,
        username: seed.username,
        error: err.message,
        totalDurationMs: null
      };
    }
  });

  console.log(`Start batch flow users=${opts.userCount}, concurrency=${opts.concurrency}`);
  const startedAt = new Date().toISOString();
  const results = await runPool(tasks, opts.concurrency);
  const endedAt = new Date().toISOString();
  const successCount = results.filter((r) => r.success).length;
  const failed = results.filter((r) => !r.success);
  const report = {
    config: opts,
    startedAt,
    endedAt,
    successCount,
    failedCount: failed.length,
    results
  };
  const outputFile = path.join(outputDir, "batch-fullflow-result.json");
  fs.writeFileSync(outputFile, JSON.stringify(report, null, 2), "utf-8");
  console.log(`Done. success=${successCount}, failed=${failed.length}`);
  console.log(`Report: ${outputFile}`);
  if (failed.length) {
    process.exitCode = 1;
  }
}

main().catch((err) => {
  console.error("Fatal:", err.message);
  process.exit(1);
});
