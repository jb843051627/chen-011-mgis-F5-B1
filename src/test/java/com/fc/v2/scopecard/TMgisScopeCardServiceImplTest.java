package com.fc.v2.scopecard;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.common.scopecard.ScopeCardAcceptException;
import com.fc.v2.common.scopecard.ScopeCardProcedure;
import com.fc.v2.common.scopecard.ScopeCardRejectReason;
import com.fc.v2.mapper.auto.TMgisScopeCardEntryMapper;
import com.fc.v2.mapper.auto.TMgisScopeCardItemMapper;
import com.fc.v2.mapper.auto.TMgisScopeCardMapper;
import com.fc.v2.mapper.auto.TMgisScopeCategoryMapper;
import com.fc.v2.mapper.auto.TMgisUnitDocMapper;
import com.fc.v2.model.auto.TMgisScopeCard;
import com.fc.v2.model.auto.TMgisScopeCardEntry;
import com.fc.v2.model.auto.TMgisScopeCardItem;
import com.fc.v2.model.auto.TMgisScopeCategory;
import com.fc.v2.model.auto.TMgisUnitDoc;
import com.fc.v2.model.custom.ScopeCardRegisterRequest;
import com.fc.v2.model.custom.ScopeCardRegisterResult;
import com.fc.v2.model.custom.ScopeCardTallyView;
import com.fc.v2.model.custom.ScopeCardView;
import com.fc.v2.service.impl.TMgisScopeCardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 登记门工序单测（假库）：立卡系统给号、同单号并一条、同单位同范围段
 * 第二张顶回、注销档案不收新卡也不许老卡添东西、走站、类别增减重折、
 * 创建人与经手人各记各的、属地只见本市、两口径看账卡数一致。
 */
class TMgisScopeCardServiceImplTest {

    private TMgisScopeCardMapper cardMapper;
    private TMgisUnitDocMapper unitDocMapper;
    private TMgisScopeCategoryMapper categoryMapper;
    private TMgisScopeCardItemMapper itemMapper;
    private TMgisScopeCardEntryMapper entryMapper;
    private TMgisScopeCardServiceImpl service;

    /** 假库：卡、档案、目录、在挂类别、经手人链 */
    private List<TMgisScopeCard> cards;
    private List<TMgisUnitDoc> docs;
    private List<TMgisScopeCategory> categories;
    private List<TMgisScopeCardItem> items;
    private List<TMgisScopeCardEntry> entries;
    private long idGen = 9000L;
    /** 并发模拟：头几回 selectOne 强制查不到（另一线程的提交还没看见） */
    private final AtomicInteger blindSelectOne = new AtomicInteger(0);

    // ---------- 假库 ----------

    private static final Pattern EQ = Pattern.compile("(\\w+)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}");
    private static final Pattern IN = Pattern.compile("(\\w+)\\s+IN\\s*\\(([^)]*)\\)");
    private static final Pattern PARAM_REF = Pattern.compile("#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}");

