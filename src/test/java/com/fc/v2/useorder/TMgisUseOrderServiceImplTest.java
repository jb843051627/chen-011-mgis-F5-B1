package com.fc.v2.useorder;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.useorder.UseOrderAction;
import com.fc.v2.common.useorder.UseOrderChainJudge;
import com.fc.v2.common.useorder.UseOrderFlow;
import com.fc.v2.common.useorder.UseOrderFlowException;
import com.fc.v2.common.useorder.UseOrderStage;
import com.fc.v2.mapper.auto.TMgisUseClaimLineMapper;
import com.fc.v2.mapper.auto.TMgisUseClaimMapper;
import com.fc.v2.mapper.auto.TMgisUseEntryMapper;
import com.fc.v2.mapper.auto.TMgisUseOrderMapper;
import com.fc.v2.mapper.auto.TMgisUseSheetMapper;
import com.fc.v2.model.auto.TMgisUseClaim;
import com.fc.v2.model.auto.TMgisUseClaimLine;
import com.fc.v2.model.auto.TMgisUseEntry;
import com.fc.v2.model.auto.TMgisUseOrder;
import com.fc.v2.model.auto.TMgisUseSheet;
import com.fc.v2.model.custom.UseClaimView;
import com.fc.v2.model.custom.UseOrderPageView;
import com.fc.v2.model.custom.UseOrderSignRequest;
import com.fc.v2.model.custom.UseOrderSignResult;
import com.fc.v2.service.impl.TMgisUseOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 签署工序单测：受理、核验、两岗点齐、发回重办、否决定死、核准锁栏、
 * 凭单放成果、一键清单、顺拉倒拉。
 */
class TMgisUseOrderServiceImplTest {

    private TMgisUseOrderMapper orderMapper;
    private TMgisUseSheetMapper sheetMapper;
    private TMgisUseEntryMapper entryMapper;
    private TMgisUseClaimMapper claimMapper;
    private TMgisUseClaimLineMapper claimLineMapper;
    private TMgisUseOrderServiceImpl service;

    private List<TMgisUseOrder> orders;
    private Map<Long, List<TMgisUseEntry>> chains;
    private Map<Long, List<TMgisUseSheet>> sheets;
    private List<TMgisUseClaim> claims;
    private Map<Long, List<TMgisUseClaimLine>> claimLines;
    private long idGen = 1000L;

