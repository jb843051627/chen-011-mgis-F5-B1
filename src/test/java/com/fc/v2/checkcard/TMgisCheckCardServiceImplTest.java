package com.fc.v2.checkcard;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TMgisCheckCardMapper;
import com.fc.v2.model.auto.TMgisCheckCard;
import com.fc.v2.service.impl.TMgisCheckCardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 整改回执单流转单测：一关接一关不跳关、末关才销号、终局拧不动、
 * 回退只退一关、老脏数据（中段误置已销号）接得住。
 */
class TMgisCheckCardServiceImplTest {

    private TMgisCheckCardMapper mapper;
    private TMgisCheckCardServiceImpl service;
    private List<TMgisCheckCard> cards;

    @BeforeEach
    void setUp() {
        mapper = mock(TMgisCheckCardMapper.class);
        service = new TMgisCheckCardServiceImpl();
        ReflectionTestUtils.setField(service, "mgisCheckCardMapper", mapper);
        cards = new ArrayList<TMgisCheckCard>();

        when(mapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TMgisCheckCard c : cards) {
                if (c.getId().equals(id)) {
                    return c;
                }
            }
            return null;
        });
        // 条件更新照真库 WHERE 匹配
        when(mapper.update(isNull(), any())).thenAnswer(inv -> {
            UpdateWrapper<TMgisCheckCard> uw = inv.getArgument(1);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?i)(id|stage|status)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)}")
                    .matcher(uw.getSqlSegment());
            Long whereId = null;
            Integer whereStage = null;
            Integer whereStatus = null;
            while (m.find()) {
                Object v = uw.getParamNameValuePairs().get(m.group(2));
                switch (m.group(1).toLowerCase()) {
                    case "id":
                        whereId = ((Number) v).longValue();
                        break;
                    case "stage":
                        whereStage = ((Number) v).intValue();
                        break;
                    default:
                        whereStatus = ((Number) v).intValue();
                        break;
                }
            }
            for (TMgisCheckCard c : cards) {
                if (!c.getId().equals(whereId)) {
                    continue;
                }
                if ((whereStage == null || whereStage.equals(c.getStage()))
                        && (whereStatus == null || whereStatus.equals(c.getStatus()))) {
                    java.util.regex.Matcher set = java.util.regex.Pattern
                            .compile("(?i)(stage|status|last_action)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)}")
                            .matcher(uw.getSqlSet());
                    while (set.find()) {
                        Object v = uw.getParamNameValuePairs().get(set.group(2));
                        switch (set.group(1).toLowerCase()) {
                            case "stage":
                                c.setStage(((Number) v).intValue());
                                break;
                            case "status":
                                c.setStatus(((Number) v).intValue());
                                break;
                            default:
                                c.setLastAction(v == null ? null : String.valueOf(v));
                                break;
                        }
                    }
                    return 1;
                }
            }
            return 0;
        });
        when(mapper.updateById(any())).thenReturn(1);
        when(mapper.deleteById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return cards.removeIf(c -> c.getId().equals(id)) ? 1 : 0;
        });
    }

    private TMgisCheckCard card(long id, int stage, Integer status) {
        TMgisCheckCard c = new TMgisCheckCard();
        c.setId(id);
        c.setBizNo("ZG" + id);
        c.setStage(stage);
        c.setStatus(status);
        c.setDelFlag(0);
        cards.add(c);
        return c;
    }

    @Test
    void 推进一关一关走过末关才销号() {
        card(1L, 0, 0);
        // 前五推：1..5 前四推停在中段在办，不许提前亮销号灯
        for (int expect = 1; expect <= 4; expect++) {
            TMgisCheckCard r = service.advance(1L, "过口" + expect);
            assertEquals(expect, r.getStage().intValue());
            assertEquals(0, r.getStatus().intValue(), "中段不许提前销号");
        }
        // 第五推到末关：销号
        TMgisCheckCard closed = service.advance(1L, "末关");
        assertEquals(5, closed.getStage().intValue());
        assertEquals(1, closed.getStatus().intValue());
        // 局结了，再推进、回退都拧不动
        assertNull(service.advance(1L, "再推"));
        assertNull(service.rollback(1L, "退回"));
        assertEquals(5, cards.get(0).getStage().intValue());
    }

    @Test
    void 一次只挪一关不跳关() {
        card(1L, 0, 0);
        TMgisCheckCard r = service.advance(1L, "一");
        assertEquals(1, r.getStage().intValue());
    }

    @Test
    void 回退只退一关退到头停住() {
        card(1L, 3, 0);
        assertEquals(2, service.rollback(1L, "退").getStage().intValue());
        card(2L, 0, 0);
        TMgisCheckCard head = service.rollback(2L, "已在头关");
        assertEquals(0, head.getStage().intValue());
        assertEquals(0, head.getStatus().intValue());
    }

    @Test
    void 封存后推进回退改记事删除一律拒() {
        card(1L, 2, 2);
        assertNull(service.advance(1L, "推"));
        assertNull(service.rollback(1L, "退"));
        assertFalse(service.updateContent(1L, "改过的记事"));
        assertFalse(service.remove(1L));
        verify(mapper, never()).deleteById(any());
    }

    @Test
    void 销号后改记事删除也拒() {
        card(1L, 5, 1);
        assertFalse(service.updateContent(1L, "x"));
        assertFalse(service.remove(1L));
        assertEquals(1, cards.size());
    }

    @Test
    void 在办卡改记事删除照常() {
        card(1L, 2, 0);
        assertTrue(service.updateContent(1L, "补齐了"));
        assertEquals("补齐了", cards.get(0).getContent());
        card(2L, 1, 0);
        assertTrue(service.remove(2L));
        assertEquals(1, cards.size());
    }

    @Test
    void 老数据中段误置已销号的卡还接得住() {
        // 早年实现每推一关就错写 status=1：卡在第 2 关、status=1，其实没销号
        card(1L, 2, 1);
        TMgisCheckCard r = service.advance(1L, "接着走");
        assertEquals(3, r.getStage().intValue());
        assertEquals(0, r.getStatus().intValue(), "接着走即拨回在办");
        // 真销号的老卡（末关 + 已销号）仍然终局
        card(2L, 5, 1);
        assertNull(service.advance(2L, "推"));
    }

    @Test
    void 件不在账上一律回null或false() {
        assertNull(service.advance(99L, "推"));
        assertNull(service.rollback(99L, "退"));
        assertFalse(service.updateContent(99L, "x"));
        assertFalse(service.remove(99L));
    }
}
