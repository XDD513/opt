package com.hospital.service.impl;

import com.hospital.dto.response.HomeRecommendationResponse;
import com.hospital.entity.AcupointCombination;
import com.hospital.entity.HealthArticle;
import com.hospital.entity.HerbalRecipe;
import com.hospital.entity.UserConstitutionTest;
import com.hospital.mapper.AcupointCombinationMapper;
import com.hospital.mapper.HealthArticleMapper;
import com.hospital.mapper.HerbalRecipeMapper;
import com.hospital.mapper.UserConstitutionTestMapper;
import com.hospital.service.AiRecommendationService;
import com.hospital.service.HomeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页推荐服务实现
 *
 * @author Hospital Team
 * @since 2025-01-XX
 */
@Slf4j
@Service
public class HomeServiceImpl implements HomeService {

    @Autowired
    private HealthArticleMapper healthArticleMapper;

    @Autowired
    private HerbalRecipeMapper herbalRecipeMapper;

    @Autowired
    private AcupointCombinationMapper acupointCombinationMapper;

    @Autowired
    private UserConstitutionTestMapper userConstitutionTestMapper;

    @Autowired(required = false)
    private AiRecommendationService aiRecommendationService;

    @Override
    public HomeRecommendationResponse getHomeRecommendations(Long userId, Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 1;
        }
        if (limit > 20) {
            limit = 20;
        }

        HomeRecommendationResponse response = new HomeRecommendationResponse();

        try {
            List<HealthArticle> articles = healthArticleMapper.selectRecommendedArticles(limit);
            if (articles != null && !articles.isEmpty()) {
                response.setArticles(articles.subList(0, Math.min(limit, articles.size())));
            } else {
                response.setArticles(new ArrayList<>());
            }
        } catch (Exception e) {
            log.error("获取推荐文章失败", e);
            response.setArticles(new ArrayList<>());
        }

        try {
            if (userId != null && aiRecommendationService != null) {
                List<HerbalRecipe> recipes = aiRecommendationService.recommendPersonalized(userId, limit);
                if (recipes != null && !recipes.isEmpty()) {
                    response.setRecipes(recipes.subList(0, Math.min(limit, recipes.size())));
                } else {
                    response.setRecipes(new ArrayList<>());
                }
            } else {
                List<HerbalRecipe> recipes = herbalRecipeMapper.selectPopularRecipes(limit);
                if (recipes != null && !recipes.isEmpty()) {
                    response.setRecipes(recipes.subList(0, Math.min(limit, recipes.size())));
                } else {
                    response.setRecipes(new ArrayList<>());
                }
            }
        } catch (Exception e) {
            log.error("获取推荐药膳失败", e);
            response.setRecipes(new ArrayList<>());
        }

        try {
            if (userId != null) {
                UserConstitutionTest latestTest = userConstitutionTestMapper.selectLatestByUserId(userId);
                if (latestTest != null && latestTest.getPrimaryConstitution() != null) {
                    String constitutionType = latestTest.getPrimaryConstitution();
                    List<AcupointCombination> acupoints = acupointCombinationMapper.selectRecommendedCombinations(constitutionType, limit);
                    if (acupoints != null && !acupoints.isEmpty()) {
                        response.setAcupoints(acupoints.subList(0, Math.min(limit, acupoints.size())));
                    } else {
                        response.setAcupoints(new ArrayList<>());
                    }
                } else {
                    response.setAcupoints(new ArrayList<>());
                }
            } else {
                response.setAcupoints(new ArrayList<>());
            }
        } catch (Exception e) {
            log.error("获取推荐穴位组合失败", e);
            response.setAcupoints(new ArrayList<>());
        }

        return response;
    }
}

