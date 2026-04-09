package com.hospital.controller;

import com.hospital.annotation.OperationLog;
import com.hospital.common.result.Result;
import com.hospital.entity.HerbalRecipe;
import com.hospital.service.AiRecommendationService;
import com.hospital.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * AI推荐控制器
 * 提供对话推荐、智能问答等功能
 *
 * @author Hospital Team
 * @since 2025-01-XX
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/recommendation")
public class AiRecommendationController {

    @Autowired
    private AiRecommendationService aiRecommendationService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 基于对话内容推荐药膳
     *
     * @param conversationContent 对话内容
     * @param httpRequest HTTP请求
     * @return 推荐的药膳列表
     */
    @OperationLog(module = "AI", type = "INSERT", description = "AI对话推荐药膳")
    @PostMapping("/conversation")
    public Result<List<HerbalRecipe>> recommendByConversation(
            @RequestParam("content") String conversationContent,
            HttpServletRequest httpRequest) {

        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        if (!StringUtils.hasText(conversationContent)) {
            return Result.error("对话内容不能为空");
        }

        log.info("基于对话推荐药膳：userId={}, contentLength={}", userId, conversationContent.length());
        List<HerbalRecipe> recipes = aiRecommendationService.recommendByConversation(conversationContent, userId);
        return Result.success(recipes);
    }

    /**
     * 智能问答
     *
     * @param question 用户问题
     * @param httpRequest HTTP请求
     * @return AI回答
     */
    @OperationLog(module = "AI", type = "INSERT", description = "AI智能问答")
    @PostMapping("/answer")
    public Result<String> answerQuestion(
            @RequestParam("question") String question,
            HttpServletRequest httpRequest) {

        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        if (!StringUtils.hasText(question)) {
            return Result.error("问题不能为空");
        }

        log.info("智能问答：userId={}, question={}", userId, question);
        String answer = aiRecommendationService.answerQuestion(question, userId);
        return Result.success(answer);
    }
}

