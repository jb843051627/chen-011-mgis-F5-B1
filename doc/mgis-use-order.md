# 用密成果领用申请规矩 → 系统口径对照

政务服务和监管处把用密成果的提供使用整个搬进系统的规矩，在系统里只有一处出处：
`com.fc.v2.common.useorder.UseOrderFlow`（走阶引擎）、`UseOrderJudge`（受理推演）、
`UseOrderChainJudge`（拉链）、`UseOrderTally`（两处计数对账）。窗口门脸、
市局核验台、省厅双签台、领用清单全从这几处过——两处口径合不上便是事故，
所以别处不许另写一套。

一张申请单从窗口递进到放成果，分四阶：窗口受理、市局核验、省厅核准、
窗口放成果。一件对一单，单上每一幅图一行（图幅代号、密级、出图年份跟着行）。

| 处里的说法 | 系统口径 | 落点 |
| --- | --- | --- |
| 进门那一张缺一项受理不下去 | 申请单位、用途、图幅行三样齐着报；每行图幅代号、密级、出图年份缺一不可，代号册内不许重号，密级只认四档，年份须是四位整数、不越过今年；哪一栏犯规矩整单顶回、一笔不落 | `UseOrderJudge.judgeAccept`、`UseOrderRejectReason` |
| 单号由系统给，请求只带填报原值 | 申请单号 `YM` 打头、批准文号 `YZ` 打头、清单号 `QD` 打头，号段都由系统划；请求里说了不算 | `TMgisUseOrderServiceImpl.sign`、`newBillNo` |
| 此路只经「签署」一道门 | 受理画押、市局核验、省厅两人签、省厅发回、写明不予核准、窗口放成果，全打 `POST /mgisUseOrder/sign` 进，动作由 `action` 指认；控制器没有立单、改单、删单端点 | `MgisUseOrderController` |
| 画押、驳回、否决三事统归服务层执掌 | 请求只带填报原值与意见，不带「通过/驳回」结果栏；成与不成、挪到哪一阶、给不给核准文号，全凭服务层回传，页面不预置 | `TMgisUseOrderServiceImpl.sign`、`UseOrderFlow.stageAfter` |
| 第二步核用途真不真、与允许的作业范围搭不搭 | 真伪与搭不搭由市局成果管理科在「市局核验」这一阶当台认定，落 `CITY_PASS`（核验通过）或 `CITY_REJECT`（写明不予核准）；系统不代核，只认这一阶落没落成、次序对不对 | `UseOrderAction.CITY_PASS/CITY_REJECT` |
| 第三步两个人各签各的 | 省厅两岗两笔：`SIGN_SECRECY`（盯密级与脱密处理到不到位）、`SIGN_PURPOSE`（看用途该不该给）；谁先谁后都行，并发落签也是两笔，不并成一条；同岗同轮第二回只认头一笔；同一件的签署在库里以行锁串行（`selectForUpdate`，事务内 `SELECT … FOR UPDATE`），两人同一分钟并发也一笔不丢、序号不乱，唯一键 `(order_id, stage, action, round_no)` 再兜底；同一登录名把两岗都签了算替签，顶回 | `TMgisUseOrderServiceImpl.sign`、`TMgisUseOrderMapper.selectForUpdate`、`uk_use_entry_point` |
| 只签一位，件还停在第三步 | 两岗各一笔才算这一阶走通；只回一笔，单子仍停在省厅阶，第四阶点不出放成果 | `UseOrderFlow.stageAfter` |
| 替签不作数 | 密级岗落不成用途岗的签，反之亦然：动作与岗焊死，登录态是谁就记谁，经办不许请求代报 | `UseOrderAction`、`TMgisUseOrderServiceImpl.sign` |
| 次序不可颠倒 | 件停在哪一阶就只开哪一阶的口子，隔着阶落笔当场顶回；省厅两枚没齐，`ISSUE` 那一笔落不上 | `UseOrderFlow.checkStage` |
| 签了几个人由件自己记 | 签署笔数只从落笔链当场重算，请求与页面都不许手填；单据上另存一份落账时的省厅已签数，翻件时两处对算，差一个就算这一阶没过 | `UseOrderTally.requireConsistent`、`t_mgis_use_order.province_sign_count` |
| 省厅发回市局，前签一笔勾销 | `RETURN`（缘由必填）把件发回市局阶，轮次加一；市局核验、省厅双签全部从新一轮重攒。旧笔链上不删不改（哈希链一笔不能少），但整批退出页面——废票与新票不许同堂并摆；重办的新笔由 `supersedes_entry_id` 指向上一轮同岗旧笔 | `UseOrderFlow.pageOf`、`t_mgis_use_entry` |
| 哪一处写明不予核准，件就此定死 | `CITY_REJECT`、`PROVINCE_REJECT` 须填缘由，落笔即状态置「已否决」：事后加不进去一枚，发回、补签、放成果一律顶回；要再申请另起一份新件 | `UseOrderFlow.checkWritable`、`UseOrderAction` |
| 核准之后各栏锁住 | 省厅两签齐即核准：状态置「已核准」、批准文号落笔那一下系统给、各栏锁住；往后只收窗口 `ISSUE` 与领清单，任何改栏、补签、发回都顶回；要动另起新件 | `UseOrderFlow.checkWritable`、`TMgisUseOrderServiceImpl.moveStage` |
| 第四步回窗口凭单放成果 | 只认已核准的件：`ISSUE` 落笔后状态置「已办结」；未核准件点不出放成果 | `UseOrderAction.ISSUE`、`UseOrderFlow.stageAfter` |
| 一键生成领用清单，窗口不誊第二遍 | `POST /mgisUseOrder/claimList`：图幅代号、密级、出图年份自申请行带，批准文号、申请单位自核准那一份带，一个字段不经手；一件对一条清单，重按只回头一遍（duplicated） | `TMgisUseOrderServiceImpl.claimList`、`uk_use_claim_order` |
| 一家单位一册图对一条清单 | 清单与核准件 1:1（唯一键兜底）；一幅图一行，清单行数与核准件图幅行数恒等，两边对不上即库里被人动过 | `t_mgis_use_claim`、`UseOrderTally` |
| 从末阶一级级捋得回去 | 每笔记前一笔哈希（头一笔认 64 个 0），哈希押进阶、动作、写的话、经办、时辰、轮次；顺拉自受理念到放成果逐笔重算，倒拉自末笔沿前哈希念回受理，步数须与总笔数相合 | `UseOrderChainJudge.verifyForward/verifyBackward` |
| 捋出来的与当日屏上同一个模样、同一序 | 逐阶页面只显最新一轮算数的笔，按落笔先后排；表式就用申请单那四阶（受理/核验/省厅/放成果），不另起炉灶 | `UseOrderFlow.pageOf`、`UseOrderPageView` |
| 旧签名照封，扩本事添重载 | 早年 `t_mgis_use_bill` 脚手架（approve/reject/rollback）原样封存不动；新流程走全新五表，旧方法签名一个不改 | `ITMgisUseBillService`（封存）、`sql/mgis_use_order.sql` |