    private static Object col(Object row, String column) {
        try {
            StringBuilder name = new StringBuilder("get");
            for (String part : column.split("_")) {
                name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            return row.getClass().getMethod(name.toString()).invoke(row);
        } catch (Exception e) {
            throw new IllegalStateException("取列失败：" + column, e);
        }
    }

    private static void setCol(Object row, String column, Object value) {
        try {
            StringBuilder name = new StringBuilder("set");
            for (String part : column.split("_")) {
                name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            for (Method m : row.getClass().getMethods()) {
                if (m.getName().equals(name.toString()) && m.getParameterTypes().length == 1
                        && (value == null || m.getParameterTypes()[0].isInstance(value))) {
                    m.invoke(row, value);
                    return;
                }
            }
            throw new IllegalStateException("没有写入法子：" + column);
        } catch (Exception e) {
            throw new IllegalStateException("写列失败：" + column, e);
        }
    }

    private static boolean matches(Object row, String sqlSegment, Map<String, Object> params) {
        String sql = sqlSegment == null ? "" : sqlSegment;
        Matcher eq = EQ.matcher(sql);
        while (eq.find()) {
            if (!Objects.equals(params.get(eq.group(2)), col(row, eq.group(1)))) {
                return false;
            }
        }
        Matcher in = IN.matcher(sql);
        while (in.find()) {
            String column = in.group(1);
            List<Object> vals = new ArrayList<Object>();
            Matcher refs = PARAM_REF.matcher(in.group(2));
            while (refs.find()) {
                Object v = params.get(refs.group(1));
                if (v instanceof Collection) {
                    vals.addAll((Collection<?>) v);
                } else {
                    vals.add(v);
                }
            }
            if (!vals.contains(col(row, column))) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Object> parseSets(String sqlSet, Map<String, Object> params) {
        Map<String, Object> sets = new java.util.LinkedHashMap<String, Object>();
        Matcher m = EQ.matcher(sqlSet == null ? "" : sqlSet);
        while (m.find()) {
            sets.put(m.group(1), params.get(m.group(2)));
        }
        return sets;
    }

    @BeforeEach
    void setUp() {
        cardMapper = mock(TMgisScopeCardMapper.class);
        unitDocMapper = mock(TMgisUnitDocMapper.class);
        categoryMapper = mock(TMgisScopeCategoryMapper.class);
        itemMapper = mock(TMgisScopeCardItemMapper.class);
        entryMapper = mock(TMgisScopeCardEntryMapper.class);
        service = new TMgisScopeCardServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", cardMapper);
        ReflectionTestUtils.setField(service, "mgisUnitDocMapper", unitDocMapper);
        ReflectionTestUtils.setField(service, "mgisScopeCategoryMapper", categoryMapper);
        ReflectionTestUtils.setField(service, "mgisScopeCardItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "mgisScopeCardEntryMapper", entryMapper);

        cards = new ArrayList<TMgisScopeCard>();
        docs = new ArrayList<TMgisUnitDoc>();
        categories = new ArrayList<TMgisScopeCategory>();
        items = new ArrayList<TMgisScopeCardItem>();
        entries = new ArrayList<TMgisScopeCardEntry>();
        blindSelectOne.set(0);

        docs.add(doc(1L, "ZZ00", "江南省测绘院第一地理信息工程院基础档案", "事业单位", "江南省—临川市—城东区", 0));
        docs.add(doc(2L, "ZZ01", "旧临川市城乡测绘服务中心基础档案（已注销）", "企业单位", "江南省—临川市—城西区", 1));
        docs.add(doc(3L, "ZW01", "外江市精密测绘有限公司基础档案", "企业单位", "江南省—外江市—北岭区", 0));

        categories.add(cate("GC", "工程测量", 1, null));
        categories.add(cate("GC01", "控制测量", 2, null));
        categories.add(cate("GC02", "地形测量", 2, null));
        categories.add(cate("JX01", "界线测绘", 2, null));
        categories.add(cate("GC09", "旧工程测量（已并入控制测量）", 2, "GC01"));

        when(cardMapper.selectOne(any())).thenAnswer(inv -> {
            if (blindSelectOne.getAndDecrement() > 0) {
                return null;
            }
            QueryWrapper<?> qw = inv.getArgument(0);
            return cards.stream().filter(c -> matches(c, qw.getSqlSegment(), qw.getParamNameValuePairs()))
                    .findFirst().orElse(null);
        });
        when(cardMapper.selectList(any())).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            List<TMgisScopeCard> found = new ArrayList<TMgisScopeCard>();
            for (TMgisScopeCard c : cards) {
                if (matches(c, qw.getSqlSegment(), qw.getParamNameValuePairs())) {
                    found.add(c);
                }
            }
            return found;
        });
        when(cardMapper.insert(any())).thenAnswer(inv -> {
            TMgisScopeCard c = inv.getArgument(0);
            // 唯一键兜底：uk_scope_card_source_bill、uk_scope_card_site_scope
            for (TMgisScopeCard e : cards) {
                if (e.getDelFlag() != null && e.getDelFlag() == 0
                        && e.getSourceBillNo() != null && e.getSourceBillNo().equals(c.getSourceBillNo())) {
                    throw new DuplicateKeyException("uk_scope_card_source_bill");
                }
                if (e.getDelFlag() != null && e.getDelFlag() == 0
                        && Objects.equals(e.getSiteNo(), c.getSiteNo())
                        && Objects.equals(e.getScopeSeg(), c.getScopeSeg())) {
                    throw new DuplicateKeyException("uk_scope_card_site_scope");
                }
            }
            if (c.getId() == null) {
                c.setId(++idGen);
            }
            cards.add(c);
            return 1;
        });
        when(cardMapper.update(isNull(), any())).thenAnswer(inv -> {
            UpdateWrapper<?> uw = inv.getArgument(1);
            Map<String, Object> sets = parseSets(uw.getSqlSet(), uw.getParamNameValuePairs());
            int n = 0;
            for (TMgisScopeCard c : cards) {
                if (matches(c, uw.getSqlSegment(), uw.getParamNameValuePairs())) {
                    final TMgisScopeCard row = c;
                    sets.forEach((k, v) -> setCol(row, k, v));
                    n++;
                }
            }
            return n;
        });

        when(unitDocMapper.selectOne(any())).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            return docs.stream().filter(d -> matches(d, qw.getSqlSegment(), qw.getParamNameValuePairs()))
                    .findFirst().orElse(null);
        });
        when(unitDocMapper.selectList(any())).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            List<TMgisUnitDoc> found = new ArrayList<TMgisUnitDoc>();
            for (TMgisUnitDoc d : docs) {
                if (matches(d, qw.getSqlSegment(), qw.getParamNameValuePairs())) {
                    found.add(d);
                }
            }
            return found;
        });

