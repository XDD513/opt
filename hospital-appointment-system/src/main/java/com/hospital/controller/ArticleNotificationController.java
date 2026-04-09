package com.hospital.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hospital.common.result.Result;
import com.hospital.common.result.ResultCode;
import com.hospital.entity.ArticleNotification;
import com.hospital.service.ArticleNotificationService;
import com.hospital.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/article/notification")
public class ArticleNotificationController {

    @Autowired
    private ArticleNotificationService articleNotificationService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/my")
    public Result<IPage<ArticleNotification>> myNotifications(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return articleNotificationService.getMyNotifications(userId, pageNum, pageSize);
    }

    @GetMapping("/admin")
    public Result<IPage<ArticleNotification>> adminNotifications(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "50") Integer pageSize,
            HttpServletRequest request) {
        Integer roleType = jwtUtil.getRoleTypeFromRequest(request);
        if (roleType == null || roleType != 1) {
            return Result.error(ResultCode.FORBIDDEN);
        }
        return articleNotificationService.getAdminNotifications(pageNum, pageSize);
    }

    @GetMapping("/unread-count")
    public Result<Integer> unreadCount(HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        Integer roleType = jwtUtil.getRoleTypeFromRequest(request);
        if (userId == null && (roleType == null || roleType != 1)) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return articleNotificationService.getUnreadCount(userId, roleType);
    }

    @PutMapping("/read/{id}")
    public Result<Void> markRead(@PathVariable("id") Long id, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        Integer roleType = jwtUtil.getRoleTypeFromRequest(request);
        if (userId == null && (roleType == null || roleType != 1)) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return articleNotificationService.markRead(id, userId, roleType);
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead(HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        Integer roleType = jwtUtil.getRoleTypeFromRequest(request);
        if (userId == null && (roleType == null || roleType != 1)) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return articleNotificationService.markAllRead(userId, roleType);
    }

    @PutMapping("/read-batch")
    public Result<Void> markReadBatch(@RequestBody List<Long> ids, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        Integer roleType = jwtUtil.getRoleTypeFromRequest(request);
        if (userId == null && (roleType == null || roleType != 1)) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return articleNotificationService.markReadBatch(ids, userId, roleType);
    }

    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        Integer roleType = jwtUtil.getRoleTypeFromRequest(request);
        if (userId == null && (roleType == null || roleType != 1)) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return articleNotificationService.deleteBatch(ids, userId, roleType);
    }
}

