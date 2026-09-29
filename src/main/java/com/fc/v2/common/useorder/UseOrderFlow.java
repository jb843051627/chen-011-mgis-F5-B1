package com.fc.v2.common.useorder;

import com.alibaba.fastjson.JSONObject;
import com.fc.v2.model.auto.TMgisUseEntry;
import com.fc.v2.model.auto.TMgisUseOrder;
import com.fc.v2.model.custom.UseOrderSignRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用密申请走阶引擎：领用核准规矩在系统里的唯一出处。
 *
 * <p>一张申请单分四阶：窗口受理、市局核验、省厅核准、窗口放成果。
 * 走阶只许一阶接一阶，次序全收在这一个类里，服务层、控制器都从这里过，
 * 别处不许另写一套。</p>
 *
 * <ul>
 *   <li><b>省厅认两个人</b>：密级脱密岗、用途岗各签各的，谁先谁后都行，
 *       同一分钟并发落签也是两笔，不并成一条；只签一枚，件仍停在省厅阶。</li>
 *   <li><b>替签不作数</b>：动作与岗焊死，密级岗落不成用途岗的签。</li>
 *   <li><b>发回一笔勾销</b>：省厅把件发回市局，轮次加一，市局核验、省厅
 *       双签全部从新一轮重攒；旧笔链上不删不改，整批退出页面。</li>
 *   <li><b>否决即定死</b>：任一处写明不予核准，事后一枚都加不进去；
 *       <b>核准即锁栏</b>：两签齐核准后只收放成果，要动另起新件。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-28
 */
public final class UseOrderFlow {

    private UseOrderFlow() {
    }

    /** 在办 */
    public static final int STATUS_OPEN = 0;
    /** 已核准：省厅两签齐，批准文号落，各栏锁住 */
    public static final int STATUS_APPROVED = 1;
    /** 已否决：写明不予核准，件定死 */
    public static final int STATUS_REJECTED = 2;
    /** 已办结：窗口凭单放完成果 */
    public static final int STATUS_DONE = 3;

    // ---- 落笔内容键 ----
    /** 受理：申请单位、用途、图幅行数、图幅代号串（受理定局快照） */
    public static final String KEY_APPLICANT = "applicant";
    public static final String KEY_PURPOSE = "purpose";
    public static final String KEY_SHEET_COUNT = "sheetCount";
    public static final String KEY_SHEET_CODES = "sheetCodes";
    /** 核验通过 / 放成果附言 */
    public static final String KEY_NOTE = "note";
    /** 省厅两岗认定意见 */
    public static final String KEY_OPINION = "opinion";
    /** 不予核准 / 发回缘由 */
    public static final String KEY_REASON = "reason";

    /**
     * 签署前核阶：件还收不收笔、这一笔够不够得着眼下的阶。
     */
    public static void checkSign(TMgisUseOrder order, UseOrderAction action) {
        checkWritable(order, action);
        checkStage(order, action);
    }

    /**
     * 核件还收不收笔：
     * 已否决的件定死，一枚都加不进去；已核准的件各栏锁住，只收窗口放成果；
     * 办结终态什么都不收。受理笔只由签署门在立件那一下落，已立的件上不收第二回。
     */
    public static void checkWritable(TMgisUseOrder order, UseOrderAction action) {
        if (order == null) {
            throw new UseOrderFlowException(UseOrderRejectReason.ORDER_NOT_FOUND,
                    UseOrderRejectReason.ORDER_NOT_FOUND.getLabel());
        }
        if (action == null) {
            throw new IllegalArgumentException("签署动作未指");
        }
        Integer status = order.getStatus();
        if (status != null && status == STATUS_REJECTED) {
            throw new UseOrderFlowException(UseOrderRejectReason.DEAD_ORDER,
                    UseOrderRejectReason.DEAD_ORDER.getLabel());
        }
        if (status != null && status == STATUS_DONE) {
            throw new UseOrderFlowException("这件已办结：成果已放，终态不再收笔；要再申请请另起新件");
        }
        if (status != null && status == STATUS_APPROVED && action != UseOrderAction.ISSUE) {
            // 核准之后各栏锁住：补签、发回、改栏一律顶回，只收窗口放成果
            throw new UseOrderFlowException(UseOrderRejectReason.APPROVED_LOCKED,
                    UseOrderRejectReason.APPROVED_LOCKED.getLabel());
        }
        if (action == UseOrderAction.ACCEPT) {
            throw new UseOrderFlowException("受理笔只在立件那一下落，一件受理不过两回；要改请另起新件");
        }
    }

