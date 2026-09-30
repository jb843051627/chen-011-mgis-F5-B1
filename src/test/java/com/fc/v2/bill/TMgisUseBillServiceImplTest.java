package com.fc.v2.bill;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TMgisUseBillMapper;
import com.fc.v2.mapper.auto.TMgisUseBillSignMapper;
import com.fc.v2.model.auto.TMgisUseBill;
import com.fc.v2.model.auto.TMgisUseBillSign;
import com.fc.v2.service.impl.TMgisUseBillServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 旧逐级核准单补齐暗病单测：否决落账、票数逐票可查、联名两人点齐、
 * 同人第二票不认、退回销旧票、旧单 sign_count 底数兼容。
 */
class TMgisUseBillServiceImplTest {

    private TMgisUseBillMapper billMapper;
    private TMgisUseBillSignMapper signMapper;
    private TMgisUseBillServiceImpl service;

    private TMgisUseBill bill;
    private List<TMgisUseBillSign> signs;
    private long idGen = 100L;

    private static final Pattern COND = Pattern.compile("(\\w+)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}");
    private static final Pattern GE = Pattern.compile("(\\w+)\\s*>=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}");

    @BeforeEach
    void setUp() {
        billMapper = mock(TMgisUseBillMapper.class);
        signMapper = mock(TMgisUseBillSignMapper.class);
        service = new TMgisUseBillServiceImpl();
        ReflectionTestUtils.setField(service, "mgisUseBillMapper", billMapper);
        ReflectionTestUtils.setField(service, "mgisUseBillSignMapper", signMapper);

        signs = new ArrayList<TMgisUseBillSign>();
        bill = new TMgisUseBill();
        bill.setId(1L);
        bill.setBillNo("GZ-1");
        bill.setNodeNo(0);
        bill.setSignMode(0);
        bill.setNeedCount(1);
        bill.setSignCount(0);
        bill.setStatus(0);
        bill.setDelFlag(0);

        when(billMapper.selectById(any())).thenReturn(bill);
        when(billMapper.selectForUpdate(any())).thenReturn(bill);
        when(billMapper.update(isNull(), any())).thenAnswer(inv -> {
            UpdateWrapper<?> uw = inv.getArgument(1);
            Map<String, Object> where = parse(uw.getSqlSegment(), uw.getParamNameValuePairs(), COND);
            where.putAll(parse(uw.getSqlSegment(), uw.getParamNameValuePairs(), GE));
            Map<String, Object> sets = parse(uw.getSqlSet(), uw.getParamNameValuePairs(), COND);
            boolean match = true;
            for (Map.Entry<String, Object> e : where.entrySet()) {
                match &= Objects.equals(col(bill, e.getKey()), e.getValue());
            }
            if (match) {
                sets.forEach((c, v) -> setCol(bill, c, v));
                return 1;
            }
            return 0;
        });

        when(signMapper.insert(any())).thenAnswer(inv -> {
            TMgisUseBillSign s = inv.getArgument(0);
            s.setId(++idGen);
            signs.add(s);
            return 1;
        });
        when(signMapper.selectList(any())).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            Map<String, Object> conds = parse(qw.getSqlSegment(), qw.getParamNameValuePairs(), COND);
            List<TMgisUseBillSign> hit = new ArrayList<TMgisUseBillSign>();
            for (TMgisUseBillSign s : signs) {
                boolean ok = true;
                for (Map.Entry<String, Object> e : conds.entrySet()) {
                    ok &= Objects.equals(col(s, e.getKey()), e.getValue());
                }
                if (ok) {
                    hit.add(s);
                }
            }
            return hit;
        });
        when(signMapper.selectCount(any())).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            Map<String, Object> conds = parse(qw.getSqlSegment(), qw.getParamNameValuePairs(), COND);
            int n = 0;
            for (TMgisUseBillSign s : signs) {
                boolean ok = true;
                for (Map.Entry<String, Object> e : conds.entrySet()) {
                    ok &= Objects.equals(col(s, e.getKey()), e.getValue());
                }
                if (ok) {
                    n++;
                }
            }
            return n;
        });
        when(signMapper.delete(any())).thenAnswer(inv -> {
            QueryWrapper<?> qw = inv.getArgument(0);
            int before = signs.size();
            signs.removeIf(s -> {
                Map<String, Object> eq = parse(qw.getSqlSegment(), qw.getParamNameValuePairs(), COND);
                Map<String, Object> ge = parse(qw.getSqlSegment(), qw.getParamNameValuePairs(), GE);
                for (Map.Entry<String, Object> e : eq.entrySet()) {
                    if (!Objects.equals(col(s, e.getKey()), e.getValue())) {
                        return false;
                    }
                }
                for (Map.Entry<String, Object> e : ge.entrySet()) {
                    Comparable<Object> got = (Comparable<Object>) col(s, e.getKey());
                    if (got == null || got.compareTo(e.getValue()) < 0) {
                        return false;
                    }
                }
                return true;
            });
            return before - signs.size();
        });
    }

    private static TMgisUseBill jointBill() {
        TMgisUseBill b = new TMgisUseBill();
        b.setId(2L);
        b.setBillNo("GZ-2");
        b.setNodeNo(1); // 停在顶节点
        b.setSignMode(1);
        b.setNeedCount(2);
        b.setSignCount(0);
        b.setStatus(0);
        b.setDelFlag(0);
        return b;
    }

    @Test
    void 任一人一票即过且票落账可查() {
        TMgisUseBill r = service.approve(1L, "王处", "同意发放");
        assertEquals(Integer.valueOf(1), r.getNodeNo());
        assertEquals(Integer.valueOf(0), r.getStatus());
        // 票逐笔落账：签人、话、同意都在
        assertEquals(1, signs.size());
        TMgisUseBillSign v = signs.get(0);
        assertEquals("王处", v.getApprover());
        assertEquals("同意发放", v.getCommentText());
        assertEquals(Integer.valueOf(TMgisUseBillSign.VERDICT_APPROVE), v.getVerdict());
        assertNotNull(v.getSignAt());
        // 查到哪位签过
        assertEquals(1, service.listSigns(1L).size());
    }

    @Test
    void 联名档一人一票不放行两人点齐才核讫() {
        TMgisUseBill joint = jointBill();
        bill = joint;
        when(billMapper.selectById(any())).thenReturn(joint);
        when(billMapper.selectForUpdate(any())).thenReturn(joint);

        TMgisUseBill afterFirst = service.approve(2L, "王处", "同意");
        // 只落一人，件仍停在顶节点、在核
        assertEquals(Integer.valueOf(1), afterFirst.getNodeNo());
        assertEquals(Integer.valueOf(0), afterFirst.getStatus());
        assertEquals(Integer.valueOf(1), afterFirst.getSignCount());

        // 同一个人再点一回：只认头一笔，不放行
        TMgisUseBill sameAgain = service.approve(2L, "王处", "再同意");
        assertEquals(Integer.valueOf(1), sameAgain.getNodeNo());
        assertEquals(Integer.valueOf(0), sameAgain.getStatus());
        assertEquals(1, signs.size());

        // 另一个人点第二笔：两名点齐，核讫
        TMgisUseBill passed = service.approve(2L, "李厅", "同意");
        assertEquals(Integer.valueOf(2), passed.getNodeNo());
        assertEquals(Integer.valueOf(1), passed.getStatus());
        assertEquals(Integer.valueOf(0), passed.getSignCount());
        assertEquals(2, signs.size());
    }

    @Test
    void 否决脸色与缘由都落账不再是空走一趟() {
        TMgisUseBill r = service.reject(1L, "赵处", "用途不实，不予核准");
        assertEquals(Integer.valueOf(2), r.getStatus());
        assertTrue(r.getRemark().contains("用途不实"));
        assertTrue(r.getRemark().contains("赵处"));
        // 否决票也落账，查得到谁否的、为何否
        assertEquals(1, signs.size());
        assertEquals(Integer.valueOf(TMgisUseBillSign.VERDICT_REJECT), signs.get(0).getVerdict());
        // 定死后再签不收
        service.approve(1L, "王处", "同意");
        assertEquals(1, signs.size());
    }

    @Test
    void 退回上一环节旧票销籍票数清零() {
        // 先在本层攒一票
        service.approve(1L, "王处", "同意");
        assertEquals(1, signs.size());
        // 退到上一环节
        TMgisUseBill r = service.rollback(1L, "补材料");
        assertEquals(Integer.valueOf(0), r.getNodeNo());
        assertEquals(Integer.valueOf(0), r.getSignCount());
        // 旧票已销籍，重报从头攒——报第二遍凑不着旧票的额
        assertEquals(0, signs.size());
        assertTrue(r.getRemark().contains("补材料"));
    }

    @Test
    void 旧单无明细以单头票数为底数兼容联名() {
        // 历年旧单：联名档，单头记着已有一票，但明细表还没建
        TMgisUseBill legacy = jointBill();
        legacy.setSignCount(1);
        bill = legacy;
        when(billMapper.selectById(any())).thenReturn(legacy);
        when(billMapper.selectForUpdate(any())).thenReturn(legacy);

        // 只来一个新人补一笔：旧账 1 + 新票 1 = 2，点齐核讫——旧单接得住
        TMgisUseBill passed = service.approve(2L, "李厅", "同意");
        assertEquals(Integer.valueOf(1), passed.getStatus());
        assertEquals(Integer.valueOf(2), passed.getNodeNo());
        assertEquals(1, signs.size());
    }

    @Test
    void 签批方式空着按任一人论() {
        bill.setSignMode(null);
        bill.setNeedCount(null);
        TMgisUseBill r = service.approve(1L, "王处", "同意");
        assertEquals(Integer.valueOf(1), r.getNeedCount());
        assertEquals(Integer.valueOf(1), r.getNodeNo());
    }

    @Test
    void 件不在账上或经办空着都不成票() {
        assertNull(service.approve(null, "王处", "x"));
        assertNull(service.approve(1L, "  ", "x"));
        assertNull(service.reject(null, "王处", "x"));
        assertFalse(service.listSigns(null).iterator().hasNext());
        assertEquals(0, signs.size());
    }

    // ---------- 假库帮手 ----------

    private static Map<String, Object> parse(String sql, Map<String, Object> params, Pattern p) {
        Map<String, Object> out = new java.util.LinkedHashMap<String, Object>();
        if (sql == null) {
            return out;
        }
        Matcher m = p.matcher(sql);
        while (m.find()) {
            out.put(m.group(1), params.get(m.group(2)));
        }
        return out;
    }

    private static Object col(Object row, String column) {
        try {
            StringBuilder name = new StringBuilder("get");
            for (String part : column.split("_")) {
                name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            return row.getClass().getMethod(name.toString()).invoke(row);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setCol(Object row, String column, Object value) {
        try {
            StringBuilder name = new StringBuilder("set");
            for (String part : column.split("_")) {
                name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            for (java.lang.reflect.Method m : row.getClass().getMethods()) {
                if (m.getName().equals(name.toString()) && m.getParameterTypes().length == 1
                        && (value == null || m.getParameterTypes()[0].isInstance(value))) {
                    m.invoke(row, value);
                    return;
                }
            }
            throw new IllegalStateException("没有写入法子：" + column);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
