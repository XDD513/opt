/**
 * 带验证码的登录（与 acceptance-tests/rec-eval.mjs 逻辑一致，供批量脚本使用）
 */
const CAPTCHA_WHITELIST = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
/** 与后端 CaptchaService.CODE_LEN 一致 */
const CAPTCHA_CODE_LEN = 5;

async function ocrCaptchaBuffer(buf) {
  const { createWorker } = await import("tesseract.js");
  const worker = await createWorker("eng");
  await worker.setParameters({
    tessedit_char_whitelist: CAPTCHA_WHITELIST,
    tessedit_pageseg_mode: "7",
  });
  try {
    const {
      data: { text },
    } = await worker.recognize(buf);
    return String(text || "")
      .replace(/\s+/g, "")
      .toUpperCase();
  } finally {
    await worker.terminate();
  }
}

/**
 * @param {string} base 如 http://121.43.140.75
 * @returns {{ token: string, userId: string|number }}
 */
async function loginWithCaptcha(base, username, password, timeoutMs, maxAttempts = 25) {
  const safeTimeout = timeoutMs || 60000;
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const capController = new AbortController();
    const capTimer = setTimeout(() => capController.abort(), safeTimeout);
    let capRes;
    try {
      capRes = await fetch(`${base}/api/captcha/image`, { signal: capController.signal });
    } finally {
      clearTimeout(capTimer);
    }
    const capJson = await capRes.json();
    if (capJson.code !== 200) {
      throw new Error(capJson.message || "获取验证码失败");
    }
    const { captchaId, imageBase64 } = capJson.data || {};
    if (!captchaId || !imageBase64) throw new Error("验证码接口返回异常");

    const buf = Buffer.from(imageBase64, "base64");
    let raw = await ocrCaptchaBuffer(buf);
    raw = [...raw].filter((ch) => CAPTCHA_WHITELIST.includes(ch)).join("");
    const captchaCode = raw.slice(0, CAPTCHA_CODE_LEN);
    if (captchaCode.length < CAPTCHA_CODE_LEN) continue;

    const loginController = new AbortController();
    const loginTimer = setTimeout(() => loginController.abort(), safeTimeout);
    let loginRes;
    try {
      loginRes = await fetch(`${base}/api/user/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password, captchaId, captchaCode }),
        signal: loginController.signal,
      });
    } finally {
      clearTimeout(loginTimer);
    }
    const loginJson = await loginRes.json();
    if (loginJson.code === 200 && loginJson.data?.token) {
      return {
        token: loginJson.data.token,
        userId: loginJson.data.id,
      };
    }
    const msg = loginJson.message || "";
    if (msg && !msg.includes("验证码")) {
      throw new Error(msg);
    }
  }
  throw new Error(`验证码登录失败，已重试 ${maxAttempts} 次`);
}

/**
 * 带验证码注册（批量脚本使用，与登录共用 OCR 逻辑）
 * @param {object} userPayload 注册 JSON 体（不含 captchaId/captchaCode，由本函数填入）
 */
async function registerWithCaptcha(base, userPayload, timeoutMs, maxAttempts = 25) {
  const safeTimeout = timeoutMs || 60000;
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const capController = new AbortController();
    const capTimer = setTimeout(() => capController.abort(), safeTimeout);
    let capRes;
    try {
      capRes = await fetch(`${base}/api/captcha/image`, { signal: capController.signal });
    } finally {
      clearTimeout(capTimer);
    }
    const capJson = await capRes.json();
    if (capJson.code !== 200) {
      throw new Error(capJson.message || "获取验证码失败");
    }
    const { captchaId, imageBase64 } = capJson.data || {};
    if (!captchaId || !imageBase64) throw new Error("验证码接口返回异常");

    const buf = Buffer.from(imageBase64, "base64");
    let raw = await ocrCaptchaBuffer(buf);
    raw = [...raw].filter((ch) => CAPTCHA_WHITELIST.includes(ch)).join("");
    const captchaCode = raw.slice(0, CAPTCHA_CODE_LEN);
    if (captchaCode.length < CAPTCHA_CODE_LEN) continue;

    const body = {
      ...userPayload,
      captchaId,
      captchaCode,
    };

    const regController = new AbortController();
    const regTimer = setTimeout(() => regController.abort(), safeTimeout);
    let regRes;
    try {
      regRes = await fetch(`${base}/api/user/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
        signal: regController.signal,
      });
    } finally {
      clearTimeout(regTimer);
    }
    const regJson = await regRes.json();
    if (regJson.code === 200) {
      return;
    }
    const msg = regJson.message || "";
    if (msg && !msg.includes("验证码")) {
      throw new Error(msg);
    }
  }
  throw new Error(`验证码注册失败，已重试 ${maxAttempts} 次`);
}

module.exports = { loginWithCaptcha, registerWithCaptcha };
