package com.fc.v2.common.scopecard;

import java.math.BigDecimal;

/**
 * 看账一行。省局口径一行是一个单位（按本年度新增挂用类别数合计往下排）；
 * 属地口径一行是一堆最末一级属地（区县）——单位数、卡数两栏都有。
 *
 * @author fuce
 * @date 2026-09-27
 */
public class ScopeCardTallyRow {

    /** 归堆键：省局口径是资质单位编号，属地口径是区县名 */
    private final String key;

    /** 抬头：省局口径是单位名称，属地口径是区县名 */
    private final String label;

    /** 这一堆几个单位（属地口径用；省局口径恒为 1） */
    private final int unitCount;

    /** 这一堆卡数 */
    private final int cardCount;

    /** 本年度新增挂用类别数合计（省局口径的排序键；属地口径不排它） */
    private final BigDecimal fineAmtSum;

    public ScopeCardTallyRow(String key, String label, int unitCount, int cardCount, BigDecimal fineAmtSum) {
        this.key = key;
        this.label = label;
        this.unitCount = unitCount;
        this.cardCount = cardCount;
        this.fineAmtSum = fineAmtSum;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public int getUnitCount() {
        return unitCount;
    }

    public int getCardCount() {
        return cardCount;
    }

    public BigDecimal getFineAmtSum() {
        return fineAmtSum;
    }
}
