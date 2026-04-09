package com.hospital.service;

import com.hospital.dto.StatisticsDTO;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 统计数据服务接口
 */
public interface StatisticsService {
    
    /**
     * 获取管理员统计数据
     */
    StatisticsDTO.AdminStats getAdminStats();
    
    /**
     * 获取患者统计数据
     * @param patientId 患者ID
     */
    StatisticsDTO.PatientStats getPatientStats(Long patientId);
}
