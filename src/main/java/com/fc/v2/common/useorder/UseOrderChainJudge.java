package com.fc.v2.common.useorder;

import com.fc.v2.model.auto.TMgisUseEntry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 签署落笔链校验：领用清单生成之后，把整件顺着落笔先后拉一遍、倒着也拉一遍，
 * 再逐轮核结构。
 *
 * <p>每一笔记「经办、时辰、写的话、阶、动作、轮次、前一笔哈希」，落笔那一下
 * 由系统写死，事后由不得人回头补录，也不许挪时辰。顺拉从受理念到末笔，
 * 逐笔重算哈希、逐笔对接前一笔，须与当日逐阶页面上留下的内容一字不差；
 * 倒拉从末笔沿前哈希念回受理，一笔都不能少。逐轮再核：市局阶恰好一枚
 * 通过或否决；省厅阶两岗各至多一枚；凡放成果的轮次省厅两签必齐；否决笔
 * 之后不得再有笔；跨轮只许「发回 → 新一轮核验」。少一笔、次序错一处、
 * 时辰倒挂、只签一枚就放成果、废票新票并排，顺拉或倒拉必有一程断。</p>
 *
 * @author fuce
 * @date 2026-09-28
 */
public final class UseOrderChainJudge {

    private UseOrderChainJudge() {
    }

    /** 头一笔的前哈希：64 个 0，受理那一笔认它 */
    public static final String GENESIS =
            "0000000000000000000000000000000000000000000000000000000000000000";

