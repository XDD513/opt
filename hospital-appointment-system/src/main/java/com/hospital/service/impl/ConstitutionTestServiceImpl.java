package com.hospital.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hospital.common.result.Result;
import com.hospital.common.result.ResultCode;
import com.hospital.dto.request.SubmitTestRequest;
import com.hospital.dto.response.ConstitutionTypeResponse;
import com.hospital.dto.response.HomeRecommendationResponse;
import com.hospital.dto.response.TestResultResponse;
import com.hospital.entity.ConstitutionType;
import com.hospital.entity.UserConstitutionTest;
import com.hospital.mapper.*;
import com.hospital.service.ConstitutionTestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 体质测试服务实现类
 *
 * @author TCM Health Team
 * @since 2025-11-03
 */
@Slf4j
@Service
public class ConstitutionTestServiceImpl implements ConstitutionTestService {

    private static final int MAX_USER_SELF_DESCRIPTION_LEN = 2000;

    @Autowired
    private UserConstitutionTestMapper testMapper;

    @Autowired
    private ConstitutionTypeMapper constitutionTypeMapper;

    @Autowired
    private com.hospital.util.RedisUtil redisUtil;

    @Autowired
    private com.hospital.mapper.UserMapper userMapper;

    @Autowired
    private AcupointCombinationMapper acupointCombinationMapper;

    @Autowired
    private HerbalRecipeMapper herbalRecipeMapper;

    @Autowired
    private com.hospital.service.AiRecommendationService aiRecommendationService;

    @Autowired
    private com.hospital.service.HealthProfileService healthProfileService;

    @Autowired
    private com.hospital.mapper.UserHealthProfileMapper userHealthProfileMapper;

    @Autowired
    private com.hospital.service.OssService ossService;

    @Override
    public Result<Map<String, Object>> tongueDiagnosis(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.error(ResultCode.PARAM_ERROR.getCode(), "请上传舌头照片");
        }

        try {
            // 1. 上传到 OSS (优先保存图片)
            String ossUrl = null;
            try {
                ossUrl = ossService.uploadFile(file, "tongue/");
            } catch (Exception e) {
                log.error("上传舌诊图片到OSS失败", e);
                // 不阻断流程，继续进行AI分析，但在生产环境中建议阻断或重试
            }

            // 2. 保存临时文件用于 AI 分析
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            String tempDir = System.getProperty("java.io.tmpdir");
            java.io.File dest = new java.io.File(tempDir, fileName);
            file.transferTo(dest);

            // 3. 调用 AI 服务进行识别
            // 配置超时时间：15秒（AI 推理可能较慢）
            org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5000);
            factory.setReadTimeout(15000);
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate(factory);

            org.springframework.core.io.FileSystemResource resource = new org.springframework.core.io.FileSystemResource(dest);
            org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("file", resource);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
            org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, Object>> requestEntity =
                    new org.springframework.http.HttpEntity<>(body, headers);

            String aiUrl = "http://localhost:5000/predict_v2";
            try {
                org.springframework.http.ResponseEntity<Map> response = restTemplate.postForEntity(aiUrl, requestEntity, Map.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<String, Object> aiBody = (Map<String, Object>) response.getBody().get("data");

                    // 适配 v2 接口返回格式
                    if (aiBody.containsKey("constitution_scores")) {
                        // 如果有机器学习模型预测的分数，优先使用
                        // 此时 aiBody 结构: { "primary_constitution": "QIXU", "constitution_scores": {"PINGHE": 0.1, ...}, "visual_features": {...} }
                        // 需要将其转换为前端兼容的格式
                        Map<String, Object> visualFeatures = (Map<String, Object>) aiBody.get("visual_features");
                        List<String> featureList = new ArrayList<>();

                        // Python 端已统一返回中文特征名，此处直接透传即可
                        if (visualFeatures != null) {
                            // 构建 features_detail 列表，兼容前端显示逻辑 (需要 name 和 confidence 字段)
                            List<Map<String, Object>> featuresDetail = new ArrayList<>();
                            for (Map.Entry<String, Object> entry : visualFeatures.entrySet()) {
                                Map<String, Object> detail = new HashMap<>();
                                detail.put("name", entry.getKey());
                                detail.put("confidence", entry.getValue());
                                featuresDetail.add(detail);
                            }

                            // 按置信度从高到低排序
                            featuresDetail.sort((a, b) -> {
                                Double confA = Double.valueOf(a.get("confidence").toString());
                                Double confB = Double.valueOf(b.get("confidence").toString());
                                return confB.compareTo(confA);
                            });

                            aiBody.put("features_detail", featuresDetail);

                            // 仅选择置信度最高的两个特征作为“主特征”展示
                            // features_list 用于前端的主特征文本显示
                            int limit = Math.min(featuresDetail.size(), 2);
                            for (int i = 0; i < limit; i++) {
                                featureList.add(featuresDetail.get(i).get("name").toString());
                            }
                        }
                        aiBody.put("features_list", featureList);

                        // 将模型预测的概率分数也透传回去，供后续评分逻辑参考 (可选)
                        aiBody.put("ml_scores", aiBody.get("constitution_scores"));
                    }

                    // 添加 OSS 图片链接
                    if (ossUrl != null) {
                        aiBody.put("image_url", ossUrl);
                    }

                    return Result.success(aiBody);
                }
            } catch (Exception e) {
                log.warn("AI服务调用失败，进入降级模式: {}", e.getMessage());
                // 降级处理：返回一个空的识别结果，允许用户继续
                Map<String, Object> fallbackData = new HashMap<>();
                fallbackData.put("feature", "识别服务暂时不可用");
                fallbackData.put("features_list", new ArrayList<>());
                fallbackData.put("image_base64", "");
                if (ossUrl != null) {
                    fallbackData.put("image_url", ossUrl);
                }
                fallbackData.put("is_fallback", true);
                return Result.success(fallbackData);
            }

