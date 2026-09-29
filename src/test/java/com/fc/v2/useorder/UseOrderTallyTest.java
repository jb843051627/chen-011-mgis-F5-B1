package com.fc.v2.useorder;

import com.fc.v2.common.useorder.UseOrderAction;
import com.fc.v2.common.useorder.UseOrderFlowException;
import com.fc.v2.common.useorder.UseOrderTally;
import com.fc.v2.model.auto.TMgisUseEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 两处计数对账单测：页面摆出来的与单据里存下的必须同一个数。
 */
class UseOrderTallyTest {

    private static TMgisUseEntry sign(UseOrderAction action, int round) {
        TMgisUseEntry e = new TMgisUseEntry();
        e.setStage(action.getStage().name());
        e.setAction(action.name());
        e.setRoundNo(round);
        return e;
    }

    @Test
    void 省厅签数两处对得上才过() {
        List<TMgisUseEntry> chain = new ArrayList<TMgisUseEntry>();
        chain.add(sign(UseOrderAction.SIGN_SECRECY, 1));
        chain.add(sign(UseOrderAction.SIGN_PURPOSE, 1));
        // 旧轮的签不算进新一轮
        chain.add(sign(UseOrderAction.SIGN_SECRECY, 2));
        assertDoesNotThrow(() -> UseOrderTally.requireProvinceCount(2, chain, 1));
        assertDoesNotThrow(() -> UseOrderTally.requireProvinceCount(1, chain, 2));
    }

    @Test
    void 省厅签数差一个这一阶就没过() {
        List<TMgisUseEntry> chain = new ArrayList<TMgisUseEntry>();
        chain.add(sign(UseOrderAction.SIGN_SECRECY, 1));
        UseOrderFlowException e = assertThrows(UseOrderFlowException.class,
                () -> UseOrderTally.requireProvinceCount(2, chain, 1));
        assertTrue(e.getMessage().contains("省厅已签人数"));
    }

    @Test
    void 图幅行数两处对得上才过() {
        assertDoesNotThrow(() -> UseOrderTally.requireSheetCount(3, 3));
        assertThrows(UseOrderFlowException.class, () -> UseOrderTally.requireSheetCount(3, 2));
    }

    @Test
    void 清单三处行数恒等() {
        assertDoesNotThrow(() -> UseOrderTally.requireClaimLines(5, 5, 5));
        UseOrderFlowException e = assertThrows(UseOrderFlowException.class,
                () -> UseOrderTally.requireClaimLines(5, 5, 4));
        assertTrue(e.getMessage().contains("清单行数"));
        // 清单行数对但与核准件图幅不等也不行
        assertThrows(UseOrderFlowException.class, () -> UseOrderTally.requireClaimLines(6, 5, 5));
    }
}