    @BeforeEach
    void setUp() {
        orderMapper = mock(TMgisUseOrderMapper.class);
        sheetMapper = mock(TMgisUseSheetMapper.class);
        entryMapper = mock(TMgisUseEntryMapper.class);
        claimMapper = mock(TMgisUseClaimMapper.class);
        claimLineMapper = mock(TMgisUseClaimLineMapper.class);
        service = new TMgisUseOrderServiceImpl();
        ReflectionTestUtils.setField(service, "mgisUseOrderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "mgisUseSheetMapper", sheetMapper);
        ReflectionTestUtils.setField(service, "mgisUseEntryMapper", entryMapper);
        ReflectionTestUtils.setField(service, "mgisUseClaimMapper", claimMapper);
        ReflectionTestUtils.setField(service, "mgisUseClaimLineMapper", claimLineMapper);

        orders = new ArrayList<TMgisUseOrder>();
        chains = new java.util.HashMap<Long, List<TMgisUseEntry>>();
        sheets = new java.util.HashMap<Long, List<TMgisUseSheet>>();
        claims = new ArrayList<TMgisUseClaim>();
        claimLines = new java.util.HashMap<Long, List<TMgisUseClaimLine>>();

        when(orderMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TMgisUseOrder o : orders) {
                if (o.getId().equals(id)) {
                    return o;
                }
            }
            return null;
        });
        // 行锁取件在假库里与按主键取同一份（真库里另加 FOR UPDATE 串行同件签署）
        when(orderMapper.selectForUpdate(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TMgisUseOrder o : orders) {
                if (o.getId().equals(id)) {
                    return o;
                }
            }
            return null;
        });
        when(orderMapper.insert(any())).thenAnswer(inv -> {
            orders.add(inv.getArgument(0));
            return 1;
        });
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        when(entryMapper.selectList(any())).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            qw.getSqlSegment();
            Object orderId = qw.getParamNameValuePairs().values().iterator().next();
            List<TMgisUseEntry> chain = chains.get(((Number) orderId).longValue());
            return chain == null ? new ArrayList<TMgisUseEntry>() : new ArrayList<TMgisUseEntry>(chain);
        });
        when(entryMapper.insert(any())).thenAnswer(inv -> {
            TMgisUseEntry e = inv.getArgument(0);
            e.setId(++idGen);
            chains.computeIfAbsent(e.getOrderId(), k -> new ArrayList<TMgisUseEntry>()).add(e);
            return 1;
        });

        when(sheetMapper.selectList(any())).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            qw.getSqlSegment();
            Object orderId = qw.getParamNameValuePairs().values().iterator().next();
            List<TMgisUseSheet> list = sheets.get(((Number) orderId).longValue());
            return list == null ? new ArrayList<TMgisUseSheet>() : new ArrayList<TMgisUseSheet>(list);
        });
        when(sheetMapper.insert(any())).thenAnswer(inv -> {
            TMgisUseSheet s = inv.getArgument(0);
            s.setId(++idGen);
            sheets.computeIfAbsent(s.getOrderId(), k -> new ArrayList<TMgisUseSheet>()).add(s);
            return 1;
        });

        when(claimMapper.selectOne(any())).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            qw.getSqlSegment();
            Object orderId = qw.getParamNameValuePairs().values().iterator().next();
            for (TMgisUseClaim c : claims) {
                if (c.getOrderId().equals(((Number) orderId).longValue())) {
                    return c;
                }
            }
            return null;
        });
        when(claimMapper.insert(any())).thenAnswer(inv -> {
            claims.add(inv.getArgument(0));
            return 1;
        });
        when(claimLineMapper.selectList(any())).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            qw.getSqlSegment();
            Object claimId = qw.getParamNameValuePairs().values().iterator().next();
            List<TMgisUseClaimLine> list = claimLines.get(((Number) claimId).longValue());
            return list == null ? new ArrayList<TMgisUseClaimLine>() : new ArrayList<TMgisUseClaimLine>(list);
        });
        when(claimLineMapper.insert(any())).thenAnswer(inv -> {
            TMgisUseClaimLine l = inv.getArgument(0);
            l.setId(++idGen);
            claimLines.computeIfAbsent(l.getClaimId(), k -> new ArrayList<TMgisUseClaimLine>()).add(l);
            return 1;
        });
    }

    // ---------- 帮手 ----------

    private static UseOrderSignRequest.Sheet sheet(String code, String level, String year) {
        UseOrderSignRequest.Sheet s = new UseOrderSignRequest.Sheet();
        s.setSheetCode(code);
        s.setSecretLevel(level);
        s.setMapYear(year);
        return s;
    }

    private static UseOrderSignRequest acceptReq() {
        UseOrderSignRequest r = new UseOrderSignRequest();
        r.setAction(UseOrderAction.ACCEPT.name());
        r.setApplicant("临川市规划院");
        r.setPurpose("城市总体规划修编用图");
        r.setSheets(Arrays.asList(sheet("H49G001002", "机密", "2021"), sheet("H49G001003", "秘密", "2024")));
        return r;
    }

    private static UseOrderSignRequest act(Long id, UseOrderAction action) {
        UseOrderSignRequest r = new UseOrderSignRequest();
        r.setOrderId(id);
        r.setAction(action.name());
        return r;
    }

    private List<TMgisUseEntry> chainOf(Long id) {
        return chains.getOrDefault(id, Collections.<TMgisUseEntry>emptyList());
    }

    /** 受理一件，返回件（停在市局阶） */
    private TMgisUseOrder acceptOne() {
        UseOrderSignResult r = service.sign(acceptReq(), "窗口周");
        assertEquals(UseOrderStage.CITY.name(), r.getOrder().getStage());
        return r.getOrder();
    }

    private void cityPass(Long id, String who) {
        UseOrderSignResult r = service.sign(act(id, UseOrderAction.CITY_PASS), who);
        assertEquals(UseOrderStage.PROVINCE.name(), r.getOrder().getStage());
    }

    // ---------- 受理 ----------

    @Test
    void 受理五样齐着才落账系统给单号() {
        TMgisUseOrder order = acceptOne();
        assertTrue(order.getOrderNo().startsWith("YM"));
        assertEquals(Integer.valueOf(2), order.getSheetCount());
        assertEquals(Integer.valueOf(0), order.getProvinceSignCount());
        assertEquals(UseOrderFlow.STATUS_OPEN, order.getStatus().intValue());
        // 图幅逐行落账
        List<TMgisUseSheet> rows = sheets.get(order.getId());
        assertEquals(2, rows.size());
        assertEquals("SECRET", rows.get(0).getSecretLevel());
        assertEquals(Integer.valueOf(2024), rows.get(1).getMapYear());
        // 受理笔认 64 个 0，链顺拉倒拉皆通
        List<TMgisUseEntry> chain = chainOf(order.getId());
        assertEquals(1, chain.size());
        assertEquals(UseOrderAction.ACCEPT.name(), chain.get(0).getAction());
        assertEquals("窗口周", chain.get(0).getPennedBy());
        assertEquals(UseOrderChainJudge.GENESIS, chain.get(0).getPrevHash());
        assertTrue(service.verifyChain(order.getId()).isOk());
    }

    @Test
    void 受理缺一项一笔不落() {
        UseOrderSignRequest bad = acceptReq();
        bad.setPurpose(" ");
        assertThrows(UseOrderFlowException.class, () -> service.sign(bad, "窗口周"));
        verify(orderMapper, never()).insert(any());
        verify(sheetMapper, never()).insert(any());
        verify(entryMapper, never()).insert(any());
    }

    // ---------- 走满四阶 ----------

    @Test
    void 两岗两人签齐即核准放成果清单全自核准件带() {
        TMgisUseOrder order = acceptOne();
        Long id = order.getId();
        cityPass(id, "市局吴");

        // 只签一枚，件还停在省厅
        UseOrderSignResult r = service.sign(opinion(id, UseOrderAction.SIGN_SECRECY, "脱密到位"), "省厅王");
        assertEquals(UseOrderStage.PROVINCE.name(), r.getOrder().getStage());
        assertEquals(Integer.valueOf(1), r.getOrder().getProvinceSignCount());
        assertNull(r.getOrder().getApproveNo());

        // 另一岗是另一个人，签齐即核准
        r = service.sign(opinion(id, UseOrderAction.SIGN_PURPOSE, "用途正当"), "省厅郑");
        assertEquals(UseOrderStage.ISSUE.name(), r.getOrder().getStage());
        assertEquals(UseOrderFlow.STATUS_APPROVED, r.getOrder().getStatus().intValue());
        assertEquals(Integer.valueOf(2), r.getOrder().getProvinceSignCount());
        assertNotNull(r.getOrder().getApproveNo());
        assertTrue(r.getOrder().getApproveNo().startsWith("YZ"));
        assertEquals("省厅郑", r.getOrder().getApprovedBy());
        assertNotNull(r.getOrder().getApprovedAt());

        // 一键生成领用清单：字段全自核准件带
        UseClaimView claim = service.claimList(id, "窗口周");
        assertFalse(claim.isDuplicated());
        TMgisUseClaim head = claim.getClaim();
        assertTrue(head.getClaimNo().startsWith("QD"));
        assertEquals(order.getOrderNo(), head.getOrderNo());
        assertEquals(r.getOrder().getApproveNo(), head.getApproveNo());
        assertEquals("临川市规划院", head.getApplicant());
        assertEquals("城市总体规划修编用图", head.getPurpose());
        assertEquals(2, claim.getLines().size());
        assertEquals("H49G001002", claim.getLines().get(0).getSheetCode());
        assertEquals("SECRET", claim.getLines().get(0).getSecretLevel());
        assertEquals(Integer.valueOf(2021), claim.getLines().get(0).getMapYear());
        assertEquals(2, head.getLineCount().intValue());

        // 重按只回头一遍
        UseClaimView again = service.claimList(id, "窗口周");
        assertTrue(again.isDuplicated());
        assertEquals(head.getId(), again.getClaim().getId());
        assertEquals(1, claims.size());

        // 放成果办结
        r = service.sign(act(id, UseOrderAction.ISSUE), "窗口周");
        assertEquals(UseOrderStage.DONE.name(), r.getOrder().getStage());
        assertEquals(UseOrderFlow.STATUS_DONE, r.getOrder().getStatus().intValue());
        assertEquals("窗口周", r.getOrder().getIssuedBy());

        // 签署链五笔（受理、核验、两签、放成果）；清单不进签署门、不进签署链
        assertEquals(5, chainOf(id).size());
        assertTrue(service.verifyChain(id).isOk());
    }

    @Test
    void 同一人把两岗都签了算替签顶回() {
        TMgisUseOrder order = acceptOne();
        Long id = order.getId();
        cityPass(id, "市局吴");
        service.sign(opinion(id, UseOrderAction.SIGN_SECRECY, "脱密到位"), "省厅王");
        // 同一支笔去签用途岗：替签不作数，一枚都不落
        UseOrderFlowException e = assertThrows(UseOrderFlowException.class,
                () -> service.sign(opinion(id, UseOrderAction.SIGN_PURPOSE, "用途正当"), "省厅王"));
        assertTrue(e.getMessage().contains("替签"));
        assertEquals(3, chainOf(id).size());
        assertEquals(UseOrderStage.PROVINCE.name(), order.getStage());
        assertEquals(Integer.valueOf(1), order.getProvinceSignCount());
    }

    @Test
    void 同岗同轮第二回只认头一笔() {
        TMgisUseOrder order = acceptOne();
        Long id = order.getId();
        cityPass(id, "市局吴");
        UseOrderSignResult first = service.sign(opinion(id, UseOrderAction.SIGN_SECRECY, "头一笔"), "省厅王");
        UseOrderSignResult second = service.sign(opinion(id, UseOrderAction.SIGN_SECRECY, "又一笔"), "省厅王");
        assertTrue(second.isDuplicated());
        assertEquals(first.getEntry().getId(), second.getEntry().getId());
        assertEquals(3, chainOf(id).size());
    }

    @Test
    void 两签意见空着落不了笔() {
        TMgisUseOrder order = acceptOne();
        Long id = order.getId();
        cityPass(id, "市局吴");
        assertThrows(UseOrderFlowException.class,
                () -> service.sign(act(id, UseOrderAction.SIGN_SECRECY), "省厅王"));
        assertEquals(2, chainOf(id).size());
    }

    // ---------- 隔阶、未核准 ----------

    @Test
    void 隔着阶落笔不生效() {
        TMgisUseOrder order = acceptOne();
        Long id = order.getId();
        // 还停在市局，直接去省厅签
        UseOrderFlowException e = assertThrows(UseOrderFlowException.class,
                () -> service.sign(opinion(id, UseOrderAction.SIGN_SECRECY, "抢跑"), "省厅王"));
        assertTrue(e.getMessage().contains("隔着阶"));
        assertEquals(1, chainOf(id).size());
    }

    @Test
    void 未核准点不出放成果也生不成清单() {
        TMgisUseOrder order = acceptOne();
        Long id = order.getId();
        cityPass(id, "市局吴");
        service.sign(opinion(id, UseOrderAction.SIGN_SECRECY, "一枚"), "省厅王");
        // 件还停在省厅，点放成果：隔阶
        assertThrows(UseOrderFlowException.class, () -> service.sign(act(id, UseOrderAction.ISSUE), "窗口周"));
        // 生清单：未核准
        UseOrderFlowException e = assertThrows(UseOrderFlowException.class,
                () -> service.claimList(id, "窗口周"));
        assertTrue(e.getMessage().contains("还没核准"));
        assertEquals(0, claims.size());
    }

    // ---------- 否决定死 ----------

    @Test
    void 市局写明不予核准则件定死() {
        TMgisUseOrder order = acceptOne();
        Long id = order.getId();
        UseOrderSignRequest reject = act(id, UseOrderAction.CITY_REJECT);
        reject.setReason("用途与允许的作业范围搭不上");
        UseOrderSignResult r = service.sign(reject, "市局吴");
        assertEquals(UseOrderFlow.STATUS_REJECTED, r.getOrder().getStatus().intValue());
        // 一枚都加不进去
        UseOrderFlowException e = assertThrows(UseOrderFlowException.class,
                () -> service.sign(act(id, UseOrderAction.CITY_PASS), "市局吴"));
        assertTrue(e.getMessage().contains("定死"));
        assertTrue(service.verifyChain(id).isOk());
    }

    @Test
    void 否决缘由空着落不了笔() {
        TMgisUseOrder order = acceptOne();
        Long id = order.getId();
        assertThrows(UseOrderFlowException.class,
                () -> service.sign(act(id, UseOrderAction.CITY_REJECT), "市局吴"));
    }

    @Test
    void 省厅写明不予核准也定死() {
        TMgisUseOrder order = acceptOne();
        Long id = order.getId();
        cityPass(id, "市局吴");
        service.sign(opinion(id, UseOrderAction.SIGN_SECRECY, "存疑"), "省厅王");
        UseOrderSignRequest reject = act(id, UseOrderAction.PROVINCE_REJECT);
        reject.setReason("这个用途不该给");
        UseOrderSignResult r = service.sign(reject, "省厅郑");
        assertEquals(UseOrderFlow.STATUS_REJECTED, r.getOrder().getStatus().intValue());
        // 定死后另一枚签加不进去
        assertThrows(UseOrderFlowException.class,
                () -> service.sign(opinion(id, UseOrderAction.SIGN_PURPOSE, "x"), "省厅郑"));
        assertTrue(service.verifyChain(id).isOk());
    }

    // ---------- 核准锁栏 ----------

    @Test
    void 核准后补签发回一律顶回只收放成果() {
        TMgisUseOrder order = acceptOne();
        Long id = order.getId();
        cityPass(id, "市局吴");
        service.sign(opinion(id, UseOrderAction.SIGN_SECRECY, "到位"), "省厅王");
        service.sign(opinion(id, UseOrderAction.SIGN_PURPOSE, "正当"), "省厅郑");
        assertThrows(UseOrderFlowException.class,
                () -> service.sign(act(id, UseOrderAction.RETURN), "省厅郑"));
        assertThrows(UseOrderFlowException.class,
                () -> service.sign(opinion(id, UseOrderAction.SIGN_SECRECY, "再签"), "省厅王"));
        // 放成果收
        UseOrderSignResult r = service.sign(act(id, UseOrderAction.ISSUE), "窗口周");
        assertEquals(UseOrderFlow.STATUS_DONE, r.getOrder().getStatus().intValue());
        // 办结后什么都不收
        assertThrows(UseOrderFlowException.class, () -> service.sign(act(id, UseOrderAction.ISSUE), "窗口周"));
    }

    // ---------- 发回重办 ----------

    @Test
    void 省厅发回前签一笔勾销从市局重攒废票新票不并排() {
        TMgisUseOrder order = acceptOne();
        Long id = order.getId();
        cityPass(id, "市局吴");
        service.sign(opinion(id, UseOrderAction.SIGN_SECRECY, "脱密说明单薄"), "省厅王");

        UseOrderSignRequest back = act(id, UseOrderAction.RETURN);
        back.setReason("脱密说明单薄，发回市局补齐");
        UseOrderSignResult r = service.sign(back, "省厅郑");
        assertEquals(UseOrderStage.CITY.name(), r.getOrder().getStage());
        assertEquals(Integer.valueOf(2), r.getOrder().getRoundNo());
        assertEquals(Integer.valueOf(0), r.getOrder().getProvinceSignCount());

        // 页面口径：市局、省厅只显第 2 轮（此刻省厅页空着），受理笔始终在
        UseOrderPageView view = service.pageView(id);
        for (UseOrderPageView.StagePage sp : view.getStages()) {
            for (TMgisUseEntry e : sp.getEntries()) {
                if (UseOrderStage.CITY.name().equals(sp.getStage())
                        || UseOrderStage.PROVINCE.name().equals(sp.getStage())) {
                    assertEquals(2, e.getRoundNo().intValue());
                }
            }
        }

        // 从市局重新攒：重办新笔指向上一轮旧笔
        r = service.sign(act(id, UseOrderAction.CITY_PASS), "市局吴");
        assertEquals(Integer.valueOf(2), r.getEntry().getRoundNo());
        assertNotNull(r.getEntry().getSupersedesEntryId());

        service.sign(opinion(id, UseOrderAction.SIGN_SECRECY, "补齐了"), "省厅王");
        service.sign(opinion(id, UseOrderAction.SIGN_PURPOSE, "用途正当"), "省厅郑");
        r = service.sign(act(id, UseOrderAction.ISSUE), "窗口周");
        assertEquals(UseOrderFlow.STATUS_DONE, r.getOrder().getStatus().intValue());

        // 链上两代笔都在（受理、核验1、密签1、发回、核验2、密签2、用途2、放 = 8）
        assertEquals(8, chainOf(id).size());
        assertTrue(service.verifyChain(id).isOk());
    }

    @Test
    void 发回缘由空着落不了笔() {
        TMgisUseOrder order = acceptOne();
        Long id = order.getId();
        cityPass(id, "市局吴");
        assertThrows(UseOrderFlowException.class, () -> service.sign(act(id, UseOrderAction.RETURN), "省厅郑"));
    }

    // ---------- 页面、链、清单回看 ----------

    @Test
    void 页面只开眼下阶且两处计数对得上() {
        TMgisUseOrder order = acceptOne();
        Long id = order.getId();
        cityPass(id, "市局吴");
        service.sign(opinion(id, UseOrderAction.SIGN_SECRECY, "一枚"), "省厅王");
        UseOrderPageView view = service.pageView(id);
        assertEquals(UseOrderStage.PROVINCE.name(), view.getCurrentStage());
        int open = 0;
        for (UseOrderPageView.StagePage sp : view.getStages()) {
            if (sp.isOpen()) {
                open++;
                assertEquals(UseOrderStage.PROVINCE.name(), sp.getStage());
            }
        }
        assertEquals(1, open);
        assertEquals(2, view.getSheets().size());
    }

    @Test
    void 事后改话这件申请即不合格() {
        TMgisUseOrder order = acceptOne();
        Long id = order.getId();
        cityPass(id, "市局吴");
        service.sign(opinion(id, UseOrderAction.SIGN_SECRECY, "到位"), "省厅王");
        service.sign(opinion(id, UseOrderAction.SIGN_PURPOSE, "正当"), "省厅郑");
        service.sign(act(id, UseOrderAction.ISSUE), "窗口周");
        assertTrue(service.verifyChain(id).isOk());
        chainOf(id).get(2).setPayload("{\"opinion\":\"改过的说法\"}");
        assertFalse(service.verifyChain(id).isOk());
    }

    @Test
    void 清单回看三处行数对得上() {
        TMgisUseOrder order = acceptOne();
        Long id = order.getId();
        cityPass(id, "市局吴");
        service.sign(opinion(id, UseOrderAction.SIGN_SECRECY, "到位"), "省厅王");
        service.sign(opinion(id, UseOrderAction.SIGN_PURPOSE, "正当"), "省厅郑");
        service.claimList(id, "窗口周");
        UseClaimView view = service.getClaim(id);
        assertEquals(2, view.getLines().size());
        assertEquals("YZ", view.getClaim().getApproveNo().substring(0, 2));
    }

    @Test
    void 件不在账上() {
        assertThrows(UseOrderFlowException.class, () -> service.sign(act(999L, UseOrderAction.CITY_PASS), "市局吴"));
        assertThrows(UseOrderFlowException.class, () -> service.pageView(999L));
        assertThrows(UseOrderFlowException.class, () -> service.verifyChain(999L));
        assertThrows(UseOrderFlowException.class, () -> service.claimList(999L, "窗口周"));
    }

    private static UseOrderSignRequest opinion(Long id, UseOrderAction action, String opinion) {
        UseOrderSignRequest r = act(id, action);
        r.setOpinion(opinion);
        return r;
    }
}
