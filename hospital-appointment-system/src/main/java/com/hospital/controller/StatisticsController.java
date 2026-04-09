package com.hospital.controller;

import com.hospital.common.result.Result;
import com.hospital.dto.StatisticsDTO;
import com.hospital.entity.User;
import com.hospital.entity.UserConstitutionTest;
import com.hospital.mapper.UserConstitutionTestMapper;
import com.hospital.mapper.UserMapper;
import com.hospital.service.StatisticsService;
import com.hospital.util.JwtUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 统计数据控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserConstitutionTestMapper userConstitutionTestMapper;

    /**
     * 获取管理员统计数据
     */
    @GetMapping("/admin")
    public Result<StatisticsDTO.AdminStats> getAdminStats(HttpServletRequest request) {
        // 验证管理员权限
        Integer roleType = jwtUtil.getRoleTypeFromRequest(request);
        if (roleType == null || roleType != 1) {
            return Result.error(403, "权限不足，仅管理员可访问");
        }
        
        StatisticsDTO.AdminStats stats = statisticsService.getAdminStats();
        return Result.success(stats);
    }

    /**
     * 管理员：用户体质测试统计（覆盖面、完成率、主要体质分布、最近30天趋势）
     */
    @GetMapping("/admin/user-test")
    public Result<StatisticsDTO.AdminUserTestStats> getAdminUserTestStats(HttpServletRequest request) {
        Integer roleType = jwtUtil.getRoleTypeFromRequest(request);
        if (roleType == null || roleType != 1) {
            return Result.error(403, "权限不足，仅管理员可访问");
        }

        // 1) 总患者人数：roleType=0/1
        long totalPatients = userMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                        .in("role_type", Arrays.asList(0, 1))
        );

        // 2) 测试记录（用于：去重用户数、主要体质分布、趋势）
        List<UserConstitutionTest> allTests = userConstitutionTestMapper.selectList(null);

        Set<Long> testedUserIds = allTests.stream()
                .map(UserConstitutionTest::getUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        int testedPatients = testedUserIds.size();
        int totalTestRecords = allTests.size();

        double completionRate = totalPatients > 0 ? ((double) testedPatients / (double) totalPatients) * 100.0 : 0.0;

        // 3) 主要体质分布：primaryConstitution -> 次数
        Map<String, Integer> primaryConstitutionCounts = allTests.stream()
                .map(UserConstitutionTest::getPrimaryConstitution)
                .filter(code -> code != null && !code.trim().isEmpty())
                .collect(Collectors.toMap(
                        code -> code,
                        code -> 1,
                        Integer::sum
                ));

        // 4) 最近30天趋势（按测试日期聚合）
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(29);

        Map<String, Integer> trendMap = new HashMap<>();
        for (int i = 0; i < 30; i++) {
            LocalDate d = startDate.plusDays(i);
            trendMap.put(d.toString(), 0);
        }

        for (UserConstitutionTest t : allTests) {
            if (t.getTestDate() == null) continue;
            LocalDate d = t.getTestDate().toLocalDate();
            if ((d.isEqual(startDate) || d.isAfter(startDate)) && (d.isEqual(endDate) || d.isBefore(endDate))) {
                String key = d.toString();
                trendMap.put(key, trendMap.getOrDefault(key, 0) + 1);
            }
        }

        List<StatisticsDTO.UserTestTrendData> last30DaysTrend = trendMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    StatisticsDTO.UserTestTrendData point = new StatisticsDTO.UserTestTrendData();
                    point.setDate(e.getKey());
                    point.setTestCount(e.getValue());
                    return point;
                })
                .collect(Collectors.toList());

        // 5) 体质测试结果：推荐穴位/宜食/忌食（从每条 testResult 的 aiSuggestion 中解析）
        Map<String, Integer> acupointAppearCounts = new HashMap<>();
        Map<String, Integer> dietRecommendAppearCounts = new HashMap<>();
        Map<String, Integer> dietAvoidAppearCounts = new HashMap<>();

        for (UserConstitutionTest t : allTests) {
            try {
                if (t.getTestResult() == null || t.getTestResult().isBlank()) continue;
                JSONObject testRoot = JSONUtil.parseObj(t.getTestResult());
                String aiSuggestionRaw = testRoot.getStr("aiSuggestion");
                if (aiSuggestionRaw == null || aiSuggestionRaw.isBlank()) continue;

                String aiClean = stripMarkdownJsonFence(aiSuggestionRaw);
                if (aiClean == null || aiClean.isBlank()) continue;

                JSONObject aiJson = JSONUtil.parseObj(aiClean);

                // 穴位：按“每条测试记录出现一次”计数
                Set<String> acupointNames = new java.util.HashSet<>();
                JSONArray acupoints = aiJson.getJSONArray("acupoints");
                if (acupoints != null) {
                    for (Object item : acupoints) {
                        if (item == null) continue;
                        if (item instanceof JSONObject) {
                            String name = ((JSONObject) item).getStr("name");
                            if (name != null && !name.trim().isEmpty()) acupointNames.add(name.trim());
                        } else {
                            String name = String.valueOf(item).trim();
                            if (!name.isEmpty()) acupointNames.add(name);
                        }
                    }
                }
                for (String name : acupointNames) {
                    acupointAppearCounts.merge(name, 1, Integer::sum);
                }

                // 饮食：diet.recommend / diet.avoid 按“每条测试记录出现一次”计数
                JSONObject diet = aiJson.getJSONObject("diet");
                if (diet != null) {
                    Set<String> recommendFoods = new java.util.HashSet<>();
                    Set<String> avoidFoods = new java.util.HashSet<>();

                    JSONArray recommendArr = diet.getJSONArray("recommend");
                    if (recommendArr != null) {
                        for (Object item : recommendArr) {
                            if (item == null) continue;
                            String food = String.valueOf(item).trim();
                            if (!food.isEmpty()) recommendFoods.add(food);
                        }
                    }

                    JSONArray avoidArr = diet.getJSONArray("avoid");
                    if (avoidArr != null) {
                        for (Object item : avoidArr) {
                            if (item == null) continue;
                            String food = String.valueOf(item).trim();
                            if (!food.isEmpty()) avoidFoods.add(food);
                        }
                    }

                    for (String food : recommendFoods) {
                        dietRecommendAppearCounts.merge(food, 1, Integer::sum);
                    }
                    for (String food : avoidFoods) {
                        dietAvoidAppearCounts.merge(food, 1, Integer::sum);
                    }
                }
            } catch (Exception ignore) {
                // 单条记录解析失败不影响整体统计
            }
        }

        StatisticsDTO.AdminUserTestStats stats = new StatisticsDTO.AdminUserTestStats();
        stats.setTotalPatients((int) totalPatients);
        stats.setTestedPatients(testedPatients);
        stats.setTotalTestRecords(totalTestRecords);
        stats.setCompletionRate(completionRate);
        stats.setPrimaryConstitutionCounts(primaryConstitutionCounts);
        stats.setLast30DaysTrend(last30DaysTrend);

        // Top N 返回给前端展示
        stats.setTopAcupoints(toTopItemStats(acupointAppearCounts, 10));
        stats.setTopDietRecommend(toTopItemStats(dietRecommendAppearCounts, 10));
        stats.setTopDietAvoid(toTopItemStats(dietAvoidAppearCounts, 10));

        return Result.success(stats);
    }

    private static String stripMarkdownJsonFence(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (!t.contains("```")) return t;
        int start = t.indexOf("```");
        int end = t.lastIndexOf("```");
        if (start < 0 || end <= start) return t;

        String inner = t.substring(start + 3, end).trim();
        // 可能是 ```json\n{...}
        if (inner.startsWith("json")) {
            inner = inner.substring(4).trim();
        }
        return inner;
    }

    private static List<StatisticsDTO.TopItemStat> toTopItemStats(Map<String, Integer> counts, int topN) {
        if (counts == null || counts.isEmpty() || topN <= 0) return Collections.emptyList();

        return counts.entrySet()
                .stream()
                .sorted((a, b) -> {
                    int cmp = Integer.compare(b.getValue(), a.getValue());
                    return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
                })
                .limit(topN)
                .map(e -> {
                    StatisticsDTO.TopItemStat s = new StatisticsDTO.TopItemStat();
                    s.setItem(e.getKey());
                    s.setCount(e.getValue());
                    return s;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取患者统计数据
     */
    @GetMapping("/patient")
    public Result<StatisticsDTO.PatientStats> getPatientStats(HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        StatisticsDTO.PatientStats stats = statisticsService.getPatientStats(userId);
        return Result.success(stats);
    }
}
