package com.hospital.common.constant;

/**
 * 推荐相关常量类
 * 定义智能推荐功能相关的所有常量
 *
 * @author Hospital Team
 * @since 2025-12-20
 */
public class RecommendationConstants {

    // ==================== 缓存相关常量 ====================

    /**
     * 推荐配置缓存键前缀
     */
    public static final String CACHE_RECOMMEND_CONFIG_PREFIX = CacheConstants.REDIS_KEY_PREFIX + "recommend:config:";

    /**
     * 推荐配置缓存TTL（分钟）
     */
    public static final long CACHE_RECOMMEND_CONFIG_TTL_MINUTES = 7;


    // ==================== 重试相关常量 ====================

    /**
     * AI生成题目最大重试次数
     */
    public static final int MAX_RETRY_TIMES = 3;

    // ==================== 题目相关常量 ====================

    /**
     * 默认题目数量（新用户首次测试）
     */
    public static final int DEFAULT_QUESTION_COUNT = 66;

    /**
     * 最少题目数量（推荐测试）
     */
    public static final int MIN_QUESTION_COUNT = 30;

    /**
     * 症状关键词最大长度（字符）
     */
    public static final int SYMPTOM_KEYWORD_MAX_LENGTH = 50;

    // ==================== 推荐权重常量 ====================

    /**
     * AI生成题目的推荐权重
     */
    public static final double AI_GENERATED_QUESTION_WEIGHT = 0.9;

    /**
     * 推荐题目的默认权重
     */
    public static final double RECOMMENDED_QUESTION_WEIGHT = 0.8;

    /**
     * 补充题目的默认权重
     */
    public static final double SUPPLEMENT_QUESTION_WEIGHT = 0.5;

    /**
     * 推荐优先级（AI生成题目）
     */
    public static final int AI_GENERATED_PRIORITY = 100;

    /**
     * 推荐优先级（默认）
     */
    public static final double DEFAULT_RECOMMEND_PRIORITY = 0.9;

    /**
     * 推荐权重（默认）
     */
    public static final double DEFAULT_RECOMMEND_WEIGHT = 0.9;

    // ==================== 匹配类型常量 ====================

    /**
     * 模糊匹配类型
     */
    public static final String MATCH_TYPE_FUZZY = "FUZZY";

    // ==================== 题目分类常量 ====================

    /**
     * AI生成题目分类
     */
    public static final String QUESTION_CATEGORY_AI_GENERATED = "AI生成";

    private RecommendationConstants() {
        // 防止实例化
    }
}

