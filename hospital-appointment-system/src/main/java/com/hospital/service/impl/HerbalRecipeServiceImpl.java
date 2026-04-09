package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.common.result.Result;
import com.hospital.common.result.ResultCode;
import com.hospital.entity.HerbalRecipe;
import com.hospital.entity.Ingredient;
import com.hospital.entity.UserConstitutionTest;
import com.hospital.entity.UserFavorite;
import com.hospital.mapper.HerbalRecipeMapper;
import com.hospital.mapper.IngredientMapper;
import com.hospital.mapper.UserConstitutionTestMapper;
import com.hospital.mapper.UserFavoriteMapper;
import com.hospital.service.AiRecommendationService;
import com.hospital.service.HerbalRecipeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 药膳食谱服务实现类
 *
 * @author TCM Health Team
 * @since 2025-11-03
 */
@Slf4j
@Service
public class HerbalRecipeServiceImpl implements HerbalRecipeService {

    @Autowired
    private HerbalRecipeMapper herbalRecipeMapper;

    @Autowired
    private IngredientMapper ingredientMapper;

    @Autowired
    private UserFavoriteMapper userFavoriteMapper;

    @Autowired
    private UserConstitutionTestMapper constitutionTestMapper;

    @Autowired
    private com.hospital.util.RedisUtil redisUtil;

