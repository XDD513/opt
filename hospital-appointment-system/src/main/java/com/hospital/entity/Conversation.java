package com.hospital.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 医患对话会话实体
 */
@Data
@TableName("conversation")
public class Conversation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("patient_id")
    private Long patientId;

    // 已移除医生维度，使用 participant1/participant2 表达双方

    @TableField("conversation_type")
    private String conversationType;

    @TableField("participant1_user_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long participant1UserId;

    @TableField("participant1_role")
    private String participant1Role;

    @TableField("participant2_user_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long participant2UserId;

    @TableField("participant2_role")
    private String participant2Role;

    @TableField("title")
    private String title;

    @TableField("summary")
    private String summary;

    @TableField("patient_nickname")
    private String patientNickname;

    @TableField("patient_avatar")
    private String patientAvatar;

    // 对方昵称、头像不再在表中冗余保存，保持由前端或关联用户解析

    @TableField("last_message_preview")
    private String lastMessagePreview;

    @TableField("last_sender_role")
    private String lastSenderRole;

    @TableField("last_sender_name")
    private String lastSenderName;

    @TableField("last_sender_avatar")
    private String lastSenderAvatar;

    @TableField("last_message_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastMessageTime;

    @TableField("unread_for_patient")
    private Integer unreadForPatient;

    // 不再单独维护“医生未读数”

    @TableField("unread_for_participant1")
    private Integer unreadForParticipant1;

    @TableField("unread_for_participant2")
    private Integer unreadForParticipant2;

    @TableField("status")
    private String status;

    @TableField("deleted_by_patient")
    private Integer deletedByPatient;

    // 不再单独维护“医生删除标记”

    @TableField("deleted_by_participant1")
    private Integer deletedByParticipant1;

    @TableField("deleted_by_participant2")
    private Integer deletedByParticipant2;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}


