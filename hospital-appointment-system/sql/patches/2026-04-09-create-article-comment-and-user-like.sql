-- Create missing tables for article comments and likes.
-- Required by ArticleCommentMapper and UserLikeMapper.

CREATE TABLE IF NOT EXISTS `content_article_comment` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `article_id` BIGINT NOT NULL COMMENT '文章ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `user_name` VARCHAR(64) NULL COMMENT '用户姓名',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父评论ID（0表示顶级评论）',
  `content` VARCHAR(1000) NOT NULL COMMENT '评论内容',
  `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞次数',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态（0-待审核 1-已发布 2-已删除）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_article_parent_status` (`article_id`, `parent_id`, `status`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_parent_status` (`parent_id`, `status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章评论表';

CREATE TABLE IF NOT EXISTS `user_like` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `target_id` BIGINT NOT NULL COMMENT '目标ID（文章ID或评论ID）',
  `target_type` VARCHAR(32) NOT NULL COMMENT '目标类型（ARTICLE/COMMENT）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target_type` (`user_id`, `target_id`, `target_type`),
  KEY `idx_target_type_target_id` (`target_type`, `target_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户点赞记录表';
