package com.hospital.service.impl;

import com.hospital.dto.response.DailyRecipeResponse;
import com.hospital.entity.HerbalRecipe;
import com.hospital.entity.UserConstitutionTest;
import com.hospital.mapper.HerbalRecipeMapper;
import com.hospital.mapper.UserConstitutionTestMapper;
import com.hospital.service.AiRecommendationService;
import com.hospital.service.DailyRecipeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 今日食谱服务实现
 *
 * @author Hospital Team
 * @since 2025-01-XX
 */
@Slf4j
@Service
public class DailyRecipeServiceImpl implements DailyRecipeService {

    @Autowired
    private HerbalRecipeMapper herbalRecipeMapper;

    @Autowired
    private UserConstitutionTestMapper userConstitutionTestMapper;

    @Autowired(required = false)
    private AiRecommendationService aiRecommendationService;

    @Override
    public DailyRecipeResponse getDailyRecipes(Long userId) {
        DailyRecipeResponse response = new DailyRecipeResponse();

        try {
            String constitutionType = null;
            if (userId != null) {
                UserConstitutionTest latestTest = userConstitutionTestMapper.selectLatestByUserId(userId);
                if (latestTest != null) {
                    constitutionType = latestTest.getPrimaryConstitution();
                }
            }

            List<HerbalRecipe> allRecipes;
            if (userId != null && aiRecommendationService != null && constitutionType != null) {
                allRecipes = aiRecommendationService.recommendPersonalized(userId, 10);
            } else {
                allRecipes = herbalRecipeMapper.selectPopularRecipes(10);
            }

            if (allRecipes == null || allRecipes.isEmpty()) {
                response.setBreakfast(new ArrayList<>());
                response.setLunch(new ArrayList<>());
                response.setDinner(new ArrayList<>());
                return response;
            }

            int total = allRecipes.size();
            int breakfastCount = Math.min(2, total);
            int lunchCount = Math.min(2, Math.max(0, total - breakfastCount));
            int dinnerCount = Math.min(2, Math.max(0, total - breakfastCount - lunchCount));

            response.setBreakfast(allRecipes.subList(0, breakfastCount));
            response.setLunch(allRecipes.subList(breakfastCount, breakfastCount + lunchCount));
            response.setDinner(allRecipes.subList(breakfastCount + lunchCount, breakfastCount + lunchCount + dinnerCount));

            log.info("获取今日食谱：用户ID={}，早餐{}个，午餐{}个，晚餐{}个", userId, breakfastCount, lunchCount, dinnerCount);
        } catch (Exception e) {
            log.error("获取今日食谱失败", e);
            response.setBreakfast(new ArrayList<>());
            response.setLunch(new ArrayList<>());
            response.setDinner(new ArrayList<>());
        }

        return response;
    }
}















