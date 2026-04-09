package com.hospital.controller;

import com.hospital.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/migration")
public class MigrationController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/add-test-id")
    public Result<String> addTestIdColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE tcm_health_plan_record ADD COLUMN test_id BIGINT COMMENT '关联的体质测试ID'");
            jdbcTemplate.execute("CREATE INDEX idx_health_plan_test_id ON tcm_health_plan_record(test_id)");
            return Result.success("Successfully added test_id column");
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }
}
