package com.hospital.service;

import com.hospital.entity.HerbalRecipe;
import com.hospital.entity.UserConstitutionTest;

import java.util.List;

/**
 * AI推荐服务接口
 *
 * @author Hospital Team
 * @since 2025-01-XX
 */
public interface AiRecommendationService {

    /**
     * 生成个性化推荐理由
     *
     * @param recipe 药膳信息
     * @param constitution 用户体质测试结果
     * @return 推荐理由文本
     */
    String generateRecommendationReason(HerbalRecipe recipe, UserConstitutionTest constitution);

    /**
     * 基于对话内容推荐药膳
     *
     * @param conversationContent 对话内容
     * @param userId 用户ID
     * @return 推荐的药膳列表
     */
    List<HerbalRecipe> recommendByConversation(String conversationContent, Long userId);

    /**
     * 智能问答
     *
     * @param question 用户问题
     * @param userId 用户ID
     * @return AI回答
     */
    String answerQuestion(String question, Long userId);

    /**
     * 根据提示词生成一份药膳配方（JSON字符串）
     * 要求返回严格的JSON，字段需与后端实体一致：
     * recipeName, constitutionType, season, category, difficulty, cookingTime, servings,
     * ingredients(数组[{name, amount, unit}]), steps(字符串数组),
     * efficacy, suitableSymptoms, contraindications, nutritionInfo(对象), tips, image, videoUrl
     *
     * @param prompt 提示词（可包含体质、季节、功效等约束）
     * @return JSON字符串；为空表示生成失败
     */
    String generateRecipeJsonByPrompt(String prompt);

    /**
     * 分片生成药膳JSON文本
     * @param prompt 提示词
     * @param contentConsumer 文本分片消费回调
     */
    void generateRecipeJsonStream(String prompt, java.util.function.Consumer<String> contentConsumer);

    /**
     * 生成多模态融合养生方案 (Phase 3: AI + 规则 + 舌象)
     * @param userId 用户ID
     * @param primaryType 主体质
     * @param scores 体质得分
     * @param tongueFeatures 舌象特征
     * @param userSelfDescription 用户自述（舌诊后的主观情况描述，可为空）
     * @param user 用户实体
     * @param profile 健康档案
     * @return 融合后的AI建议
     */
    String generateMultiModalRecommendation(Long userId, String primaryType, java.util.Map<String, Double> scores, java.util.List<String> tongueFeatures, String userSelfDescription, com.hospital.entity.User user, com.hospital.entity.UserHealthProfile profile);

    /**
     * 生成多模态融合养生方案（分片输出）
     *
     * @param userId 用户ID
     * @param primaryType 主体质
     * @param scores 体质得分
     * @param tongueFeatures 舌象特征
     * @param userSelfDescription 用户自述
     * @param user 用户实体
     * @param profile 健康档案
     * @param contentConsumer 内容输出回调
     */
    void generateMultiModalRecommendationStream(Long userId, String primaryType, java.util.Map<String, Double> scores, java.util.List<String> tongueFeatures, String userSelfDescription, com.hospital.entity.User user, com.hospital.entity.UserHealthProfile profile, java.util.function.Consumer<String> contentConsumer);

    /**
     * 分片生成「深度分析报告」JSON（含 analysis/summary/diet 等，不含 plans 或 plans 为空）
     */
    void generateHealthAnalysisRecommendationStream(Long userId, String primaryType, java.util.Map<String, Double> scores, java.util.List<String> tongueFeatures, String userSelfDescription, com.hospital.entity.User user, com.hospital.entity.UserHealthProfile profile, java.util.function.Consumer<String> contentConsumer);

    /**
     * 在已有辨证分析基础上，仅分片生成「养生计划」JSON（将只解析并合并 plans 字段）
     * @param priorReportContext 上一步报告中的分析/原则等文本摘要
     */
    void generateHealthPlansRecommendationStream(Long userId, String primaryType, java.util.Map<String, Double> scores, java.util.List<String> tongueFeatures, String userSelfDescription, com.hospital.entity.User user, com.hospital.entity.UserHealthProfile profile, String priorReportContext, java.util.function.Consumer<String> contentConsumer);

    /**
     * 基于协同过滤的药膳推荐
     *
     * @param userId 用户ID
     * @param limit  推荐数量
     * @return 药膳列表
     */
    List<HerbalRecipe> recommendByCollaborativeFiltering(Long userId, int limit);

    /**
     * 基于内容画像的药膳推荐
     *
     * @param userId 用户ID
     * @param limit  推荐数量
     * @return 药膳列表
     */
    List<HerbalRecipe> recommendByContentPreference(Long userId, int limit);

    /**
     * 综合个性化推荐
     *
     * @param userId 用户ID
     * @param limit  推荐数量
     * @return 药膳列表
     */
    List<HerbalRecipe> recommendPersonalized(Long userId, int limit);
}