            return Result.error(ResultCode.SYSTEM_ERROR.getCode(), "AI分析服务异常");
        } catch (Exception e) {
            log.error("舌诊分析失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR.getCode(), "分析过程出错: " + e.getMessage());
        }
    }

    /**
     * 提交测试答案并计算结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<TestResultResponse> submitTest(Long userId, SubmitTestRequest request) {
        try {
            // 0. appointment 模块已下线：不再通过 appointmentId 校验重复提交

            // 1. 验证输入：问卷体系已下线，仅支持舌诊/多模态智能辨识
            if (request.getTongueResult() == null || request.getTongueResult().isEmpty()) {
                log.warn("舌诊结果为空");
                return Result.error(ResultCode.PARAM_ERROR.getCode(), "请先完成舌诊分析");
            }
            // 问卷答案已下线：统一保存空对象，兼容历史字段/库表
            Map<Long, Long> answers = new HashMap<>();

            log.info("开始处理体质测试提交: userId={}", userId);

            // 2. 计算各体质得分（纯舌诊 AI 评分模式）
            Map<String, Double> scores = new HashMap<>();
            List<com.hospital.entity.ConstitutionType> constitutionTypes = constitutionTypeMapper.selectAllOrdered();

            // 解析舌象客观特征分 (Phase 3: 多模态融合 -> 纯 AI 驱动)
            // 映射关系：舌象特征 -> 影响的体质代码 -> 基础权重分数
            Map<String, Map<String, Double>> tongueFeatureWeightMap = new HashMap<>();

            // 气虚质特征 (QIXU)
            Map<String, Double> qixuWeights = new HashMap<>();
            qixuWeights.put("QIXU", 40.0);
            tongueFeatureWeightMap.put("齿痕", qixuWeights);
            tongueFeatureWeightMap.put("齿痕舌", qixuWeights);
            tongueFeatureWeightMap.put("chihenshe", qixuWeights);
            tongueFeatureWeightMap.put("瘦舌", qixuWeights);
            tongueFeatureWeightMap.put("shoushe", qixuWeights);

            Map<String, Double> qixuDepressionWeights = new HashMap<>();
            qixuDepressionWeights.put("QIXU", 25.0);
            tongueFeatureWeightMap.put("脾胃凹陷", qixuDepressionWeights);
            tongueFeatureWeightMap.put("piweiao", qixuDepressionWeights);
            tongueFeatureWeightMap.put("肺区凹陷", qixuDepressionWeights); // 假设
            tongueFeatureWeightMap.put("xinfeiao", qixuDepressionWeights);

            // 阳虚质特征 (YANGXU)
            Map<String, Double> yangxuWeights = new HashMap<>();
            yangxuWeights.put("YANGXU", 40.0);
            tongueFeatureWeightMap.put("胖大舌", yangxuWeights);
            tongueFeatureWeightMap.put("pangdashe", yangxuWeights);
            tongueFeatureWeightMap.put("白苔", yangxuWeights);
            tongueFeatureWeightMap.put("baitaishe", yangxuWeights);

            Map<String, Double> yangxuDepressionWeights = new HashMap<>();
            yangxuDepressionWeights.put("YANGXU", 30.0);
            yangxuDepressionWeights.put("QIXU", 20.0);
            tongueFeatureWeightMap.put("肾区凹陷", yangxuDepressionWeights);
            tongueFeatureWeightMap.put("shenquao", yangxuDepressionWeights);

            // 阴虚质特征 (YINXU)
            Map<String, Double> yinxuWeights = new HashMap<>();
            yinxuWeights.put("YINXU", 45.0);
            tongueFeatureWeightMap.put("红舌", yinxuWeights);
            tongueFeatureWeightMap.put("hongshe", yinxuWeights);
            tongueFeatureWeightMap.put("裂纹舌", yinxuWeights);
            tongueFeatureWeightMap.put("liewenshe", yinxuWeights);
            tongueFeatureWeightMap.put("少苔", yinxuWeights);
            tongueFeatureWeightMap.put("shaotai", yinxuWeights);

            // 痰湿质特征 (TANSHI)
            Map<String, Double> tanshiWeights = new HashMap<>();
            tanshiWeights.put("TANSHI", 45.0);
            tongueFeatureWeightMap.put("腻苔", tanshiWeights);
            tongueFeatureWeightMap.put("nitai", tanshiWeights);
            tongueFeatureWeightMap.put("滑苔", tanshiWeights);
            tongueFeatureWeightMap.put("huataishe", tanshiWeights);

            // 湿热质特征 (SHIRE)
            Map<String, Double> shireWeights = new HashMap<>();
            shireWeights.put("SHIRE", 50.0);
            tongueFeatureWeightMap.put("黄苔", shireWeights);
            tongueFeatureWeightMap.put("huangtaishe", shireWeights);

            // 血瘀质特征 (XUEYU)
            Map<String, Double> xueyuWeights = new HashMap<>();
            xueyuWeights.put("XUEYU", 50.0);
            tongueFeatureWeightMap.put("紫舌", xueyuWeights);
            tongueFeatureWeightMap.put("zishe", xueyuWeights);
            tongueFeatureWeightMap.put("瘀点", xueyuWeights);
            tongueFeatureWeightMap.put("yudian", xueyuWeights);

            // 气郁质特征 (QIYU)
            Map<String, Double> qiyuWeights = new HashMap<>();
            qiyuWeights.put("QIYU", 35.0);
            tongueFeatureWeightMap.put("肝胆凹陷", qiyuWeights);
            tongueFeatureWeightMap.put("gandanao", qiyuWeights);

            // 平和质特征 (PINGHE)
            Map<String, Double> pingheWeights = new HashMap<>();
            pingheWeights.put("PINGHE", 80.0);
            tongueFeatureWeightMap.put("健康舌", pingheWeights);
            tongueFeatureWeightMap.put("jiankangshe", pingheWeights);

            Map<String, Double> botaiWeights = new HashMap<>();
            botaiWeights.put("PINGHE", 60.0);
            tongueFeatureWeightMap.put("薄白苔", botaiWeights);
            tongueFeatureWeightMap.put("botaishe", botaiWeights);

            Map<String, Double> tongueObjectiveScores = new HashMap<>();

            // 增强逻辑：解析 AI v2 返回的 ML 预测分数 (Stage 2 Result)
            boolean usedMlScoring = false;
            if (request.getTongueResult() != null) {
                try {
                    JSONObject tongueJson = JSONUtil.parseObj(request.getTongueResult());

                    // 暂时禁用 ML 绝对评分，采用特征加权评分，因为目前 ML 模型可能存在严重偏向平和质的问题
                    // if (tongueJson.containsKey("ml_scores")) {
                    //    ... usedMlScoring = true;
                    // }

                    // 回退到基于规则的特征加权 (v1 逻辑)
                    if (!usedMlScoring) {
                        // 优先尝试解析详细特征列表 (带置信度)
                        if (tongueJson.containsKey("features_detail")) {
                            cn.hutool.json.JSONArray details = tongueJson.getJSONArray("features_detail");
                            for (int i = 0; i < details.size(); i++) {
                                JSONObject detail = details.getJSONObject(i);
                                String featureName = detail.getStr("name");
                                Double confidence = detail.getDouble("confidence", 1.0); // 默认为 1.0

                                if (tongueFeatureWeightMap.containsKey(featureName)) {
                                    Map<String, Double> weights = tongueFeatureWeightMap.get(featureName);
                                    weights.forEach((code, weight) -> {
                                        // 核心算法：得分 = 基础权重 * 置信度
                                        double score = weight * confidence;
                                        tongueObjectiveScores.merge(code, score, Double::sum);
                                    });
                                }
                            }
                        }
                        // 降级兼容：解析简单列表
                        else if (tongueJson.containsKey("features_list")) {
                            List<String> features = tongueJson.getBeanList("features_list", String.class);
                            if (features != null) {
                                for (String feature : features) {
                                    if (tongueFeatureWeightMap.containsKey(feature)) {
                                        Map<String, Double> weights = tongueFeatureWeightMap.get(feature);
                                        weights.forEach((code, weight) ->
                                            tongueObjectiveScores.merge(code, weight, Double::sum));
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析舌诊结果失败", e);
                }
            }

            for (ConstitutionType type : constitutionTypes) {
                // 纯舌诊评分模式
                double transformedScore = 0.0;

                if (usedMlScoring) {
                    // 如果使用了 ML 评分，直接取值
                    transformedScore = tongueObjectiveScores.getOrDefault(type.getTypeCode(), 0.0);
                } else {
                    // v1 规则基评分逻辑
                    // 融合舌象客观分
                    if (tongueObjectiveScores.containsKey(type.getTypeCode())) {
                        transformedScore = tongueObjectiveScores.get(type.getTypeCode());
                    } else if ("PINGHE".equals(type.getTypeCode())) {
                        // 平和质特殊处理 (规则基)
                        double maxPathologyScore = tongueObjectiveScores.entrySet().stream()
                            .filter(e -> !"PINGHE".equals(e.getKey()))
                            .mapToDouble(Map.Entry::getValue)
                            .max().orElse(0.0);

                        if (maxPathologyScore > 30.0) {
                            transformedScore = 10.0;
                        } else if (maxPathologyScore > 0.0) {
                            transformedScore = 30.0;
                        } else {
                            transformedScore = 40.0;
                        }
                    }
                }
                // 不要在这里做 Math.min(100.0) 限制，先累加原始分数，最后做归一化
                scores.put(type.getTypeCode(), transformedScore);
            }

            // 【核心修复】：归一化算法，确保所有体质得分加和为 100 分 (100%)
            double sumScores = scores.values().stream().mapToDouble(Double::doubleValue).sum();
            if (sumScores > 0) {
                for (Map.Entry<String, Double> entry : scores.entrySet()) {
                    // 计算占比：当前得分 / 总分 * 100
                    double normalizedScore = (entry.getValue() / sumScores) * 100.0;
                    // 保留两位小数
                    normalizedScore = Math.round(normalizedScore * 100.0) / 100.0;
                    scores.put(entry.getKey(), normalizedScore);
                }
            }

            // 7. 判定主要体质和次要体质（基于数据库中的体质标准）
            // 按照转化分从高到低排序，得分最高的为主要体质
            List<Map.Entry<String, Double>> sortedScores = scores.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .collect(Collectors.toList());

            if (sortedScores.isEmpty()) {
                log.error("无法判定体质：得分列表为空");
                return Result.error(ResultCode.SYSTEM_ERROR.getCode(), "体质判定失败，请检查系统配置");
            }

            // 主要体质：得分最高的体质（从数据库中查询对应的体质信息）
            String primaryConstitution = sortedScores.get(0).getKey();

            // 次要体质：取第二高分体质（与前端展示/兜底逻辑保持一致，避免“显示有次要体质但数据库未保存”）
            String secondaryConstitution = null;
            if (sortedScores.size() > 1) {
                secondaryConstitution = sortedScores.get(1).getKey();
            }

            // 8. 查询体质类型信息
            ConstitutionType primaryType = constitutionTypeMapper.selectByTypeCode(primaryConstitution);
            ConstitutionType secondaryType = secondaryConstitution != null ?
                    constitutionTypeMapper.selectByTypeCode(secondaryConstitution) : null;

            if (primaryType == null) {
                log.error("无法找到主要体质详情: {}", primaryConstitution);
                return Result.error(ResultCode.DATA_NOT_FOUND.getCode(), "体质详情缺失");
            }

            // 9. 保存测试记录
            UserConstitutionTest test = new UserConstitutionTest();
            test.setUserId(userId);
            test.setPrimaryConstitution(primaryConstitution);
            test.setSecondaryConstitution(secondaryConstitution);

            // 将舌诊结果合并到 testResult JSON 中，避免因缺失 tongue_result 字段导致插入失败
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("scores", scores);
            if (request.getTongueResult() != null) {
                resultData.put("tongueResult", request.getTongueResult());
            }
            String userSelfDescription = trimUserSelfDescription(request.getUserSelfDescription());
            if (!userSelfDescription.isEmpty()) {
                resultData.put("userSelfDescription", userSelfDescription);
            }

            // 预设 AI 结果字段
            resultData.put("aiSuggestion", "");
            test.setTestResult(JSONUtil.toJsonStr(resultData));

            // 问卷已下线：不再保存 answers 字段
            test.setReportGenerated(0);
            test.setTestDate(LocalDateTime.now());

            try {
                testMapper.insert(test);
                log.info("成功插入体质测试记录: id={}", test.getId());
            } catch (Exception e) {
                log.error("数据库插入失败: " + e.getMessage(), e);
                // 如果合并后依然失败，尝试仅保存分数（最简模式）
                try {
                    test.setTestResult(JSONUtil.toJsonStr(scores));
                    testMapper.insert(test);
                    log.info("成功插入体质测试记录（最简模式）: id={}", test.getId());
                } catch (Exception e2) {
                    throw new RuntimeException("数据库最终保存失败: " + e2.getMessage());
                }
            }

            // AI 报告由前端分步调用异步任务（深度分析 → 健康计划）

            // 11. 通知已下线：不再发送消息通知

            // 12. 构建响应
            TestResultResponse response = buildTestResultResponse(test, primaryType, secondaryType, scores);

            log.info("用户{}完成体质测试，主要体质：{}，次要体质：{}", userId, primaryConstitution, secondaryConstitution);
            return Result.success(response);

        } catch (Exception e) {
            log.error("提交测试失败: " + e.getMessage(), e);
            return Result.error(ResultCode.SYSTEM_ERROR.getCode(), "提交测试失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户测试历史记录
     */
    @Override
    public Result<List<TestResultResponse>> getTestHistory(Long userId) {
        try {
            List<UserConstitutionTest> tests = testMapper.selectHistoryByUserId(userId);

            List<TestResultResponse> responseList = tests.stream()
                    .map(test -> {
                        ConstitutionType primaryType = constitutionTypeMapper.selectByTypeCode(test.getPrimaryConstitution());
                        ConstitutionType secondaryType = test.getSecondaryConstitution() != null ?
                                constitutionTypeMapper.selectByTypeCode(test.getSecondaryConstitution()) : null;

                        Map<String, Double> scores = parseScoresFromJson(test.getTestResult());
                        return buildTestResultResponse(test, primaryType, secondaryType, scores);
                    })
                    .collect(Collectors.toList());

            return Result.success(responseList);

        } catch (Exception e) {
            log.error("获取测试历史失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 获取用户最新测试结果
     */
    @Override
    public Result<TestResultResponse> getLatestTestResult(Long userId) {
        try {
            UserConstitutionTest test = testMapper.selectLatestByUserId(userId);
            if (test == null) {
                log.warn("用户{}尚未进行体质测试", userId);
                return Result.error(ResultCode.DATA_NOT_FOUND.getCode(), "您还没有进行过体质测试");
            }

            ConstitutionType primaryType = constitutionTypeMapper.selectByTypeCode(test.getPrimaryConstitution());
            ConstitutionType secondaryType = test.getSecondaryConstitution() != null ?
                    constitutionTypeMapper.selectByTypeCode(test.getSecondaryConstitution()) : null;

            Map<String, Double> scores = parseScoresFromJson(test.getTestResult());
            TestResultResponse response = buildTestResultResponse(test, primaryType, secondaryType, scores);

            return Result.success(response);

        } catch (Exception e) {
            log.error("获取最新测试结果失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    @Override
    public void generateAiSuggestionStream(Long testId, String phase, java.util.function.Consumer<String> contentConsumer) {
        UserConstitutionTest test = testMapper.selectById(testId);
        if (test == null) {
            contentConsumer.accept("{\"error\": \"测试记录不存在\"}");
            return;
        }

        String ph = (phase == null || phase.isBlank()) ? "analysis" : phase.trim().toLowerCase();
        JSONObject testResultJson = test.getTestResult() != null
                ? JSONUtil.parseObj(test.getTestResult()) : new JSONObject();
        String existingSuggestion = testResultJson.getStr("aiSuggestion");

        if ("plans".equals(ph)) {
            if (hasValidPlansAiJson(existingSuggestion)) {
                log.info("健康计划已存在，返回缓存: testId={}", testId);
                contentConsumer.accept(existingSuggestion);
                return;
            }
            if (!hasValidAnalysisAiJson(existingSuggestion)) {
                contentConsumer.accept("{\"error\":\"请先生成深度分析报告后再生成健康计划\"}");
                return;
            }
        } else {
            if (hasValidAnalysisAiJson(existingSuggestion)) {
                log.info("深度分析报告已存在，返回缓存: testId={}", testId);
                contentConsumer.accept(existingSuggestion);
                return;
            }
        }

        Map<String, Double> scores = parseScoresFromJson(test.getTestResult());
        ConstitutionType primaryType = constitutionTypeMapper.selectOne(
                new QueryWrapper<ConstitutionType>().eq("type_code", test.getPrimaryConstitution()));
        String primaryName = primaryType != null ? primaryType.getTypeName() : "未知体质";

        List<String> tongueFeatures = new ArrayList<>();
        String tongueResultStr = testResultJson.getStr("tongueResult");
        String userSelfDescription = testResultJson.getStr("userSelfDescription", "");
        if (tongueResultStr != null && tongueResultStr.contains("[")) {
            try {
                JSONObject tj = JSONUtil.parseObj(tongueResultStr);
                if (tj.containsKey("features_list")) {
                    List<String> features = tj.getBeanList("features_list", String.class);
                    if (features != null) {
                        tongueFeatures.addAll(features);
                    }
                }
            } catch (Exception e) {
                log.warn("解析舌诊特征列表失败", e);
            }
        }

        com.hospital.entity.User user = userMapper.selectById(test.getUserId());
        com.hospital.entity.UserHealthProfile profile = userHealthProfileMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.hospital.entity.UserHealthProfile>()
                        .eq("user_id", test.getUserId()));

        StringBuilder fullSuggestion = new StringBuilder();

        if ("plans".equals(ph)) {
            String prior = extractPriorContextForPlans(existingSuggestion);
            aiRecommendationService.generateHealthPlansRecommendationStream(
                    test.getUserId(), primaryName, scores, tongueFeatures, userSelfDescription, user, profile, prior,
                    chunk -> {
                        fullSuggestion.append(chunk);
                        contentConsumer.accept(chunk);
                    });
            try {
                UserConstitutionTest latest = testMapper.selectById(testId);
                if (latest != null && latest.getTestResult() != null) {
                    String merged = mergePlansIntoAiSuggestion(latest.getTestResult(), fullSuggestion.toString());
                    JSONObject root = JSONUtil.parseObj(latest.getTestResult());
                    root.set("aiSuggestion", merged);
                    UserConstitutionTest updateTest = new UserConstitutionTest();
                    updateTest.setId(testId);
                    updateTest.setTestResult(root.toString());
                    testMapper.updateById(updateTest);

                    // 避免前端随后调用 getTestReport 时读取到旧缓存（导致“闪一下/不显示”）
                    String cacheKey = "hospital:constitution:report:" + testId;
                    redisUtil.delete(cacheKey);

                    log.info("健康计划已合并入库: testId={}", testId);
                }
            } catch (Exception e) {
                log.error("合并健康计划入库失败: testId={}", testId, e);
            }
        } else {
            aiRecommendationService.generateHealthAnalysisRecommendationStream(
                    test.getUserId(), primaryName, scores, tongueFeatures, userSelfDescription, user, profile,
                    chunk -> {
                        fullSuggestion.append(chunk);
                        contentConsumer.accept(chunk);
                    });
            try {
                String normalized = normalizeAnalysisAiJsonRemovePlans(fullSuggestion.toString());
                JSONObject json = JSONUtil.parseObj(test.getTestResult());
                json.set("aiSuggestion", normalized);
                UserConstitutionTest updateTest = new UserConstitutionTest();
                updateTest.setId(testId);
                updateTest.setTestResult(json.toString());
                testMapper.updateById(updateTest);

                // 同样失效 getTestReport 缓存，避免分析/展示内容被旧缓存覆盖
                String cacheKey = "hospital:constitution:report:" + testId;
                redisUtil.delete(cacheKey);

                log.info("深度分析报告已入库: testId={}", testId);
            } catch (Exception e) {
                log.error("深度分析报告入库失败: testId={}", testId, e);
            }
        }
    }

    private static String stripMarkdownJsonFence(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (!t.startsWith("```")) {
            return t;
        }
        int firstLine = t.indexOf('\n');
        if (firstLine > 0) {
            t = t.substring(firstLine + 1);
        }
        int endFence = t.lastIndexOf("```");
        if (endFence >= 0) {
            t = t.substring(0, endFence).trim();
        }
        return t;
    }

    private boolean hasValidAnalysisAiJson(String raw) {
        try {
            if (raw == null || raw.isBlank()) {
                return false;
            }
            cn.hutool.json.JSONObject o = JSONUtil.parseObj(stripMarkdownJsonFence(raw));
            String a = o.getStr("analysis");
            return a != null && a.length() >= 40;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasValidPlansAiJson(String raw) {
        try {
            if (raw == null || raw.isBlank()) {
                return false;
            }
            cn.hutool.json.JSONObject o = JSONUtil.parseObj(stripMarkdownJsonFence(raw));
            cn.hutool.json.JSONArray arr = o.getJSONArray("plans");
            return arr != null && arr.size() >= 4;
        } catch (Exception e) {
            return false;
        }
    }

    private String normalizeAnalysisAiJsonRemovePlans(String raw) {
        try {
            cn.hutool.json.JSONObject o = JSONUtil.parseObj(stripMarkdownJsonFence(raw));
            o.remove("plans");
            return o.toString();
        } catch (Exception e) {
            log.warn("分析报告 JSON 归一化失败，保留原文: {}", e.getMessage());
            return raw;
        }
    }

    private String mergePlansIntoAiSuggestion(String testResultRow, String plansDeltaRaw) {
        cn.hutool.json.JSONObject row = JSONUtil.parseObj(testResultRow);
        String existingAi = row.getStr("aiSuggestion", "");
        cn.hutool.json.JSONObject base = JSONUtil.parseObj(stripMarkdownJsonFence(existingAi));
        cn.hutool.json.JSONObject delta = JSONUtil.parseObj(stripMarkdownJsonFence(plansDeltaRaw));
        if (delta.containsKey("plans")) {
            base.set("plans", delta.get("plans"));
        } else {
            throw new IllegalArgumentException("AI 返回中缺少 plans 字段");
        }
        return base.toString();
    }

    private String extractPriorContextForPlans(String existingRaw) {
        try {
            cn.hutool.json.JSONObject o = JSONUtil.parseObj(stripMarkdownJsonFence(existingRaw));
            StringBuilder sb = new StringBuilder();
            sb.append(o.getStr("analysis", "")).append("\n\n");
            sb.append(o.getStr("summary", ""));
            if (o.containsKey("diet")) {
                sb.append("\n\n").append(o.get("diet").toString());
            }
            return sb.toString();
        } catch (Exception e) {
            return existingRaw != null ? existingRaw : "";
        }
    }

    /**
     * 根据 AI 结果自动创建健康计划
     */
    private void createHealthPlansFromAiResult(Long userId, String jsonContent) {
        try {
            // 1. 清洗 JSON
            String cleanJson = jsonContent.trim();
            if (cleanJson.contains("```json")) {
                cleanJson = cleanJson.split("```json")[1].split("```")[0].trim();
            } else if (cleanJson.contains("```")) {
                cleanJson = cleanJson.split("```")[1].split("```")[0].trim();
            }

            // 2. 解析 JSON
            JSONObject root = JSONUtil.parseObj(cleanJson);
            if (!root.containsKey("plans")) {
                return;
            }

            cn.hutool.json.JSONArray plans = root.getJSONArray("plans");
            if (plans == null || plans.isEmpty()) {
                return;
            }

            // 3. 遍历创建计划
            for (int i = 0; i < plans.size(); i++) {
                JSONObject planJson = plans.getJSONObject(i);

                com.hospital.entity.HealthPlanRecord plan = new com.hospital.entity.HealthPlanRecord();
                plan.setUserId(userId);
                plan.setPlanType(planJson.getStr("type"));
                plan.setPlanName(planJson.getStr("name"));
                plan.setDescription(planJson.getStr("description"));
                plan.setFrequency(planJson.getStr("frequency"));

                Integer duration = planJson.getInt("duration", 30);
                plan.setStartDate(java.time.LocalDate.now());
                plan.setEndDate(java.time.LocalDate.now().plusDays(duration));

                // 调用 Service 创建
                healthProfileService.createHealthPlan(plan);
            }
            log.info("自动创建健康计划成功: userId={}, count={}", userId, plans.size());

        } catch (Exception e) {
            log.error("自动创建健康计划失败: userId={}", userId, e);
        }
    }

    /**
     * 根据测试ID获取测试报告
     */
    @Override
    public Result<TestResultResponse> getTestReport(Long testId) {
        try {
            // 尝试从缓存获取
            String cacheKey = "hospital:constitution:report:" + testId;
            Object cached = redisUtil.get(cacheKey);
            if (cached != null) {
                try {
                    // 简单的类型转换，依赖于 RedisUtil 和 RedisConfig 的正确配置 (Jackson2JsonRedisSerializer)
                    // 如果存储的是 LinkedHashMap (Jackson默认行为)，可能需要转换
                    if (cached instanceof TestResultResponse) {
                        return Result.success((TestResultResponse) cached);
                    } else if (cached instanceof Map) {
                         // 如果反序列化回来是 Map，尝试转换
                         TestResultResponse response = BeanUtil.toBean(cached, TestResultResponse.class);
                         return Result.success(response);
                    }
                } catch (Exception e) {
                    log.warn("缓存转换失败，将重新查询数据库: {}", e.getMessage());
                }
            }

            UserConstitutionTest test = testMapper.selectById(testId);
            if (test == null) {
                log.warn("测试记录不存在: {}", testId);
                return Result.error(ResultCode.DATA_NOT_FOUND);
            }

            ConstitutionType primaryType = constitutionTypeMapper.selectByTypeCode(test.getPrimaryConstitution());
            ConstitutionType secondaryType = test.getSecondaryConstitution() != null ?
                    constitutionTypeMapper.selectByTypeCode(test.getSecondaryConstitution()) : null;

            Map<String, Double> scores = parseScoresFromJson(test.getTestResult());
            TestResultResponse response = buildTestResultResponse(test, primaryType, secondaryType, scores);

            // 存入缓存（1小时）
            redisUtil.set(cacheKey, response, 1, java.util.concurrent.TimeUnit.HOURS);

            return Result.success(response);

        } catch (Exception e) {
            log.error("获取测试报告失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 从JSON字符串解析分数Map，确保值为Double类型
     */
    private Map<String, Double> parseScoresFromJson(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) return new HashMap<>();

        JSONObject json = JSONUtil.parseObj(jsonStr);
        Map<String, Object> rawMap;

        // 兼容新旧格式：新格式包含 "scores" 键，旧格式直接是 Map
        if (json.containsKey("scores")) {
            rawMap = json.get("scores", Map.class);
        } else {
            rawMap = json.toBean(Map.class);
        }

        Map<String, Double> scores = new LinkedHashMap<>();
        if (rawMap != null) {
            for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                Object value = entry.getValue();
                Double score;

                if (value instanceof Double) {
                    score = (Double) value;
                } else if (value instanceof Integer) {
                    score = ((Integer) value).doubleValue();
                } else if (value instanceof Number) {
                    score = ((Number) value).doubleValue();
                } else {
                    score = Double.parseDouble(value.toString());
                }

                scores.put(entry.getKey(), score);
            }
        }

        return scores;
    }

    /**
     * 构建测试结果响应
     */
    private TestResultResponse buildTestResultResponse(UserConstitutionTest test,
                                                        ConstitutionType primaryType,
                                                        ConstitutionType secondaryType,
                                                        Map<String, Double> scores) {
        TestResultResponse response = new TestResultResponse();
        response.setId(test.getId());
        response.setPrimaryConstitution(test.getPrimaryConstitution());
        response.setPrimaryConstitutionName(primaryType != null ? primaryType.getTypeName() : "未知体质");
        response.setSecondaryConstitution(test.getSecondaryConstitution());
        response.setSecondaryConstitutionName(secondaryType != null ? secondaryType.getTypeName() : null);
        response.setScores(scores);

        // 将 LocalDateTime 转换为 LocalDate
        response.setTestDate(test.getTestDate() != null ? test.getTestDate().toLocalDate() : null);

        // 动态生成报告和建议
        response.setReport(generateReportText(primaryType, secondaryType, scores));

        // --- Phase 3: AI 多模态融合建议 (读取异步生成的建议) ---
        String aiSuggestion = null;
        if (test.getTestResult() != null) {
            JSONObject json = JSONUtil.parseObj(test.getTestResult());
            if (json.containsKey("aiSuggestion")) {
                aiSuggestion = json.getStr("aiSuggestion");
            }
        }

        if (aiSuggestion == null || aiSuggestion.trim().isEmpty()) {
            aiSuggestion = "AI 专家正在分析您的舌象数据，请稍后在历史记录中查看深度报告...";
        }

        // 移除了默认拼接的 generateHealthSuggestionText(primaryType, secondaryType)
        // 避免默认冗长文本占据页面
        response.setHealthSuggestion(aiSuggestion);

        response.setPrimaryConstitutionDetail(BeanUtil.copyProperties(primaryType, ConstitutionTypeResponse.class));
        response.setSecondaryConstitutionDetail(secondaryType != null ?
                BeanUtil.copyProperties(secondaryType, ConstitutionTypeResponse.class) : null);

        // 获取推荐方案
        if (primaryType != null) {
            try {
                HomeRecommendationResponse recommendations = new HomeRecommendationResponse();

                // 推荐穴位
                List<com.hospital.entity.AcupointCombination> acupoints = acupointCombinationMapper.selectRecommendedCombinations(primaryType.getTypeCode(), 4);
                recommendations.setAcupoints(acupoints != null ? acupoints : new ArrayList<>());

                // 推荐药膳
                // 注意：这里没有传入季节，实际应用中可以根据当前月份计算季节
                com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.hospital.entity.HerbalRecipe> page =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 4);
                com.baomidou.mybatisplus.core.metadata.IPage<com.hospital.entity.HerbalRecipe> recipePage =
                    herbalRecipeMapper.selectRecommendedRecipes(page, primaryType.getTypeCode(), null);
                List<com.hospital.entity.HerbalRecipe> recipes = recipePage.getRecords();
                recommendations.setRecipes(recipes != null ? recipes : new ArrayList<>());

                response.setRecommendations(recommendations);
            } catch (Exception e) {
                log.warn("获取推荐方案失败", e);
            }
        }

        return response;
    }

    /**
     * 生成测试报告文本
     */
    private String generateReportText(ConstitutionType primaryType, ConstitutionType secondaryType, Map<String, Double> scores) {
        StringBuilder report = new StringBuilder();
        report.append("【体质测试报告】\n\n");
        report.append("您的主要体质是：").append(primaryType != null ? primaryType.getTypeName() : "未知").append("\n");
        report.append("得分：").append(primaryType != null ? String.format("%.1f", scores.getOrDefault(primaryType.getTypeCode(), 0.0)) : "0.0").append("分\n\n");

        if (secondaryType != null) {
            report.append("您的次要体质是：").append(secondaryType.getTypeName()).append("\n");
            report.append("得分：").append(String.format("%.1f", scores.getOrDefault(secondaryType.getTypeCode(), 0.0))).append("分\n\n");
        }

        if (primaryType != null) {
            report.append("【主要特征】\n").append(primaryType.getCharacteristics()).append("\n\n");
            report.append("【易患疾病】\n").append(primaryType.getSusceptibleDiseases()).append("\n");
        }

        return report.toString();
    }

    /**
     * 生成养生建议文本
     */
    private String generateHealthSuggestionText(ConstitutionType primaryType, ConstitutionType secondaryType) {
        StringBuilder suggestion = new StringBuilder();
        suggestion.append("【养生建议】\n\n");
        if (primaryType != null) {
            suggestion.append("饮食调养：\n").append(primaryType.getDietAdvice()).append("\n\n");
            suggestion.append("运动调养：\n").append(primaryType.getExerciseAdvice()).append("\n\n");
            suggestion.append("情志调节：\n").append(primaryType.getEmotionAdvice()).append("\n");
        }

        if (secondaryType != null) {
            suggestion.append("\n【次要体质建议】\n");
            suggestion.append("饮食：").append(secondaryType.getDietAdvice()).append("\n");
        }

        return suggestion.toString();
    }

    /**
     * 根据预约ID获取体质测试结果
     */
    @Override
    public Result<TestResultResponse> getTestResultByAppointment(Long appointmentId) {
        // 预约模块已下线：不再支持通过 appointmentId 查询测试结果
        return Result.error(ResultCode.PARAM_ERROR.getCode(), "预约模块已下线，无法通过预约ID查询体质测试结果");
    }

    /**
     * 检查预约是否已有体质测试记录
     */
    @Override
    public Result<Boolean> hasTestByAppointment(Long appointmentId) {
        // 预约模块已下线：不再支持通过 appointmentId 查询测试是否存在
        return Result.success(false);
    }

    private static String trimUserSelfDescription(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.length() <= MAX_USER_SELF_DESCRIPTION_LEN) {
            return t;
        }
        return t.substring(0, MAX_USER_SELF_DESCRIPTION_LEN);
    }
}


