package com.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.entity.ArticleTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文章标签关联Mapper接口
 *
 * @author TCM Health Team
 * @date 2025-12-21
 */
@Mapper
public interface ArticleTagMapper extends BaseMapper<ArticleTag> {

    /**
     * 根据文章ID查询所有标签
     *
     * @param articleId 文章ID
     * @return 标签列表
     */
    List<String> selectTagNamesByArticleId(@Param("articleId") Long articleId);

    /**
     * 根据标签名称查询所有文章ID
     *
     * @param tagName 标签名称
     * @return 文章ID列表
     */
    List<Long> selectArticleIdsByTagName(@Param("tagName") String tagName);

    /**
     * 批量插入文章标签关联
     *
     * @param articleTags 文章标签关联列表
     * @return 插入条数
     */
    int insertBatch(@Param("list") List<ArticleTag> articleTags);

    /**
     * 根据文章ID将article_id设置为NULL（软删除，保留标签记录）
     *
     * @param articleId 文章ID
     * @return 更新条数
     */
    int setArticleIdToNull(@Param("articleId") Long articleId);

    /**
     * 根据文章ID和标签名称删除关联
     *
     * @param articleId 文章ID
     * @param tagName 标签名称
     * @return 删除条数
     */
    int deleteByArticleIdAndTagName(@Param("articleId") Long articleId, @Param("tagName") String tagName);

    /**
     * 查询所有不重复的标签名称
     *
     * @return 标签名称列表
     */
    List<String> selectAllDistinctTagNames();
}

