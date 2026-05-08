package com.hospital.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OSS配置类
 *
 * @author Hospital Team
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssConfig {

    /**
     * AccessKey ID
     */
    private String accessKeyId;

    /**
     * AccessKey Secret
     */
    private String accessKeySecret;

    /**
     * OSS端点
     */
    private String endpoint;

    /**
     * 存储桶名称
     */
    private String bucketName;

    /**
     * 头像存储路径
     */
    private String avatarPath = "avatar/";

    /**
     * URL协议（http或https）
     */
    private String urlProtocol = "https";

    /**
     * {@code GET /api/oss/presigned-url} 允许的最大有效期（分钟），防止客户端请求过长签名窗口。
     * 实际有效期取「请求参数」与「本上限」的较小值。
     */
    private Integer presignedUrlMaxMinutes = 120;
}

