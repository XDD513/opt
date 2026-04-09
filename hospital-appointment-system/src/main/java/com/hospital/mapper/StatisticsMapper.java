package com.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.dto.StatisticsDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 统计数据Mapper接口
 */
@Mapper
public interface StatisticsMapper extends BaseMapper<Object> {
    
    /**
     * 获取管理员统计数据
     */
    StatisticsDTO.AdminStats getAdminStats();
    
    /**
     * 获取患者统计数据
     * @param patientId 患者ID
     */
    StatisticsDTO.PatientStats getPatientStats(@Param("patientId") Long patientId);
}
