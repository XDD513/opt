package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户体质测试记录实体类
 *
 * @author TCM Health Team
 * @since 2025-11-03
 */
@Data
@TableName("user_constitution_test")
public class UserConstitutionTest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 测试记录ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    /**
     * 用户ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    /**
     * 主要体质代码
     */
    private String primaryConstitution;

    /**
     * 次要体质代码
     */
    private String secondaryConstitution;

    /**
     * 测试结果（JSON格式，包含各体质得分）
     */
    private String testResult;

    /**
     * 是否生成报告（0-未生成，1-已生成）
     */
    private Integer reportGenerated;

    /**
     * 测试日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime testDate;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 舌诊分析结果 (JSON格式)
     * 标记为 exist = false，因为数据库中可能不存在此字段
     */
    @TableField(exist = false)
    private String tongueResult;
}

