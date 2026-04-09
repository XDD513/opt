package com.hospital.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hospital.common.constant.CacheConstants;
import com.hospital.common.constant.SystemConstants;
import com.hospital.config.AvatarConfig;
import com.hospital.dto.ConversationMessageEventDTO;
import com.hospital.dto.request.ConversationCreateRequest;
import com.hospital.dto.request.SendMessageRequest;
import com.hospital.entity.Conversation;
import com.hospital.entity.ConversationMessage;
import com.hospital.entity.User;
import com.hospital.mapper.ConversationMapper;
import com.hospital.mapper.ConversationMessageMapper;
import com.hospital.mapper.UserMapper;
import com.hospital.messaging.ConversationMessagePublisher;
import com.hospital.service.ConversationService;
import com.hospital.service.OssService;
import com.hospital.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 医患对话服务实现
 */
@Slf4j
@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation> implements ConversationService {

    @Autowired
    private ConversationMessageMapper conversationMessageMapper;

    @Autowired
    private UserMapper userMapper;

    // 已移除医生模块

    @Autowired
    private ConversationMessagePublisher conversationMessagePublisher;

    @Autowired
    private OssService ossService;

    // 

    @Autowired
    private AvatarConfig avatarConfig;

    @Autowired
    private RedisUtil redisUtil;

    // 已彻底移除医生模块
    @Override
    public IPage<Conversation> listConversations(Map<String, Object> params) {
        Page<Conversation> page = buildPage(params);
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();

        Long patientId = parseLong(params.get("patientId"));
        Long doctorId = parseLong(params.get("doctorId"));
        Long userId = parseLong(params.get("userId")); // 新增：支持通过用户ID查询
        String status = params.get("status") != null ? params.get("status").toString() : null;
        String keyword = params.get("keyword") != null ? params.get("keyword").toString() : null;

        // 确定用于缓存的用户ID（优先使用userId）
        Long cacheUserId = userId != null ? userId : (patientId != null ? patientId : doctorId);

        // 尝试从缓存获取会话列表（仅当查询条件简单时，即只有userId且无keyword和status过滤）
        boolean canUseCache = cacheUserId != null && !StringUtils.hasText(keyword) &&
                              (status == null || "ACTIVE".equals(status)) &&
                              page.getCurrent() == 1 && page.getSize() <= 20;

        if (canUseCache) {
            String listCacheKey = CacheConstants.CACHE_CONVERSATION_LIST_PREFIX + cacheUserId + ":page:1:size:" + page.getSize();
            Object cachedList = redisUtil.get(listCacheKey);
            if (cachedList != null && cachedList instanceof IPage) {
                @SuppressWarnings("unchecked")
                IPage<Conversation> cachedPage = (IPage<Conversation>) cachedList;
                return cachedPage;
            }

            // 尝试从缓存获取总数
            String countCacheKey = CacheConstants.CACHE_CONVERSATION_COUNT_PREFIX + cacheUserId;
            Object cachedCount = redisUtil.get(countCacheKey);
        }

        // 支持通过用户ID查询（新方式，支持三种身份）
        if (userId != null) {
            wrapper.and(q -> q.eq(Conversation::getParticipant1UserId, userId)
                    .or().eq(Conversation::getParticipant2UserId, userId));
            // 过滤掉被该用户删除的会话
            wrapper.and(q -> q.and(w -> w.eq(Conversation::getParticipant1UserId, userId)
                            .and(sub -> sub.isNull(Conversation::getDeletedByParticipant1)
                                    .or().eq(Conversation::getDeletedByParticipant1, 0)))
                    .or(w -> w.eq(Conversation::getParticipant2UserId, userId)
                            .and(sub -> sub.isNull(Conversation::getDeletedByParticipant2)
                                    .or().eq(Conversation::getDeletedByParticipant2, 0))));
        }

        // 向后兼容：支持通过patientId查询
        if (patientId != null && userId == null) {
            wrapper.and(q -> q.eq(Conversation::getPatientId, patientId)
                    .or().eq(Conversation::getParticipant1UserId, patientId)
                    .or().eq(Conversation::getParticipant2UserId, patientId));
            // 过滤掉被患者删除的会话
            wrapper.and(q -> q.isNull(Conversation::getDeletedByPatient)
                    .or().eq(Conversation::getDeletedByPatient, 0));
        }

        // 兼容旧参数：如果传入 doctorId，则按参与者用户ID查询
        if (doctorId != null && userId == null) {
            wrapper.and(q -> q.eq(Conversation::getParticipant2UserId, doctorId)
                    .or().eq(Conversation::getParticipant1UserId, doctorId));
            wrapper.and(q -> q.isNull(Conversation::getDeletedByParticipant2)
                    .or().eq(Conversation::getDeletedByParticipant2, 0));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Conversation::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(q -> q.like(Conversation::getTitle, keyword)
                    .or().like(Conversation::getSummary, keyword));
        }

        wrapper.orderByDesc(Conversation::getUpdatedAt);
        IPage<Conversation> result = baseMapper.selectPage(page, wrapper);

        // 为会话列表中的头像生成签名URL
        List<Conversation> records = result.getRecords();
        for (Conversation conversation : records) {
            conversation.setPatientAvatar(resolveAvatarUrl(conversation.getPatientAvatar(), conversation.getPatientId(), "patient"));
            conversation.setLastSenderAvatar(resolveAvatarUrl(conversation.getLastSenderAvatar(), null, null));
        }

        // 缓存会话列表和总数（仅当查询条件简单时）
        if (canUseCache && cacheUserId != null) {
            String listCacheKey = CacheConstants.CACHE_CONVERSATION_LIST_PREFIX + cacheUserId + ":page:1:size:" + page.getSize();
            redisUtil.set(listCacheKey, result, CacheConstants.CACHE_CONVERSATION_LIST_TTL_SECONDS, TimeUnit.SECONDS);

            String countCacheKey = CacheConstants.CACHE_CONVERSATION_COUNT_PREFIX + cacheUserId;
            redisUtil.set(countCacheKey, result.getTotal(), CacheConstants.CACHE_CONVERSATION_COUNT_TTL_SECONDS, TimeUnit.SECONDS);
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Conversation createConversation(ConversationCreateRequest request, Long currentUserId, Integer currentRoleType) {
        // 不再使用 doctor 维度；把“对方”统一为 participant2UserId
        boolean isAdminConversation = "ADMIN_USER".equals(request.getConversationType())
                && currentRoleType != null && currentRoleType == 1;

        User patient = userMapper.selectById(request.getPatientId());
        if (patient == null) {
            throw new IllegalArgumentException("患者不存在");
        }

        if (currentUserId == null) {
            throw new IllegalArgumentException("当前用户未登录");
        }

        // participant2：管理员对话时为管理员ID；否则来自请求
        Long participant2UserId = isAdminConversation ? currentUserId : request.getParticipant2UserId();
        if (participant2UserId == null) {
            throw new IllegalArgumentException("对方用户ID不能为空");
        }

        String conversationType = StringUtils.hasText(request.getConversationType())
                ? request.getConversationType()
                : (isAdminConversation ? "ADMIN_USER" : "USER_USER");

        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getPatientId, request.getPatientId())
                .eq(Conversation::getParticipant2UserId, participant2UserId)
                .eq(Conversation::getConversationType, conversationType)
                .eq(Conversation::getStatus, "ACTIVE");

        Conversation existed = getOne(wrapper, false);
        if (existed != null) {
            // 如果会话被当前用户删除，则恢复
            if (Objects.equals(existed.getParticipant1UserId(), currentUserId)) {
                existed.setDeletedByParticipant1(0);
                existed.setDeletedByPatient(0);
            }
            if (Objects.equals(existed.getParticipant2UserId(), currentUserId)) {
                existed.setDeletedByParticipant2(0);
            }
            existed.setUnreadForParticipant1(0);
            existed.setUnreadForParticipant2(0);
            updateById(existed);
            evictConversationCache(existed.getId());
            evictConversationListCache(existed);
            // 返回前补齐签名头像（仅患者头像）
            existed.setPatientAvatar(resolveAvatarUrl(existed.getPatientAvatar(), existed.getPatientId(),
                    "patient"));
            return existed;
        }

        Conversation conversation = new Conversation();
        conversation.setPatientId(request.getPatientId());
        conversation.setConversationType(conversationType);

        // participant1：固定为患者
        conversation.setParticipant1UserId(request.getPatientId());
        conversation.setParticipant1Role("PATIENT");

        // participant2：对方用户（管理员或普通用户）
        conversation.setParticipant2UserId(participant2UserId);
        conversation.setParticipant2Role(isAdminConversation ? "ADMIN" : "USER");

        String patientName = StringUtils.hasText(patient.getRealName()) ? patient.getRealName() : patient.getUsername();
        conversation.setPatientNickname(patientName);
        conversation.setPatientAvatar(patient.getAvatar());

        conversation.setTitle(StringUtils.hasText(request.getTitle())
                ? request.getTitle()
                : patientName + " x 对方");
        conversation.setSummary(StringUtils.hasText(request.getSummary())
                ? request.getSummary()
                : (isAdminConversation ? "与管理员对话" : "智能康复沟通会话"));

        conversation.setStatus("ACTIVE");
        conversation.setUnreadForPatient(0);
        conversation.setUnreadForParticipant1(0);
        conversation.setUnreadForParticipant2(0);
        conversation.setDeletedByPatient(0);
        conversation.setDeletedByParticipant1(0);
        conversation.setDeletedByParticipant2(0);

        save(conversation);
        evictConversationCache(conversation.getId());
        evictConversationListCache(conversation);

        conversation.setPatientAvatar(resolveAvatarUrl(conversation.getPatientAvatar(), conversation.getPatientId(), "patient"));
        log.info("创建会话成功: conversationId={}, type={}, patientId={}, participant2UserId={}",
                conversation.getId(), conversation.getConversationType(), request.getPatientId(), participant2UserId);

        return conversation;
    }

    @Override
    public IPage<ConversationMessage> listMessages(Long conversationId, Map<String, Object> params) {
        ensureConversationExists(conversationId);
        Page<ConversationMessage> page = buildMessagePage(params);
        LambdaQueryWrapper<ConversationMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationMessage::getConversationId, conversationId)
                .orderByAsc(ConversationMessage::getSentAt);

        boolean canUseCache = page.getCurrent() == 1;
        String messageCacheKey = null;
        if (canUseCache) {
            messageCacheKey = redisUtil.buildCacheKey(
                    CacheConstants.CACHE_CONVERSATION_MESSAGE_LIST_PREFIX + conversationId,
                    (int) page.getCurrent(),
                    (int) page.getSize(),
                    params);
            Object cached = redisUtil.get(messageCacheKey);
            if (cached instanceof IPage) {
                @SuppressWarnings("unchecked")
                IPage<ConversationMessage> cachedPage = (IPage<ConversationMessage>) cached;
                return cachedPage;
            }
        }

        IPage<ConversationMessage> result = conversationMessageMapper.selectPage(page, wrapper);

        // 为每条消息生成可访问的头像URL（避免历史数据中的过期签名导致头像失效）
        if (result != null && result.getRecords() != null && !result.getRecords().isEmpty()) {
            for (ConversationMessage message : result.getRecords()) {
                if (message == null) {
                    continue;
                }
                String entityType = null;
                String senderRole = message.getSenderRole();
                if ("PATIENT".equalsIgnoreCase(senderRole)) {
                    entityType = "patient";
                } else if ("DOCTOR".equalsIgnoreCase(senderRole)) {
                    entityType = "doctor";
                } else if ("ADMIN".equalsIgnoreCase(senderRole)) {
                    entityType = "admin";
                }
                message.setSenderAvatar(resolveAvatarUrl(message.getSenderAvatar(), message.getSenderId(), entityType));
            }
        }

        if (canUseCache && messageCacheKey != null) {
            redisUtil.set(messageCacheKey, result,
                    CacheConstants.CACHE_CONVERSATION_MESSAGE_LIST_TTL_SECONDS, TimeUnit.SECONDS);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationMessage appendMessage(Long conversationId, SendMessageRequest request) {
        Conversation conversation = ensureConversationExists(conversationId);

        ConversationMessage message = new ConversationMessage();
        message.setConversationId(conversationId);
        message.setSenderRole(request.getSenderRole());

        // 如果请求中没有senderId，从对话信息中获取（兜底逻辑）
        Long senderId = request.getSenderId();
        if (senderId == null) {
            String senderRole = request.getSenderRole();
            if ("PATIENT".equalsIgnoreCase(senderRole)) {
                // 患者：使用对话中的patientId
                senderId = conversation.getPatientId();
            } else if ("DOCTOR".equalsIgnoreCase(senderRole)) {
                // 医生：需要从doctorId转换为用户ID
                if (conversation.getParticipant2UserId() != null && "DOCTOR".equals(conversation.getParticipant2Role())) {
                    senderId = conversation.getParticipant2UserId();
                } else if (conversation.getParticipant2UserId() != null) {
                    // 兼容旧字段：doctorId 直接视为用户ID
                    senderId = conversation.getParticipant2UserId();
                }
            } else if ("ADMIN".equalsIgnoreCase(senderRole)) {
                // 管理员：使用participant2UserId或doctorId
                if (conversation.getParticipant2UserId() != null && "ADMIN".equals(conversation.getParticipant2Role())) {
                    senderId = conversation.getParticipant2UserId();
                } else {
                    senderId = conversation.getParticipant2UserId();
                }
            }

            if (senderId == null) {
                throw new IllegalArgumentException("无法确定发送者ID，请提供senderId参数");
            }
        }

        message.setSenderId(senderId);
        message.setSenderName(StringUtils.hasText(request.getSenderName())
                ? request.getSenderName()
                : defaultSenderName(request.getSenderRole(), conversation));
        String requestAvatar = StringUtils.hasText(request.getSenderAvatar())
                ? request.getSenderAvatar()
                : defaultSenderAvatar(request.getSenderRole(), conversation);
        message.setSenderAvatar(sanitizeAvatarUrl(requestAvatar));
        message.setContent(request.getContent());
        message.setContentType(StringUtils.hasText(request.getContentType()) ? request.getContentType() : "TEXT");
        message.setAttachmentUrl(StringUtils.hasText(request.getAttachmentUrl()) ? request.getAttachmentUrl() : null);
        message.setMetadata(StringUtils.hasText(request.getMetadata()) ? request.getMetadata() : null);
        message.setIsRead(0);
        message.setSentAt(LocalDateTime.now());

        conversationMessageMapper.insert(message);
        refreshConversationSnapshot(conversation, message);
        publishConversationEvent(conversation, message);
        sendMessageNotification(conversation, message);
        evictConversationMessageCache(conversationId);
        return message;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean clearMessages(Long conversationId) {
        Conversation conversation = ensureConversationExists(conversationId);
        LambdaQueryWrapper<ConversationMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationMessage::getConversationId, conversationId);
        conversationMessageMapper.delete(wrapper);

        Conversation reset = new Conversation();
        reset.setId(conversationId);
        reset.setLastMessagePreview(null);
        reset.setLastSenderRole(null);
        reset.setLastSenderName(null);
        reset.setLastSenderAvatar(null);
        reset.setLastMessageTime(null);
        reset.setUnreadForParticipant2(0);
        reset.setUnreadForPatient(0);
        reset.setSummary("暂无对话，等待新的消息");

        boolean updated = updateById(reset);
        if (updated) {
            evictConversationCache(conversationId);
            // 清除相关用户的会话列表缓存
            if (conversation != null) {
                evictConversationListCache(conversation);
            }
            evictConversationMessageCache(conversationId);
        }
        return updated;
    }

    @Override
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteConversation(Long conversationId) {
        throw new IllegalArgumentException("请使用 deleteConversationByRole 方法，需要指定删除角色");
    }

    /**
     * 根据角色删除会话（单方面删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteConversationByRole(Long conversationId, String role) {
        Conversation conversation = ensureConversationExists(conversationId);

        String normalizedRole = role != null ? role.toUpperCase() : "";
        Conversation update = new Conversation();
        update.setId(conversationId);

        // 参与者删除标记：participant1 = PATIENT，participant2 = 对方（管理员对应 ADMIN）
        if ("DOCTOR".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) {
            update.setDeletedByParticipant2(1);
        } else if ("PATIENT".equals(normalizedRole)) {
            // 向后兼容：保留 deleted_by_patient，同时也同步 deleted_by_participant1
            update.setDeletedByPatient(1);
            update.setDeletedByParticipant1(1);
        } else {
            throw new IllegalArgumentException("无效的角色: " + role);
        }

        boolean updated = updateById(update);

        if (updated) {
            evictConversationCache(conversationId);
            // 清除相关用户的会话列表缓存
            // 重新获取最新状态，因为上面已经更新了删除标记
            Conversation latestConversation = getById(conversationId);
            if (latestConversation != null) {
                evictConversationListCache(latestConversation);
            }
            log.info("标记删除会话成功: conversationId={}, role={}", conversationId, role);
        } else {
            log.warn("标记删除会话失败: conversationId={}, role={}", conversationId, role);
        }

        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteAllConversations(Long participantId, String role) {
        if (participantId == null) {
            throw new IllegalArgumentException("参与者ID不能为空");
        }
        if (!StringUtils.hasText(role)) {
            throw new IllegalArgumentException("角色不能为空");
        }

        String normalizedRole = role.toUpperCase();
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();

        // 根据角色查询对应的会话（不包括已被删除的）
        if ("DOCTOR".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) {
            wrapper.eq(Conversation::getParticipant2UserId, participantId);
            wrapper.and(q -> q.isNull(Conversation::getDeletedByParticipant2)
                    .or().eq(Conversation::getDeletedByParticipant2, 0));
        } else if ("PATIENT".equals(normalizedRole)) {
            wrapper.eq(Conversation::getParticipant1UserId, participantId);
            wrapper.and(q -> q.isNull(Conversation::getDeletedByParticipant1)
                    .or().eq(Conversation::getDeletedByParticipant1, 0));
        } else {
            throw new IllegalArgumentException("无效的角色: " + role);
        }

        // 查询所有要删除的会话
        List<Conversation> conversations = list(wrapper);
        if (conversations.isEmpty()) {
            log.info("没有找到要删除的会话: participantId={}, role={}", participantId, role);
            return true;
        }

        // 批量标记删除
        for (Conversation conversation : conversations) {
            Conversation update = new Conversation();
            update.setId(conversation.getId());
            if ("DOCTOR".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) {
                update.setDeletedByParticipant2(1);
            } else {
                update.setDeletedByPatient(1);        // 向后兼容
                update.setDeletedByParticipant1(1);  // 新字段
            }
            updateById(update);
            evictConversationCache(conversation.getId());
            // 清除相关用户的会话列表缓存
            evictConversationListCache(conversation);
        }

        log.info("批量标记删除会话成功: participantId={}, role={}, count={}", participantId, role, conversations.size());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markConversationAsRead(Long conversationId, Long userId, String role) {
        Conversation conversation = ensureConversationExists(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在");
        }

        Long readerUserId = resolveReaderUserId(conversation, userId, role);
        String normalizedRole = role != null ? role.toUpperCase() : null;

        if (readerUserId == null && !StringUtils.hasText(normalizedRole)) {
            throw new IllegalArgumentException("缺少有效的阅读者身份信息");
        }

        Conversation update = new Conversation();
        update.setId(conversationId);
        boolean changed = false;

        if (readerUserId != null) {
            if (Objects.equals(conversation.getParticipant1UserId(), readerUserId)
                    && Objects.requireNonNullElse(conversation.getUnreadForParticipant1(), 0) > 0) {
                update.setUnreadForParticipant1(0);
                changed = true;
            }
            if (Objects.equals(conversation.getParticipant2UserId(), readerUserId)
                    && Objects.requireNonNullElse(conversation.getUnreadForParticipant2(), 0) > 0) {
                update.setUnreadForParticipant2(0);
                changed = true;
            }
        }

        if (normalizedRole == null && readerUserId != null) {
            if (Objects.equals(conversation.getPatientId(), readerUserId)
                    || (conversation.getParticipant1Role() != null
                        && "PATIENT".equalsIgnoreCase(conversation.getParticipant1Role())
                        && Objects.equals(conversation.getParticipant1UserId(), readerUserId))) {
                normalizedRole = "PATIENT";
            } else if (Objects.equals(conversation.getParticipant2UserId(), readerUserId)
                    || (conversation.getParticipant2Role() != null
                        && ("DOCTOR".equalsIgnoreCase(conversation.getParticipant2Role())
                            || "ADMIN".equalsIgnoreCase(conversation.getParticipant2Role()))
                        && Objects.equals(conversation.getParticipant2UserId(), readerUserId))) {
                normalizedRole = conversation.getParticipant2Role() != null
                        ? conversation.getParticipant2Role().toUpperCase()
                        : "DOCTOR";
            }
        }

        if ("PATIENT".equals(normalizedRole)) {
            if (Objects.requireNonNullElse(conversation.getUnreadForPatient(), 0) > 0) {
                update.setUnreadForPatient(0);
                changed = true;
            }
        } else if ("DOCTOR".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) {
            if (Objects.requireNonNullElse(conversation.getUnreadForParticipant2(), 0) > 0) {
                update.setUnreadForParticipant2(0);
                changed = true;
            }
        }

        if (!changed) {
            return true;
        }

        boolean updated = updateById(update);
        if (updated) {
            updateConversationCache(conversationId, update);
            evictConversationListCache(conversation);
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllConversationsAsRead(Long userId, String role) {
        if (userId == null && !StringUtils.hasText(role)) {
            throw new IllegalArgumentException("缺少有效的阅读者身份信息");
        }

        String normalizedRole = role != null ? role.toUpperCase() : null;

        // 查询用户的所有未读会话
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();

        // 根据userId查询
        if (userId != null) {
            wrapper.and(q -> q.eq(Conversation::getParticipant1UserId, userId)
                    .or().eq(Conversation::getParticipant2UserId, userId));
            // 过滤掉被用户删除的会话
            wrapper.and(q -> q.and(w -> w.eq(Conversation::getParticipant1UserId, userId)
                            .and(sub -> sub.isNull(Conversation::getDeletedByParticipant1)
                                    .or().eq(Conversation::getDeletedByParticipant1, 0)))
                    .or(w -> w.eq(Conversation::getParticipant2UserId, userId)
                            .and(sub -> sub.isNull(Conversation::getDeletedByParticipant2)
                                    .or().eq(Conversation::getDeletedByParticipant2, 0))));
        }

        // 根据角色查询（向后兼容）
        if (normalizedRole != null) {
            if ("PATIENT".equals(normalizedRole)) {
                wrapper.and(q -> q.isNull(Conversation::getDeletedByPatient)
                        .or().eq(Conversation::getDeletedByPatient, 0));
                wrapper.and(q -> q.gt(Conversation::getUnreadForPatient, 0)
                        .or().and(w -> w.eq(Conversation::getParticipant1Role, "PATIENT")
                                .gt(Conversation::getUnreadForParticipant1, 0))
                        .or().and(w -> w.eq(Conversation::getParticipant2Role, "PATIENT")
                                .gt(Conversation::getUnreadForParticipant2, 0)));
            } else if ("DOCTOR".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) {
                wrapper.and(q -> q.isNull(Conversation::getDeletedByParticipant2)
                        .or().eq(Conversation::getDeletedByParticipant2, 0));
                wrapper.and(q -> q.gt(Conversation::getUnreadForParticipant2, 0)
                        .or().and(w -> w.eq(Conversation::getParticipant1Role, normalizedRole)
                                .gt(Conversation::getUnreadForParticipant1, 0))
                        .or().and(w -> w.eq(Conversation::getParticipant2Role, normalizedRole)
                                .gt(Conversation::getUnreadForParticipant2, 0)));
            }
        }

        List<Conversation> conversations = list(wrapper);
        if (conversations.isEmpty()) {
            log.info("没有找到需要标记为已读的会话: userId={}, role={}", userId, role);
            return 0;
        }

        int count = 0;
        for (Conversation conversation : conversations) {
            try {
                boolean success = markConversationAsRead(conversation.getId(), userId, role);
                if (success) {
                    count++;
                }
            } catch (Exception e) {
                log.warn("标记会话已读失败: conversationId={}, userId={}, role={}, error={}",
                        conversation.getId(), userId, role, e.getMessage());
            }
        }

        log.info("批量标记会话已读成功: userId={}, role={}, total={}, success={}",
                userId, role, conversations.size(), count);
        return count;
    }

    private Page<Conversation> buildPage(Map<String, Object> params) {
        int page = parseInt(params.get("page"), 1);
        int pageSize = parseInt(params.get("pageSize"), SystemConstants.DEFAULT_PAGE_SIZE);
        pageSize = Math.min(pageSize, SystemConstants.MAX_PAGE_SIZE);
        return new Page<>(page, pageSize);
    }

    private Page<ConversationMessage> buildMessagePage(Map<String, Object> params) {
        int page = parseInt(params.get("page"), 1);
        int pageSize = parseInt(params.get("pageSize"), 50);
        pageSize = Math.min(pageSize, SystemConstants.MAX_PAGE_SIZE);
        return new Page<>(page, pageSize);
    }

    private Conversation ensureConversationExists(Long conversationId) {
        // 先尝试从缓存获取
        String cacheKey = CacheConstants.CACHE_CONVERSATION_PREFIX + conversationId;
        Object cached = redisUtil.get(cacheKey);
        if (cached != null && cached instanceof Conversation) {
            return (Conversation) cached;
        }

        // 缓存未命中，从数据库查询
        Conversation conversation = getById(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在");
        }

        // 存入缓存
        redisUtil.set(cacheKey, conversation, CacheConstants.CACHE_CONVERSATION_TTL_SECONDS, TimeUnit.SECONDS);

        return conversation;
    }

    /**
     * 清除会话缓存（在更新会话信息后调用）
     */
    private void evictConversationCache(Long conversationId) {
        String cacheKey = CacheConstants.CACHE_CONVERSATION_PREFIX + conversationId;
        redisUtil.delete(cacheKey);
    }

    /**
     * 更新会话缓存（方案1：更新缓存而不是清除缓存）
     * 从数据库重新查询最新的会话信息并更新缓存
     */
    private void updateConversationCache(Long conversationId, Conversation update) {
        try {
            // 重新查询最新的会话信息
            Conversation latest = getById(conversationId);
            if (latest != null) {
                String cacheKey = CacheConstants.CACHE_CONVERSATION_PREFIX + conversationId;
                redisUtil.set(cacheKey, latest, CacheConstants.CACHE_CONVERSATION_TTL_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("更新会话缓存失败，降级为清除缓存: conversationId={}, error={}", conversationId, e.getMessage());
            // 降级：如果更新失败，清除缓存
            evictConversationCache(conversationId);
        }
    }

    /**
     * 清除相关用户的会话列表缓存（方案2）
     * 当会话信息更新时，清除所有相关用户的会话列表缓存
     */
    private void evictConversationListCache(Conversation conversation) {
        try {
            // 清除participant1的会话列表缓存
            if (conversation.getParticipant1UserId() != null) {
                String pattern1 = CacheConstants.CACHE_CONVERSATION_LIST_PREFIX + conversation.getParticipant1UserId() + ":*";
                redisUtil.deleteByPattern(pattern1);
                String countKey1 = CacheConstants.CACHE_CONVERSATION_COUNT_PREFIX + conversation.getParticipant1UserId();
                redisUtil.delete(countKey1);
            }

            // 清除participant2的会话列表缓存
            if (conversation.getParticipant2UserId() != null) {
                String pattern2 = CacheConstants.CACHE_CONVERSATION_LIST_PREFIX + conversation.getParticipant2UserId() + ":*";
                redisUtil.deleteByPattern(pattern2);
                String countKey2 = CacheConstants.CACHE_CONVERSATION_COUNT_PREFIX + conversation.getParticipant2UserId();
                redisUtil.delete(countKey2);
            }

            // 向后兼容：清除patientId的缓存
            if (conversation.getPatientId() != null) {
                String pattern3 = CacheConstants.CACHE_CONVERSATION_LIST_PREFIX + conversation.getPatientId() + ":*";
                redisUtil.deleteByPattern(pattern3);
                String countKey3 = CacheConstants.CACHE_CONVERSATION_COUNT_PREFIX + conversation.getPatientId();
                redisUtil.delete(countKey3);
            }
            // 不再使用 doctorId 缓存键
        } catch (Exception e) {
            log.warn("清除会话列表缓存失败: conversationId={}, error={}", conversation.getId(), e.getMessage());
        }
    }

    private void evictConversationMessageCache(Long conversationId) {
        try {
            String pattern = CacheConstants.CACHE_CONVERSATION_MESSAGE_LIST_PREFIX + conversationId + ":*";
            redisUtil.deleteByPattern(pattern);
        } catch (Exception e) {
            log.warn("清除会话消息缓存失败: conversationId={}, error={}", conversationId, e.getMessage());
        }
    }

    private void refreshConversationSnapshot(Conversation conversation, ConversationMessage message) {
        Conversation update = new Conversation();
        update.setId(conversation.getId());
        update.setLastMessagePreview(buildPreview(message.getContent()));
        update.setLastSenderRole(message.getSenderRole());
        update.setLastSenderName(message.getSenderName());
        update.setLastSenderAvatar(message.getSenderAvatar());
        update.setLastMessageTime(message.getSentAt());

        String role = message.getSenderRole() != null ? message.getSenderRole().toUpperCase() : "SYSTEM";
        int unreadForParticipant1 = Objects.requireNonNullElse(conversation.getUnreadForParticipant1(), 0);
        int unreadForParticipant2 = Objects.requireNonNullElse(conversation.getUnreadForParticipant2(), 0);

        // 获取发送者的用户ID
        Long senderUserId = getSenderUserId(message, role);

        // 优先使用新字段更新未读数（只有接收者增加未读数）
        Long participant1UserId = conversation.getParticipant1UserId();
        Long participant2UserId = conversation.getParticipant2UserId();

        if (senderUserId != null && participant1UserId != null && participant2UserId != null) {
            // 使用新字段：发送者不增加未读数，只有接收者增加未读数
            if (participant1UserId.equals(senderUserId)) {
                // 发送者是participant1，接收者是participant2，增加participant2的未读数
                update.setUnreadForParticipant2(unreadForParticipant2 + 1);
                update.setUnreadForParticipant1(0); // 发送者未读数清零
            } else if (participant2UserId.equals(senderUserId)) {
                // 发送者是participant2，接收者是participant1，增加participant1的未读数
                update.setUnreadForParticipant1(unreadForParticipant1 + 1);
                update.setUnreadForParticipant2(0); // 发送者未读数清零
            }
        }

        // 旧版 doctor/patient 字段已移除：这里仅保留 participant1/participant2 的未读更新逻辑
        if ("ADMIN".equals(role)) {
            // 管理员发送消息，更新接收者的未读数（向后兼容）
            if (participant1UserId != null && participant1UserId.equals(senderUserId)) {
                update.setUnreadForParticipant2(unreadForParticipant2 + 1);
                update.setUnreadForParticipant1(0);
            } else if (participant2UserId != null && participant2UserId.equals(senderUserId)) {
                update.setUnreadForParticipant1(unreadForParticipant1 + 1);
                update.setUnreadForParticipant2(0);
            }
        }

        updateById(update);

        // 方案1：更新缓存而不是清除缓存
        updateConversationCache(conversation.getId(), update);

        // 方案2：清除相关用户的会话列表缓存（因为会话列表已更新）
        evictConversationListCache(conversation);
    }

    private void publishConversationEvent(Conversation conversation, ConversationMessage message) {
        try {
            Long doctorUserId = null;
            Long participant1UserId = conversation.getParticipant1UserId();
            Long participant2UserId = conversation.getParticipant2UserId();

            // 优先使用新字段
            if (participant1UserId != null && participant2UserId != null) {
                // 根据对话类型和参与者角色确定doctorUserId
                if ("PATIENT_DOCTOR".equals(conversation.getConversationType())) {
                    // 患者-医生对话：participant2是医生
                    if ("DOCTOR".equals(conversation.getParticipant2Role())) {
                        doctorUserId = participant2UserId;
                    }
                } else if ("ADMIN_USER".equals(conversation.getConversationType())) {
                    // 管理员-用户对话：participant2是管理员
                    if ("ADMIN".equals(conversation.getParticipant2Role())) {
                        doctorUserId = participant2UserId;
                    }
                }
            }

            // doctor 旧字段已删除：doctorUserId 仅由 participant2Role 推导

            String entityType = null;
            String senderRole = message.getSenderRole();
            if ("PATIENT".equalsIgnoreCase(senderRole)) {
                entityType = "patient";
            } else if ("DOCTOR".equalsIgnoreCase(senderRole)) {
                entityType = "doctor";
            } else if ("ADMIN".equalsIgnoreCase(senderRole)) {
                entityType = "admin";
            }
            String resolvedSenderAvatar = resolveAvatarUrl(message.getSenderAvatar(), message.getSenderId(), entityType);

            ConversationMessageEventDTO event = ConversationMessageEventDTO.builder()
                    .conversationId(conversation.getId())
                    .messageId(message.getId())
                    .patientId(conversation.getPatientId()) // 向后兼容
                    .doctorId(conversation.getParticipant2UserId()) // 向后兼容：doctorId 语义统一为对方用户ID
                    .doctorUserId(doctorUserId) // 向后兼容
                    .conversationType(conversation.getConversationType())
                    .participant1UserId(conversation.getParticipant1UserId())
                    .participant1Role(conversation.getParticipant1Role())
                    .participant2UserId(conversation.getParticipant2UserId())
                    .participant2Role(conversation.getParticipant2Role())
                    .senderId(message.getSenderId())
                    .senderRole(message.getSenderRole())
                    .senderName(message.getSenderName())
                    .senderAvatar(resolvedSenderAvatar)
                    .content(message.getContent())
                    .contentType(message.getContentType())
                    .sentAt(message.getSentAt())
                    .lastMessagePreview(buildPreview(message.getContent()))
                    .build();

            conversationMessagePublisher.publish(event);
        } catch (Exception e) {
            log.error("会话消息事件发布失败：conversationId={}, messageId={}",
                    conversation.getId(), message.getId(), e);
        }
    }

    private String buildPreview(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String trimmed = content.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 117) + "..." : trimmed;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.warn("无法解析为Long：{}", value);
            return null;
        }
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            log.warn("无法解析为int：{}", value);
            return defaultValue;
        }
    }

    private String defaultSenderName(String role, Conversation conversation) {
        String normalized = role != null ? role.toUpperCase() : "";
        if ("DOCTOR".equals(normalized)) {
            // doctor 旧字段已删除：用 participant2Role 映射通用文案
            return "ADMIN".equals(conversation.getParticipant2Role()) ? "管理员" : "对方用户";
        }
        if ("PATIENT".equals(normalized)) {
            return conversation.getPatientNickname();
        }
        return "系统助手";
    }

    private String defaultSenderAvatar(String role, Conversation conversation) {
        String normalized = role != null ? role.toUpperCase() : "";
        if ("DOCTOR".equals(normalized)) {
            return null; // doctor 旧字段已删除，头像由前端/其它逻辑解析
        }
        if ("PATIENT".equals(normalized)) {
            return conversation.getPatientAvatar();
        }
        return null;
    }

    /**
     * 去除头像URL中的过期签名参数，确保可以重新生成新的签名URL
     */
    private String sanitizeAvatarUrl(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl)) {
            return null;
        }
        String sanitized = avatarUrl.trim();
        boolean containsSignatureParam = sanitized.contains("Signature=") || sanitized.contains("OSSAccessKeyId=")
                || sanitized.contains("Expires=");
        if (containsSignatureParam) {
            int questionIndex = sanitized.indexOf('?');
            if (questionIndex > 0) {
                sanitized = sanitized.substring(0, questionIndex);
            } else {
                int ampIndex = sanitized.indexOf('&');
                if (ampIndex > 0) {
                    sanitized = sanitized.substring(0, ampIndex);
                }
            }
        }
        return sanitized;
    }

    /**
     * 发送消息通知提醒
     * 根据新表结构，只有接收信息的人才会收到未读消息提醒
     */
    private void sendMessageNotification(Conversation conversation, ConversationMessage message) {
        try {
            String senderRole = message.getSenderRole() != null ? message.getSenderRole().toUpperCase() : "";
            String senderName = message.getSenderName() != null ? message.getSenderName() : "未知用户";
            String preview = buildPreview(message.getContent());

            // 发送方用户ID（participant2/participant1 逻辑统一由 senderId + senderRole 决定）
            Long senderUserId = getSenderUserId(message, senderRole);
            if (senderUserId == null) {
                log.warn("无法获取发送者用户ID，跳过通知：conversationId={}, messageId={}, senderRole={}, senderId={}",
                        conversation.getId(), message.getId(), senderRole, message.getSenderId());
                return;
            }

            Long participant1UserId = conversation.getParticipant1UserId();
            Long participant2UserId = conversation.getParticipant2UserId();

            if (participant1UserId == null || participant2UserId == null) {
                log.warn("会话 participant 为空，跳过通知：conversationId={}, participant1={}, participant2={}",
                        conversation.getId(), participant1UserId, participant2UserId);
                return;
            }

            // 接收方 = 发送方的另一个 participant
            Long receiverUserId;
            if (participant1UserId.equals(senderUserId)) {
                receiverUserId = participant2UserId;
            } else if (participant2UserId.equals(senderUserId)) {
                receiverUserId = participant1UserId;
            } else {
                log.warn("发送者用户ID与participant不匹配，跳过通知：conversationId={}, senderUserId={}, participant1UserId={}, participant2UserId={}",
                        conversation.getId(), senderUserId, participant1UserId, participant2UserId);
                return;
            }

            if (senderUserId.equals(receiverUserId)) {
                return;
            }

            // 已移除通知模块：此处仅记录日志（不再区分 DOCTOR_REPLY 等通知类型）
            log.info("对话消息通知（跳过发送）：receiverUserId={}, senderUserId={}, senderName={}, conversationId={}, preview={}",
                    receiverUserId, senderUserId, senderName, conversation.getId(), preview);
        } catch (Exception e) {
            log.error("发送对话消息通知失败：conversationId={}, messageId={}, error={}",
                    conversation.getId(), message.getId(), e.getMessage(), e);
        }
    }

    /**
     * 获取发送者的用户ID
     */
    private Long getSenderUserId(ConversationMessage message, String senderRole) {
        if ("PATIENT".equals(senderRole) || "ADMIN".equals(senderRole)) {
            // 患者和管理员：senderId就是用户ID
            return message.getSenderId();
        } else if ("DOCTOR".equals(senderRole)) {
            // 医生身份保留兼容：senderId 直接作为用户ID
            return message.getSenderId();
        }
        return null;
    }

    /**
     * 生成可直接访问的头像URL
     */
    private String resolveAvatarUrl(String rawAvatar, Long entityId, String entityType) {
        String sanitizedAvatar = sanitizeAvatarUrl(rawAvatar);
        if (StringUtils.hasText(sanitizedAvatar)) {
            // 尝试从缓存获取签名URL
            String cacheKey = CacheConstants.CACHE_OSS_SIGNED_URL_PREFIX + sanitizedAvatar;
            Object cached = redisUtil.get(cacheKey);
            if (cached != null && cached instanceof String) {
                return (String) cached;
            }

            try {
                String signedUrl = ossService.generatePresignedUrl(sanitizedAvatar, avatarConfig.getTtlMinutes());
                if (StringUtils.hasText(signedUrl)) {
                    // 存入缓存
                    redisUtil.set(cacheKey, signedUrl, CacheConstants.CACHE_OSS_SIGNED_URL_TTL_SECONDS, TimeUnit.SECONDS);
                    return signedUrl;
                }
            } catch (Exception e) {
                log.warn("生成头像签名URL失败: avatar={}, error={}", sanitizedAvatar, e.getMessage());
                return sanitizedAvatar;
            }
        }
        // 使用默认头像
        if ("patient".equals(entityType) && entityId != null) {
            return avatarConfig.getDefaultPatient() + "&seed=" + entityId;
        }
        if ("doctor".equals(entityType) && entityId != null) {
            return avatarConfig.getDefaultDoctor() + "&seed=" + entityId;
        }
        if ("admin".equals(entityType) && entityId != null) {
            return avatarConfig.getDefaultAdmin() + "&seed=" + entityId;
        }
        return avatarConfig.getDefaultPatient();
    }

    @Override
    public List<Map<String, Object>> getRecentSenders(Long conversationId) {
        ensureConversationExists(conversationId);

        // 查询最近的消息，按发送时间倒序，获取不同的发送者
        LambdaQueryWrapper<ConversationMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationMessage::getConversationId, conversationId)
                .eq(ConversationMessage::getDeleted, 0)
                .orderByDesc(ConversationMessage::getSentAt)
                .last("LIMIT 100"); // 先取最近100条消息，然后去重

        List<ConversationMessage> messages = conversationMessageMapper.selectList(wrapper);

        // 去重，保留每个发送者的最新一条消息
        Map<String, ConversationMessage> uniqueSenders = new LinkedHashMap<>();
        for (ConversationMessage message : messages) {
            String senderKey = message.getSenderId() + "_" + message.getSenderRole();
            if (!uniqueSenders.containsKey(senderKey)) {
                uniqueSenders.put(senderKey, message);
            }
        }

        // 取前三个，转换为Map列表
        List<Map<String, Object>> result = new ArrayList<>();
        int count = 0;
        for (ConversationMessage message : uniqueSenders.values()) {
            if (count >= 3) break;

            Map<String, Object> senderInfo = new HashMap<>();
            senderInfo.put("senderId", message.getSenderId());
            senderInfo.put("senderRole", message.getSenderRole());
            senderInfo.put("senderName", message.getSenderName());

            String entityType = null;
            if ("PATIENT".equals(message.getSenderRole())) {
                entityType = "patient";
            } else if ("DOCTOR".equals(message.getSenderRole())) {
                entityType = "doctor";
            } else if ("ADMIN".equals(message.getSenderRole())) {
                entityType = "admin";
            }
            senderInfo.put("senderAvatar", resolveAvatarUrl(message.getSenderAvatar(), message.getSenderId(), entityType));

            result.add(senderInfo);
            count++;
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Conversation getOrCreateAdminConversation(Long senderId) {
        if (senderId == null) {
            throw new IllegalArgumentException("发送者ID不能为空");
        }

        // 查找一个管理员用户（roleType=1）
        LambdaQueryWrapper<User> adminWrapper = new LambdaQueryWrapper<>();
        adminWrapper.eq(User::getRoleType, 1)
                .eq(User::getStatus, 1)
                .last("LIMIT 1");
        User admin = userMapper.selectOne(adminWrapper);

        if (admin == null) {
            throw new IllegalArgumentException("系统中没有可用的管理员");
        }

        // 查询发送者用户信息
        User sender = userMapper.selectById(senderId);
        if (sender == null) {
            throw new IllegalArgumentException("发送者不存在");
        }

        // 查询是否已存在该发送者与管理员对话的会话
        // 使用patientId存储发送者ID，doctorId存储管理员ID（虽然字段名不太合适，但可以工作）
        // 为了区分管理员对话，我们可以使用一个特殊的标记，比如doctorId为-1或使用admin的ID
        // 这里我们使用admin的ID作为doctorId，但需要确保不会与真实的医生ID冲突
        // 更好的方式是添加一个conversation_type字段，但为了快速实现，我们使用现有字段

        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getPatientId, senderId)
                .eq(Conversation::getParticipant2UserId, admin.getId()) // 管理员放在 participant2
                .eq(Conversation::getStatus, "ACTIVE");
        Conversation existed = getOne(wrapper, false);

        if (existed != null) {
            // 如果会话存在但被发送者删除，恢复它
            if (existed.getDeletedByPatient() != null && existed.getDeletedByPatient() == 1) {
                existed.setDeletedByPatient(0);
                updateById(existed);
                evictConversationCache(existed.getId());
                evictConversationListCache(existed);
            }
            // 为已存在的会话对象生成签名头像URL
            existed.setPatientAvatar(resolveAvatarUrl(existed.getPatientAvatar(), existed.getPatientId(),
                    getEntityTypeByRole(sender.getRoleType())));
            return existed;
        }

        // 创建新的管理员对话会话
        Conversation conversation = new Conversation();
        conversation.setPatientId(senderId); // 发送者ID（向后兼容）
        conversation.setConversationType("ADMIN_USER");
        conversation.setParticipant1UserId(senderId);
        conversation.setParticipant1Role("PATIENT");
        conversation.setParticipant2UserId(admin.getId());
        conversation.setParticipant2Role("ADMIN");

        // 设置发送者信息
        String senderName = StringUtils.hasText(sender.getRealName()) ? sender.getRealName() : sender.getUsername();
        conversation.setPatientNickname(senderName);
        conversation.setPatientAvatar(sender.getAvatar());

        // 设置标题和摘要
        conversation.setTitle(senderName + " x 管理员");
        conversation.setSummary("与管理员对话");

        conversation.setStatus("ACTIVE");
        conversation.setUnreadForPatient(0);
        conversation.setUnreadForParticipant1(0);
        conversation.setUnreadForParticipant2(0);
        conversation.setDeletedByPatient(0);
        conversation.setDeletedByParticipant1(0);
        conversation.setDeletedByParticipant2(0);

        save(conversation);

        // 清除会话缓存（新创建的会话）
        evictConversationCache(conversation.getId());
        // 清除相关用户的会话列表缓存（新创建的会话会影响列表）
        evictConversationListCache(conversation);

        // 为返回的会话对象生成签名头像URL
        conversation.setPatientAvatar(resolveAvatarUrl(conversation.getPatientAvatar(), conversation.getPatientId(),
                getEntityTypeByRole(sender.getRoleType())));

        log.info("创建管理员对话会话成功: conversationId={}, senderId={}, adminId={}",
                conversation.getId(), senderId, admin.getId());

        return conversation;
    }

    /**
     * 根据角色类型获取实体类型（用于头像解析）
     */
    private String getEntityTypeByRole(Integer roleType) {
        if (roleType == null) {
            return "patient";
        }
        if (roleType == 0) {
            return "patient";
        }
        if (roleType == 1) {
            return "admin";
        }
        if (roleType == 2) {
            return "doctor";
        }
        return "patient";
    }

    /**
     * 根据传入的参数解析出阅读者的用户ID
     */
    private Long resolveReaderUserId(Conversation conversation, Long userId, String role) {
        if (userId != null) {
            return userId;
        }

        String normalizedRole = role != null ? role.toUpperCase() : null;
        if ("PATIENT".equals(normalizedRole)) {
            if (conversation.getParticipant1Role() != null
                    && "PATIENT".equalsIgnoreCase(conversation.getParticipant1Role())
                    && conversation.getParticipant1UserId() != null) {
                return conversation.getParticipant1UserId();
            }
            return conversation.getPatientId();
        }

        if ("DOCTOR".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) {
            if (conversation.getParticipant1Role() != null
                    && normalizedRole.equalsIgnoreCase(conversation.getParticipant1Role())
                    && conversation.getParticipant1UserId() != null) {
                return conversation.getParticipant1UserId();
            }
            if (conversation.getParticipant2Role() != null
                    && normalizedRole.equalsIgnoreCase(conversation.getParticipant2Role())
                    && conversation.getParticipant2UserId() != null) {
                return conversation.getParticipant2UserId();
            }
            return conversation.getParticipant2UserId();
        }

        return userId;
    }
}


