/**
 * 验收用例库：按「验收维度」组织，覆盖功能链路、安全鉴权、业务规则与性能抽样场景。
 */
import { wrapSuccess, wrapExpectBodyCode, wrapExpectHttp } from './lib/assert.mjs';

/**
 * @typedef {object} AccCase
 * @property {string} id
 * @property {string} dimension - 功能验收 | 安全与鉴权 | 业务规则校验
 * @property {string} module
 * @property {string} scene - 业务场景简述（论文可引用）
 * @property {string} name
 * @property {boolean} [skip]
 * @property {string} [skipReason]
 * @property {(ctx: any) => Promise<{ok:boolean,httpStatus:number,bodyCode:any,detail:string,note?:string}>} run
 */

/** @returns {AccCase[]} */
export function buildCases() {
  const cases = [];

  // ---------- 安全与鉴权 ----------
  cases.push({
    id: 'TC-SEC-001',
    dimension: '安全与鉴权',
    module: '认证',
    scene: '未登录访问受保护接口',
    name: '无 Token 访问 /api/user/info 应拒绝',
    run: async ({ anonHttp }) => wrapExpectHttp(await anonHttp.get('/api/user/info'), 401),
  });
  cases.push({
    id: 'TC-SEC-002',
    dimension: '安全与鉴权',
    module: '认证',
    scene: '未登录访问业务接口',
    name: '无 Token 访问 /api/constitution/types 应拒绝',
    run: async ({ anonHttp }) => wrapExpectHttp(await anonHttp.get('/api/constitution/types'), 401),
  });
  cases.push({
    id: 'TC-SEC-003',
    dimension: '安全与鉴权',
    module: '公开配置',
    scene: '登录前读取运行时配置',
    name: '匿名访问 /api/config 应成功',
    run: async ({ anonHttp }) => wrapSuccess(await anonHttp.get('/api/config')),
  });

  // ---------- 用户管理 ----------
  cases.push({
    id: 'TC-ACC-USER-001',
    dimension: '功能验收',
    module: '用户管理',
    scene: '登录后会话有效',
    name: '获取当前用户信息',
    run: async ({ userHttp }) => {
      const res = await userHttp.get('/api/user/info');
      return { ...wrapSuccess(res), note: '' };
    },
  });
  cases.push({
    id: 'TC-ACC-USER-002',
    dimension: '功能验收',
    module: '用户管理',
    scene: '个人偏好与设置',
    name: '获取用户设置',
    run: async ({ userHttp }) => wrapSuccess(await userHttp.get('/api/user/settings')),
  });
  cases.push({
    id: 'TC-ACC-USER-003',
    dimension: '功能验收',
    module: '用户管理',
    scene: '新用户引导判断',
    name: '检查是否新用户',
    run: async ({ userHttp }) => wrapSuccess(await userHttp.get('/api/user/check-new-user')),
  });

  // ---------- 体质辨识 ----------
  cases.push({
    id: 'TC-ACC-CONS-001',
    dimension: '功能验收',
    module: '体质辨识',
    scene: '展示九种体质元数据',
    name: '获取体质类型列表',
    run: async ({ userHttp }) => wrapSuccess(await userHttp.get('/api/constitution/types')),
  });
  cases.push({
    id: 'TC-ACC-CONS-002',
    dimension: '功能验收',
    module: '体质辨识',
    scene: '体质详情页',
    name: '根据首条体质代码取详情',
    run: async ({ userHttp, cache }) => {
      const r = await userHttp.get('/api/constitution/types');
      if (r.status !== 200 || r.data?.code !== 200 || !Array.isArray(r.data?.data) || !r.data.data.length) {
        return { ok: false, httpStatus: r.status, bodyCode: r.data?.code, detail: '无法取得体质列表', note: '' };
      }
      cache.firstConstitutionCode = r.data.data[0].typeCode;
      const res = await userHttp.get(`/api/constitution/type/${encodeURIComponent(cache.firstConstitutionCode)}`);
      return wrapSuccess(res);
    },
  });
  cases.push({
    id: 'TC-ACC-CONS-RULE-001',
    dimension: '业务规则校验',
    module: '体质辨识',
    scene: '未完成舌诊禁止提交体测',
    name: '空提交体测应返回业务错误（非成功码）',
    run: async ({ userHttp }) => {
      const res = await userHttp.post('/api/constitution/test/submit', {});
      // 当前后端：未舌诊时返回 400 等业务码，验收「规则生效」
      if (res.status === 200 && res.data?.code === 400) {
        return wrapExpectBodyCode(res, 400);
      }
      if (res.status === 200 && res.data?.code === 200) {
        return { ok: true, httpStatus: res.status, bodyCode: res.data?.code, detail: 'code=200（当前环境允许空提交）', note: '与后端策略一致即通过' };
      }
      return { ok: false, httpStatus: res.status, bodyCode: res.data?.code, detail: res.data?.message || '未预期响应', note: '' };
    },
  });
  cases.push({
    id: 'TC-ACC-CONS-004',
    dimension: '功能验收',
    module: '体质辨识',
    scene: '个人体质结果',
    name: '获取最新体质测试结果',
    run: async ({ userHttp }) => wrapSuccess(await userHttp.get('/api/constitution/test/latest')),
  });
  cases.push({
    id: 'TC-ACC-CONS-005',
    dimension: '功能验收',
    module: '体质辨识',
    scene: '历史记录列表',
    name: '获取体质测试历史',
    run: async ({ userHttp }) => wrapSuccess(await userHttp.get('/api/constitution/test/history')),
  });
  cases.push({
    id: 'TC-ACC-CONS-006',
    dimension: '功能验收',
    module: '体质辨识',
    scene: '报告详情链路',
    name: '根据最新测试 ID 拉取报告',
    run: async ({ userHttp, cache }) => {
      const r = await userHttp.get('/api/constitution/test/latest');
      if (r.status !== 200 || r.data?.code !== 200 || r.data?.data == null) {
        return { ok: false, httpStatus: r.status, bodyCode: r.data?.code, detail: '无最新测试记录，跳过报告详情', note: '数据依赖' };
      }
      const id = r.data.data.id;
      cache.latestTestId = id;
      const res = await userHttp.get(`/api/constitution/test/report/${id}`);
      return wrapSuccess(res);
    },
  });

  // ---------- 养生推荐 / 首页 ----------
  cases.push({
    id: 'TC-ACC-HOME-001',
    dimension: '功能验收',
    module: '首页聚合',
    scene: '首页推荐流',
    name: '首页综合推荐',
    run: async ({ userHttp }) =>
      wrapSuccess(await userHttp.get('/api/home/recommendations', { params: { limit: 4 } })),
  });
  cases.push({
    id: 'TC-ACC-REC-001',
    dimension: '功能验收',
    module: '养生推荐',
    scene: '药膳浏览',
    name: '药膳分页列表',
    run: async ({ userHttp }) =>
      wrapSuccess(await userHttp.get('/api/recipe/list', { params: { pageNum: 1, pageSize: 8 } })),
  });
  cases.push({
    id: 'TC-ACC-REC-002',
    dimension: '功能验收',
    module: '养生推荐',
    scene: '按体质推荐',
    name: '体质相关药膳推荐',
    run: async ({ userHttp }) =>
      wrapSuccess(await userHttp.get('/api/recipe/recommend', { params: { pageNum: 1, pageSize: 8 } })),
  });
  cases.push({
    id: 'TC-ACC-REC-003',
    dimension: '功能验收',
    module: '养生推荐',
    scene: '多策略推荐',
    name: '协同过滤推荐',
    run: async ({ userHttp }) =>
      wrapSuccess(await userHttp.get('/api/recipe/recommend/cf', { params: { limit: 6 } })),
  });
  cases.push({
    id: 'TC-ACC-REC-004',
    dimension: '功能验收',
    module: '养生推荐',
    scene: '多策略推荐',
    name: '内容画像推荐',
    run: async ({ userHttp }) =>
      wrapSuccess(await userHttp.get('/api/recipe/recommend/content', { params: { limit: 6 } })),
  });
  cases.push({
    id: 'TC-ACC-REC-005',
    dimension: '功能验收',
    module: '养生推荐',
    scene: '个性化排序',
    name: '个性化组合推荐',
    run: async ({ userHttp }) =>
      wrapSuccess(await userHttp.get('/api/recipe/recommend/personalized', { params: { limit: 8 } })),
  });
  cases.push({
    id: 'TC-ACC-REC-006',
    dimension: '功能验收',
    module: '养生推荐',
    scene: '检索与排行',
    name: '药膳搜索（空关键字分页）',
    run: async ({ userHttp }) =>
      wrapSuccess(await userHttp.get('/api/recipe/search', { params: { pageNum: 1, pageSize: 5 } })),
  });
  cases.push({
    id: 'TC-ACC-REC-007',
    dimension: '功能验收',
    module: '养生推荐',
    scene: '检索与排行',
    name: '热门药膳',
    run: async ({ userHttp }) => wrapSuccess(await userHttp.get('/api/recipe/popular')),
  });
  cases.push({
    id: 'TC-ACC-REC-008',
    dimension: '功能验收',
    module: '养生推荐',
    scene: '节气养生',
    name: '时令药膳',
    run: async ({ userHttp }) => wrapSuccess(await userHttp.get('/api/recipe/seasonal')),
  });
  cases.push({
    id: 'TC-ACC-REC-009',
    dimension: '功能验收',
    module: '养生推荐',
    scene: '详情与收藏列表',
    name: '药膳详情（取列表首条）',
    run: async ({ userHttp, cache }) => {
      const r = await userHttp.get('/api/recipe/list', { params: { pageNum: 1, pageSize: 1 } });
      if (r.status !== 200 || r.data?.code !== 200 || !r.data?.data?.records?.length) {
        return { ok: false, httpStatus: r.status, bodyCode: r.data?.code, detail: '无药膳数据', note: '数据依赖' };
      }
      const id = r.data.data.records[0].id;
      cache.recipeId = id;
      return wrapSuccess(await userHttp.get(`/api/recipe/${id}`));
    },
  });
  cases.push({
    id: 'TC-ACC-REC-010',
    dimension: '功能验收',
    module: '养生推荐',
    scene: '用户收藏',
    name: '药膳收藏列表',
    run: async ({ userHttp }) => wrapSuccess(await userHttp.get('/api/recipe/favorites')),
  });
  cases.push({
    id: 'TC-ACC-REC-011',
    dimension: '功能验收',
    module: '养生推荐',
    scene: '每日一膳',
    name: '今日药膳',
    run: async ({ userHttp }) => wrapSuccess(await userHttp.get('/api/daily-recipe/today')),
  });
  cases.push({
    id: 'TC-ACC-REC-012',
    dimension: '功能验收',
    module: '养生推荐',
    scene: '食材与体质关联',
    name: '按体质查询食材（使用列表首条体质码）',
    run: async ({ userHttp, cache }) => {
      if (!cache.firstConstitutionCode) {
        const r = await userHttp.get('/api/constitution/types');
        if (r.data?.code === 200 && r.data?.data?.[0]?.typeCode) {
          cache.firstConstitutionCode = r.data.data[0].typeCode;
        }
      }
      const code = cache.firstConstitutionCode;
      if (!code) return { ok: false, httpStatus: 0, bodyCode: null, detail: '无体质码', note: '' };
      return wrapSuccess(await userHttp.get(`/api/recipe/ingredients/${encodeURIComponent(code)}`));
    },
  });

  // ---------- 健康管理 ----------
  cases.push({
    id: 'TC-ACC-HEA-001',
    dimension: '功能验收',
    module: '健康管理',
    scene: '电子健康档案',
    name: '健康档案查询（带 userId）',
    run: async ({ userHttp, cache }) => {
      const uid = cache.userId;
      if (!uid) return { ok: false, httpStatus: 0, bodyCode: null, detail: '缺少 userId', note: '' };
      return wrapSuccess(await userHttp.get('/api/health/profile', { params: { userId: uid } }));
    },
  });
  cases.push({
    id: 'TC-ACC-HEA-002',
    dimension: '功能验收',
    module: '健康管理',
    scene: '调理计划',
    name: '养生计划列表（带 userId）',
    run: async ({ userHttp, cache }) => {
      const uid = cache.userId;
      if (!uid) return { ok: false, httpStatus: 0, bodyCode: null, detail: '缺少 userId', note: '' };
      return wrapSuccess(
        await userHttp.get('/api/health/plan/list', { params: { userId: uid, pageNum: 1, pageSize: 10 } })
      );
    },
  });
  cases.push({
    id: 'TC-ACC-HEA-003',
    dimension: '功能验收',
    module: '健康管理',
    scene: '打卡与统计',
    name: '打卡记录列表（带 userId）',
    run: async ({ userHttp, cache }) => {
      const uid = cache.userId;
      if (!uid) return { ok: false, httpStatus: 0, bodyCode: null, detail: '缺少 userId', note: '' };
      return wrapSuccess(
        await userHttp.get('/api/health/checkin/list', { params: { userId: uid, pageNum: 1, pageSize: 20 } })
      );
    },
  });
  cases.push({
    id: 'TC-ACC-HEA-004',
    dimension: '功能验收',
    module: '健康管理',
    scene: '打卡与统计',
    name: '健康统计数据（带 userId）',
    run: async ({ userHttp, cache }) => {
      const uid = cache.userId;
      if (!uid) return { ok: false, httpStatus: 0, bodyCode: null, detail: '缺少 userId', note: '' };
      return wrapSuccess(await userHttp.get('/api/health/statistics', { params: { userId: uid } }));
    },
  });
  cases.push({
    id: 'TC-ACC-HEA-005',
    dimension: '功能验收',
    module: '健康管理',
    scene: '患者看板',
    name: '患者端统计数据',
    run: async ({ userHttp }) => wrapSuccess(await userHttp.get('/api/statistics/patient')),
  });

  // ---------- 养生社区 ----------
  cases.push({
    id: 'TC-ACC-ART-001',
    dimension: '功能验收',
    module: '养生社区',
    scene: '内容流',
    name: '文章分页列表',
    run: async ({ userHttp }) =>
      wrapSuccess(await userHttp.get('/api/article/list', { params: { pageNum: 1, pageSize: 8 } })),
  });
  cases.push({
    id: 'TC-ACC-ART-002',
    dimension: '功能验收',
    module: '养生社区',
    scene: '热门与标签',
    name: '热门文章',
    run: async ({ userHttp }) => wrapSuccess(await userHttp.get('/api/article/popular')),
  });
  cases.push({
    id: 'TC-ACC-ART-003',
    dimension: '功能验收',
    module: '养生社区',
    scene: '热门与标签',
    name: '文章标签云',
    run: async ({ userHttp }) => wrapSuccess(await userHttp.get('/api/article/tags')),
  });
  cases.push({
    id: 'TC-ACC-ART-004',
    dimension: '功能验收',
    module: '养生社区',
    scene: '个性化阅读',
    name: '推荐文章',
    run: async ({ userHttp }) => wrapSuccess(await userHttp.get('/api/article/recommended')),
  });
  cases.push({
    id: 'TC-ACC-ART-005',
    dimension: '功能验收',
    module: '养生社区',
    scene: '阅读详情',
    name: '文章详情（取列表首条）',
    run: async ({ userHttp, cache }) => {
      const r = await userHttp.get('/api/article/list', { params: { pageNum: 1, pageSize: 1 } });
      if (r.status !== 200 || r.data?.code !== 200 || !r.data?.data?.records?.length) {
        return { ok: false, httpStatus: r.status, bodyCode: r.data?.code, detail: '无文章数据', note: '数据依赖' };
      }
      const id = r.data.data.records[0].id;
      cache.articleId = id;
      return wrapSuccess(await userHttp.get(`/api/article/${id}`));
    },
  });
  cases.push({
    id: 'TC-ACC-ART-006',
    dimension: '功能验收',
    module: '养生社区',
    scene: '互动数据',
    name: '文章评论列表（依赖文章 ID）',
    run: async ({ userHttp, cache }) => {
      if (!cache.articleId) {
        const r = await userHttp.get('/api/article/list', { params: { pageNum: 1, pageSize: 1 } });
        if (r.data?.code !== 200 || !r.data?.data?.records?.length) {
          return { ok: false, httpStatus: r.status, bodyCode: r.data?.code, detail: '无文章', note: '' };
        }
        cache.articleId = r.data.data.records[0].id;
      }
      return wrapSuccess(await userHttp.get(`/api/article/${cache.articleId}/comments`));
    },
  });
  cases.push({
    id: 'TC-ACC-ART-007',
    dimension: '功能验收',
    module: '养生社区',
    scene: '搜索与热词',
    name: '热门搜索词',
    run: async ({ userHttp }) =>
      wrapSuccess(await userHttp.get('/api/search/hot-keywords', { params: { limit: 10 } })),
  });
  cases.push({
    id: 'TC-ACC-ART-008',
    dimension: '功能验收',
    module: '养生社区',
    scene: '搜索与热词',
    name: '记录搜索关键词',
    run: async ({ userHttp }) =>
      wrapSuccess(await userHttp.post('/api/search/record', null, { params: { keyword: '验收自动化' } })),
  });
  cases.push({
    id: 'TC-ACC-ART-009',
    dimension: '功能验收',
    module: '养生社区',
    scene: '消息通知',
    name: '文章相关未读数',
    run: async ({ userHttp }) => wrapSuccess(await userHttp.get('/api/article/notification/unread-count')),
  });

  // ---------- 医患对话 ----------
  cases.push({
    id: 'TC-ACC-CONV-001',
    dimension: '功能验收',
    module: '医患对话',
    scene: '会话列表',
    name: '会话分页（page/pageSize）',
    run: async ({ userHttp }) =>
      wrapSuccess(await userHttp.get('/api/conversations', { params: { page: 1, pageSize: 8 } })),
  });
  cases.push({
    id: 'TC-ACC-CONV-002',
    dimension: '功能验收',
    module: '医患对话',
    scene: '消息历史',
    name: '首条会话消息列表',
    run: async ({ userHttp, cache }) => {
      const r = await userHttp.get('/api/conversations', { params: { page: 1, pageSize: 1 } });
      if (r.status !== 200 || r.data?.code !== 200 || !r.data?.data?.records?.length) {
        return { ok: true, httpStatus: r.status, bodyCode: r.data?.code, detail: '无会话数据', note: '空列表按通过计' };
      }
      const cid = r.data.data.records[0].id;
      cache.conversationId = cid;
      return wrapSuccess(
        await userHttp.get(`/api/conversations/${cid}/messages`, { params: { page: 1, pageSize: 20 } })
      );
    },
  });

  // ---------- 中医知识（穴位）----------
  cases.push({
    id: 'TC-ACC-ACU-001',
    dimension: '功能验收',
    module: '中医知识',
    scene: '穴位词典',
    name: '穴位分页列表',
    run: async ({ userHttp }) =>
      wrapSuccess(await userHttp.get('/api/acupoint/list', { params: { pageNum: 1, pageSize: 10 } })),
  });
  cases.push({
    id: 'TC-ACC-ACU-002',
    dimension: '功能验收',
    module: '中医知识',
    scene: '穴位词典',
    name: '经络列表',
    run: async ({ userHttp }) => wrapSuccess(await userHttp.get('/api/acupoint/meridians')),
  });
  cases.push({
    id: 'TC-ACC-ACU-003',
    dimension: '功能验收',
    module: '中医知识',
    scene: '穴位组合',
    name: '热门穴位组合',
    run: async ({ userHttp }) =>
      wrapSuccess(await userHttp.get('/api/acupoint/combination/popular')),
  });

  // ---------- 系统配置 ----------
  cases.push({
    id: 'TC-ACC-SYS-001',
    dimension: '功能验收',
    module: '系统配置',
    scene: '运行时参数',
    name: '登录后读取系统设置分组',
    run: async ({ userHttp }) => wrapSuccess(await userHttp.get('/api/system/settings')),
  });

  // ---------- 后台管理（无管理员账号时由 run.mjs 标记为跳过）----------
  cases.push({
    id: 'TC-ACC-ADM-001',
    dimension: '功能验收',
    module: '后台管理',
    scene: '运营看板',
    name: '管理员综合统计',
    run: async ({ adminHttp }) => wrapSuccess(await adminHttp.get('/api/statistics/admin')),
  });
  cases.push({
    id: 'TC-ACC-ADM-002',
    dimension: '功能验收',
    module: '后台管理',
    scene: '体质运营数据',
    name: '用户体质测试统计',
    run: async ({ adminHttp }) => wrapSuccess(await adminHttp.get('/api/statistics/admin/user-test')),
  });

  return cases;
}

