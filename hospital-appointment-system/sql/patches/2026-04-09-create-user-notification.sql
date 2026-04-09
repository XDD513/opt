-- Create user notification table for article workflow and interaction events.

CREATE TABLE IF NOT EXISTS `user_notification` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '接收用户ID',
  `role_type` TINYINT NOT NULL DEFAULT 0 COMMENT '接收者角色（0-患者 1-管理员）',
  `title` VARCHAR(120) NOT NULL COMMENT '通知标题',
  `content` VARCHAR(1000) NOT NULL COMMENT '通知内容',
  `notification_type` VARCHAR(32) NOT NULL COMMENT '通知类型（REVIEW/OFFLINE/INTERACTION/SYSTEM）',
  `biz_type` VARCHAR(32) NOT NULL DEFAULT 'ARTICLE' COMMENT '业务类型',
  `biz_id` BIGINT NULL COMMENT '业务主键ID（文章ID）',
  `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读（0-未读 1-已读）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_read_created` (`user_id`, `is_read`, `created_at`),
  KEY `idx_role_read_created` (`role_type`, `is_read`, `created_at`),
  KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知表';

