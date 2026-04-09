package com.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hospital.entity.SymptomKeyword;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 症状关键词Mapper接口
 *
 * @author Hospital Team
 * @since 2025-12-09
 */
@Mapper
public interface SymptomKeywordMapper extends BaseMapper<SymptomKeyword> {

    /**
     * 根据关键词查询（模糊匹配）
     *
     * @param keyword 关键词
     * @return 症状关键词列表
     */
    @Select("SELECT * FROM symptom_keyword WHERE keyword LIKE CONCAT('%', #{keyword}, '%') ORDER BY weight DESC, usage_count DESC")
    List<SymptomKeyword> selectByKeyword(@Param("keyword") String keyword);

    /**
     * 查询所有启用的关键词（按权重和使用次数排序）
     *
     * @return 症状关键词列表
     */
    @Select("SELECT * FROM symptom_keyword ORDER BY weight DESC, usage_count DESC")
    List<SymptomKeyword> selectAll();

    /**
     * 增加关键词使用次数
     *
     * @param keyword 关键词
     * @return 更新行数
     */
    @Update("UPDATE symptom_keyword SET usage_count = usage_count + 1, updated_at = NOW() WHERE keyword = #{keyword}")
    int incrementUsageCount(@Param("keyword") String keyword);

    /**
     * 批量增加关键词使用次数（通过循环调用单个更新实现）
     * 注意：这个方法在 Service 层通过循环调用 incrementUsageCount 实现
     */
}

