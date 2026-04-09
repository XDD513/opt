package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.common.result.Result;
import com.hospital.common.result.ResultCode;
import com.hospital.entity.ArticleNotification;
import com.hospital.entity.User;
import com.hospital.mapper.ArticleNotificationMapper;
import com.hospital.mapper.UserMapper;
import com.hospital.service.ArticleNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ArticleNotificationServiceImpl implements ArticleNotificationService {

    @Autowired
    private ArticleNotificationMapper articleNotificationMapper;

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Override
    public Result<IPage<ArticleNotification>> getMyNotifications(Long userId, Integer pageNum, Integer pageSize) {
        try {
            Page<ArticleNotification> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<ArticleNotification> wrapper = new LambdaQueryWrapper<ArticleNotification>()
                    .eq(ArticleNotification::getUserId, userId)
                    .eq(ArticleNotification::getIsDeleted, 0)
                    .orderByAsc(ArticleNotification::getIsRead)
                    .orderByDesc(ArticleNotification::getCreatedAt);
            return Result.success(articleNotificationMapper.selectPage(page, wrapper));
        } catch (Exception e) {
            log.error("查询用户通知失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    @Override
    public Result<IPage<ArticleNotification>> getAdminNotifications(Integer pageNum, Integer pageSize) {
        try {
            Page<ArticleNotification> page = new Page<>(pageNum, pageSize);
            LambdaQueryWrapper<ArticleNotification> wrapper = new LambdaQueryWrapper<ArticleNotification>()
                    .eq(ArticleNotification::getRoleType, 1)
                    .eq(ArticleNotification::getIsDeleted, 0)
                    .orderByAsc(ArticleNotification::getIsRead)
                    .orderByDesc(ArticleNotification::getCreatedAt);
            return Result.success(articleNotificationMapper.selectPage(page, wrapper));
        } catch (Exception e) {
            log.error("查询管理员通知失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    @Override
    public Result<Integer> getUnreadCount(Long userId, Integer roleType) {
        try {
            LambdaQueryWrapper<ArticleNotification> wrapper = new LambdaQueryWrapper<ArticleNotification>()
                    .eq(ArticleNotification::getIsRead, 0)
                    .eq(ArticleNotification::getIsDeleted, 0);
            if (roleType != null && roleType == 1) {
                wrapper.eq(ArticleNotification::getRoleType, 1);
            } else {
                wrapper.eq(ArticleNotification::getUserId, userId);
            }
            Long count = articleNotificationMapper.selectCount(wrapper);
            return Result.success(count == null ? 0 : count.intValue());
        } catch (Exception e) {
            log.error("查询未读通知数量失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    @Override
    public Result<Void> markRead(Long id, Long userId, Integer roleType) {
        try {
            LambdaUpdateWrapper<ArticleNotification> wrapper = new LambdaUpdateWrapper<ArticleNotification>()
                    .eq(ArticleNotification::getId, id)
                    .eq(ArticleNotification::getIsDeleted, 0)
                    .set(ArticleNotification::getIsRead, 1);
            if (roleType != null && roleType == 1) {
                wrapper.eq(ArticleNotification::getRoleType, 1);
            } else {
                wrapper.eq(ArticleNotification::getUserId, userId);
            }
            articleNotificationMapper.update(null, wrapper);
            return Result.success();
        } catch (Exception e) {
            log.error("标记通知已读失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    @Override
    public Result<Void> markAllRead(Long userId, Integer roleType) {
        try {
            LambdaUpdateWrapper<ArticleNotification> wrapper = new LambdaUpdateWrapper<ArticleNotification>()
                    .eq(ArticleNotification::getIsRead, 0)
                    .eq(ArticleNotification::getIsDeleted, 0)
                    .set(ArticleNotification::getIsRead, 1);
            if (roleType != null && roleType == 1) {
                wrapper.eq(ArticleNotification::getRoleType, 1);
            } else {
                wrapper.eq(ArticleNotification::getUserId, userId);
            }
            articleNotificationMapper.update(null, wrapper);
            return Result.success();
        } catch (Exception e) {
            log.error("全部标记通知已读失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    @Override
    public void createNotificationForUser(Long userId, Integer roleType, String title, String content,
                                          String notificationType, String bizType, Long bizId) {
        try {
            if (userId == null) {
                return;
            }
            ArticleNotification notification = new ArticleNotification();
            notification.setUserId(userId);
            notification.setRoleType(roleType);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setNotificationType(notificationType);
            notification.setBizType(bizType);
            notification.setBizId(bizId);
            notification.setIsRead(0);
            notification.setIsDeleted(0);
            articleNotificationMapper.insert(notification);
            // 实时推送给目标用户
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId),
                    "/queue/article-notifications",
                    notification
            );
        } catch (Exception e) {
            log.warn("创建用户通知失败：userId={} title={} err={}", userId, title, e.getMessage());
        }
    }

    @Override
    public void createNotificationForAdmins(String title, String content, String notificationType, String bizType, Long bizId) {
        try {
            java.util.List<User> admins = userMapper.selectList(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getRoleType, 1)
                            .eq(User::getStatus, 1)
                            .select(User::getId, User::getRoleType)
            );
            if (admins == null || admins.isEmpty()) {
                return;
            }
            for (User admin : admins) {
                createNotificationForUser(admin.getId(), 1, title, content, notificationType, bizType, bizId);
            }
        } catch (Exception e) {
            log.warn("创建管理员通知失败：title={} err={}", title, e.getMessage());
        }
    }

    @Override
    public Result<Void> markReadBatch(java.util.List<Long> ids, Long userId, Integer roleType) {
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.success();
            }
            LambdaUpdateWrapper<ArticleNotification> wrapper = new LambdaUpdateWrapper<ArticleNotification>()
                    .in(ArticleNotification::getId, ids)
                    .eq(ArticleNotification::getIsDeleted, 0)
                    .set(ArticleNotification::getIsRead, 1);
            if (roleType != null && roleType == 1) {
                wrapper.eq(ArticleNotification::getRoleType, 1);
            } else {
                wrapper.eq(ArticleNotification::getUserId, userId);
            }
            articleNotificationMapper.update(null, wrapper);
            return Result.success();
        } catch (Exception e) {
            log.error("批量标记已读失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }

    @Override
    public Result<Void> deleteBatch(java.util.List<Long> ids, Long userId, Integer roleType) {
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.success();
            }
            LambdaUpdateWrapper<ArticleNotification> wrapper = new LambdaUpdateWrapper<ArticleNotification>()
                    .in(ArticleNotification::getId, ids)
                    .eq(ArticleNotification::getIsDeleted, 0)
                    .set(ArticleNotification::getIsDeleted, 1);
            if (roleType != null && roleType == 1) {
                wrapper.eq(ArticleNotification::getRoleType, 1);
            } else {
                wrapper.eq(ArticleNotification::getUserId, userId);
            }
            articleNotificationMapper.update(null, wrapper);
            return Result.success();
        } catch (Exception e) {
            log.error("批量删除通知失败", e);
            return Result.error(ResultCode.SYSTEM_ERROR);
        }
    }
}

