package com.fc.v2.useorder;

import com.fc.v2.common.useorder.UseOrderAction;
import com.fc.v2.common.useorder.UseOrderFlow;
import com.fc.v2.common.useorder.UseOrderFlowException;
import com.fc.v2.common.useorder.UseOrderStage;
import com.fc.v2.model.auto.TMgisUseEntry;
import com.fc.v2.model.auto.TMgisUseOrder;
import com.fc.v2.model.custom.UseOrderSignRequest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 走阶章程单测：四阶、双签点齐、发回清账、否决定死、核准锁栏逐条对号。
 */
class UseOrderFlowTest {

    private static TMgisUseOrder orderAt(UseOrderStage stage, int round, int status) {
        TMgisUseOrder o = new TMgisUseOrder();
        o.setId(1L);
        o.setStage(stage.name());
        o.setRoundNo(round);
        o.setStatus(status);
        return o;
    }

    private static UseOrderSignRequest req(UseOrderAction action) {
        UseOrderSignRequest r = new UseOrderSignRequest();
        r.setOrderId(1L);
        r.setAction(action.name());
        return r;
    }

    private static TMgisUseEntry pen(UseOrderAction action, int round) {
        TMgisUseEntry e = new TMgisUseEntry();
        e.setStage(action.getStage().name());
        e.setAction(action.name());
        e.setRoundNo(round);
        return e;
    }

    // ---------- 落笔后挪阶 ----------

    @Test
    void 受理完停市局() {
        assertEquals(UseOrderStage.CITY, UseOrderFlow.stageAfter(UseOrderAction.ACCEPT, false));
    }

    @Test
    void 市局核验通过上省厅() {
        assertEquals(UseOrderStage.PROVINCE, UseOrderFlow.stageAfter(UseOrderAction.CITY_PASS, false));
    }

    @Test
    void 省厅只签一枚件还停在第三步() {
        assertEquals(UseOrderStage.PROVINCE, UseOrderFlow.stageAfter(UseOrderAction.SIGN_SECRECY, false));
        assertEquals(UseOrderStage.PROVINCE, UseOrderFlow.stageAfter(UseOrderAction.SIGN_PURPOSE, false));
        // 两岗都签了才准往窗口放成果那阶走
        assertEquals(UseOrderStage.ISSUE, UseOrderFlow.stageAfter(UseOrderAction.SIGN_SECRECY, true));
        assertEquals(UseOrderStage.ISSUE, UseOrderFlow.stageAfter(UseOrderAction.SIGN_PURPOSE, true));
    }

    @Test
    void 省厅发回退回市局() {
        assertEquals(UseOrderStage.CITY, UseOrderFlow.stageAfter(UseOrderAction.RETURN, false));
        assertTrue(UseOrderFlow.isReturn(UseOrderAction.RETURN));
        assertFalse(UseOrderFlow.isReturn(UseOrderAction.SIGN_SECRECY));
    }

    @Test
    void 否决停在原处定死放成果办结() {
        assertEquals(UseOrderStage.CITY, UseOrderFlow.stageAfter(UseOrderAction.CITY_REJECT, false));
        assertEquals(UseOrderStage.PROVINCE, UseOrderFlow.stageAfter(UseOrderAction.PROVINCE_REJECT, false));
        assertTrue(UseOrderAction.CITY_REJECT.isReject());
        assertTrue(UseOrderAction.PROVINCE_REJECT.isReject());
        assertEquals(UseOrderStage.DONE, UseOrderFlow.stageAfter(UseOrderAction.ISSUE, false));
    }

    // ---------- 这一笔要说的话 ----------

    @Test
    void 两签认定意见缺了落不了笔() {
        UseOrderFlowException e = assertThrows(UseOrderFlowException.class,
                () -> UseOrderFlow.packPayload(UseOrderAction.SIGN_SECRECY, req(UseOrderAction.SIGN_SECRECY)));
        assertTrue(e.getMessage().contains("认定意见"));
    }

    @Test
    void 否决与发回缘由缺了落不了笔() {
        assertThrows(UseOrderFlowException.class,
                () -> UseOrderFlow.packPayload(UseOrderAction.CITY_REJECT, req(UseOrderAction.CITY_REJECT)));
        assertThrows(UseOrderFlowException.class,
                () -> UseOrderFlow.packPayload(UseOrderAction.PROVINCE_REJECT, req(UseOrderAction.PROVINCE_REJECT)));
        UseOrderFlowException e = assertThrows(UseOrderFlowException.class,
                () -> UseOrderFlow.packPayload(UseOrderAction.RETURN, req(UseOrderAction.RETURN)));
        assertTrue(e.getMessage().contains("发回缘由"));
    }

