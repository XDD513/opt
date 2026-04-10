package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.result.Result;
import com.hospital.common.result.ResultCode;
import com.hospital.entity.*;
import com.hospital.mapper.*;
import com.hospital.service.HealthProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

/**
 * 健康档案服务实现类
 *
 * @author TCM Health Team
 * @date 2025-11-03
 */
@Slf4j
@Service
public class HealthProfileServiceImpl implements HealthProfileService {

    @Autowired
    private UserHealthProfileMapper healthProfileMapper;

    @Autowired
    private HealthPlanRecordMapper healthPlanMapper;

    @Autowired
    private HealthCheckinMapper healthCheckinMapper;

    @Autowired
    private UserConstitutionTestMapper constitutionTestMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private com.hospital.util.RedisUtil redisUtil;

    /**
     * 获取用户健康档案
     */
    @Override
    public Result<Map<String, Object>> getHealthProfile(Long userId) {
        try {
            // 1. 尝试从缓存获取
            String cacheKey = "hospital:patient:health:profile:user:" + userId;
            Object cached = redisUtil.get(cacheKey);
            if (cached instanceof Map) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> cachedProfile = (Map<String, Object>) cached;
                    return Result.success(cachedProfile);
                } catch (ClassCastException ignored) {}
            }

            // 2. 查询健康档案
            UserHealthProfile profile = healthProfileMapper.selectByUserId(userId);
            if (profile == null) {
                // 如果不存在，创建默认档案
                profile = new UserHealthProfile();
                profile.setUserId(userId);
                healthProfileMapper.insert(profile);
            }

            // 3. 查询用户基本信息
            User user = userMapper.selectById(userId);

            // 3. 查询最新体质测试结果
            UserConstitutionTest latestTest = constitutionTestMapper.selectLatestByUserId(userId);

            // 4. 组装返回数据
            Map<String, Object> result = new HashMap<>();
            result.put("id", profile.getId()); // 健康档案ID
            result.put("userId", userId);
            result.put("userName", user != null ? user.getRealName() : null);
            result.put("gender", user != null ? user.getGender() : null);
            result.put("birthday", user != null ? user.getBirthDate() : null);

            // 计算年龄
            if (user != null && user.getBirthDate() != null) {
                int age = LocalDate.now().getYear() - user.getBirthDate().getYear();
                result.put("age", age);
            } else {
                result.put("age", null);
            }

            // 健康档案信息
            result.put("height", profile.getHeight());
            result.put("weight", profile.getWeight());
            result.put("bmi", profile.getBmi());
            result.put("bloodType", profile.getBloodType());
            result.put("allergyHistory", profile.getAllergies());
            result.put("medicalHistory", profile.getMedicalHistory());
            result.put("familyHistory", profile.getFamilyHistory());
            result.put("currentMedications", profile.getCurrentMedications());
            result.put("lifestyle", profile.getLifestyle());
            result.put("dietPreference", profile.getDietPreference());
            result.put("exerciseHabit", profile.getExerciseHabit());
            result.put("sleepQuality", profile.getSleepQuality());
            result.put("stressLevel", profile.getStressLevel());
            result.put("lastCheckupDate", profile.getLastCheckupDate());
            result.put("checkupReport", profile.getCheckupReport());
            result.put("healthGoals", profile.getHealthGoals());
            result.put("remark", profile.getRemark());
            result.put("updatedAt", profile.getUpdatedAt());

            // 体质类型
            if (latestTest != null) {
                result.put("constitutionType", latestTest.getPrimaryConstitution());
                result.put("secondaryConstitutionType", latestTest.getSecondaryConstitution());
            } else {
                result.put("constitutionType", null);
                result.put("secondaryConstitutionType", null);
            }

            // 存入缓存（30分钟）
            redisUtil.set(cacheKey, result, 30, java.util.concurrent.TimeUnit.MINUTES);

