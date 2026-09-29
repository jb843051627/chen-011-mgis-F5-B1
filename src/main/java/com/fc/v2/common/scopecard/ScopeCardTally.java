package com.fc.v2.common.scopecard;

import com.fc.v2.model.auto.TMgisScopeCard;
import com.fc.v2.model.auto.TMgisUnitDoc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 看账两口径：省局从新增类别最多的单位往下排，属地把单位按最末一级
 * 归堆再数。两条路数出来的卡数必须一致，差一笔就算这一屏没过——
 * 两个口径同出这一处计算，别处不许另写一套。
 *
 * <p>归堆键各走各的：省局口径按卡上自己记的资质单位编号归堆（不依赖
 * 档案），属地口径按档案注册地的最末一级（区县）归堆。卡对不上档案时，
 * 省局口径照数、属地口径数不到——两条路立时差一笔，这一屏就是没过，
 * 追得到是对不上档案的那几笔。</p>
 *
 * @author fuce
 * @date 2026-09-27
 */
public final class ScopeCardTally {

    /** 属地名址的分段记号：省—市—区县 */
    private static final String SEG = "—";

    private final int totalCards;
    private final int provinceTotal;
    private final int regionTotal;
    private final int orphanCount;
    private final List<ScopeCardTallyRow> provinceRows;
    private final List<ScopeCardTallyRow> regionRows;

    private ScopeCardTally(int totalCards, int provinceTotal, int regionTotal, int orphanCount,
                           List<ScopeCardTallyRow> provinceRows, List<ScopeCardTallyRow> regionRows) {
        this.totalCards = totalCards;
        this.provinceTotal = provinceTotal;
        this.regionTotal = regionTotal;
        this.orphanCount = orphanCount;
        this.provinceRows = provinceRows;
        this.regionRows = regionRows;
    }

    /**
     * 两口径归堆。传入的卡集须是同一可见范围（省局全览或本市），
     * 两口径从同一叠卡出发各数各的。
     *
     * @param cards        可见范围内的卡
     * @param docsBySiteNo 相关档案（资质单位编号 → 档案）
     */
    public static ScopeCardTally tally(List<TMgisScopeCard> cards, Map<String, TMgisUnitDoc> docsBySiteNo) {
        List<TMgisScopeCard> safe = cards == null ? new ArrayList<TMgisScopeCard>() : cards;

        // 省局口径：按卡上自己记的资质单位编号归堆，新增合计大的在前
        Map<String, List<TMgisScopeCard>> bySite = new LinkedHashMap<String, List<TMgisScopeCard>>();
        // 属地口径：按档案注册地最末一级（区县）归堆；对不上档案的卡进不了任何堆
        Map<String, List<TMgisScopeCard>> byDistrict = new LinkedHashMap<String, List<TMgisScopeCard>>();
        Map<String, String> siteNameOf = new LinkedHashMap<String, String>();
        int orphan = 0;
        for (TMgisScopeCard card : safe) {
            String siteNo = card.getSiteNo();
            if (siteNo != null && !siteNo.trim().isEmpty()) {
                bySite.computeIfAbsent(siteNo.trim(), k -> new ArrayList<TMgisScopeCard>()).add(card);
            }
            TMgisUnitDoc doc = siteNo == null ? null : docsBySiteNo.get(siteNo.trim());
            if (doc == null) {
                orphan++;
                siteNameOf.put(siteNo == null ? "" : siteNo.trim(), "档案缺失");
            } else {
                siteNameOf.put(siteNo.trim(), doc.getSiteName());
                String district = districtOf(doc.getRoadName());
                if (district != null) {
                    byDistrict.computeIfAbsent(district, k -> new ArrayList<TMgisScopeCard>()).add(card);
                }
            }
        }

        List<ScopeCardTallyRow> provinceRows = new ArrayList<ScopeCardTallyRow>();
        int provinceTotal = 0;
        for (Map.Entry<String, List<TMgisScopeCard>> e : bySite.entrySet()) {
            BigDecimal fineSum = BigDecimal.ZERO;
            for (TMgisScopeCard card : e.getValue()) {
                fineSum = fineSum.add(card.getFineAmt() == null ? BigDecimal.ZERO : card.getFineAmt());
            }
            provinceTotal += e.getValue().size();
            provinceRows.add(new ScopeCardTallyRow(e.getKey(), siteNameOf.get(e.getKey()), 1, e.getValue().size(), fineSum));
        }
        // 省局从新增类别最多的单位往下排；同额按单位编号字典序，排得稳
        provinceRows.sort((a, b) -> {
            int cmp = b.getFineAmtSum().compareTo(a.getFineAmtSum());
            return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
        });

        List<ScopeCardTallyRow> regionRows = new ArrayList<ScopeCardTallyRow>();
        int regionTotal = 0;
        for (Map.Entry<String, List<TMgisScopeCard>> e : byDistrict.entrySet()) {
            java.util.Set<String> units = new java.util.HashSet<String>();
            for (TMgisScopeCard card : e.getValue()) {
                units.add(card.getSiteNo() == null ? "" : card.getSiteNo().trim());
            }
            regionTotal += e.getValue().size();
            regionRows.add(new ScopeCardTallyRow(e.getKey(), e.getKey(), units.size(), e.getValue().size(), null));
        }
        // 属地按区县名字典序归堆罗列
        regionRows.sort((a, b) -> a.getKey().compareTo(b.getKey()));

        return new ScopeCardTally(safe.size(), provinceTotal, regionTotal, orphan, provinceRows, regionRows);
    }

