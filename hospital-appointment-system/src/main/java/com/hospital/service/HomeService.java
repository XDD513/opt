package com.hospital.service;

import com.hospital.dto.response.HomeRecommendationResponse;

/**
 * 首页推荐服务接口
 *
 * @author Hospital Team
 * @since 2025-01-XX
 */
public interface HomeService {

    /**
     * 获取首页推荐内容
     * 包括：推荐文章、推荐药膳
     *
     * @param userId 用户ID（可选，用于个性化推荐）
     * @param limit 每类内容的数量限制
     * @return 首页推荐数据
     */
    HomeRecommendationResponse getHomeRecommendations(Long userId, Integer limit);
}

