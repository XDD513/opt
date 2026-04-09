package com.hospital.service;

import com.hospital.common.result.Result;
import com.hospital.dto.request.SubmitTestRequest;
import com.hospital.dto.response.TestResultResponse;

import java.util.List;
import java.util.Map;

/**
 * 体质测试服务接口
 *
 * @author TCM Health Team
 * @since 2025-11-03
 */
public interface ConstitutionTestService {

    /**
     * 提交测试答案并计算结果
     *
     * @param userId 用户ID
     * @param request 测试答案
     * @return 测试结果
     */
    Result<TestResultResponse> submitTest(Long userId, SubmitTestRequest request);

    /**
     * 获取用户测试历史记录
     *
     * @param userId 用户ID
     * @return 测试历史列表
     */
    Result<List<TestResultResponse>> getTestHistory(Long userId);

    /**
     * 获取用户最新测试结果
     *
     * @param userId 用户ID
     * @return 最新测试结果
     */
    Result<TestResultResponse> getLatestTestResult(Long userId);

    /**
     * 根据测试ID获取测试报告
     *
     * @param testId 测试ID
     * @return 测试报告
     */
    Result<TestResultResponse> getTestReport(Long testId);

    /**
     * 分片生成 AI 养生建议
     */
    /**
     * @param phase analysis：仅辨证与调理建议（无计划）；plans：在已有分析基础上仅生成 plans 并合并入库
     */
    void generateAiSuggestionStream(Long testId, String phase, java.util.function.Consumer<String> contentConsumer);

    /**
     * 根据预约ID获取体质测试结果
     *
     * @param appointmentId 预约ID
     * @return 测试结果
     */
    Result<TestResultResponse> getTestResultByAppointment(Long appointmentId);

    /**
     * 检查预约是否已有体质测试记录
     *
     * @param appointmentId 预约ID
     * @return 是否存在测试记录
     */
    Result<Boolean> hasTestByAppointment(Long appointmentId);

    /**
     * 舌诊图片分析
     * @param file 舌头图片
     * @return 分析结果（包含特征和置信度）
     */
    Result<Map<String, Object>> tongueDiagnosis(org.springframework.web.multipart.MultipartFile file);
}