    @Test
    void 通过与放成果附言可空() {
        assertEquals("{}", UseOrderFlow.packPayload(UseOrderAction.CITY_PASS, req(UseOrderAction.CITY_PASS)));
        assertEquals("{}", UseOrderFlow.packPayload(UseOrderAction.ISSUE, req(UseOrderAction.ISSUE)));
    }

    // ---------- 隔阶与定死 ----------

    @Test
    void 隔着阶落笔不生效() {
        TMgisUseOrder order = orderAt(UseOrderStage.CITY, 1, UseOrderFlow.STATUS_OPEN);
        UseOrderFlowException e = assertThrows(UseOrderFlowException.class,
                () -> UseOrderFlow.checkSign(order, UseOrderAction.SIGN_SECRECY));
        assertTrue(e.getMessage().contains("隔着阶"));
        // 当前阶的动作收
        UseOrderFlow.checkSign(order, UseOrderAction.CITY_PASS);
    }

    @Test
    void 已否决件一枚都加不进去() {
        TMgisUseOrder dead = orderAt(UseOrderStage.PROVINCE, 1, UseOrderFlow.STATUS_REJECTED);
        UseOrderFlowException e = assertThrows(UseOrderFlowException.class,
                () -> UseOrderFlow.checkWritable(dead, UseOrderAction.SIGN_PURPOSE));
        assertTrue(e.getMessage().contains("定死"));
    }

    @Test
    void 核准后只收放成果() {
        TMgisUseOrder approved = orderAt(UseOrderStage.ISSUE, 1, UseOrderFlow.STATUS_APPROVED);
        // 放成果收
        UseOrderFlow.checkWritable(approved, UseOrderAction.ISSUE);
        // 补签、发回、改栏一律顶回
        assertThrows(UseOrderFlowException.class,
                () -> UseOrderFlow.checkWritable(approved, UseOrderAction.SIGN_SECRECY));
        assertThrows(UseOrderFlowException.class,
                () -> UseOrderFlow.checkWritable(approved, UseOrderAction.RETURN));
    }

    @Test
    void 未核准停在省厅时点不出放成果() {
        TMgisUseOrder order = orderAt(UseOrderStage.PROVINCE, 1, UseOrderFlow.STATUS_OPEN);
        UseOrderFlowException e = assertThrows(UseOrderFlowException.class,
                () -> UseOrderFlow.checkSign(order, UseOrderAction.ISSUE));
        assertTrue(e.getMessage().contains("隔着阶"));
    }

    @Test
    void 办结终态不收笔受理笔不重落() {
        TMgisUseOrder done = orderAt(UseOrderStage.DONE, 1, UseOrderFlow.STATUS_DONE);
        assertThrows(UseOrderFlowException.class, () -> UseOrderFlow.checkWritable(done, UseOrderAction.ISSUE));
        TMgisUseOrder open = orderAt(UseOrderStage.CITY, 1, UseOrderFlow.STATUS_OPEN);
        assertThrows(UseOrderFlowException.class, () -> UseOrderFlow.checkWritable(open, UseOrderAction.ACCEPT));
    }

    // ---------- 链上走阶次序 ----------

    @Test
    void 顺走四阶次序合法() {
        assertTrue(UseOrderFlow.legalTransition(pen(UseOrderAction.ACCEPT, 1), pen(UseOrderAction.CITY_PASS, 1)));
        assertTrue(UseOrderFlow.legalTransition(pen(UseOrderAction.CITY_PASS, 1), pen(UseOrderAction.SIGN_SECRECY, 1)));
        // 省厅两岗先后都行
        assertTrue(UseOrderFlow.legalTransition(pen(UseOrderAction.SIGN_SECRECY, 1), pen(UseOrderAction.SIGN_PURPOSE, 1)));
        assertTrue(UseOrderFlow.legalTransition(pen(UseOrderAction.SIGN_PURPOSE, 1), pen(UseOrderAction.SIGN_SECRECY, 1)));
        // 一签之后可发回、可否决
        assertTrue(UseOrderFlow.legalTransition(pen(UseOrderAction.SIGN_SECRECY, 1), pen(UseOrderAction.RETURN, 1)));
        assertTrue(UseOrderFlow.legalTransition(pen(UseOrderAction.SIGN_PURPOSE, 1), pen(UseOrderAction.PROVINCE_REJECT, 1)));
        // 两签齐放成果
        assertTrue(UseOrderFlow.legalTransition(pen(UseOrderAction.SIGN_PURPOSE, 1), pen(UseOrderAction.ISSUE, 1)));
        // 跨轮：发回接新一轮核验
        assertTrue(UseOrderFlow.legalTransition(pen(UseOrderAction.RETURN, 1), pen(UseOrderAction.CITY_PASS, 2)));
    }

