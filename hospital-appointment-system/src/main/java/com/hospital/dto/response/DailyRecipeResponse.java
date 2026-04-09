package com.hospital.dto.response;

import com.hospital.entity.HerbalRecipe;
import lombok.Data;

import java.util.List;

/**
 * 今日食谱响应DTO
 *
 * @author Hospital Team
 * @since 2025-01-XX
 */
@Data
public class DailyRecipeResponse {

    /**
     * 早餐推荐药膳列表
     */
    private List<HerbalRecipe> breakfast;

    /**
     * 午餐推荐药膳列表
     */
    private List<HerbalRecipe> lunch;

    /**
     * 晚餐推荐药膳列表
     */
    private List<HerbalRecipe> dinner;
}