            log.info("查询用户健康档案：用户ID={}", userId);
            return Result.success(result);

        } catch (Exception e) {
            log.error("查询用户健康档案失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    @Override
    public boolean isMandatoryHealthProfileComplete(Long userId) {
        if (userId == null) {
            return false;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }
        Integer gender = user.getGender();
        if (gender == null || (gender != 1 && gender != 2)) {
            return false;
        }
        if (user.getBirthDate() == null) {
            return false;
        }
        UserHealthProfile p = healthProfileMapper.selectByUserId(userId);
        if (p == null) {
            return false;
        }
        if (p.getHeight() == null || p.getWeight() == null) {
            return false;
        }
        if (!StringUtils.hasText(p.getBloodType())) {
            return false;
        }
        String bt = p.getBloodType().trim();
        return "A".equals(bt) || "B".equals(bt) || "AB".equals(bt) || "O".equals(bt);
    }

    /**
     * 更新用户健康档案
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<UserHealthProfile> updateHealthProfile(UserHealthProfile profile) {
        try {
            if (!StringUtils.hasText(profile.getBloodType())) {
                return Result.error(ResultCode.PARAM_ERROR.getCode(), "请选择血型");
            }
            String bt = profile.getBloodType().trim();
            if (!("A".equals(bt) || "B".equals(bt) || "AB".equals(bt) || "O".equals(bt))) {
                return Result.error(ResultCode.PARAM_ERROR.getCode(), "请选择血型");
            }
            profile.setBloodType(bt);

            // 计算BMI
            if (profile.getHeight() != null && profile.getWeight() != null) {
                double heightInMeters = profile.getHeight() / 100.0;
                double bmi = profile.getWeight() / (heightInMeters * heightInMeters);
                profile.setBmi(Math.round(bmi * 10.0) / 10.0);
            }

            UserHealthProfile existingProfile = healthProfileMapper.selectByUserId(profile.getUserId());
            if (existingProfile == null) {
                healthProfileMapper.insert(profile);
            } else {
                profile.setId(existingProfile.getId());
                healthProfileMapper.updateById(profile);
            }

            // 失效缓存
            redisUtil.delete("hospital:patient:health:profile:user:" + profile.getUserId());

            log.info("更新用户健康档案成功：用户ID={}", profile.getUserId());
            return Result.success(profile);

        } catch (Exception e) {
            log.error("更新用户健康档案失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 删除用户健康档案
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteHealthProfile(Long userId) {
        try {
            UserHealthProfile existingProfile = healthProfileMapper.selectByUserId(userId);
            if (existingProfile == null) {
                return Result.error(ResultCode.DATA_NOT_FOUND.getCode(), "健康档案不存在");
            }

            // 删除健康档案
            healthProfileMapper.deleteByUserId(userId);

            // 失效缓存
            redisUtil.delete("hospital:patient:health:profile:user:" + userId);

            log.info("删除用户健康档案成功：用户ID={}", userId);
            return Result.success();

        } catch (Exception e) {
            log.error("删除用户健康档案失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 创建健康计划
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<HealthPlanRecord> createHealthPlan(HealthPlanRecord plan) {
        try {
            // 设置初始值
            plan.setCompletedCount(0);
            plan.setCompletionRate(0);
            plan.setStatus(1); // 进行中

            // 计算目标次数
            if (plan.getTargetCount() == null) {
                long days = ChronoUnit.DAYS.between(plan.getStartDate(), plan.getEndDate()) + 1;
                plan.setTargetCount((int) days);
            }

            healthPlanMapper.insert(plan);

            log.info("创建健康计划成功：用户ID={}，计划名称={}", plan.getUserId(), plan.getPlanName());
            return Result.success(plan);

        } catch (Exception e) {
            log.error("创建健康计划失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 更新健康计划
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<HealthPlanRecord> updateHealthPlan(HealthPlanRecord plan) {
        try {
            HealthPlanRecord existingPlan = healthPlanMapper.selectById(plan.getId());
            if (existingPlan == null) {
                return Result.error(ResultCode.DATA_NOT_FOUND.getCode(), "健康计划不存在");
            }

            // 只允许用户更新自己的计划
            if (!existingPlan.getUserId().equals(plan.getUserId())) {
                return Result.error(ResultCode.FORBIDDEN.getCode(), "无权限更新此计划");
            }

            healthPlanMapper.updateById(plan);

            log.info("更新健康计划成功：计划ID={}", plan.getId());
            return Result.success(plan);

        } catch (Exception e) {
            log.error("更新健康计划失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 删除健康计划
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteHealthPlan(Long id, Long userId) {
        try {
            HealthPlanRecord plan = healthPlanMapper.selectById(id);
            if (plan == null) {
                return Result.error(ResultCode.DATA_NOT_FOUND.getCode(), "健康计划不存在");
            }

            // 只允许用户删除自己的计划
            if (!plan.getUserId().equals(userId)) {
                return Result.error(ResultCode.FORBIDDEN.getCode(), "无权限删除此计划");
            }

            // 软删除：更新状态为已放弃
            plan.setStatus(3);
            healthPlanMapper.updateById(plan);

            log.info("删除健康计划成功：计划ID={}", id);
            return Result.success();

        } catch (Exception e) {
            log.error("删除健康计划失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 分页查询用户的健康计划
     */
    @Override
    public Result<IPage<HealthPlanRecord>> getHealthPlanList(Long userId, Integer status, Integer pageNum, Integer pageSize) {
        try {
            Page<HealthPlanRecord> page = new Page<>(pageNum, pageSize);
            IPage<HealthPlanRecord> result = healthPlanMapper.selectByUserId(page, userId, status);
            return Result.success(result);

        } catch (Exception e) {
            log.error("查询用户健康计划失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 健康打卡
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<HealthCheckin> healthCheckin(HealthCheckin checkin) {
        try {
            // 已恢复为仅允许当天打卡，不再接受客户端自定义打卡日期
            LocalDate checkinDate = LocalDate.now();
            checkin.setCheckinDate(checkinDate);

            // 同一用户同一天仅允许打卡一次
            List<HealthCheckin> existed = healthCheckinMapper.selectByUserIdAndDate(checkin.getUserId(), checkinDate);
            if (existed != null && !existed.isEmpty()) {
                return Result.error(ResultCode.HEALTH_CHECKIN_ALREADY_EXISTS);
            }

            healthCheckinMapper.insert(checkin);

            // 如果关联了计划，更新计划完成次数
            if (checkin.getPlanId() != null) {
                healthPlanMapper.incrementCompletedCount(checkin.getPlanId());
            }

            log.info("健康打卡成功：用户ID={}，打卡类型={}", checkin.getUserId(), checkin.getCheckinType());
            return Result.success(checkin);

        } catch (Exception e) {
            log.error("健康打卡失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 分页查询用户的打卡记录
     */
    @Override
    public Result<IPage<HealthCheckin>> getCheckinList(Long userId, String checkinType, LocalDate startDate, LocalDate endDate, Integer pageNum, Integer pageSize) {
        try {
            // 如果指定了日期范围，使用日期范围查询
            if (startDate != null && endDate != null) {
                List<HealthCheckin> checkins = healthCheckinMapper.selectByDateRange(userId, startDate, endDate);

                // 如果指定了打卡类型，进行过滤
                if (checkinType != null && !checkinType.isEmpty()) {
                    checkins = checkins.stream()
                            .filter(c -> checkinType.equals(c.getCheckinType()))
                            .collect(java.util.stream.Collectors.toList());
                }

                // 手动分页
                Page<HealthCheckin> page = new Page<>(pageNum, pageSize);
                page.setTotal(checkins.size());
                int start = (pageNum - 1) * pageSize;
                int end = Math.min(start + pageSize, checkins.size());
                page.setRecords(start < checkins.size() ? checkins.subList(start, end) : new java.util.ArrayList<>());

                // 查询用户打卡记录（日期范围）
                return Result.success(page);
            } else {
                // 使用原有的分页查询
                Page<HealthCheckin> page = new Page<>(pageNum, pageSize);
                IPage<HealthCheckin> result = healthCheckinMapper.selectByUserId(page, userId, checkinType);
                return Result.success(result);
            }

        } catch (Exception e) {
            log.error("查询用户打卡记录失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 查询用户指定日期的打卡记录
     */
    @Override
    public Result<HealthCheckin> getCheckinByDate(Long userId, LocalDate date) {
        try {
            List<HealthCheckin> checkins = healthCheckinMapper.selectByUserIdAndDate(userId, date);
            HealthCheckin checkin = checkins.isEmpty() ? null : checkins.get(0);
            log.info("查询用户指定日期打卡记录：用户ID={}，日期={}", userId, date);
            return Result.success(checkin);

        } catch (Exception e) {
            log.error("查询用户指定日期打卡记录失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 获取用户健康统计数据
     */
    @Override
    public Result<Map<String, Object>> getHealthStatistics(Long userId, LocalDate startDate, LocalDate endDate) {
        try {
            Map<String, Object> statistics = new HashMap<>();

            // 查询打卡记录
            List<HealthCheckin> checkins = healthCheckinMapper.selectByDateRange(userId, startDate, endDate);

            // 统计打卡天数
            long checkinDays = checkins.stream()
                    .map(HealthCheckin::getCheckinDate)
                    .distinct()
                    .count();
            statistics.put("checkinDays", checkinDays);

            // 统计连续打卡天数
            Integer continuousDays = healthCheckinMapper.countContinuousDays(userId);
            statistics.put("continuousDays", continuousDays);

            // 统计平均体重
            Double avgWeight = checkins.stream()
                    .filter(c -> c.getWeight() != null)
                    .mapToDouble(HealthCheckin::getWeight)
                    .average()
                    .orElse(0.0);
            statistics.put("avgWeight", Math.round(avgWeight * 10.0) / 10.0);

            // 统计平均睡眠时长
            Double avgSleep = checkins.stream()
                    .filter(c -> c.getSleepDuration() != null)
                    .mapToDouble(HealthCheckin::getSleepDuration)
                    .average()
                    .orElse(0.0);
            statistics.put("avgSleepDuration", Math.round(avgSleep * 10.0) / 10.0);

            // 统计平均运动时长
            Double avgExercise = checkins.stream()
                    .filter(c -> c.getExerciseDuration() != null)
                    .mapToDouble(HealthCheckin::getExerciseDuration)
                    .average()
                    .orElse(0.0);
            statistics.put("avgExerciseDuration", Math.round(avgExercise));

            // 统计平均心情评分
            Double avgMood = checkins.stream()
                    .filter(c -> c.getMoodScore() != null)
                    .mapToDouble(HealthCheckin::getMoodScore)
                    .average()
                    .orElse(0.0);
            statistics.put("avgMoodScore", Math.round(avgMood * 10.0) / 10.0);

            // 生成趋势数据
            List<Map<String, Object>> weightTrend = new ArrayList<>();
            List<Map<String, Object>> sleepTrend = new ArrayList<>();
            List<Map<String, Object>> exerciseTrend = new ArrayList<>();

            // 按日期分组统计
            Map<LocalDate, List<HealthCheckin>> checkinsByDate = checkins.stream()
                    .collect(Collectors.groupingBy(HealthCheckin::getCheckinDate));

            // 生成每日趋势数据
            checkinsByDate.forEach((date, dailyCheckins) -> {
                // 体重趋势
                dailyCheckins.stream()
                        .filter(c -> c.getWeight() != null)
                        .findFirst()
                        .ifPresent(c -> {
                            Map<String, Object> weightData = new HashMap<>();
                            weightData.put("date", date.toString());
                            weightData.put("weight", c.getWeight());
                            weightTrend.add(weightData);
                        });

                // 睡眠趋势
                dailyCheckins.stream()
                        .filter(c -> c.getSleepDuration() != null)
                        .findFirst()
                        .ifPresent(c -> {
                            Map<String, Object> sleepData = new HashMap<>();
                            sleepData.put("date", date.toString());
                            sleepData.put("duration", c.getSleepDuration());
                            sleepData.put("quality", c.getSleepQuality() != null ? c.getSleepQuality() : 0);
                            sleepTrend.add(sleepData);
                        });

                // 运动趋势
                dailyCheckins.stream()
                        .filter(c -> c.getExerciseDuration() != null)
                        .findFirst()
                        .ifPresent(c -> {
                            Map<String, Object> exerciseData = new HashMap<>();
                            exerciseData.put("date", date.toString());
                            exerciseData.put("duration", c.getExerciseDuration());
                            exerciseTrend.add(exerciseData);
                        });
            });

            // 按日期排序
            weightTrend.sort((a, b) -> ((String) a.get("date")).compareTo((String) b.get("date")));
            sleepTrend.sort((a, b) -> ((String) a.get("date")).compareTo((String) b.get("date")));
            exerciseTrend.sort((a, b) -> ((String) a.get("date")).compareTo((String) b.get("date")));

            statistics.put("weightTrend", weightTrend);
            statistics.put("sleepTrend", sleepTrend);
            statistics.put("exerciseTrend", exerciseTrend);

            // 心情分布统计
            Map<String, Long> moodDistribution = new HashMap<>();
            moodDistribution.put("GOOD", checkins.stream().filter(c -> c.getMoodScore() != null && c.getMoodScore() >= 4).count());
            moodDistribution.put("NORMAL", checkins.stream().filter(c -> c.getMoodScore() != null && c.getMoodScore() == 3).count());
            moodDistribution.put("BAD", checkins.stream().filter(c -> c.getMoodScore() != null && c.getMoodScore() <= 2).count());
            statistics.put("moodDistribution", moodDistribution);

            log.info("获取用户健康统计数据：用户ID={}，打卡天数={}", userId, checkinDays);
            return Result.success(statistics);

        } catch (Exception e) {
            log.error("获取用户健康统计数据失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    @Override
    public Result<List<Map<String, Object>>> getHealthPlanHistory(Long userId) {
        try {
            // 1. 获取所有体质测试记录
            List<UserConstitutionTest> tests = constitutionTestMapper.selectHistoryByUserId(userId);

            List<Map<String, Object>> history = new ArrayList<>();

            // 2. 遍历测试记录，查询对应的计划数量
            for (UserConstitutionTest test : tests) {
                Map<String, Object> item = new HashMap<>();
                item.put("testId", test.getId());
                item.put("testDate", test.getTestDate());
                item.put("primaryConstitution", test.getPrimaryConstitution());

                // 尝试解析缩略图 (从 testResult 中获取 tongueResult -> image_base64)
                String thumbnail = null;
                if (test.getTestResult() != null) {
                    try {
                        cn.hutool.json.JSONObject json = cn.hutool.json.JSONUtil.parseObj(test.getTestResult());
                        if (json.containsKey("tongueResult")) {
                            String tongueResultStr = json.getStr("tongueResult");
                            cn.hutool.json.JSONObject tongueJson = cn.hutool.json.JSONUtil.parseObj(tongueResultStr);

                            // 优先使用 OSS 图片链接，其次是 Base64
                            if (tongueJson.containsKey("image_url")) {
                                thumbnail = tongueJson.getStr("image_url");
                            } else if (tongueJson.containsKey("image_base64")) {
                                thumbnail = tongueJson.getStr("image_base64");
                                // 如果是 data:image 格式，可能太长，仅截取前缀或标记有图片
                                // 前端展示列表时通常不需要完整的 base64，除非是缩略图。
                                // 这里假设前端列表需要显示小图，所以返回完整 base64 (注意性能)
                                // 优化：如果太长，可以考虑只返回 boolean hasImage
                            }
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
                item.put("thumbnail", thumbnail);

                // 查询计划数量
                Long count = healthPlanMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<HealthPlanRecord>()
                        .eq("test_id", test.getId()));
                item.put("planCount", count);

                history.add(item);
            }

            // 3. 查询未关联测试的计划（手动创建）
            Long manualCount = healthPlanMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<HealthPlanRecord>()
                    .eq("user_id", userId)
                    .isNull("test_id"));

            if (manualCount > 0) {
                Map<String, Object> manualItem = new HashMap<>();
                manualItem.put("testId", -1L); // Special ID for manual
                manualItem.put("testDate", null);
                manualItem.put("primaryConstitution", "自建计划");
                manualItem.put("planCount", manualCount);
                manualItem.put("thumbnail", null);
                history.add(manualItem);
            }

            return Result.success(history);
        } catch (Exception e) {
            log.error("获取健康计划历史失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    @Override
    public Result<List<HealthPlanRecord>> getHealthPlansByTestId(Long userId, Long testId) {
        try {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<HealthPlanRecord> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            wrapper.eq("user_id", userId);

            if (testId != null && testId != -1) {
                wrapper.eq("test_id", testId);
            } else {
                wrapper.isNull("test_id");
            }

            wrapper.orderByDesc("created_at");

            List<HealthPlanRecord> list = healthPlanMapper.selectList(wrapper);
            return Result.success(list);
        } catch (Exception e) {
            log.error("查询测试对应计划失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> createHealthPlansFromAiResult(Long userId, Long testId, String aiSuggestion) {
        try {
            // 1. 清洗 JSON
            String cleanJson = aiSuggestion.trim();
            if (cleanJson.contains("```json")) {
                cleanJson = cleanJson.split("```json")[1].split("```")[0].trim();
            } else if (cleanJson.contains("```")) {
                cleanJson = cleanJson.split("```")[1].split("```")[0].trim();
            }

            // 2. 解析 JSON
            cn.hutool.json.JSONObject root = cn.hutool.json.JSONUtil.parseObj(cleanJson);
            if (!root.containsKey("plans")) {
                return Result.error(ResultCode.PARAM_ERROR.getCode(), "AI建议中未找到计划数据");
            }

            cn.hutool.json.JSONArray plans = root.getJSONArray("plans");
            if (plans == null || plans.isEmpty()) {
                return Result.success(true); // 无计划也算成功
            }

            // 3. 遍历创建计划
            for (int i = 0; i < plans.size(); i++) {
                cn.hutool.json.JSONObject planJson = plans.getJSONObject(i);

                HealthPlanRecord plan = new HealthPlanRecord();
                plan.setUserId(userId);
                plan.setTestId(testId); // 关联测试ID

                // 按当前 AI 协议字段名解析（type/name/description/frequency/targetContent 可选）
                plan.setPlanType(planJson.getStr("type"));
                plan.setPlanName(planJson.getStr("name"));
                plan.setDescription(planJson.getStr("description"));
                plan.setFrequency(planJson.getStr("frequency"));
                plan.setTargetContent(planJson.getStr("targetContent"));

                Integer duration = planJson.getInt("duration", 30);
                plan.setStartDate(LocalDate.now());
                plan.setEndDate(LocalDate.now().plusDays(duration));

                // 设置初始状态
                plan.setCompletedCount(0);
                plan.setCompletionRate(0);
                plan.setStatus(1); // 进行中
                plan.setTargetCount(duration); // 简单起见，按天数作为目标次数

                healthPlanMapper.insert(plan);
            }

            log.info("从AI建议创建计划成功: userId={}, testId={}, count={}", userId, testId, plans.size());
            return Result.success(true);

        } catch (Exception e) {
            log.error("从AI建议创建计划失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }
}

