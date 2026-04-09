package com.hospital.controller;

import com.hospital.annotation.OperationLog;
import com.hospital.common.result.Result;
import com.hospital.dto.response.DailyRecipeResponse;
import com.hospital.service.DailyRecipeService;
import com.hospital.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 今日食谱控制器
 *
 * @author Hospital Team
 * @since 2025-01-XX
 */
@Slf4j
@RestController
@RequestMapping("/api/daily-recipe")
public class DailyRecipeController {

    @Autowired
    private DailyRecipeService dailyRecipeService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 获取今日食谱推荐
     *
     * @param httpRequest HTTP请求
     * @return 今日食谱数据
     */
    @OperationLog(module = "DAILY", type = "SELECT", description = "查询今日食谱")
    @GetMapping("/today")
    public Result<DailyRecipeResponse> getDailyRecipes(HttpServletRequest httpRequest) {
        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        log.info("获取今日食谱：userId={}", userId);

        DailyRecipeResponse response = dailyRecipeService.getDailyRecipes(userId);
        return Result.success(response);
    }
}















