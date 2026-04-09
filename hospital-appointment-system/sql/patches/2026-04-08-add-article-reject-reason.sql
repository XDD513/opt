-- Add reject reason field for admin article review.
-- Table: content_article

ALTER TABLE content_article
  ADD COLUMN reject_reason VARCHAR(500) NULL COMMENT '驳回原因（审核不通过时填写）' AFTER status;

