package com.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.entity.HealthArticle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 养生文章Mapper接口
 *
 * @author TCM Health Team
 * @date 2025-11-03
 */
@Mapper
public interface HealthArticleMapper extends BaseMapper<HealthArticle> {

    /**
     * 分页查询文章列表
     *
     * @param page 分页对象
     * @param category 分类（可选）
     * @param constitutionType 体质类型（可选）
     * @param tags 标签列表（可选，支持多个标签）
     * @param isFeatured 是否精选（可选）
     * @return 文章列表
     */
    IPage<HealthArticle> selectArticlePage(Page<HealthArticle> page,
                                           @Param("category") String category,
                                           @Param("constitutionType") String constitutionType,
                                           @Param("tags") List<String> tags,
                                           @Param("isFeatured") Integer isFeatured);

    /**
     * 搜索文章（按标题、摘要、标签）
     *
     * @param page 分页对象
     * @param keyword 关键词
     * @return 文章列表
     */
    IPage<HealthArticle> searchArticles(Page<HealthArticle> page, @Param("keyword") String keyword);

    /**
     * 根据作者ID查询文章
     *
     * @param page 分页对象
     * @param authorId 作者ID
     * @return 文章列表
     */
    IPage<HealthArticle> selectByAuthorId(Page<HealthArticle> page, @Param("authorId") Long authorId);

    /**
     * 获取精选文章
     *
     * @param limit 数量限制
     * @return 文章列表
     */
    List<HealthArticle> selectRecommendedArticles(@Param("limit") Integer limit);

    /**
     * 获取热门文章
     *
     * @param limit 数量限制
     * @return 文章列表
     */
    List<HealthArticle> selectPopularArticles(@Param("limit") Integer limit);

    /**
     * 增加浏览次数
     *
     * @param id 文章ID
     */
    void incrementViewCount(@Param("id") Long id);

    /**
     * 增加点赞次数
     *
     * @param id 文章ID
     */
    void incrementLikeCount(@Param("id") Long id);

    /**
     * 减少点赞次数
     *
     * @param id 文章ID
     */
    void decrementLikeCount(@Param("id") Long id);

    /**
     * 增加收藏次数
     *
     * @param id 文章ID
     */
    void incrementFavoriteCount(@Param("id") Long id);

    /**
     * 减少收藏次数
     *
     * @param id 文章ID
     */
    void decrementFavoriteCount(@Param("id") Long id);

    /**
     * 增加评论次数
     *
     * @param id 文章ID
     */
    void incrementCommentCount(@Param("id") Long id);

    /**
     * 减少评论次数
     *
     * @param id 文章ID
     */
    void decrementCommentCount(@Param("id") Long id);

    /**
     * 管理员分页查询文章列表（支持按状态筛选和所有筛选条件）
     *
     * @param page 分页对象
     * @param category 分类（可选）
     * @param constitutionType 体质类型（可选）
     * @param tags 标签列表（可选）
     * @param isFeatured 是否精选（可选）
     * @param status 状态（可选，0-草稿 1-已发布 2-已下架，null表示查询所有状态）
     * @param keyword 搜索关键词（可选，按标题、摘要、标签搜索）
     * @return 文章列表
     */
    IPage<HealthArticle> selectArticlePageForAdmin(Page<HealthArticle> page,
                                                    @Param("category") String category,
                                                    @Param("constitutionType") String constitutionType,
                                                    @Param("tags") List<String> tags,
                                                    @Param("isFeatured") Integer isFeatured,
                                                    @Param("status") Integer status,
                                                    @Param("keyword") String keyword);
}

