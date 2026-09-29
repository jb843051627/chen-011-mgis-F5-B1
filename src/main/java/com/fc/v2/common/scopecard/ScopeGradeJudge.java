package com.fc.v2.common.scopecard;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 层级档折算：拿挂用类别数去撞档案三道顶线，折算口径在系统里的唯一出处。
 * 立卡、类别增减、定档、档案顶线或属地变更后的重折、看账当场重折，
 * 全从这一处过——只在立卡那天折一次是远远不够的，别处也不许另写一套。
 *
 * <p>折算口径（留两位小数，四舍五入）：</p>
 * <ul>
 *   <li>挂用类别数未及一档顶线：一档起步，按占一档顶线的比例往二档走
 *       （档 = 1 + 数/一档顶线）；</li>
 *   <li>压线算高一档：恰为一档顶线记 2.00，恰为二档顶线记 3.00；</li>
 *   <li>两道顶线之间：按在本档区间里走过的比例往高一档走；</li>
 *   <li>恰为三档顶线记 4.00（顶格）；越过三档顶线即越过档案允许承接的
 *       顶线，保存拦下，缘故指到「挂用类别数」那一栏。</li>
 * </ul>
 *
 * <p>层级档不许人手填：请求里带不带这一栏系统都不认，只认这一处折算。</p>
 *
 * @author fuce
 * @date 2026-09-27
 */
public final class ScopeGradeJudge {

    private ScopeGradeJudge() {
    }

    /** 一档顶线 30、二档 120、三档 360 时，恰压三档顶线折算出的顶格档 */
    public static final BigDecimal GRADE_CAP = new BigDecimal("4.00");

    /**
     * 折算层级档。挂用类别数不是正数、越过三档顶线，当场拦下，
     * 缘故指到「挂用类别数」那一栏并带填报原值；档案三道顶线
     * 未维护齐（缺数、倒挂、不起于正数），没法折算，一样拦下。
     *
     * @param qty  挂用类别数（项）
     * @param th1  一档顶线（项）
     * @param th2  二档顶线（项）
     * @param th3  三档顶线（项）
     * @return 层级档，两位小数
     */
    public static BigDecimal gradeOf(BigDecimal qty, BigDecimal th1, BigDecimal th2, BigDecimal th3) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ScopeCardAcceptException(ScopeCardRejectReason.QTY_NOT_POSITIVE,
                    ScopeCardRejectReason.QTY_NOT_POSITIVE.getLabel()
                            + "（填报原值：" + (qty == null ? "空" : qty.toPlainString()) + "）");
        }
        requireCeilings(th1, th2, th3);
        if (qty.compareTo(th3) > 0) {
            throw new ScopeCardAcceptException(ScopeCardRejectReason.QTY_OVER_CEILING,
                    ScopeCardRejectReason.QTY_OVER_CEILING.getLabel()
                            + "（填报原值：" + qty.toPlainString() + "，三档顶线：" + th3.toPlainString() + "）");
        }
        if (qty.compareTo(th1) < 0) {
            return fold(BigDecimal.ONE, qty, BigDecimal.ZERO, th1);
        }
        if (qty.compareTo(th1) == 0) {
            // 压一档顶线，等号算高一档
            return new BigDecimal("2.00");
        }
        if (qty.compareTo(th2) < 0) {
            return fold(new BigDecimal("2"), qty, th1, th2);
        }
        if (qty.compareTo(th2) == 0) {
            // 压二档顶线，等号算高一档
            return new BigDecimal("3.00");
        }
        if (qty.compareTo(th3) < 0) {
            return fold(new BigDecimal("3"), qty, th2, th3);
        }
        // 恰压三档顶线：顶格
        return GRADE_CAP;
    }

    /**
     * 档案三道顶线核形：缺数、不起于正数、倒挂，都是档案未维护齐，
     * 没法折算——不许拿残缺的顶线硬折出一个数充数。
     */
    public static void requireCeilings(BigDecimal th1, BigDecimal th2, BigDecimal th3) {
        if (th1 == null || th2 == null || th3 == null
                || th1.compareTo(BigDecimal.ZERO) <= 0
                || th1.compareTo(th2) > 0 || th2.compareTo(th3) > 0) {
            throw new ScopeCardAcceptException(ScopeCardRejectReason.CEILING_NOT_READY,
                    ScopeCardRejectReason.CEILING_NOT_READY.getLabel()
                            + "（一档：" + plain(th1) + "，二档：" + plain(th2) + "，三档：" + plain(th3) + "）");
        }
    }

    /** 档内折算：基数档 + 本档区间里走过的比例，留两位小数 */
    private static BigDecimal fold(BigDecimal base, BigDecimal qty, BigDecimal bandFloor, BigDecimal bandCeil) {
        BigDecimal walked = qty.subtract(bandFloor)
                .divide(bandCeil.subtract(bandFloor), 10, RoundingMode.HALF_UP);
        return base.add(walked).setScale(2, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal v) {
        return v == null ? "空" : v.toPlainString();
    }
}
