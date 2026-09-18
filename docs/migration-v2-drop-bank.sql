-- Sentinel 3.0 v2 去银行化迁移脚本
-- 说明：
--   新增列（domain / url / app_name / contact_phone /
--           text_content / input_type / fraud_type / fraud_confidence）
--   由 JPA `ddl-auto: update` 自动创建，无需手动执行。
--
-- 以下语句用于删除遗留的银行列（Hibernate ddl-auto 不会自动删列）。
-- 执行前请先备份 log_events 表。

ALTER TABLE log_events
  DROP COLUMN receiver_bank,
  DROP COLUMN receiver_id,
  DROP COLUMN amount;
