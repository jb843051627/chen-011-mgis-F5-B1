-- ----------------------------------------------------------------------------
-- 早年「成果提供使用逐级核准单」t_mgis_use_bill 的票籍表（补账）
--
-- 来由：旧脚手架 approve/reject/rollback 三法只挪 node_no，签署的票不往
-- 账上落——哪位签过、签的什么意见、打回后旧票销没销，一概无从查。
-- 这张表把每一笔记进账：
--   * approve 落 APPROVE 票：同层同趟同一人只认头一张（幂等），两人联名档
--     （sign_mode=1）须不同的人签齐 need_count 张才放行，一人两笔不算数；
--   * reject 落 REJECT 票并把单据置「已打回」(2)，单据就此定死；
--   * rollback 落 ROLLBACK 票，退出的那一层旧票整批 active 置 0（旧票籍
--     销档，链上仍在），再上来按新一趟重签，不许拿旧票凑齐额。
-- 老库执行本脚本即可；新库按 doc/schema/mgis.sql 全量建表已带出。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `t_mgis_use_bill_sign` (
  `id`         BIGINT       NOT NULL COMMENT '主键',
  `bill_id`    BIGINT       NOT NULL COMMENT '核准单主键',
  `node_no`    INT          NOT NULL COMMENT '落笔时所在层 0..1(签批层)',
  `batch_no`   INT          NOT NULL DEFAULT 1 COMMENT '第几趟到这一层(打回后再上来一趟加一)',
  `action`     VARCHAR(16)  NOT NULL COMMENT '票种 APPROVE签批 REJECT打回 ROLLBACK退回',
  `signer`     VARCHAR(64)  NOT NULL COMMENT '签署经办(自登录态写死,不许代报)',
  `comment`    VARCHAR(500) NULL COMMENT '签批意见/打回或退回缘由',
  `active`     INT          NOT NULL DEFAULT 1 COMMENT '还算不算数 1算数 0随打回销档(票仍在链上)',
  `create_by`  VARCHAR(64)  NULL,
  `create_time` DATETIME    NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_use_bill_sign_point` (`bill_id`, `node_no`, `batch_no`, `signer`, `action`),
  KEY `idx_use_bill_sign_bill` (`bill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成果提供使用逐级核准单票籍(只立;打回销档不删行)';