## 门脸规矩

- **此路只经「签署」一道门**：只有 `POST /mgisUseOrder/sign` 一个动作门；
  受理、核验、两岗签、发回、不予核准、放成果都由 `action` 指认。
- **成与未成但凭回传定夺**：请求只带填报原值（受理）或意见缘由（其余动作），
  不预设结果；回话里是落了的那一笔、件眼下停在哪一阶、核准没核准，页面只按回话刷新。
- **校验的活儿不落进页面**：缺项顶回、对密级、走阶、双签点齐、发回清账、
  锁栏、计数两口径对账，全在服务层与裁判类。
- **绕开门面立单改单的路堵严**：控制器无 add/edit/remove；申请图幅行、落笔链、
  清单行只立不改（无 update/del_flag 列，服务层无改删入口）。
- **领用清单另开一扇只读门**：`POST /mgisUseOrder/claimList` 只在核准后开得，
  一件一生、重按回头一遍；它不是签署，不进签署门。
- **欲扩充另起新重载**：已发布方法签名不动，新意思加新方法。

## 四阶与动作（`UseOrderStage` / `UseOrderAction`）

| 阶 | 阶代号（序） | 落得上的动作 | 这一笔要说的话 |
| --- | --- | --- | --- |
| 窗口受理 | WINDOW(0) | ACCEPT 受理 | 申请单位、用途、图幅行（每行代号/密级/出图年份），齐着才落 |
| 市局核验 | CITY(1) | CITY_PASS 核验通过 / CITY_REJECT 不予核准 | PASS 附言可空；REJECT 缘由必填，落笔定死 |
| 省厅核准 | PROVINCE(2) | SIGN_SECRECY 密级脱密签 / SIGN_PURPOSE 用途签 / PROVINCE_REJECT 不予核准 / RETURN 发回市局 | 两签各带认定意见（必填）；REJECT、RETURN 缘由必填；两签齐才准往下走 |
| 窗口放成果 | ISSUE(3) | ISSUE 放成果 | 凭核准单放，附言可空；落笔即办结 |
| 办结 | DONE(4) | —（终态，不收笔） | — |

件的状态：`0` 在办、`1` 已核准（两签齐、批准文号落、各栏锁住）、
`2` 已否决（定死）、`3` 已办结（成果已放）。

隔阶落笔当场顶回，不生效；并发撞同一岗同一轮，条件更新
（`WHERE station=眼下阶 AND round_no=眼下轮 AND status=0`）加唯一键双兜底，
后到的顶回。

## 受理校验与拦下缘故（`UseOrderJudge` / `UseOrderRejectReason`）

