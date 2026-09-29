package com.fc.v2.useorder;

import com.fc.v2.common.useorder.UseOrderAction;
import com.fc.v2.common.useorder.UseOrderChainJudge;
import com.fc.v2.model.auto.TMgisUseEntry;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 签署链单测：顺拉倒拉，少一笔、错一处、只签一枚就放成果、废票新票并排，
 * 都算不合格。
 */
class UseOrderChainJudgeTest {

    private static final long ORDER_ID = 9L;
    private int minute = 0;

    private static Date at(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /** 往链尾落一笔（哈希按章程押好，时辰自受理起一分钟一笔） */
    private TMgisUseEntry pen(List<TMgisUseEntry> chain, UseOrderAction action, int round, String payload) {
        TMgisUseEntry e = new TMgisUseEntry();
        e.setOrderId(ORDER_ID);
        e.setSeq(chain.size() + 1);
        e.setRoundNo(round);
        e.setStage(action.getStage().name());
        e.setAction(action.name());
        e.setPayload(payload);
        e.setPennedBy("经办" + chain.size());
        e.setPennedAt(at("2026-09-20 09:" + String.format("%02d", minute % 60) + ":00"));
        minute++;
        e.setPrevHash(chain.isEmpty() ? UseOrderChainJudge.GENESIS
                : chain.get(chain.size() - 1).getHash());
        e.setHash(UseOrderChainJudge.hashOf(e));
        chain.add(e);
        return e;
    }

    /** 走满四阶、两签齐、放成果的一件 */
    private List<TMgisUseEntry> happyChain() {
        List<TMgisUseEntry> chain = new ArrayList<TMgisUseEntry>();
        pen(chain, UseOrderAction.ACCEPT, 1, "{\"applicant\":\"规划院\"}");
        pen(chain, UseOrderAction.CITY_PASS, 1, "{}");
        pen(chain, UseOrderAction.SIGN_SECRECY, 1, "{\"opinion\":\"脱密到位\"}");
        pen(chain, UseOrderAction.SIGN_PURPOSE, 1, "{\"opinion\":\"用途正当\"}");
        pen(chain, UseOrderAction.ISSUE, 1, "{}");
        return chain;
    }

    @Test
    void 顺拉倒拉皆通才算合格() {
        List<TMgisUseEntry> chain = happyChain();
        assertTrue(UseOrderChainJudge.verifyForward(chain).isOk());
        assertTrue(UseOrderChainJudge.verifyBackward(chain).isOk());
    }

    @Test
    void 市局否决的件链也通且收尾() {
        List<TMgisUseEntry> chain = new ArrayList<TMgisUseEntry>();
        pen(chain, UseOrderAction.ACCEPT, 1, "{}");
        pen(chain, UseOrderAction.CITY_REJECT, 1, "{\"reason\":\"用途不实\"}");
        assertTrue(UseOrderChainJudge.verifyForward(chain).isOk());
        assertTrue(UseOrderChainJudge.verifyBackward(chain).isOk());
    }

    @Test
    void 省厅否决的件链通且收尾() {
        List<TMgisUseEntry> chain = new ArrayList<TMgisUseEntry>();
        pen(chain, UseOrderAction.ACCEPT, 1, "{}");
        pen(chain, UseOrderAction.CITY_PASS, 1, "{}");
        pen(chain, UseOrderAction.SIGN_SECRECY, 1, "{\"opinion\":\"存疑\"}");
        pen(chain, UseOrderAction.PROVINCE_REJECT, 1, "{\"reason\":\"不该给\"}");
        assertTrue(UseOrderChainJudge.verifyForward(chain).isOk());
        assertTrue(UseOrderChainJudge.verifyBackward(chain).isOk());
    }

    @Test
    void 发回重办后走满放成果两代笔都在链上() {
        List<TMgisUseEntry> chain = new ArrayList<TMgisUseEntry>();
        pen(chain, UseOrderAction.ACCEPT, 1, "{}");
        pen(chain, UseOrderAction.CITY_PASS, 1, "{}");
        pen(chain, UseOrderAction.SIGN_SECRECY, 1, "{\"opinion\":\"脱墨说明单薄\"}");
        pen(chain, UseOrderAction.RETURN, 1, "{\"reason\":\"脱密说明单薄，补齐再来\"}");
        pen(chain, UseOrderAction.CITY_PASS, 2, "{}");
        pen(chain, UseOrderAction.SIGN_SECRECY, 2, "{\"opinion\":\"补齐了\"}");
        pen(chain, UseOrderAction.SIGN_PURPOSE, 2, "{\"opinion\":\"用途正当\"}");
        pen(chain, UseOrderAction.ISSUE, 2, "{}");
        assertTrue(UseOrderChainJudge.verifyForward(chain).isOk());
        assertTrue(UseOrderChainJudge.verifyBackward(chain).isOk());
    }

    @Test
    void 在办件停在省厅只签一枚链也成立() {
        List<TMgisUseEntry> chain = new ArrayList<TMgisUseEntry>();
        pen(chain, UseOrderAction.ACCEPT, 1, "{}");
        pen(chain, UseOrderAction.CITY_PASS, 1, "{}");
        pen(chain, UseOrderAction.SIGN_SECRECY, 1, "{\"opinion\":\"待用途岗\"}");
        // 件正停在省厅等另一枚：在办合法
        assertTrue(UseOrderChainJudge.verifyForward(chain).isOk());
        assertTrue(UseOrderChainJudge.verifyBackward(chain).isOk());
    }

    @Test
    void 发回后停回市局等重核链收得住() {
        List<TMgisUseEntry> chain = new ArrayList<TMgisUseEntry>();
        pen(chain, UseOrderAction.ACCEPT, 1, "{}");
        pen(chain, UseOrderAction.CITY_PASS, 1, "{}");
        pen(chain, UseOrderAction.RETURN, 1, "{\"reason\":\"补齐\"}");
        assertTrue(UseOrderChainJudge.verifyForward(chain).isOk());
        assertTrue(UseOrderChainJudge.verifyBackward(chain).isOk());
    }

    @Test
    void 只签一枚就放成果不合格() {
        List<TMgisUseEntry> chain = new ArrayList<TMgisUseEntry>();
        pen(chain, UseOrderAction.ACCEPT, 1, "{}");
        pen(chain, UseOrderAction.CITY_PASS, 1, "{}");
        pen(chain, UseOrderAction.SIGN_SECRECY, 1, "{\"opinion\":\"一枚\"}");
        pen(chain, UseOrderAction.ISSUE, 1, "{}");
        UseOrderChainJudge.Leg f = UseOrderChainJudge.verifyForward(chain);
        assertFalse(f.isOk());
        assertTrue(f.getMessage().contains("两枚没齐"));
    }

    @Test
    void 同岗落两枚替签重签不作数() {
        List<TMgisUseEntry> chain = happyChain();
        // 在放成果前硬插第二枚密级岗签（接在用途签之后），放成果笔顺延且时辰靠后
        TMgisUseEntry tail = chain.remove(chain.size() - 1);
        pen(chain, UseOrderAction.SIGN_SECRECY, 1, "{\"opinion\":\"又一枚\"}");
        tail.setSeq(chain.size() + 1);
        tail.setPennedAt(at("2026-09-20 10:00:00"));
        tail.setPrevHash(chain.get(chain.size() - 1).getHash());
        tail.setHash(UseOrderChainJudge.hashOf(tail));
        chain.add(tail);
        UseOrderChainJudge.Leg f = UseOrderChainJudge.verifyForward(chain);
        assertFalse(f.isOk());
        assertTrue(f.getMessage().contains("同一岗"));
    }

    @Test
    void 否决后再放成果定死被破不合格() {
        List<TMgisUseEntry> chain = new ArrayList<TMgisUseEntry>();
        pen(chain, UseOrderAction.ACCEPT, 1, "{}");
        pen(chain, UseOrderAction.CITY_REJECT, 1, "{\"reason\":\"不实\"}");
        // 否决之后又挂一枚
        TMgisUseEntry extra = pen(chain, UseOrderAction.SIGN_SECRECY, 1, "{\"opinion\":\"x\"}");
        UseOrderChainJudge.Leg f = UseOrderChainJudge.verifyForward(chain);
        assertFalse(f.isOk());
        // 走阶次序先断（市局→省厅隔阶）
        assertTrue(f.getMessage().contains("次序") || f.getMessage().contains("定死"));
    }

    @Test
    void 少一笔即不合格() {
        List<TMgisUseEntry> chain = happyChain();
        chain.remove(2); // 抽走一枚省厅签
        assertFalse(UseOrderChainJudge.verifyForward(chain).isOk());
        assertFalse(UseOrderChainJudge.verifyBackward(chain).isOk());
    }

    @Test
    void 序号错位即不合格() {
        List<TMgisUseEntry> chain = happyChain();
        TMgisUseEntry a = chain.get(2);
        chain.set(2, chain.get(3));
        chain.set(3, a);
        assertFalse(UseOrderChainJudge.verifyForward(chain).isOk());
    }

    @Test
    void 事后改话哈希对不上() {
        List<TMgisUseEntry> chain = happyChain();
        chain.get(2).setPayload("{\"opinion\":\"改过的说法\"}");
        UseOrderChainJudge.Leg f = UseOrderChainJudge.verifyForward(chain);
        assertFalse(f.isOk());
        assertTrue(f.getMessage().contains("哈希"));
    }

    @Test
    void 挪时辰倒挂即不合格() {
        List<TMgisUseEntry> chain = happyChain();
        TMgisUseEntry e = chain.get(3);
        e.setPennedAt(at("2026-09-19 08:00:00"));
        e.setHash(UseOrderChainJudge.hashOf(e));
        UseOrderChainJudge.Leg f = UseOrderChainJudge.verifyForward(chain);
        assertFalse(f.isOk());
        assertTrue(f.getMessage().contains("时辰"));
    }

    @Test
    void 没经发回却起新一轮不合格() {
        List<TMgisUseEntry> chain = new ArrayList<TMgisUseEntry>();
        pen(chain, UseOrderAction.ACCEPT, 1, "{}");
        pen(chain, UseOrderAction.CITY_PASS, 1, "{}");
        // 第一轮没发回，第二轮又来一枚核验：废票新票同堂
        pen(chain, UseOrderAction.CITY_PASS, 2, "{}");
        UseOrderChainJudge.Leg f = UseOrderChainJudge.verifyForward(chain);
        assertFalse(f.isOk());
        assertTrue(f.getMessage().contains("次序") || f.getMessage().contains("同堂"));
    }

    @Test
    void 链尾硬塞来路不正的笔倒拉断() {
        List<TMgisUseEntry> chain = happyChain();
        TMgisUseEntry ghost = new TMgisUseEntry();
        ghost.setOrderId(ORDER_ID);
        ghost.setSeq(chain.size() + 1);
        ghost.setRoundNo(1);
        ghost.setStage(UseOrderAction.ISSUE.getStage().name());
        ghost.setAction(UseOrderAction.ISSUE.name());
        ghost.setPayload("{}");
        ghost.setPennedBy("旁人");
        ghost.setPennedAt(at("2026-09-26 01:00:00"));
        ghost.setPrevHash(UseOrderChainJudge.GENESIS);
        ghost.setHash(UseOrderChainJudge.hashOf(ghost));
        chain.add(ghost);
        assertFalse(UseOrderChainJudge.verifyForward(chain).isOk());
        assertFalse(UseOrderChainJudge.verifyBackward(chain).isOk());
    }

    @Test
    void 头一笔不是受理即来路不正() {
        List<TMgisUseEntry> chain = new ArrayList<TMgisUseEntry>();
        pen(chain, UseOrderAction.CITY_PASS, 1, "{}");
        assertFalse(UseOrderChainJudge.verifyForward(chain).isOk());
    }

    @Test
    void 空链不合格() {
        assertFalse(UseOrderChainJudge.verifyForward(new ArrayList<TMgisUseEntry>()).isOk());
        assertFalse(UseOrderChainJudge.verifyBackward(new ArrayList<TMgisUseEntry>()).isOk());
    }
}
