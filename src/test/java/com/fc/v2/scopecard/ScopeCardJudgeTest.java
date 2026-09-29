package com.fc.v2.scopecard;

import com.fc.v2.common.scopecard.ScopeCardAcceptException;
import com.fc.v2.common.scopecard.ScopeCardJudge;
import com.fc.v2.common.scopecard.ScopeCardProcedure;
import com.fc.v2.common.scopecard.ScopeCardRejectReason;
import com.fc.v2.common.scopecard.ScopeCardSource;
import com.fc.v2.common.scopecard.ScopeCardVerdict;
import com.fc.v2.model.auto.TMgisScopeCard;
import com.fc.v2.model.auto.TMgisScopeCategory;
import com.fc.v2.model.auto.TMgisUnitDoc;
import com.fc.v2.model.custom.ScopeCardRegisterRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登记推演单测：对档案只按编号、注销档案不收、第二张顶回、只许挂末级、
 * 并走旧类别不给落、挂用类别数三样浑数拦在「挂用类别数」那一栏——
 * 每条拦下都要说清拦在哪一栏，只甩一句「不合法」等于没说。
 */
class ScopeCardJudgeTest {

    // ---------- 帮手 ----------

    private static TMgisUnitDoc doc(int status) {
        TMgisUnitDoc d = new TMgisUnitDoc();
        d.setId(9L);
        d.setSiteNo("ZZ00");
        d.setSiteName("江南省测绘院第一地理信息工程院基础档案");
        d.setSiteType("事业单位");
        d.setRoadName("江南省—临川市—城东区");
        d.setTh1Max(new BigDecimal("30"));
        d.setTh2Max(new BigDecimal("120"));
        d.setTh3Max(new BigDecimal("360"));
        d.setStatus(status);
        return d;
    }

    private static Map<String, TMgisScopeCategory> index() {
        Map<String, TMgisScopeCategory> idx = new LinkedHashMap<String, TMgisScopeCategory>();
        idx.put("GC", cate("GC", "工程测量", 1, null));
        idx.put("GC01", cate("GC01", "控制测量", 2, null));
        idx.put("GC02", cate("GC02", "地形测量", 2, null));
        idx.put("JX01", cate("JX01", "界线测绘", 2, null));
        idx.put("GC09", cate("GC09", "旧工程测量", 2, "GC01"));
        return idx;
    }

    private static TMgisScopeCategory cate(String code, String name, int lvl, String mergedInto) {
        TMgisScopeCategory c = new TMgisScopeCategory();
        c.setCateCode(code);
        c.setCateName(name);
        c.setLvl(lvl);
        c.setMergedInto(mergedInto);
        c.setStatus(0);
        return c;
    }

    private static ScopeCardRegisterRequest req(String bill, String from, String siteNo, String seg,
                                                String qty, String fine, String... categories) {
        ScopeCardRegisterRequest r = new ScopeCardRegisterRequest();
        r.setProcedure("REGISTER");
        r.setSourceBillNo(bill);
        r.setSourceFrom(from);
        r.setSiteNo(siteNo);
        r.setScopeSeg(seg);
        r.setQty(qty);
        r.setFineAmt(fine);
        r.setCategories(Arrays.asList(categories));
        return r;
    }

    private static ScopeCardAcceptException thrown(ScopeCardRegisterRequest r) {
        return assertThrows(ScopeCardAcceptException.class,
                () -> ScopeCardJudge.judgeRegister(r, doc(0), null, index()));
    }

    // ---------- 立卡过得去 ----------

    @Test
    void 立卡推演定局逐项对号() {
        ScopeCardVerdict v = ScopeCardJudge.judgeRegister(
                req("LW-001", "单位自报", "ZZ00", "SEG-A", "45", "5", "GC01", "GC02", "GC01"),
                doc(0), null, index());
        assertEquals("LW-001", v.getSourceBillNo());
        assertEquals(ScopeCardSource.UNIT_SELF, v.getSourceFrom());
        assertEquals("ZZ00", v.getSiteNo());
        assertEquals(Integer.valueOf(9), v.getSiteId());
        assertEquals("SEG-A", v.getScopeSeg());
        // 清单内重号按一个算，次序照填报
        assertEquals(Arrays.asList("GC01", "GC02"), v.getCategories());
        assertEquals(new BigDecimal("45"), v.getQty());
        assertEquals(new BigDecimal("5"), v.getFineAmt());
        // 层级档系统折算：(45-30)/90 → 2.17
        assertEquals(new BigDecimal("2.17"), v.getGradeLevel());
    }

