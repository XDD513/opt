package com.hospital.service.impl;

import com.hospital.dto.StatisticsDTO;
import com.hospital.mapper.StatisticsMapper;
import com.hospital.service.StatisticsService;
import com.hospital.util.CacheKeyBuilder;
import com.hospital.util.CacheTtlPolicy;
import com.hospital.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 统计数据服务实现类
 */
@Slf4j
@Service
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    private StatisticsMapper statisticsMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Override
    public StatisticsDTO.AdminStats getAdminStats() {
        log.info("获取管理员统计数据");
        String cacheKey = CacheKeyBuilder.of("hospital:admin:stats:overview").build();
        Object cached = redisUtil.get(cacheKey);
        if (cached instanceof StatisticsDTO.AdminStats) {
            return (StatisticsDTO.AdminStats) cached;
        }
        StatisticsDTO.AdminStats data = statisticsMapper.getAdminStats();
        // 管理员统计数据，缓存 10 分钟
        redisUtil.set(cacheKey, data, CacheTtlPolicy.CONVERSATION_DETAIL.getSeconds(), TimeUnit.SECONDS);
        return data;
    }

    @Override
    public StatisticsDTO.PatientStats getPatientStats(Long patientId) {
        log.info("获取患者统计数据，患者ID：{}", patientId);
        String cacheKey = CacheKeyBuilder.of("hospital:patient:stats:overview")
                .append("patient", patientId)
                .build();
        Object cached = redisUtil.get(cacheKey);
        if (cached instanceof StatisticsDTO.PatientStats) {
            return (StatisticsDTO.PatientStats) cached;
        }
        StatisticsDTO.PatientStats data = statisticsMapper.getPatientStats(patientId);
        // 患者统计数据，缓存 10 分钟
        redisUtil.set(cacheKey, data, CacheTtlPolicy.CONVERSATION_DETAIL.getSeconds(), TimeUnit.SECONDS);
        return data;
    }

}
