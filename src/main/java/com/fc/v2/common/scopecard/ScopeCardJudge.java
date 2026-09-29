package com.fc.v2.common.scopecard;

import com.fc.v2.model.auto.TMgisScopeCard;
import com.fc.v2.model.auto.TMgisScopeCategory;
import com.fc.v2.model.auto.TMgisUnitDoc;
import com.fc.v2.model.custom.ScopeCardRegisterRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 标签卡登记引擎：测绘资质监督规矩在系统里的唯一出处。
 *
 * <p>进门只认「登记」一处：立卡、核对、定档、类别增减四道手续的章程
 * 都在这一处推演——对档案只按资质单位编号（只按名称匹配不作数）、
 * 注销档案不收新卡也不许从老卡上接着添东西、同一单位同一段范围
 * 第二张顶回、卡上只许挂末级类别（大类与末级混挂、挂已并走的旧类别
 * 都不给落）、挂用类别数填负数填零或越过档案顶线当场拦下并说清
 * 拦在哪一栏。服务层、控制器都从这里过，别处不许另写一套。</p>
 *
 * <p>层级档不许人手填，由 {@link ScopeGradeJudge} 拿挂用类别数撞档案
 * 三道顶线折算；类别有增减、单位改了属地，这一档跟着重新折算。</p>
 *
 * @author fuce
 * @date 2026-09-27
 */
public final class ScopeCardJudge {

    private ScopeCardJudge() {
    }

    /** 进展：待核对 */
    public static final int STATUS_PENDING = 0;
    /** 进展：已核对 */
    public static final int STATUS_CHECKED = 1;
    /** 进展：已定档 */
    public static final int STATUS_GRADED = 2;

    /**
     * 立卡推演。同一来文单号并一条的认账在服务层先做（重递原样回头一张）；
     * 走到这里的是头一遍：哪一栏犯了哪条规矩，整单顶回（{@link ScopeCardAcceptException}），
     * 一笔不落；过得去才给定局。
     *
     * @param req           登记请求（只带填报原值，收不收系统说了算）
     * @param siteDoc       按资质单位编号对到的档案（对不上传 null）
     * @param dupScopeCard  同一单位同一段范围已有的卡（没有传 null）
     * @param categoryIndex 专业类别目录（代号 → 目录行，只装现行在册的）
     * @return 立卡定局
     */
    public static ScopeCardVerdict judgeRegister(ScopeCardRegisterRequest req, TMgisUnitDoc siteDoc,
                                                 TMgisScopeCard dupScopeCard,
                                                 Map<String, TMgisScopeCategory> categoryIndex) {
        if (req == null) {
            throw new ScopeCardAcceptException("登记请求为空：登记门只认填报原值，空单没法落账");
        }
        String sourceBillNo = trim(req.getSourceBillNo());
        if (sourceBillNo == null) {
            throw new ScopeCardAcceptException(ScopeCardRejectReason.MISSING_SOURCE_BILL,
                    ScopeCardRejectReason.MISSING_SOURCE_BILL.getLabel());
        }
        ScopeCardSource source = ScopeCardSource.require(req.getSourceFrom());
        String siteNo = trim(req.getSiteNo());
        if (siteNo == null) {
            throw new ScopeCardAcceptException(ScopeCardRejectReason.MISSING_SITE_NO,
                    ScopeCardRejectReason.MISSING_SITE_NO.getLabel());
        }
        if (siteDoc == null) {
            // 只按编号对档案：同名两家单位我市真有过，只按名称匹配不作数
            throw new ScopeCardAcceptException(ScopeCardRejectReason.SITE_DOC_NOT_FOUND,
                    ScopeCardRejectReason.SITE_DOC_NOT_FOUND.getLabel() + "（填报原值：" + siteNo + "）");
        }
        checkDocWritable(siteDoc);
        String scopeSeg = trim(req.getScopeSeg());
        if (scopeSeg == null) {
            throw new ScopeCardAcceptException(ScopeCardRejectReason.MISSING_SCOPE_SEG,
                    ScopeCardRejectReason.MISSING_SCOPE_SEG.getLabel());
        }
        if (dupScopeCard != null) {
            // 同一单位同一段范围想再立一张：第二张必须顶回去
            throw new ScopeCardAcceptException(ScopeCardRejectReason.DUPLICATE_SCOPE,
                    ScopeCardRejectReason.DUPLICATE_SCOPE.getLabel()
                            + "（已有卡号：" + dupScopeCard.getBillNo() + "，范围段：" + scopeSeg + "）");
        }
        List<String> categories = validateCategories(req.getCategories(), categoryIndex);
        BigDecimal qty = parseQty(req.getQty());
        BigDecimal grade = ScopeGradeJudge.gradeOf(qty, siteDoc.getTh1Max(), siteDoc.getTh2Max(), siteDoc.getTh3Max());
        BigDecimal fineAmt = parseFineAmt(req.getFineAmt(), BigDecimal.ZERO);
        return new ScopeCardVerdict(sourceBillNo, source, siteDoc.getSiteNo(),
                siteDoc.getId() == null ? null : siteDoc.getId().intValue(), scopeSeg,
                categories, qty, fineAmt, grade);
    }

