-- ----------------------------------------------------------------------------
-- 成果提供使用逐级核准单·签署明细（旧单补账）
--
-- 早年的 t_mgis_use_bill 只在单头存一个 sign_count，哪位签过、签的什么话
-- 无从查，联名一档落一个人也放得行，退回上一环节旧票数不清、第二报凑着
-- 旧票就齐了额。这张表把每一节点每一票落账，把这几处填平：
--   1. 签批人、同意与否、签的话、签批时辰逐票可查；
--   2. 同一节点同一人只认一票（唯一键 uk_use_bill_sign_point 兜底）；
--   3. 两名点齐（sign_mode=1）须两名不同的人各一票才放行；
--   4. 退回上一环节时，退回落地节点及更后各层旧票整批销籍，重报从头攒。
-- 旧方法签名一个不改；历年旧单头一回经新章程办时，以单头 sign_count 为
-- 旧账底数认下，落第一笔新票后两处口径归一。
-- 新库按 doc/schema/mgis.sql 全量建表已带出本表，老库执行本脚本即可。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `t_mgis_use_bill_sign` (
  `id`           BIGINT       NOT NULL COMMENT '主键',
  `bill_id`      BIGINT       NOT NULL COMMENT '核准单主键',
  `node_no`      INT          NOT NULL COMMENT '签在哪一层(与落笔时单据 node_no 同)',
  `approver`     VARCHAR(64)  NULL COMMENT '签批人(登录态是谁记谁)',
  `verdict`      INT          NOT NULL COMMENT '这一票 1同意 2否决',
  `comment_text` VARCHAR(500) NULL COMMENT '签批意见/否决缘由',
  `sign_at`      DATETIME     NOT NULL COMMENT '签批时辰(系统写死,以秒计)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_use_bill_sign_point` (`bill_id`, `node_no`, `approver`, `verdict`),
  KEY `idx_use_bill_sign_bill` (`bill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成果提供使用逐级核准单签署明细';
