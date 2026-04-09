package com.hospital.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hospital.common.result.Result;
import com.hospital.entity.HealthArticle;

import java.util.List;

/**
 * 养生文章服务接口
 *
 * @author TCM Health Team
 * @date 2025-11-03
 */
public interface HealthArticleService {

    /**
     * 统一的分页查询文章列表接口（支持所有查询场景）
     *
     * @param category 分类（可选）
     * @param constitutionType 体质类型（可选）
     * @param tags 标签（可选，支持多个标签，逗号分隔）
     * @param isFeatured 是否精选（可选）
     * @param status 状态（可选，0-草稿 1-已发布 2-已下架，null表示已发布，管理员可查询所有状态）
     * @param keyword 搜索关键词（可选，按标题、摘要、标签搜索）
     * @param userId 用户ID（可选，用于查询我的文章或收藏文章）
     * @param type 查询类型（可选，my-我的文章，favorites-收藏文章，recommended-精选，popular-热门）
     * @param isAdmin 是否管理员查询（可选，true表示管理员查询，即使status为null也查询所有状态）
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 文章列表
     */
    Result<IPage<HealthArticle>> getArticleList(String category, String constitutionType, String tags, 
                                                Integer isFeatured, Integer status, String keyword, 
                                                Long userId, String type, Boolean isAdmin, Integer pageNum, Integer pageSize);

    /**
     * 获取文章详情
     *
     * @param id 文章ID
     * @return 文章详情
     */
    Result<HealthArticle> getArticleDetail(Long id);

    /**
     * 发布文章
     *
     * @param article 文章信息
     * @return 发布结果
     */
    Result<HealthArticle> publishArticle(HealthArticle article);

    /**
     * 更新文章
     *
     * @param article 文章信息
     * @return 更新结果
     */
    Result<HealthArticle> updateArticle(HealthArticle article);

    /**
     * 删除文章
     *
     * @param id 文章ID
     * @param userId 用户ID
     * @return 删除结果
     */
    Result<Void> deleteArticle(Long id, Long userId);

    /**
     * 点赞文章
     *
     * @param articleId 文章ID
     * @param userId 用户ID
     * @return 点赞结果
     */
    Result<Void> likeArticle(Long articleId, Long userId);

    /**
     * 取消点赞文章
     *
     * @param articleId 文章ID
     * @param userId 用户ID
     * @return 取消点赞结果
     */
    Result<Void> unlikeArticle(Long articleId, Long userId);

    /**
     * 收藏文章
     *
     * @param articleId 文章ID
     * @param userId 用户ID
     * @param remark 备注
     * @return 收藏结果
     */
    Result<Void> favoriteArticle(Long articleId, Long userId, String remark);

    /**
     * 取消收藏文章
     *
     * @param articleId 文章ID
     * @param userId 用户ID
     * @return 取消收藏结果
     */
    Result<Void> unfavoriteArticle(Long articleId, Long userId);

    /**
     * 获取文章状态（点赞和收藏状态）
     *
     * @param articleId 文章ID
     * @param userId 用户ID
     * @return 状态信息（包含liked和favorited）
     */
    Result<java.util.Map<String, Boolean>> getArticleStatus(Long articleId, Long userId);

    /**
     * 获取所有标签列表（用于筛选）
     *
     * @return 标签列表
     */
    Result<List<String>> getAllTags();

    /**
     * 下架文章（软删除：只更新状态，不删除关联数据）
     *
     * @param id 文章ID
     * @param reason 下架原因
     * @return 下架结果
     */
    Result<Void> offlineArticle(Long id, String reason);

    /**
     * 上架文章（将已下架状态改为已发布）
     *
     * @param id 文章ID
     * @return 上架结果
     */
    Result<Void> onlineArticle(Long id);

    /**
     * 审核文章（通过后上线，不通过则驳回）
     *
     * @param id 文章ID
     * @param approved 是否通过审核
     * @param reason 驳回原因（approved=false时可填）
     * @return 审核结果
     */
    Result<Void> reviewArticle(Long id, Boolean approved, String reason);

    /**
     * 设置文章推荐状态（管理员）
     *
     * @param id 文章ID
     * @param featured 是否推荐（0/1）
     * @return 处理结果
     */
    Result<Void> setFeatured(Long id, Integer featured);
}

