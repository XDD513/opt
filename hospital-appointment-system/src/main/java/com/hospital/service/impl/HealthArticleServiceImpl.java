package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.result.Result;
import com.hospital.common.result.ResultCode;
import com.hospital.entity.*;
import com.hospital.mapper.HealthArticleMapper;
import com.hospital.mapper.UserFavoriteMapper;
import com.hospital.mapper.UserLikeMapper;
import com.hospital.mapper.UserMapper;
import com.hospital.service.ArticleNotificationService;
import com.hospital.service.HealthArticleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 养生文章服务实现类
 *
 * @author TCM Health Team
 * @date 2025-11-03
 */
@Slf4j
@Service
public class HealthArticleServiceImpl implements HealthArticleService {
    private static final int ARTICLE_STATUS_PUBLISHED = 1;

    @Autowired
    private HealthArticleMapper articleMapper;

    @Autowired
    private UserLikeMapper userLikeMapper;

    @Autowired
    private UserFavoriteMapper userFavoriteMapper;

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ArticleNotificationService articleNotificationService;

    @Autowired
    private com.hospital.util.RedisUtil redisUtil;

    @Autowired
    private com.hospital.mapper.ArticleCommentMapper articleCommentMapper;

    @Autowired
    private com.hospital.mapper.ArticleTagMapper articleTagMapper;

