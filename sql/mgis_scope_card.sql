-- ----------------------------------------------------------------------------
-- 作业范围标签建档卡（测绘资质监督管理规矩落库）
--
-- 口径一句话：一张卡只讲一段范围，卡上没有的类别业务办不成。标签卡号系统给号；
-- 同一单位同一段范围第二张顶回；市局抄送与单位自报是同一单号就并成一条，
-- 账上不留两笔；挂用类别数填负数、填零、越过档案顶线，保存拦下并说清拦在哪一栏；
-- 层级档由系统拿挂用类别数撞档案三道顶线折算，留两位小数，不许人手填；
-- 创建人立卡那一下写死，经手人每过一道手续在链上记一条，两个位置不许互相顶。
-- ----------------------------------------------------------------------------

-- ----------------------------------------------------------------------------
-- 卡表补列：老库执行下面六句；新库按 doc/schema/mgis.sql 全量建表已带出，跳过。
-- ----------------------------------------------------------------------------
-- ALTER TABLE `t_mgis_scope_card` ADD COLUMN `source_bill_no` VARCHAR(64) NULL COMMENT '来文单号(人工填写;同一单号并一条,账上不留两笔)' AFTER `bill_no`;
-- ALTER TABLE `t_mgis_scope_card` ADD COLUMN `source_from` VARCHAR(16) NULL COMMENT '来文来源 CITY_FORWARD市局抄送 UNIT_SELF单位自报' AFTER `source_bill_no`;
-- ALTER TABLE `t_mgis_scope_card` ADD COLUMN `scope_seg` VARCHAR(64) NULL COMMENT '作业范围段代号(同一单位同一段范围只许一张卡)' AFTER `site_no`;
-- ALTER TABLE `t_mgis_scope_card` MODIFY COLUMN `grade_level` DECIMAL(5,2) NULL COMMENT '标签层级档(系统撞三道顶线折算,留两位小数,不许人手填)';
-- ALTER TABLE `t_mgis_scope_card` ADD UNIQUE KEY `uk_scope_card_source_bill` (`source_bill_no`);
-- ALTER TABLE `t_mgis_scope_card` ADD UNIQUE KEY `uk_scope_card_site_scope` (`site_no`, `scope_seg`);

-- ----------------------------------------------------------------------------
-- 专业类别目录树：一级大类、二级末级；卡上只许挂末级。
-- 并走的旧类别 merged_into 指向去向，不许再挂；目录只读，系统不设维护门。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_mgis_scope_category` (
  `id`          BIGINT       NOT NULL COMMENT '主键',
  `cate_code`   VARCHAR(32)  NOT NULL COMMENT '专业类别代号',
  `cate_name`   VARCHAR(128) NOT NULL COMMENT '专业类别名称',
  `lvl`         INT          NOT NULL COMMENT '层级 1大类 2末级(卡上只许挂末级)',
  `parent_code` VARCHAR(32)  NULL COMMENT '所属大类代号(末级必填;大类为空)',
  `merged_into` VARCHAR(32)  NULL COMMENT '并走去向(非空=旧类别已并入别类,不许再挂)',
  `status`      INT          NOT NULL DEFAULT 0 COMMENT '0现行 1停用',
  `del_flag`    INT          NOT NULL DEFAULT 0 COMMENT '删除标记 0正常 1删除',
  `create_by`   VARCHAR(64)  NULL,
  `create_time` DATETIME     NULL,
  `update_by`   VARCHAR(64)  NULL,
  `update_time` DATETIME     NULL,
  `remark`      VARCHAR(500) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_category_code` (`cate_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测绘专业类别目录树(一级大类,二级末级)';

-- ----------------------------------------------------------------------------
-- 卡在挂类别：一张卡挂了哪几末级，只记眼下在挂；类别增减整批替换，
-- 换下来的清单在经手人链那一笔里留话，条目表本身不留历史。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_mgis_scope_card_item` (
  `id`          BIGINT      NOT NULL COMMENT '主键',
  `card_id`     BIGINT      NOT NULL COMMENT '卡主键',
  `cate_code`   VARCHAR(32) NOT NULL COMMENT '挂用的末级类别代号',
  `create_by`   VARCHAR(64) NULL,
  `create_time` DATETIME    NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_card_item` (`card_id`, `cate_code`),
  KEY `idx_scope_item_card` (`card_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业范围标签卡在挂类别(只记眼下在挂)';

-- ----------------------------------------------------------------------------
-- 经手人落笔链：每过一道手续记一条，只增不改——不设 update_by/update_time/
-- del_flag，服务层无改删入口。经手人、落笔时辰在落笔那一下由系统写死，
-- 与卡上的创建人各记各的，两个位置不许互相顶。
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_mgis_scope_card_entry` (
  `id`         BIGINT       NOT NULL COMMENT '主键',
  `card_id`    BIGINT       NOT NULL COMMENT '卡主键',
  `seq`        INT          NOT NULL COMMENT '链上序号(自1连排)',
  `proc`       VARCHAR(16)  NOT NULL COMMENT '手续 REGISTER立卡 CHECK核对 GRADE定档 ADJUST类别增减',
  `payload`    VARCHAR(500) NULL COMMENT '这一笔要说的话(落笔即定,往后只读)',
  `penned_by`  VARCHAR(64)  NOT NULL COMMENT '经手人(落笔那一下系统写死)',
  `penned_at`  DATETIME     NOT NULL COMMENT '落笔时辰(系统写死,以秒计)',
  `create_by`  VARCHAR(64)  NULL,
  `create_time` DATETIME    NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_entry_seq` (`card_id`, `seq`),
  KEY `idx_scope_entry_card` (`card_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签卡经手人落笔链(只增不改)';

-- ----------------------------------------------------------------------------
-- 专业类别目录种子：现行目录（三个大类各带末级）+ 一条已并走的旧类别样例。
-- GC09 旧工程测量已并入 GC01 控制测量——挂它立卡，当场顶回并指明去向。
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `t_mgis_scope_category` (id, cate_code, cate_name, lvl, parent_code, merged_into, status, del_flag, create_by, create_time)
VALUES (101, 'GC',   '工程测量',                 1, NULL, NULL,   0, 0, 'seed', NOW()),
       (102, 'GC01', '控制测量',                 2, 'GC', NULL,   0, 0, 'seed', NOW()),
       (103, 'GC02', '地形测量',                 2, 'GC', NULL,   0, 0, 'seed', NOW()),
       (104, 'GC03', '规划测量',                 2, 'GC', NULL,   0, 0, 'seed', NOW()),
       (105, 'JX',   '界线与不动产测绘',         1, NULL, NULL,   0, 0, 'seed', NOW()),
       (106, 'JX01', '界线测绘',                 2, 'JX', NULL,   0, 0, 'seed', NOW()),
       (107, 'JX02', '不动产测绘',               2, 'JX', NULL,   0, 0, 'seed', NOW()),
       (108, 'DT',   '地图编制',                 1, NULL, NULL,   0, 0, 'seed', NOW()),
       (109, 'DT01', '地形图编制',               2, 'DT', NULL,   0, 0, 'seed', NOW()),
       (110, 'DT02', '专题地图编制',             2, 'DT', NULL,   0, 0, 'seed', NOW()),
       (111, 'GC09', '旧工程测量（已并入控制测量）', 2, 'GC', 'GC01', 1, 0, 'seed', NOW());
