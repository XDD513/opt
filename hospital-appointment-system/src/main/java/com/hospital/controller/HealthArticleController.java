package com.hospital.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hospital.annotation.OperationLog;
import com.hospital.common.result.Result;
import com.hospital.common.result.ResultCode;
import com.hospital.entity.HealthArticle;
import com.hospital.service.HealthArticleService;
import com.hospital.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/article")
public class HealthArticleController {

    @Autowired
    private HealthArticleService healthArticleService;

    @Autowired
    private JwtUtil jwtUtil;

    private boolean isAdmin(HttpServletRequest request) {
        Integer roleType = jwtUtil.getRoleTypeFromRequest(request);
        return roleType != null && roleType == 1;
    }

    @GetMapping("/list")
    public Result<IPage<HealthArticle>> list(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "constitutionType", required = false) String constitutionType,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "isFeatured", required = false) Integer isFeatured,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        return healthArticleService.getArticleList(category, constitutionType, tags, isFeatured, null, keyword, userId, type, false, pageNum, pageSize);
    }

    @GetMapping("/{id}")
    public Result<HealthArticle> detail(@PathVariable("id") Long id) {
        return healthArticleService.getArticleDetail(id);
    }

    @OperationLog(module = "ARTICLE", type = "INSERT", description = "发布文章")
    @PostMapping("/publish")
    public Result<HealthArticle> publish(@RequestBody HealthArticle article, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        article.setAuthorId(userId);
        return healthArticleService.publishArticle(article);
    }

    @OperationLog(module = "ARTICLE", type = "UPDATE", description = "更新文章")
    @PutMapping("/{id}")
    public Result<HealthArticle> update(@PathVariable("id") Long id, @RequestBody HealthArticle article, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        article.setId(id);
        article.setAuthorId(userId);
        return healthArticleService.updateArticle(article);
    }

    @OperationLog(module = "ARTICLE", type = "DELETE", description = "删除文章")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return healthArticleService.deleteArticle(id, userId);
    }

    @PostMapping("/like/{id}")
    public Result<Void> like(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return healthArticleService.likeArticle(id, userId);
    }

    @DeleteMapping("/like/{id}")
    public Result<Void> unlike(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return healthArticleService.unlikeArticle(id, userId);
    }

    @PostMapping("/favorite/{id}")
    public Result<Void> favorite(@PathVariable("id") Long id,
                                 @RequestParam(value = "remark", required = false) String remark,
                                 HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return healthArticleService.favoriteArticle(id, userId, remark);
    }

    @DeleteMapping("/favorite/{id}")
    public Result<Void> unfavorite(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return healthArticleService.unfavoriteArticle(id, userId);
    }

    @GetMapping("/status/{id}")
    public Result<Map<String, Boolean>> status(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return healthArticleService.getArticleStatus(id, userId);
    }

    @GetMapping("/favorites")
    public Result<IPage<HealthArticle>> favorites(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return healthArticleService.getArticleList(null, null, null, null, null, null, userId, "favorites", false, pageNum, pageSize);
    }

    @GetMapping("/my")
    public Result<IPage<HealthArticle>> myArticles(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return healthArticleService.getArticleList(null, null, null, null, null, null, userId, "my", false, pageNum, pageSize);
    }

    @GetMapping("/recommended")
    public Result<IPage<HealthArticle>> recommended(@RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        return healthArticleService.getArticleList(null, null, null, null, null, null, null, "recommended", false, 1, limit);
    }

    @GetMapping("/popular")
    public Result<IPage<HealthArticle>> popular(@RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        return healthArticleService.getArticleList(null, null, null, null, null, null, null, "popular", false, 1, limit);
    }

    @GetMapping("/tags")
    public Result<List<String>> allTags() {
        return healthArticleService.getAllTags();
    }

    @GetMapping("/admin/list")
    public Result<IPage<HealthArticle>> adminList(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "constitutionType", required = false) String constitutionType,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "isFeatured", required = false) Integer isFeatured,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {
        if (!isAdmin(request)) {
            return Result.error(ResultCode.FORBIDDEN);
        }
        return healthArticleService.getArticleList(category, constitutionType, tags, isFeatured, status, keyword, null, null, true, pageNum, pageSize);
    }

    @OperationLog(module = "ARTICLE_ADMIN", type = "UPDATE", description = "审核文章")
    @PutMapping("/admin/review/{id}")
    public Result<Void> review(@PathVariable("id") Long id,
                               @RequestParam("approved") Boolean approved,
                               @RequestParam(value = "reason", required = false) String reason,
                               HttpServletRequest request) {
        if (!isAdmin(request)) {
            return Result.error(ResultCode.FORBIDDEN);
        }
        return healthArticleService.reviewArticle(id, approved, reason);
    }

    @OperationLog(module = "ARTICLE_ADMIN", type = "UPDATE", description = "上架文章")
    @PutMapping("/admin/online/{id}")
    public Result<Void> online(@PathVariable("id") Long id, HttpServletRequest request) {
        if (!isAdmin(request)) {
            return Result.error(ResultCode.FORBIDDEN);
        }
        return healthArticleService.onlineArticle(id);
    }

    @OperationLog(module = "ARTICLE_ADMIN", type = "UPDATE", description = "下架文章")
    @PutMapping("/admin/offline/{id}")
    public Result<Void> offline(@PathVariable("id") Long id,
                                @RequestParam(value = "reason", required = false) String reason,
                                HttpServletRequest request) {
        if (!isAdmin(request)) {
            return Result.error(ResultCode.FORBIDDEN);
        }
        return healthArticleService.offlineArticle(id, reason);
    }

    @OperationLog(module = "ARTICLE_ADMIN", type = "UPDATE", description = "推荐文章")
    @PutMapping("/admin/recommend/{id}")
    public Result<Void> recommend(@PathVariable("id") Long id, HttpServletRequest request) {
        if (!isAdmin(request)) {
            return Result.error(ResultCode.FORBIDDEN);
        }
        return healthArticleService.setFeatured(id, 1);
    }

    @OperationLog(module = "ARTICLE_ADMIN", type = "UPDATE", description = "取消推荐文章")
    @PutMapping("/admin/unrecommend/{id}")
    public Result<Void> unrecommend(@PathVariable("id") Long id, HttpServletRequest request) {
        if (!isAdmin(request)) {
            return Result.error(ResultCode.FORBIDDEN);
        }
        return healthArticleService.setFeatured(id, 0);
    }
}
