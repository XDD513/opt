package com.hospital.controller;

import com.hospital.annotation.OperationLog;
import com.hospital.common.result.Result;
import com.hospital.dto.response.HomeRecommendationResponse;
import com.hospital.service.HomeService;
import com.hospital.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 首页控制器
 *
 * @author Hospital Team
 * @since 2025-01-XX
 */
@Slf4j
@RestController
@RequestMapping("/api/home")
public class HomeController {

    @Autowired
    private HomeService homeService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 获取首页推荐内容
     *
     * @param limit 每类内容的数量限制（默认3）
     * @param httpRequest HTTP请求
     * @return 首页推荐数据
     */
    @OperationLog(module = "HOME", type = "SELECT", description = "查询首页推荐")
    @GetMapping("/recommendations")
    public Result<HomeRecommendationResponse> getHomeRecommendations(
            @RequestParam(value = "limit", defaultValue = "3") Integer limit,
            HttpServletRequest httpRequest) {

        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        log.info("获取首页推荐：userId={}, limit={}", userId, limit);

        HomeRecommendationResponse response = homeService.getHomeRecommendations(userId, limit);
        return Result.success(response);
    }
}