整单顶回（一笔不落）：

| 代号 | 栏位 | 触发 |
| --- | --- | --- |
| `MISSING_APPLICANT` | 申请单位 | 空白 |
| `MISSING_PURPOSE` | 用途 | 空白 |
| `MISSING_SHEET` | 图幅行 | 一行都没有（白表不许受理） |
| `SHEET_OVER_CEILING` | 图幅行 | 行数越过 `MAX_SHEETS`(2000) |
| `MISSING_SHEET_CODE` | 图幅代号 | 某行空白 |
| `DUPLICATE_SHEET_CODE` | 图幅代号 | 册内重号（带重号代号） |
| `MISSING_SECRET_LEVEL` / `UNKNOWN_SECRET_LEVEL` | 密级 | 空白 / 不在四档（绝密、机密、秘密、内部；代号或中文名皆认，带原值） |
| `MAP_YEAR_NOT_A_NUMBER` / `MAP_YEAR_OUT_OF_RANGE` | 出图年份 | 不是四位整数 / 小于 1900 或越过今年（带原值） |
| `ORDER_NOT_FOUND` | 件 | 件不在账上 |
| `BAD_STAGE` | 动作 | 隔阶：眼下在哪阶、只开哪个口写清 |
| `DEAD_ORDER` | 件 | 已否决件定死：一枚都加不进去，要办另起新件 |
| `APPROVED_LOCKED` | 件 | 已核准件各栏锁住：只收放成果与领清单，要改另起新件 |
| `NOT_APPROVED` | 件 | 未核准件点不出放成果、生不成清单（省厅两枚没齐） |
| `MISSING_OPINION` / `MISSING_REASON` | 意见/缘由 | 两签的认定意见、否决与发回的缘由空着 |

## 两处计数对账（`UseOrderTally`）

| 数 | 口径一：单据上存的 | 口径二：当场重算的 |
| --- | --- | --- |
| 省厅已签人数 | `t_mgis_use_order.province_sign_count`（落笔时落账） | 最新一轮省厅阶 `SIGN_SECRECY`+`SIGN_PURPOSE` 链上笔数 |
| 图幅行数 | `t_mgis_use_order.sheet_count`（受理时落账） | `t_mgis_use_sheet` 行数 |
| 清单行数 | 清单头 `line_count` | `t_mgis_use_claim_line` 行数，且须等于核准件图幅行数 |

三处恒等，任一处对不上即抛错：这一阶/这一屏没过，不许把两本各说各话的账摆上桌。

## 落笔链顺倒拉（`UseOrderChainJudge`）

- 每笔押 `order_id/seq/round_no/stage/action/payload/penned_by/penned_at/prev_hash`
  进 SHA-256；经办、时辰落笔那一下系统写死（时辰以秒计，与库面 DATETIME 同口径）。
- 顺拉：序号自 1 连排、头笔是受理且认 64 个 0、前后笔哈希接得住、时辰只增不减、
  走阶次序合法，并逐轮核对结构——市局阶恰好一枚通过或否决；省厅阶两岗各至多一枚；
  凡有 `ISSUE` 的轮次，省厅两签必齐；否决笔之后不得再有笔；跨轮只许「发回 → 新一轮核验通过」。
- 倒拉：自末笔沿前哈希念回受理，每步只找得到一笔，步数须与总笔数相合。
- 少一笔、次序错一处、时辰倒挂、内容被改、只签一枚就放成果、废票新票并排，
  顺拉或倒拉必有一程断，这件即不合格。

## 端点

| 端点 | 用途 |
| --- | --- |
| `POST /mgisUseOrder/sign` | 签署（唯一动作门；权限 `mgisUseOrder:sign`）。动作 ACCEPT/CITY_PASS/CITY_REJECT/SIGN_SECRECY/SIGN_PURPOSE/PROVINCE_REJECT/RETURN/ISSUE |
| `POST /mgisUseOrder/claimList` | 核准件一键生成领用清单（权限 `mgisUseOrder:claim`）；一件一条，重按 duplicated 为真 |
| `GET  /mgisUseOrder/list` | 申请台账（按单位、阶、状态、轮次查；权限 `mgisUseOrder:list`） |
| `GET  /mgisUseOrder/{id}` | 申请单页面：四阶表式，只开眼下阶的口子，每阶只显最新一轮 |
| `GET  /mgisUseOrder/{id}/chain` | 落笔链校验：顺拉倒拉，两程皆通才算合格 |
| `GET  /mgisUseOrder/{id}/claim` | 领用清单回看（图幅代号、密级、出图年份、批准文号） |

无 add/edit/remove 端点；改栏、撤件一律另起新申请。

建表脚本：`sql/mgis_use_order.sql`（申请单、申请图幅行、签署落笔链、
领用清单、清单行五表）。早年脚手架 `t_mgis_use_bill` 与本流程无涉，原样封存。
