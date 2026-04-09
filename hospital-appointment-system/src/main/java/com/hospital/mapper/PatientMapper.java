package com.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Map;

/**
 * 患者Mapper接口
 */
@Mapper
public interface PatientMapper extends BaseMapper<User> {
    
    /**
     * 获取所有患者列表（管理员端使用）
     * @param page 分页对象
     * @param params 查询参数
     */
    IPage<User> selectPatientList(Page<User> page, @Param("params") Map<String, Object> params);
}
