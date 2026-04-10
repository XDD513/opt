-- 九种中医体质字典：submitTest 依赖本表有数据，否则体质得分列表为空会直接失败。
-- 可重复执行：仅当对应 type_code 不存在时插入。

INSERT INTO tcm_constitution_type (type_code, type_name, sort_order, description, created_at, updated_at)
SELECT 'PINGHE', '平和质', 1, '九种体质辨识-平和质', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tcm_constitution_type WHERE type_code = 'PINGHE' LIMIT 1);

INSERT INTO tcm_constitution_type (type_code, type_name, sort_order, description, created_at, updated_at)
SELECT 'QIXU', '气虚质', 2, '九种体质辨识-气虚质', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tcm_constitution_type WHERE type_code = 'QIXU' LIMIT 1);

INSERT INTO tcm_constitution_type (type_code, type_name, sort_order, description, created_at, updated_at)
SELECT 'YANGXU', '阳虚质', 3, '九种体质辨识-阳虚质', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tcm_constitution_type WHERE type_code = 'YANGXU' LIMIT 1);

INSERT INTO tcm_constitution_type (type_code, type_name, sort_order, description, created_at, updated_at)
SELECT 'YINXU', '阴虚质', 4, '九种体质辨识-阴虚质', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tcm_constitution_type WHERE type_code = 'YINXU' LIMIT 1);

INSERT INTO tcm_constitution_type (type_code, type_name, sort_order, description, created_at, updated_at)
SELECT 'TANSHI', '痰湿质', 5, '九种体质辨识-痰湿质', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tcm_constitution_type WHERE type_code = 'TANSHI' LIMIT 1);

INSERT INTO tcm_constitution_type (type_code, type_name, sort_order, description, created_at, updated_at)
SELECT 'SHIRE', '湿热质', 6, '九种体质辨识-湿热质', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tcm_constitution_type WHERE type_code = 'SHIRE' LIMIT 1);

INSERT INTO tcm_constitution_type (type_code, type_name, sort_order, description, created_at, updated_at)
SELECT 'XUEYU', '血瘀质', 7, '九种体质辨识-血瘀质', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tcm_constitution_type WHERE type_code = 'XUEYU' LIMIT 1);

INSERT INTO tcm_constitution_type (type_code, type_name, sort_order, description, created_at, updated_at)
SELECT 'QIYU', '气郁质', 8, '九种体质辨识-气郁质', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tcm_constitution_type WHERE type_code = 'QIYU' LIMIT 1);

INSERT INTO tcm_constitution_type (type_code, type_name, sort_order, description, created_at, updated_at)
SELECT 'TEBING', '特禀质', 9, '九种体质辨识-特禀质', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tcm_constitution_type WHERE type_code = 'TEBING' LIMIT 1);