    @Test
    void 跳阶替签同岗重签都错乱() {
        // 受理直接跳省厅
        assertFalse(UseOrderFlow.legalTransition(pen(UseOrderAction.ACCEPT, 1), pen(UseOrderAction.SIGN_SECRECY, 1)));
        // 同岗两枚
        assertFalse(UseOrderFlow.legalTransition(pen(UseOrderAction.SIGN_SECRECY, 1), pen(UseOrderAction.SIGN_SECRECY, 1)));
        // 发回之后同轮再来
        assertFalse(UseOrderFlow.legalTransition(pen(UseOrderAction.RETURN, 1), pen(UseOrderAction.SIGN_SECRECY, 1)));
        // 没经发回的跨轮不认
        assertFalse(UseOrderFlow.legalTransition(pen(UseOrderAction.CITY_PASS, 1), pen(UseOrderAction.CITY_PASS, 2)));
    }

    // ---------- 页面口径：发回后废票退出页面 ----------

    @Test
    void 发回后市局省厅只显新一轮受理笔始终在() {
        List<TMgisUseEntry> chain = Arrays.asList(
                pen(UseOrderAction.ACCEPT, 1),
                pen(UseOrderAction.CITY_PASS, 1),
                pen(UseOrderAction.SIGN_SECRECY, 1),
                pen(UseOrderAction.RETURN, 1),
                pen(UseOrderAction.CITY_PASS, 2),
                pen(UseOrderAction.SIGN_SECRECY, 2),
                pen(UseOrderAction.SIGN_PURPOSE, 2));
        Map<String, List<TMgisUseEntry>> page = UseOrderFlow.pageOf(chain, 2);
        // 受理笔始终在
        assertEquals(1, page.get(UseOrderStage.WINDOW.name()).size());
        // 市局、省厅只显第 2 轮，废票与新票不并排
        assertEquals(1, page.get(UseOrderStage.CITY.name()).size());
        assertEquals(2, page.get(UseOrderStage.CITY.name()).get(0).getRoundNo());
        assertEquals(2, page.get(UseOrderStage.PROVINCE.name()).size());
        for (TMgisUseEntry e : page.get(UseOrderStage.PROVINCE.name())) {
            assertEquals(2, e.getRoundNo());
        }
    }

    @Test
    void 发回后新轮一笔未落旧票也退出页面() {
        List<TMgisUseEntry> chain = Arrays.asList(
                pen(UseOrderAction.ACCEPT, 1),
                pen(UseOrderAction.CITY_PASS, 1),
                pen(UseOrderAction.SIGN_SECRECY, 1),
                pen(UseOrderAction.RETURN, 1));
        // 件正停在第 2 轮市局阶，新一轮一笔没落：市局、省厅页面都空，受理笔仍在
        Map<String, List<TMgisUseEntry>> page = UseOrderFlow.pageOf(chain, 2);
        assertNull(page.get(UseOrderStage.CITY.name()));
        assertNull(page.get(UseOrderStage.PROVINCE.name()));
        assertEquals(1, page.get(UseOrderStage.WINDOW.name()).size());
    }

    @Test
    void 省厅已签数当场从链上数() {
        List<TMgisUseEntry> chain = Arrays.asList(
                pen(UseOrderAction.ACCEPT, 1),
                pen(UseOrderAction.CITY_PASS, 1),
                pen(UseOrderAction.SIGN_SECRECY, 1),
                pen(UseOrderAction.RETURN, 1),
                pen(UseOrderAction.CITY_PASS, 2),
                pen(UseOrderAction.SIGN_SECRECY, 2),
                pen(UseOrderAction.SIGN_PURPOSE, 2));
        assertEquals(1, UseOrderFlow.provinceSigned(chain, 1));
        assertEquals(2, UseOrderFlow.provinceSigned(chain, 2));
    }
}
