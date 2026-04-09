package com.hospital.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hospital.common.result.Result;
import com.hospital.common.result.ResultCode;
import com.hospital.entity.HerbalRecipe;
import com.hospital.entity.Ingredient;
import com.hospital.service.AiRecommendationService;
import com.hospital.service.HerbalRecipeService;
import com.hospital.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 药膳食谱控制器
 *
 * @author TCM Health Team
 * @since 2025-11-03
 */
@Slf4j
@RestController
@RequestMapping("/api/recipe")
public class HerbalRecipeController {

    @Autowired
    private HerbalRecipeService herbalRecipeService;

    @Autowired
    private AiRecommendationService aiRecommendationService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    /**
     * 根据用户体质推荐药膳（分页）
     */
    @GetMapping("/recommend")
    public Result<IPage<HerbalRecipe>> getRecommendedRecipes(
            @RequestParam(value = "season", required = false) String season,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize, // 默认值对应SystemConstants.DEFAULT_PAGE_SIZE
            HttpServletRequest httpRequest) {
        
        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        log.info("为用户{}推荐药膳，季节：{}", userId, season);
        return herbalRecipeService.getRecommendedRecipes(userId, season, pageNum, pageSize);
    }

    /**
     * 协同过滤推荐
     */
    @GetMapping("/recommend/cf")
    public Result<List<HerbalRecipe>> recommendByCollaborativeFiltering(
            @RequestParam(value = "limit", defaultValue = "6") Integer limit,
            HttpServletRequest httpRequest) {

        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        List<HerbalRecipe> recipes = aiRecommendationService.recommendByCollaborativeFiltering(userId, limit);
        return Result.success(recipes);
    }

    /**
     * 内容画像推荐
     */
    @GetMapping("/recommend/content")
    public Result<List<HerbalRecipe>> recommendByContentPreference(
            @RequestParam(value = "limit", defaultValue = "6") Integer limit,
            HttpServletRequest httpRequest) {

        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        List<HerbalRecipe> recipes = aiRecommendationService.recommendByContentPreference(userId, limit);
        return Result.success(recipes);
    }

    /**
     * 个性化组合推荐
     */
    @GetMapping("/recommend/personalized")
    public Result<List<HerbalRecipe>> recommendPersonalized(
            @RequestParam(value = "limit", defaultValue = "6") Integer limit,
            HttpServletRequest httpRequest) {

        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        List<HerbalRecipe> recipes = aiRecommendationService.recommendPersonalized(userId, limit);
        return Result.success(recipes);
    }

    /**
     * 获取全部药膳列表（分页，不包含AI推荐）
     */
    @GetMapping("/list")
    public Result<IPage<HerbalRecipe>> getAllRecipes(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            HttpServletRequest httpRequest) {

        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        log.info("获取全部药膳列表，用户ID：{}", userId);
        return herbalRecipeService.getAllRecipes(pageNum, pageSize, userId);
    }

