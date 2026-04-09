-- Sync article status semantics with current backend implementation.
-- status: 0-待审核 1-已发布 2-已下架 3-已驳回

ALTER TABLE `content_article`
  MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态（0-待审核 1-已发布 2-已下架 3-已驳回）';

ALTER TABLE `content_article`
  MODIFY COLUMN `reject_reason` VARCHAR(500) NULL COMMENT '原因说明（审核驳回或下架时填写）';

