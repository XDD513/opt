package com.hospital.dto.request;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 创建医患对话会话请求
 */
@Data
public class ConversationCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "患者ID不能为空")
    private Long patientId;

    /**
     * 对话参与者2的用户ID（管理员创建会话时可为空，将使用当前管理员ID）
     */
    private Long participant2UserId;

    /**
     * 会话类型：USER_USER（用户-用户）或 ADMIN_USER（管理员-用户）
     */
    private String conversationType;

    @Size(max = 120, message = "标题长度不能超过120个字符")
    private String title;

    @Size(max = 255, message = "摘要长度不能超过255个字符")
    private String summary;
}


