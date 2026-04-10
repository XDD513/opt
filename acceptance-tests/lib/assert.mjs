/**
 * 统一封装接口响应判定，便于功能验收 / 规则验收 / 安全验收。
 */
export function wrapSuccess(res, expectBodyCode = 200) {
  const httpStatus = res.status;
  const bodyCode = res.data?.code;
  const ok = httpStatus === 200 && bodyCode === expectBodyCode;
  const detail = ok
    ? `code=${bodyCode}`
    : `http=${httpStatus} bodyCode=${bodyCode ?? 'n/a'} msg=${res.data?.message ?? ''}`;
  return { ok, httpStatus, bodyCode, detail, note: '' };
}

/** 业务规则类：期望特定业务码（如未登录 401、未舌诊 400） */
export function wrapExpectBodyCode(res, expectBodyCode) {
  const httpStatus = res.status;
  const bodyCode = res.data?.code;
  const ok = httpStatus === 200 && bodyCode === expectBodyCode;
  const detail = ok
    ? `按预期返回业务码 ${expectBodyCode}`
    : `期望业务码 ${expectBodyCode}，实际 http=${httpStatus} bodyCode=${bodyCode ?? 'n/a'} msg=${res.data?.message ?? ''}`;
  return { ok, httpStatus, bodyCode, detail, note: '' };
}

/** HTTP 层期望（如未携带 Token 返回 401） */
export function wrapExpectHttp(res, expectHttp) {
  const httpStatus = res.status;
  const bodyCode = res.data?.code;
  const ok = httpStatus === expectHttp;
  const detail = ok
    ? `HTTP ${httpStatus} 符合预期`
    : `期望 HTTP ${expectHttp}，实际 ${httpStatus} bodyCode=${bodyCode ?? 'n/a'}`;
  return { ok, httpStatus, bodyCode, detail, note: '' };
}
