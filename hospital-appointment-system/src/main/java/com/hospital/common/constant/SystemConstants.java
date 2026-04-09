package com.hospital.common.constant;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 系统常量类
 *
 * @author Hospital Team
 * @since 2025-10-24
 */
@Component
public class SystemConstants {

    /**
     * -- GETTER --
     *  获取JWT密钥
     */
    @Getter
    private static String jwtSecret;
    /**
     * -- GETTER --
     *  获取JWT过期时间（毫秒）
     */
    @Getter
    private static Long jwtExpiration;

    /**
     * -- GETTER --
     *  获取刷新令牌过期时间（毫秒）
     */
    @Getter
    private static Long jwtRefreshExpiration;
    /**
     * -- GETTER --
     *  获取支付超时时间（分钟）
     */
    @Getter
    private static Integer paymentTimeoutMinutes;

    @Value("${hospital.auth.jwt-secret}")
    public void setJwtSecret(String secret) {
        SystemConstants.jwtSecret = secret;
    }

    @Value("${hospital.auth.jwt-expiration:7200000}")
    public void setJwtExpiration(Long expiration) {
        SystemConstants.jwtExpiration = expiration;
    }

    @Value("${hospital.auth.jwt-refresh-expiration:1209600000}")
    public void setJwtRefreshExpiration(Long expiration) {
        SystemConstants.jwtRefreshExpiration = expiration;
    }

    @Value("${hospital.appointment.payment-timeout-minutes:30}")
    public void setPaymentTimeoutMinutes(Integer minutes) {
        SystemConstants.paymentTimeoutMinutes = minutes;
    }

    /**
     * Token请求头名称
     */
    public static final String TOKEN_HEADER = "Authorization";

    /**
     * Token前缀
     */
    public static final String TOKEN_PREFIX = "Bearer ";

    /**
     * Redis Key前缀
     */
    public static final String REDIS_KEY_PREFIX = "hospital:";

    /**
     * 用户Token缓存前缀
     */
    public static final String REDIS_USER_TOKEN = REDIS_KEY_PREFIX + "user:token:";

    /**
     * 验证码缓存前缀
     */
    public static final String REDIS_CAPTCHA = REDIS_KEY_PREFIX + "captcha:";

    /**
     * 排班号源锁前缀
     */
    public static final String REDIS_SCHEDULE_LOCK = REDIS_KEY_PREFIX + "schedule:lock:";

    /**
     * 默认分页大小
     */
    public static final Integer DEFAULT_PAGE_SIZE = 10;

    /**
     * 最大分页大小
     */
    public static final Integer MAX_PAGE_SIZE = 100;

    /**
     * 可提前预约天数
     */
    public static final Integer ADVANCE_APPOINTMENT_DAYS = 7;

    /**
     * 取消预约提前时间（小时）
     */
    public static final Integer CANCEL_APPOINTMENT_HOURS = 2;

}

