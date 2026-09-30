package com.fc.v2.usebill;

import com.fc.v2.common.usebill.LegacyBillAction;
import com.fc.v2.common.usebill.LegacyBillException;
import com.fc.v2.common.usebill.LegacyUseBillJudge;
import com.fc.v2.model.auto.TMgisUseBill;
import com.fc.v2.model.auto.TMgisUseBillSign;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 旧核准单走层裁判单测：任一人/联名两档、同人幂等、打回定死、
 * 退回销档另起趟、两本账对账、折新四阶。
 */
class LegacyUseBillJudgeTest {

    private static TMgisUseBill bill(int node, int mode, int need, int signCount, int status) {
        TMgisUseBill b = new TMgisUseBill();
        b.setId(1L);
        b.setBillNo("YZ-1");
        b.setNodeNo(node);
        b.setSignMode(mode);
        b.setNeedCount(need);
        b.setSignCount(signCount);
        b.setStatus(status);
        b.setDelFlag(0);
        return b;
    }

    private static TMgisUseBillSign sign(long id, int node, int batch, String signer, int active) {
        TMgisUseBillSign t = new TMgisUseBillSign();
        t.setId(id);
        t.setBillId(1L);
        t.setNodeNo(node);
        t.setBatchNo(batch);
        t.setAction(LegacyBillAction.APPROVE.name());
        t.setSigner(signer);
        t.setComment("同意");
        t.setActive(active);
        return t;
    }

    @Test
    void 任一人档一张签票即过层() {
        TMgisUseBill b = bill(0, LegacyUseBillJudge.MODE_OR, 1, 0, 0);
        LegacyUseBillJudge.ApproveVerdict v = LegacyUseBillJudge.decideApprove(b, new ArrayList<>(), "王科");
        assertTrue(v.isLayerPassed());
        assertFalse(v.isPassed());
        assertEquals(1, v.getNextNode());
        assertEquals(0, v.getNextStatus());
        assertEquals(1, v.getSignedCount());
    }

    @Test
    void 头层过后第二层再签齐即核讫() {
        TMgisUseBill b = bill(1, LegacyUseBillJudge.MODE_OR, 1, 0, 0);
        LegacyUseBillJudge.ApproveVerdict v = LegacyUseBillJudge.decideApprove(b, new ArrayList<>(), "省厅郑");
        assertTrue(v.isPassed());
        assertEquals(LegacyUseBillJudge.DONE_NODE, v.getNextNode());
        assertEquals(LegacyUseBillJudge.STATUS_PASS, v.getNextStatus());
    }

    @Test
    void 联名档落一人不放行停在本层() {
        TMgisUseBill b = bill(0, LegacyUseBillJudge.MODE_AND, 2, 0, 0);
        LegacyUseBillJudge.ApproveVerdict v = LegacyUseBillJudge.decideApprove(b, new ArrayList<>(), "王科");
        assertTrue(v.isCounted());
        assertFalse(v.isLayerPassed());
        assertEquals(0, v.getNextNode());
        assertEquals(1, v.getSignedCount());
        assertEquals(2, v.getRequired());
    }

    @Test
    void 联名档两个不同的人签齐才过() {
        TMgisUseBill b = bill(0, LegacyUseBillJudge.MODE_AND, 2, 1, 0);
        List<TMgisUseBillSign> tickets = new ArrayList<>();
        tickets.add(sign(1L, 0, 1, "王科", 1));
        LegacyUseBillJudge.ApproveVerdict v = LegacyUseBillJudge.decideApprove(b, tickets, "郑处");
        assertTrue(v.isLayerPassed());
        assertEquals(2, v.getSignedCount());
    }

    @Test
    void 同一个人签两回只认头一张票数不涨() {
        TMgisUseBill b = bill(0, LegacyUseBillJudge.MODE_AND, 2, 1, 0);
        List<TMgisUseBillSign> tickets = new ArrayList<>();
        tickets.add(sign(1L, 0, 1, "王科", 1));
        // 王科第二回来：幂等，回的头一张，不记数、不挪层
        LegacyUseBillJudge.ApproveVerdict v = LegacyUseBillJudge.decideApprove(b, tickets, "王科");
        assertTrue(v.isDuplicate());
        assertEquals(1L, v.getFirstTicket().getId());
        assertEquals(0, v.getNextNode());
    }

