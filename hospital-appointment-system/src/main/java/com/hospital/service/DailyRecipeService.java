package com.hospital.service;

import com.hospital.dto.response.DailyRecipeResponse;

/**
 * 今日食谱服务接口
 *
 * @author Hospital Team
 * @since 2025-01-XX
 */
public interface DailyRecipeService {

    /**
     * 获取今日食谱推荐
     * 根据用户体质和当前时间推荐早中晚食谱
     *
     * @param userId 用户ID
     * @return 今日食谱数据
     */
    DailyRecipeResponse getDailyRecipes(Long userId);
}















