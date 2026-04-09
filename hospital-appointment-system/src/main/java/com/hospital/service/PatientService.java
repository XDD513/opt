package com.hospital.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hospital.entity.User;
import java.util.List;
import java.util.Map;

/**
 * 患者管理服务接口
 */
public interface PatientService extends IService<User> {
    
    /**
     * 获取所有患者列表（管理员端使用）
     * @param params 查询参数
     * @return 分页结果
     */
    IPage<User> getPatientList(Map<String, Object> params);
    
    /**
     * 添加患者
     * @param patient 患者信息
     * @return 是否成功
     */
    boolean addPatient(User patient);
    
    /**
     * 更新患者信息
     * @param patient 患者信息
     * @return 是否成功
     */
    boolean updatePatient(User patient);
    
    /**
     * 删除患者
     * @param id 患者ID
     * @return 是否成功
     */
    boolean deletePatient(Long id);
}