    @Test
    void 本年度新增空着按零计() {
        ScopeCardVerdict v = ScopeCardJudge.judgeRegister(
                req("LW-002", "CITY_FORWARD", "ZZ00", "SEG-A", "10", null, "GC01"),
                doc(0), null, index());
        assertEquals(BigDecimal.ZERO, v.getFineAmt());
        assertEquals(ScopeCardSource.CITY_FORWARD, v.getSourceFrom());
    }

    // ---------- 拦下缘故逐条对号，条条指到栏 ----------

    @Test
    void 来文单号未填() {
        ScopeCardAcceptException e = thrown(req(" ", "单位自报", "ZZ00", "SEG-A", "10", null, "GC01"));
        assertEquals(ScopeCardRejectReason.MISSING_SOURCE_BILL.getCode(), e.getReasonCode());
        assertEquals("来文单号", e.getField());
    }

    @Test
    void 来文来源不识带原值() {
        ScopeCardAcceptException e = thrown(req("LW-003", "省局直报", "ZZ00", "SEG-A", "10", null, "GC01"));
        assertEquals(ScopeCardRejectReason.UNKNOWN_SOURCE.getCode(), e.getReasonCode());
        assertTrue(e.getMessage().contains("省局直报"));
    }

    @Test
    void 单位编号未填与对不上各是各的话() {
        ScopeCardAcceptException miss = thrown(req("LW-004", "单位自报", " ", "SEG-A", "10", null, "GC01"));
        assertEquals(ScopeCardRejectReason.MISSING_SITE_NO.getCode(), miss.getReasonCode());
        assertEquals("资质单位编号", miss.getField());

        // 对不上档案：只按名称匹配不作数，名称再像也不认
        ScopeCardAcceptException notFound = assertThrows(ScopeCardAcceptException.class,
                () -> ScopeCardJudge.judgeRegister(
                        req("LW-005", "单位自报", "ZZ99", "SEG-A", "10", null, "GC01"), null, null, index()));
        assertEquals(ScopeCardRejectReason.SITE_DOC_NOT_FOUND.getCode(), notFound.getReasonCode());
        assertTrue(notFound.getMessage().contains("ZZ99"));
    }

    @Test
    void 档案已注销不收新卡() {
        ScopeCardAcceptException e = assertThrows(ScopeCardAcceptException.class,
                () -> ScopeCardJudge.judgeRegister(
                        req("LW-006", "单位自报", "ZZ00", "SEG-A", "10", null, "GC01"), doc(1), null, index()));
        assertEquals(ScopeCardRejectReason.SITE_DOC_CANCELLED.getCode(), e.getReasonCode());
        assertEquals("资质单位编号", e.getField());
    }

    @Test
    void 同一单位同一段范围第二张顶回() {
        TMgisScopeCard first = new TMgisScopeCard();
        first.setBillNo("SC100");
        ScopeCardAcceptException e = assertThrows(ScopeCardAcceptException.class,
                () -> ScopeCardJudge.judgeRegister(
                        req("LW-007", "单位自报", "ZZ00", "SEG-A", "10", null, "GC01"), doc(0), first, index()));
        assertEquals(ScopeCardRejectReason.DUPLICATE_SCOPE.getCode(), e.getReasonCode());
        assertEquals("作业范围段", e.getField());
        assertTrue(e.getMessage().contains("SC100"));
    }

    @Test
    void 类别清单三样不给落() {
        // 空清单
        ScopeCardAcceptException miss = assertThrows(ScopeCardAcceptException.class,
                () -> ScopeCardJudge.judgeRegister(
                        req("LW-008", "单位自报", "ZZ00", "SEG-A", "10", null), doc(0), null, index()));
        assertEquals(ScopeCardRejectReason.MISSING_CATEGORY.getCode(), miss.getReasonCode());
        assertEquals("挂用类别", miss.getField());

        // 代号不识
        ScopeCardAcceptException unknown = thrown(req("LW-009", "单位自报", "ZZ00", "SEG-A", "10", null, "XX99"));
        assertEquals(ScopeCardRejectReason.UNKNOWN_CATEGORY.getCode(), unknown.getReasonCode());
        assertTrue(unknown.getMessage().contains("XX99"));

        // 大类与末级混挂：整单不给落，指到是哪个代号
        ScopeCardAcceptException major = thrown(req("LW-010", "单位自报", "ZZ00", "SEG-A", "10", null, "GC01", "GC"));
        assertEquals(ScopeCardRejectReason.MAJOR_NOT_LEAF.getCode(), major.getReasonCode());
        assertTrue(major.getMessage().contains("GC"));

        // 早已并入别处的旧类别：不给落，并到哪里去写清
        ScopeCardAcceptException merged = thrown(req("LW-011", "单位自报", "ZZ00", "SEG-A", "10", null, "GC09"));
        assertEquals(ScopeCardRejectReason.MERGED_CATEGORY.getCode(), merged.getReasonCode());
        assertTrue(merged.getMessage().contains("GC09"));
        assertTrue(merged.getMessage().contains("GC01"));
    }

