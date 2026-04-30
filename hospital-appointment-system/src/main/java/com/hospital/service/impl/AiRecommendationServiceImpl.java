package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.constant.CacheConstants;
import com.hospital.config.DeepSeekConfig;
import com.hospital.entity.HerbalRecipe;
import com.hospital.entity.User;
import com.hospital.entity.UserConstitutionTest;
import com.hospital.entity.UserFavorite;
import com.hospital.entity.UserHealthProfile;
import com.hospital.mapper.HerbalRecipeMapper;
import com.hospital.mapper.UserConstitutionTestMapper;
import com.hospital.mapper.UserFavoriteMapper;
import com.hospital.service.AiRecommendationService;
import com.hospital.util.CacheKeyBuilder;
import com.hospital.util.CacheTtlPolicy;
import com.hospital.util.RedisUtil;
import com.theokanning.openai.client.OpenAiApi;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import cn.hutool.json.JSONUtil;

/**
 * AI推荐服务实现类
 * 使用OpenAI Java SDK（兼容DeepSeek API）
 *
 * @author Hospital Team
 * @since 2025-01-XX
 */
@Slf4j
@Service
public class AiRecommendationServiceImpl implements AiRecommendationService {

    @Autowired
    private DeepSeekConfig deepSeekConfig;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private HerbalRecipeMapper herbalRecipeMapper;

    @Autowired
    private UserFavoriteMapper userFavoriteMapper;

    @Autowired
    private UserConstitutionTestMapper userConstitutionTestMapper;

    private OpenAiService openAiService;
    private OkHttpClient okHttpClient;

    private static final int DEFAULT_RECOMMENDATION_LIMIT = 6;
    private static final int CONTENT_CANDIDATE_LIMIT = 200;

    @PostConstruct
    public void init() {
        if (StringUtils.hasText(deepSeekConfig.getApiKey())) {
            try {
                // 创建拦截器，添加 Authorization 头
                Interceptor authInterceptor = new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request original = chain.request();
                        Request request = original.newBuilder()
                                .header("Authorization", "Bearer " + deepSeekConfig.getApiKey())
                                .header("Content-Type", "application/json")
                                .build();
                        return chain.proceed(request);
                    }
                };

                // 创建 OkHttpClient，添加认证拦截器
                // 增加超时时间，给AI足够的时间响应（智能导诊需要等待AI回复）
                okHttpClient = new OkHttpClient.Builder()
                        .connectTimeout(java.time.Duration.ofSeconds(60))  // 连接超时增加到 60s
                        .readTimeout(java.time.Duration.ofSeconds(120))    // 读取超时增加到 120s
                        .writeTimeout(java.time.Duration.ofSeconds(60))    // 写入超时增加到 60s
                        .addInterceptor(authInterceptor)
                        .build();

                // 创建自定义 ObjectMapper，配置忽略未知属性（DeepSeek API 可能返回 SDK 不支持的字段）
                ObjectMapper retrofitObjectMapper = new ObjectMapper();
                retrofitObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                retrofitObjectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);

                // 创建 Retrofit 实例，使用 DeepSeek 的 API 地址
                Retrofit retrofit = new Retrofit.Builder()
                        .baseUrl(deepSeekConfig.getApiUrl() + "/")
                        .client(okHttpClient)
                        .addConverterFactory(JacksonConverterFactory.create(retrofitObjectMapper))
                        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                        .build();

                // 创建 OpenAiApi 实例
                OpenAiApi api = retrofit.create(OpenAiApi.class);

                // 创建 OpenAiService 实例
                openAiService = new OpenAiService(api);