    /**
     * 统一的分页查询文章列表接口（支持所有查询场景）
     */
    @Override
    public Result<IPage<HealthArticle>> getArticleList(String category, String constitutionType, String tags, 
                                                        Integer isFeatured, Integer status, String keyword, 
                                                        Long userId, String type, Boolean isAdmin, Integer pageNum, Integer pageSize) {
        try {
            Page<HealthArticle> page = new Page<>(pageNum, pageSize);
            IPage<HealthArticle> result;

            // 根据type参数决定查询类型
            if ("my".equals(type) && userId != null) {
                // 查询我的文章
                result = articleMapper.selectByAuthorId(page, userId);
            } else if ("favorites".equals(type) && userId != null) {
                // 查询收藏的文章（需要特殊处理）
                Page<UserFavorite> favoritePage = new Page<>(pageNum, pageSize);
                IPage<UserFavorite> favorites = userFavoriteMapper.selectArticleFavoritesByUserId(favoritePage, userId);
                // 转换为文章列表
                result = convertFavoritesToArticles(favorites);
            } else if ("recommended".equals(type)) {
                // 查询精选文章（不分页，返回列表）
                List<HealthArticle> articles = articleMapper.selectRecommendedArticles(pageSize);
                result = convertListToPage(articles, pageNum, pageSize);
            } else if ("popular".equals(type)) {
                // 查询热门文章（不分页，返回列表）
                List<HealthArticle> articles = articleMapper.selectPopularArticles(pageSize);
                result = convertListToPage(articles, pageNum, pageSize);
            } else {
                // 普通列表查询（支持搜索、筛选等）
                // 处理标签参数：将逗号分隔的字符串转换为List
                List<String> tagList = null;
                if (tags != null && !tags.trim().isEmpty()) {
                    tagList = new java.util.ArrayList<>();
                    String[] tagArray = tags.split(",");
                    for (String tag : tagArray) {
                        String trimmedTag = tag.trim();
                        if (!trimmedTag.isEmpty()) {
                            tagList.add(trimmedTag);
                        }
                    }
                }

                // 如果有keyword且不是管理员查询，使用搜索查询；如果是管理员查询或status不为null，使用管理员查询；否则使用普通查询
                if (keyword != null && !keyword.trim().isEmpty() && status == null && (isAdmin == null || !isAdmin)) {
                    // 搜索查询（只搜索已发布文章，普通用户使用）
                    result = articleMapper.searchArticles(page, keyword);
                } else if (status != null || (isAdmin != null && isAdmin)) {
                    // 管理员查询（支持状态筛选和所有筛选条件）
                    // 如果是管理员查询但status为null，表示查询所有状态
                    result = articleMapper.selectArticlePageForAdmin(page, category, constitutionType, tagList, isFeatured, status, keyword);
                    
                    // 从article_tag表查询每个文章的标签并设置到tags字段
                    if (result.getRecords() != null && !result.getRecords().isEmpty()) {
                        for (HealthArticle article : result.getRecords()) {
                            List<String> tagNames = articleTagMapper.selectTagNamesByArticleId(article.getId());
                            if (tagNames != null && !tagNames.isEmpty()) {
                                article.setTags(String.join(",", tagNames));
                            } else {
                                article.setTags("");
                            }
                        }
                    }
                } else {
                    // 普通查询（只查询已发布文章）
                    result = articleMapper.selectArticlePage(page, category, constitutionType, tagList, isFeatured);
                }
            }

            // 缓存策略：普通列表查询的前3页缓存15分钟
            if (type == null && keyword == null && status == null && pageNum <= 3) {
                Map<String, Object> filterParams = new java.util.HashMap<>();
                if (category != null && !"all".equals(category)) {
                    filterParams.put("cat", category);
                }
                if (constitutionType != null && !"all".equals(constitutionType)) {
                    filterParams.put("type", constitutionType);
                }
                if (tags != null && !tags.trim().isEmpty()) {
                    filterParams.put("tags", tags);
                }
                if (isFeatured != null && !"all".equals(String.valueOf(isFeatured))) {
                    filterParams.put("featured", isFeatured);
                }
                String cacheKey = redisUtil.buildCacheKey("hospital:common:article:list", pageNum, pageSize, filterParams);
                redisUtil.set(cacheKey, result, 15, java.util.concurrent.TimeUnit.MINUTES);
            }

            return Result.success(result);

        } catch (Exception e) {
            log.error("查询文章列表失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 将收藏列表转换为文章列表
     */
    private IPage<HealthArticle> convertFavoritesToArticles(IPage<UserFavorite> favorites) {
        Page<HealthArticle> articlePage = new Page<>(favorites.getCurrent(), favorites.getSize());
        articlePage.setTotal(favorites.getTotal());
        articlePage.setPages(favorites.getPages());
        
        List<HealthArticle> articles = new java.util.ArrayList<>();
        for (UserFavorite favorite : favorites.getRecords()) {
            if (favorite.getTargetId() != null) {
                // 如果关联查询已经包含了文章信息，直接使用
                if (favorite.getTitle() != null) {
                    HealthArticle article = new HealthArticle();
                    article.setId(favorite.getTargetId());
                    article.setTitle(favorite.getTitle());
                    article.setSummary(favorite.getSummary());
                    article.setCoverImage(favorite.getCoverImage());
                    article.setCategory(favorite.getCategory());
                    article.setViewCount(favorite.getViewCount());
                    article.setLikeCount(favorite.getLikeCount());
                    article.setFavoriteCount(favorite.getFavoriteCount());
                    // 设置作者姓名
                    if (favorite.getAuthorName() != null) {
                        article.setAuthorName(favorite.getAuthorName());
                    }
                    articles.add(article);
                } else {
                    // 否则从数据库查询
                    HealthArticle article = articleMapper.selectById(favorite.getTargetId());
                    if (article != null && article.getStatus() == 1) { // 只返回已发布的文章
                        articles.add(article);
                    }
                }
            }
        }
        articlePage.setRecords(articles);
        return articlePage;
    }

    /**
     * 将列表转换为分页对象
     */
    private IPage<HealthArticle> convertListToPage(List<HealthArticle> articles, Integer pageNum, Integer pageSize) {
        Page<HealthArticle> page = new Page<>(pageNum, pageSize);
        page.setTotal(articles.size());
        
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, articles.size());
        if (start < articles.size()) {
            page.setRecords(articles.subList(start, end));
        } else {
            page.setRecords(new java.util.ArrayList<>());
        }
        return page;
    }

    /**
     * 获取文章详情
     */
    @Override
    public Result<HealthArticle> getArticleDetail(Long id) {
        try {
            String cacheKey = "hospital:common:article:detail:id:" + id;

            // 尝试从缓存获取
            Object cached = redisUtil.get(cacheKey);
            if (cached instanceof HealthArticle article) {
                // 即使从缓存获取，也要从数据库查询最新的标签（因为标签可能被更新）
                List<String> tagNames = articleTagMapper.selectTagNamesByArticleId(id);
                if (tagNames != null && !tagNames.isEmpty()) {
                    article.setTags(String.join(",", tagNames));
                } else {
                    article.setTags("");
                }
                
                // 仅已发布文章才计入浏览量，避免待审核/驳回文章污染统计数据
                if (isPublishedArticle(article)) {
                    articleMapper.incrementViewCount(id);
                    article.setViewCount(article.getViewCount() + 1);
                    article.setUpdatedAt(LocalDateTime.now());
                }
                redisUtil.set(cacheKey, article, 30, java.util.concurrent.TimeUnit.MINUTES);
                // 失效列表缓存，确保列表中的浏览数也会更新
                redisUtil.deleteByPattern("hospital:common:article:list:*");
                redisUtil.deleteByPattern("hospital:common:article:search:*");
                return Result.success(article);
            }

            HealthArticle article = articleMapper.selectById(id);
            if (article == null) {
                return Result.error(ResultCode.DATA_NOT_FOUND.getCode(), "文章不存在");
            }

            // 查询作者信息并设置作者姓名
            if (article.getAuthorId() != null) {
                User author = userMapper.selectById(article.getAuthorId());
                if (author != null) {
                    article.setAuthorName(author.getRealName() != null ? author.getRealName() : author.getUsername());
                }
            }

            // 从article_tag表查询标签并设置到tags字段（逗号分隔）
            List<String> tagNames = articleTagMapper.selectTagNamesByArticleId(id);
            if (tagNames != null && !tagNames.isEmpty()) {
                article.setTags(String.join(",", tagNames));
            } else {
                article.setTags("");
            }

            // 仅已发布文章才计入浏览量
            if (isPublishedArticle(article)) {
                articleMapper.incrementViewCount(id);
                article.setViewCount(article.getViewCount() + 1);
                // 更新更新时间（因为incrementViewCount已经更新了数据库的updated_at，需要同步到缓存对象）
                article.setUpdatedAt(LocalDateTime.now());
            }

            // 存入缓存（30分钟）
            redisUtil.set(cacheKey, article, 30, java.util.concurrent.TimeUnit.MINUTES);

            log.info("查询文章详情：id={}，标题={}", id, article.getTitle());
            return Result.success(article);

        } catch (Exception e) {
            log.error("查询文章详情失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }


    /**
     * 发布文章
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<HealthArticle> publishArticle(HealthArticle article) {
        try {
            // 设置初始值
            article.setViewCount(0);
            article.setLikeCount(0);
            article.setFavoriteCount(0);
            article.setCommentCount(0);
            // 用户发布后先进入待审核
            article.setStatus(0); // 待审核
            article.setPublishTime(null);

            articleMapper.insert(article);

            // 查询作者信息并设置作者姓名（用于返回给前端）
            if (article.getAuthorId() != null) {
                User author = userMapper.selectById(article.getAuthorId());
                if (author != null) {
                    article.setAuthorName(author.getRealName() != null ? author.getRealName() : author.getUsername());
                }
            }

            // 保存标签关联（如果tags字段存在）
            if (article.getTags() != null && !article.getTags().trim().isEmpty()) {
                saveArticleTags(article.getId(), article.getTags());
            }

            // 失效文章列表缓存（不删除标签列表缓存，标签缓存为永久缓存）
            redisUtil.deleteByPattern("hospital:common:article:list:*");
            redisUtil.deleteByPattern("hospital:common:article:search:*");

            log.info("发布文章成功：id={}，标题={}", article.getId(), article.getTitle());
            articleNotificationService.createNotificationForAdmins(
                    "收到新文章审核申请",
                    "《" + article.getTitle() + "》已提交审核，请及时处理。",
                    "REVIEW",
                    "ARTICLE",
                    article.getId()
            );
            return Result.success(article);

        } catch (Exception e) {
            log.error("发布文章失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 更新文章
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<HealthArticle> updateArticle(HealthArticle article) {
        try {
            HealthArticle existingArticle = articleMapper.selectById(article.getId());
            if (existingArticle == null) {
                return Result.error(ResultCode.DATA_NOT_FOUND.getCode(), "文章不存在");
            }

            // 只允许作者更新自己的文章
            if (!existingArticle.getAuthorId().equals(article.getAuthorId())) {
                return Result.error(ResultCode.FORBIDDEN.getCode(), "无权限更新此文章");
            }

            // 确保 updated_at 会被更新（自动填充会处理）
            article.setUpdatedAt(null); // 设置为null，让自动填充生效
            articleMapper.updateById(article);

            // 更新标签关联（如果tags字段有变化）
            if (article.getTags() != null) {
                updateArticleTags(article.getId(), article.getTags());
            }

            // 失效相关缓存（不删除标签列表缓存，标签缓存为永久缓存）
            redisUtil.delete("hospital:common:article:detail:id:" + article.getId());
            redisUtil.deleteByPattern("hospital:common:article:list:*");
            redisUtil.deleteByPattern("hospital:common:article:search:*");

            log.info("更新文章成功：id={}，标题={}", article.getId(), article.getTitle());
            return Result.success(article);

        } catch (Exception e) {
            log.error("更新文章失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 删除文章（硬删除：直接删除数据库记录和Redis缓存）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteArticle(Long id, Long userId) {
        try {
            HealthArticle article = articleMapper.selectById(id);
            if (article == null) {
                return Result.error(ResultCode.DATA_NOT_FOUND.getCode(), "文章不存在");
            }

            // 只允许作者删除自己的文章
            if (!article.getAuthorId().equals(userId)) {
                return Result.error(ResultCode.FORBIDDEN.getCode(), "无权限删除此文章");
            }

            // 1. 先查询文章的所有评论ID，用于删除评论的点赞记录
            List<Long> commentIds = null;
            try {
                List<ArticleComment> comments = articleCommentMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ArticleComment>()
                        .eq(ArticleComment::getArticleId, id)
                        .select(ArticleComment::getId)
                );
                if (comments != null && !comments.isEmpty()) {
                    commentIds = comments.stream().map(ArticleComment::getId).collect(java.util.stream.Collectors.toList());
                }
            } catch (Exception e) {
                log.warn("查询文章评论ID失败：文章ID={}，错误={}", id, e.getMessage());
            }

            // 2. 删除文章的所有评论（包括所有状态的评论）
            try {
                int commentCount = articleCommentMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ArticleComment>()
                        .eq(ArticleComment::getArticleId, id)
                );
                log.info("删除文章评论：文章ID={}，共{}条", id, commentCount);
            } catch (Exception e) {
                log.warn("删除文章评论失败：文章ID={}，错误={}", id, e.getMessage());
            }

            // 3. 删除评论的点赞记录
            if (commentIds != null && !commentIds.isEmpty()) {
                try {
                    int commentLikeCount = userLikeMapper.delete(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserLike>()
                            .in(UserLike::getTargetId, commentIds)
                            .eq(UserLike::getTargetType, "COMMENT")
                    );
                    log.info("删除评论点赞记录：文章ID={}，共{}条", id, commentLikeCount);
                } catch (Exception e) {
                    log.warn("删除评论点赞记录失败：文章ID={}，错误={}", id, e.getMessage());
                }
            }

            // 4. 删除文章的所有点赞记录
            try {
                int likeCount = userLikeMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserLike>()
                        .eq(UserLike::getTargetId, id)
                        .eq(UserLike::getTargetType, "ARTICLE")
                );
                log.info("删除文章点赞记录：文章ID={}，共{}条", id, likeCount);
            } catch (Exception e) {
                log.warn("删除文章点赞记录失败：文章ID={}，错误={}", id, e.getMessage());
            }

            // 5. 删除文章的所有收藏记录
            try {
                int favoriteCount = userFavoriteMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getTargetType, "ARTICLE")
                        .eq(UserFavorite::getTargetId, id)
                );
                log.info("删除文章收藏记录：文章ID={}，共{}条", id, favoriteCount);
            } catch (Exception e) {
                log.warn("删除文章收藏记录失败：文章ID={}，错误={}", id, e.getMessage());
            }

            // 6. 将文章标签关联的article_id设置为NULL（软删除，保留标签记录）
            try {
                int tagCount = articleTagMapper.setArticleIdToNull(id);
                log.info("将文章标签关联的article_id设置为NULL：文章ID={}，共{}条", id, tagCount);
            } catch (Exception e) {
                log.warn("设置文章标签关联article_id为NULL失败：文章ID={}，错误={}", id, e.getMessage());
            }

            // 7. 删除文章本身（硬删除）
            articleMapper.deleteById(id);

            // 8. 删除Redis缓存（不删除标签列表缓存，标签缓存为永久缓存）
            try {
                redisUtil.delete("hospital:common:article:detail:id:" + id);
                redisUtil.deleteByPattern("hospital:common:article:list:*");
                redisUtil.deleteByPattern("hospital:common:article:search:*");
                log.info("删除文章Redis缓存：文章ID={}", id);
            } catch (Exception e) {
                log.warn("删除文章Redis缓存失败：文章ID={}，错误={}", id, e.getMessage());
            }

            log.info("删除文章成功（硬删除）：id={}，标题={}", id, article.getTitle());
            return Result.success();

        } catch (Exception e) {
            log.error("删除文章失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 下架文章（软删除：只更新状态为已下架，不删除关联数据）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> offlineArticle(Long id, String reason) {
        try {
            HealthArticle article = articleMapper.selectById(id);
            if (article == null) {
                return Result.error(ResultCode.DATA_NOT_FOUND.getCode(), "文章不存在");
            }

            // 如果已经是已下架状态，直接返回
            if (article.getStatus() == 2) {
                return Result.error(ResultCode.PARAM_ERROR.getCode(), "文章已经是已下架状态");
            }

            // 软删除：更新状态为已下架
            String trimmedReason = reason == null ? "" : reason.trim();
            if (trimmedReason.isEmpty()) {
                return Result.error(ResultCode.PARAM_ERROR.getCode(), "下架原因不能为空");
            }
            if (trimmedReason.length() > 500) {
                trimmedReason = trimmedReason.substring(0, 500);
            }
            article.setStatus(2);
            article.setRejectReason(trimmedReason);
            // 确保 updated_at 会被更新（自动填充会处理）
            article.setUpdatedAt(null); // 设置为null，让自动填充生效
            articleMapper.updateById(article);

            // 失效相关缓存
            try {
                redisUtil.delete("hospital:common:article:detail:id:" + id);
                redisUtil.deleteByPattern("hospital:common:article:list:*");
                redisUtil.deleteByPattern("hospital:common:article:search:*");
                log.info("下架文章，已失效Redis缓存：文章ID={}", id);
            } catch (Exception e) {
                log.warn("下架文章时删除Redis缓存失败：文章ID={}，错误={}", id, e.getMessage());
            }

            log.info("下架文章成功：id={}，标题={}", id, article.getTitle());
            articleNotificationService.createNotificationForUser(
                    article.getAuthorId(),
                    0,
                    "文章已下架",
                    "《" + article.getTitle() + "》已下架，原因：" + trimmedReason,
                    "OFFLINE",
                    "ARTICLE",
                    article.getId()
            );
            return Result.success();

        } catch (Exception e) {
            log.error("下架文章失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 上架文章（将已下架状态改为已发布）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> onlineArticle(Long id) {
        try {
            HealthArticle article = articleMapper.selectById(id);
            if (article == null) {
                return Result.error(ResultCode.DATA_NOT_FOUND.getCode(), "文章不存在");
            }

            // 如果已经是已发布状态，直接返回
            if (article.getStatus() == 1) {
                return Result.error(ResultCode.PARAM_ERROR.getCode(), "文章已经是已发布状态");
            }

            // 上架：更新状态为已发布
            article.setStatus(1);
            article.setRejectReason(null);
            // 确保 updated_at 会被更新（自动填充会处理）
            article.setUpdatedAt(null); // 设置为null，让自动填充生效
            articleMapper.updateById(article);

            // 失效相关缓存
            try {
                redisUtil.delete("hospital:common:article:detail:id:" + id);
                redisUtil.deleteByPattern("hospital:common:article:list:*");
                redisUtil.deleteByPattern("hospital:common:article:search:*");
                log.info("上架文章，已失效Redis缓存：文章ID={}", id);
            } catch (Exception e) {
                log.warn("上架文章时删除Redis缓存失败：文章ID={}，错误={}", id, e.getMessage());
            }

            log.info("上架文章成功：id={}，标题={}", id, article.getTitle());
            return Result.success();

        } catch (Exception e) {
            log.error("上架文章失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 审核文章（通过后自动上线）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> reviewArticle(Long id, Boolean approved, String reason) {
        try {
            HealthArticle article = articleMapper.selectById(id);
            if (article == null) {
                return Result.error(ResultCode.DATA_NOT_FOUND.getCode(), "文章不存在");
            }
            if (approved == null) {
                return Result.error(ResultCode.PARAM_ERROR.getCode(), "审核结果不能为空");
            }

            if (approved) {
                article.setStatus(1);
                article.setRejectReason(null);
                if (article.getPublishTime() == null) {
                    article.setPublishTime(LocalDateTime.now());
                }
                articleNotificationService.createNotificationForUser(
                        article.getAuthorId(),
                        0,
                        "文章审核通过",
                        "《" + article.getTitle() + "》已审核通过并发布。",
                        "REVIEW",
                        "ARTICLE",
                        article.getId()
                );
            } else {
                article.setStatus(3);
                String trimmedReason = reason == null ? "" : reason.trim();
                if (!trimmedReason.isEmpty()) {
                    // 控制长度，避免极端长文本导致DB写入异常
                    if (trimmedReason.length() > 500) {
                        trimmedReason = trimmedReason.substring(0, 500);
                    }
                    article.setRejectReason(trimmedReason);
                } else {
                    article.setRejectReason(null);
                }
                articleNotificationService.createNotificationForUser(
                        article.getAuthorId(),
                        0,
                        "文章审核驳回",
                        "《" + article.getTitle() + "》审核未通过，意见：" + (article.getRejectReason() == null ? "请根据规范修改后重提" : article.getRejectReason()),
                        "REVIEW",
                        "ARTICLE",
                        article.getId()
                );
            }
            article.setUpdatedAt(null);
            articleMapper.updateById(article);

            redisUtil.delete("hospital:common:article:detail:id:" + id);
            redisUtil.deleteByPattern("hospital:common:article:list:*");
            redisUtil.deleteByPattern("hospital:common:article:search:*");

            return Result.success();
        } catch (Exception e) {
            log.error("审核文章失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> setFeatured(Long id, Integer featured) {
        try {
            HealthArticle article = articleMapper.selectById(id);
            if (article == null) {
                return Result.error(ResultCode.DATA_NOT_FOUND.getCode(), "文章不存在");
            }
            if (featured == null || (featured != 0 && featured != 1)) {
                return Result.error(ResultCode.PARAM_ERROR.getCode(), "推荐状态参数错误");
            }
            if (!Integer.valueOf(1).equals(article.getStatus())) {
                return Result.error(ResultCode.PARAM_ERROR.getCode(), "仅已发布文章支持推荐操作");
            }
            article.setIsFeatured(featured);
            article.setUpdatedAt(null);
            articleMapper.updateById(article);

            redisUtil.delete("hospital:common:article:detail:id:" + id);
            redisUtil.deleteByPattern("hospital:common:article:list:*");
            redisUtil.deleteByPattern("hospital:common:article:search:*");
            return Result.success();
        } catch (Exception e) {
            log.error("设置文章推荐状态失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 点赞文章
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> likeArticle(Long articleId, Long userId) {
        try {
            Result<Void> statusCheck = validateArticleCanInteract(articleId);
            if (statusCheck != null) {
                return statusCheck;
            }
            // 检查是否已点赞
            UserLike existingLike = userLikeMapper.selectByUserAndTarget(userId, articleId, "ARTICLE");
            if (existingLike != null) {
                return Result.error(ResultCode.OPERATION_FAILED.getCode(), "已点赞过该文章");
            }

            // 创建点赞记录
            UserLike userLike = new UserLike();
            userLike.setUserId(userId);
            userLike.setTargetId(articleId);
            userLike.setTargetType("ARTICLE");
            userLikeMapper.insert(userLike);

            // 增加文章点赞数
            articleMapper.incrementLikeCount(articleId);

            // 失效相关缓存
            redisUtil.delete("hospital:common:article:detail:id:" + articleId);
            redisUtil.deleteByPattern("hospital:common:article:list:*");
            redisUtil.deleteByPattern("hospital:common:article:search:*");

            log.info("点赞文章成功：文章ID={}，用户ID={}", articleId, userId);
            notifyArticleInteraction(articleId, userId, "点赞");
            return Result.success();

        } catch (Exception e) {
            log.error("点赞文章失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 取消点赞文章
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> unlikeArticle(Long articleId, Long userId) {
        try {
            // 检查是否已点赞
            UserLike existingLike = userLikeMapper.selectByUserAndTarget(userId, articleId, "ARTICLE");
            if (existingLike == null) {
                return Result.error(ResultCode.OPERATION_FAILED.getCode(), "未点赞过该文章");
            }

            // 删除点赞记录
            userLikeMapper.deleteByUserAndTarget(userId, articleId, "ARTICLE");

            // 减少文章点赞数
            articleMapper.decrementLikeCount(articleId);

            // 失效相关缓存
            redisUtil.delete("hospital:common:article:detail:id:" + articleId);
            redisUtil.deleteByPattern("hospital:common:article:list:*");
            redisUtil.deleteByPattern("hospital:common:article:search:*");

            log.info("取消点赞文章成功：文章ID={}，用户ID={}", articleId, userId);
            return Result.success();

        } catch (Exception e) {
            log.error("取消点赞文章失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 收藏文章
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> favoriteArticle(Long articleId, Long userId, String remark) {
        try {
            Result<Void> statusCheck = validateArticleCanInteract(articleId);
            if (statusCheck != null) {
                return statusCheck;
            }
            // 检查是否已收藏
            UserFavorite existingFavorite = userFavoriteMapper.selectByUserAndTarget(userId, "ARTICLE", articleId);
            if (existingFavorite != null) {
                return Result.error(ResultCode.OPERATION_FAILED.getCode(), "已收藏过该文章");
            }

            // 创建收藏记录
            UserFavorite favorite = new UserFavorite();
            favorite.setUserId(userId);
            favorite.setTargetType("ARTICLE");
            favorite.setTargetId(articleId);
            favorite.setRemark(remark);
            userFavoriteMapper.insert(favorite);

            // 增加文章收藏数
            articleMapper.incrementFavoriteCount(articleId);

            // 失效相关缓存
            redisUtil.delete("hospital:common:article:detail:id:" + articleId);
            redisUtil.deleteByPattern("hospital:common:article:list:*");
            redisUtil.deleteByPattern("hospital:common:article:search:*");

            log.info("收藏文章成功：文章ID={}，用户ID={}", articleId, userId);
            notifyArticleInteraction(articleId, userId, "收藏");
            return Result.success();

        } catch (Exception e) {
            log.error("收藏文章失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 取消收藏文章
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> unfavoriteArticle(Long articleId, Long userId) {
        try {
            // 检查是否已收藏
            UserFavorite existingFavorite = userFavoriteMapper.selectByUserAndTarget(userId, "ARTICLE", articleId);
            if (existingFavorite == null) {
                return Result.error(ResultCode.OPERATION_FAILED.getCode(), "未收藏过该文章");
            }

            // 删除收藏记录
            userFavoriteMapper.deleteByUserAndTarget(userId, "ARTICLE", articleId);

            // 减少文章收藏数
            articleMapper.decrementFavoriteCount(articleId);

            // 失效相关缓存
            redisUtil.delete("hospital:common:article:detail:id:" + articleId);
            redisUtil.deleteByPattern("hospital:common:article:list:*");
            redisUtil.deleteByPattern("hospital:common:article:search:*");

            log.info("取消收藏文章成功：文章ID={}，用户ID={}", articleId, userId);
            return Result.success();

        } catch (Exception e) {
            log.error("取消收藏文章失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 获取文章状态（点赞和收藏状态）
     */
    @Override
    public Result<java.util.Map<String, Boolean>> getArticleStatus(Long articleId, Long userId) {
        try {
            java.util.Map<String, Boolean> status = new java.util.HashMap<>();
            
            // 检查点赞状态
            UserLike existingLike = userLikeMapper.selectByUserAndTarget(userId, articleId, "ARTICLE");
            status.put("liked", existingLike != null);
            
            // 检查收藏状态
            UserFavorite existingFavorite = userFavoriteMapper.selectByUserAndTarget(userId, "ARTICLE", articleId);
            status.put("favorited", existingFavorite != null);
            
            log.info("查询文章状态：文章ID={}，用户ID={}，已点赞={}，已收藏={}", 
                    articleId, userId, status.get("liked"), status.get("favorited"));
            return Result.success(status);

        } catch (Exception e) {
            log.error("查询文章状态失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 获取所有标签列表（用于筛选和选择）
     * 从article_tag表中查询所有不重复的标签名称
     */
    @Override
    public Result<List<String>> getAllTags() {
        try {
            String cacheKey = "hospital:common:article:tags:all";

            // 尝试从缓存获取
            Object cached = redisUtil.get(cacheKey);
            if (cached instanceof List) {
                try {
                    @SuppressWarnings("unchecked")
                    List<String> tags = (List<String>) cached;
                    return Result.success(tags);
                } catch (ClassCastException ignored) {}
            }

            // 从article_tag表查询所有不重复的标签名称（只查询article_id不为NULL的记录）
            List<String> tagList = articleTagMapper.selectAllDistinctTagNames();
            
            // 过滤空标签并排序
            if (tagList != null) {
                tagList = tagList.stream()
                        .filter(tag -> tag != null && !tag.trim().isEmpty())
                        .map(String::trim)
                        .sorted()
                        .collect(java.util.stream.Collectors.toList());
            } else {
                tagList = new java.util.ArrayList<>();
            }

            // 存入缓存（永久缓存，TTL=-1）
            redisUtil.set(cacheKey, tagList);

            return Result.success(tagList);

        } catch (Exception e) {
            log.error("获取所有标签失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 保存文章标签关联（用于发布文章时）
     * 如果标签已存在（article_id为NULL），则更新article_id；如果不存在，创建新的标签关联
     *
     * @param articleId 文章ID
     * @param tagsStr 标签字符串（逗号分隔）
     */
    private void saveArticleTags(Long articleId, String tagsStr) {
        try {
            if (tagsStr == null || tagsStr.trim().isEmpty()) {
                return;
            }

            // 解析标签字符串
            String[] tagArray = tagsStr.split(",");
            List<ArticleTag> newArticleTags = new java.util.ArrayList<>();
            int updatedCount = 0;

            for (String tag : tagArray) {
                String trimmedTag = tag.trim();
                if (!trimmedTag.isEmpty()) {
                    // 1. 先检查该标签是否已经与该文章关联（article_id不为NULL）
                    List<ArticleTag> existingTags = articleTagMapper.selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ArticleTag>()
                                    .eq(ArticleTag::getArticleId, articleId)
                                    .eq(ArticleTag::getTagName, trimmedTag)
                    );

                    // 如果已存在关联，跳过
                    if (existingTags != null && !existingTags.isEmpty()) {
                        continue;
                    }

                    // 2. 检查是否存在该标签但article_id为NULL的记录（可复用的标签）
                    List<ArticleTag> nullArticleIdTags = articleTagMapper.selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ArticleTag>()
                                    .isNull(ArticleTag::getArticleId)
                                    .eq(ArticleTag::getTagName, trimmedTag)
                                    .last("LIMIT 1")
                    );

                    if (nullArticleIdTags != null && !nullArticleIdTags.isEmpty()) {
                        // 如果存在article_id为NULL的记录，更新它
                        ArticleTag existingTag = nullArticleIdTags.get(0);
                        existingTag.setArticleId(articleId);
                        articleTagMapper.updateById(existingTag);
                        updatedCount++;
                    } else {
                        // 如果不存在，创建新的标签关联
                        ArticleTag articleTag = new ArticleTag();
                        articleTag.setArticleId(articleId);
                        articleTag.setTagName(trimmedTag);
                        newArticleTags.add(articleTag);
                    }
                }
            }

            // 批量插入新标签关联
            if (!newArticleTags.isEmpty()) {
                articleTagMapper.insertBatch(newArticleTags);
            }

            log.info("保存文章标签关联成功：文章ID={}，新建{}条，复用{}条", articleId, newArticleTags.size(), updatedCount);
        } catch (Exception e) {
            log.error("保存文章标签关联失败：文章ID={}，错误={}", articleId, e.getMessage(), e);
            throw e; // 抛出异常以便事务回滚
        }
    }

    /**
     * 更新文章标签关联（用于更新文章时）
     * 步骤：1. 先将原来的标签的article_id设置为NULL
     *       2. 然后创建新的标签关联
     *
     * @param articleId 文章ID
     * @param tagsStr 标签字符串（逗号分隔）
     */
    private void updateArticleTags(Long articleId, String tagsStr) {
        try {
            // 1. 先将原来的标签的article_id设置为NULL（软删除，保留标签记录）
            articleTagMapper.setArticleIdToNull(articleId);

            // 2. 如果新标签不为空，则创建新的标签关联
            if (tagsStr != null && !tagsStr.trim().isEmpty()) {
                saveArticleTags(articleId, tagsStr);
            }

            // 不删除标签列表缓存，标签缓存为永久缓存，不受增删改操作影响

            log.info("更新文章标签关联成功：文章ID={}", articleId);
        } catch (Exception e) {
            log.error("更新文章标签关联失败：文章ID={}，错误={}", articleId, e.getMessage(), e);
            throw e; // 抛出异常以便事务回滚
        }
    }

    private Result<Void> validateArticleCanInteract(Long articleId) {
        HealthArticle article = articleMapper.selectById(articleId);
        if (article == null) {
            return Result.error(ResultCode.DATA_NOT_FOUND.getCode(), "文章不存在");
        }
        if (!isPublishedArticle(article)) {
            return Result.error(ResultCode.OPERATION_FAILED.getCode(), "待审核、已驳回或已下架文章不支持点赞/收藏");
        }
        return null;
    }

    private boolean isPublishedArticle(HealthArticle article) {
        return article != null && Integer.valueOf(ARTICLE_STATUS_PUBLISHED).equals(article.getStatus());
    }

    private void notifyArticleInteraction(Long articleId, Long actorUserId, String action) {
        try {
            HealthArticle article = articleMapper.selectById(articleId);
            if (article == null || article.getAuthorId() == null || article.getAuthorId().equals(actorUserId)) {
                return;
            }
            User actor = userMapper.selectById(actorUserId);
            String actorName = actor == null ? "有用户" : (actor.getRealName() == null ? actor.getUsername() : actor.getRealName());
            articleNotificationService.createNotificationForUser(
                    article.getAuthorId(),
                    0,
                    "文章互动提醒",
                    actorName + action + "了你的文章《" + article.getTitle() + "》。",
                    "INTERACTION",
                    "ARTICLE",
                    articleId
            );
        } catch (Exception ignored) {
        }
    }

}

