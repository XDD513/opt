package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文章通知实体
 */
@Data
@TableName("user_notification")
public class ArticleNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    /**
     * 接收者角色（0-患者 1-管理员）
     */
    private Integer roleType;

    /**
     * 通知标题
     */
    private String title;

    /**
     * 通知内容
     */
    private String content;

    /**
     * 通知类型：REVIEW/OFFLINE/INTERACTION/SYSTEM
     */
    private String notificationType;

    /**
     * 业务类型：ARTICLE
     */
    private String bizType;

    /**
     * 业务主键（文章ID）
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long bizId;

    /**
     * 是否已读（0-未读 1-已读）
     */
    private Integer isRead;

    /**
     * 是否删除（0-否 1-是）
     */
    private Integer isDeleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}

