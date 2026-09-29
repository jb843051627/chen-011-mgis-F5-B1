package com.fc.v2.useorder;

import com.fc.v2.common.useorder.UseAcceptVerdict;
import com.fc.v2.common.useorder.UseOrderFlowException;
import com.fc.v2.common.useorder.UseOrderJudge;
import com.fc.v2.common.useorder.UseOrderRejectReason;
import com.fc.v2.common.useorder.UseSecretLevel;
import com.fc.v2.model.custom.UseOrderSignRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 受理推演单测：进门那一张缺一项受理不下去，规矩逐条对号。
 */
class UseOrderJudgeTest {

    private static final int YEAR = 2026;

    private static UseOrderSignRequest.Sheet sheet(String code, String level, String year) {
        UseOrderSignRequest.Sheet s = new UseOrderSignRequest.Sheet();
        s.setSheetCode(code);
        s.setSecretLevel(level);
        s.setMapYear(year);
        return s;
    }

    private static UseOrderSignRequest req(String applicant, String purpose,
                                           List<UseOrderSignRequest.Sheet> sheets) {
        UseOrderSignRequest r = new UseOrderSignRequest();
        r.setAction("ACCEPT");
        r.setApplicant(applicant);
        r.setPurpose(purpose);
        r.setSheets(sheets);
        return r;
    }

    private static List<UseOrderSignRequest.Sheet> sheets(UseOrderSignRequest.Sheet... s) {
        return new ArrayList<UseOrderSignRequest.Sheet>(java.util.Arrays.asList(s));
    }

    @Test
    void 五样齐着才受理得下去() {
        UseAcceptVerdict v = UseOrderJudge.judgeAccept(
                req("临川市规划院", "城市总体规划修编",
                        sheets(sheet("H49G001002", "机密", "2021"), sheet("H49G001003", "秘密", "2024"))), YEAR);
        assertEquals("临川市规划院", v.getApplicant());
        assertEquals(2, v.getSheetCount());
        assertEquals(UseSecretLevel.SECRET, v.getSheets().get(0).getSecretLevel());
        assertEquals(2021, v.getSheets().get(0).getMapYear());
        // 行次自 1 连排
        assertEquals(2, v.getSheets().get(1).getRowNo());
    }

    @Test
    void 密级中文名也认() {
        UseAcceptVerdict v = UseOrderJudge.judgeAccept(
                req("单位", "用途", sheets(sheet("A1", "绝密", "2020"), sheet("A2", "内部", "2020"))), YEAR);
        assertEquals(UseSecretLevel.TOP_SECRET, v.getSheets().get(0).getSecretLevel());
        assertEquals(UseSecretLevel.INTERNAL, v.getSheets().get(1).getSecretLevel());
    }

    @Test
    void 申请单位缺了受理不下去() {
        UseOrderFlowException e = assertThrows(UseOrderFlowException.class,
                () -> UseOrderJudge.judgeAccept(req("  ", "用途", sheets(sheet("A1", "机密", "2020"))), YEAR));
        assertEquals(UseOrderRejectReason.MISSING_APPLICANT.getCode(), e.getReasonCode());
    }

    @Test
    void 用途缺了受理不下去() {
        UseOrderFlowException e = assertThrows(UseOrderFlowException.class,
                () -> UseOrderJudge.judgeAccept(req("单位", null, sheets(sheet("A1", "机密", "2020"))), YEAR));
        assertEquals(UseOrderRejectReason.MISSING_PURPOSE.getCode(), e.getReasonCode());
    }

    @Test
    void 白表受理不下去() {
        UseOrderFlowException e = assertThrows(UseOrderFlowException.class,
                () -> UseOrderJudge.judgeAccept(req("单位", "用途", new ArrayList<UseOrderSignRequest.Sheet>()), YEAR));
        assertEquals(UseOrderRejectReason.MISSING_SHEET.getCode(), e.getReasonCode());
    }

