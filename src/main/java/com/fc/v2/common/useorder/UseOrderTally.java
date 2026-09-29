package com.fc.v2.common.useorder;

import com.fc.v2.model.auto.TMgisUseEntry;

import java.util.List;

/**
 * 两处计数对账：页面摆出来的与单据里存下的是两个地方的口径，两个地方必须
 * 同一个数，少一个就算这一阶没过。三处恒等都收在这里，别处不许另算一套。
 *
 * <ul>
 *   <li>省厅已签人数：单据上落账一份，签署链最新一轮当场重算一份；</li>
 *   <li>图幅行数：单据上落账一份，申请图幅行表当场数一份；</li>
 *   <li>清单行数：清单头落账一份，清单行表当场数一份，且须与核准件图幅行恒等。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-28
 */
public final class UseOrderTally {

    private UseOrderTally() {
    }

    /** 链上最新一轮省厅两岗已签数（落账口径外的第二个口径） */
    public static int provinceSigned(List<TMgisUseEntry> chain, int roundNo) {
        return UseOrderFlow.provinceSigned(chain, roundNo);
    }

    /** 省厅签数两处须同一个数：单据上存的与链上当场重算的对不上即顶回 */
    public static void requireProvinceCount(int stored, List<TMgisUseEntry> chain, int roundNo) {
        int recomputed = provinceSigned(chain, roundNo);
        if (stored != recomputed) {
            throw new UseOrderFlowException("省厅已签人数两处口径对不上：单据上存着 " + stored
                    + " 枚，签署链第 " + roundNo + " 轮当场数出 " + recomputed
                    + " 枚，少一个这一阶就算没过——库里这一件被人动过");
        }
    }

    /** 图幅行数两处须同一个数：单据上存的与行表当场数的对不上即顶回 */
    public static void requireSheetCount(int stored, int actualRows) {
        if (stored != actualRows) {
            throw new UseOrderFlowException("图幅行数两处口径对不上：单据上存着 " + stored
                    + " 行，图幅行表里当场数出 " + actualRows + " 行——库里这一件被人动过");
        }
    }

    /**
     * 领用清单三处恒等：清单头存的行数 = 清单行表行数 = 核准件图幅行数。
     * 一家单位一册图对一条清单，对不上即顶回，清单摆不出来。
     */
    public static void requireClaimLines(int orderSheetRows, int storedLineCount, int actualClaimRows) {
        if (storedLineCount != actualClaimRows || actualClaimRows != orderSheetRows) {
            throw new UseOrderFlowException("领用清单行数对不上：核准件图幅 " + orderSheetRows
                    + " 行，清单头存着 " + storedLineCount + " 行，清单行表里当场数出 " + actualClaimRows
                    + " 行；一家单位一册图对一条清单，差一行这份清单都不能给窗口");
        }
    }
}
