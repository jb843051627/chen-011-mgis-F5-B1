# 作业范围标签建档卡规矩 → 系统口径对照

测绘资质监督的立卡规矩，在系统里只有一处出处：
`com.fc.v2.common.scopecard.ScopeCardJudge`（登记推演）、
`ScopeGradeJudge`（层级档折算）、`ScopeCardTally`（看账两口径）。
登记门、卡页面、看账两屏全从这三处过——两处口径合不上便是事故，
所以别处不许另写一套。

一张卡只讲一段范围，卡上没有的类别，业务就办不成。进门只认「登记」
一处：立卡、核对、定档、类别增减四道手续都打
`POST /mgisScopeCard/register` 进，手续由请求指认。

| 处里的说法 | 系统口径 | 落点 |
| --- | --- | --- |
| 标签卡号由系统给号 | 卡号 `SC` 打头由系统划号段，请求里说了不算；请求只带填报原值 | `TMgisScopeCardServiceImpl.doRegister`、`newBillNo` |
| 同一单位同一段范围想再立一张，第二张必须顶回去 | `(site_no, scope_seg)` 唯一，第二张整单顶回、一笔不落，顶回的话里带上已有卡号；唯一键 `uk_scope_card_site_scope` 兜底并发 | `ScopeCardJudge.judgeRegister`、`TMgisScopeCardServiceImpl.doRegister` |
| 市局抄送与单位自报并成一条 | 同一来文单号只认头一张：先查单号在不在账，在账即把头一张原样回（duplicated），后到的不顶先立的；唯一键 `uk_scope_card_source_bill` 兜底并发。单号一径由人工填写，系统不代拟 | `TMgisScopeCardServiceImpl.doRegister` |
| 挂用类别数填负数、填零、越过顶线，保存拦下来，说清拦在哪一栏 | 拦下缘故条条带栏位名（`ScopeCardRejectReason.field`），成文里栏位、填报原值、顶线齐着写；只甩一句「不合法」等于没说 | `ScopeCardJudge.parseQty`、`ScopeGradeJudge.gradeOf` |
| 立卡先按资质单位编号对档案，只按名称匹配不作数 | 档案只按 `site_no` 对（同名两家单位我市真有过）；对不上、已注销都顶回 | `ScopeCardJudge.judgeRegister`、`claimedSiteDoc` |
| 注销档案不收新卡；老卡照旧查得到，但不能从老卡上接着添东西 | 档案 `status=1`：立卡顶回；老卡的核对、定档、类别增减一样顶回；查询、卡页面照常 | `ScopeCardJudge.checkDocWritable` |
| 卡上只许挂到末级；大类与末级混挂、挂已并走的旧类别都不给落 | 类别目录一棵树（`t_mgis_scope_category`，一级大类、二级末级）：清单里有大类整单顶回并指到是哪个代号；`merged_into` 非空的旧类别顶回并写明并到哪 | `ScopeCardJudge.validateCategories` |
| 层级档不许人手填，系统拿挂用类别数撞三道顶线折算，留两位小数 | 请求里带不带这一栏系统都不认，只认 `ScopeGradeJudge.gradeOf` 一处折算；落库 `grade_level` 两位小数 | `ScopeGradeJudge` |
| 类别有增减、单位改了属地，这一档就得跟着重新折算 | 立卡、类别增减、定档三道手续各折一次落库；档案顶线、属地变了走 `recalcGradesOfSite` 整批重折；卡页面当场再重折一遍与落库档并列，对不上照实亮出来——只在立卡那天折一次是远远不够的 | `doAdjust`、`doProceed`、`recalcGradesOfSite`、`cardView` |
| 创建人按下立卡那一下是谁就是谁，重录不许挪 | `create_by` 立卡时由服务层写死（经办自登录态取）；更新路径一律不碰这一栏；同一单号重递并一条，头一张原样回，创建人不动 | `doRegister` |
| 经手人每过一道手续记一条，两个位置不许互相顶 | 经手人落笔链 `t_mgis_scope_card_entry` 只增不改（无 update/del_flag 列，服务层无改删入口）；经手人、落笔时辰在落笔那一下系统写死；链与 `create_by` 各记各的 | `TMgisScopeCardServiceImpl.pen` |
| 省局从新增类别最多的单位往下排 | 省局口径按卡上单位编号归堆，本年度新增合计降序（同额按编号字典序排稳） | `ScopeCardTally.tally` → `provinceRows` |
| 属地把单位按最末一级归堆再数 | 属地口径按档案注册地「省—市—区县」末段（区县）归堆，数单位数与卡数 | `ScopeCardTally.tally` → `regionRows` |
| 两条路数出来的卡数必须一致，差一笔就算这一屏没过 | 两口径同出 `ScopeCardTally.tally` 一处计算；卡对不上档案时省局口径照数、属地口径数不到，总数立时差一笔，`requireConsistent` 当场拦住，这一屏摆不出来 | `ScopeCardTally.requireConsistent`、`tallyOfVisible` |
| 属地的人只看得见本市，省局看得见全省 | 看账人属地市（登录用户部门名，与注册地市段同名）非空即加箍：列表、卡页面、看账两屏同一道箍；对不上本市的卡当不在账上回，不泄露存在性。有 `mgisScopeCard:province` 权限的看全省 | `applyVisibility`、`requireVisible`、`MgisScopeCardController.viewerCity` |
| 同一条卡在两边点开是同一笔账 | 卡页面只按主键拉一份：卡、出处档案、在挂类别、经手人链，省局属地看到的字字相同，不做字段裁剪 | `TMgisScopeCardServiceImpl.cardView` |