    /**
     * 核这一笔够不够得着：件眼下停在哪一阶，就只开哪一阶的口子，
     * 隔着阶落笔当场顶回，不生效。
     */
    public static void checkStage(TMgisUseOrder order, UseOrderAction action) {
        UseOrderStage current = UseOrderStage.require(order.getStage());
        if (action.getStage() != current) {
            throw new UseOrderFlowException(UseOrderRejectReason.BAD_STAGE,
                    UseOrderRejectReason.BAD_STAGE.getLabel() + "：件眼下停在「" + current.getLabel()
                            + "」，「" + action.getLabel() + "」这一笔隔着阶，不生效");
        }
    }

    /**
     * 把签署请求归置成这一笔要写的话（JSON）。
     * 省厅两岗的认定意见、否决与发回的缘由缺了落不了笔；
     * 核验通过、放成果的附言可空。
     */
    public static String packPayload(UseOrderAction action, UseOrderSignRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("签署请求为空");
        }
        JSONObject payload = new JSONObject(true);
        switch (action) {
            case CITY_PASS:
            case ISSUE:
                if (hasText(req.getNote())) {
                    payload.put(KEY_NOTE, req.getNote().trim());
                }
                break;
            case CITY_REJECT:
            case PROVINCE_REJECT:
            case RETURN:
                payload.put(KEY_REASON, requireText(req.getReason(),
                        action == UseOrderAction.RETURN
                                ? UseOrderRejectReason.MISSING_REASON.getLabel() + "：省厅发回市局须写清发回缘由"
                                : UseOrderRejectReason.MISSING_REASON.getLabel() + "：写明不予核准须把缘由写清"));
                break;
            case SIGN_SECRECY:
            case SIGN_PURPOSE:
                payload.put(KEY_OPINION, requireText(req.getOpinion(),
                        UseOrderRejectReason.MISSING_OPINION.getLabel() + "（" + action.getLabel() + "）"));
                break;
            default:
                throw new UseOrderFlowException("受理笔在立件那一下落，不经这一处归置");
        }
        return payload.toJSONString();
    }

    /**
     * 受理那一笔的话：受理定局原样押进链（单位、用途、行数、代号串），
     * 往后各阶只读取不重填。
     */
    public static String acceptPayload(UseAcceptVerdict verdict) {
        JSONObject payload = new JSONObject(true);
        payload.put(KEY_APPLICANT, verdict.getApplicant());
        payload.put(KEY_PURPOSE, verdict.getPurpose());
        payload.put(KEY_SHEET_COUNT, verdict.getSheetCount());
        List<String> codes = new ArrayList<String>(verdict.getSheetCount());
        for (UseSheetDraft s : verdict.getSheets()) {
            codes.add(s.getSheetCode());
        }
        payload.put(KEY_SHEET_CODES, codes);
        return payload.toJSONString();
    }

    /**
     * 落笔之后件挪到哪一阶。省厅那一阶认两个人：两岗各一笔才算走通，
     * 只回来一枚件仍停在省厅；省厅发回则退回市局阶。
     *
     * @param action         刚落的这一笔
     * @param provinceBothIn 省厅两岗本轮是否都已签（仅省厅签用得上）
     */
    public static UseOrderStage stageAfter(UseOrderAction action, boolean provinceBothIn) {
        switch (action) {
            case ACCEPT:
                return UseOrderStage.CITY;
            case CITY_PASS:
                return UseOrderStage.PROVINCE;
            case CITY_REJECT:
                // 写明不予核准：件定死在市局阶
                return UseOrderStage.CITY;
            case SIGN_SECRECY:
            case SIGN_PURPOSE:
                return provinceBothIn ? UseOrderStage.ISSUE : UseOrderStage.PROVINCE;
            case PROVINCE_REJECT:
                return UseOrderStage.PROVINCE;
            case RETURN:
                // 发回市局：轮次加一（由服务层记），前签一笔勾销
                return UseOrderStage.CITY;
            case ISSUE:
            default:
                return UseOrderStage.DONE;
        }
    }

    /** 这一笔是不是省厅发回市局（是则轮次加一、前签一笔勾销） */
    public static boolean isReturn(UseOrderAction action) {
        return action == UseOrderAction.RETURN;
    }

    /**
     * 链上前后两笔的走阶次序合不合法：
     * 同轮只许一阶接一阶，省厅阶上两岗各至多一枚、签后可发回可否决；
     * 跨轮只许「省厅发回 → 新一轮市局核验/否决」这一种来路。
     */
    public static boolean legalTransition(TMgisUseEntry prev, TMgisUseEntry next) {
        if (prev == null || next == null) {
            return false;
        }
        UseOrderAction pa;
        UseOrderAction na;
        try {
            pa = UseOrderAction.valueOf(prev.getAction());
            na = UseOrderAction.valueOf(next.getAction());
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!pa.getStage().name().equals(prev.getStage()) || !na.getStage().name().equals(next.getStage())) {
            return false;
        }
        int dr = roundOf(next) - roundOf(prev);
        if (dr == 0) {
            if (pa.getStage() == na.getStage()) {
                // 同阶多笔只许出现在省厅阶：两岗各签、签后发回/否决
                if (pa.getStage() != UseOrderStage.PROVINCE) {
                    return false;
                }
                if (!pa.isProvinceSign()) {
                    // 发回、否决之后同轮不得再有笔
                    return false;
                }
                if (na == pa) {
                    // 同岗落两回不算数（唯一键也兜底）
                    return false;
                }
                return na.isProvinceSign() || na == UseOrderAction.RETURN || na == UseOrderAction.PROVINCE_REJECT;
            }
            if (na.getStage().getOrder() != pa.getStage().getOrder() + 1) {
                return false;
            }
            // 相邻阶的具体接法
            if (pa == UseOrderAction.ACCEPT) {
                return na == UseOrderAction.CITY_PASS || na == UseOrderAction.CITY_REJECT;
            }
            if (pa == UseOrderAction.CITY_PASS) {
                return na.getStage() == UseOrderStage.PROVINCE;
            }
            if (pa.isProvinceSign()) {
                // 两岗齐才接得上放成果（两岗齐没齐由整轮校验另核）
                return na == UseOrderAction.ISSUE;
            }
            return false;
        }
        if (dr == 1) {
            // 跨轮只许省厅发回撑起来，新一轮从市局阶重新攒（核验通过或直接否决）
            return pa == UseOrderAction.RETURN && na.getStage() == UseOrderStage.CITY
                    && (na == UseOrderAction.CITY_PASS || na == UseOrderAction.CITY_REJECT);
        }
        return false;
    }

    /**
     * 页面口径：每一阶只显当前轮算数的笔，按落笔先后排。
     * 省厅发回、轮次加一后，市局、省厅两阶的旧轮笔整批退出页面——哪怕新轮
     * 还一笔没落（件正停回市局等重核），废票也不与新票同堂并摆；受理笔自
     * 第一轮起固定，始终在页面上。链上旧笔原样留着，查链一笔不少。
     *
     * @param entries     全链
     * @param activeRound 件眼下的轮次（单据上的 round_no）
     * @return 阶代号 → 该阶算数的落笔（按落笔先后排）
     */
    public static Map<String, List<TMgisUseEntry>> pageOf(List<TMgisUseEntry> entries, int activeRound) {
        Map<String, List<TMgisUseEntry>> page = new LinkedHashMap<String, List<TMgisUseEntry>>();
        for (UseOrderStage stage : UseOrderStage.values()) {
            if (stage == UseOrderStage.DONE) {
                continue;
            }
            List<TMgisUseEntry> shown = new ArrayList<TMgisUseEntry>();
            if (entries != null) {
                for (TMgisUseEntry e : entries) {
                    if (!stage.name().equals(e.getStage())) {
                        continue;
                    }
                    // 受理笔始终在；其余两阶只显当前轮，发回前的旧票退出页面
                    if (stage == UseOrderStage.WINDOW || roundOf(e) == activeRound) {
                        shown.add(e);
                    }
                }
            }
            if (!shown.isEmpty()) {
                page.put(stage.name(), shown);
            }
        }
        return page;
    }

    /** 某轮省厅阶已签人数（两岗各算一笔；发回、否决不算签） */
    public static int provinceSigned(List<TMgisUseEntry> entries, int roundNo) {
        int n = 0;
        if (entries != null) {
            for (TMgisUseEntry e : entries) {
                if (UseOrderStage.PROVINCE.name().equals(e.getStage()) && roundOf(e) == roundNo) {
                    try {
                        if (UseOrderAction.valueOf(e.getAction()).isProvinceSign()) {
                            n++;
                        }
                    } catch (IllegalArgumentException ignore) {
                        // 动作不识交给链校验顶，这里不当签数
                    }
                }
            }
        }
        return n;
    }

    /** 状态成文 */
    public static String statusLabel(Integer status) {
        if (status != null) {
            switch (status) {
                case STATUS_OPEN:
                    return "在办";
                case STATUS_APPROVED:
                    return "已核准";
                case STATUS_REJECTED:
                    return "已否决";
                case STATUS_DONE:
                    return "已办结";
                default:
                    break;
            }
        }
        return "未知";
    }

    /** 阶代号译阶名，译不得照原样回 */
    public static String labelOf(String stageName) {
        try {
            return UseOrderStage.require(stageName).getLabel();
        } catch (IllegalArgumentException e) {
            return String.valueOf(stageName);
        }
    }

    private static int roundOf(TMgisUseEntry e) {
        return e.getRoundNo() == null ? 1 : e.getRoundNo();
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String requireText(String s, String message) {
        if (!hasText(s)) {
            throw new UseOrderFlowException(message);
        }
        return s.trim();
    }
}
