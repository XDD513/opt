package com.hospital.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 预约统计响应 DTO
 *
 * 说明：当前项目已下线预约模块接口，但后端仍可能存在历史残留引用，
 * 该类用于保证编译通过；业务层不再使用该 DTO。
 */
@Data
public class PatientAppointmentStatsResponse implements Serializable {
    private static final long serialVersionUID = 1L;
}

