package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 题目推荐配置实体类
 *
 * @author Hospital Team
 * @since 2025-12-09
 */
@Data
@TableName("question_recommendation_config")
public class QuestionRecommendationConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /**
     * 题目ID
     */
    @TableField("question_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long questionId;

    /**
     * 科室ID（NULL表示通用）
     */
    @TableField("department_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long departmentId;

    /**
     * 症状关键词
     */
    @TableField("symptom_keyword")
    private String symptomKeyword;

    /**
     * 推荐权重（0-1）
     */
    @TableField("recommend_weight")
    private BigDecimal recommendWeight;

    /**
     * 匹配类型（EXACT-精确, FUZZY-模糊）
     */
    @TableField("match_type")
    private String matchType;

    /**
     * 优先级
     */
    @TableField("priority")
    private Integer priority;

    /**
     * 是否启用
     */
    @TableField("is_enabled")
    private Integer isEnabled;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}


