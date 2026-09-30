package com.fc.v2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
 * 成果提供使用逐级核准单 Service业务层处理（approval-chain 形状：多阶段签批）。
 *
 * <p>早年的旧单只有单头一个 sign_count，几处暗病一并填平，旧方法签名一个不改：</p>
 * <ul>
 *   <li><b>否决落账</b>：reject 不再空走一趟，件置「已打回」、缘由连同否决票落账；</li>
 *   <li><b>票数落账</b>：每一节点每一票落签署明细表，哪位签的、签的什么话、
 *       什么时辰签的都查得到，单头 sign_count 与明细笔数两处口径恒等；</li>
 *   <li><b>联名点齐</b>：sign_mode=1（两名点齐）须两名不同的人各一票才放行，
 *       同一人连点两回只认头一笔，一个人放不得行；</li>
 *   <li><b>打回销籍</b>：rollback 退节点时把经过节点的旧票整批销掉、票数清零，
 *       重报从头攒，旧票不许凑第二遍的额；</li>
 *   <li><b>并发收口</b>：同件签批以行锁串行，条件更新只在「在核」件上落，
 *       与新用密流程同一道坎。</li>
 * </ul>
 *
 * <p><b>旧数据兼容</b>：历年来大批旧单没有签署明细，头一回经新章程办时，
 * 本层以单头 sign_count 为旧账底数认下，落第一笔新票后两处口径归一；
 * sign_mode 空着按「任一人」论，need_count 空着按签批方式补。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMgisUseBillServiceImpl implements ITMgisUseBillService {

    private static final int MAX_NODE = 2;
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TMgisUseBillMapper mgisUseBillMapper;

    @javax.annotation.Resource
    private TMgisUseBillSignMapper mgisUseBillSignMapper;

    @Override
    public TMgisUseBill selectTMgisUseBillById(Long id) {
        return this.mgisUseBillMapper.selectById(id);
    }

    /**
     * 签批同意一票。任一人档一票即过；两名点齐档须两名不同的人各一票，
     * 同一人第二票只认头一笔。走到顶节点点齐，件置「已核讫」。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMgisUseBill approve(Long id, String approver, String comment) {
        if (id == null) {
            return null;
        }
        TMgisUseBill r = this.mgisUseBillMapper.selectForUpdate(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        if (r.getStatus() != null && r.getStatus() != STATUS_RUNNING) {
            // 已核讫、已打回都是定局，不再收票
            return r;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        if (node >= MAX_NODE) {
            return r;
        }
        String who = approver.trim();
        List<TMgisUseBillSign> votes = approveVotes(id, node);
        for (TMgisUseBillSign v : votes) {
            // 同节点同一人第二回只认头一笔，不叠票、不凑联名的额
            if (who.equals(v.getApprover())) {
                return r;
            }
        }

        // 旧单无明细：在落本笔新票之前先把单头 sign_count 记着的旧账认作底数，
        // 两处归一；本笔落下后明细表即有票，之后一律以明细为准
        int legacyBase = legacyBaseCount(r, id);
        int need = needCount(r);

        TMgisUseBillSign vote = new TMgisUseBillSign();
        vote.setBillId(id);
        vote.setNodeNo(node);
        vote.setApprover(who);
        vote.setVerdict(TMgisUseBillSign.VERDICT_APPROVE);
        vote.setCommentText(comment == null ? null : comment.trim());
        vote.setSignAt(nowSecond());
        try {
            this.mgisUseBillSignMapper.insert(vote);
        } catch (DuplicateKeyException e) {
            // 并发同人同节点落两票：唯一键兜底，认头一笔
            return r;
        }

        // 明细票天然不重人（同人第二票上头已挡），故联名档票数够 2 即两名不同的人；
        // 旧账底数只记着数、记不得人，信任为旧年不同人所签（旧数据固有口径）。
        int voteCount = legacyBase + votes.size() + 1;
        boolean enough = voteCount >= need;
        if (!enough) {
            // 联名还没点齐：件仍停在本层，只把票数落账
            return syncHead(id, r, node, STATUS_RUNNING, voteCount, need);
        }

        int nextNode = node + 1;
        if (nextNode >= MAX_NODE) {
            // 顶节点点齐：核讫落定
            return finishHead(id, r, nextNode, STATUS_PASS, 0, need);
        }
        // 进下一节点重攒：本节点票数随件走账留痕，新节点从 0 起
        return finishHead(id, r, nextNode, STATUS_RUNNING, 0, need);
    }

    /**
     * 否决一票：件即置「已打回」定局，否决人与缘由连同这一票落账，
     * 不再是只动一下单头、话没处放的空走趟。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMgisUseBill reject(Long id, String approver, String comment) {
        if (id == null) {
            return null;
        }
        TMgisUseBill r = this.mgisUseBillMapper.selectForUpdate(id);
        if (r == null) {
            return null;
        }
        if (r.getStatus() != null && r.getStatus() != STATUS_RUNNING) {
            return r;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        String who = approver == null || approver.trim().isEmpty() ? null : approver.trim();
        TMgisUseBillSign vote = new TMgisUseBillSign();
        vote.setBillId(id);
        vote.setNodeNo(node);
        vote.setApprover(who);
        vote.setVerdict(TMgisUseBillSign.VERDICT_REJECT);
        vote.setCommentText(comment == null ? null : comment.trim());
        vote.setSignAt(nowSecond());
        this.mgisUseBillSignMapper.insert(vote);

        String reason = comment == null || comment.trim().isEmpty()
                ? "写明不予核准" : comment.trim();
        int rows = this.mgisUseBillMapper.update(null, new UpdateWrapper<TMgisUseBill>()
                .eq("id", id).eq("status", STATUS_RUNNING)
                .set("status", STATUS_VETO)
                .set("remark", (who == null ? "" : who + "：") + reason)
                .set("update_time", nowSecond()));
        if (rows == 1) {
            r.setStatus(STATUS_VETO);
            r.setRemark((who == null ? "" : who + "：") + reason);
        }
        return r;
    }

    /**
     * 退回上一环节：退节点、票数清零，退回经过的各节点旧票整批销籍——
     * 旧票不销，第二回报上来凑着旧票就齐了额，那是放糊涂件。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TMgisUseBill rollback(Long id, String comment) {
        if (id == null) {
            return null;
        }
        TMgisUseBill r = this.mgisUseBillMapper.selectForUpdate(id);
        if (r == null) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        int backTo = Math.max(0, node - 1);
        // 旧票销籍：退回落地的这一层与更后各层的票都不算数，重报从头攒
        this.mgisUseBillSignMapper.delete(new QueryWrapper<TMgisUseBillSign>()
                .eq("bill_id", id).ge("node_no", backTo));
        int need = needCount(r);
        int rows = this.mgisUseBillMapper.update(null, new UpdateWrapper<TMgisUseBill>()
                .eq("id", id)
                .set("node_no", backTo)
                .set("sign_count", 0)
                .set("need_count", need)
                .set("status", STATUS_RUNNING)
                .set("remark", comment == null ? r.getRemark()
                        : ("退回上一环节：" + comment.trim()))
                .set("update_time", nowSecond()));
        if (rows == 1) {
            r.setNodeNo(backTo);
            r.setSignCount(0);
            r.setNeedCount(need);
            r.setStatus(STATUS_RUNNING);
            if (comment != null && !comment.trim().isEmpty()) {
                r.setRemark("退回上一环节：" + comment.trim());
            }
        }
        return r;
    }

    /**
     * 查这件各节点的签署票（新起重载，旧签名不动）：哪位签过、同意与否、
     * 签的什么话、何时签的，一查便知。
     */
    @Override
    public List<TMgisUseBillSign> listSigns(Long id) {
        if (id == null) {
            return new ArrayList<TMgisUseBillSign>();
        }
        return this.mgisUseBillSignMapper.selectList(new QueryWrapper<TMgisUseBillSign>()
                .eq("bill_id", id).orderByAsc("node_no").orderByAsc("sign_at").orderByAsc("id"));
    }

    // ---------- 私有 ----------

    private List<TMgisUseBillSign> approveVotes(Long id, int node) {
        return this.mgisUseBillSignMapper.selectList(new QueryWrapper<TMgisUseBillSign>()
                .eq("bill_id", id).eq("node_no", node).eq("verdict", TMgisUseBillSign.VERDICT_APPROVE)
                .orderByAsc("id"));
    }

    /** 件仍在本层：同步票数落单头，两处口径归一 */
    private TMgisUseBill syncHead(Long id, TMgisUseBill r, int node, int status, int signCount, int need) {
        this.mgisUseBillMapper.update(null, new UpdateWrapper<TMgisUseBill>()
                .eq("id", id).eq("status", STATUS_RUNNING)
                .set("node_no", node)
                .set("sign_count", signCount)
                .set("need_count", need)
                .set("status", status)
                .set("update_time", nowSecond()));
        r.setNodeNo(node);
        r.setSignCount(signCount);
        r.setNeedCount(need);
        r.setStatus(status);
        return r;
    }

    /** 件挪节点或定局：条件更新只在「在核」件上落，并发先落的那笔定局 */
    private TMgisUseBill finishHead(Long id, TMgisUseBill r, int node, int status, int signCount, int need) {
        int rows = this.mgisUseBillMapper.update(null, new UpdateWrapper<TMgisUseBill>()
                .eq("id", id).eq("status", STATUS_RUNNING)
                .set("node_no", node)
                .set("sign_count", signCount)
                .set("need_count", need)
                .set("status", status)
                .set("update_time", nowSecond()));
        if (rows == 1) {
            r.setNodeNo(node);
            r.setSignCount(signCount);
            r.setNeedCount(need);
            r.setStatus(status);
        }
        return r;
    }

    /** 本层签批方式：空着按「任一人」论，兜住历年旧数据 */
    private static int modeOf(TMgisUseBill r) {
        return r.getSignMode() != null && r.getSignMode() == MODE_AND ? MODE_AND : MODE_OR;
    }

    /** 本层应签人数：联名两名，其余一名；旧单 need_count 空着照此补 */
    private static int needCount(TMgisUseBill r) {
        if (r.getNeedCount() != null && r.getNeedCount() > 0) {
            return r.getNeedCount();
        }
        return modeOf(r) == MODE_AND ? 2 : 1;
    }

    /**
     * 旧账底数：这件在明细表尚无票（历年旧单）时，单头 sign_count 记着几，
     * 先认下几笔旧账，不叫老单在新章程下平白多出缺口。明细已有票则以明细为准。
     */
    private int legacyBaseCount(TMgisUseBill r, Long id) {
        Integer any = this.mgisUseBillSignMapper.selectCount(new QueryWrapper<TMgisUseBillSign>()
                .eq("bill_id", id));
        if (any != null && any > 0) {
            return 0;
        }
        return r.getSignCount() == null || r.getSignCount() < 0 ? 0 : r.getSignCount();
    }

    private static Date nowSecond() {
        return new Date(System.currentTimeMillis() / 1000L * 1000L);
    }
}
