package com.fc.v2.common.usebill;

/**
 * 旧核准单票种：签批、打回、退回。
 *
 * @author fuce
 * @date 2026-09-30
 */
public enum LegacyBillAction {

    /** 签批：本层认一笔；联名档须不同的人签齐 */
    APPROVE("签批"),
    /** 写明不予核准：落笔即打回定死 */
    REJECT("打回"),
    /** 退回上一层：旧票籍销档，从上一层重新攒 */
    ROLLBACK("退回");

    private final String label;

    LegacyBillAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
