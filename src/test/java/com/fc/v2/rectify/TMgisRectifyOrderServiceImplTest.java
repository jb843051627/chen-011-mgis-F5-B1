package com.fc.v2.rectify;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.rectify.RectifyAction;
import com.fc.v2.common.rectify.RectifyChainJudge;
import com.fc.v2.common.rectify.RectifyChainReport;
import com.fc.v2.common.rectify.RectifyFlowException;
import com.fc.v2.common.rectify.RectifyStation;
import com.fc.v2.mapper.auto.TMgisQualConclusionMapper;
import com.fc.v2.mapper.auto.TMgisRectifyEntryMapper;
import com.fc.v2.mapper.auto.TMgisRectifyItemMapper;
import com.fc.v2.mapper.auto.TMgisRectifyOrderMapper;
import com.fc.v2.model.auto.TMgisQualConclusion;
import com.fc.v2.model.auto.TMgisRectifyEntry;
import com.fc.v2.model.auto.TMgisRectifyItem;
import com.fc.v2.model.auto.TMgisRectifyOrder;
import com.fc.v2.model.custom.RectifyPageView;
import com.fc.v2.model.custom.RectifyUndertakeRequest;
import com.fc.v2.model.custom.RectifyUndertakeResult;
import com.fc.v2.service.impl.TMgisRectifyOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
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
 * 承办工序单测：一站站走、同点认头一笔、整改站顶回、退回重办、
 * 封存定案、零点归集、顺拉倒拉。
 */
class TMgisRectifyOrderServiceImplTest {

    private TMgisRectifyOrderMapper orderMapper;
    private TMgisRectifyEntryMapper entryMapper;
    private TMgisRectifyItemMapper itemMapper;
    private TMgisQualConclusionMapper conclusionMapper;
    private TMgisRectifyOrderServiceImpl service;

    /** 假库：单子、逐单落笔链、归集件 */
    private List<TMgisRectifyOrder> orders;
    private Map<Long, List<TMgisRectifyEntry>> chains;
    private List<TMgisRectifyItem> items;
    private long idGen = 1000L;

