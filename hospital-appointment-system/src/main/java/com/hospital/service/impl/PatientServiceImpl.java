package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hospital.common.constant.SystemConstants;
import com.hospital.entity.User;
import com.hospital.mapper.PatientMapper;
import com.hospital.service.PatientService;
import com.hospital.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 患者管理服务实现类
 */
@Slf4j
@Service
public class PatientServiceImpl extends ServiceImpl<PatientMapper, User> implements PatientService {

    @Autowired
    private PatientMapper patientMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Override
    public IPage<User> getPatientList(Map<String, Object> params) {
        log.info("获取患者列表，参数：{}", params);

        Integer page = (Integer) params.get("page");
        Integer pageSize = (Integer) params.get("pageSize");

        Page<User> pageObject = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : SystemConstants.DEFAULT_PAGE_SIZE);
        return patientMapper.selectPatientList(pageObject, params);
    }

    @Override
    public boolean addPatient(User patient) {
        log.info("添加患者，用户名：{}", patient.getUsername());

        // 设置角色为患者
        patient.setRoleType(1);
        patient.setStatus(1);

        return save(patient);
    }

    @Override
    public boolean updatePatient(User patient) {
        log.info("更新患者信息，患者ID：{}", patient.getId());
        return updateById(patient);
    }

    @Override
    public boolean deletePatient(Long id) {
        log.info("删除患者，患者 ID：{}", id);
    
        // 直接删除患者
        return removeById(id);
    }
}