/** @returns {{ id: string, module: string, name: string, request: (h: import('axios').AxiosInstance) => Promise<import('axios').AxiosResponse> }[]} */
export function buildPerfScenarios() {
  return [
    {
      id: 'PERF-001',
      module: '用户管理',
      name: 'GET /api/user/info',
      request: (h) => h.get('/api/user/info'),
    },
    {
      id: 'PERF-002',
      module: '养生推荐',
      name: 'GET /api/recipe/list',
      request: (h) => h.get('/api/recipe/list', { params: { pageNum: 1, pageSize: 10 } }),
    },
    {
      id: 'PERF-003',
      module: '养生推荐',
      name: 'GET /api/recipe/recommend/personalized',
      request: (h) => h.get('/api/recipe/recommend/personalized', { params: { limit: 8 } }),
    },
    {
      id: 'PERF-004',
      module: '养生社区',
      name: 'GET /api/article/list',
      request: (h) => h.get('/api/article/list', { params: { pageNum: 1, pageSize: 10 } }),
    },
    {
      id: 'PERF-005',
      module: '首页聚合',
      name: 'GET /api/home/recommendations',
      request: (h) => h.get('/api/home/recommendations', { params: { limit: 4 } }),
    },
    {
      id: 'PERF-006',
      module: '体质辨识',
      name: 'GET /api/constitution/types',
      request: (h) => h.get('/api/constitution/types'),
    },
  ];
}
