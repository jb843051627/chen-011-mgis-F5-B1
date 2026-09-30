package com.fc.v2.common.usebill;

import com.fc.v2.model.auto.TMgisUseBill;
import com.fc.v2.model.auto.TMgisUseBillSign;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 旧「成果提供使用逐级核准单」走层裁判：旧脚手架三法（approve/reject/
 * rollback）的规矩在系统里唯一出处。
 *
 * <p>旧单据两道签批层（{@link #FIRST_NODE}、{@link #LAST_SIGN_NODE}），
 * 走过两层即核讫。每层怎么算过看层上的规矩：</p>
 * <ul>
 *   <li>{@code sign_mode=0} 任一人：本层一张签票即过；</li>
 *   <li>{@code sign_mode=1} 两名点齐：本层须 {@code need_count} 个
 *       <b>不同的人</b>签齐才过——本须联名画押的一档，落了一人不许放行；
 *       同一个人签两回只认头一张，票数不往上涨。</li>
 * </ul>
 *
 * <p><b>票往账上落</b>：签批、打回、退回每一笔都进票籍表，哪位签过、
 * 签的什么意见都查得到。<b>打回即定死</b>：写明不予核准那一下单据置
 * 「已打回」，事后一枚都加不进去。<b>退回清票</b>：退回上一层，被退
 * 那一层的旧票整批销档（票仍留在链上），再上来按新一趟重签，不许拿
 * 旧票凑齐第二遍。</p>
 *
 * <p>本裁判是纯推演，不落库；落账在服务层。与新流程
 * {@link com.fc.v2.common.useorder.UseOrderFlow} 的状态口径一一对应
 * （在办 0 / 已核讫≡已核准 1 / 已打回≡已否决 2），旧单照接得住。</p>
 *
 * @author fuce
 * @date 2026-09-30
 */
public final class LegacyUseBillJudge {

    private LegacyUseBillJudge() {
    }

    /** 在核 */
    public static final int STATUS_RUNNING = 0;
    /** 已核讫（与新流程「已核准」同码同义） */
    public static final int STATUS_PASS = 1;
    /** 已打回（与新流程「已否决」同码同义，落笔定死） */
    public static final int STATUS_VETO = 2;

    /** 任一人核准 */
    public static final int MODE_OR = 0;
    /** 两名点齐（联名画押） */
    public static final int MODE_AND = 1;

    /** 头一道签批层 */
    public static final int FIRST_NODE = 0;
    /** 末道签批层：两层签齐即核讫 */
    public static final int LAST_SIGN_NODE = 1;
    /** 核讫落点：走过末道签批层后挪到这一节点 */
    public static final int DONE_NODE = 2;

    /** 联名档缺省应签人数（need_count 没维护时按两名） */
    public static final int DEFAULT_AND_NEED = 2;

    /**
     * 核单据还收不收笔：已打回定死、已核讫锁栏，都不再收签批、打回、退回。
     */
    public static void checkWritable(TMgisUseBill bill) {
        if (bill == null) {
            throw new LegacyBillException("核准单不在账上");
        }
        Integer status = bill.getStatus();
        if (status != null && status == STATUS_VETO) {
            throw new LegacyBillException("这件已写明不予核准、打回定死：一枚都加不进去，要办另起新件");
        }
        if (status != null && status == STATUS_PASS) {
            throw new LegacyBillException("这件已核讫，各栏锁住：要动另起新件");
        }
    }

    /** 本层应签人数：任一人档认 1；联名档认 need_count（缺省两名） */
    public static int requiredCount(TMgisUseBill bill) {
        int mode = bill.getSignMode() == null ? MODE_OR : bill.getSignMode();
        if (mode == MODE_AND) {
            int need = bill.getNeedCount() == null ? DEFAULT_AND_NEED : bill.getNeedCount();
            return need > 0 ? need : DEFAULT_AND_NEED;
        }
        return 1;
    }

    /** 是不是联名画押档 */
    public static boolean isJointSign(TMgisUseBill bill) {
        return bill.getSignMode() != null && bill.getSignMode() == MODE_AND;
    }

    /**
     * 本层本趟算数的签票（active=1、APPROVE），按签署人去重——
     * 同一个人签两回只算一个人，票取头一张。
     */
    public static List<TMgisUseBillSign> activeSigns(List<TMgisUseBillSign> tickets, int node, int batch) {
        Map<String, TMgisUseBillSign> bySigner = new LinkedHashMap<String, TMgisUseBillSign>();
        if (tickets != null) {
            for (TMgisUseBillSign t : tickets) {
                if (t == null || !LegacyBillAction.APPROVE.name().equals(t.getAction())) {
                    continue;
                }
                if (t.getNodeNo() == null || t.getNodeNo() != node) {
                    continue;
                }
                int b = t.getBatchNo() == null ? 1 : t.getBatchNo();
                if (b != batch || t.getActive() == null || t.getActive() != 1) {
                    continue;
                }
                String signer = t.getSigner() == null ? "" : t.getSigner().trim();
                // 同人多笔只留头一张，保持落笔先后
                if (!bySigner.containsKey(signer)) {
                    bySigner.put(signer, t);
                }
            }
        }
        return new ArrayList<TMgisUseBillSign>(bySigner.values());
    }

    /** 本层本趟这位签过没有（幂等：同一点第二回只认头一张） */
    public static TMgisUseBillSign findSign(List<TMgisUseBillSign> tickets, int node, int batch, String signer) {
        String who = signer == null ? "" : signer.trim();
        for (TMgisUseBillSign t : activeSigns(tickets, node, batch)) {
            if (who.equals(t.getSigner() == null ? "" : t.getSigner().trim())) {
                return t;
            }
        }
        return null;
    }

    /**
     * 签批这一笔落下去会成什么局面（纯推演，不落库）。
     *
     * @return 定局：认头一张（幂等）/ 已记数还没齐 / 走过这一层 / 整件核讫
     */
    public static ApproveVerdict decideApprove(TMgisUseBill bill, List<TMgisUseBillSign> tickets, String signer) {
        checkWritable(bill);
        if (signer == null || signer.trim().isEmpty()) {
            throw new LegacyBillException("签署经办未认：签批人自登录态取，不许代报");
        }
        int node = currentNode(bill);
        int batch = visitBatch(tickets, node);
        TMgisUseBillSign prior = findSign(tickets, node, batch, signer);
        if (prior != null) {
            // 同人同点第二回：只认头一张，一张新票都不立，件也不重挪
            return ApproveVerdict.duplicate(prior, bill.getNodeNo(), bill.getStatus());
        }
        int signed = activeSigns(tickets, node, batch).size() + 1;
        int need = requiredCount(bill);
        if (signed < need) {
            // 联名档还没点齐：件停在本层，签数往账上落
            return ApproveVerdict.counted(signed, need, node, batch);
        }
        // 本层签齐：往下一层挪；走过末道签批层即核讫
        if (node >= LAST_SIGN_NODE) {
            return ApproveVerdict.pass(DONE_NODE, STATUS_PASS, signed, need, node, batch);
        }
        return ApproveVerdict.advance(node + 1, STATUS_RUNNING, signed, need, node, batch);
    }

    /** 当前停在哪一层（老数据 node 空着按头层接） */
    public static int currentNode(TMgisUseBill bill) {
        return bill.getNodeNo() == null ? FIRST_NODE : Math.max(FIRST_NODE, bill.getNodeNo());
    }

    /**
     * 这一层眼下这一趟是第几趟：
     * 层上还有算数签票，就是正签着的这一趟（续趟，两人同趟点齐）；
     * 一张算数票都没有（头回来，或旧票已随退回销档），另起一趟——
     * 趟号取层上最大趟号加一，不许拿旧票凑新趟的额。
     */
    public static int visitBatch(List<TMgisUseBillSign> tickets, int node) {
        int maxBatch = 0;
        boolean activeHere = false;
        if (tickets != null) {
            for (TMgisUseBillSign t : tickets) {
                if (t == null || t.getNodeNo() == null || t.getNodeNo() != node) {
                    continue;
                }
                int b = t.getBatchNo() == null ? 1 : t.getBatchNo();
                if (b > maxBatch) {
                    maxBatch = b;
                }
                if (b == maxBatch && LegacyBillAction.APPROVE.name().equals(t.getAction())
                        && t.getActive() != null && t.getActive() == 1) {
                    activeHere = true;
                }
            }
        }
        if (maxBatch == 0) {
            return 1;
        }
        return activeHere ? maxBatch : maxBatch + 1;
    }

    /** 退回那一笔落在哪一趟：离开层眼下这一趟（有票续趟，无票也记最大趟+1） */
    public static int rollbackBatch(List<TMgisUseBillSign> tickets, int node) {
        int maxBatch = 0;
        if (tickets != null) {
            for (TMgisUseBillSign t : tickets) {
                if (t != null && t.getNodeNo() != null && t.getNodeNo() == node
                        && t.getBatchNo() != null && t.getBatchNo() > maxBatch) {
                    maxBatch = t.getBatchNo();
                }
            }
        }
        return Math.max(1, maxBatch);
    }

    /**
     * 退回上一层：退回后落到哪一层（头层退回停在头层）。
     */
    public static int nodeAfterRollback(TMgisUseBill bill) {
        return Math.max(FIRST_NODE, currentNode(bill) - 1);
    }

    /**
     * 两处口径对账：单据上存的本层已签数，与票籍当场按人重算的须同一个数；
     * 在办件停在哪一层、层上规矩认几人，也都对得上。对不上即库里被人动过。
     */
    public static void requireConsistent(TMgisUseBill bill, List<TMgisUseBillSign> tickets) {
        if (bill == null) {
            throw new LegacyBillException("核准单未指：两本账没法对");
        }
        Integer status = bill.getStatus();
        if (status != null && (status == STATUS_PASS || status == STATUS_VETO)) {
            // 终局件：票籍是定局那一刻的历史，单据签数停在最后一层，不强对
            return;
        }
        // 票籍是后补的账：历年旧单一笔票籍都没有，单据签数是当年的旧记，
        // 无从按人重算，这种老单放行回看（票只立不删，零票只可能是老单）。
        // 一旦有票籍，两本账就得对得齐。
        if (tickets == null || tickets.isEmpty()) {
            return;
        }
        int node = currentNode(bill);
        int batch = visitBatch(tickets, node);
        int recomputed = activeSigns(tickets, node, batch).size();
        int stored = bill.getSignCount() == null ? 0 : bill.getSignCount();
        if (stored != recomputed) {
            throw new LegacyBillException("本层已签人数两处口径对不上：单据上存着 " + stored
                    + " 枚，票籍第 " + batch + " 趟当场按人数出 " + recomputed
                    + " 枚——库里这件被人动过");
        }
        if (isJointSign(bill) && recomputed > requiredCount(bill)) {
            throw new LegacyBillException("本层算数的签票多过应签人数，联名档票数有鬼");
        }
    }

    /**
     * 旧单据挪到新流程四阶口径（兼容用）：
     * 在办头层→市局核验，在办末层→省厅核准，核讫→窗口放成果（已核准），
     * 打回→停在被打回那一层。阶名用新流程的代号，一处对得上旧单。
     */
    public static String newStageOf(TMgisUseBill bill, List<TMgisUseBillSign> tickets) {
        Integer status = bill.getStatus();
        if (status != null && status == STATUS_PASS) {
            return com.fc.v2.common.useorder.UseOrderStage.ISSUE.name();
        }
        int node = currentNode(bill);
        if (status != null && status == STATUS_VETO) {
            return node <= FIRST_NODE
                    ? com.fc.v2.common.useorder.UseOrderStage.CITY.name()
                    : com.fc.v2.common.useorder.UseOrderStage.PROVINCE.name();
        }
        return node <= FIRST_NODE
                ? com.fc.v2.common.useorder.UseOrderStage.CITY.name()
                : com.fc.v2.common.useorder.UseOrderStage.PROVINCE.name();
    }

    /** 签批定局 */
    public static final class ApproveVerdict {
        /** 幂等：同人同点第二回，回的头一张票 */
        private final boolean duplicate;
        /** 记数但没齐，件停本层 */
        private final boolean counted;
        /** 走过本层（含核讫） */
        private final boolean layerPassed;
        /** 整件核讫 */
        private final boolean passed;
        /** 落档后该在的节点 */
        private final int nextNode;
        /** 落档后该在的状态 */
        private final int nextStatus;
        /** 本层这一趟算数签数（含刚落这一笔） */
        private final int signedCount;
        /** 本层应签人数 */
        private final int required;
        /** 本层 */
        private final int node;
        /** 本趟 */
        private final int batch;
        /** 幂等时回的头一张票 */
        private final TMgisUseBillSign firstTicket;

        private ApproveVerdict(boolean duplicate, boolean counted, boolean layerPassed, boolean passed,
                               int nextNode, int nextStatus, int signedCount, int required,
                               int node, int batch, TMgisUseBillSign firstTicket) {
            this.duplicate = duplicate;
            this.counted = counted;
            this.layerPassed = layerPassed;
            this.passed = passed;
            this.nextNode = nextNode;
            this.nextStatus = nextStatus;
            this.signedCount = signedCount;
            this.required = required;
            this.node = node;
            this.batch = batch;
            this.firstTicket = firstTicket;
        }

        static ApproveVerdict duplicate(TMgisUseBillSign first, Integer keepNode, Integer keepStatus) {
            return new ApproveVerdict(true, false, false, false,
                    keepNode == null ? FIRST_NODE : keepNode,
                    keepStatus == null ? STATUS_RUNNING : keepStatus,
                    0, 0, first.getNodeNo() == null ? FIRST_NODE : first.getNodeNo(),
                    first.getBatchNo() == null ? 1 : first.getBatchNo(), first);
        }

        static ApproveVerdict counted(int signed, int need, int node, int batch) {
            return new ApproveVerdict(false, true, false, false, node, STATUS_RUNNING,
                    signed, need, node, batch, null);
        }

        static ApproveVerdict advance(int nextNode, int nextStatus, int signed, int need, int node, int batch) {
            return new ApproveVerdict(false, false, true, false, nextNode, nextStatus,
                    signed, need, node, batch, null);
        }

        static ApproveVerdict pass(int doneNode, int passStatus, int signed, int need, int node, int batch) {
            return new ApproveVerdict(false, false, true, true, doneNode, passStatus,
                    signed, need, node, batch, null);
        }

        public boolean isDuplicate() {
            return duplicate;
        }

        public boolean isCounted() {
            return counted;
        }

        public boolean isLayerPassed() {
            return layerPassed;
        }

        public boolean isPassed() {
            return passed;
        }

        public int getNextNode() {
            return nextNode;
        }

        public int getNextStatus() {
            return nextStatus;
        }

        public int getSignedCount() {
            return signedCount;
        }

        public int getRequired() {
            return required;
        }

        public int getNode() {
            return node;
        }

        public int getBatch() {
            return batch;
        }

        public TMgisUseBillSign getFirstTicket() {
            return firstTicket;
        }
    }
}