    @Autowired(required = false)
    private AiRecommendationService aiRecommendationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 根据用户体质推荐药膳（分页）
     */
    @Override
    public Result<IPage<HerbalRecipe>> getRecommendedRecipes(Long userId, String season, Integer pageNum, Integer pageSize) {
        try {
            // 1. 获取用户最新体质测试结果
            UserConstitutionTest latestTest = constitutionTestMapper.selectLatestByUserId(userId);
            if (latestTest == null) {
                log.warn("用户{}尚未进行体质测试，无法推荐药膳", userId);
                return Result.error(ResultCode.DATA_NOT_FOUND.getCode(), "请先完成体质测试");
            }

            String constitutionType = latestTest.getPrimaryConstitution();

            // 2. 只缓存前3页（不包含用户特定的收藏状态），使用参数哈希简化层级
            Map<String, Object> filterParams = new java.util.HashMap<>();
            filterParams.put("type", constitutionType);
            if (season != null && !"all".equals(season)) {
                filterParams.put("season", season);
            }
            String cacheKey = redisUtil.buildCacheKey("hospital:common:recipe:recommend", pageNum, pageSize, filterParams);

            if (pageNum <= 3) {
                Object cached = redisUtil.get(cacheKey);
                if (cached instanceof IPage) {
                    try {
                        @SuppressWarnings("unchecked")
                        IPage<HerbalRecipe> cachedPage = (IPage<HerbalRecipe>) cached;
                        // 设置用户特定的收藏状态和AI推荐理由
                        // 性能优化：只对前3个药膳生成AI推荐理由
                        int aiRecommendationLimit = 3;
                        int index = 0;

                        for (HerbalRecipe recipe : cachedPage.getRecords()) {
                            UserFavorite favorite = userFavoriteMapper.selectByUserAndTarget(userId, "RECIPE", recipe.getId());
                            recipe.setIsFavorited(favorite != null);

                            // AI增强：只对前几个药膳生成个性化推荐理由
                            if (aiRecommendationService != null && index < aiRecommendationLimit) {
                                try {
                                    String recommendationReason = aiRecommendationService.generateRecommendationReason(recipe, latestTest);
                                    recipe.setRecommendationReason(recommendationReason);
                                } catch (Exception e) {
                                    log.warn("生成推荐理由失败，使用降级方案：recipeId={}", recipe.getId(), e);
                                    // 降级：使用默认推荐理由
                                    recipe.setRecommendationReason(buildDefaultRecommendationReason(recipe, latestTest));
                                }
                            } else {
                                // 其他药膳使用默认推荐理由
                                recipe.setRecommendationReason(buildDefaultRecommendationReason(recipe, latestTest));
                            }
                            index++;
                        }
                        return Result.success(cachedPage);
                    } catch (ClassCastException ignored) {}
                }
            }

            // 3. 分页查询推荐药膳
            Page<HerbalRecipe> page = new Page<>(pageNum, pageSize);
            IPage<HerbalRecipe> result = herbalRecipeMapper.selectRecommendedRecipes(page, constitutionType, season);

            // 4. 设置每个药膳的收藏状态和AI推荐理由
            // 性能优化：只对前3个药膳生成AI推荐理由，其他使用默认理由，避免响应时间过长
            int aiRecommendationLimit = 3;
            int index = 0;

            for (HerbalRecipe recipe : result.getRecords()) {
                UserFavorite favorite = userFavoriteMapper.selectByUserAndTarget(userId, "RECIPE", recipe.getId());
                recipe.setIsFavorited(favorite != null);

                // AI增强：只对前几个药膳生成个性化推荐理由
                if (aiRecommendationService != null && index < aiRecommendationLimit) {
                    try {
                        String recommendationReason = aiRecommendationService.generateRecommendationReason(recipe, latestTest);
                        recipe.setRecommendationReason(recommendationReason);
                    } catch (Exception e) {
                        log.warn("生成推荐理由失败，使用降级方案：recipeId={}", recipe.getId(), e);
                        // 降级：使用默认推荐理由
                        recipe.setRecommendationReason(buildDefaultRecommendationReason(recipe, latestTest));
                    }
                } else {
                    // 其他药膳使用默认推荐理由
                    recipe.setRecommendationReason(buildDefaultRecommendationReason(recipe, latestTest));
                }
                index++;
            }

            // 5. 缓存前3页（15分钟）
            if (pageNum <= 3) {
                redisUtil.set(cacheKey, result, 15, java.util.concurrent.TimeUnit.MINUTES);
            }

            log.info("为用户{}推荐药膳，体质：{}，季节：{}，共{}条", userId, constitutionType, season, result.getTotal());
            return Result.success(result);

        } catch (Exception e) {
            log.error("推荐药膳失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<HerbalRecipe> saveSuggestionText(String text, Long testId, Long userId) {
        try {
            if (text == null || text.trim().isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR.getCode(), "内容为空");
            }
            // 基于测试记录补充上下文（体质/季节）
            UserConstitutionTest latest = null;
            if (testId != null) {
                latest = constitutionTestMapper.selectById(testId);
            } else if (userId != null) {
                latest = constitutionTestMapper.selectLatestByUserId(userId);
            }
            String constitutionType = latest != null ? latest.getPrimaryConstitution() : "ALL";
            String season = resolveSeason(java.time.LocalDate.now());

            HerbalRecipe recipe = buildFromSuggestionText(text, latest, constitutionType, season);
            // 仅“一键保存药膳”路径：将本次药膳与体质测试绑定
            if (testId != null) {
                recipe.setTestId(testId);
            } else if (latest != null) {
                recipe.setTestId(latest.getId());
            }
            recipe.setStatus(1);
            recipe.setCreatedAt(java.time.LocalDateTime.now());
            recipe.setUpdatedAt(java.time.LocalDateTime.now());
            herbalRecipeMapper.insert(recipe);
            // 保存成功后，若指定了用户，则同时收藏该药膳（幂等）
            if (userId != null && recipe.getId() != null) {
                try {
                    UserFavorite existing = userFavoriteMapper.selectByUserAndTarget(userId, "RECIPE", recipe.getId());
                    if (existing == null) {
                        UserFavorite favorite = new UserFavorite();
                        favorite.setUserId(userId);
                        favorite.setTargetType("RECIPE");
                        favorite.setTargetId(recipe.getId());
                        favorite.setRemark("一键保存自动收藏");
                        userFavoriteMapper.insert(favorite);
                        herbalRecipeMapper.incrementFavoriteCount(recipe.getId());
                    }
                    recipe.setIsFavorited(true);
                } catch (Exception exFav) {
                    log.warn("保存后自动收藏失败（忽略，不影响主流程）：userId={}, recipeId={}, err={}",
                            userId, recipe.getId(), exFav.getMessage());
                }
            }
            return Result.success(recipe);
        } catch (Exception e) {
            log.error("保存药膳建议失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 将“药膳建议”纯文本模板化为 HerbalRecipe（启发式，稳定可入库）
     */
    private HerbalRecipe buildFromSuggestionText(String text,
                                                 UserConstitutionTest test,
                                                 String constitutionType,
                                                 String season) {
        HerbalRecipe r = new HerbalRecipe();
        // 1) 菜名：优先从文本第一行或【】内提取，否则用体质+季节命名
        String title = null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("【?([\\u4e00-\\u9fa5A-Za-z0-9_\\-]{2,20})】?").matcher(text);
        if (m.find()) title = m.group(1);
        if (title == null) {
            String ct = constitutionType != null ? constitutionType : "ALL";
            title = (ct + "体质·时令药膳");
        }
        r.setRecipeName(title);
        r.setConstitutionType(constitutionType != null ? constitutionType : "ALL");
        r.setSeason(season != null ? season : "ALL");
        r.setCategory("汤品");
        r.setDifficulty(2);
        r.setCookingTime(30);
        r.setServings(2);
        // 2) 简要食材：启发式抽取常见词；若无则给默认模板
        java.util.List<java.util.Map<String, Object>> ings = new java.util.ArrayList<>();
        String[] candidates = {"山药","枸杞","桂圆","红枣","当归","生姜","乌鸡","莲子","芡实"};
        int picked = 0;
        for (String c : candidates) {
            if (text.contains(c)) {
                java.util.Map<String,Object> it = new java.util.HashMap<>();
                it.put("name", c);
                it.put("amount", 20);
                it.put("unit", "g");
                ings.add(it);
                if (++picked >= 3) break;
            }
        }
        if (ings.isEmpty()) {
            ings.add(java.util.Map.of("name","山药","amount",100,"unit","g"));
            ings.add(java.util.Map.of("name","枸杞","amount",20,"unit","g"));
        }
        try { r.setIngredients(objectMapper.writeValueAsString(ings)); } catch (Exception ignored) {}
        // 3) 步骤：固定两步模板
        java.util.List<String> steps = java.util.List.of("准备食材，清洗切配；",
                "加水炖煮30-60分钟，调味后食用。");
        try { r.setSteps(objectMapper.writeValueAsString(steps)); } catch (Exception ignored) {}
        // 4) 从文本提取功效/禁忌关键句
        String efficacy = extractBetween(text, "功效", 120);
        String contraindications = extractBetween(text, "禁忌", 120);
        r.setEfficacy(efficacy != null ? efficacy : "调理体质，扶正固本。");
        r.setSuitableSymptoms(""); // 可后续增强
        r.setContraindications(contraindications != null ? contraindications : "对相关食材过敏者慎用。");
        r.setNutritionInfo("{}");
        r.setTips("");
        r.setImage("");
        r.setVideoUrl("");
        return r;
    }

    private String extractBetween(String text, String key, int maxLen) {
        int i = text.indexOf(key);
        if (i < 0) return null;
        String tail = text.substring(i);
        String[] breakers = {"；","。","\n","【","饮食","起居","穴位","健康计划"};
        int cut = tail.length();
        for (String b : breakers) {
            int p = tail.indexOf(b);
            if (p > 0) cut = Math.min(cut, p);
        }
        String s = tail.substring(0, Math.min(cut, maxLen)).replaceAll("^[：:】\\s]+","").trim();
        return s;
    }
    /**
     * 获取全部药膳列表（分页，不包含AI推荐）
     */
    @Override
    public Result<IPage<HerbalRecipe>> getAllRecipes(Integer pageNum, Integer pageSize, Long userId) {
        try {
            // 缓存前3页
            Map<String, Object> filterParams = new HashMap<>();
            String cacheKey = redisUtil.buildCacheKey("hospital:common:recipe:list", pageNum, pageSize, filterParams);

            if (pageNum <= 3) {
                Object cached = redisUtil.get(cacheKey);
                if (cached instanceof IPage) {
                    try {
                        @SuppressWarnings("unchecked")
                        IPage<HerbalRecipe> cachedPage = (IPage<HerbalRecipe>) cached;
                        // 设置用户特定的收藏状态
                        if (userId != null) {
                            for (HerbalRecipe recipe : cachedPage.getRecords()) {
                                UserFavorite favorite = userFavoriteMapper.selectByUserAndTarget(userId, "RECIPE", recipe.getId());
                                recipe.setIsFavorited(favorite != null);
                            }
                        }
                        return Result.success(cachedPage);
                    } catch (ClassCastException ignored) {}
                }
            }

            Page<HerbalRecipe> page = new Page<>(pageNum, pageSize);
            IPage<HerbalRecipe> result = herbalRecipeMapper.selectAllRecipes(page);

            // 如果用户已登录，设置每个药膳的收藏状态
            if (userId != null) {
                for (HerbalRecipe recipe : result.getRecords()) {
                    UserFavorite favorite = userFavoriteMapper.selectByUserAndTarget(userId, "RECIPE", recipe.getId());
                    recipe.setIsFavorited(favorite != null);
                }
            }

            // 缓存前3页（15分钟）
            if (pageNum <= 3) {
                redisUtil.set(cacheKey, result, 15, java.util.concurrent.TimeUnit.MINUTES);
            }

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取全部药膳列表失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 搜索药膳（分页）
     */
    @Override
    public Result<IPage<HerbalRecipe>> searchRecipes(String keyword,
                                                     String season,
                                                     String constitutionType,
                                                     String effect,
                                                     Integer pageNum,
                                                     Integer pageSize,
                                                     Long userId) {
        try {
            // 只缓存前3页，关键词作为特殊参数处理（保留可读性）
            Map<String, Object> filterParams = new java.util.HashMap<>();
            if (StringUtils.hasText(keyword)) {
                filterParams.put("keyword", keyword);
            }
            if (StringUtils.hasText(season)) {
                filterParams.put("season", season);
            }
            if (StringUtils.hasText(constitutionType)) {
                filterParams.put("constitutionType", constitutionType);
            }
            if (StringUtils.hasText(effect)) {
                filterParams.put("effect", effect);
            }
            String cacheKey = redisUtil.buildCacheKey("hospital:common:recipe:search", pageNum, pageSize, filterParams);

            if (pageNum <= 3) {
                Object cached = redisUtil.get(cacheKey);
                if (cached instanceof IPage) {
                    try {
                        @SuppressWarnings("unchecked")
                        IPage<HerbalRecipe> cachedPage = (IPage<HerbalRecipe>) cached;
                        // 设置用户特定的收藏状态
                        if (userId != null) {
                            for (HerbalRecipe recipe : cachedPage.getRecords()) {
                                UserFavorite favorite = userFavoriteMapper.selectByUserAndTarget(userId, "RECIPE", recipe.getId());
                                recipe.setIsFavorited(favorite != null);
                            }
                        }
                        return Result.success(cachedPage);
                    } catch (ClassCastException ignored) {}
                }
            }

            Page<HerbalRecipe> page = new Page<>(pageNum, pageSize);
            IPage<HerbalRecipe> result = herbalRecipeMapper.searchRecipes(
                    page,
                    keyword,
                    season,
                    constitutionType,
                    effect
            );

            // 如果用户已登录，设置每个药膳的收藏状态
            if (userId != null) {
                for (HerbalRecipe recipe : result.getRecords()) {
                    UserFavorite favorite = userFavoriteMapper.selectByUserAndTarget(userId, "RECIPE", recipe.getId());
                    recipe.setIsFavorited(favorite != null);
                }
            }

            // 缓存前3页（15分钟）
            if (pageNum <= 3) {
                redisUtil.set(cacheKey, result, 15, java.util.concurrent.TimeUnit.MINUTES);
            }

            log.info("搜索药膳：keyword={}, season={}, constitutionType={}, effect={}, total={}",
                    keyword, season, constitutionType, effect, result.getTotal());
            return Result.success(result);

        } catch (Exception e) {
            log.error("搜索药膳失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 获取药膳详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<HerbalRecipe> getRecipeDetail(Long recipeId, Long userId) {
        try {
            String cacheKey = "hospital:common:recipe:detail:id:" + recipeId;

            // 尝试从缓存获取
            Object cached = redisUtil.get(cacheKey);
            HerbalRecipe recipe = null;
            if (cached instanceof HerbalRecipe) {
                recipe = (HerbalRecipe) cached;
            } else {
                recipe = herbalRecipeMapper.selectById(recipeId);
                if (recipe == null) {
                    log.warn("药膳不存在: {}", recipeId);
                    return Result.error(ResultCode.DATA_NOT_FOUND);
                }
                // 存入缓存（30分钟）
                redisUtil.set(cacheKey, recipe, 30, java.util.concurrent.TimeUnit.MINUTES);
            }

            // 增加浏览次数（无论是否从缓存获取）
            herbalRecipeMapper.incrementViewCount(recipeId);
            // 更新缓存中的浏览次数和更新时间
            recipe.setViewCount(recipe.getViewCount() + 1);
            recipe.setUpdatedAt(LocalDateTime.now());
            redisUtil.set(cacheKey, recipe, 30, java.util.concurrent.TimeUnit.MINUTES);

            // 如果用户已登录，设置收藏状态
            if (userId != null) {
                UserFavorite favorite = userFavoriteMapper.selectByUserAndTarget(userId, "RECIPE", recipeId);
                recipe.setIsFavorited(favorite != null);
            }

            // 补全食材备注信息
            enrichIngredientsWithNotes(recipe);

            return Result.success(recipe);

        } catch (Exception e) {
            log.error("获取药膳详情失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 获取热门药膳
     */
    @Override
    public Result<List<HerbalRecipe>> getPopularRecipes(Integer limit, Long userId) {
        try {
            String cacheKey = "recipe:popular:limit:" + limit;

            // 尝试从缓存获取
            Object cached = redisUtil.get(cacheKey);
            List<HerbalRecipe> recipes = null;
            if (cached instanceof List) {
                try {
                    @SuppressWarnings("unchecked")
                    List<HerbalRecipe> list = (List<HerbalRecipe>) cached;
                    recipes = list;
                } catch (ClassCastException ignored) {}
            }

            if (recipes == null) {
                recipes = herbalRecipeMapper.selectPopularRecipes(limit);
                // 存入缓存（30分钟）
                redisUtil.set(cacheKey, recipes, 30, java.util.concurrent.TimeUnit.MINUTES);
            }

            // 如果用户已登录，设置每个药膳的收藏状态
            if (userId != null) {
                for (HerbalRecipe recipe : recipes) {
                    UserFavorite favorite = userFavoriteMapper.selectByUserAndTarget(userId, "RECIPE", recipe.getId());
                    recipe.setIsFavorited(favorite != null);
                }
            }

            return Result.success(recipes);

        } catch (Exception e) {
            log.error("获取热门药膳失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 获取时令药膳
     */
    @Override
    public Result<List<HerbalRecipe>> getSeasonalRecipes(String season, Integer limit, Long userId) {
        try {
            String cacheKey = "recipe:seasonal:season:" + season + ":limit:" + limit;

            // 尝试从缓存获取
            Object cached = redisUtil.get(cacheKey);
            List<HerbalRecipe> recipes = null;
            if (cached instanceof List) {
                try {
                    @SuppressWarnings("unchecked")
                    List<HerbalRecipe> list = (List<HerbalRecipe>) cached;
                    recipes = list;
                } catch (ClassCastException ignored) {}
            }

            if (recipes == null) {
                recipes = herbalRecipeMapper.selectSeasonalRecipes(season, limit);
                // 存入缓存（30分钟）
                redisUtil.set(cacheKey, recipes, 30, java.util.concurrent.TimeUnit.MINUTES);
            }

            // 如果用户已登录，设置每个药膳的收藏状态
            if (userId != null) {
                for (HerbalRecipe recipe : recipes) {
                    UserFavorite favorite = userFavoriteMapper.selectByUserAndTarget(userId, "RECIPE", recipe.getId());
                    recipe.setIsFavorited(favorite != null);
                }
            }

            return Result.success(recipes);

        } catch (Exception e) {
            log.error("获取时令药膳失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 收藏药膳
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> favoriteRecipe(Long userId, Long recipeId, String remark) {
        try {
            // 1. 检查药膳是否存在
            HerbalRecipe recipe = herbalRecipeMapper.selectById(recipeId);
            if (recipe == null) {
                log.warn("药膳不存在: {}", recipeId);
                return Result.error(ResultCode.DATA_NOT_FOUND);
            }

            // 2. 检查是否已收藏
            UserFavorite existing = userFavoriteMapper.selectByUserAndTarget(userId, "RECIPE", recipeId);
            if (existing != null) {
                log.warn("用户{}已收藏药膳{}", userId, recipeId);
                return Result.error(ResultCode.PARAM_ERROR.getCode(), "您已收藏过该药膳");
            }

            // 3. 创建收藏记录
            UserFavorite favorite = new UserFavorite();
            favorite.setUserId(userId);
            favorite.setTargetType("RECIPE");
            favorite.setTargetId(recipeId);
            favorite.setRemark(remark);
            userFavoriteMapper.insert(favorite);

            // 4. 增加收藏次数
            herbalRecipeMapper.incrementFavoriteCount(recipeId);

            log.info("用户{}收藏药膳{}", userId, recipeId);
            return Result.success();

        } catch (Exception e) {
            log.error("收藏药膳失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 取消收藏
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> unfavoriteRecipe(Long userId, Long recipeId) {
        try {
            // 1. 检查收藏记录是否存在
            UserFavorite favorite = userFavoriteMapper.selectByUserAndTarget(userId, "RECIPE", recipeId);
            if (favorite == null) {
                log.warn("用户{}未收藏药膳{}", userId, recipeId);
                return Result.error(ResultCode.DATA_NOT_FOUND.getCode(), "您未收藏该药膳");
            }

            // 2. 删除收藏记录
            userFavoriteMapper.deleteByUserAndTarget(userId, "RECIPE", recipeId);

            // 3. 减少收藏次数
            herbalRecipeMapper.decrementFavoriteCount(recipeId);

            log.info("用户{}取消收藏药膳{}", userId, recipeId);
            return Result.success();

        } catch (Exception e) {
            log.error("取消收藏失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 获取用户收藏的药膳列表（分页）
     */
    @Override
    public Result<IPage<HerbalRecipe>> getUserFavorites(Long userId, Integer pageNum, Integer pageSize) {
        try {
            Page<UserFavorite> page = new Page<>(pageNum, pageSize);
            IPage<UserFavorite> favorites = userFavoriteMapper.selectRecipeFavoritesByUserId(page, userId);

            // 转换为HerbalRecipe列表
            Page<HerbalRecipe> recipePage = new Page<>(favorites.getCurrent(), favorites.getSize());
            recipePage.setTotal(favorites.getTotal());
            recipePage.setPages(favorites.getPages());
            
            List<HerbalRecipe> recipes = new java.util.ArrayList<>();
            for (UserFavorite favorite : favorites.getRecords()) {
                if (favorite.getTargetId() != null) {
                    HerbalRecipe recipe = herbalRecipeMapper.selectById(favorite.getTargetId());
                    if (recipe != null && recipe.getStatus() == 1) {
                        recipes.add(recipe);
                    }
                }
            }
            recipePage.setRecords(recipes);
            
            return Result.success(recipePage);

        } catch (Exception e) {
            log.error("获取收藏列表失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 根据体质类型查询适用食材
     */
    @Override
    public Result<List<Ingredient>> getIngredientsByConstitution(String constitutionType) {
        try {
            List<Ingredient> ingredients = ingredientMapper.selectByConstitutionType(constitutionType);
            return Result.success(ingredients);

        } catch (Exception e) {
            log.error("获取适用食材失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 根据提示词生成并保存药膳
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<HerbalRecipe> generateAndSaveRecipe(String prompt, Long userId) {
        try {
            if (aiRecommendationService == null) {
                return Result.error(ResultCode.SYSTEM_ERROR.getCode(), "AI服务未启用");
            }
            // 0) 先尝试基于体质、季节 + 舌诊/深度分析结果从库中直接推荐，避免重复生成
            UserConstitutionTest latestTest = constitutionTestMapper.selectLatestByUserId(userId);
            String constitutionType = null;
            String season = resolveSeason(java.time.LocalDate.now());
            String effectKeywords = null;
            if (latestTest != null) {
                constitutionType = latestTest.getPrimaryConstitution();
                effectKeywords = extractEffectKeywordsFromTest(latestTest);
            }
            // 优先用[体质+季节+效果关键词]检索
            Page<HerbalRecipe> page = new Page<>(1, 1);
            IPage<HerbalRecipe> rec = herbalRecipeMapper.searchRecipes(
                    page,
                    null,              // keyword
                    season,            // season
                    constitutionType,  // constitutionType
                    effectKeywords     // effect
            );
            if (rec != null && rec.getRecords() != null && !rec.getRecords().isEmpty()) {
                HerbalRecipe hit = rec.getRecords().get(0);
                return Result.success(hit);
            }

            // 1) 走AI生成前，构造更完整的提示词，并显式排除现有名称，减少重复
            String enhancedPrompt = buildEnhancedPrompt(prompt, constitutionType, season, effectKeywords);
            String json = aiRecommendationService.generateRecipeJsonByPrompt(enhancedPrompt);
            if (!StringUtils.hasText(json)) {
                return Result.error(ResultCode.SYSTEM_ERROR.getCode(), "AI 生成失败，请稍后再试");
            }

            // 解析生成的JSON为实体
            String strictJson = toStrictJson(json);
            if (!StringUtils.hasText(strictJson)) {
                log.error("AI返回内容无法提取为有效JSON，原始内容前100字：{}", json.length() > 100 ? json.substring(0, 100) : json);
                return Result.error(ResultCode.PARAM_ERROR.getCode(), "AI 返回格式异常，请重试");
            }

            Map<String, Object> map = parseRecipeJsonLenient(strictJson);
            HerbalRecipe recipe = new HerbalRecipe();
            recipe.setRecipeName((String) map.getOrDefault("recipeName", "AI药膳"));
            // 若同名已存在，直接返回已存在记录，避免重复
            if (StringUtils.hasText(recipe.getRecipeName())) {
                HerbalRecipe exist = herbalRecipeMapper.selectByExactName(recipe.getRecipeName());
                if (exist != null && exist.getStatus() != null && exist.getStatus() == 1) {
                    return Result.success(exist);
                }
            }
            recipe.setConstitutionType((String) map.getOrDefault("constitutionType", StringUtils.hasText(constitutionType) ? constitutionType : "ALL"));
            recipe.setSeason((String) map.getOrDefault("season", season));
            recipe.setCategory((String) map.getOrDefault("category", "汤品"));
            recipe.setDifficulty(castToInt(map.get("difficulty"), 2));
            recipe.setCookingTime(castToInt(map.get("cookingTime"), 30));
            recipe.setServings(castToInt(map.get("servings"), 2));

            // 重置为字符串JSON字段
            if (map.get("ingredients") != null) {
                recipe.setIngredients(objectMapper.writeValueAsString(map.get("ingredients")));
            }
            if (map.get("steps") != null) {
                recipe.setSteps(objectMapper.writeValueAsString(map.get("steps")));
            }
            recipe.setEfficacy((String) map.getOrDefault("efficacy", ""));
            recipe.setSuitableSymptoms((String) map.getOrDefault("suitableSymptoms", ""));
            recipe.setContraindications((String) map.getOrDefault("contraindications", ""));
            if (map.get("nutritionInfo") != null) {
                recipe.setNutritionInfo(objectMapper.writeValueAsString(map.get("nutritionInfo")));
            }
            recipe.setTips((String) map.getOrDefault("tips", ""));
            recipe.setImage((String) map.getOrDefault("image", ""));
            recipe.setVideoUrl((String) map.getOrDefault("videoUrl", ""));

            // 默认状态：1-启用
            recipe.setStatus(1);
            recipe.setCreatedAt(LocalDateTime.now());
            recipe.setUpdatedAt(LocalDateTime.now());

            // 保存
            herbalRecipeMapper.insert(recipe);
            // 可选：为用户打上最近生成记录（略）
            // 生成并保存后，若指定了用户，则同时收藏该药膳（幂等）
            if (userId != null && recipe.getId() != null) {
                try {
                    UserFavorite existing = userFavoriteMapper.selectByUserAndTarget(userId, "RECIPE", recipe.getId());
                    if (existing == null) {
                        UserFavorite favorite = new UserFavorite();
                        favorite.setUserId(userId);
                        favorite.setTargetType("RECIPE");
                        favorite.setTargetId(recipe.getId());
                        favorite.setRemark("AI生成自动收藏");
                        userFavoriteMapper.insert(favorite);
                        herbalRecipeMapper.incrementFavoriteCount(recipe.getId());
                    }
                    recipe.setIsFavorited(true);
                } catch (Exception exFav) {
                    log.warn("AI生成后自动收藏失败（忽略，不影响主流程）：userId={}, recipeId={}, err={}",
                            userId, recipe.getId(), exFav.getMessage());
                }
            }

            log.info("AI生成并保存药膳成功：name={}, id={}", recipe.getRecipeName(), recipe.getId());
            return Result.success(recipe);
        } catch (Exception e) {
            log.error("生成并保存药膳失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    private String resolveSeason(java.time.LocalDate date) {
        int m = date.getMonthValue();
        if (m >= 3 && m <= 5) return "SPRING";
        if (m >= 6 && m <= 8) return "SUMMER";
        if (m >= 9 && m <= 11) return "AUTUMN";
        return "WINTER";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<HerbalRecipe> saveGeneratedRecipeJson(String json, Long testId, Long userId) {
        try {
            if (!StringUtils.hasText(json)) {
                return Result.error(ResultCode.PARAM_ERROR.getCode(), "内容为空");
            }
            Map<String, Object> map;
            try {
                // 优先按标准JSON解析（前端直接传对象/标准字符串时不应进入AI容错清洗流程）
                map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            } catch (Exception normalParseEx) {
                String strictJson = toStrictJson(json);
                if (!StringUtils.hasText(strictJson)) {
                    return Result.error(ResultCode.PARAM_ERROR.getCode(), "JSON格式不合法");
                }
                // 仅在标准解析失败时，才启用宽松修复，兼容 AI 返回噪声文本
                map = parseRecipeJsonLenient(strictJson);
                log.warn("保存药膳走宽松解析路径: userId={}, testId={}, err={}",
                        userId, testId, normalParseEx.getMessage());
            }
            HerbalRecipe recipe = new HerbalRecipe();
            recipe.setRecipeName((String) map.getOrDefault("recipeName", "AI药膳"));
            recipe.setConstitutionType((String) map.getOrDefault("constitutionType", "ALL"));
            recipe.setSeason((String) map.getOrDefault("season", "ALL"));
            recipe.setCategory((String) map.getOrDefault("category", ""));
            recipe.setDifficulty(castToNullableInt(map.get("difficulty")));
            recipe.setCookingTime(castToNullableInt(map.get("cookingTime")));
            recipe.setServings(castToNullableInt(map.get("servings")));
            if (map.get("ingredients") != null) {
                recipe.setIngredients(objectMapper.writeValueAsString(map.get("ingredients")));
            }
            if (map.get("steps") != null) {
                recipe.setSteps(objectMapper.writeValueAsString(map.get("steps")));
            }
            recipe.setEfficacy((String) map.getOrDefault("efficacy", ""));
            recipe.setSuitableSymptoms((String) map.getOrDefault("suitableSymptoms", ""));
            recipe.setContraindications((String) map.getOrDefault("contraindications", ""));
            if (map.get("nutritionInfo") != null) {
                recipe.setNutritionInfo(objectMapper.writeValueAsString(map.get("nutritionInfo")));
            }
            recipe.setTips((String) map.getOrDefault("tips", ""));
            recipe.setImage((String) map.getOrDefault("image", ""));
            recipe.setVideoUrl((String) map.getOrDefault("videoUrl", ""));
            if (testId != null) {
                recipe.setTestId(testId);
            } else if (userId != null) {
                // 兼容老调用：未显式传 testId 时，尝试关联用户最新一次体质测试
                UserConstitutionTest latest = constitutionTestMapper.selectLatestByUserId(userId);
                if (latest != null) {
                    recipe.setTestId(latest.getId());
                }
            }
            recipe.setStatus(1);
            recipe.setCreatedAt(LocalDateTime.now());
            recipe.setUpdatedAt(LocalDateTime.now());
            herbalRecipeMapper.insert(recipe);
            // 保存成功后，若指定了用户，则同时收藏该药膳（幂等）
            if (userId != null && recipe.getId() != null) {
                try {
                    UserFavorite existing = userFavoriteMapper.selectByUserAndTarget(userId, "RECIPE", recipe.getId());
                    if (existing == null) {
                        UserFavorite favorite = new UserFavorite();
                        favorite.setUserId(userId);
                        favorite.setTargetType("RECIPE");
                        favorite.setTargetId(recipe.getId());
                        favorite.setRemark("一键保存自动收藏");
                        userFavoriteMapper.insert(favorite);
                        herbalRecipeMapper.incrementFavoriteCount(recipe.getId());
                    }
                    recipe.setIsFavorited(true);
                } catch (Exception exFav) {
                    log.warn("保存后自动收藏失败（忽略，不影响主流程）：userId={}, recipeId={}, err={}",
                            userId, recipe.getId(), exFav.getMessage());
                }
            }
            return Result.success(recipe);
        } catch (Exception e) {
            log.error("保存生成的药膳失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 将AI响应清洗为严格JSON：
     * 1) 去除```json / ``` 包裹
     * 2) 截取第一个'{'到最后一个'}'闭合体
     */
    private String toStrictJson(String aiResponse) {
        if (!StringUtils.hasText(aiResponse)) {
            return null;
        }
        String s = aiResponse.trim();
        // 去除代码块围栏
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```json\\s*", "");
            s = s.replaceFirst("^```\\s*", "");
        }
        if (s.endsWith("```")) {
            int idx = s.lastIndexOf("```");
            if (idx > 0) {
                s = s.substring(0, idx);
            }
        }
        // 截取 JSON 对象范围（按花括号配平提取，考虑字符串与转义）
        int start = s.indexOf('{');
        if (start < 0) return null;

        boolean inString = false;
        char stringQuote = 0;
        boolean escape = false;
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == stringQuote) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                stringQuote = c;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    String json = s.substring(start, i + 1).trim();
                    // 规范化：去除多余的结尾逗号
                    json = json.replaceAll(",\\s*(\\}|\\])", "$1");
                    // 规范化：替换可能出现的中文引号
                    json = json.replace('“', '"')
                               .replace('”', '"')
                               .replace('‘', '"')
                               .replace('’', '"');
                    // 进一步清洗：去除键名前后的多余空白
                    json = json.replaceAll("\\s+\"\\s*", "\"");
                    return json;
                }
            }
        }
        // 未能配平，尝试“修复”尾部，尽力返回可解析的 JSON
        String tail = s.substring(start).trim();
        // 1) 标准化引号与移除围栏
        tail = tail.replace('“', '"')
                   .replace('”', '"')
                   .replace('‘', '"')
                   .replace('’', '"');
        // 2) 去除明显的结尾噪声：悬空的逗号、未闭合的键名/字符串
        tail = tail.replaceAll(",\\s*$", "");
        tail = tail.replaceAll("[:，]\\s*$", "");
        // 3) 统计括号并补齐
        int openCurly = 0, openSquare = 0;
        inString = false; escape = false; stringQuote = 0;
        for (int i = 0; i < tail.length(); i++) {
            char c = tail.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == stringQuote) {
                    inString = false;
                }
                continue;
            }
            if (c == '"' || c == '\'') { inString = true; stringQuote = c; continue; }
            if (c == '{') openCurly++;
            else if (c == '}') openCurly = Math.max(0, openCurly - 1);
            else if (c == '[') openSquare++;
            else if (c == ']') openSquare = Math.max(0, openSquare - 1);
        }
        StringBuilder repaired = new StringBuilder(tail);
        // 4) 去掉数组/对象内可能的尾随逗号
        String tmp = repaired.toString().replaceAll(",\\s*(\\}|\\])", "$1");
        repaired.setLength(0);
        repaired.append(tmp);
        // 5) 补齐方括号与花括号
        while (openSquare-- > 0) repaired.append(']');
        while (openCurly-- > 0) repaired.append('}');
        // 6) 最后保障：若不是以 '}' 结束，尝试补一个
        if (!repaired.toString().trim().endsWith("}")) {
            repaired.append('}');
        }
        // 7) 防止键名未闭合导致 EOF：移除末尾残缺的引号与逗号
        String fixed = repaired.toString()
                .replaceAll(",\\s*(\\}|\\])", "$1")
                .replaceAll("[,\\s]*}$", "}")
                .trim();
        return fixed;
    }

    /**
     * 更宽松的解析：先尝试标准解析；失败则对关键数组字段做修复再解析
     */
    private Map<String, Object> parseRecipeJsonLenient(String strictJson) throws Exception {
        try {
            return objectMapper.readValue(strictJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception first) {
            String repaired = strictJson;
            // 1) 全局：移除对象与数组中的尾随逗号
            repaired = repaired.replaceAll(",\\s*(\\}|\\])", "$1");
            // 2) 修复 ingredients 对象数组：移除不完整对象
            repaired = fixObjectArray(repaired, "ingredients", /*requireKey*/"name");
            // 3) 修复 steps 字符串数组：去除空元素、尾随逗号
            repaired = fixStringArray(repaired, "steps");
            // 4) 再尝试解析
            try {
                return objectMapper.readValue(repaired, new TypeReference<Map<String, Object>>() {});
            } catch (Exception second) {
                // 5) 兜底：仅提取顶层基础字段，数组设为空，避免生成失败
                Map<String, Object> fallback = new HashMap<>();
                fallback.put("recipeName", extractStringField(strictJson, "recipeName", "AI药膳"));
                fallback.put("constitutionType", extractStringField(strictJson, "constitutionType", "ALL"));
                fallback.put("season", extractStringField(strictJson, "season", resolveSeason(java.time.LocalDate.now())));
                fallback.put("category", extractStringField(strictJson, "category", ""));
                fallback.put("difficulty", castToNullableInt(extractNumberField(strictJson, "difficulty")));
                fallback.put("cookingTime", castToNullableInt(extractNumberField(strictJson, "cookingTime")));
                fallback.put("servings", castToNullableInt(extractNumberField(strictJson, "servings")));
                fallback.put("ingredients", java.util.Collections.emptyList());
                fallback.put("steps", java.util.Collections.emptyList());
                fallback.put("efficacy", extractStringField(strictJson, "efficacy", ""));
                fallback.put("suitableSymptoms", extractStringField(strictJson, "suitableSymptoms", ""));
                fallback.put("contraindications", extractStringField(strictJson, "contraindications", ""));
                fallback.put("nutritionInfo", new HashMap<>());
                return fallback;
            }
        }
    }

    private String extractStringField(String json, String key, String defVal) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}
        return defVal;
    }

    private Number extractNumberField(String json, String key) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*([0-9]+)");
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) return Integer.parseInt(m.group(1));
        } catch (Exception ignored) {}
        return null;
    }

    private String fixObjectArray(String json, String field, String requireKey) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*\\[(.*?)\\]", java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher m = p.matcher(json);
            if (!m.find()) return json;
            String body = m.group(1);
            // 简单切分对象（假设无嵌套），兼容最后一个无逗号结尾
            java.util.List<String> parts = new java.util.ArrayList<>();
            StringBuilder buf = new StringBuilder();
            int depth = 0;
            boolean inStr = false, esc = false;
            for (int i = 0; i < body.length(); i++) {
                char c = body.charAt(i);
                if (inStr) {
                    buf.append(c);
                    if (esc) { esc = false; continue; }
                    if (c == '\\') { esc = true; continue; }
                    if (c == '"') { inStr = false; }
                    continue;
                }
                if (c == '"') { inStr = true; buf.append(c); continue; }
                if (c == '{') { depth++; buf.append(c); continue; }
                if (c == '}') {
                    depth--; buf.append(c);
                    if (depth == 0) {
                        parts.add(buf.toString());
                        buf.setLength(0);
                    }
                    continue;
                }
                if (depth > 0 || !Character.isWhitespace(c)) {
                    buf.append(c);
                }
            }
            java.util.List<String> cleaned = new java.util.ArrayList<>();
            for (String part : parts) {
                String cleanedPart = part.replaceAll(",\\s*([}\\]])", "$1");
                if (requireKey == null || cleanedPart.contains("\"" + requireKey + "\"")) {
                    cleaned.add(cleanedPart);
                }
            }
            String rebuilt = "[" + String.join(",", cleaned) + "]";
            return json.substring(0, m.start(1) - 1) + rebuilt + json.substring(m.end(1) + 1);
        } catch (Exception ignored) {
            return json;
        }
    }

    private String fixStringArray(String json, String field) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*\\[(.*?)\\]", java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher m = p.matcher(json);
            if (!m.find()) return json;
            String body = m.group(1);
            String[] tokens = body.split(",");
            java.util.List<String> items = new java.util.ArrayList<>();
            for (String t : tokens) {
                String s = t.trim();
                if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
                    String val = s.substring(1, s.length() - 1).trim();
                    if (!val.isEmpty()) items.add("\"" + val.replace("\"", "\\\"") + "\"");
                }
            }
            String rebuilt = "[" + String.join(",", items) + "]";
            return json.substring(0, m.start(1) - 1) + rebuilt + json.substring(m.end(1) + 1);
        } catch (Exception ignored) {
            return json;
        }
    }

    /**
     * 从测试记录中提取与药膳功效相关的关键词：
     * 1) 舌诊特征（如“口干”“怕冷”“舌苔厚腻”等）
     * 2) 用户自述（如“睡眠差、易上火”等）
     * 3) 体质默认功效映射（兜底）
     */
    private String extractEffectKeywordsFromTest(UserConstitutionTest test) {
        if (test == null || !StringUtils.hasText(test.getTestResult())) {
            return null;
        }
        try {
            Map<?, ?> map = objectMapper.readValue(test.getTestResult(), new TypeReference<Map<String, Object>>() {});
            StringBuilder sb = new StringBuilder();
            Object tongue = map.get("tongueResult");
            if (tongue != null) {
                String t = String.valueOf(tongue);
                // 简单提取中文词串（最多取前3个非空词）
                String[] parts = t.replaceAll("[^\\u4e00-\\u9fa5，、;；\\s]", "")
                        .split("[，、;；\\s]+");
                int cnt = 0;
                for (String p : parts) {
                    if (StringUtils.hasText(p)) {
                        if (sb.length() > 0) sb.append(' ');
                        sb.append(p);
                        if (++cnt >= 3) break;
                    }
                }
            }
            Object usd = map.get("userSelfDescription");
            if (usd != null && StringUtils.hasText(String.valueOf(usd))) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(String.valueOf(usd).replaceAll("[^\\u4e00-\\u9fa5\\s]", ""));
            }
            // 体质映射兜底关键词（帮助 SQL 命中 efficacy/suitable_symptoms）
            if (sb.length() == 0 && StringUtils.hasText(test.getPrimaryConstitution())) {
                switch (test.getPrimaryConstitution()) {
                    case "YANGXU": sb.append("温阳 补气"); break;
                    case "YINXU": sb.append("滋阴 清热"); break;
                    case "QIXU": sb.append("益气 健脾"); break;
                    case "TANSHI": sb.append("祛湿 健脾"); break;
                    case "SHIRE": sb.append("清热 利湿"); break;
                    case "XUEYU": sb.append("活血 化瘀"); break;
                    case "QIYU": sb.append("疏肝 解郁"); break;
                    default: sb.append("调理 益气 健脾");
                }
            }
            String kw = sb.toString().trim();
            return StringUtils.hasText(kw) ? kw : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 组合更严格的提示词：体质、季节、症状/功效关键词 + 排除库内已有名称
     */
    private String buildEnhancedPrompt(String userPrompt, String constitutionType, String season, String effectKeywords) {
        StringBuilder p = new StringBuilder();
        if (StringUtils.hasText(constitutionType)) {
            p.append("主体质：").append(constitutionType).append("。");
        }
        if (StringUtils.hasText(season)) {
            p.append("当前季节：").append(season).append("。");
        }
        if (StringUtils.hasText(effectKeywords)) {
            p.append("请匹配以下症状/功效方向：").append(effectKeywords).append("。");
        }
        // 收集库内前若干热门名称，构造排除列表（简单取热门接口）
        try {
            java.util.List<HerbalRecipe> hot = herbalRecipeMapper.selectPopularRecipes(10);
            if (hot != null && !hot.isEmpty()) {
                String names = hot.stream().map(HerbalRecipe::getRecipeName)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .collect(java.util.stream.Collectors.joining("、"));
                if (StringUtils.hasText(names)) {
                    p.append("不要生成以下已存在的菜谱名称：").append(names).append("。");
                }
            }
        } catch (Exception ignored) {}
        if (StringUtils.hasText(userPrompt)) {
            p.append("附加要求：").append(userPrompt);
        }
        return p.toString();
    }

    private Integer castToInt(Object obj, int defaultValue) {
        if (obj == null) return defaultValue;
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private Integer castToNullableInt(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            String s = String.valueOf(obj).trim();
            if (!StringUtils.hasText(s)) return null;
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 生成默认推荐理由（降级方案）
     */
    private String buildDefaultRecommendationReason(HerbalRecipe recipe, UserConstitutionTest constitution) {
        StringBuilder reason = new StringBuilder();
        reason.append("根据您的").append(constitution.getPrimaryConstitution()).append("体质，");
        reason.append("推荐这道").append(recipe.getRecipeName()).append("。");
        if (StringUtils.hasText(recipe.getEfficacy())) {
            reason.append("该药膳具有").append(recipe.getEfficacy()).append("的功效，");
        }
        reason.append("适合您当前的身体状况。建议适量食用，配合规律作息效果更佳。");
        return reason.toString();
    }

    /**
     * 补全食材备注信息
     * 根据食材名称查询食材库，补充性味、功效等信息作为备注
     */
    private void enrichIngredientsWithNotes(HerbalRecipe recipe) {
        if (recipe == null || !StringUtils.hasText(recipe.getIngredients())) {
            return;
        }

        try {
            // 解析食材JSON
            List<Map<String, Object>> ingredients = objectMapper.readValue(
                recipe.getIngredients(),
                new TypeReference<List<Map<String, Object>>>() {}
            );

            if (ingredients == null || ingredients.isEmpty()) {
                return;
            }

            // 为每个食材查询详细信息并补充备注
            for (Map<String, Object> ingredient : ingredients) {
                String name = (String) ingredient.get("name");
                if (!StringUtils.hasText(name)) {
                    continue;
                }

                try {
                    // 查询食材库
                    QueryWrapper<Ingredient> wrapper = new QueryWrapper<>();
                    wrapper.eq("name", name);
                    wrapper.eq("status", 1);
                    Ingredient ingredientInfo = ingredientMapper.selectOne(wrapper);

                    if (ingredientInfo != null) {
                        // 构建备注信息
                        StringBuilder note = new StringBuilder();

                        // 性味
                        if (StringUtils.hasText(ingredientInfo.getProperties())) {
                            note.append("性味：").append(ingredientInfo.getProperties());
                        }

                        // 味道
                        if (StringUtils.hasText(ingredientInfo.getFlavor())) {
                            if (note.length() > 0) {
                                note.append("，");
                            }
                            note.append("味：").append(ingredientInfo.getFlavor());
                        }

                        // 功效
                        if (StringUtils.hasText(ingredientInfo.getEfficacy())) {
                            if (note.length() > 0) {
                                note.append("；");
                            }
                            note.append("功效：").append(ingredientInfo.getEfficacy());
                        }

                        // 将备注添加到食材信息中
                        if (note.length() > 0) {
                            ingredient.put("note", note.toString());
                        }
                    }
                } catch (Exception e) {
                    log.warn("查询食材{}的详细信息失败: {}", name, e.getMessage());
                    // 继续处理下一个食材
                }
            }

            // 将补全后的食材信息重新序列化为JSON
            recipe.setIngredients(objectMapper.writeValueAsString(ingredients));

        } catch (Exception e) {
            log.error("补全食材备注信息失败", e);
            // 如果处理失败，保持原始数据不变
        }
    }

    @Override
    public Result<java.util.List<HerbalRecipe>> getRecipesByTestId(Long testId) {
        try {
            if (testId == null) {
                return Result.error(ResultCode.PARAM_ERROR.getCode(), "testId 不能为空");
            }
            List<HerbalRecipe> list = herbalRecipeMapper.selectByTestId(testId);
            return Result.success(list != null ? list : java.util.Collections.emptyList());
        } catch (Exception e) {
            log.error("按 testId 查询药膳失败, testId={}", testId, e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }
}