    /** 账面卡数（同一可见范围） */
    public int getTotalCards() {
        return totalCards;
    }

    /** 省局口径数出来的卡数 */
    public int getProvinceTotal() {
        return provinceTotal;
    }

    /** 属地口径数出来的卡数 */
    public int getRegionTotal() {
        return regionTotal;
    }

    /** 对不上档案的卡数（两口径差笔的祸根） */
    public int getOrphanCount() {
        return orphanCount;
    }

    /** 省局口径行（新增合计降序） */
    public List<ScopeCardTallyRow> getProvinceRows() {
        return provinceRows;
    }

    /** 属地口径行（区县归堆） */
    public List<ScopeCardTallyRow> getRegionRows() {
        return regionRows;
    }

    /** 两条路数出来的卡数一致，这一屏才算过 */
    public boolean isConsistent() {
        return provinceTotal == totalCards && regionTotal == totalCards;
    }

    /** 差一笔就算这一屏没过：不一致当场拦住，不许把两本各说各话的账摆上桌 */
    public void requireConsistent() {
        if (!isConsistent()) {
            throw new IllegalStateException("看账两口径对不上：账面共 " + totalCards + " 笔，省局口径数出 "
                    + provinceTotal + " 笔、属地口径数出 " + regionTotal + " 笔，差一笔这一屏没过"
                    + (orphanCount > 0 ? "（对不上档案的卡 " + orphanCount + " 笔）" : ""));
        }
    }

    /** 属地名址取市段：「省—市—区县」的中段；分不出段来返回 null */
    public static String cityOf(String roadName) {
        String[] segs = segments(roadName);
        return segs.length >= 2 ? segs[1] : null;
    }

    /** 属地名址取最末一级（区县）：「省—市—区县」的末段；分不出段来返回 null */
    public static String districtOf(String roadName) {
        String[] segs = segments(roadName);
        return segs.length >= 1 ? segs[segs.length - 1] : null;
    }

    private static String[] segments(String roadName) {
        if (roadName == null) {
            return new String[0];
        }
        String[] raw = roadName.split(SEG);
        List<String> segs = new ArrayList<String>(raw.length);
        for (String s : raw) {
            String t = s == null ? "" : s.trim();
            if (!t.isEmpty()) {
                segs.add(t);
            }
        }
        return segs.toArray(new String[0]);
    }
}
