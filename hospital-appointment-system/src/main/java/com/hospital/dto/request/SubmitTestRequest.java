package com.hospital.dto.request;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * 提交体质测试请求DTO
 *
 * @author TCM Health Team
 * @since 2025-11-03
 */
@Data
public class SubmitTestRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    // 问卷体系已下线：移除 answers/questionIds 字段

    /**
     * 舌诊分析结果（可选，用于多模态融合辨识）
     * 格式：JSON字符串或体质类型代码
     */
    private String tongueResult;

    /**
     * 用户主观情况描述（可选）：在舌诊完成后的自述，如近期不适、睡眠、二便、寒热等，供 AI 与舌象、体质分数、健康档案一并参考。
     */
    private String userSelfDescription;
}

