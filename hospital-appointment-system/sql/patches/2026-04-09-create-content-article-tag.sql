-- Create article-tag relation table for health articles.
-- This table is required by ArticleTagMapper and HealthArticleServiceImpl.

CREATE TABLE IF NOT EXISTS `content_article_tag` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `article_id` BIGINT NULL COMMENT '文章ID（软删除时可置空）',
  `tag_name` VARCHAR(64) NOT NULL COMMENT '标签名称',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_tag` (`article_id`, `tag_name`),
  KEY `idx_article_id` (`article_id`),
  KEY `idx_tag_name` (`tag_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章标签关联表';