## 门脸规矩

- **进门只认「登记」一处**：只有 `POST /mgisScopeCard/register` 一个写入门；
  立卡、核对、定档、类别增减四道手续都由它进，动作由 `procedure` 指认。
- **登记成个什么样子，页面不许预填硬数**：请求只带填报原值；卡号、
  层级档系统给，请求里说了不算。
- **灵不灵以服务回传为凭**：回话里是卡眼下的局面与经手人链上落了的那
  一笔，页面只按回话刷新；同一单号重递 duplicated 为真。
- **单号一径由人工填写**：来文单号 `sourceBillNo` 必填、人工填，系统不代拟。
- **校验的活儿不落进页面**：对档案、核类别、核挂用类别数、走站，全在
  服务层与裁判类；页面不揣章程。
- **原有签名照封，扩功能走新重载**：`ITMgisScopeCardService` 早年六个
  方法签名一仍其旧（门脸已不再调用），登记、看账、属地可见全走新加
  方法；单条添、单条改、单条删的门不辟（控制器无 add/edit/remove）。

## 登记四道手续（`ScopeCardProcedure`）

| 手续 | 卡口 | 这一笔要说的话 |
| --- | --- | --- |
| REGISTER 立卡 | 对档案、核范围段、核类别、核挂用类别数 | 单号、来源、范围段、挂用类别数、层级档 |
| CHECK 核对 | 只许从「待核对」起走 | 核对 |
| GRADE 定档 | 只许从「已核对」起走 | 定档，层级档再折算一次落库 |
| ADJUST 类别增减 | 哪段进展都开得（档案须在册） | 新清单、新挂用类别数、重折层级档；旧清单整批退出 |

隔着站落笔当场顶回，不生效；并发撞同一道手续，条件更新
（`WHERE status=眼下进展`）兜底，后到的顶回。

## 拦下缘故（`ScopeCardRejectReason`，条条指到栏）

