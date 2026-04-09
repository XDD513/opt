package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户收藏实体类（统一收藏表）
 *
 * @author Hospital Team
 * @since 2025-12-09
 */
@Data
@TableName("user_favorite")
public class UserFavorite implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 收藏ID
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
     * 目标类型（ARTICLE-文章，RECIPE-药膳，ACUPOINT-穴位，ACUPOINT_COMBINATION-穴位组合）
     */
    private String targetType;

    /**
     * 目标ID
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long targetId;

    /**
     * 收藏备注
     */
    private String remark;

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

    // ========== 以下字段来自关联查询，非数据库字段 ==========

    /**
     * 文章标题（来自health_article表，仅当target_type=ARTICLE时有效）
     */
    @TableField(exist = false)
    private String title;

    /**
     * 文章摘要（来自health_article表，仅当target_type=ARTICLE时有效）
     */
    @TableField(exist = false)
    private String summary;

    /**
     * 封面图片（来自health_article表，仅当target_type=ARTICLE时有效）
     */
    @TableField(exist = false)
    private String coverImage;

    /**
     * 作者姓名（来自user表，仅当target_type=ARTICLE时有效）
     */
    @TableField(exist = false)
    private String authorName;

    /**
     * 文章分类（来自health_article表，仅当target_type=ARTICLE时有效）
     */
    @TableField(exist = false)
    private String category;

    /**
     * 文章浏览量（来自health_article表，仅当target_type=ARTICLE时有效）
     */
    @TableField(exist = false)
    private Integer viewCount;

    /**
     * 文章点赞数（来自health_article表，仅当target_type=ARTICLE时有效）
     */
    @TableField(exist = false)
    private Integer likeCount;

    /**
     * 文章收藏数（来自health_article表，仅当target_type=ARTICLE时有效）
     */
    @TableField(exist = false)
    private Integer favoriteCount;

    /**
     * 药膳名称（来自herbal_recipe表，仅当target_type=RECIPE时有效）
     */
    @TableField(exist = false)
    private String recipeName;

    /**
     * 药膳分类（来自herbal_recipe表，仅当target_type=RECIPE时有效）
     */
    @TableField(exist = false)
    private String recipeCategory;

    /**
     * 药膳难度（来自herbal_recipe表，仅当target_type=RECIPE时有效）
     */
    @TableField(exist = false)
    private String difficulty;

    /**
     * 药膳制作时间（来自herbal_recipe表，仅当target_type=RECIPE时有效）
     */
    @TableField(exist = false)
    private Integer cookingTime;

    /**
     * 药膳功效（来自herbal_recipe表，仅当target_type=RECIPE时有效）
     */
    @TableField(exist = false)
    private String efficacy;

    /**
     * 药膳图片（来自herbal_recipe表，仅当target_type=RECIPE时有效）
     */
    @TableField(exist = false)
    private String recipeImage;

    /**
     * 药膳浏览量（来自herbal_recipe表，仅当target_type=RECIPE时有效）
     */
    @TableField(exist = false)
    private Integer recipeViewCount;

    /**
     * 药膳收藏数（来自herbal_recipe表，仅当target_type=RECIPE时有效）
     */
    @TableField(exist = false)
    private Integer recipeFavoriteCount;
}