    @BeforeEach
    void setUp() {
        orderMapper = mock(TMgisRectifyOrderMapper.class);
        entryMapper = mock(TMgisRectifyEntryMapper.class);
        itemMapper = mock(TMgisRectifyItemMapper.class);
        conclusionMapper = mock(TMgisQualConclusionMapper.class);
        service = new TMgisRectifyOrderServiceImpl();
        ReflectionTestUtils.setField(service, "mgisRectifyOrderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "mgisRectifyEntryMapper", entryMapper);
        ReflectionTestUtils.setField(service, "mgisRectifyItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "mgisQualConclusionMapper", conclusionMapper);

        orders = new ArrayList<TMgisRectifyOrder>();
        chains = new HashMap<Long, List<TMgisRectifyEntry>>();
        items = new ArrayList<TMgisRectifyItem>();

        when(orderMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TMgisRectifyOrder o : orders) {
                if (o.getId().equals(id)) {
                    return o;
                }
            }
            return null;
        });
        // 行锁取件在假库里与按主键取同一份（真库里另加 FOR UPDATE 串行同单承办）
        when(orderMapper.selectForUpdate(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TMgisRectifyOrder o : orders) {
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
        // 条件更新照 WHERE 落到匹配的单上：旧站旧轮旧状态对不上即 0 行，
        // 真库怎么顶，假库就怎么顶（封存后、并发先挪的单都挪不动）
        when(orderMapper.update(isNull(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<?> uw = inv.getArgument(1);
            java.util.Map<String, Object> where = parseConds(uw.getSqlSegment(), uw.getParamNameValuePairs());
            java.util.Map<String, Object> sets = parseConds(uw.getSqlSet(), uw.getParamNameValuePairs());
            int n = 0;
            for (TMgisRectifyOrder o : orders) {
                if (matchesConds(o, where)) {
                    sets.forEach((col, v) -> setColumn(o, col, v));
                    n++;
                }
            }
            return n;
        });
        when(orderMapper.selectCount(any())).thenReturn(0);
        // 归集件数当场按单从归集件表数，供页面两处口径对账
        when(itemMapper.selectCount(any())).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            qw.getSqlSegment();
            Object orderId = qw.getParamNameValuePairs().values().iterator().next();
            int n = 0;
            for (TMgisRectifyItem it : items) {
                if (it.getOrderId().equals(((Number) orderId).longValue())) {
                    n++;
                }
            }
            return n;
        });

        when(entryMapper.selectList(any())).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            // 触发条件归置后再取参数（MyBatis-Plus 惰性归置）
            qw.getSqlSegment();
            Object orderId = qw.getParamNameValuePairs().values().iterator().next();
            List<TMgisRectifyEntry> chain = chains.get(((Number) orderId).longValue());
            return chain == null ? new ArrayList<TMgisRectifyEntry>() : new ArrayList<TMgisRectifyEntry>(chain);
        });
        when(entryMapper.insert(any())).thenAnswer(inv -> {
            TMgisRectifyEntry e = inv.getArgument(0);
            e.setId(++idGen);
            chains.computeIfAbsent(e.getOrderId(), k -> new ArrayList<TMgisRectifyEntry>()).add(e);
            return 1;
        });
        when(itemMapper.insert(any())).thenAnswer(inv -> {
            TMgisRectifyItem it = inv.getArgument(0);
            it.setId(++idGen);
            items.add(it);
            return 1;
        });
    }

    // ---------- 假库帮手 ----------

    private static Date day(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(s);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static TMgisQualConclusion concl(long id, String batchNo, String itemCode) {
        TMgisQualConclusion c = new TMgisQualConclusion();
        c.setId(id);
        c.setBatchNo(batchNo);
        c.setItemCode(itemCode);
        c.setTier(3);
        c.setIssuedAt(day("2026-09-25"));
        return c;
    }

    private static RectifyUndertakeRequest req(Long orderId, RectifyAction action) {
        RectifyUndertakeRequest r = new RectifyUndertakeRequest();
        r.setOrderId(orderId);
        r.setAction(action.name());
        return r;
    }

    private static RectifyUndertakeRequest notifyReq(Long orderId) {
        RectifyUndertakeRequest r = req(orderId, RectifyAction.NOTIFY);
        r.setMaps("图幅A、图幅B");
        r.setSampling("按批抽百分之十");
        r.setEvidence("复测报告原件");
        return r;
    }

    private static RectifyUndertakeRequest rectifyReq(Long orderId, String note) {
        RectifyUndertakeRequest r = req(orderId, RectifyAction.RECTIFY);
        r.setNote(note);
        return r;
    }

    private static RectifyUndertakeRequest retestReq(Long orderId, RectifyAction who, String opinion) {
        RectifyUndertakeRequest r = req(orderId, who);
        r.setOpinion(opinion);
        return r;
    }

    private List<TMgisRectifyEntry> chainOf(Long orderId) {
        return chains.getOrDefault(orderId, Collections.emptyList());
    }

    /** 零点归集立一张单（批 B1 一件检出），返回单子 */
    private TMgisRectifyOrder raiseOne() {
        when(conclusionMapper.selectList(any())).thenReturn(
                new ArrayList<TMgisQualConclusion>(Collections.singletonList(concl(1L, "B1", "图幅A"))));
        List<TMgisRectifyOrder> raised = service.raiseDailyOrders(day("2026-09-25"));
        assertEquals(1, raised.size());
        return raised.get(0);
    }

    // ---------- 零点归集立单 ----------

    @Test
    void 零点归集按批立单() {
        when(conclusionMapper.selectList(any())).thenReturn(new ArrayList<TMgisQualConclusion>(
                Arrays.asList(concl(1L, "B1", "图幅A"), concl(2L, "B1", "图幅B"), concl(3L, "B2", "图幅C"))));
        List<TMgisRectifyOrder> raised = service.raiseDailyOrders(day("2026-09-25"));
        // 两批各一张，一批三件里的两件归一堆
        assertEquals(2, raised.size());
        TMgisRectifyOrder b1 = raised.get(0);
        assertEquals("B1", b1.getBatchNo());
        assertEquals(Integer.valueOf(2), b1.getItemCount());
        assertEquals(RectifyStation.NOTIFY.name(), b1.getStation());
        assertEquals(Integer.valueOf(1), b1.getRoundNo());
        assertEquals(Integer.valueOf(0), b1.getStatus());
        assertEquals(Integer.valueOf(1), b1.getInFlight());
        assertTrue(b1.getOrderNo().startsWith("ZG"));
        // 归集件挂进单子
        assertEquals(3, items.size());
        // 挂号那一笔：系统落笔，认 64 个 0 的前哈希
        List<TMgisRectifyEntry> chain = chainOf(b1.getId());
        assertEquals(1, chain.size());
        TMgisRectifyEntry reg = chain.get(0);
        assertEquals(RectifyAction.REGISTER.name(), reg.getAction());
        assertEquals("系统归集", reg.getPennedBy());
        assertEquals(RectifyChainJudge.GENESIS, reg.getPrevHash());
        assertEquals(RectifyChainJudge.hashOf(reg), reg.getHash());
        // 单笔链顺拉倒拉皆通
        assertTrue(service.verifyChain(b1.getId()).isOk());
    }

    @Test
    void 同一批还有单子在途第二张立不出来() {
        when(orderMapper.selectCount(any())).thenReturn(1);
        when(conclusionMapper.selectList(any())).thenReturn(
                new ArrayList<TMgisQualConclusion>(Collections.singletonList(concl(1L, "B1", "图幅A"))));
        List<TMgisRectifyOrder> raised = service.raiseDailyOrders(day("2026-09-25"));
        assertTrue(raised.isEmpty());
        verify(orderMapper, never()).insert(any());
    }

    // ---------- 走满六站 ----------

    @Test
    void 一站站走满六站_封存后顺拉倒拉皆通() {
        TMgisRectifyOrder order = raiseOne();
        Long id = order.getId();

        // 告知：三样说定，快照进单
        RectifyUndertakeResult r = service.undertake(notifyReq(id), "张三");
        assertFalse(r.isDuplicated());
        assertEquals(RectifyStation.RECTIFY.name(), r.getOrder().getStation());
        assertEquals("图幅A、图幅B", r.getOrder().getNotifyMaps());
        assertEquals("按批抽百分之十", r.getOrder().getNotifySampling());
        assertEquals("复测报告原件", r.getOrder().getNotifyEvidence());
        assertEquals("张三", r.getOrder().getNotifyBy());
        assertNotNull(r.getOrder().getNotifyAt());
        // 链上接得住挂号那一笔
        assertEquals(2, chainOf(id).size());
        assertEquals(chainOf(id).get(0).getHash(), chainOf(id).get(1).getPrevHash());

        // 整改
        r = service.undertake(rectifyReq(id, "两幅图已重测整改"), "李四");
        assertEquals(RectifyStation.RETEST.name(), r.getOrder().getStation());

        // 复测：只回来检验机构一名，这一站不算走通
        r = service.undertake(retestReq(id, RectifyAction.RETEST_AGENCY, "复测合格"), "王五");
        assertEquals(RectifyStation.RETEST.name(), r.getOrder().getStation());
        // 属地市局那名也进了单，才往销号挪
        r = service.undertake(retestReq(id, RectifyAction.RETEST_CITY, "同意复测结论"), "赵六");
        assertEquals(RectifyStation.CLOSE.name(), r.getOrder().getStation());

        // 销号、封存
        r = service.undertake(req(id, RectifyAction.CLOSE), "张三");
        assertEquals(RectifyStation.ARCHIVE.name(), r.getOrder().getStation());
        r = service.undertake(req(id, RectifyAction.ARCHIVE), "张三");
        assertEquals(Integer.valueOf(1), r.getOrder().getStatus());
        assertNull(r.getOrder().getInFlight());

        // 封了存的单子不再挂新条目
        RectifyFlowException e = assertThrows(RectifyFlowException.class,
                () -> service.undertake(req(id, RectifyAction.ARCHIVE), "张三"));
        assertTrue(e.getMessage().contains("封存"));

        // 整链七笔，顺拉倒拉皆通
        assertEquals(7, chainOf(id).size());
        RectifyChainReport report = service.verifyChain(id);
        assertTrue(report.isOk());
        assertEquals(7, report.getEntryCount());
    }

    @Test
    void 同一点第二回来的只认头一笔() {
        TMgisRectifyOrder order = raiseOne();
        Long id = order.getId();
        RectifyUndertakeResult first = service.undertake(notifyReq(id), "张三");
        // 单子已挪到整改，告知那一笔重投：不再落笔，头一笔原样回给它
        RectifyUndertakeResult second = service.undertake(notifyReq(id), "张三");
        assertTrue(second.isDuplicated());
        assertEquals(first.getEntry().getId(), second.getEntry().getId());
        assertEquals(first.getEntry().getPennedAt(), second.getEntry().getPennedAt());
        assertEquals(2, chainOf(id).size());

        // 复测站上同一名进两回，一样只认头一笔
        service.undertake(rectifyReq(id, "已整改"), "李四");
        RectifyUndertakeResult agency = service.undertake(retestReq(id, RectifyAction.RETEST_AGENCY, "合格"), "王五");
        RectifyUndertakeResult agencyAgain = service.undertake(retestReq(id, RectifyAction.RETEST_AGENCY, "合格"), "王五");
        assertTrue(agencyAgain.isDuplicated());
        assertEquals(agency.getEntry().getId(), agencyAgain.getEntry().getId());
        assertEquals(4, chainOf(id).size());
    }

    @Test
    void 隔着站去点后面那一站不生效() {
        TMgisRectifyOrder order = raiseOne();
        Long id = order.getId();
        // 单子停在告知，直接点整改
        RectifyFlowException e = assertThrows(RectifyFlowException.class,
                () -> service.undertake(rectifyReq(id, "抢跑"), "李四"));
        assertTrue(e.getMessage().contains("隔着站"));
        assertEquals(1, chainOf(id).size());
        assertEquals(RectifyStation.NOTIFY.name(), order.getStation());
    }

    @Test
    void 整改那一站想改抽样口径当场顶回() {
        TMgisRectifyOrder order = raiseOne();
        Long id = order.getId();
        service.undertake(notifyReq(id), "张三");
        RectifyUndertakeRequest sneaky = rectifyReq(id, "已整改");
        sneaky.setSampling("改成全数检查");
        RectifyFlowException e = assertThrows(RectifyFlowException.class,
                () -> service.undertake(sneaky, "李四"));
        assertTrue(e.getMessage().contains("当场顶回"));
        // 一笔都没落，单子还停在整改，告知快照原样
        assertEquals(2, chainOf(id).size());
        assertEquals(RectifyStation.RECTIFY.name(), order.getStation());
        assertEquals("按批抽百分之十", order.getNotifySampling());
    }

    @Test
    void 告知三样缺一样落不了笔() {
        TMgisRectifyOrder order = raiseOne();
        Long id = order.getId();
        RectifyUndertakeRequest r = notifyReq(id);
        r.setEvidence(null);
        assertThrows(RectifyFlowException.class, () -> service.undertake(r, "张三"));
        assertEquals(1, chainOf(id).size());
        assertNull(order.getNotifyEvidence());
    }

    @Test
    void 经办时辰系统在落笔那一下写死() {
        TMgisRectifyOrder order = raiseOne();
        Date before = new Date(System.currentTimeMillis() / 1000L * 1000L);
        RectifyUndertakeResult r = service.undertake(notifyReq(order.getId()), "李四");
        Date after = new Date(System.currentTimeMillis() / 1000L * 1000L + 1000);
        // 经办是落笔那一下认的人，时辰落在落笔当口、以秒计
        assertEquals("李四", r.getEntry().getPennedBy());
        assertFalse(r.getEntry().getPennedAt().before(before));
        assertFalse(r.getEntry().getPennedAt().after(after));
        assertEquals(0, r.getEntry().getPennedAt().getTime() % 1000L);
    }

    // ---------- 退回重办 ----------

    @Test
    void 复测看出合不上把整改退回重办() {
        TMgisRectifyOrder order = raiseOne();
        Long id = order.getId();
        service.undertake(notifyReq(id), "张三");
        RectifyUndertakeResult rectifyOld = service.undertake(rectifyReq(id, "已整改"), "李四");
        service.undertake(retestReq(id, RectifyAction.RETEST_AGENCY, "合格"), "王五");

        // 复测上看出抽样幅数与告知合不上：退回一站，整改重办
        RectifyUndertakeRequest back = req(id, RectifyAction.SEND_BACK);
        back.setReason("实抽幅数与告知口径合不上");
        RectifyUndertakeResult r = service.undertake(back, "赵六");
        assertEquals(RectifyStation.RECTIFY.name(), r.getOrder().getStation());
        assertEquals(Integer.valueOf(2), r.getOrder().getRoundNo());

        // 整改重办：新一笔顶替上一轮那一笔
        r = service.undertake(rectifyReq(id, "按告知口径重新整改"), "李四");
        assertEquals(Integer.valueOf(2), r.getEntry().getRoundNo());
        assertEquals(rectifyOld.getEntry().getId(), r.getEntry().getSupersedesEntryId());

        // 页面口径：整改站只显重办后的那一份，原先写的退出页面
        RectifyPageView view = service.pageView(id);
        RectifyPageView.StationPage rectifyPage = null;
        RectifyPageView.StationPage retestPage = null;
        for (RectifyPageView.StationPage sp : view.getStations()) {
            if (RectifyStation.RECTIFY.name().equals(sp.getStation())) {
                rectifyPage = sp;
            }
            if (RectifyStation.RETEST.name().equals(sp.getStation())) {
                retestPage = sp;
            }
        }
        assertNotNull(rectifyPage);
        assertEquals(1, rectifyPage.getEntries().size());
        assertTrue(rectifyPage.getEntries().get(0).getPayload().contains("重新整改"));
        // 复测站还停在第一轮：退回那一笔在页面上，缘由查得到
        assertNotNull(retestPage);
        assertTrue(retestPage.getEntries().get(retestPage.getEntries().size() - 1).getPayload()
                .contains("合不上"));

        // 重办后走满剩下的站，链上两代人的笔都还在
        service.undertake(retestReq(id, RectifyAction.RETEST_AGENCY, "合格"), "王五");
        service.undertake(retestReq(id, RectifyAction.RETEST_CITY, "同意"), "孙七");
        service.undertake(req(id, RectifyAction.CLOSE), "张三");
        service.undertake(req(id, RectifyAction.ARCHIVE), "张三");
        int rectifyPens = 0;
        for (TMgisRectifyEntry e2 : chainOf(id)) {
            if (RectifyAction.RECTIFY.name().equals(e2.getAction())) {
                rectifyPens++;
            }
        }
        assertEquals(2, rectifyPens);
        assertEquals(10, chainOf(id).size());
        assertTrue(service.verifyChain(id).isOk());
    }

    // ---------- 页面与链 ----------

    @Test
    void 页面只开眼下那一站的口子() {
        TMgisRectifyOrder order = raiseOne();
        Long id = order.getId();
        service.undertake(notifyReq(id), "张三");
        RectifyPageView view = service.pageView(id);
        assertEquals(RectifyStation.RECTIFY.name(), view.getCurrentStation());
        assertFalse(view.isSealed());
        int open = 0;
        for (RectifyPageView.StationPage sp : view.getStations()) {
            if (sp.isOpen()) {
                open++;
                assertEquals(RectifyStation.RECTIFY.name(), sp.getStation());
            }
        }
        assertEquals(1, open);
    }

    @Test
    void 事后改话这批整改即不合格() {
        TMgisRectifyOrder order = raiseOne();
        Long id = order.getId();
        service.undertake(notifyReq(id), "张三");
        service.undertake(rectifyReq(id, "已整改"), "李四");
        service.undertake(retestReq(id, RectifyAction.RETEST_AGENCY, "合格"), "王五");
        service.undertake(retestReq(id, RectifyAction.RETEST_CITY, "同意"), "赵六");
        service.undertake(req(id, RectifyAction.CLOSE), "张三");
        service.undertake(req(id, RectifyAction.ARCHIVE), "张三");
        assertTrue(service.verifyChain(id).isOk());
        // 有人事后把整改那一笔的话改了
        chainOf(id).get(2).setPayload("{\"note\":\"改过的说法\"}");
        RectifyChainReport report = service.verifyChain(id);
        assertFalse(report.isOk());
        assertFalse(report.isForwardOk());
    }

    @Test
    void 单子不在账上() {
        assertThrows(RectifyFlowException.class, () -> service.undertake(notifyReq(999L), "张三"));
        assertThrows(RectifyFlowException.class, () -> service.pageView(999L));
        assertThrows(RectifyFlowException.class, () -> service.verifyChain(999L));
    }

    @Test
    void 页面点名复测还欠哪一名换岗接班不必先试一笔() {
        TMgisRectifyOrder order = raiseOne();
        Long id = order.getId();
        service.undertake(notifyReq(id), "张三");
        service.undertake(rectifyReq(id, "已整改"), "李四");

        // 两名都没进：复测站欠两名
        RectifyPageView v0 = service.pageView(id);
        assertEquals(java.util.Arrays.asList("RETEST_AGENCY", "RETEST_CITY"), awaitingOf(v0, "RETEST"));

        service.undertake(retestReq(id, RectifyAction.RETEST_AGENCY, "合格"), "王五");
        // 只回检验机构一名：还欠属地市局
        RectifyPageView v1 = service.pageView(id);
        assertEquals(java.util.Collections.singletonList("RETEST_CITY"), awaitingOf(v1, "RETEST"));

        service.undertake(retestReq(id, RectifyAction.RETEST_CITY, "同意"), "赵六");
        // 两名齐、件挪到销号：复测站不再开口子、不欠名
        RectifyPageView v2 = service.pageView(id);
        assertTrue(awaitingOf(v2, "RETEST").isEmpty());
    }

    private static List<String> awaitingOf(RectifyPageView view, String station) {
        for (RectifyPageView.StationPage sp : view.getStations()) {
            if (station.equals(sp.getStation())) {
                return sp.getAwaitingActions();
            }
        }
        throw new AssertionError("没有这一站：" + station);
    }

    @Test
    void 归集件数汇总与拆项对不上页面摆不出来() {
        TMgisRectifyOrder order = raiseOne();
        // 库里有人改了单头件数：单头 9 件，归集件表只 1 件
        order.setItemCount(9);
        RectifyFlowException e = assertThrows(RectifyFlowException.class,
                () -> service.pageView(order.getId()));
        assertTrue(e.getMessage().contains("两处口径对不上"));
    }

    // ---------- 条件更新假库帮手 ----------

    private static final java.util.regex.Pattern COND =
            java.util.regex.Pattern.compile("(\\w+)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}");

    /** 把 WHERE 段或 SET 段归置成 列 → 值 */
    private static java.util.Map<String, Object> parseConds(String sql, java.util.Map<String, Object> params) {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<String, Object>();
        if (sql == null) {
            return out;
        }
        java.util.regex.Matcher m = COND.matcher(sql);
        while (m.find()) {
            out.put(m.group(1), params.get(m.group(2)));
        }
        return out;
    }

    private static boolean matchesConds(TMgisRectifyOrder o, java.util.Map<String, Object> conds) {
        for (java.util.Map.Entry<String, Object> e : conds.entrySet()) {
            Object got = columnOf(o, e.getKey());
            Object want = e.getValue();
            if (want == null ? got != null : !want.equals(got)) {
                return false;
            }
        }
        return true;
    }

    private static Object columnOf(Object row, String column) {
        try {
            StringBuilder name = new StringBuilder("get");
            for (String part : column.split("_")) {
                name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            return row.getClass().getMethod(name.toString()).invoke(row);
        } catch (Exception ex) {
            throw new IllegalStateException("取列失败：" + column, ex);
        }
    }

    private static void setColumn(Object row, String column, Object value) {
        try {
            StringBuilder name = new StringBuilder("set");
            for (String part : column.split("_")) {
                name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            for (java.lang.reflect.Method mth : row.getClass().getMethods()) {
                if (mth.getName().equals(name.toString()) && mth.getParameterTypes().length == 1
                        && (value == null || mth.getParameterTypes()[0].isInstance(value))) {
                    mth.invoke(row, value);
                    return;
                }
            }
            throw new IllegalStateException("没有写入法子：" + column);
        } catch (Exception ex) {
            throw new IllegalStateException("写列失败：" + column, ex);
        }
    }
}
