package com.fc.v2.scopecard;

import com.fc.v2.common.scopecard.ScopeCardTally;
import com.fc.v2.common.scopecard.ScopeCardTallyRow;
import com.fc.v2.model.auto.TMgisScopeCard;
import com.fc.v2.model.auto.TMgisUnitDoc;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 看账两口径单测：省局从新增类别最多的单位往下排，属地按最末一级归堆；
 * 两条路数出来的卡数必须一致，差一笔就算这一屏没过。
 */
class ScopeCardTallyTest {

    private static TMgisUnitDoc doc(String siteNo, String name, String road) {
        TMgisUnitDoc d = new TMgisUnitDoc();
        d.setSiteNo(siteNo);
        d.setSiteName(name);
        d.setRoadName(road);
        d.setStatus(0);
        return d;
    }

    private static TMgisScopeCard card(String siteNo, String fineAmt) {
        TMgisScopeCard c = new TMgisScopeCard();
        c.setSiteNo(siteNo);
        c.setFineAmt(fineAmt == null ? null : new BigDecimal(fineAmt));
        return c;
    }

    private static Map<String, TMgisUnitDoc> docs(TMgisUnitDoc... arr) {
        Map<String, TMgisUnitDoc> map = new LinkedHashMap<String, TMgisUnitDoc>();
        for (TMgisUnitDoc d : arr) {
            map.put(d.getSiteNo(), d);
        }
        return map;
    }

    @Test
    void 两口径归堆各就各位卡数一致() {
        Map<String, TMgisUnitDoc> docs = docs(
                doc("ZA", "甲院", "江南省—临川市—城东区"),
                doc("ZB", "乙所", "江南省—临川市—城东区"),
                doc("ZC", "丙队", "江南省—临川市—城西区"));
        List<TMgisScopeCard> cards = new ArrayList<TMgisScopeCard>();
        cards.add(card("ZA", "5"));
        cards.add(card("ZA", "3"));
        cards.add(card("ZB", "9"));
        cards.add(card("ZC", "1"));

        ScopeCardTally tally = ScopeCardTally.tally(cards, docs);
        assertTrue(tally.isConsistent());
        assertEquals(4, tally.getTotalCards());
        assertEquals(4, tally.getProvinceTotal());
        assertEquals(4, tally.getRegionTotal());
        assertEquals(0, tally.getOrphanCount());

        // 省局口径：新增合计降序——ZB(9) 在 ZA(5+3=8) 前，ZA 在 ZC(1) 前
        List<ScopeCardTallyRow> province = tally.getProvinceRows();
        assertEquals(3, province.size());
        assertEquals("ZB", province.get(0).getKey());
        assertEquals(new BigDecimal("9"), province.get(0).getFineAmtSum());
        assertEquals(1, province.get(0).getCardCount());
        assertEquals("ZA", province.get(1).getKey());
        assertEquals(new BigDecimal("8"), province.get(1).getFineAmtSum());
        assertEquals(2, province.get(1).getCardCount());
        assertEquals("ZC", province.get(2).getKey());

        // 属地口径：最末一级归堆——城东区两单位三笔卡，城西区一单位一笔
        List<ScopeCardTallyRow> region = tally.getRegionRows();
        assertEquals(2, region.size());
        assertEquals("城东区", region.get(0).getKey());
        assertEquals(2, region.get(0).getUnitCount());
        assertEquals(3, region.get(0).getCardCount());
        assertEquals("城西区", region.get(1).getKey());
        assertEquals(1, region.get(1).getUnitCount());
        assertEquals(1, region.get(1).getCardCount());
    }

    @Test
    void 对不上档案的卡让两口径差一笔这一屏没过() {
        Map<String, TMgisUnitDoc> docs = docs(doc("ZA", "甲院", "江南省—临川市—城东区"));
        List<TMgisScopeCard> cards = new ArrayList<TMgisScopeCard>();
        cards.add(card("ZA", "5"));
        // 这笔卡挂的单位编号档案里查不到：省局口径照数，属地口径数不到
        cards.add(card("ZX", "2"));

        ScopeCardTally tally = ScopeCardTally.tally(cards, docs);
        assertFalse(tally.isConsistent());
        assertEquals(2, tally.getTotalCards());
        assertEquals(2, tally.getProvinceTotal());
        assertEquals(1, tally.getRegionTotal());
        assertEquals(1, tally.getOrphanCount());

        IllegalStateException e = assertThrows(IllegalStateException.class, tally::requireConsistent);
        assertTrue(e.getMessage().contains("差一笔"));
    }

    @Test
    void 省局口径同额按单位编号排得稳() {
        Map<String, TMgisUnitDoc> docs = docs(
                doc("ZB", "乙所", "江南省—临川市—城东区"),
                doc("ZA", "甲院", "江南省—临川市—城东区"));
        List<TMgisScopeCard> cards = new ArrayList<TMgisScopeCard>();
        cards.add(card("ZB", "5"));
        cards.add(card("ZA", "5"));
        List<ScopeCardTallyRow> province = ScopeCardTally.tally(cards, docs).getProvinceRows();
        assertEquals("ZA", province.get(0).getKey());
        assertEquals("ZB", province.get(1).getKey());
    }

    @Test
    void 属地名址分段() {
        assertEquals("临川市", ScopeCardTally.cityOf("江南省—临川市—城东区"));
        assertEquals("城东区", ScopeCardTally.districtOf("江南省—临川市—城东区"));
        assertNull(ScopeCardTally.cityOf(null));
        assertNull(ScopeCardTally.districtOf("  "));
    }
}
