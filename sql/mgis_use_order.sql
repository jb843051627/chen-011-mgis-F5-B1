-- ----------------------------------------------------------------------------
-- 用密成果领用申请（政务服务和监管处规矩落库）
--
-- 口径一句话：一张申请单从窗口递进到放成果分四阶——窗口受理、市局核验、
-- 省厅核准、窗口放成果。此路只经「签署」一道门：受理画押、核验、省厅两岗
-- 各签、发回、写明不予核准、放成果，全打签署门进，成与不成但凭服务层回传。
-- 省厅两岗各签各的，只签一枚件还停在第三步；省厅发回市局，前签一笔勾销，
-- 从市局那一阶重新攒起，废票与新票不许同堂并摆。哪一处写明不予核准则件定死；
-- 核准后各栏锁住，要动另起新件。签署笔数由件自己记（链上当场重算），
-- 单据上另存一份，两处须同一个数。核准件一键生领用清单，一件一条，
-- 字段全自核准那一份带。落笔链只增不改，顺拉倒拉各验一遍。
--
-- 早年脚手架 t_mgis_use_bill（approve/reject/rollback，无门脸）与本流程
-- 无涉，原样封存，不在本脚本改动。
-- ----------------------------------------------------------------------------

-- 申请单：一件对一单；单号系统给（YM 打头）
CREATE TABLE IF NOT EXISTS `t_mgis_use_order` (
  `id`                    BIGINT       NOT NULL COMMENT '主键',
  `order_no`              VARCHAR(64)  NOT NULL COMMENT '申请单号(YM打头,系统给号)',
  `applicant`             VARCHAR(128) NOT NULL COMMENT '申请单位',
  `purpose`               VARCHAR(500) NOT NULL COMMENT '用途',
  `stage`                 VARCHAR(16)  NOT NULL COMMENT '眼下停在哪一阶 WINDOW窗口受理 CITY市局核验 PROVINCE省厅核准 ISSUE窗口放成果 DONE办结',
  `round_no`              INT          NOT NULL DEFAULT 1 COMMENT '第几轮(省厅发回市局一轮加一)',
  `status`                INT          NOT NULL DEFAULT 0 COMMENT '状态 0在办 1已核准 2已否决 3已办结',
  `sheet_count`           INT          NOT NULL DEFAULT 0 COMMENT '图幅行数(受理落账;与行表当场重算两处对账)',
  `province_sign_count`   INT          NOT NULL DEFAULT 0 COMMENT '省厅已签人数(落账;与链上最新一轮两岗笔数两处对账)',
  `approve_no`            VARCHAR(64)  NULL COMMENT '批准文号(YZ打头,省厅两签齐核准那一下系统给)',
  `approved_by`           VARCHAR(64)  NULL COMMENT '核准时最后落签经办(快照)',
  `approved_at`           DATETIME     NULL COMMENT '核准时辰(快照)',
  `issued_by`             VARCHAR(64)  NULL COMMENT '放成果经办(快照)',
  `issued_at`             DATETIME     NULL COMMENT '放成果时辰(快照)',
  `create_by`             VARCHAR(64)  NULL,
  `create_time`           DATETIME     NULL,
  `update_by`             VARCHAR(64)  NULL,
  `update_time`           DATETIME     NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_use_order_no` (`order_no`),
  KEY `idx_use_order_applicant` (`applicant`),
  KEY `idx_use_order_stage` (`stage`),
  KEY `idx_use_order_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用密成果领用申请单';

-- 申请图幅行：受理时逐行落账，只增不改（核准后各栏锁住，要动另起新件）
CREATE TABLE IF NOT EXISTS `t_mgis_use_sheet` (
  `id`           BIGINT      NOT NULL COMMENT '主键',
  `order_id`     BIGINT      NOT NULL COMMENT '申请单主键',
  `row_no`       INT         NOT NULL COMMENT '册内行次(自1连排)',
  `sheet_code`   VARCHAR(64) NOT NULL COMMENT '图幅代号',
  `secret_level` VARCHAR(16) NOT NULL COMMENT '密级 TOP_SECRET绝密 SECRET机密 CONFIDENTIAL秘密 INTERNAL内部',
  `map_year`     INT         NOT NULL COMMENT '图上出图年份(四位整数)',
  `create_by`    VARCHAR(64) NULL,
  `create_time`  DATETIME    NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_use_sheet_row` (`order_id`, `row_no`),
  UNIQUE KEY `uk_use_sheet_code` (`order_id`, `sheet_code`),
  KEY `idx_use_sheet_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用密成果申请图幅行(只增不改)';

-- 签署落笔链：只增不改——不设 update_by/update_time/del_flag，服务层无改删入口
CREATE TABLE IF NOT EXISTS `t_mgis_use_entry` (
  `id`                  BIGINT      NOT NULL COMMENT '主键',
  `order_id`            BIGINT      NOT NULL COMMENT '申请单主键',
  `seq`                 INT         NOT NULL COMMENT '链上序号(自1连排)',
  `round_no`            INT         NOT NULL DEFAULT 1 COMMENT '第几轮(省厅发回一轮加一)',
  `stage`               VARCHAR(16) NOT NULL COMMENT '落笔阶 WINDOW/CITY/PROVINCE/ISSUE',
  `action`              VARCHAR(20) NOT NULL COMMENT '落笔动作 ACCEPT/CITY_PASS/CITY_REJECT/SIGN_SECRECY/SIGN_PURPOSE/PROVINCE_REJECT/RETURN/ISSUE',
  `payload`             TEXT        NULL COMMENT '这一笔写的话(JSON,落笔即定)',
  `penned_by`           VARCHAR(64) NOT NULL COMMENT '经办(落笔那一下系统写死,替签不作数)',
  `penned_at`           DATETIME    NOT NULL COMMENT '落笔时辰(系统写死,以秒计)',
  `prev_hash`           CHAR(64)    NOT NULL COMMENT '前一笔哈希(头一笔为64个0)',
  `hash`                CHAR(64)    NOT NULL COMMENT '本笔哈希',
  `supersedes_entry_id` BIGINT      NULL COMMENT '发回重办顶替的旧笔(旧笔退出页面,链上仍在)',
  `create_by`           VARCHAR(64) NULL,
  `create_time`         DATETIME    NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_use_entry_seq` (`order_id`, `seq`),
  UNIQUE KEY `uk_use_entry_point` (`order_id`, `stage`, `action`, `round_no`),
  KEY `idx_use_entry_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用密成果申请签署落笔链(只增不改)';

-- 领用清单头：一件对一条，核准后一键生成，重按只回头一遍
CREATE TABLE IF NOT EXISTS `t_mgis_use_claim` (
  `id`              BIGINT       NOT NULL COMMENT '主键',
  `claim_no`        VARCHAR(64)  NOT NULL COMMENT '领用清单号(QD打头,系统给号)',
  `order_id`        BIGINT       NOT NULL COMMENT '核准申请单主键(一件对一条)',
  `order_no`        VARCHAR(64)  NOT NULL COMMENT '申请单号(自核准件带)',
  `approve_no`      VARCHAR(64)  NOT NULL COMMENT '批准文号(自核准件带,窗口不誊第二遍)',
  `applicant`       VARCHAR(128) NOT NULL COMMENT '申请单位(自核准件带)',
  `purpose`         VARCHAR(500) NOT NULL COMMENT '用途(自核准件带)',
  `line_count`      INT          NOT NULL DEFAULT 0 COMMENT '清单行数(与行表、核准件图幅行三处对账)',
  `generated_by`    VARCHAR(64)  NOT NULL COMMENT '生成清单经办(登录态取)',
  `generated_at`    DATETIME     NOT NULL COMMENT '生成时辰(系统写死,以秒计)',
  `create_by`       VARCHAR(64)  NULL,
  `create_time`     DATETIME     NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_use_claim_no` (`claim_no`),
  UNIQUE KEY `uk_use_claim_order` (`order_id`),
  KEY `idx_use_claim_applicant` (`applicant`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用密成果领用清单(一件一条,只立不改)';

-- 领用清单行：图幅代号、密级、出图年份全自核准那一份申请行带，不经手誊抄
CREATE TABLE IF NOT EXISTS `t_mgis_use_claim_line` (
  `id`           BIGINT      NOT NULL COMMENT '主键',
  `claim_id`     BIGINT      NOT NULL COMMENT '清单主键',
  `order_id`     BIGINT      NOT NULL COMMENT '核准申请单主键',
  `row_no`       INT         NOT NULL COMMENT '行次(与申请行同序)',
  `sheet_code`   VARCHAR(64) NOT NULL COMMENT '图幅代号(自申请行带)',
  `secret_level` VARCHAR(16) NOT NULL COMMENT '密级(自申请行带)',
  `map_year`     INT         NOT NULL COMMENT '出图年份(自申请行带)',
  `create_by`    VARCHAR(64) NULL,
  `create_time`  DATETIME    NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_use_claim_line_row` (`claim_id`, `row_no`),
  KEY `idx_use_claim_line_claim` (`claim_id`),
  KEY `idx_use_claim_line_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用密成果领用清单行(只立不改)';