    @Test
    void 挂用类别数三样浑数都拦在那一栏() {
        ScopeCardAcceptException nan = thrown(req("LW-012", "单位自报", "ZZ00", "SEG-A", "三", null, "GC01"));
        assertEquals(ScopeCardRejectReason.QTY_NOT_A_NUMBER.getCode(), nan.getReasonCode());
        assertEquals("挂用类别数", nan.getField());
        assertTrue(nan.getMessage().contains("三"));

        ScopeCardAcceptException zero = thrown(req("LW-013", "单位自报", "ZZ00", "SEG-A", "0", null, "GC01"));
        assertEquals(ScopeCardRejectReason.QTY_NOT_POSITIVE.getCode(), zero.getReasonCode());
        assertEquals("挂用类别数", zero.getField());

        ScopeCardAcceptException neg = thrown(req("LW-014", "单位自报", "ZZ00", "SEG-A", "-2", null, "GC01"));
        assertEquals(ScopeCardRejectReason.QTY_NOT_POSITIVE.getCode(), neg.getReasonCode());

        ScopeCardAcceptException over = thrown(req("LW-015", "单位自报", "ZZ00", "SEG-A", "999", null, "GC01"));
        assertEquals(ScopeCardRejectReason.QTY_OVER_CEILING.getCode(), over.getReasonCode());
        assertEquals("挂用类别数", over.getField());
    }

    @Test
    void 本年度新增负数拦在那一栏() {
        ScopeCardAcceptException e = thrown(req("LW-016", "单位自报", "ZZ00", "SEG-A", "10", "-1", "GC01"));
        assertEquals(ScopeCardRejectReason.FINE_AMT_NEGATIVE.getCode(), e.getReasonCode());
        assertEquals("本年度新增挂用类别数", e.getField());
    }

    // ---------- 走站 ----------

    @Test
    void 走站只开眼下那道手续的口子() {
        TMgisScopeCard card = new TMgisScopeCard();
        card.setStatus(ScopeCardJudge.STATUS_PENDING);
        // 待核对：核对开得，定档开不得
        ScopeCardJudge.checkStation(card, ScopeCardProcedure.CHECK);
        ScopeCardAcceptException e1 = assertThrows(ScopeCardAcceptException.class,
                () -> ScopeCardJudge.checkStation(card, ScopeCardProcedure.GRADE));
        assertEquals(ScopeCardRejectReason.BAD_STATION.getCode(), e1.getReasonCode());
        assertTrue(e1.getMessage().contains("待核对"));

        // 已核对：定档开得，核对走回头路开不得
        card.setStatus(ScopeCardJudge.STATUS_CHECKED);
        ScopeCardJudge.checkStation(card, ScopeCardProcedure.GRADE);
        assertThrows(ScopeCardAcceptException.class,
                () -> ScopeCardJudge.checkStation(card, ScopeCardProcedure.CHECK));

        // 已定档：类别增减仍开得（类别有增减，档跟着重折）
        card.setStatus(ScopeCardJudge.STATUS_GRADED);
        ScopeCardJudge.checkStation(card, ScopeCardProcedure.ADJUST);
    }

    @Test
    void 注销档案老卡不许接着添东西() {
        ScopeCardAcceptException e = assertThrows(ScopeCardAcceptException.class,
                () -> ScopeCardJudge.checkDocWritable(doc(1)));
        assertEquals(ScopeCardRejectReason.SITE_DOC_CANCELLED.getCode(), e.getReasonCode());
        assertTrue(e.getMessage().contains("老卡照旧查得到"));
    }
}
