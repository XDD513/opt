package com.hospital.controller;

import com.hospital.annotation.OperationLog;
import com.hospital.common.result.Result;
import com.hospital.dto.request.SubmitTestRequest;
import com.hospital.dto.response.ConstitutionTypeResponse;
import com.hospital.dto.response.TestResultResponse;
import com.hospital.service.ConstitutionService;
import com.hospital.service.ConstitutionTestService;
import com.hospital.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 中医体质测试控制器
 *
 * @author TCM Health Team
 * @since 2025-11-03
 */
@Slf4j
@RestController
@RequestMapping("/api/constitution")
public class ConstitutionController {

    @Autowired
    private ConstitutionService constitutionService;

    @Autowired
    private ConstitutionTestService constitutionTestService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 获取所有体质类型列表
     */
    @GetMapping("/types")
    public Result<List<ConstitutionTypeResponse>> getConstitutionTypes() {
        log.info("获取体质类型列表");
        return constitutionService.getConstitutionTypes();
    }

    /**
     * 根据体质代码获取体质详情
     */
    @GetMapping("/type/{code}")
    public Result<ConstitutionTypeResponse> getConstitutionDetail(@PathVariable("code") String code) {
        log.info("获取体质详情: {}", code);
        return constitutionService.getConstitutionDetail(code);
    }

    /**
     * 提交测试答案
     */
    @OperationLog(module = "CONSTITUTION", type = "INSERT", description = "提交体质测试")
    @PostMapping("/test/submit")
    public Result<TestResultResponse> submitTest(@Validated @RequestBody SubmitTestRequest request,
                                                   HttpServletRequest httpRequest) {
        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        log.info("用户{}提交体质测试", userId);
        return constitutionTestService.submitTest(userId, request);
    }

    /**
     * 获取用户测试历史记录
     */
    @OperationLog(module = "CONSTITUTION", type = "SELECT", description = "查询体质测试历史")
    @GetMapping("/test/history")
    public Result<List<TestResultResponse>> getTestHistory(HttpServletRequest httpRequest) {
        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        log.info("获取用户{}的测试历史", userId);
        return constitutionTestService.getTestHistory(userId);
    }

    /**
     * 获取用户最新测试结果
     */
    @OperationLog(module = "CONSTITUTION", type = "SELECT", description = "查询最新体质测试结果")
    @GetMapping("/test/latest")
    public Result<TestResultResponse> getLatestTestResult(HttpServletRequest httpRequest) {
        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        log.info("获取用户{}的最新测试结果", userId);
        return constitutionTestService.getLatestTestResult(userId);
    }

    /**
     * 根据测试ID获取测试报告
     */
    @OperationLog(module = "CONSTITUTION", type = "SELECT", description = "查询体质测试报告")
    @GetMapping("/test/report/{id}")
    public Result<TestResultResponse> getTestReport(@PathVariable("id") Long id) {
        log.info("获取测试报告: {}", id);
        return constitutionTestService.getTestReport(id);
    }

    @GetMapping(value = "/test/ai-suggestion/stream/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAiSuggestion(@PathVariable("id") Long id,
                                         @RequestParam(value = "phase", defaultValue = "analysis") String phase) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            constitutionTestService.generateAiSuggestionStream(id, phase, chunk -> {
                try {
                    emitter.send(SseEmitter.event().data(chunk));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            emitter.send(SseEmitter.event().name("finish").data("[DONE]"));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /**
     * 根据用户ID获取最新测试结果（医生端使用）
     */
    @GetMapping("/test/user/{userId}/latest")
    public Result<TestResultResponse> getUserLatestTestResult(@PathVariable("userId") Long userId) {
        log.info("获取用户{}的最新测试结果（医生端查询）", userId);
        return constitutionTestService.getLatestTestResult(userId);
    }

    /**
     * 根据用户ID获取测试历史记录（医生端使用）
     */
    @GetMapping("/test/user/{userId}/history")
    public Result<List<TestResultResponse>> getUserTestHistory(@PathVariable("userId") Long userId) {
        log.info("获取用户{}的测试历史（医生端查询）", userId);
        return constitutionTestService.getTestHistory(userId);
    }

    /**
     * 根据预约ID获取体质测试结果（医生端使用）
     */
    @GetMapping("/test/appointment/{appointmentId}")
    public Result<TestResultResponse> getTestResultByAppointment(@PathVariable("appointmentId") Long appointmentId) {
        log.info("获取预约{}关联的体质测试结果", appointmentId);
        return constitutionTestService.getTestResultByAppointment(appointmentId);
    }

    /**
     * 检查预约是否已有体质测试记录
     */
    @GetMapping("/test/appointment/{appointmentId}/exists")
    public Result<Boolean> checkTestByAppointment(@PathVariable("appointmentId") Long appointmentId) {
        log.info("检查预约{}是否已有测试记录", appointmentId);
        return constitutionTestService.hasTestByAppointment(appointmentId);
    }

    /**
     * 舌诊分析
     */
    @OperationLog(module = "CONSTITUTION", type = "SELECT", description = "舌诊分析")
    @PostMapping("/tongue-diagnosis")
    public Result<Map<String, Object>> tongueDiagnosis(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        log.info("开始舌诊分析");
        return constitutionTestService.tongueDiagnosis(file);
    }
}

