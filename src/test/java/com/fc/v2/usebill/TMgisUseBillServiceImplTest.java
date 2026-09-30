package com.fc.v2.usebill;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.common.usebill.LegacyBillAction;
import com.fc.v2.common.usebill.LegacyBillException;
import com.fc.v2.mapper.auto.TMgisUseBillMapper;
import com.fc.v2.mapper.auto.TMgisUseBillSignMapper;
import com.fc.v2.model.auto.TMgisUseBill;
import com.fc.v2.model.auto.TMgisUseBillSign;
import com.fc.v2.model.custom.LegacyBillView;
import com.fc.v2.service.impl.TMgisUseBillServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 旧核准单服务层单测：票往账上落、联名点齐、同人幂等、打回定死、
 * 退回清旧票新趟重签、按新四阶口径回看。
 */
class TMgisUseBillServiceImplTest {

    private TMgisUseBillMapper billMapper;
    private TMgisUseBillSignMapper signMapper;
    private TMgisUseBillServiceImpl service;

    private List<TMgisUseBill> bills;
    private List<TMgisUseBillSign> signs;
    private long idGen = 7000L;

    @BeforeEach
    void setUp() {
        billMapper = mock(TMgisUseBillMapper.class);
        signMapper = mock(TMgisUseBillSignMapper.class);
        service = new TMgisUseBillServiceImpl(billMapper, signMapper);
        bills = new ArrayList<>();
        signs = new ArrayList<>();

        when(billMapper.selectById(any())).thenAnswer(inv -> findBill(((Number) inv.getArgument(0)).longValue()));
        when(billMapper.selectForUpdate(any())).thenAnswer(inv -> findBill(((Number) inv.getArgument(0)).longValue()));
        when(signMapper.selectList(any())).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            qw.getSqlSegment();
            Long billId = ((Number) qw.getParamNameValuePairs().values().iterator().next()).longValue();
            List<TMgisUseBillSign> found = new ArrayList<>();
            for (TMgisUseBillSign s : signs) {
                if (s.getBillId().equals(billId)) {
                    found.add(s);
                }
            }
            found.sort((a, b) -> Long.compare(a.getId(), b.getId()));
            return found;
        });
        when(signMapper.insert(any())).thenAnswer(inv -> {
            TMgisUseBillSign s = inv.getArgument(0);
            s.setId(++idGen);
            signs.add(s);
            return 1;
        });
        when(signMapper.update(isNull(), any())).thenAnswer(inv -> {
            UpdateWrapper<TMgisUseBillSign> uw = inv.getArgument(1);
            Long id = ((Number) uw.getParamNameValuePairs().values().iterator().next()).longValue();
            for (TMgisUseBillSign s : signs) {
                if (s.getId().equals(id)) {
                    s.setActive(0);
                    return 1;
                }
            }
            return 0;
        });
        // 单据条件更新按 WHERE 匹配并真改内存
        when(billMapper.update(isNull(), any())).thenAnswer(inv -> {
            UpdateWrapper<TMgisUseBill> uw = inv.getArgument(1);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?i)(id|node_no|status)\\s*(=|IS)\\s*(#\\{ew\\.paramNameValuePairs\\.(\\w+)})?")
                    .matcher(uw.getSqlSegment());
            Long whereId = null;
            Integer whereNode = null;
            Integer whereStatus = null;
            boolean nodeNull = false;
            boolean statusNull = false;
            while (m.find()) {
                String col = m.group(1).toLowerCase();
                boolean is = "IS".equalsIgnoreCase(m.group(2));
                String key = m.group(4);
                Object v = key == null ? null : uw.getParamNameValuePairs().get(key);
                if ("id".equals(col)) {
                    whereId = ((Number) v).longValue();
                } else if ("node_no".equals(col)) {
                    if (is) {
                        nodeNull = true;
                    } else {
                        whereNode = ((Number) v).intValue();
                    }
                } else if ("status".equals(col)) {
                    if (is) {
                        statusNull = true;
                    } else {
                        whereStatus = ((Number) v).intValue();
                    }
                }
            }
            for (TMgisUseBill b : bills) {
                if (!b.getId().equals(whereId)) {
                    continue;
                }
                boolean nodeOk = nodeNull ? b.getNodeNo() == null
                        : whereNode == null || whereNode.equals(b.getNodeNo());
                boolean statusOk = statusNull ? b.getStatus() == null
                        : whereStatus == null || whereStatus.equals(b.getStatus());
                if (!nodeOk || !statusOk) {
                    continue;
                }
                java.util.regex.Matcher set = java.util.regex.Pattern
                        .compile("(?i)(node_no|status|sign_count)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)}")
                        .matcher(uw.getSqlSet());
                while (set.find()) {
                    Object v = uw.getParamNameValuePairs().get(set.group(2));
                    switch (set.group(1).toLowerCase()) {
                        case "node_no":
                            b.setNodeNo(((Number) v).intValue());
                            break;
                        case "status":
                            b.setStatus(((Number) v).intValue());
                            break;
                        default:
                            b.setSignCount(((Number) v).intValue());
                            break;
                    }
                }
                return 1;
            }
            return 0;
        });
    }

    private TMgisUseBill findBill(long id) {
        for (TMgisUseBill b : bills) {
            if (b.getId().equals(id)) {
                return b;
            }
        }
        return null;
    }

    private TMgisUseBill newBill(long id, int node, int mode, int need, int signCount, int status) {
        TMgisUseBill b = new TMgisUseBill();
        b.setId(id);
        b.setBillNo("YZ-" + id);
        b.setNodeNo(node);
        b.setSignMode(mode);
        b.setNeedCount(need);
        b.setSignCount(signCount);
        b.setStatus(status);
        b.setDelFlag(0);
        bills.add(b);
        return b;
    }

    private long activeCount(long billId) {
        return signs.stream().filter(s -> s.getBillId().equals(billId)
                && LegacyBillAction.APPROVE.name().equals(s.getAction()) && s.getActive() == 1).count();
    }

    @Test
    void 签批票往账上落哪位签过查得到() {
        TMgisUseBill b = newBill(1L, 0, 0, 1, 0, 0);
        TMgisUseBill r = service.approve(1L, "王科", "同意");
        assertEquals(1, r.getNodeNo().intValue());
        List<TMgisUseBillSign> tickets = service.listSigns(1L);
        assertEquals(1, tickets.size());
        assertEquals("王科", tickets.get(0).getSigner());
        assertEquals("同意", tickets.get(0).getComment());
        assertEquals(LegacyBillAction.APPROVE.name(), tickets.get(0).getAction());
        assertEquals(1, tickets.get(0).getBatchNo().intValue());
    }

    @Test
    void 两层签齐即核讫() {
        newBill(1L, 0, 0, 1, 0, 0);
        service.approve(1L, "王科", "过");
        TMgisUseBill r = service.approve(1L, "郑处", "过");
        assertEquals(2, r.getNodeNo().intValue());
        assertEquals(1, r.getStatus().intValue());
    }

    @Test
    void 联名档一人签不放行两人不同才核讫() {
        TMgisUseBill b = newBill(1L, 0, 1, 2, 0, 0);
        TMgisUseBill one = service.approve(1L, "王科", "同意");
        assertEquals(0, one.getNodeNo().intValue());
        assertEquals(1, one.getSignCount().intValue());
        // 同一人第二回：不记数、不立第二张票
        TMgisUseBill again = service.approve(1L, "王科", "又同意");
        assertEquals(1, again.getSignCount().intValue());
        assertEquals(0, again.getNodeNo().intValue());
        assertEquals(1, activeCount(1L));
        // 另一个人签齐
        TMgisUseBill pass = service.approve(1L, "郑处", "同意");
        assertEquals(1, pass.getNodeNo().intValue());
        assertEquals(0, pass.getSignCount().intValue(), "过层后下一层签数清零重攒");
    }

    @Test
    void 打回落笔即定死状态改了票也落了() {
        TMgisUseBill b = newBill(1L, 0, 0, 1, 0, 0);
        TMgisUseBill r = service.reject(1L, "王科", "用途不实");
        assertEquals(2, r.getStatus().intValue());
        assertEquals("用途不实", service.listSigns(1L).get(0).getComment());
        // 定死后签批、打回、退回都进不去
        assertThrows(LegacyBillException.class, () -> service.approve(1L, "郑处", "过"));
        assertThrows(LegacyBillException.class, () -> service.rollback(1L, "郑处", "退"));
    }

    @Test
    void 退回销旧票再上来新趟重签旧票不凑数() {
        TMgisUseBill b = newBill(1L, 0, 1, 2, 0, 0);
        service.approve(1L, "王科", "一");
        service.approve(1L, "郑处", "二"); // 头层联名齐，件到第二层 node=1
        assertEquals(1, b.getNodeNo().intValue());
        // 第二层退回头层
        TMgisUseBill back = service.rollback(1L, "省厅", "材料不齐");
        assertEquals(0, back.getNodeNo().intValue());
        assertEquals(0, back.getSignCount().intValue());
        // 头层旧票两章已销档
        assertEquals(0, activeCount(1L));
        // 票仍在链上（含退回票），只是 active=0
        long approveRows = signs.stream().filter(s -> LegacyBillAction.APPROVE.name().equals(s.getAction())).count();
        assertEquals(2, approveRows);
        assertTrue(signs.stream().anyMatch(s -> LegacyBillAction.ROLLBACK.name().equals(s.getAction())));

        // 再上来：新一趟，头一人只记一枚，旧票没凑数
        TMgisUseBill again = service.approve(1L, "王科", "重签");
        assertEquals(0, again.getNodeNo().intValue());
        assertEquals(1, again.getSignCount().intValue());
        TMgisUseBillSign newTicket = signs.stream()
                .filter(s -> LegacyBillAction.APPROVE.name().equals(s.getAction()) && s.getActive() == 1)
                .findFirst().orElse(null);
        assertNotNull(newTicket);
        assertEquals(2, newTicket.getBatchNo().intValue());
    }

    @Test
    void 退回在头层停在头层() {
        newBill(1L, 0, 0, 1, 0, 0);
        TMgisUseBill r = service.rollback(1L, "王科", "已在头层");
        assertEquals(0, r.getNodeNo().intValue());
    }

    @Test
    void 件不在账回null() {
        assertNull(service.approve(99L, "王科", "x"));
        assertNull(service.reject(99L, "王科", "x"));
        assertNull(service.rollback(99L, "王科", "x"));
    }

    @Test
    void 按新四阶口径回看旧单() {
        newBill(1L, 1, 1, 2, 0, 0);
        service.approve(1L, "王科", "一");
        LegacyBillView view = service.viewByNewRules(1L);
        assertEquals("PROVINCE", view.getNewStage());
        assertEquals("在办", view.getStatusLabel());
        assertEquals(1, view.getTickets().size());
    }

    @Test
    void 零票籍老单照样回看得到() {
        newBill(1L, 0, 0, 1, 0, 0);
        LegacyBillView view = service.viewByNewRules(1L);
        assertEquals("CITY", view.getNewStage());
        assertTrue(view.getTickets().isEmpty());
    }
}