| 代号 | 栏位 | 触发 |
| --- | --- | --- |
| `MISSING_PROCEDURE` / `UNKNOWN_PROCEDURE` | 手续 | 未指 / 不识（带原值） |
| `MISSING_SOURCE_BILL` | 来文单号 | 空白（单号一径人工填写） |
| `MISSING_SOURCE` / `UNKNOWN_SOURCE` | 来文来源 | 空白 / 非市局抄送、单位自报两路（带原值） |
| `MISSING_SITE_NO` / `SITE_DOC_NOT_FOUND` | 资质单位编号 | 空白 / 按编号对不上档案（带原值） |
| `SITE_DOC_CANCELLED` | 资质单位编号 | 档案已注销：不收新卡，老卡不许添东西 |
| `MISSING_SCOPE_SEG` / `DUPLICATE_SCOPE` | 作业范围段 | 空白 / 同单位同范围段已有卡（带已有卡号） |
| `MISSING_CATEGORY` / `UNKNOWN_CATEGORY` | 挂用类别 | 清单空 / 代号目录里查不到（带原值） |
| `MAJOR_NOT_LEAF` | 挂用类别 | 挂的是大类：只许挂末级，大类与末级不许混挂 |
| `MERGED_CATEGORY` | 挂用类别 | 旧类别已并走（带原值与去向） |
| `QTY_NOT_A_NUMBER` / `QTY_NOT_POSITIVE` / `QTY_OVER_CEILING` | 挂用类别数 | 不是数 / 负数、零 / 越过三档顶线（带原值与顶线） |
| `FINE_AMT_NOT_A_NUMBER` / `FINE_AMT_NEGATIVE` | 本年度新增挂用类别数 | 不是数 / 负数（带原值） |
| `CARD_NOT_FOUND` | 卡 | 卡不在账上（属地看外市的卡同此回话） |
| `BAD_STATION` | 手续 | 隔站：眼下进展、只开哪道口子写清 |
| `CEILING_NOT_READY` | 档案顶线 | 三道顶线缺数、倒挂，没法折算 |

## 层级档折算口径（`ScopeGradeJudge`，留两位小数）

以挂用类别数 q、档案三道顶线 t1/t2/t3（种子 30/120/360）：

| 局面 | 层级档 |
| --- | --- |
| 0 < q < t1 | 1 + q/t1（如 q=15 → 1.50） |
| q = t1 | 2.00（压线算高一档） |
| t1 < q < t2 | 2 + (q−t1)/(t2−t1)（如 q=45 → 2.17） |
| q = t2 | 3.00 |
| t2 < q < t3 | 3 + (q−t2)/(t3−t2)（如 q=300 → 3.75） |
| q = t3 | 4.00（顶格） |
| q > t3 | 保存拦下（`QTY_OVER_CEILING`） |

四舍五入留两位小数；三道顶线缺数、不起于正数、倒挂，没法折算
（`CEILING_NOT_READY`），不许拿残缺顶线硬折充数。

## 看账两口径（`ScopeCardTally`）

| 数 | 口径 |
| --- | --- |
| 省局口径行 | 按卡上单位编号归堆：单位、卡数、本年度新增合计；新增合计降序，同额按编号字典序 |
| 属地口径行 | 按档案注册地末段（区县）归堆：区县、单位数、卡数；区县名字典序 |
| 恒等式 | 省局口径卡数合计 = 属地口径卡数合计 = 账面卡数；差一笔这一屏没过 |

## 端点

| 端点 | 用途 |
| --- | --- |
| `POST /mgisScopeCard/register` | 登记（唯一写入门；权限 `mgisScopeCard:register`） |
| `GET  /mgisScopeCard/list` | 卡台账（属地只见本市；权限 `mgisScopeCard:list`） |
| `GET  /mgisScopeCard/{id}` | 卡页面：卡、档案、在挂类别、经手人链、层级档当场重折 |
| `GET  /mgisScopeCard/tallyByProvince` | 看账·省局口径（新增最多的单位在前） |
| `GET  /mgisScopeCard/tallyByRegion` | 看账·属地口径（区县归堆） |
| `GET  /mgisScopeCard/categories` | 专业类别目录树（登记页面拉目录，只读） |

省局看全省另需权限 `mgisScopeCard:province`；无此权限的按登录用户
部门名（与注册地市段同名）加属地箍。无 add/edit/remove 端点。

建表与补列脚本：`sql/mgis_scope_card.sql`（卡表补列三栏、层级档改
两位小数、两把唯一键；类别目录、在挂类别、经手人链三表；目录种子
含一条已并走的旧类别 GC09）。新库按 `doc/schema/mgis.sql` 全量建表
已带出补列。
