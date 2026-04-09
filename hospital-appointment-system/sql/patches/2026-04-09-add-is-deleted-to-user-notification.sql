-- Add soft-delete support for user notifications.

ALTER TABLE `user_notification`
  ADD COLUMN `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除（0-否 1-是）' AFTER `is_read`;

ALTER TABLE `user_notification`
  ADD KEY `idx_user_deleted_created` (`user_id`, `is_deleted`, `created_at`);

