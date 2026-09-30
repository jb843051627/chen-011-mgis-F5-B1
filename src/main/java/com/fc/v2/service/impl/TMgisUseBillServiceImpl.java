package com.fc.v2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fc.v2.common.usebill.LegacyBillAction;
import com.fc.v2.common.usebill.LegacyBillException;
import com.fc.v2.common.usebill.LegacyUseBillJudge;
import com.fc.v2.mapper.auto.TMgisUseBillMapper;
import com.fc.v2.mapper.auto.TMgisUseBillSignMapper;
import com.fc.v2.model.auto.TMgisUseBill;
import com.fc.v2.model.auto.TMgisUseBillSign;
import com.fc.v2.service.ITMgisUseBillService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 成果提供使用逐级核准单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * <p>旧脚手架三法的走层、双签、打回、退回章程唯一出处在
 * {@link LegacyUseBillJudge}。早年三法只挪 node_no：打回不落状态、签票
 * 不记账、联名档一人就放行、退回不清旧票，如今一一填平——</p>
 * <ul>
 *   <li><b>票往账上落</b>：签批、打回、退回每一笔进 t_mgis_use_bill_sign，
 *       哪位签过、签的什么意见，翻票籍就查得到；</li>
 *   <li><b>联名点齐</b>：sign_mode=1 的层须不同的人签满 need_count，
 *       同一个人签两回只认头一张，一人不许放行；</li>
 *   <li><b>打回定死</b>：reject 落票即置「已打回」，件定死；</li>
 *   <li><b>退回清票</b>：退回把离开层、退回层的算数旧票整批销档（票留
 *       链上），再上来另起一趟重签，旧票不凑第二遍的额；</li>
 *   <li><b>行锁串行</b>：同一件并发签批在库里 FOR UPDATE 串行。</li>
 * </ul>
 *
 * <p>早年方法签名一仍其旧；带经办人的新意思走新重载。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMgisUseBillServiceImpl implements ITMgisUseBillService {

    /** 旧签名（无经办人）退回/打回落笔的系统署名；新门脸请走带经办人的重载 */
    public static final String SYSTEM_OPERATOR = "系统";

    private final TMgisUseBillMapper mgisUseBillMapper;
    private final TMgisUseBillSignMapper mgisUseBillSignMapper;

    public TMgisUseBillServiceImpl(TMgisUseBillMapper mgisUseBillMapper,
                                   TMgisUseBillSignMapper mgisUseBillSignMapper) {
        this.mgisUseBillMapper = mgisUseBillMapper;
        this.mgisUseBillSignMapper = mgisUseBillSignMapper;
    }

    @Override
    public TMgisUseBill selectTMgisUseBillById(Long id) {
        return this.mgisUseBillMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMgisUseBill approve(Long id, String approver, String comment) {
        if (id == null) {
            return null;
        }
        TMgisUseBill bill = this.mgisUseBillMapper.selectForUpdate(id);
        if (bill == null) {
            return null;
        }
        List<TMgisUseBillSign> tickets = listTickets(id);
        LegacyUseBillJudge.ApproveVerdict verdict =
                LegacyUseBillJudge.decideApprove(bill, tickets, approver);

        if (verdict.isDuplicate()) {
            // 同层同趟同人第二回：只认头一张，不立票、不挪单
            return bill;
        }

        TMgisUseBillSign ticket = newTicket(id, verdict.getNode(), verdict.getBatch(),
                LegacyBillAction.APPROVE, approver.trim(), comment);
        try {
            this.mgisUseBillSignMapper.insert(ticket);
        } catch (DuplicateKeyException e) {
            // 并发同人同点：唯一键兜底，认头一张
            return bill;
        }

        // 挪单带旧层旧状态作比：被人抢先挪过即顶回。老数据这两栏可能为 NULL，
        // 按 IS NULL 对，不擅自当成 0
        UpdateWrapper<TMgisUseBill> uw = new UpdateWrapper<TMgisUseBill>().eq("id", id);
        if (bill.getNodeNo() == null) {
            uw.isNull("node_no");
        } else {
            uw.eq("node_no", bill.getNodeNo());
        }
        if (bill.getStatus() == null) {
            uw.isNull("status");
        } else {
            uw.eq("status", bill.getStatus());
        }
        uw.set("node_no", verdict.getNextNode())
                .set("status", verdict.getNextStatus());
        if (verdict.isLayerPassed()) {
            // 走过这一层：下一层从 0 重新攒
            uw.set("sign_count", 0);
        } else {
            uw.set("sign_count", verdict.getSignedCount());
        }
        if (verdict.isPassed()) {
            uw.set("update_time", new Date());
        }
        int rows = this.mgisUseBillMapper.update(null, uw);
        if (rows != 1) {
            throw new LegacyBillException("单据已被挪动或已定局，请刷新后重来");
        }
        bill.setNodeNo(verdict.getNextNode());
        bill.setStatus(verdict.getNextStatus());
        bill.setSignCount(verdict.isLayerPassed() ? 0 : verdict.getSignedCount());
        return bill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMgisUseBill reject(Long id, String approver, String comment) {
        if (id == null) {
            return null;
        }
        TMgisUseBill bill = this.mgisUseBillMapper.selectForUpdate(id);
        if (bill == null) {
            return null;
        }
        LegacyUseBillJudge.checkWritable(bill);
        int node = LegacyUseBillJudge.currentNode(bill);
        List<TMgisUseBillSign> tickets = listTickets(id);
        int batch = LegacyUseBillJudge.visitBatch(tickets, node);
        String signer = (approver == null || approver.trim().isEmpty()) ? SYSTEM_OPERATOR : approver.trim();

        TMgisUseBillSign ticket = newTicket(id, node, batch, LegacyBillAction.REJECT, signer, comment);
        try {
            this.mgisUseBillSignMapper.insert(ticket);
        } catch (DuplicateKeyException e) {
            // 同人同点已落过打回票：认头一张，单据状态照定死口径再拧一遍即可
        }
        // 写明不予核准：单据脸色就此改成「已打回」，定死
        int rows = this.mgisUseBillMapper.update(null, new UpdateWrapper<TMgisUseBill>()
                .eq("id", id)
                .ne("status", LegacyUseBillJudge.STATUS_VETO)
                .set("status", LegacyUseBillJudge.STATUS_VETO)
                .set("update_time", new Date()));
        if (rows != 1) {
            // 并发两笔打回，后到的认头一张，不算出错
            TMgisUseBill now = this.mgisUseBillMapper.selectById(id);
            if (now == null || now.getStatus() == null
                    || now.getStatus() != LegacyUseBillJudge.STATUS_VETO) {
                throw new LegacyBillException("打回落笔失败，请刷新后重来");
            }
        }
        bill.setStatus(LegacyUseBillJudge.STATUS_VETO);
        return bill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMgisUseBill rollback(Long id, String comment) {
        return rollback(id, SYSTEM_OPERATOR, comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMgisUseBill rollback(Long id, String operator, String comment) {
        if (id == null) {
            return null;
        }
        TMgisUseBill bill = this.mgisUseBillMapper.selectForUpdate(id);
        if (bill == null) {
            return null;
        }
        LegacyUseBillJudge.checkWritable(bill);
        List<TMgisUseBillSign> tickets = listTickets(id);

        int from = LegacyUseBillJudge.currentNode(bill);
        int to = LegacyUseBillJudge.nodeAfterRollback(bill);
        int leaveBatch = LegacyUseBillJudge.rollbackBatch(tickets, from);
        String signer = (operator == null || operator.trim().isEmpty()) ? SYSTEM_OPERATOR : operator.trim();

        // 退回票先落（落在离开层这一趟）。同一点第二回（如头层连着退）撞唯一
        // 键，只认头一张，不重落、不重复销档
        TMgisUseBillSign back = newTicket(id, from, leaveBatch, LegacyBillAction.ROLLBACK, signer, comment);
        boolean backAlready;
        try {
            this.mgisUseBillSignMapper.insert(back);
            backAlready = false;
        } catch (DuplicateKeyException e) {
            backAlready = true;
        }

        // 旧票籍销档：离开层算数签票整批退出；退回层（头层除外，它没更旧的一趟）
        // 的算数签票也销，免得报第二遍时拿旧票凑齐额。票不删，只把 active 置 0。
        vacate(tickets, from);
        if (to != from) {
            vacate(tickets, to);
        }

        int rows = this.mgisUseBillMapper.update(null, new UpdateWrapper<TMgisUseBill>()
                .eq("id", id)
                .eq("node_no", from)
                .set("node_no", to)
                .set("sign_count", 0)
                .set("status", LegacyUseBillJudge.STATUS_RUNNING)
                .set("update_time", new Date()));
        if (rows != 1) {
            throw new LegacyBillException("单据已被挪动，请刷新后重来");
        }
        bill.setNodeNo(to);
        bill.setSignCount(0);
        bill.setStatus(LegacyUseBillJudge.STATUS_RUNNING);
        return bill;
    }

    // ---------- 票籍 ----------

    /** 一件的票籍（按下笔先后） */
    public List<TMgisUseBillSign> listSigns(Long id) {
        return listTickets(id);
    }

    /** 翻件前对两本账：单据签数与票籍当场重算一致才摆得出 */
    public TMgisUseBill requireConsistent(Long id) {
        TMgisUseBill bill = this.mgisUseBillMapper.selectById(id);
        if (bill == null) {
            throw new LegacyBillException("核准单不在账上：" + id);
        }
        LegacyUseBillJudge.requireConsistent(bill, listTickets(id));
        return bill;
    }

    @Override
    public com.fc.v2.model.custom.LegacyBillView viewByNewRules(Long id) {
        TMgisUseBill bill = requireConsistent(id);
        List<TMgisUseBillSign> tickets = listTickets(id);
        String stage = LegacyUseBillJudge.newStageOf(bill, tickets);
        // 状态两码本就同一个讲法：0 在办、1 已核准、2 已否决（旧叫已打回）
        String label = com.fc.v2.common.useorder.UseOrderFlow.statusLabel(bill.getStatus());
        return new com.fc.v2.model.custom.LegacyBillView(bill, stage, label, tickets);
    }

    private List<TMgisUseBillSign> listTickets(Long billId) {
        List<TMgisUseBillSign> all = this.mgisUseBillSignMapper.selectList(
                new QueryWrapper<TMgisUseBillSign>().eq("bill_id", billId).orderByAsc("id"));
        return all == null ? new ArrayList<TMgisUseBillSign>() : all;
    }

    /** 该层所有算数签票销档（active 置 0） */
    private void vacate(List<TMgisUseBillSign> tickets, int node) {
        for (TMgisUseBillSign t : tickets) {
            if (t.getNodeNo() != null && t.getNodeNo() == node
                    && LegacyBillAction.APPROVE.name().equals(t.getAction())
                    && t.getActive() != null && t.getActive() == 1) {
                this.mgisUseBillSignMapper.update(null, new UpdateWrapper<TMgisUseBillSign>()
                        .eq("id", t.getId()).eq("active", 1).set("active", 0));
                t.setActive(0);
            }
        }
    }

    private TMgisUseBillSign newTicket(Long billId, int node, int batch, LegacyBillAction action,
                                       String signer, String comment) {
        TMgisUseBillSign t = new TMgisUseBillSign();
        t.setId(IdWorker.getId());
        t.setBillId(billId);
        t.setNodeNo(node);
        t.setBatchNo(batch);
        t.setAction(action.name());
        t.setSigner(signer);
        t.setComment(comment);
        t.setActive(1);
        return t;
    }
}