    /**
     * 本笔哈希：把这一笔的件、序号、轮次、阶、动作、写的话、经办、落笔时辰、
     * 前一笔哈希一并押进 SHA-256。动其中任何一处哈希即对不上——
     * 事后补录、改话、挪时辰都藏不住。
     */
    public static String hashOf(TMgisUseEntry e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getOrderId()).append('\n')
                .append(e.getSeq()).append('\n')
                .append(e.getRoundNo()).append('\n')
                .append(e.getStage()).append('\n')
                .append(e.getAction()).append('\n')
                .append(e.getPayload() == null ? "" : e.getPayload()).append('\n')
                .append(e.getPennedBy() == null ? "" : e.getPennedBy()).append('\n')
                .append(e.getPennedAt() == null ? "" : penText(e)).append('\n')
                .append(e.getPrevHash());
        return sha256Hex(sb.toString());
    }

    /**
     * 顺拉：从受理念到末笔。
     * 序号自 1 连排、头笔是受理且认 GENESIS、每笔接得住前一笔哈希、
     * 内容重算一字不差、时辰只增不减、走阶次序合法、逐轮结构对——
     * 哪一处对不上即不合格。
     *
     * @param entries 按 seq 升序排好的全链
     */
    public static Leg verifyForward(List<TMgisUseEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Leg.fail(null, "链上一笔都没有，受理那一笔不见了");
        }
        TMgisUseEntry prev = null;
        int expectSeq = 1;
        for (TMgisUseEntry cur : entries) {
            Long at = cur.getSeq() == null ? null : cur.getSeq().longValue();
            if (cur.getSeq() == null || cur.getSeq() != expectSeq) {
                return Leg.fail(at, "第 " + expectSeq + " 笔序号断档或错位，链上少笔或次序错乱");
            }
            if (expectSeq == 1) {
                if (!UseOrderAction.ACCEPT.name().equals(cur.getAction())) {
                    return Leg.fail(at, "头一笔不是受理，来路不正");
                }
                if (!GENESIS.equals(cur.getPrevHash())) {
                    return Leg.fail(at, "受理那一笔的前哈希不是 64 个 0，来路不正");
                }
            } else {
                if (!prev.getHash().equals(cur.getPrevHash())) {
                    return Leg.fail(at, "第 " + expectSeq + " 笔接不住前一笔，链在这里断开");
                }
                if (prev.getPennedAt() != null && cur.getPennedAt() != null
                        && cur.getPennedAt().before(prev.getPennedAt())) {
                    return Leg.fail(at, "第 " + expectSeq + " 笔时辰早于前一笔，疑似事后挪时辰补录");
                }
                if (!UseOrderFlow.legalTransition(prev, cur)) {
                    return Leg.fail(at, "第 " + expectSeq + " 笔走阶次序错乱："
                            + UseOrderFlow.labelOf(prev.getStage()) + " → "
                            + UseOrderFlow.labelOf(cur.getStage()) + " 不合章程");
                }
            }
            if (cur.getHash() == null || !cur.getHash().equals(hashOf(cur))) {
                return Leg.fail(at, "第 " + expectSeq + " 笔内容被改动（哈希对不上），疑似事后人手补录");
            }
            prev = cur;
            expectSeq++;
        }
        return verifyRounds(entries);
    }

    /**
     * 逐轮核结构：轮次自 1 连号；受理只许是全链头一笔；每轮市局阶恰好一枚
     * 通过或否决且排在省厅各笔之前；省厅阶两岗各至多一枚，否决与发回各至多
     * 一枚且互不相容；不是最后一轮就必须以发回收尾（撑出下一轮）；最后一轮
     * 须以放成果（两签必齐）或否决收尾，在办的可停在中途。否决、放成果都须
     * 是全链末笔。
     */
    private static Leg verifyRounds(List<TMgisUseEntry> entries) {
        Map<Integer, List<TMgisUseEntry>> byRound = new LinkedHashMap<Integer, List<TMgisUseEntry>>();
        for (TMgisUseEntry e : entries) {
            int r = e.getRoundNo() == null ? 1 : e.getRoundNo();
            byRound.computeIfAbsent(r, k -> new ArrayList<TMgisUseEntry>()).add(e);
        }
        int expectRound = 1;
        int lastRoundNo = 0;
        for (Integer r : byRound.keySet()) {
            lastRoundNo = r;
        }
        for (Map.Entry<Integer, List<TMgisUseEntry>> bucket : byRound.entrySet()) {
            int round = bucket.getKey();
            boolean lastRound = round == lastRoundNo;
            if (round != expectRound) {
                return Leg.fail(null, "轮次断档：该到第 " + expectRound + " 轮，链上却是第 " + round + " 轮");
            }
            List<TMgisUseEntry> pens = bucket.getValue(); // 已按 seq 升序
            TMgisUseEntry window = null;
            List<TMgisUseEntry> city = new ArrayList<TMgisUseEntry>();
            List<TMgisUseEntry> province = new ArrayList<TMgisUseEntry>();
            List<TMgisUseEntry> issue = new ArrayList<TMgisUseEntry>();
            for (TMgisUseEntry e : pens) {
                UseOrderAction a;
                try {
                    a = UseOrderAction.valueOf(e.getAction());
                } catch (IllegalArgumentException ex) {
                    return Leg.fail(seq(e), "落笔动作不识：" + e.getAction());
                }
                if (a.getStage() == UseOrderStage.WINDOW) {
                    window = e;
                } else if (a.getStage() == UseOrderStage.CITY) {
                    city.add(e);
                } else if (a.getStage() == UseOrderStage.PROVINCE) {
                    province.add(e);
                } else {
                    issue.add(e);
                }
            }
            if (round == 1) {
                if (window == null || entries.get(0) != window) {
                    return Leg.fail(null, "头一笔不是受理，来路不正");
                }
            } else if (window != null) {
                return Leg.fail(seq(window), "受理笔只许有一枚，重办轮里不许再冒受理");
            }
            if (city.size() == 0) {
                // 末轮一枚市局笔都没有：只许是刚受理、件正停在市局等核验的在办局面
                if (!(lastRound && province.isEmpty() && issue.isEmpty() && round == 1 && window != null)) {
                    return Leg.fail(null, "第 " + round + " 轮缺市局核验那一枚，件却走到了后头，来路不正");
                }
            }
            if (city.size() > 1) {
                return Leg.fail(null, "第 " + round + " 轮市局核验至多一枚，链上有 " + city.size() + " 枚");
            }
            if (city.isEmpty()) {
                // 刚受理的在办件：这一轮核到这里收住，合法
                expectRound++;
                continue;
            }
            TMgisUseEntry cityPen = city.get(0);
            UseOrderAction cityAction = UseOrderAction.valueOf(cityPen.getAction());
            // 市局这一枚须排在该轮省厅、放成果各笔之前
            int cityIdx = pens.indexOf(cityPen);
            for (int i = 0; i < cityIdx; i++) {
                if (UseOrderAction.valueOf(pens.get(i).getAction()).getStage() != UseOrderStage.WINDOW) {
                    return Leg.fail(seq(cityPen), "第 " + round + " 轮市局核验之前混进了后面阶的笔，次序错乱");
                }
            }
            for (int i = cityIdx + 1; i < pens.size(); i++) {
                if (UseOrderAction.valueOf(pens.get(i).getAction()).getStage() == UseOrderStage.CITY) {
                    return Leg.fail(seq(cityPen), "第 " + round + " 轮市局核验落了两枚，一枚顶了用，重办须等省厅发回");
                }
            }
            if (cityAction == UseOrderAction.CITY_REJECT) {
                if (!lastRound || !cityPen.equals(entries.get(entries.size() - 1))
                        || !province.isEmpty() || !issue.isEmpty()) {
                    return Leg.fail(seq(cityPen), "市局已写明不予核准，件就此定死：后头一枚都不许有");
                }
                return Leg.ok();
            }
            int secrecy = 0;
            int purpose = 0;
            TMgisUseEntry reject = null;
            TMgisUseEntry back = null;
            boolean terminalSeen = false; // 否决/发回之后同轮不许再有省厅笔
            for (TMgisUseEntry e : province) {
                UseOrderAction a = UseOrderAction.valueOf(e.getAction());
                if (terminalSeen) {
                    return Leg.fail(seq(e), "省厅否决或发回之后，这一轮不许再落笔：废票新票不许同堂并摆");
                }
                if (a == UseOrderAction.SIGN_SECRECY) {
                    secrecy++;
                } else if (a == UseOrderAction.SIGN_PURPOSE) {
                    purpose++;
                } else if (a == UseOrderAction.PROVINCE_REJECT) {
                    reject = e;
                    terminalSeen = true;
                } else if (a == UseOrderAction.RETURN) {
                    back = e;
                    terminalSeen = true;
                }
            }
            if (secrecy > 1 || purpose > 1) {
                return Leg.fail(seq(province), "第 " + round + " 轮省厅同一岗落了两枚：替签、重签不作数");
            }
            if (issue.size() > 1) {
                return Leg.fail(seq(issue), "第 " + round + " 轮放成果落了两枚，一件只放一回");
            }
            if (reject != null) {
                if (!lastRound || !reject.equals(entries.get(entries.size() - 1)) || !issue.isEmpty()) {
                    return Leg.fail(seq(reject), "省厅已写明不予核准，件就此定死：后头一枚都不许有");
                }
                return Leg.ok();
            }
            if (back != null) {
                // 发回轮不许放成果，且必须撑出下一轮（在办件可正停在发回后等市局重核，
                // 那样这一轮本身就是末轮，下面另核）
                if (!issue.isEmpty()) {
                    return Leg.fail(seq(back), "同轮既发回又放成果，两件事不许并在一张票上");
                }
                if (lastRound && entries.get(entries.size() - 1) == back) {
                    // 件正停回市局等重核：合法的在办局面，链到这里收住
                    return Leg.ok();
                }
                if (lastRound) {
                    return Leg.fail(seq(back), "发回市局那一笔之后不见新一轮重核，链没收住");
                }
            } else {
                // 没发回：这一轮必须是末轮，否则等于废票新票同堂
                if (!lastRound) {
                    return Leg.fail(null, "第 " + round + " 轮没经省厅发回却起了新一轮，废票与新票不许同堂并摆");
                }
                if (issue.size() == 1) {
                    // 放成果那一轮，省厅两签必齐；只签一枚点不出放成果
                    if (secrecy != 1 || purpose != 1) {
                        return Leg.fail(seq(issue), "省厅两枚没齐就放成果：只签了一位，件走不到第四步");
                    }
                    if (!issue.get(0).equals(entries.get(entries.size() - 1))) {
                        return Leg.fail(seq(issue), "放成果是末笔，落完即办结，后头不许再有笔");
                    }
                }
                // issue 为 0：在办件，市局过了、省厅签了零到两枚，链停在中途，合法
            }
            expectRound++;
        }
        return Leg.ok();
    }

    /**
     * 倒拉：从末笔沿前一笔哈希念回受理。
     * 每一步的前哈希都须在链上找得到且只找得到一笔，念到头须是受理，
     * 步数须与链上总笔数相合——少一笔即不合格。
     */
    public static Leg verifyBackward(List<TMgisUseEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Leg.fail(null, "链上一笔都没有，无从倒拉");
        }
        Map<String, TMgisUseEntry> byHash = new HashMap<String, TMgisUseEntry>();
        for (TMgisUseEntry e : entries) {
            if (e.getHash() != null && byHash.put(e.getHash(), e) != null) {
                return Leg.fail(e.getSeq() == null ? null : e.getSeq().longValue(),
                        "两笔同一个哈希，链上有鬼");
            }
        }
        TMgisUseEntry cur = entries.get(entries.size() - 1);
        int steps = 1;
        while (!GENESIS.equals(cur.getPrevHash())) {
            TMgisUseEntry prev = byHash.get(cur.getPrevHash());
            if (prev == null) {
                return Leg.fail(cur.getSeq() == null ? null : cur.getSeq().longValue(),
                        "倒拉断在第 " + cur.getSeq() + " 笔：前一笔找不到，链上少笔");
            }
            cur = prev;
            steps++;
        }
        if (!UseOrderAction.ACCEPT.name().equals(cur.getAction())) {
            return Leg.fail(cur.getSeq() == null ? null : cur.getSeq().longValue(),
                    "倒拉念到头不是受理，来路不正");
        }
        if (steps != entries.size()) {
            return Leg.fail(null, "倒拉只念回 " + steps + " 笔，链上共 " + entries.size()
                    + " 笔，有笔不在链上（疑似事后塞入）");
        }
        return Leg.ok();
    }

    /** 落笔时辰一律精确到秒（库面 DATETIME 以秒计，落笔与校验同一口径） */
    static String penText(TMgisUseEntry e) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(e.getPennedAt());
    }

    private static Long seq(TMgisUseEntry e) {
        return e == null || e.getSeq() == null ? null : e.getSeq().longValue();
    }

    /** 一组笔里头一笔的序号（空组返回 null） */
    private static Long seq(List<TMgisUseEntry> list) {
        return list == null || list.isEmpty() ? null : seq(list.get(0));
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不在台上", e);
        }
    }

    /**
     * 一程校验的结论：顺拉一程、倒拉一程，各自回话。
     */
    public static final class Leg {
        private final boolean ok;
        private final Long breakSeq;
        private final String message;

        private Leg(boolean ok, Long breakSeq, String message) {
            this.ok = ok;
            this.breakSeq = breakSeq;
            this.message = message;
        }

        public static Leg ok() {
            return new Leg(true, null, "通");
        }

        public static Leg fail(Long breakSeq, String message) {
            return new Leg(false, breakSeq, message);
        }

        public boolean isOk() {
            return ok;
        }

        /** 断在第几笔（链上序号），不断为 null */
        public Long getBreakSeq() {
            return breakSeq;
        }

        public String getMessage() {
            return message;
        }
    }
}