    @Test
    void 某行代号空了整单顶回() {
        UseOrderFlowException e = assertThrows(UseOrderFlowException.class,
                () -> UseOrderJudge.judgeAccept(
                        req("单位", "用途", sheets(sheet("A1", "机密", "2020"), sheet(" ", "机密", "2020"))), YEAR));
        assertEquals(UseOrderRejectReason.MISSING_SHEET_CODE.getCode(), e.getReasonCode());
        assertTrue(e.getMessage().contains("第 2 行"));
    }

    @Test
    void 册内代号重号整单顶回() {
        UseOrderFlowException e = assertThrows(UseOrderFlowException.class,
                () -> UseOrderJudge.judgeAccept(
                        req("单位", "用途", sheets(sheet("A1", "机密", "2020"), sheet("A1", "秘密", "2021"))), YEAR));
        assertEquals(UseOrderRejectReason.DUPLICATE_SHEET_CODE.getCode(), e.getReasonCode());
        assertTrue(e.getMessage().contains("A1"));
    }

    @Test
    void 密级空着或不识整单顶回() {
        UseOrderFlowException e1 = assertThrows(UseOrderFlowException.class,
                () -> UseOrderJudge.judgeAccept(req("单位", "用途", sheets(sheet("A1", " ", "2020"))), YEAR));
        assertEquals(UseOrderRejectReason.MISSING_SECRET_LEVEL.getCode(), e1.getReasonCode());
        UseOrderFlowException e2 = assertThrows(UseOrderFlowException.class,
                () -> UseOrderJudge.judgeAccept(req("单位", "用途", sheets(sheet("A1", "特急", "2020"))), YEAR));
        assertEquals(UseOrderRejectReason.UNKNOWN_SECRET_LEVEL.getCode(), e2.getReasonCode());
        assertTrue(e2.getMessage().contains("特急"));
    }

    @Test
    void 出图年份不是四位整数顶回() {
        UseOrderFlowException e1 = assertThrows(UseOrderFlowException.class,
                () -> UseOrderJudge.judgeAccept(req("单位", "用途", sheets(sheet("A1", "机密", "2021年"))), YEAR));
        assertEquals(UseOrderRejectReason.MAP_YEAR_NOT_A_NUMBER.getCode(), e1.getReasonCode());
        UseOrderFlowException e2 = assertThrows(UseOrderFlowException.class,
                () -> UseOrderJudge.judgeAccept(req("单位", "用途", sheets(sheet("A1", "机密", "2021.0"))), YEAR));
        assertEquals(UseOrderRejectReason.MAP_YEAR_NOT_A_NUMBER.getCode(), e2.getReasonCode());
    }

    @Test
    void 出图年份越界顶回() {
        UseOrderFlowException e1 = assertThrows(UseOrderFlowException.class,
                () -> UseOrderJudge.judgeAccept(req("单位", "用途", sheets(sheet("A1", "机密", "1899"))), YEAR));
        assertEquals(UseOrderRejectReason.MAP_YEAR_OUT_OF_RANGE.getCode(), e1.getReasonCode());
        // 越过今年
        UseOrderFlowException e2 = assertThrows(UseOrderFlowException.class,
                () -> UseOrderJudge.judgeAccept(req("单位", "用途", sheets(sheet("A1", "机密", "2027"))), YEAR));
        assertEquals(UseOrderRejectReason.MAP_YEAR_OUT_OF_RANGE.getCode(), e2.getReasonCode());
        // 压线今年认
        UseAcceptVerdict v = UseOrderJudge.judgeAccept(req("单位", "用途", sheets(sheet("A1", "机密", "2026"))), YEAR);
        assertEquals(2026, v.getSheets().get(0).getMapYear());
    }

    @Test
    void 越过行数上限整单不收() {
        List<UseOrderSignRequest.Sheet> many = new ArrayList<UseOrderSignRequest.Sheet>();
        for (int i = 0; i < UseOrderJudge.MAX_SHEETS + 1; i++) {
            many.add(sheet("A" + i, "机密", "2020"));
        }
        UseOrderFlowException e = assertThrows(UseOrderFlowException.class,
                () -> UseOrderJudge.judgeAccept(req("单位", "用途", many), YEAR));
        assertEquals(UseOrderRejectReason.SHEET_OVER_CEILING.getCode(), e.getReasonCode());
    }
}
