package com.hospital.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hospital.annotation.OperationLog;
import com.hospital.common.result.Result;
import com.hospital.entity.User;
import com.hospital.service.PatientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 患者管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/patient")
public class PatientController {

    @Autowired
    private PatientService patientService;

    /**
     * 获取所有患者列表（管理员端使用）
     */
    @GetMapping("/list")
    public Result<IPage<User>> getPatientList(@RequestParam Map<String, Object> params) {
        // 由于管理员端已移除“患者管理”，这里禁用管理员患者列表接口。
        log.info("患者管理已关闭：GET /api/patient/list");
        return Result.error(404, "患者管理接口已关闭");
    }

    /**
     * 获取患者详情
     */
    @GetMapping("/{id}")
    public Result<User> getPatientById(@PathVariable Long id) {
        log.info("患者管理已关闭：GET /api/patient/{}", id);
        return Result.error(404, "患者管理接口已关闭");
    }

    /**
     * 添加患者
     */
    @OperationLog(module = "PATIENT", type = "INSERT", description = "添加患者")
    @PostMapping("/add")
    public Result<Boolean> addPatient(@RequestBody User patient) {
        log.info("患者管理已关闭：POST /api/patient/add");
        return Result.error(404, "患者管理接口已关闭");
    }

    /**
     * 更新患者信息
     */
    @OperationLog(module = "PATIENT", type = "UPDATE", description = "更新患者信息")
    @PutMapping("/update")
    public Result<Boolean> updatePatient(@RequestBody User patient) {
        log.info("患者管理已关闭：PUT /api/patient/update");
        return Result.error(404, "患者管理接口已关闭");
    }

    /**
     * 删除患者
     */
    @OperationLog(module = "PATIENT", type = "DELETE", description = "删除患者")
    @DeleteMapping("/{id}")
    public Result<Boolean> deletePatient(@PathVariable Long id) {
        log.info("患者管理已关闭：DELETE /api/patient/{}", id);
        return Result.error(404, "患者管理接口已关闭");
    }
}