    /**
     * 类别增减推演。在挂类别整批换一遍，挂用类别数跟着重报、层级档跟着
     * 重折；本年度新增那一栏空着表示不动。哪一栏犯了规矩，整单顶回，
     * 旧清单一项不动。
     */
    public static ScopeCardVerdict judgeAdjust(ScopeCardRegisterRequest req, TMgisUnitDoc siteDoc,
                                               Map<String, TMgisScopeCategory> categoryIndex) {
        if (req == null) {
            throw new ScopeCardAcceptException("登记请求为空：类别增减也得走登记门，空单没法落账");
        }
        List<String> categories = validateCategories(req.getCategories(), categoryIndex);
        BigDecimal qty = parseQty(req.getQty());
        BigDecimal grade = ScopeGradeJudge.gradeOf(qty, siteDoc.getTh1Max(), siteDoc.getTh2Max(), siteDoc.getTh3Max());
        BigDecimal fineAmt = parseFineAmt(req.getFineAmt(), null);
        return new ScopeCardVerdict(null, null, siteDoc.getSiteNo(),
                siteDoc.getId() == null ? null : siteDoc.getId().intValue(), null,
                categories, qty, fineAmt, grade);
    }

    /**
     * 走站校验：卡停在哪一段进展就只开哪一道手续的口子。核对须从待核对
     * 起走，定档须从已核对起走；类别增减哪段进展都开得。隔着站落笔
     * 当场顶回，不生效。
     */
    public static void checkStation(TMgisScopeCard card, ScopeCardProcedure proc) {
        Integer status = card == null ? null : card.getStatus();
        if (proc == ScopeCardProcedure.CHECK && (status == null || status != STATUS_PENDING)) {
            throw badStation(status, proc);
        }
        if (proc == ScopeCardProcedure.GRADE && (status == null || status != STATUS_CHECKED)) {
            throw badStation(status, proc);
        }
    }

    /**
     * 档案可写校验：档案显示已注销的，往后不再收它的新卡；老卡照旧查得到，
     * 但不能从老卡上接着添东西——核对、定档、类别增减一样不许。
     */
    public static void checkDocWritable(TMgisUnitDoc siteDoc) {
        if (siteDoc != null && siteDoc.getStatus() != null && siteDoc.getStatus() == 1) {
            throw new ScopeCardAcceptException(ScopeCardRejectReason.SITE_DOC_CANCELLED,
                    ScopeCardRejectReason.SITE_DOC_CANCELLED.getLabel()
                            + "（编号：" + siteDoc.getSiteNo() + "）：注销后不再收新卡；"
                            + "老卡照旧查得到，但不能从老卡上接着添东西");
        }
    }

    /** 进展成文 */
    public static String statusLabel(Integer status) {
        if (status != null) {
            if (status == STATUS_PENDING) {
                return "待核对";
            }
            if (status == STATUS_CHECKED) {
                return "已核对";
            }
            if (status == STATUS_GRADED) {
                return "已定档";
            }
        }
        return "未知";
    }

