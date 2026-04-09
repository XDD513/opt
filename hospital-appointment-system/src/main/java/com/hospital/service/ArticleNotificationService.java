package com.hospital.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hospital.common.result.Result;
import com.hospital.entity.ArticleNotification;

public interface ArticleNotificationService {

    Result<IPage<ArticleNotification>> getMyNotifications(Long userId, Integer pageNum, Integer pageSize);

    Result<IPage<ArticleNotification>> getAdminNotifications(Integer pageNum, Integer pageSize);

    Result<Integer> getUnreadCount(Long userId, Integer roleType);

    Result<Void> markRead(Long id, Long userId, Integer roleType);

    Result<Void> markAllRead(Long userId, Integer roleType);

    Result<Void> markReadBatch(java.util.List<Long> ids, Long userId, Integer roleType);

    Result<Void> deleteBatch(java.util.List<Long> ids, Long userId, Integer roleType);

    void createNotificationForUser(Long userId, Integer roleType, String title, String content,
                                   String notificationType, String bizType, Long bizId);

    void createNotificationForAdmins(String title, String content, String notificationType, String bizType, Long bizId);
}

