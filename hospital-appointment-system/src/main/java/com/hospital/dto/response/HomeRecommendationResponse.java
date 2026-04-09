package com.hospital.dto.response;

import com.hospital.entity.AcupointCombination;
import com.hospital.entity.HealthArticle;
import com.hospital.entity.HerbalRecipe;
import lombok.Data;

import java.util.List;

/**
 * 首页推荐响应DTO
 *
 * @author Hospital Team
 * @since 2025-01-XX
 */
@Data
public class HomeRecommendationResponse {

    /**
     * 推荐文章列表
     */
    private List<HealthArticle> articles;

    /**
     * 推荐药膳列表
     */
    private List<HerbalRecipe> recipes;

    /**
     * 推荐穴位组合列表
     */
    private List<AcupointCombination> acupoints;
}

