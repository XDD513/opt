package com.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.entity.UserFavorite;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户收藏Mapper接口（统一收藏表）
 *
 * @author Hospital Team
 * @since 2025-12-09
 */
@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {

    /**
     * 查询用户是否已收藏
     *
     * @param userId 用户ID
     * @param targetType 目标类型
     * @param targetId 目标ID
     * @return 收藏记录
     */
    @Select("SELECT * FROM user_favorite WHERE user_id = #{userId} AND target_type = #{targetType} AND target_id = #{targetId}")
    UserFavorite selectByUserAndTarget(@Param("userId") Long userId,
                                       @Param("targetType") String targetType,
                                       @Param("targetId") Long targetId);

    /**
     * 分页查询用户收藏的文章
     *
     * @param page 分页对象
     * @param userId 用户ID
     * @return 收藏列表
     */
    @Select("SELECT uf.*, ha.title, ha.summary, ha.cover_image, ha.category, " +
            "COALESCE(u.real_name, u.username) AS author_name, " +
            "ha.view_count, ha.like_count, ha.favorite_count " +
            "FROM user_favorite uf " +
            "LEFT JOIN content_article ha ON uf.target_id = ha.id AND uf.target_type = 'ARTICLE' " +
            "LEFT JOIN tcm_user u ON ha.author_id = u.id " +
            "WHERE uf.user_id = #{userId} AND uf.target_type = 'ARTICLE' AND ha.status = 1 " +
            "ORDER BY uf.created_at DESC")
    IPage<UserFavorite> selectArticleFavoritesByUserId(Page<UserFavorite> page, @Param("userId") Long userId);

    /**
     * 分页查询用户收藏的药膳
     *
     * @param page 分页对象
     * @param userId 用户ID
     * @return 收藏列表
     */
    @Select("SELECT uf.*, r.recipe_name, r.category as recipe_category, r.difficulty, r.cooking_time, " +
            "r.efficacy, r.image as recipe_image, r.view_count as recipe_view_count, r.favorite_count as recipe_favorite_count " +
            "FROM user_favorite uf " +
            "LEFT JOIN tcm_herbal_recipe r ON uf.target_id = r.id AND uf.target_type = 'RECIPE' " +
            "WHERE uf.user_id = #{userId} AND uf.target_type = 'RECIPE' AND r.status = 1 " +
            "ORDER BY uf.created_at DESC")
    IPage<UserFavorite> selectRecipeFavoritesByUserId(Page<UserFavorite> page, @Param("userId") Long userId);

    /**
     * 删除收藏记录
     *
     * @param userId 用户ID
     * @param targetType 目标类型
     * @param targetId 目标ID
     */
    @Delete("DELETE FROM user_favorite WHERE user_id = #{userId} AND target_type = #{targetType} AND target_id = #{targetId}")
    void deleteByUserAndTarget(@Param("userId") Long userId,
                               @Param("targetType") String targetType,
                               @Param("targetId") Long targetId);

    /**
     * 统计用户收藏数量
     *
     * @param userId 用户ID
     * @param targetType 目标类型（可选，null表示统计所有类型）
     * @return 收藏数量
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM user_favorite WHERE user_id = #{userId} " +
            "<if test='targetType != null'> AND target_type = #{targetType} </if>" +
            "</script>")
    Integer countByUserId(@Param("userId") Long userId, @Param("targetType") String targetType);

    /**
     * 查询用户收藏的全部目标ID列表
     *
     * @param userId 用户ID
     * @param targetType 目标类型
     * @return 目标ID列表
     */
    @Select("SELECT target_id FROM user_favorite WHERE user_id = #{userId} AND target_type = #{targetType}")
    List<Long> selectTargetIdsByUserId(@Param("userId") Long userId, @Param("targetType") String targetType);
}