    /**
     * 搜索药膳（分页）
     */
    @GetMapping("/search")
    public Result<IPage<HerbalRecipe>> searchRecipes(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "season", required = false) String season,
            @RequestParam(value = "constitutionType", required = false) String constitutionType,
            @RequestParam(value = "effect", required = false) String effect,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            HttpServletRequest httpRequest) {

        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        log.info("搜索药膳，keyword={}, season={}, constitutionType={}, effect={}, userId={}",
                keyword, season, constitutionType, effect, userId);
        return herbalRecipeService.searchRecipes(keyword, season, constitutionType, effect, pageNum, pageSize, userId);
    }

    /**
     * 获取药膳详情
     */
    @GetMapping("/{id}")
    public Result<HerbalRecipe> getRecipeDetail(
            @PathVariable("id") Long id,
            HttpServletRequest httpRequest) {

        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        log.info("获取药膳详情: {}，用户ID：{}", id, userId);
        return herbalRecipeService.getRecipeDetail(id, userId);
    }

    /**
     * 获取热门药膳
     */
    @GetMapping("/popular")
    public Result<List<HerbalRecipe>> getPopularRecipes(
            @RequestParam(value = "limit", defaultValue = "10") Integer limit,
            HttpServletRequest httpRequest) {

        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        log.info("获取热门药膳，数量：{}，用户ID：{}", limit, userId);
        return herbalRecipeService.getPopularRecipes(limit, userId);
    }

    /**
     * 获取时令药膳
     */
    @GetMapping("/seasonal")
    public Result<List<HerbalRecipe>> getSeasonalRecipes(
            @RequestParam("season") String season,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit,
            HttpServletRequest httpRequest) {

        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        log.info("获取时令药膳：{}，数量：{}，用户ID：{}", season, limit, userId);
        return herbalRecipeService.getSeasonalRecipes(season, limit, userId);
    }

    /**
     * 收藏药膳
     */
    @PostMapping("/favorite/{id}")
    public Result<Void> favoriteRecipe(
            @PathVariable("id") Long id,
            @RequestParam(value = "remark", required = false) String remark,
            HttpServletRequest httpRequest) {
        
        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        log.info("用户{}收藏药膳{}", userId, id);
        return herbalRecipeService.favoriteRecipe(userId, id, remark);
    }

    /**
     * 取消收藏
     */
    @DeleteMapping("/favorite/{id}")
    public Result<Void> unfavoriteRecipe(
            @PathVariable("id") Long id,
            HttpServletRequest httpRequest) {
        
        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        log.info("用户{}取消收藏药膳{}", userId, id);
        return herbalRecipeService.unfavoriteRecipe(userId, id);
    }

    /**
     * 获取用户收藏的药膳列表（分页）
     */
    @GetMapping("/favorites")
    public Result<IPage<HerbalRecipe>> getUserFavorites(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize, // 默认值对应SystemConstants.DEFAULT_PAGE_SIZE
            HttpServletRequest httpRequest) {
        
        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        log.info("获取用户{}的收藏列表", userId);
        return herbalRecipeService.getUserFavorites(userId, pageNum, pageSize);
    }

    /**
     * 根据体质类型查询适用食材
     */
    @GetMapping("/ingredients/{constitutionType}")
    public Result<List<Ingredient>> getIngredientsByConstitution(
            @PathVariable("constitutionType") String constitutionType) {
        
        log.info("获取体质{}的适用食材", constitutionType);
        return herbalRecipeService.getIngredientsByConstitution(constitutionType);
    }

    /**
     * 根据提示词生成药膳（并保存）
     */
    @PostMapping("/generate")
    public Result<HerbalRecipe> generateRecipe(@RequestBody java.util.Map<String, String> body,
                                               HttpServletRequest httpRequest) {
        String prompt = body != null ? body.getOrDefault("prompt", "") : "";
        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        log.info("AI生成药膳，请求用户：{}，提示词长度：{}", userId, prompt != null ? prompt.length() : 0);
        return herbalRecipeService.generateAndSaveRecipe(prompt, userId);
    }

    /**
     * 根据提示词生成药膳 JSON 文本（不入库）
     */
    @PostMapping("/generate-json")
    public Result<String> generateRecipeJson(@RequestBody java.util.Map<String, String> body) {
        String prompt = body != null ? body.getOrDefault("prompt", "") : "";
        if (prompt == null || prompt.trim().isEmpty()) {
            return Result.error("prompt不能为空");
        }
        AtomicReference<String> latest = new AtomicReference<>("");
        StringBuilder full = new StringBuilder();
        aiRecommendationService.generateRecipeJsonStream(prompt, chunk -> {
            latest.set(chunk);
            full.append(chunk);
        });
        String last = latest.get();
        if (last != null && last.contains("\"error\"")) {
            return Result.error("AI生成失败: " + last);
        }
        return Result.success(full.toString());
    }

    @GetMapping(value = "/generate-json/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateRecipeJsonStream(@RequestParam("prompt") String prompt) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            aiRecommendationService.generateRecipeJsonStream(prompt, chunk -> {
                try {
                    emitter.send(SseEmitter.event().data(chunk));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            emitter.send(SseEmitter.event().name("finish").data("[DONE]"));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /**
     * 直接保存 AI 生成完成后的 JSON
     */
    @PostMapping("/save")
    public Result<HerbalRecipe> saveRecipeFromJson(@RequestBody java.util.Map<String, Object> body,
                                                   HttpServletRequest httpRequest) {
        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        Object json = body != null ? body.get("json") : null;
        Object testIdObj = body != null ? (body.containsKey("test_id") ? body.get("test_id") : body.get("testId")) : null;
        Long testId = null;
        if (testIdObj != null) {
            try {
                testId = Long.parseLong(String.valueOf(testIdObj));
            } catch (Exception e) {
                log.warn("保存药膳：testId格式不合法 userId={} raw={}", userId, testIdObj);
            }
        }
        String jsonStr = null;
        try {
            if (json == null) {
                jsonStr = null;
            } else if (json instanceof String) {
                jsonStr = (String) json;
            } else {
                // 前端可能直接传了对象/数组；此时 String.valueOf 会变成 Map.toString() 造成非JSON
                ObjectMapper om = (objectMapper != null) ? objectMapper : new ObjectMapper();
                jsonStr = om.writeValueAsString(json);
            }
        } catch (Exception e) {
            log.warn("保存药膳：入参json序列化失败 userId={} type={} err={}",
                    userId, json != null ? json.getClass().getName() : null, e.getMessage());
            jsonStr = json != null ? String.valueOf(json) : null;
        }
        return herbalRecipeService.saveGeneratedRecipeJson(jsonStr, testId, userId);
    }

    /**
     * 将“药膳建议”文本模板化并保存为药膳
     */
    @PostMapping("/suggestion/save")
    public Result<HerbalRecipe> saveRecipeFromSuggestion(@RequestBody java.util.Map<String, Object> body,
                                                         HttpServletRequest httpRequest) {
        Long userId = jwtUtil.getUserIdFromRequest(httpRequest);
        Long testId = null;
        String text = null;
        if (body != null) {
            Object t = body.get("testId");
            if (t != null) {
                try { testId = Long.parseLong(String.valueOf(t)); } catch (Exception ignored) {}
            }
            Object s = body.get("text");
            if (s != null) text = String.valueOf(s);
        }
        log.info("保存药膳建议为药膳：userId={}, testId={}, textLen={}", userId, testId, text != null ? text.length() : 0);
        return herbalRecipeService.saveSuggestionText(text, testId, userId);
    }

    /**
     * 按体质测试ID获取本次生成的药膳
     */
    @GetMapping("/by-test/{testId}")
    public Result<List<HerbalRecipe>> getRecipesByTestId(@PathVariable("testId") Long testId) {
        return herbalRecipeService.getRecipesByTestId(testId);
    }
}

