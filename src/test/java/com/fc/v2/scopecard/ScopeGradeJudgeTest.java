package com.fc.v2.scopecard;

import com.fc.v2.common.scopecard.ScopeCardAcceptException;
import com.fc.v2.common.scopecard.ScopeCardRejectReason;
import com.fc.v2.common.scopecard.ScopeGradeJudge;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 层级档折算单测：撞三道顶线的口径逐条对号——档内按比例往高一档走、
 * 压线算高一档、恰压三档顶线顶格、越过顶线拦在「挂用类别数」那一栏、
 * 顶线未维护齐没法折算；留两位小数。
 */
class ScopeGradeJudgeTest {

    private static final BigDecimal TH1 = new BigDecimal("30");
    private static final BigDecimal TH2 = new BigDecimal("120");
    private static final BigDecimal TH3 = new BigDecimal("360");

    private static BigDecimal grade(String qty) {
        return ScopeGradeJudge.gradeOf(new BigDecimal(qty), TH1, TH2, TH3);
    }

    @Test
    void 档内按占顶线的比例往高一档走() {
        // 15/30：一档走了一半 → 1.50
        assertEquals(new BigDecimal("1.50"), grade("15"));
        // (45-30)/(120-30)=1/6：二档走了六分之一 → 2.17（留两位小数，四舍五入）
        assertEquals(new BigDecimal("2.17"), grade("45"));
        // (300-120)/(360-120)=0.75：三档走了四分之三 → 3.75
        assertEquals(new BigDecimal("3.75"), grade("300"));
    }

    @Test
    void 压线算高一档() {
        assertEquals(new BigDecimal("2.00"), grade("30"));
        assertEquals(new BigDecimal("3.00"), grade("120"));
    }

    @Test
    void 恰压三档顶线顶格() {
        assertEquals(new BigDecimal("4.00"), grade("360"));
    }

    @Test
    void 零与负数拦在挂用类别数那一栏() {
        ScopeCardAcceptException zero = assertThrows(ScopeCardAcceptException.class, () -> grade("0"));
        assertEquals(ScopeCardRejectReason.QTY_NOT_POSITIVE.getCode(), zero.getReasonCode());
        assertEquals("挂用类别数", zero.getField());
        ScopeCardAcceptException neg = assertThrows(ScopeCardAcceptException.class, () -> grade("-3"));
        assertEquals(ScopeCardRejectReason.QTY_NOT_POSITIVE.getCode(), neg.getReasonCode());
        assertTrue(neg.getMessage().contains("-3"));
    }

    @Test
    void 越过三档顶线拦下并带顶线原值() {
        ScopeCardAcceptException over = assertThrows(ScopeCardAcceptException.class, () -> grade("361"));
        assertEquals(ScopeCardRejectReason.QTY_OVER_CEILING.getCode(), over.getReasonCode());
        assertEquals("挂用类别数", over.getField());
        assertTrue(over.getMessage().contains("361"));
        assertTrue(over.getMessage().contains("360"));
    }

    @Test
    void 顶线未维护齐没法折算() {
        assertThrows(ScopeCardAcceptException.class,
                () -> ScopeGradeJudge.gradeOf(new BigDecimal("10"), null, TH2, TH3));
        // 顶线倒挂一样算未维护齐
        assertThrows(ScopeCardAcceptException.class,
                () -> ScopeGradeJudge.gradeOf(new BigDecimal("10"), TH2, TH1, TH3));
    }

    @Test
    void 折算留两位小数() {
        // 1/3：1.3333… → 1.33
        assertEquals(new BigDecimal("1.33"),
                ScopeGradeJudge.gradeOf(BigDecimal.ONE, new BigDecimal("3"),
                        new BigDecimal("6"), new BigDecimal("9")));
    }
}
