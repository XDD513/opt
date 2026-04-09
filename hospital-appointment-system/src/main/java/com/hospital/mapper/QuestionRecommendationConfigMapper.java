package com.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.entity.QuestionRecommendationConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 题目推荐配置Mapper接口
 *
 * @author Hospital Team
 * @since 2025-12-09
 */
@Mapper
public interface QuestionRecommendationConfigMapper extends BaseMapper<QuestionRecommendationConfig> {

    /**
     * 根据题目ID查询所有启用的配置
     *
     * @param questionId 题目ID
     * @return 配置列表
     */
    @Select("SELECT * FROM question_recommendation_config " +
            "WHERE question_id = #{questionId} AND is_enabled = 1")
    List<QuestionRecommendationConfig> selectByQuestionId(@Param("questionId") Long questionId);

    /**
     * 根据科室ID和症状关键词查询推荐配置
     *
     * @param departmentId 科室ID
     * @param symptomKeyword 症状关键词（模糊匹配）
     * @return 配置列表
     */
    @Select("<script>" +
            "SELECT * FROM question_recommendation_config " +
            "WHERE is_enabled = 1 " +
            "<if test='departmentId != null'>" +
            "AND (department_id = #{departmentId} OR department_id IS NULL) " +
            "</if>" +
            "<if test='symptomKeyword != null and symptomKeyword != \"\"'>" +
            "AND (" +
            "  symptom_keyword LIKE CONCAT('%', #{symptomKeyword}, '%') " +
            "  OR #{symptomKeyword} LIKE CONCAT('%', symptom_keyword, '%') " +
            "  OR symptom_keyword IS NULL " +
            ") " +
            "</if>" +
            "ORDER BY " +
            "  CASE WHEN department_id = #{departmentId} THEN 1 ELSE 2 END, " +
            "  CASE WHEN symptom_keyword IS NOT NULL AND symptom_keyword != '' AND " +
            "       (symptom_keyword LIKE CONCAT('%', #{symptomKeyword}, '%') OR #{symptomKeyword} LIKE CONCAT('%', symptom_keyword, '%')) " +
            "       THEN 1 ELSE 2 END, " +
            "  priority DESC, " +
            "  recommend_weight DESC " +
            "LIMIT 100" +
            "</script>")
    List<QuestionRecommendationConfig> selectByDepartmentAndSymptom(
            @Param("departmentId") Long departmentId,
            @Param("symptomKeyword") String symptomKeyword);
}