    /**
     * 核挂用类别清单：只许挂到末级；大类与末级混挂、挂早已并入别处的旧类别，
     * 都不给落——顶回的话里指到「挂用类别」那一栏，哪个代号犯的、并到哪去了
     * 齐着写。清单内重号按一个算（同一类别挂两回还是那一段范围）。
     */
    private static List<String> validateCategories(List<String> raw, Map<String, TMgisScopeCategory> index) {
        if (raw == null || raw.isEmpty()) {
            throw new ScopeCardAcceptException(ScopeCardRejectReason.MISSING_CATEGORY,
                    ScopeCardRejectReason.MISSING_CATEGORY.getLabel());
        }
        Set<String> codes = new LinkedHashSet<String>();
        for (String item : raw) {
            String code = trim(item);
            if (code == null) {
                continue;
            }
            TMgisScopeCategory cate = index == null ? null : index.get(code);
            if (cate == null || (cate.getStatus() != null && cate.getStatus() != 0 && trim(cate.getMergedInto()) == null)) {
                throw new ScopeCardAcceptException(ScopeCardRejectReason.UNKNOWN_CATEGORY,
                        ScopeCardRejectReason.UNKNOWN_CATEGORY.getLabel() + "（填报原值：" + code + "）");
            }
            if (trim(cate.getMergedInto()) != null) {
                // 早已并进别处的旧类别，不许再挂
                throw new ScopeCardAcceptException(ScopeCardRejectReason.MERGED_CATEGORY,
                        ScopeCardRejectReason.MERGED_CATEGORY.getLabel()
                                + "（填报原值：" + code + "，现并入：" + cate.getMergedInto().trim() + "）");
            }
            if (cate.getLvl() == null || cate.getLvl() != 2) {
                // 大类不许上卡：把大类与末级混挂在同一张卡上，不给落
                throw new ScopeCardAcceptException(ScopeCardRejectReason.MAJOR_NOT_LEAF,
                        ScopeCardRejectReason.MAJOR_NOT_LEAF.getLabel()
                                + "（填报原值：" + code + "）：卡上只许挂到末级，大类与末级不许混挂");
            }
            codes.add(code);
        }
        if (codes.isEmpty()) {
            throw new ScopeCardAcceptException(ScopeCardRejectReason.MISSING_CATEGORY,
                    ScopeCardRejectReason.MISSING_CATEGORY.getLabel());
        }
        return new ArrayList<String>(codes);
    }

    /**
     * 挂用类别数照填报原值核：不是数、填负数、填零都拦在「挂用类别数」
     * 那一栏，原值带进缘故，追得到原笔。越过档案顶线的拦在折算那一步
     * （{@link ScopeGradeJudge#gradeOf}），缘故一样指到这一栏。
     */
    private static BigDecimal parseQty(String raw) {
        String s = trim(raw);
        if (s == null) {
            throw new ScopeCardAcceptException(ScopeCardRejectReason.QTY_NOT_A_NUMBER,
                    ScopeCardRejectReason.QTY_NOT_A_NUMBER.getLabel() + "（填报原值：空）");
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new ScopeCardAcceptException(ScopeCardRejectReason.QTY_NOT_A_NUMBER,
                    ScopeCardRejectReason.QTY_NOT_A_NUMBER.getLabel() + "（填报原值：" + s + "）");
        }
    }

    /**
     * 本年度新增挂用类别数：空着按缺省计（立卡按 0，类别增减表示这一栏不动）；
     * 填了但不是数、填负数，拦在「本年度新增挂用类别数」那一栏。
     */
    private static BigDecimal parseFineAmt(String raw, BigDecimal defaulted) {
        String s = trim(raw);
        if (s == null) {
            return defaulted;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new ScopeCardAcceptException(ScopeCardRejectReason.FINE_AMT_NOT_A_NUMBER,
                    ScopeCardRejectReason.FINE_AMT_NOT_A_NUMBER.getLabel() + "（填报原值：" + s + "）");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ScopeCardAcceptException(ScopeCardRejectReason.FINE_AMT_NEGATIVE,
                    ScopeCardRejectReason.FINE_AMT_NEGATIVE.getLabel() + "（填报原值：" + s + "）");
        }
        return value;
    }

    private static ScopeCardAcceptException badStation(Integer status, ScopeCardProcedure proc) {
        return new ScopeCardAcceptException(ScopeCardRejectReason.BAD_STATION,
                ScopeCardRejectReason.BAD_STATION.getLabel() + "：卡眼下进展「" + statusLabel(status)
                        + "」，「" + proc.getLabel() + "」那一道走不到，眼下只开「" + allowedOf(status) + "」的口子");
    }

    /** 眼下进展开得动的手续 */
    private static String allowedOf(Integer status) {
        if (status != null && status == STATUS_PENDING) {
            return "核对、类别增减";
        }
        if (status != null && status == STATUS_CHECKED) {
            return "定档、类别增减";
        }
        return "类别增减";
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