        when(categoryMapper.selectList(any())).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            List<TMgisScopeCategory> found = new ArrayList<TMgisScopeCategory>();
            for (TMgisScopeCategory c : categories) {
                if (matches(c, qw.getSqlSegment(), qw.getParamNameValuePairs())) {
                    found.add(c);
                }
            }
            return found;
        });

        when(itemMapper.insert(any())).thenAnswer(inv -> {
            TMgisScopeCardItem it = inv.getArgument(0);
            it.setId(++idGen);
            items.add(it);
            return 1;
        });
        when(itemMapper.selectList(any())).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            List<TMgisScopeCardItem> found = new ArrayList<TMgisScopeCardItem>();
            for (TMgisScopeCardItem it : items) {
                if (matches(it, qw.getSqlSegment(), qw.getParamNameValuePairs())) {
                    found.add(it);
                }
            }
            return found;
        });
        when(itemMapper.delete(any())).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            items.removeIf(it -> matches(it, qw.getSqlSegment(), qw.getParamNameValuePairs()));
            return 1;
        });

        when(entryMapper.insert(any())).thenAnswer(inv -> {
            TMgisScopeCardEntry e = inv.getArgument(0);
            e.setId(++idGen);
            entries.add(e);
            return 1;
        });
        when(entryMapper.selectList(any())).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            List<TMgisScopeCardEntry> found = new ArrayList<TMgisScopeCardEntry>();
            for (TMgisScopeCardEntry e : entries) {
                if (matches(e, qw.getSqlSegment(), qw.getParamNameValuePairs())) {
                    found.add(e);
                }
            }
            found.sort((a, b) -> a.getSeq() - b.getSeq());
            return found;
        });
        when(entryMapper.selectCount(any())).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            int n = 0;
            for (TMgisScopeCardEntry e : entries) {
                if (matches(e, qw.getSqlSegment(), qw.getParamNameValuePairs())) {
                    n++;
                }
            }
            return n;
        });
    }

    private static TMgisUnitDoc doc(long id, String siteNo, String name, String type, String road, int status) {
        TMgisUnitDoc d = new TMgisUnitDoc();
        d.setId(id);
        d.setSiteNo(siteNo);
        d.setSiteName(name);
        d.setSiteType(type);
        d.setRoadName(road);
        d.setTh1Max(new BigDecimal("30"));
        d.setTh2Max(new BigDecimal("120"));
        d.setTh3Max(new BigDecimal("360"));
        d.setStatus(status);
        d.setDelFlag(0);
        return d;
    }

    private static TMgisScopeCategory cate(String code, String name, int lvl, String mergedInto) {
        TMgisScopeCategory c = new TMgisScopeCategory();
        c.setCateCode(code);
        c.setCateName(name);
        c.setLvl(lvl);
        c.setMergedInto(mergedInto);
        c.setStatus(0);
        c.setDelFlag(0);
        return c;
    }

    private static ScopeCardRegisterRequest registerReq(String bill, String from, String siteNo, String seg,
                                                        String qty, String fine, String... cats) {
        ScopeCardRegisterRequest r = new ScopeCardRegisterRequest();
        r.setProcedure("REGISTER");
        r.setSourceBillNo(bill);
        r.setSourceFrom(from);
        r.setSiteNo(siteNo);
        r.setScopeSeg(seg);
        r.setQty(qty);
        r.setFineAmt(fine);
        r.setCategories(new ArrayList<String>(Arrays.asList(cats)));
        return r;
    }

    private static ScopeCardRegisterRequest proceedReq(String proc, Long cardId) {
        ScopeCardRegisterRequest r = new ScopeCardRegisterRequest();
        r.setProcedure(proc);
        r.setCardId(cardId);
        return r;
    }

    // ---------- 立卡 ----------

    @Test
    void 立卡系统给号创建人写死经手人落头一笔() {
        ScopeCardRegisterResult result = service.register(
                registerReq("LW-001", "单位自报", "ZZ00", "SEG-A", "45", "5", "GC01", "GC02"), "张三");
        assertFalse(result.isDuplicated());
        TMgisScopeCard card = result.getCard();
        assertTrue(card.getBillNo().startsWith("SC"));
        assertEquals("LW-001", card.getSourceBillNo());
        assertEquals("UNIT_SELF", card.getSourceFrom());
        assertEquals("ZZ00", card.getSiteNo());
        assertEquals("SEG-A", card.getScopeSeg());
        assertEquals(new BigDecimal("45"), card.getQty());
        assertEquals(new BigDecimal("5"), card.getFineAmt());
        // 层级档系统折算落库：(45-30)/90 → 2.17
        assertEquals(new BigDecimal("2.17"), card.getGradeLevel());
        assertEquals(Integer.valueOf(0), card.getStatus());
        // 创建人：按下立卡那一下是谁就是谁
        assertEquals("张三", card.getCreateBy());
        // 在挂类别逐条落账
        assertEquals(2, items.size());
        // 经手人链头一笔：立卡，经手人是张三
        assertEquals(1, entries.size());
        assertEquals(ScopeCardProcedure.REGISTER.name(), entries.get(0).getProc());
        assertEquals("张三", entries.get(0).getPennedBy());
        assertNotNull(entries.get(0).getPennedAt());
    }

    @Test
    void 同一单号两路来文并成一条() {
        ScopeCardRegisterResult first = service.register(
                registerReq("LW-002", "单位自报", "ZZ00", "SEG-A", "10", null, "GC01"), "张三");
        // 市局抄送同一单号递进来：并一条，头一张原样回
        ScopeCardRegisterResult second = service.register(
                registerReq("LW-002", "市局抄送", "ZZ00", "SEG-A", "10", null, "GC01"), "李四");
        assertTrue(second.isDuplicated());
        assertEquals(first.getCard().getBillNo(), second.getCard().getBillNo());
        // 账上不留两笔：卡一张、经手人一条、创建人没挪
        assertEquals(1, cards.size());
        assertEquals(1, entries.size());
        assertEquals("张三", cards.get(0).getCreateBy());
    }

    @Test
    void 同一单位同一段范围第二张顶回() {
        service.register(registerReq("LW-003", "单位自报", "ZZ00", "SEG-A", "10", null, "GC01"), "张三");
        ScopeCardAcceptException e = assertThrows(ScopeCardAcceptException.class, () ->
                service.register(registerReq("LW-004", "单位自报", "ZZ00", "SEG-A", "20", null, "GC02"), "李四"));
        assertEquals(ScopeCardRejectReason.DUPLICATE_SCOPE.getCode(), e.getReasonCode());
        assertEquals("作业范围段", e.getField());
        assertEquals(1, cards.size());
    }

    @Test
    void 注销档案不收新卡编号对不上也不收() {
        ScopeCardAcceptException cancelled = assertThrows(ScopeCardAcceptException.class, () ->
                service.register(registerReq("LW-005", "单位自报", "ZZ01", "SEG-A", "10", null, "GC01"), "张三"));
        assertEquals(ScopeCardRejectReason.SITE_DOC_CANCELLED.getCode(), cancelled.getReasonCode());

        ScopeCardAcceptException notFound = assertThrows(ScopeCardAcceptException.class, () ->
                service.register(registerReq("LW-006", "单位自报", "ZZ99", "SEG-A", "10", null, "GC01"), "张三"));
        assertEquals(ScopeCardRejectReason.SITE_DOC_NOT_FOUND.getCode(), notFound.getReasonCode());
        assertEquals(0, cards.size());
    }

    @Test
    void 挂用类别数与类别清单的拦法与裁判同口径() {
        ScopeCardAcceptException neg = assertThrows(ScopeCardAcceptException.class, () ->
                service.register(registerReq("LW-007", "单位自报", "ZZ00", "SEG-A", "-1", null, "GC01"), "张三"));
        assertEquals(ScopeCardRejectReason.QTY_NOT_POSITIVE.getCode(), neg.getReasonCode());
        assertEquals("挂用类别数", neg.getField());

        ScopeCardAcceptException major = assertThrows(ScopeCardAcceptException.class, () ->
                service.register(registerReq("LW-008", "单位自报", "ZZ00", "SEG-A", "10", null, "GC"), "张三"));
        assertEquals(ScopeCardRejectReason.MAJOR_NOT_LEAF.getCode(), major.getReasonCode());

        ScopeCardAcceptException merged = assertThrows(ScopeCardAcceptException.class, () ->
                service.register(registerReq("LW-009", "单位自报", "ZZ00", "SEG-A", "10", null, "GC09"), "张三"));
        assertEquals(ScopeCardRejectReason.MERGED_CATEGORY.getCode(), merged.getReasonCode());
        assertEquals(0, cards.size());
    }

    // ---------- 走站与类别增减 ----------

    @Test
    void 核对定档一站接一站经手人逐笔记() {
        ScopeCardRegisterResult reg = service.register(
                registerReq("LW-010", "单位自报", "ZZ00", "SEG-A", "45", null, "GC01"), "张三");
        Long cardId = reg.getCard().getId();

        ScopeCardRegisterResult checked = service.register(proceedReq("CHECK", cardId), "李四");
        assertEquals(Integer.valueOf(1), checked.getCard().getStatus());

        // 定档前档案顶线变了：层级档跟着重新折算，不许只吃立卡那天那一折
        docs.get(0).setTh1Max(new BigDecimal("40"));
        ScopeCardRegisterResult graded = service.register(proceedReq("定档", cardId), "王五");
        assertEquals(Integer.valueOf(2), graded.getCard().getStatus());
        // (45-40)/(120-40)=0.0625 → 2.06
        assertEquals(new BigDecimal("2.06"), graded.getCard().getGradeLevel());

        // 经手人链：立卡、核对、定档三条，各记各的经手人
        assertEquals(3, entries.size());
        assertEquals("张三", entries.get(0).getPennedBy());
        assertEquals("李四", entries.get(1).getPennedBy());
        assertEquals("王五", entries.get(2).getPennedBy());
        // 创建人没挪
        assertEquals("张三", graded.getCard().getCreateBy());

        // 已定档再走核对：隔站顶回
        ScopeCardAcceptException e = assertThrows(ScopeCardAcceptException.class, () ->
                service.register(proceedReq("CHECK", cardId), "赵六"));
        assertEquals(ScopeCardRejectReason.BAD_STATION.getCode(), e.getReasonCode());
        assertEquals(3, entries.size());
    }

    @Test
    void 类别增减整批换清单层级档跟着重折() {
        ScopeCardRegisterResult reg = service.register(
                registerReq("LW-011", "单位自报", "ZZ00", "SEG-A", "10", "2", "GC01"), "张三");
        Long cardId = reg.getCard().getId();

        ScopeCardRegisterRequest adj = proceedReq("ADJUST", cardId);
        adj.setCategories(new ArrayList<String>(Arrays.asList("GC01", "GC02", "JX01")));
        adj.setQty("200");
        ScopeCardRegisterResult adjusted = service.register(adj, "李四");
        // 清单整批换过
        assertEquals(3, items.size());
        assertEquals(new BigDecimal("200"), adjusted.getCard().getQty());
        // (200-120)/240 → 3.33
        assertEquals(new BigDecimal("3.33"), adjusted.getCard().getGradeLevel());
        // 本年度新增空着不动
        assertEquals(new BigDecimal("2"), adjusted.getCard().getFineAmt());
        assertEquals(2, entries.size());
        assertEquals("李四", entries.get(1).getPennedBy());

        // 增减到越过顶线：整单顶回，旧清单一项不动
        ScopeCardRegisterRequest over = proceedReq("ADJUST", cardId);
        over.setCategories(new ArrayList<String>(Arrays.asList("GC01")));
        over.setQty("999");
        ScopeCardAcceptException e = assertThrows(ScopeCardAcceptException.class, () ->
                service.register(over, "李四"));
        assertEquals(ScopeCardRejectReason.QTY_OVER_CEILING.getCode(), e.getReasonCode());
        assertEquals(3, items.size());
        assertEquals(new BigDecimal("200"), cards.get(0).getQty());
    }

    @Test
    void 注销档案的老卡查得到但不许接着添东西() {
        ScopeCardRegisterResult reg = service.register(
                registerReq("LW-012", "单位自报", "ZW01", "SEG-W", "10", null, "GC01"), "张三");
        Long cardId = reg.getCard().getId();
        // 档案注销
        docs.get(2).setStatus(1);

        ScopeCardAcceptException check = assertThrows(ScopeCardAcceptException.class, () ->
                service.register(proceedReq("CHECK", cardId), "李四"));
        assertEquals(ScopeCardRejectReason.SITE_DOC_CANCELLED.getCode(), check.getReasonCode());

        ScopeCardRegisterRequest adj = proceedReq("ADJUST", cardId);
        adj.setCategories(new ArrayList<String>(Arrays.asList("GC01", "GC02")));
        adj.setQty("20");
        assertThrows(ScopeCardAcceptException.class, () -> service.register(adj, "李四"));

        // 老卡照旧查得到
        ScopeCardView view = service.cardView(cardId, null);
        assertEquals("ZW01", view.getCard().getSiteNo());
        assertEquals(1, view.getEntries().size());
    }

    // ---------- 卡页面与层级档当场重折 ----------

    @Test
    void 卡页面层级档当场重折与落库档并列() {
        ScopeCardRegisterResult reg = service.register(
                registerReq("LW-013", "单位自报", "ZZ00", "SEG-A", "45", null, "GC01"), "张三");
        ScopeCardView view = service.cardView(reg.getCard().getId(), null);
        assertEquals(new BigDecimal("2.17"), view.getCurrentGrade());
        assertTrue(view.isGradeConsistent());
        assertEquals(1, view.getCategories().size());
        assertEquals("控制测量", view.getCategories().get(0).getCateName());

        // 档案顶线事后变了：当场重折跟着变，落库档还是老值——对不上照实亮出来
        docs.get(0).setTh1Max(new BigDecimal("40"));
        ScopeCardView view2 = service.cardView(reg.getCard().getId(), null);
        assertEquals(new BigDecimal("2.06"), view2.getCurrentGrade());
        assertFalse(view2.isGradeConsistent());
    }

    // ---------- 属地可见与看账 ----------

    @Test
    void 属地只看得见本市省局看得见全省() {
        service.register(registerReq("LW-014", "单位自报", "ZZ00", "SEG-A", "10", "3", "GC01"), "张三");
        ScopeCardRegisterResult outer = service.register(
                registerReq("LW-015", "单位自报", "ZW01", "SEG-W", "20", "7", "GC02"), "李四");

        // 省局（viewerCity 为空）：全省两笔
        assertEquals(2, service.selectVisibleTMgisScopeCardList(new QueryWrapper<TMgisScopeCard>(), null).size());
        // 临川市：只见本市那笔
        List<TMgisScopeCard> linchuan = service.selectVisibleTMgisScopeCardList(
                new QueryWrapper<TMgisScopeCard>(), "临川市");
        assertEquals(1, linchuan.size());
        assertEquals("ZZ00", linchuan.get(0).getSiteNo());
        // 外江市的卡在临川市点开：当不在账上回
        ScopeCardAcceptException e = assertThrows(ScopeCardAcceptException.class, () ->
                service.cardView(outer.getCard().getId(), "临川市"));
        assertEquals(ScopeCardRejectReason.CARD_NOT_FOUND.getCode(), e.getReasonCode());
        // 同一条卡在省局点开是同一笔账
        assertEquals(outer.getCard().getBillNo(), service.cardView(outer.getCard().getId(), null).getCard().getBillNo());
    }

    @Test
    void 看账两条路数出来的卡数一致() {
        service.register(registerReq("LW-016", "单位自报", "ZZ00", "SEG-A", "10", "3", "GC01"), "张三");
        service.register(registerReq("LW-017", "单位自报", "ZZ00", "SEG-B", "20", "5", "GC02"), "张三");
        service.register(registerReq("LW-018", "单位自报", "ZW01", "SEG-W", "30", "9", "GC01"), "李四");

        ScopeCardTallyView province = service.tallyByProvince(null);
        ScopeCardTallyView region = service.tallyByRegion(null);
        assertEquals(3, province.getTotalCards());
        assertEquals(3, region.getTotalCards());
        // 省局口径：ZW01 新增 9 排前，ZZ00 新增 3+5=8 在后
        assertEquals("ZW01", province.getRows().get(0).getKey());
        assertEquals("ZZ00", province.getRows().get(1).getKey());
        assertEquals(new BigDecimal("8"), province.getRows().get(1).getFineAmtSum());
        // 属地口径：城东区一单位两笔，北岭区一单位一笔
        assertEquals(2, region.getRows().size());
        assertEquals("北岭区", region.getRows().get(0).getKey());
        assertEquals("城东区", region.getRows().get(1).getKey());
        assertEquals(2, region.getRows().get(1).getCardCount());

        // 属地看账一样两口径一致
        assertEquals(service.tallyByProvince("临川市").getTotalCards(),
                service.tallyByRegion("临川市").getTotalCards());
    }

    // ---------- 并发兜底 ----------

    @Test
    void 并发同递同一单号唯一键兜底并一条() {
        // 另一线程已把 LW-019 落账，本线程查询时还没看见
        TMgisScopeCard committed = new TMgisScopeCard();
        committed.setId(1L);
        committed.setBillNo("SC1");
        committed.setSourceBillNo("LW-019");
        committed.setSourceFrom("UNIT_SELF");
        committed.setSiteNo("ZZ00");
        committed.setScopeSeg("SEG-A");
        committed.setQty(new BigDecimal("10"));
        committed.setGradeLevel(new BigDecimal("1.33"));
        committed.setStatus(0);
        committed.setDelFlag(0);
        committed.setCreateBy("张三");
        cards.add(committed);
        // 单号查询、范围段查询两回都没看见另一线程的提交，插入才撞上唯一键
        blindSelectOne.set(2);

        ScopeCardRegisterResult result = service.register(
                registerReq("LW-019", "市局抄送", "ZZ00", "SEG-A", "10", null, "GC01"), "李四");
        assertTrue(result.isDuplicated());
        assertEquals("SC1", result.getCard().getBillNo());
        assertEquals(1, cards.size());
    }

    @Test
    void 并发同范围段第二张唯一键兜底顶回() {
        TMgisScopeCard committed = new TMgisScopeCard();
        committed.setId(2L);
        committed.setBillNo("SC2");
        committed.setSourceBillNo("LW-020");
        committed.setSourceFrom("UNIT_SELF");
        committed.setSiteNo("ZZ00");
        committed.setScopeSeg("SEG-A");
        committed.setQty(new BigDecimal("10"));
        committed.setGradeLevel(new BigDecimal("1.33"));
        committed.setStatus(0);
        committed.setDelFlag(0);
        committed.setCreateBy("张三");
        cards.add(committed);
        // 单号查询、范围段查询两回都没看见，插入才撞上唯一键
        blindSelectOne.set(2);

        ScopeCardAcceptException e = assertThrows(ScopeCardAcceptException.class, () ->
                service.register(registerReq("LW-021", "单位自报", "ZZ00", "SEG-A", "10", null, "GC01"), "李四"));
        assertEquals(ScopeCardRejectReason.DUPLICATE_SCOPE.getCode(), e.getReasonCode());
        assertEquals(1, cards.size());
    }

    // ---------- 档案变更重折 ----------

    @Test
    void 档案顶线变了名下各卡跟着重折越线的留原档() {
        service.register(registerReq("LW-022", "单位自报", "ZZ00", "SEG-A", "45", null, "GC01"), "张三");
        service.register(registerReq("LW-023", "单位自报", "ZZ00", "SEG-B", "10", null, "GC02"), "张三");
        service.register(registerReq("LW-024", "单位自报", "ZZ00", "SEG-C", "100", null, "JX01"), "张三");

        // 顶线调整：40/45/50——qty 100 那张越过新顶线，留原档待人处置
        docs.get(0).setTh1Max(new BigDecimal("40"));
        docs.get(0).setTh2Max(new BigDecimal("45"));
        docs.get(0).setTh3Max(new BigDecimal("50"));
        int folded = service.recalcGradesOfSite("ZZ00");
        assertEquals(2, folded);
        // 45 恰压二档新顶线 → 3.00；10 → 1+10/40=1.25；100 越线留 2.78
        assertEquals(new BigDecimal("3.00"), cards.get(0).getGradeLevel());
        assertEquals(new BigDecimal("1.25"), cards.get(1).getGradeLevel());
        assertEquals(new BigDecimal("2.78"), cards.get(2).getGradeLevel());
    }

    @Test
    void 手续不识与未指都顶回() {
        ScopeCardAcceptException miss = assertThrows(ScopeCardAcceptException.class, () ->
                service.register(proceedReq(null, 1L), "张三"));
        assertEquals(ScopeCardRejectReason.MISSING_PROCEDURE.getCode(), miss.getReasonCode());

        ScopeCardAcceptException unknown = assertThrows(ScopeCardAcceptException.class, () ->
                service.register(proceedReq("销毁", 1L), "张三"));
        assertEquals(ScopeCardRejectReason.UNKNOWN_PROCEDURE.getCode(), unknown.getReasonCode());
        assertTrue(cards.isEmpty());
    }
}