                log.info("DeepSeek API服务初始化成功，API地址: {}", deepSeekConfig.getApiUrl());
            } catch (Exception e) {
                log.error("初始化DeepSeek API服务失败", e);
            }
        } else {
            log.warn("DeepSeek API Key未配置，AI推荐功能将不可用");
        }
    }

    @PreDestroy
    public void destroy() {
        shutdownOkHttpClient();

        if (openAiService != null) {
            try {
                openAiService.shutdownExecutor();
                log.info("OpenAI服务已关闭");
            } catch (Exception e) {
                log.warn("关闭OpenAI服务时出错: {}", e.getMessage());
            }
        }
    }

    private void shutdownOkHttpClient() {
        if (okHttpClient == null) {
            return;
        }
        try {
            okHttpClient.dispatcher().cancelAll();
            okHttpClient.dispatcher().executorService().shutdown();
            okHttpClient.connectionPool().evictAll();
            if (okHttpClient.cache() != null) {
                okHttpClient.cache().close();
            }
            log.info("OkHttp客户端已关闭");
        } catch (Exception e) {
            log.warn("关闭OkHttp客户端时出错: {}", e.getMessage());
        }
    }

    @Override
    public String generateRecommendationReason(HerbalRecipe recipe, UserConstitutionTest constitution) {
        if (recipe == null || constitution == null) {
            log.warn("生成推荐理由失败：参数为空");
            return null;
        }

        if (openAiService == null) {
            log.warn("DeepSeek API服务未初始化，返回默认推荐理由");
            return buildDefaultRecommendationReason(recipe, constitution);
        }

        // 生成缓存键
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("recipeId", recipe.getId());
        params.put("constitution", constitution.getPrimaryConstitution());
        String cacheKey = CacheKeyBuilder.of(CacheConstants.AI_RECOMMENDATION_REASON_CACHE_PREFIX)
                .appendParamsHash(params)
                .build();

        // 尝试从缓存获取
        Object cached = redisUtil.get(cacheKey);
        if (cached instanceof String) {
            return (String) cached;
        }

        try {
            // 构建Prompt
            String prompt = buildRecommendationReasonPrompt(recipe, constitution);

            // 调用DeepSeek API
            String response = callDeepSeekApi(prompt);

            if (StringUtils.hasText(response)) {
                // 缓存结果
                redisUtil.set(cacheKey, response,
                        deepSeekConfig.getCacheTtlHours(), TimeUnit.HOURS);
                log.info("生成推荐理由成功：recipeId={}", recipe.getId());
                return response;
            }

        } catch (Exception e) {
            log.error("生成推荐理由失败：recipeId={}", recipe.getId(), e);
        }

        // 降级：返回默认推荐理由
        return buildDefaultRecommendationReason(recipe, constitution);
    }

    @Override
    public List<HerbalRecipe> recommendByConversation(String conversationContent, Long userId) {
        if (!StringUtils.hasText(conversationContent)) {
            log.warn("对话内容为空，无法推荐");
            return Collections.emptyList();
        }

        if (openAiService == null) {
            log.warn("DeepSeek API服务未初始化，返回空列表");
            return Collections.emptyList();
        }

        // 生成缓存键
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("content", conversationContent);
        params.put("userId", userId != null ? userId.toString() : "anonymous");
        String cacheKey = CacheKeyBuilder.of(CacheConstants.AI_CONVERSATION_CACHE_PREFIX)
                .appendParamsHash(params)
                .build();

        // 尝试从缓存获取
        Object cached = redisUtil.get(cacheKey);
        if (cached instanceof List) {
            try {
                @SuppressWarnings("unchecked")
                List<HerbalRecipe> cachedList = (List<HerbalRecipe>) cached;
                return cachedList;
            } catch (ClassCastException ignored) {}
        }

        try {
            // 构建Prompt
            String prompt = buildConversationRecommendationPrompt(conversationContent);

            // 调用DeepSeek API
            String response = callDeepSeekApi(prompt);

            if (StringUtils.hasText(response)) {
                // 解析推荐结果
                List<HerbalRecipe> recommendations = parseRecommendationResponse(response);

                if (!recommendations.isEmpty()) {
                    // 缓存结果
                    redisUtil.set(cacheKey, recommendations,
                            deepSeekConfig.getCacheTtlHours(), TimeUnit.HOURS);
                    log.info("对话推荐成功：userId={}, count={}", userId, recommendations.size());
                    return recommendations;
                }
            }

        } catch (Exception e) {
            log.error("对话推荐失败：userId={}", userId, e);
        }

        // 降级：返回空列表
        return Collections.emptyList();
    }

    @Override
    public String answerQuestion(String question, Long userId) {
        if (!StringUtils.hasText(question)) {
            return "您的问题不能为空，请重新提问。";
        }

        if (openAiService == null) {
            log.warn("DeepSeek API服务未初始化，返回默认回答");
            return "抱歉，AI服务暂时不可用，请稍后再试或咨询专业医生。";
        }

        // 生成缓存键
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("question", question);
        params.put("userId", userId != null ? userId.toString() : "anonymous");
        String cacheKey = CacheKeyBuilder.of(CacheConstants.AI_QUESTION_CACHE_PREFIX)
                .appendParamsHash(params)
                .build();

        // 尝试从缓存获取
        Object cached = redisUtil.get(cacheKey);
        if (cached instanceof String) {
            return (String) cached;
        }

        try {
            // 构建Prompt
            String prompt = buildQuestionAnswerPrompt(question);

            // 调用DeepSeek API
            String response = callDeepSeekApi(prompt);

            if (StringUtils.hasText(response)) {
                // 缓存结果
                redisUtil.set(cacheKey, response,
                        deepSeekConfig.getCacheTtlHours(), TimeUnit.HOURS);
                log.info("智能问答成功：userId={}", userId);
                return response;
            }

        } catch (Exception e) {
            log.error("智能问答失败：userId={}", userId, e);
        }

        // 降级：返回默认回答
        return "抱歉，我现在无法回答您的问题，请稍后再试或咨询专业医生。";
    }

    @Override
    public String generateMultiModalRecommendation(Long userId, String primaryType, Map<String, Double> scores, List<String> tongueFeatures,
            String userSelfDescription, User user, UserHealthProfile profile) {
        if (openAiService == null) {
            return "{\"summary\": \"基于您的" + primaryType + "体质，建议保持规律作息，平补饮食。\"}";
        }

        String season = resolveCurrentSeasonInChinese();
        StringBuilder prompt = new StringBuilder();
        prompt.append("# Role\n你是一位拥有30年临床经验的高级中医养生专家，精通《中医体质分类与判定》国家标准。你的输出用于「养生与健康教育」，不提供疾病诊断，不开具体药方。\n\n");
        appendHealthReportInputData(prompt, primaryType, scores, tongueFeatures, userSelfDescription, user, profile, season);

        prompt.append("# Task\n请综合「用户自述」「舌象算法识别结果」「系统体质分数」「用户健康档案」制定个性化辨证施养方案（JSON）。\n");
        prompt.append("若用户自述与舌象或分数暗示的倾向不完全一致，请在 analysis 中简要分析可能原因，并给出稳妥、可执行的建议，避免绝对化表述。\n");
        prompt.append("你只输出一个合法 JSON 对象，禁止 Markdown 代码块或任何前缀/后缀说明文字。\n\n");

        prompt.append("# Constraints\n");
        prompt.append("1. 必须且只能返回 JSON，键名使用示例中的英文，字段值用中文。\n");
        prompt.append("2. 必须包含 'plans' 数组且含 4 项：饮食、运动、穴位、起居。\n");
        prompt.append("3. 所有面向用户的说明文字必须在 JSON 字段内，不要用 Markdown 标题。\n");
        prompt.append("4. 在 summary 或 analysis 须体现对用户自述的回应，并与舌象、分数相互印证或解释差异。\n");
        prompt.append("5. 必须包含免责声明：\"以上建议仅供参考，严重不适请及时就医。\"\n\n");
        appendStructuredFieldContract(prompt);
        appendPlansArrayContract(prompt);

        prompt.append("# Output Format (Strictly JSON)\n");
        prompt.append("{\n");
        prompt.append("  \"analysis\": \"1. 体质状态分析...\\n2. 舌象特征解读...\\n3. 与用户自述的对照...\",\n");
        prompt.append("  \"summary\": \"总体调理原则（分点描述）\",\n");
        prompt.append("  \"plans\": [\n");
        prompt.append("    {\"planType\": \"DIET\", \"planName\": \"饮食调理计划\", \"description\": \"简要描述\", \"frequency\": \"DAILY\", \"targetContent\": \"具体目标(如: 减重2kg)\", \"duration\": 30},\n");
        prompt.append("    {\"planType\": \"EXERCISE\", \"planName\": \"运动强身计划\", \"description\": \"简要描述\", \"frequency\": \"WEEKLY\", \"targetContent\": \"具体目标\", \"duration\": 30},\n");
        prompt.append("    {\"planType\": \"ACUPOINT\", \"planName\": \"穴位保健计划\", \"description\": \"简要描述\", \"frequency\": \"DAILY\", \"targetContent\": \"具体目标\", \"duration\": 30},\n");
        prompt.append("    {\"planType\": \"SLEEP\", \"planName\": \"起居作息计划\", \"description\": \"简要描述\", \"frequency\": \"DAILY\", \"targetContent\": \"具体目标\", \"duration\": 30}\n");
        prompt.append("  ],\n");
        prompt.append("  \"diet\": {\"recommend\": [\"宜食食物1\", \"宜食食物2\"], \"avoid\": [\"忌食食物1\", \"忌食食物2\"]},\n");
        prompt.append("  \"lifestyle\": [\"起居建议1\", \"起居建议2\"],\n");
        prompt.append("  \"acupoints\": [\n");
        prompt.append("    {\"name\": \"关元穴\", \"location\": \"一句取穴\", \"effect\": \"一句操作要点\"},\n");
        prompt.append("    {\"name\": \"足三里穴\", \"location\": \"一句取穴\", \"effect\": \"一句操作要点\"},\n");
        prompt.append("    {\"name\": \"肾俞穴\", \"location\": \"一句取穴\", \"effect\": \"一句操作要点\"},\n");
        prompt.append("    {\"name\": \"三阴交穴\", \"location\": \"一句取穴\", \"effect\": \"一句操作要点\"}\n");
        prompt.append("  ],\n");
        prompt.append("  \"exercise\": \"具体运动方案描述\",\n");
        prompt.append("  \"disclaimer\": \"以上建议仅供参考，严重不适请及时就医。\"\n");
        prompt.append("}\n");

        try {
            String response = callDeepSeekApi(prompt.toString());
            log.info("AI 多模态建议原始响应: {}", response);
            return response;
        } catch (Exception e) {
            log.error("生成多模态融合建议失败", e);
            return "{\"summary\": \"AI服务繁忙，建议咨询专业医生。\"}";
        }
    }

    private Map<String, String> buildConstitutionNameMap() {
        Map<String, String> m = new HashMap<>();
        m.put("PINGHE", "平和质");
        m.put("QIXU", "气虚质");
        m.put("YANGXU", "阳虚质");
        m.put("YINXU", "阴虚质");
        m.put("TANSHI", "痰湿质");
        m.put("SHIRE", "湿热质");
        m.put("XUEYU", "血瘀质");
        m.put("QIYU", "气郁质");
        m.put("TEBING", "特禀质");
        return m;
    }

    private Map<String, String> buildTongueFeatureCnMap() {
        Map<String, String> tongueMap = new HashMap<>();
        tongueMap.put("baitaishe", "白苔舌");
        tongueMap.put("huangtaishe", "黄苔舌");
        tongueMap.put("houtaishe", "厚苔舌");
        tongueMap.put("liewenshe", "裂纹舌");
        tongueMap.put("chihenshe", "齿痕舌");
        tongueMap.put("pangdashe", "胖大舌");
        tongueMap.put("shoushe", "瘦舌");
        tongueMap.put("danyushe", "淡瘀舌");
        tongueMap.put("hongshe", "红舌");
        tongueMap.put("piweiao", "脾胃凹");
        return tongueMap;
    }

    /**
     * 健康报告：自述 + 舌象算法结果 + 系统体质分数 + 档案 + 时令。
     */
    private void appendHealthReportInputData(StringBuilder prompt, String primaryType, Map<String, Double> scores,
            List<String> tongueFeatures, String userSelfDescription, User user, UserHealthProfile profile, String season) {
        Map<String, String> constitutionNameMap = buildConstitutionNameMap();
        List<Map.Entry<String, Double>> sortedScores = scores != null && !scores.isEmpty()
                ? scores.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed()).collect(Collectors.toList())
                : Collections.emptyList();

        String secondaryTypeCode = (sortedScores.size() > 1 && sortedScores.get(1).getValue() >= 40)
                ? sortedScores.get(1).getKey() : null;
        String secondaryType = secondaryTypeCode != null
                ? constitutionNameMap.getOrDefault(secondaryTypeCode, secondaryTypeCode) : "无显著兼夹";

        Map<String, String> tongueMap = buildTongueFeatureCnMap();
        List<String> chineseFeatures = tongueFeatures != null ? tongueFeatures.stream()
                .map(f -> tongueMap.getOrDefault(f, f))
                .collect(Collectors.toList()) : new ArrayList<>();

        prompt.append("# Input Data\n");
        prompt.append("以下信息按来源分层。舌象标签来自图像检测算法（如 YOLO）；体质分数由系统根据识别结果与规则计算。仅供养生参考，非临床诊断。\n\n");

        prompt.append("## 1. 用户主观自述\n");
        if (StringUtils.hasText(userSelfDescription)) {
            prompt.append(userSelfDescription.trim()).append("\n\n");
        } else {
            prompt.append("（未填写）\n\n");
        }

        prompt.append("## 2. 舌象客观信息（图像算法识别）\n");
        prompt.append("- 识别特征: ").append(!chineseFeatures.isEmpty() ? String.join("、", chineseFeatures) : "未见明显异常或未识别到特征标签").append("\n\n");

        prompt.append("## 3. 系统体质评分（相对占比，各维分值加总为 100）\n");
        prompt.append("- 主导体质: ").append(primaryType).append("\n");
        prompt.append("- 兼夹体质: ").append(secondaryType).append("\n");
        prompt.append("- 各体质分值明细:\n");
        if (scores != null && !scores.isEmpty()) {
            scores.forEach((code, score) -> {
                String name = constitutionNameMap.getOrDefault(code, code);
                prompt.append("  - ").append(name).append(": ").append(score).append("\n");
            });
        } else {
            prompt.append("  - （无）\n");
        }
        prompt.append("\n");

        prompt.append("## 4. 用户健康档案（账号内录入）\n");
        if (user != null) {
            String sex = user.getGender() != null ? (user.getGender() == 1 ? "男" : (user.getGender() == 2 ? "女" : "未知")) : "未知";
            int age = 0;
            if (user.getBirthDate() != null) {
                age = java.time.Period.between(user.getBirthDate(), java.time.LocalDate.now()).getYears();
            }
            prompt.append("- 基本信息: 性别 ").append(sex).append(", 年龄 ").append(age).append(" 岁\n");
        } else {
            prompt.append("- 基本信息: 未知\n");
        }
        if (profile != null) {
            if (profile.getHeight() != null) {
                prompt.append("- 身高: ").append(profile.getHeight()).append(" cm\n");
            }
            if (profile.getWeight() != null) {
                prompt.append("- 体重: ").append(profile.getWeight()).append(" kg\n");
            }
            if (profile.getBmi() != null) {
                prompt.append("- BMI: ").append(profile.getBmi()).append("\n");
            }
            if (StringUtils.hasText(profile.getAllergies())) {
                prompt.append("- 过敏史: ").append(profile.getAllergies()).append("\n");
            }
            if (StringUtils.hasText(profile.getMedicalHistory())) {
                prompt.append("- 既往病史: ").append(profile.getMedicalHistory()).append("\n");
            }
            if (StringUtils.hasText(profile.getLifestyle())) {
                prompt.append("- 生活习惯: ").append(profile.getLifestyle()).append("\n");
            }
            if (StringUtils.hasText(profile.getHealthGoals())) {
                prompt.append("- 养生目标: ").append(profile.getHealthGoals()).append("\n");
            }
        }
        prompt.append("\n");

        prompt.append("## 5. 时令\n");
        prompt.append("- 当前时令: ").append(season).append("\n\n");
    }

    private String resolveCurrentSeasonInChinese() {
        java.time.Month month = java.time.LocalDate.now().getMonth();
        switch (month) {
            case DECEMBER: case JANUARY: case FEBRUARY: return "冬季";
            case MARCH: case APRIL: case MAY: return "春季";
            case JUNE: case JULY: case AUGUST: return "夏季";
            default: return "秋季";
        }
    }

    private List<Map.Entry<String, Double>> getSortedScoreEntries(Map<String, Double> scores) {
        if (scores == null || scores.isEmpty()) {
            return Collections.emptyList();
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
    }

    private String buildTopConstitutionSummary(Map<String, Double> scores) {
        Map<String, String> constitutionNameMap = buildConstitutionNameMap();
        List<Map.Entry<String, Double>> sorted = getSortedScoreEntries(scores);
        if (sorted.isEmpty()) {
            return "（无体质分值数据）";
        }
        Map.Entry<String, Double> first = sorted.get(0);
        String firstName = constitutionNameMap.getOrDefault(first.getKey(), first.getKey());
        String firstPart = firstName + "(" + String.format("%.2f", first.getValue()) + ")";
        if (sorted.size() == 1) {
            return firstPart;
        }
        Map.Entry<String, Double> second = sorted.get(1);
        String secondName = constitutionNameMap.getOrDefault(second.getKey(), second.getKey());
        double diff = first.getValue() - second.getValue();
        String secondPart = secondName + "(" + String.format("%.2f", second.getValue()) + ")";
        return firstPart + "，次高：" + secondPart + "，分差：" + String.format("%.2f", diff);
    }

    private String buildTongueFeatureSummary(List<String> tongueFeatures) {
        if (tongueFeatures == null || tongueFeatures.isEmpty()) {
            return "（无明显舌象特征）";
        }
        Map<String, String> tongueMap = buildTongueFeatureCnMap();
        return tongueFeatures.stream()
                .filter(StringUtils::hasText)
                .map(f -> tongueMap.getOrDefault(f, f))
                .distinct()
                .limit(3)
                .collect(Collectors.joining("、"));
    }

    private String buildSelfDescriptionKeywords(String userSelfDescription) {
        if (!StringUtils.hasText(userSelfDescription)) {
            return "（未填写）";
        }
        String normalized = userSelfDescription
                .replaceAll("[,，。；;！!？?、\\n\\r\\t]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isEmpty()) {
            return "（未填写）";
        }
        LinkedHashSet<String> uniq = new LinkedHashSet<>();
        for (String token : normalized.split(" ")) {
            String t = token.trim();
            if (t.length() >= 2 && t.length() <= 12) {
                uniq.add(t);
            }
            if (uniq.size() >= 6) {
                break;
            }
        }
        return uniq.isEmpty() ? normalized : String.join("、", uniq);
    }

    private String buildProfileRiskSummary(UserHealthProfile profile) {
        if (profile == null) {
            return "（无健康档案）";
        }
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(profile.getAllergies())) {
            parts.add("过敏史:" + profile.getAllergies());
        }
        if (StringUtils.hasText(profile.getMedicalHistory())) {
            parts.add("病史:" + profile.getMedicalHistory());
        }
        if (profile.getBmi() != null) {
            parts.add("BMI:" + profile.getBmi());
        }
        if (StringUtils.hasText(profile.getHealthGoals())) {
            parts.add("目标:" + profile.getHealthGoals());
        }
        if (parts.isEmpty()) {
            return "（无高风险信息）";
        }
        return String.join("；", parts);
    }

    private String buildRotationHint(Long userId, String phase) {
        String[] analysisStyles = {
                "先证据后结论，再给优先级。",
                "先冲突解释再落地建议，避免重复措辞。",
                "先时令影响再体质要点，最后给执行顺序。"
        };
        String[] planStyles = {
                "饮食偏温补+步行耐力+基础穴位按揉+固定睡眠窗。",
                "饮食偏清润+拉伸与低冲击+按压轮换穴位+午晚节律管理。",
                "饮食偏健脾祛湿+间歇活动+穴位交替+睡前放松流程。"
        };
        int idx = Math.floorMod(Objects.hash(userId == null ? 0L : userId, phase == null ? "" : phase), 3);
        if ("analysis".equalsIgnoreCase(phase)) {
            return analysisStyles[idx];
        }
        return planStyles[idx];
    }

    /**
     * 与前端解析约定一致的 JSON 形态（仅通过提示词约束模型输出，不增加业务代码）。
     */
    private void appendStructuredFieldContract(StringBuilder prompt) {
        prompt.append("# 字段形态契约（须严格遵守，否则系统无法解析展示）\n");
        prompt.append("- diet：必须是对象，且仅含键 recommend、avoid；二者均为字符串数组，各至少 2 条；不得把宜忌写成单段长文或换用其它键名。\n");
        prompt.append("- lifestyle：必须是字符串数组，每项一条独立建议，至少 2 条。\n");
        prompt.append("- acupoints：必须是长度恰好为 4 的对象数组。**每个元素必须且只能**是 {\"name\":\"...\",\"location\":\"...\",\"effect\":\"...\"} 三个键（缺一不可）；name 为穴位简称（如「关元穴」），location 为一句取穴描述，effect 为一句操作要点。**禁止**用字符串作为数组元素，**禁止**省略某键或改用段落数组。\n");
        prompt.append("- exercise、analysis、summary、disclaimer：均为单个字符串；analysis/exercise 长文内若分点请使用 JSON 转义换行符 \\n。\n\n");
    }

    /** 第二步计划列表的固定结构 */
    private void appendPlansArrayContract(StringBuilder prompt) {
        prompt.append("# plans 契约\n");
        prompt.append("- 根对象仅含 plans 键；plans 为长度恰好 4 的数组。\n");
        prompt.append("- 按顺序：第 1 条 planType=DIET，第 2 条 EXERCISE，第 3 条 ACUPOINT，第 4 条 SLEEP（全大写）。\n");
        prompt.append("- 每条必须含：planType、planName、description、frequency（仅 DAILY 或 WEEKLY）、targetContent、duration（正整数天数）；禁止缺键、禁止嵌套子对象。\n\n");
    }

    /** 通用：减少模板化与空泛语句 */
    private void appendAntiTemplateConstraints(StringBuilder prompt) {
        prompt.append("# 去模板化约束\n");
        prompt.append("- 禁止空泛表述：如“规律作息”“适量运动”“均衡饮食”不得单独成句，必须落到可执行动作。\n");
        prompt.append("- 每个核心段落至少引用 1 个输入证据锚点（舌象特征/体质分值/用户自述/档案风险中的任意项）。\n");
        prompt.append("- 同一输出内不得出现同义改写重复句；若语义相近，必须改为替代动作并说明差异。\n\n");
    }

    /** 第二步计划专用：可量化 + 去重 */
    private void appendPlanQuantAndDedupConstraints(StringBuilder prompt) {
        prompt.append("# 可执行与去重约束\n");
        prompt.append("- 每条计划必须包含可量化动作（次数/时长/时间窗/频率至少 2 项）。\n");
        prompt.append("- 四条计划的 description 与 targetContent 禁止同义重复；动作类型需互补。\n");
        prompt.append("- 若与“已生成辨证报告”内容高度相似，必须给出替代动作 A/B。\n\n");
    }

    @Override
    public void generateMultiModalRecommendationStream(Long userId, String primaryType, Map<String, Double> scores, List<String> tongueFeatures,
            String userSelfDescription, User user, UserHealthProfile profile, java.util.function.Consumer<String> contentConsumer) {
        if (openAiService == null) {
            contentConsumer.accept("{\"summary\": \"AI服务未初始化，请检查配置。\"}");
            return;
        }

        String season = resolveCurrentSeasonInChinese();
        StringBuilder prompt = new StringBuilder();
        prompt.append("# Role\n你是一位拥有30年临床经验的高级中医养生专家，精通《中医体质分类与判定》国家标准。输出用于养生与健康教育，不提供疾病诊断。\n\n");
        appendHealthReportInputData(prompt, primaryType, scores, tongueFeatures, userSelfDescription, user, profile, season);

        prompt.append("# Task\n请综合「用户自述」「舌象算法识别」「系统体质分数」「用户健康档案」制定辨证施养方案；输出须为完整 JSON。\n");
        prompt.append("若自述与舌象/分数不完全一致，在 analysis 中分点说明可能原因并给出稳妥建议。\n\n");

        prompt.append("# Constraints\n");
        prompt.append("1. 严禁在 JSON 键名以外使用英文或拼音串作为面向用户的正文（字段值请用中文）。\n");
        prompt.append("2. 必须兼顾主导与兼夹体质；须回应用户自述，并与舌象、分数相互印证或解释差异。\n");
        prompt.append("3. analysis 中请先分析体质与舌象联系，再对照用户自述，使用「1. 2. 3.」分点。\n");
        prompt.append("4. 建议须具体可操作：饮食宜忌、起居、穴位、运动；过敏与病史需在 diet/plans 中规避相关风险。\n");
        prompt.append("5. 必须包含免责声明：\"以上建议仅供参考，严重不适请及时就医。\"\n");
        prompt.append("6. plans 数组须包含饮食、运动、穴位、起居四类计划。\n\n");
        appendStructuredFieldContract(prompt);
        appendPlansArrayContract(prompt);

        prompt.append("# Output Format (Strictly JSON)\n");
        prompt.append("请务必只返回标准的 JSON 格式，不要有任何其他开场白或解释文字。确保 JSON 结构完整，不要缺失花括号：\n");
        prompt.append("{\n");
        prompt.append("  \"analysis\": \"1. 体质状态分析...\\n2. 舌象特征解读...\\n3. 时令影响...\",\n");
        prompt.append("  \"summary\": \"总体调理原则（分点描述）\",\n");
        prompt.append("  \"plans\": [\n");
        prompt.append("    {\"planType\": \"DIET\", \"planName\": \"饮食调理计划\", \"description\": \"简要描述\", \"frequency\": \"DAILY\", \"targetContent\": \"具体目标(如: 减重2kg)\", \"duration\": 30},\n");
        prompt.append("    {\"planType\": \"EXERCISE\", \"planName\": \"运动强身计划\", \"description\": \"简要描述\", \"frequency\": \"WEEKLY\", \"targetContent\": \"具体目标\", \"duration\": 30},\n");
        prompt.append("    {\"planType\": \"ACUPOINT\", \"planName\": \"穴位保健计划\", \"description\": \"简要描述\", \"frequency\": \"DAILY\", \"targetContent\": \"具体目标\", \"duration\": 30},\n");
        prompt.append("    {\"planType\": \"SLEEP\", \"planName\": \"起居作息计划\", \"description\": \"简要描述\", \"frequency\": \"DAILY\", \"targetContent\": \"具体目标\", \"duration\": 30}\n");
        prompt.append("  ],\n");
        prompt.append("  \"diet\": {\"recommend\": [\"宜食食物1\", \"宜食食物2\"], \"avoid\": [\"忌食食物1\", \"忌食食物2\"]},\n");
        prompt.append("  \"lifestyle\": [\"起居建议1\", \"起居建议2\"],\n");
        prompt.append("  \"acupoints\": [\n");
        prompt.append("    {\"name\": \"关元穴\", \"location\": \"一句取穴\", \"effect\": \"一句操作要点\"},\n");
        prompt.append("    {\"name\": \"足三里穴\", \"location\": \"一句取穴\", \"effect\": \"一句操作要点\"},\n");
        prompt.append("    {\"name\": \"肾俞穴\", \"location\": \"一句取穴\", \"effect\": \"一句操作要点\"},\n");
        prompt.append("    {\"name\": \"三阴交穴\", \"location\": \"一句取穴\", \"effect\": \"一句操作要点\"}\n");
        prompt.append("  ],\n");
        prompt.append("  \"exercise\": \"具体运动方案描述\",\n");
        prompt.append("  \"disclaimer\": \"以上建议仅供参考，严重不适请及时就医。\"\n");
        prompt.append("}\n");

        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(),
                    "你是一位专业的中医健康顾问，擅长根据用户体质和症状推荐合适的养生建议。请始终使用中文回答，不要使用英文或其他语言。请务必输出合法的 JSON 格式内容。"));
            messages.add(new ChatMessage(ChatMessageRole.USER.value(), prompt.toString()));

            // 增加 Max Tokens 防止被截断
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(deepSeekConfig.getModel())
                    .messages(messages)
                    .maxTokens(4000) // 增加到 4000，确保能完整输出长 JSON
                    .temperature(deepSeekConfig.getTemperature())
                    .stream(true)
                    .build();

            openAiService.streamChatCompletion(request)
                    .doOnError(e -> {
                        log.error("分片调用 AI 失败", e);
                        contentConsumer.accept("{\"error\": \"AI 响应异常: " + e.getMessage() + "\"}");
                    })
                    .blockingForEach(completion -> {
                        if (completion.getChoices() != null && !completion.getChoices().isEmpty()) {
                            String content = completion.getChoices().get(0).getMessage().getContent();
                            if (content != null) {
                                contentConsumer.accept(content);
                            }
                        }
                    });

        } catch (Exception e) {
            log.error("生成建议失败", e);
            contentConsumer.accept("{\"error\": \"生成报告失败: " + e.getMessage() + "\"}");
        }
    }

    @Override
    public void generateHealthAnalysisRecommendationStream(Long userId, String primaryType, Map<String, Double> scores, List<String> tongueFeatures,
            String userSelfDescription, User user, UserHealthProfile profile, java.util.function.Consumer<String> contentConsumer) {
        if (openAiService == null) {
            contentConsumer.accept("{\"error\": \"AI服务未初始化\"}");
            return;
        }
        String season = resolveCurrentSeasonInChinese();
        StringBuilder prompt = new StringBuilder();
        prompt.append("# Role\n你是一位拥有30年临床经验的高级中医养生专家。输出用于养生与健康教育，不提供疾病诊断。\n\n");
        appendHealthReportInputData(prompt, primaryType, scores, tongueFeatures, userSelfDescription, user, profile, season);
        prompt.append("# Task（第一步：仅辨证与调养指导）\n");
        prompt.append("请输出「深度分析报告」JSON：包含 analysis、summary、diet、lifestyle、acupoints、exercise、disclaimer。\n");
        prompt.append("严禁包含 plans 字段；不要生成任何阶段性执行计划卡片（计划将在第二步单独生成）。\n");
        prompt.append("若自述与舌象/分数不一致，在 analysis 中分点说明。\n\n");
        prompt.append("# Constraints\n");
        prompt.append("1. 只输出一个合法 JSON 对象，键名用英文，正文用中文。\n");
        prompt.append("2. 必须包含 disclaimer：\"以上建议仅供参考，严重不适请及时就医。\"\n");
        prompt.append("3. 不要输出 plans 键。\n\n");
        appendAntiTemplateConstraints(prompt);
        prompt.append("# 轮换提示（用于跨次降重）\n");
        prompt.append("- 本次推荐写作风格：").append(buildRotationHint(userId, "analysis")).append("\n");
        prompt.append("- 避免复用近期历史摘要中的高频句式，若语义接近请改写为新动作。\n\n");
        appendStructuredFieldContract(prompt);
        prompt.append("# analysis 写作要求\n");
        prompt.append("- analysis 必须按“1.证据归纳 2.冲突解释 3.调理优先级”输出；每一点都要对应输入证据。\n");
        prompt.append("- 如自述与舌象/分值存在不一致，必须在第2点给出至少1种合理解释与稳妥建议。\n\n");
        prompt.append("# Output Format（示例占位须换成真实辨证内容）\n");
        prompt.append("{\n");
        prompt.append("  \"analysis\": \"...\",\n");
        prompt.append("  \"summary\": \"...\",\n");
        prompt.append("  \"diet\": {\"recommend\":[\"...\",\"...\"],\"avoid\":[\"...\",\"...\"]},\n");
        prompt.append("  \"lifestyle\": [\"...\",\"...\"],\n");
        prompt.append("  \"acupoints\": [\n");
        prompt.append("    {\"name\":\"关元穴\",\"location\":\"...\",\"effect\":\"...\"},\n");
        prompt.append("    {\"name\":\"足三里穴\",\"location\":\"...\",\"effect\":\"...\"},\n");
        prompt.append("    {\"name\":\"肾俞穴\",\"location\":\"...\",\"effect\":\"...\"},\n");
        prompt.append("    {\"name\":\"三阴交穴\",\"location\":\"...\",\"effect\":\"...\"}\n");
        prompt.append("  ],\n");
        prompt.append("  \"exercise\": \"...\",\n");
        prompt.append("  \"disclaimer\": \"以上建议仅供参考，严重不适请及时就医。\"\n");
        prompt.append("}\n");
        streamDeepSeekUserPrompt(prompt.toString(), contentConsumer);
    }

    @Override
    public void generateHealthPlansRecommendationStream(Long userId, String primaryType, Map<String, Double> scores, List<String> tongueFeatures,
            String userSelfDescription, User user, UserHealthProfile profile, String priorReportContext, java.util.function.Consumer<String> contentConsumer) {
        if (openAiService == null) {
            contentConsumer.accept("{\"error\": \"AI服务未初始化\"}");
            return;
        }
        String season = resolveCurrentSeasonInChinese();
        StringBuilder prompt = new StringBuilder();
        prompt.append("# Role\n你是中医养生方案设计专家。根据已确认的辨证结论，设计可执行的四周养生计划（非医疗处方）。\n\n");
        prompt.append("# Context\n");
        prompt.append("- 主导体质: ").append(primaryType).append("\n");
        prompt.append("- 当前时令: ").append(season).append("\n\n");
        prompt.append("## 结构化输入快照（用于个体化约束）\n");
        prompt.append("- top2体质及分差: ").append(buildTopConstitutionSummary(scores)).append("\n");
        prompt.append("- 舌象高置信特征: ").append(buildTongueFeatureSummary(tongueFeatures)).append("\n");
        prompt.append("- 用户自述关键词: ").append(buildSelfDescriptionKeywords(userSelfDescription)).append("\n");
        prompt.append("- 档案风险约束: ").append(buildProfileRiskSummary(profile)).append("\n\n");
        prompt.append("## 已生成的辨证报告（须与此保持一致，勿自相矛盾）\n");
        prompt.append(priorReportContext != null ? priorReportContext : "（无）").append("\n\n");
        prompt.append("# Task（第二步：仅输出计划）\n");
        prompt.append("只输出一个 JSON 对象，且根对象仅有 \"plans\" 一个键。\n");
        appendPlansArrayContract(prompt);
        appendAntiTemplateConstraints(prompt);
        appendPlanQuantAndDedupConstraints(prompt);
        prompt.append("# 轮换提示（用于跨次降重）\n");
        prompt.append("- 本次计划动作风格建议：").append(buildRotationHint(userId, "plans")).append("\n");
        prompt.append("- 优先选择与“近期历史输出摘要”不同的动作组合。\n\n");
        prompt.append("# 风险约束\n");
        prompt.append("- 必须根据“档案风险约束”规避不适宜食材或动作；若有替代方案，写入 targetContent。\n");
        prompt.append("- 每条计划都要体现“为何适配当前体质与季节”的短说明（放在 description 内）。\n\n");
        prompt.append("# Output Format\n");
        prompt.append("{\"plans\":[\n");
        prompt.append("  {\"planType\":\"DIET\",\"planName\":\"...\",\"description\":\"...\",\"frequency\":\"DAILY\",\"targetContent\":\"...\",\"duration\":30},\n");
        prompt.append("  {\"planType\":\"EXERCISE\",\"planName\":\"...\",\"description\":\"...\",\"frequency\":\"WEEKLY\",\"targetContent\":\"...\",\"duration\":30},\n");
        prompt.append("  {\"planType\":\"ACUPOINT\",\"planName\":\"...\",\"description\":\"...\",\"frequency\":\"DAILY\",\"targetContent\":\"...\",\"duration\":30},\n");
        prompt.append("  {\"planType\":\"SLEEP\",\"planName\":\"...\",\"description\":\"...\",\"frequency\":\"DAILY\",\"targetContent\":\"...\",\"duration\":30}\n");
        prompt.append("]}\n");
        streamDeepSeekUserPrompt(prompt.toString(), contentConsumer);
    }

    private void streamDeepSeekUserPrompt(String userPrompt, java.util.function.Consumer<String> contentConsumer) {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(),
                    "你是一位专业的中医健康顾问。JSON 中 acupoints 的每一项必须是含 name、location、effect 三个键的对象（禁止字符串元素）；diet 必须是 recommend/avoid 两数组；plans 若出现则须 4 条且 planType 依次为 DIET、EXERCISE、ACUPOINT、SLEEP。字段值用中文。输出合法完整 JSON，勿用 Markdown 代码围栏。禁止模板化套话，禁止同义重复句，建议必须可执行且可量化。"));
            messages.add(new ChatMessage(ChatMessageRole.USER.value(), userPrompt));
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(deepSeekConfig.getModel())
                    .messages(messages)
                    .maxTokens(4000)
                    .temperature(deepSeekConfig.getTemperature())
                    .stream(true)
                    .build();
            openAiService.streamChatCompletion(request)
                    .doOnError(e -> {
                        log.error("分片调用 AI 失败", e);
                        contentConsumer.accept("{\"error\": \"AI 响应异常: " + e.getMessage() + "\"}");
                    })
                    .blockingForEach(completion -> {
                        if (completion.getChoices() != null && !completion.getChoices().isEmpty()) {
                            String content = completion.getChoices().get(0).getMessage().getContent();
                            if (content != null) {
                                contentConsumer.accept(content);
                            }
                        }
                    });
        } catch (Exception e) {
            log.error("生成失败", e);
            contentConsumer.accept("{\"error\": \"生成失败: " + e.getMessage() + "\"}");
        }
    }

    @Override
    public List<HerbalRecipe> recommendByCollaborativeFiltering(Long userId, int limit) {
        limit = normalizeLimit(limit);
        if (userId == null) {
            log.warn("协同过滤推荐失败：用户未登录");
            return Collections.emptyList();
        }

        String cacheKey = CacheConstants.AI_CF_RECOMMEND_CACHE_PREFIX + userId + ":limit:" + limit;
        Object cached = redisUtil.get(cacheKey);
        if (cached instanceof List) {
            try {
                @SuppressWarnings("unchecked")
                List<HerbalRecipe> cachedList = (List<HerbalRecipe>) cached;
                return cachedList;
            } catch (ClassCastException ignored) {}
        }

        // 从新表查询所有药膳收藏
        List<UserFavorite> allFavorites = userFavoriteMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getTargetType, "RECIPE")
        );
        Map<Long, Set<Long>> userFavoriteMap = buildUserFavoriteMap(allFavorites);
        Set<Long> targetFavorites = userFavoriteMap.getOrDefault(userId, Collections.emptySet());

        if (targetFavorites.isEmpty()) {
            log.info("用户{}无收藏记录，协同过滤降级为热门推荐", userId);
            List<HerbalRecipe> fallback = herbalRecipeMapper.selectPopularRecipes(limit);
            applyFavoriteFlag(fallback, userId);
            redisUtil.set(cacheKey, fallback, CacheTtlPolicy.AI_RECOMMENDATION.getSeconds(), TimeUnit.SECONDS);
            return fallback;
        }

        Map<Long, Double> scoreMap = new HashMap<>();

        userFavoriteMap.forEach((otherUserId, otherFavorites) -> {
            if (otherUserId.equals(userId) || otherFavorites.isEmpty()) {
                return;
            }
            double similarity = computeSimilarity(targetFavorites, otherFavorites);
            if (similarity <= 0) {
                return;
            }
            for (Long recipeId : otherFavorites) {
                if (targetFavorites.contains(recipeId)) {
                    continue;
                }
                scoreMap.merge(recipeId, similarity, Double::sum);
            }
        });

        if (scoreMap.isEmpty()) {
            log.info("协同过滤得分为空，降级为热门推荐");
            List<HerbalRecipe> fallback = herbalRecipeMapper.selectPopularRecipes(limit);
            applyFavoriteFlag(fallback, userId);
            redisUtil.set(cacheKey, fallback, CacheTtlPolicy.AI_RECOMMENDATION.getSeconds(), TimeUnit.SECONDS);
            return fallback;
        }

        List<Long> recommendIds = scoreMap.entrySet()
                .stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .limit(limit)
                .collect(Collectors.toList());

        List<HerbalRecipe> recipes = fetchActiveRecipesByIds(recommendIds);
        setCollaborativeReason(recipes, userFavoriteMap, userId);
        applyFavoriteFlag(recipes, userId);
        redisUtil.set(cacheKey, recipes, CacheConstants.AI_RECOMMENDATION_TTL_SECONDS, TimeUnit.SECONDS);
        return recipes;
    }

    @Override
    public List<HerbalRecipe> recommendByContentPreference(Long userId, int limit) {
        limit = normalizeLimit(limit);
        if (userId == null) {
            log.warn("内容推荐失败：用户未登录");
            return Collections.emptyList();
        }

        String cacheKey = CacheConstants.AI_CONTENT_RECOMMEND_CACHE_PREFIX + userId + ":limit:" + limit;
        Object cached = redisUtil.get(cacheKey);
        if (cached instanceof List) {
            try {
                @SuppressWarnings("unchecked")
                List<HerbalRecipe> cachedList = (List<HerbalRecipe>) cached;
                return cachedList;
            } catch (ClassCastException ignored) {}
        }

        UserPreferenceProfile profile = buildUserPreferenceProfile(userId);
        List<HerbalRecipe> candidates = herbalRecipeMapper.selectActiveRecipesForRecommendation(CONTENT_CANDIDATE_LIMIT);

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        String currentSeason = resolveCurrentSeason();
        Map<Long, Double> scored = new HashMap<>();
        for (HerbalRecipe recipe : candidates) {
            if (profile.favoriteRecipeIds.contains(recipe.getId())) {
                continue;
            }
            double score = computeContentScore(recipe, profile, currentSeason);
            if (score > 0) {
                scored.put(recipe.getId(), score);
            }
        }

        if (scored.isEmpty()) {
            log.info("内容推荐得分为空，返回热门数据");
            List<HerbalRecipe> fallback = herbalRecipeMapper.selectPopularRecipes(limit);
            applyFavoriteFlag(fallback, userId);
            redisUtil.set(cacheKey, fallback, CacheTtlPolicy.AI_RECOMMENDATION.getSeconds(), TimeUnit.SECONDS);
            return fallback;
        }

        List<Long> recommendIds = scored.entrySet()
                .stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .limit(limit)
                .collect(Collectors.toList());

        List<HerbalRecipe> recipes = fetchActiveRecipesByIds(recommendIds);
        setContentReason(recipes, profile, currentSeason);
        applyFavoriteFlag(recipes, userId);
        redisUtil.set(cacheKey, recipes, CacheConstants.AI_RECOMMENDATION_TTL_SECONDS, TimeUnit.SECONDS);
        return recipes;
    }

    @Override
    public List<HerbalRecipe> recommendPersonalized(Long userId, int limit) {
        limit = normalizeLimit(limit);
        if (userId == null) {
            log.warn("个性化推荐失败：用户未登录");
            return Collections.emptyList();
        }

        String cacheKey = CacheConstants.AI_PERSONALIZED_RECOMMEND_CACHE_PREFIX + userId + ":limit:" + limit;
        Object cached = redisUtil.get(cacheKey);
        if (cached instanceof List) {
            try {
                @SuppressWarnings("unchecked")
                List<HerbalRecipe> cachedList = (List<HerbalRecipe>) cached;
                return cachedList;
            } catch (ClassCastException ignored) {}
        }

        List<HerbalRecipe> cfList = recommendByCollaborativeFiltering(userId, limit * 2);
        List<HerbalRecipe> contentList = recommendByContentPreference(userId, limit * 2);

        LinkedHashMap<Long, HerbalRecipe> merged = new LinkedHashMap<>();
        mergeRecommendations(merged, cfList);
        mergeRecommendations(merged, contentList);

        if (merged.size() < limit) {
            List<HerbalRecipe> fallback = herbalRecipeMapper.selectPopularRecipes(limit * 2);
            applyFavoriteFlag(fallback, userId);
            fallback.forEach(recipe -> recipe.setRecommendationReason(
                    recipe.getRecommendationReason() != null ? recipe.getRecommendationReason() : "根据综合热度推荐"));
            mergeRecommendations(merged, fallback);
        }

        List<HerbalRecipe> result = merged.values()
                .stream()
                .limit(limit)
                .collect(Collectors.toList());

        redisUtil.set(cacheKey, result, CacheConstants.AI_RECOMMENDATION_TTL_SECONDS, TimeUnit.SECONDS);
        return result;
    }

    /**
     * 调用DeepSeek API（使用OpenAI SDK）
     */
    private String callDeepSeekApi(String prompt) {
        try {
            // 构建消息列表
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(),
                    "你是一位专业的中医健康顾问，擅长根据用户体质和症状推荐合适的药膳和养生建议。请始终使用中文回答，不要使用英文或其他语言。"));
            messages.add(new ChatMessage(ChatMessageRole.USER.value(), prompt));

            // 构建请求
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(deepSeekConfig.getModel())
                    .messages(messages)
                    .maxTokens(deepSeekConfig.getMaxTokens())
                    .temperature(deepSeekConfig.getTemperature())
                    .build();

            // 调用API
            String response = openAiService.createChatCompletion(request)
                    .getChoices()
                    .get(0)
                    .getMessage()
                    .getContent();

            // 清理响应，确保只返回中文内容
            if (response != null) {
                response = cleanChineseResponse(response.trim());
            }

            return response;

        } catch (Exception e) {
            log.error("调用DeepSeek API失败", e);
            throw new RuntimeException("API调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 带自定义System提示词的调用
     */
    private String callDeepSeekApiWithSystem(String systemText, String userPrompt) {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(),
                    StringUtils.hasText(systemText) ? systemText :
                            "你是一位专业的中医药膳师，请始终使用中文，并严格按要求返回JSON。"));
            messages.add(new ChatMessage(ChatMessageRole.USER.value(), userPrompt));

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(deepSeekConfig.getModel())
                    .messages(messages)
                    .maxTokens(deepSeekConfig.getMaxTokens())
                    .temperature(0.7)
                    .build();

            String response = openAiService.createChatCompletion(request)
                    .getChoices()
                    .get(0)
                    .getMessage()
                    .getContent();
            return response != null ? response.trim() : null;
        } catch (Exception e) {
            log.error("调用DeepSeek API（with system）失败", e);
            return null;
        }
    }

    @Override
    public void generateRecipeJsonStream(String promptText, java.util.function.Consumer<String> contentConsumer) {
        if (!StringUtils.hasText(promptText) || openAiService == null) {
            contentConsumer.accept("{\"error\":\"服务不可用或提示词为空\"}");
            return;
        }

        String sys = "你是资深的中医药膳师与营养师，严格输出JSON内容片段，不要多余解释。";
        String formatGuide =
                "{ \"recipeName\":\"\", \"constitutionType\":\"PINGHE|QIXU|YANGXU|YINXU|TANSHI|SHIRE|XUEYU|QIYU|TEBING|ALL\", \"season\":\"SPRING|SUMMER|AUTUMN|WINTER|ALL\", \"category\":\"汤品/粥品/茶饮/炒菜/甜品\", \"difficulty\":2, \"cookingTime\":30, \"servings\":2, \"ingredients\":[{\"name\":\"\",\"amount\":10,\"unit\":\"g\",\"note\":\"\"}], \"steps\":[\"步骤1\",\"步骤2\"], \"efficacy\":\"\", \"suitableSymptoms\":\"\", \"contraindications\":\"\", \"nutritionInfo\":{\"calorie\":0,\"protein_g\":0,\"fat_g\":0,\"carb_g\":0}, \"tips\":\"\" }";
        String userPrompt = "请基于以下提示，生成一份药膳，严格按给定JSON结构输出（仅JSON）：\n【提示词】\n" + promptText + "\n【结构】\n" + formatGuide;

        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), sys));
            messages.add(new ChatMessage(ChatMessageRole.USER.value(), userPrompt));

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(deepSeekConfig.getModel())
                    .messages(messages)
                    .maxTokens(deepSeekConfig.getMaxTokens())
                    .temperature(0.7)
                    .stream(true)
                    .build();

            openAiService.streamChatCompletion(request)
                    .doOnError(e -> {
                        log.error("生成药膳失败", e);
                        contentConsumer.accept("{\"error\":\"" + e.getMessage() + "\"}");
                    })
                    .blockingForEach(completion -> {
                        if (completion.getChoices() != null && !completion.getChoices().isEmpty()) {
                            String content = completion.getChoices().get(0).getMessage().getContent();
                            if (content != null) {
                                contentConsumer.accept(content);
                            }
                        }
                    });
        } catch (Exception e) {
            log.error("生成药膳异常", e);
            contentConsumer.accept("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 构建推荐理由Prompt
     */
    private String buildRecommendationReasonPrompt(HerbalRecipe recipe, UserConstitutionTest constitution) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("根据以下信息，生成一段个性化的药膳推荐理由（100-150字，仅使用中文）：\n\n");
        prompt.append("【用户体质】").append(constitution.getPrimaryConstitution());
        if (StringUtils.hasText(constitution.getSecondaryConstitution())) {
            prompt.append("（次要体质：").append(constitution.getSecondaryConstitution()).append("）");
        }
        prompt.append("\n\n");
        prompt.append("【药膳信息】\n");
        prompt.append("名称：").append(recipe.getRecipeName()).append("\n");
        if (StringUtils.hasText(recipe.getEfficacy())) {
            prompt.append("功效：").append(recipe.getEfficacy()).append("\n");
        }
        if (StringUtils.hasText(recipe.getSuitableSymptoms())) {
            prompt.append("适用症状：").append(recipe.getSuitableSymptoms()).append("\n");
        }
        prompt.append("\n请用专业、温暖的中文语言说明为什么这道药膳适合该用户，并给出食用建议。不要使用英文、拼音或任何非中文字符。");
        return prompt.toString();
    }

    /**
     * 构建对话推荐Prompt
     */
    private String buildConversationRecommendationPrompt(String conversationContent) {
        String prompt = "根据以下用户对话内容，推荐3-5道适合的药膳，只返回中文药膳名称列表（每行一个，仅使用中文）：\n\n" +
                "【对话内容】\n" + conversationContent + "\n\n" +
                "请基于中医理论，推荐适合的药膳。只返回中文药膳名称，不要使用英文或其他语言。";
        return prompt;
    }

    /**
     * 构建问答Prompt
     */
    private String buildQuestionAnswerPrompt(String question) {
        String prompt = "请作为专业的中医健康顾问，回答以下问题。回答要专业、准确、易懂（200字以内，仅使用中文）：\n\n" +
                "【问题】\n" + question +
                "\n\n请用中文回答，不要使用英文或其他语言。";
        return prompt;
    }

    @Override
    public String generateRecipeJsonByPrompt(String promptText) {
        if (!StringUtils.hasText(promptText)) {
            log.warn("生成药膳失败：提示词为空");
            return null;
        }

        if (openAiService == null) {
            log.warn("DeepSeek API服务未初始化，无法生成药膳JSON");
            return null;
        }

        try {
            String sys = "你是资深的中医药膳师与营养师，严格输出JSON，键名必须与后端实体一致，且不要包含注释或多余文本。必须返回完整字段，不得省略。";
            String formatGuide =
                    "{\n" +
                    "  \"recipeName\": \"\",\n" +
                    "  \"constitutionType\": \"PINGHE|QIXU|YANGXU|YINXU|TANSHI|SHIRE|XUEYU|QIYU|TEBING|ALL\",\n" +
                    "  \"season\": \"SPRING|SUMMER|AUTUMN|WINTER|ALL\",\n" +
                    "  \"category\": \"汤品/粥品/茶饮/炒菜/甜品等之一\",\n" +
                    "  \"difficulty\": 1,\n" +
                    "  \"cookingTime\": 30,\n" +
                    "  \"servings\": 2,\n" +
                    "  \"ingredients\": [ { \"name\": \"\", \"amount\": 10, \"unit\": \"g\", \"note\": \"\" } ],\n" +
                    "  \"steps\": [\"步骤1\", \"步骤2\"],\n" +
                    "  \"efficacy\": \"\",\n" +
                    "  \"suitableSymptoms\": \"\",\n" +
                    "  \"contraindications\": \"\",\n" +
                    "  \"nutritionInfo\": { \"calorie\": 0, \"protein_g\": 0, \"fat_g\": 0, \"carb_g\": 0 },\n" +
                    "  \"tips\": \"\"\n" +
                    "}";

            String userPrompt =
                    "请基于以下提示，生成一份中医药膳，严格按给定JSON结构输出（不要任何额外说明、标题或Markdown）：\n" +
                    "【提示词】\n" + promptText + "\n\n" +
                    "【JSON结构（键名必须完全一致）】\n" + formatGuide;

            String response = callDeepSeekApiWithSystem(sys, userPrompt);
            return response;
        } catch (Exception e) {
            log.error("生成药膳JSON失败", e);
            return null;
        }
    }

    /**
     * 解析推荐响应（简化实现）
     */
    private List<HerbalRecipe> parseRecommendationResponse(String response) {
        List<HerbalRecipe> recipes = new ArrayList<>();
        if (!StringUtils.hasText(response)) {
            return recipes;
        }

        // 按行分割，提取药膳名称
        String[] lines = response.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("【")) {
                continue;
            }

            // 移除序号和特殊字符
            line = line.replaceAll("^\\d+[.、]\\s*", "").trim();

            // 搜索匹配的药膳
            List<HerbalRecipe> found = herbalRecipeMapper.selectList(
                    new QueryWrapper<HerbalRecipe>()
                            .like("recipe_name", line)
                            .eq("status", 1)
                            .last("LIMIT 1")
            );

            if (!found.isEmpty()) {
                recipes.add(found.get(0));
            }

            // 限制最多返回5个
            if (recipes.size() >= 5) {
                break;
            }
        }

        return recipes;
    }

    /**
     * 生成默认推荐理由（降级方案）
     */
    private String buildDefaultRecommendationReason(HerbalRecipe recipe, UserConstitutionTest constitution) {
        StringBuilder reason = new StringBuilder();
        reason.append("根据您的").append(constitution.getPrimaryConstitution()).append("体质，");
        reason.append("推荐这道").append(recipe.getRecipeName()).append("。");
        if (StringUtils.hasText(recipe.getEfficacy())) {
            reason.append("该药膳具有").append(recipe.getEfficacy()).append("的功效，");
        }
        reason.append("适合您当前的身体状况。建议适量食用，配合规律作息效果更佳。");
        return reason.toString();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_RECOMMENDATION_LIMIT;
        }
        return Math.min(limit, 20);
    }

    private Map<Long, Set<Long>> buildUserFavoriteMap(List<UserFavorite> favorites) {
        Map<Long, Set<Long>> map = new HashMap<>();
        for (UserFavorite favorite : favorites) {
            if (favorite.getUserId() == null || favorite.getTargetId() == null) {
                continue;
            }
            map.computeIfAbsent(favorite.getUserId(), k -> new HashSet<>())
                    .add(favorite.getTargetId());
        }
        return map;
    }

    private double computeSimilarity(Set<Long> target, Set<Long> other) {
        if (target.isEmpty() || other.isEmpty()) {
            return 0;
        }
        int intersection = 0;
        for (Long id : target) {
            if (other.contains(id)) {
                intersection++;
            }
        }
        if (intersection == 0) {
            return 0;
        }
        int union = target.size() + other.size() - intersection;
        return union == 0 ? 0 : (double) intersection / union;
    }

    private List<HerbalRecipe> fetchActiveRecipesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<HerbalRecipe> recipeList = herbalRecipeMapper.selectActiveRecipesByIds(ids);
        Map<Long, HerbalRecipe> recipeMap = new HashMap<>();
        for (HerbalRecipe recipe : recipeList) {
            recipeMap.put(recipe.getId(), recipe);
        }
        List<HerbalRecipe> ordered = new ArrayList<>();
        for (Long id : ids) {
            HerbalRecipe recipe = recipeMap.get(id);
            if (recipe != null) {
                ordered.add(recipe);
            }
        }
        return ordered;
    }

    private void setCollaborativeReason(List<HerbalRecipe> recipes, Map<Long, Set<Long>> userFavoriteMap, Long userId) {
        if (recipes == null || recipes.isEmpty()) {
            return;
        }
        for (HerbalRecipe recipe : recipes) {
            recipe.setRecommendationReason("基于与您口味相近用户的收藏偏好，为您推荐此药膳。");
        }
    }

    private UserPreferenceProfile buildUserPreferenceProfile(Long userId) {
        UserPreferenceProfile profile = new UserPreferenceProfile();
        if (userId == null) {
            return profile;
        }

        UserConstitutionTest latestTest = userConstitutionTestMapper.selectLatestByUserId(userId);
        if (latestTest != null) {
            profile.primaryConstitution = latestTest.getPrimaryConstitution();
            profile.secondaryConstitution = latestTest.getSecondaryConstitution();
        }

        List<Long> favoriteIds = userFavoriteMapper.selectTargetIdsByUserId(userId, "RECIPE");
        if (favoriteIds != null) {
            profile.favoriteRecipeIds.addAll(favoriteIds);
        }

        if (!profile.favoriteRecipeIds.isEmpty()) {
            List<HerbalRecipe> favoriteRecipes = herbalRecipeMapper.selectActiveRecipesByIds(new ArrayList<>(profile.favoriteRecipeIds));
            for (HerbalRecipe recipe : favoriteRecipes) {
                accumulatePreference(profile.preferredCategories, recipe.getCategory());
                accumulatePreference(profile.preferredSeasons, recipe.getSeason());
                accumulatePreference(profile.preferredEffects, recipe.getEfficacy());
            }
        }

        return profile;
    }

    private void accumulatePreference(Map<String, Integer> counter, String source) {
        if (!StringUtils.hasText(source)) {
            return;
        }
        for (String token : splitToTokens(source)) {
            if (!token.isEmpty()) {
                counter.merge(token, 1, Integer::sum);
            }
        }
    }

    private List<String> splitToTokens(String source) {
        if (!StringUtils.hasText(source)) {
            return Collections.emptyList();
        }
        String normalized = source.replace("、", ",")
                .replace("，", ",")
                .replace("/", ",");
        String[] parts = normalized.split(",");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                tokens.add(trimmed.toUpperCase());
            }
        }
        return tokens;
    }

    private double computeContentScore(HerbalRecipe recipe, UserPreferenceProfile profile, String currentSeason) {
        if (recipe == null) {
            return 0;
        }
        double score = 0;

        if (StringUtils.hasText(profile.primaryConstitution) &&
                containsIgnoreCase(recipe.getConstitutionType(), profile.primaryConstitution)) {
            score += 3.0;
        }
        if (StringUtils.hasText(profile.secondaryConstitution) &&
                containsIgnoreCase(recipe.getConstitutionType(), profile.secondaryConstitution)) {
            score += 1.5;
        }

        score += preferenceScore(profile.preferredCategories, recipe.getCategory(), 1.2);
        score += preferenceScore(profile.preferredSeasons, recipe.getSeason(), 0.8);

        if (StringUtils.hasText(recipe.getSeason()) &&
                recipe.getSeason().toUpperCase().contains(currentSeason)) {
            score += 0.5;
        }

        for (String effect : splitToTokens(recipe.getEfficacy())) {
            score += profile.preferredEffects.getOrDefault(effect, 0) * 0.3;
        }

        if (recipe.getFavoriteCount() != null) {
            score += Math.min(recipe.getFavoriteCount(), 200) * 0.01;
        }
        if (recipe.getViewCount() != null) {
            score += Math.log(recipe.getViewCount() + 1) * 0.2;
        }

        return score;
    }

    private double preferenceScore(Map<String, Integer> preference, String value, double weight) {
        if (!StringUtils.hasText(value) || preference.isEmpty()) {
            return 0;
        }
        double score = 0;
        for (String token : splitToTokens(value)) {
            int count = preference.getOrDefault(token, 0);
            if (count > 0) {
                score += weight * count;
            }
        }
        return score;
    }

    private boolean containsIgnoreCase(String source, String target) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
            return false;
        }
        return source.toUpperCase().contains(target.toUpperCase());
    }

    private String resolveCurrentSeason() {
        java.time.Month month = java.time.LocalDate.now().getMonth();
        switch (month) {
            case DECEMBER:
            case JANUARY:
            case FEBRUARY:
                return "WINTER";
            case MARCH:
            case APRIL:
            case MAY:
                return "SPRING";
            case JUNE:
            case JULY:
            case AUGUST:
                return "SUMMER";
            default:
                return "AUTUMN";
        }
    }

    private void applyFavoriteFlag(List<HerbalRecipe> recipes, Long userId) {
        if (recipes == null || recipes.isEmpty() || userId == null) {
            return;
        }
        List<Long> favoriteIds = userFavoriteMapper.selectTargetIdsByUserId(userId, "RECIPE");
        Set<Long> favoriteSet = favoriteIds == null ? Collections.emptySet() : new HashSet<>(favoriteIds);
        for (HerbalRecipe recipe : recipes) {
            recipe.setIsFavorited(favoriteSet.contains(recipe.getId()));
        }
    }

    private void setContentReason(List<HerbalRecipe> recipes, UserPreferenceProfile profile, String currentSeason) {
        if (recipes == null) {
            return;
        }
        for (HerbalRecipe recipe : recipes) {
            StringBuilder reason = new StringBuilder("结合您的体质与历史偏好推荐：");
            if (StringUtils.hasText(profile.primaryConstitution) &&
                    containsIgnoreCase(recipe.getConstitutionType(), profile.primaryConstitution)) {
                reason.append("适合").append(profile.primaryConstitution).append("体质，");
            }
            if (StringUtils.hasText(recipe.getSeason()) &&
                    recipe.getSeason().toUpperCase().contains(currentSeason)) {
                reason.append("当前季节食用更佳，");
            }
            if (StringUtils.hasText(recipe.getEfficacy())) {
                reason.append("功效：").append(recipe.getEfficacy()).append("。");
            } else {
                reason.append("帮助更好地平衡身体。");
            }
            recipe.setRecommendationReason(reason.toString());
        }
    }

    private void mergeRecommendations(Map<Long, HerbalRecipe> container, List<HerbalRecipe> candidates) {
        if (candidates == null) {
            return;
        }
        for (HerbalRecipe recipe : candidates) {
            if (recipe == null || recipe.getId() == null) {
                continue;
            }
            container.putIfAbsent(recipe.getId(), recipe);
        }
    }

    private static class UserPreferenceProfile {
        private String primaryConstitution;
        private String secondaryConstitution;
        private final Map<String, Integer> preferredCategories = new HashMap<>();
        private final Map<String, Integer> preferredEffects = new HashMap<>();
        private final Map<String, Integer> preferredSeasons = new HashMap<>();
        private final Set<Long> favoriteRecipeIds = new HashSet<>();
    }

    /**
     * 清理响应内容，确保主要使用中文
     * 移除明显的英文段落，但保留中文内容中的标点和数字
     */
    private String cleanChineseResponse(String response) {
        // 移除所有清洗逻辑，原样返回
        return response;
    }

}

