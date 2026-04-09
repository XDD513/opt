package com.hospital.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hospital.common.result.Result;
import com.hospital.entity.HerbalRecipe;
import com.hospital.entity.Ingredient;

import java.util.List;

/**
 * 药膳食谱服务接口
 *
 * @author TCM Health Team
 * @since 2025-11-03
 */
public interface HerbalRecipeService {

    /**
     * 根据用户体质推荐药膳（分页）
     *
     * @param userId 用户ID
     * @param season 季节（可选）
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 推荐药膳列表
     */
    Result<IPage<HerbalRecipe>> getRecommendedRecipes(Long userId, String season, Integer pageNum, Integer pageSize);

    /**
     * 获取全部药膳列表（分页，不包含AI推荐）
     *
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param userId 用户ID（可选，用于设置收藏状态）
     * @return 药膳列表
     */
    Result<IPage<HerbalRecipe>> getAllRecipes(Integer pageNum, Integer pageSize, Long userId);

    /**
     * 搜索药膳（分页）
     *
     * @param keyword 关键词
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param userId 用户ID（可选，用于设置收藏状态）
     * @return 药膳列表
     */
    Result<IPage<HerbalRecipe>> searchRecipes(String keyword,
                                              String season,
                                              String constitutionType,
                                              String effect,
                                              Integer pageNum,
                                              Integer pageSize,
                                              Long userId);

    /**
     * 获取药膳详情
     *
     * @param recipeId 食谱ID
     * @param userId 用户ID（可选，用于设置收藏状态）
     * @return 药膳详情
     */
    Result<HerbalRecipe> getRecipeDetail(Long recipeId, Long userId);

    /**
     * 获取热门药膳
     *
     * @param limit 数量限制
     * @param userId 用户ID（可选，用于设置收藏状态）
     * @return 热门药膳列表
     */
    Result<List<HerbalRecipe>> getPopularRecipes(Integer limit, Long userId);

    /**
     * 获取时令药膳
     *
     * @param season 季节
     * @param limit 数量限制
     * @param userId 用户ID（可选，用于设置收藏状态）
     * @return 时令药膳列表
     */
    Result<List<HerbalRecipe>> getSeasonalRecipes(String season, Integer limit, Long userId);

    /**
     * 收藏药膳
     *
     * @param userId 用户ID
     * @param recipeId 食谱ID
     * @param remark 备注
     * @return 操作结果
     */
    Result<Void> favoriteRecipe(Long userId, Long recipeId, String remark);

    /**
     * 取消收藏
     *
     * @param userId 用户ID
     * @param recipeId 食谱ID
     * @return 操作结果
     */
    Result<Void> unfavoriteRecipe(Long userId, Long recipeId);

    /**
     * 获取用户收藏的药膳列表（分页）
     *
     * @param userId 用户ID
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 收藏列表
     */
    Result<IPage<HerbalRecipe>> getUserFavorites(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 根据体质类型查询适用食材
     *
     * @param constitutionType 体质类型
     * @return 食材列表
     */
    Result<List<Ingredient>> getIngredientsByConstitution(String constitutionType);

    /**
     * 根据提示词生成并保存药膳
     *
     * @param prompt 提示词
     * @param userId 用户ID（可用于审计或个性化）
     * @return 新建的药膳
     */
    Result<HerbalRecipe> generateAndSaveRecipe(String prompt, Long userId);

    /**
     * 从 JSON 文本直接保存药膳（供异步生成完成后调用）
     * @param json AI返回的JSON文本
     * @param testId 体质测试ID（可选）
     * @param userId 用户ID
     */
    Result<HerbalRecipe> saveGeneratedRecipeJson(String json, Long testId, Long userId);

    /**
     * 将药膳建议文本模板化并入库
     * @param text 药膳建议纯文本
     * @param testId 体质测试ID（用于上下文，如体质/季节）
     * @param userId 用户ID
     */
    Result<HerbalRecipe> saveSuggestionText(String text, Long testId, Long userId);

    /**
     * 按体质测试ID获取本次生成的药膳
     * @param testId 测试ID
     */
    Result<List<HerbalRecipe>> getRecipesByTestId(Long testId);
}

