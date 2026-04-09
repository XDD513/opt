package com.hospital.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 统计数据DTO
 */
@Data
public class StatisticsDTO {

    /**
     * 管理员统计数据
     */
    @Data
    public static class AdminStats {
        private Integer users;            // 用户数量
    }

    /**
     * 患者统计数据
     */
    @Data
    public static class PatientStats {
        private Integer testCount;        // 体质测试次数
        private Integer recipeCount;      // 收藏药膳数量
        private Integer checkinDays;      // 连续打卡天数
    }

    /**
     * 管理员：用户体质测试统计（用于统计页面展示）
     */
    @Data
    public static class AdminUserTestStats {
        private Integer totalPatients;                 // 体质测试覆盖的总用户数（患者）
        private Integer testedPatients;               // 完成体质测试的用户数（去重）
        private Integer totalTestRecords;            // 体质测试记录总数
        private Double completionRate;               // 完成率（0-100）
        private Map<String, Integer> primaryConstitutionCounts; // 主要体质分布：代码 -> 次数
        private List<UserTestTrendData> last30DaysTrend;          // 最近30天趋势：按测试日期计数

        // ===== 体质测试结果：穴位/饮食等（用于统计页展示）=====
        private List<TopItemStat> topAcupoints;                 // 推荐穴位 TopN（按出现次数聚合）
        private List<TopItemStat> topDietRecommend;           // 宜食食物 TopN
        private List<TopItemStat> topDietAvoid;                // 忌食食物 TopN
    }

    /**
     * 最近趋势数据（按日期聚合）
     */
    @Data
    public static class UserTestTrendData {
        private String date;            // yyyy-MM-dd
        private Integer testCount;    // 当日测试次数（记录数）
    }

    /**
     * Top 聚合统计项
     */
    @Data
    public static class TopItemStat {
        private String item;     // 项目名称（穴位名/食物名等）
        private Integer count;  // 出现次数（聚合口径：按“测试记录”出现一次计数）
    }

}