    @Test
    void 打回即定死一枚都加不进去() {
        TMgisUseBill b = bill(0, LegacyUseBillJudge.MODE_OR, 1, 0, LegacyUseBillJudge.STATUS_VETO);
        assertThrows(LegacyBillException.class,
                () -> LegacyUseBillJudge.decideApprove(b, new ArrayList<>(), "王科"));
    }

    @Test
    void 核讫即锁栏() {
        TMgisUseBill b = bill(2, LegacyUseBillJudge.MODE_OR, 1, 0, LegacyUseBillJudge.STATUS_PASS);
        assertThrows(LegacyBillException.class,
                () -> LegacyUseBillJudge.decideApprove(b, new ArrayList<>(), "王科"));
    }

    @Test
    void 退回后旧票销档新趟重签旧票不凑数() {
        // 头层有第 1 趟两张齐票（联名已过，件曾到第二层后被退回头层）
        TMgisUseBill b = bill(0, LegacyUseBillJudge.MODE_AND, 2, 0, 0);
        List<TMgisUseBillSign> tickets = new ArrayList<>();
        tickets.add(sign(1L, 0, 1, "王科", 0)); // 已销档
        tickets.add(sign(2L, 0, 1, "郑处", 0)); // 已销档
        // 再上来：旧票 active=0 不算数，这是第 2 趟，头一人只记一枚
        assertEquals(2, LegacyUseBillJudge.visitBatch(tickets, 0));
        LegacyUseBillJudge.ApproveVerdict v = LegacyUseBillJudge.decideApprove(b, tickets, "王科");
        assertTrue(v.isCounted());
        assertEquals(1, v.getSignedCount());
        assertEquals(2, v.getBatch());
    }

    @Test
    void 层上有算数票续同一趟两人同趟点齐() {
        TMgisUseBill b = bill(1, LegacyUseBillJudge.MODE_AND, 2, 1, 0);
        List<TMgisUseBillSign> tickets = new ArrayList<>();
        tickets.add(sign(1L, 1, 1, "王科", 1));
        assertEquals(1, LegacyUseBillJudge.visitBatch(tickets, 1));
    }

    @Test
    void 两本账签数对不上即顶回() {
        TMgisUseBill b = bill(0, LegacyUseBillJudge.MODE_AND, 2, 2, 0);
        List<TMgisUseBillSign> tickets = new ArrayList<>();
        tickets.add(sign(1L, 0, 1, "王科", 1)); // 票籍只有 1 人，单据却存 2
        assertThrows(LegacyBillException.class,
                () -> LegacyUseBillJudge.requireConsistent(b, tickets));
    }

    @Test
    void 零票籍的历年旧单放行回看() {
        TMgisUseBill b = bill(0, LegacyUseBillJudge.MODE_AND, 2, 1, 0);
        LegacyUseBillJudge.requireConsistent(b, new ArrayList<>()); // 不抛
    }

    @Test
    void 折新四阶口径() {
        assertEquals("CITY", LegacyUseBillJudge.newStageOf(
                bill(0, 0, 1, 0, 0), new ArrayList<>()));
        assertEquals("PROVINCE", LegacyUseBillJudge.newStageOf(
                bill(1, 0, 1, 0, 0), new ArrayList<>()));
        assertEquals("ISSUE", LegacyUseBillJudge.newStageOf(
                bill(2, 0, 1, 0, LegacyUseBillJudge.STATUS_PASS), new ArrayList<>()));
        // 第二层打回折回省厅阶
        assertEquals("PROVINCE", LegacyUseBillJudge.newStageOf(
                bill(1, 0, 1, 0, LegacyUseBillJudge.STATUS_VETO), new ArrayList<>()));
    }

    @Test
    void 经办人空着顶回() {
        TMgisUseBill b = bill(0, 0, 1, 0, 0);
        assertThrows(LegacyBillException.class,
                () -> LegacyUseBillJudge.decideApprove(b, new ArrayList<>(), "  "));
    }

    @Test
    void 空节点老单按头层接() {
        TMgisUseBill b = new TMgisUseBill();
        b.setId(1L);
        b.setStatus(0);
        assertEquals(0, LegacyUseBillJudge.currentNode(b));
        assertNull(b.getNodeNo());
    }
}
