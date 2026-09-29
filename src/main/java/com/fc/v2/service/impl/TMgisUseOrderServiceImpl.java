package com.fc.v2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fc.v2.common.useorder.UseAcceptVerdict;
import com.fc.v2.common.useorder.UseOrderAction;
import com.fc.v2.common.useorder.UseOrderChainJudge;
import com.fc.v2.common.useorder.UseOrderChainReport;
import com.fc.v2.common.useorder.UseOrderFlow;
import com.fc.v2.common.useorder.UseOrderFlowException;
import com.fc.v2.common.useorder.UseOrderJudge;
import com.fc.v2.common.useorder.UseOrderRejectReason;
import com.fc.v2.common.useorder.UseOrderStage;
import com.fc.v2.common.useorder.UseOrderTally;
import com.fc.v2.common.useorder.UseSheetDraft;
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
import com.fc.v2.service.ITMgisUseOrderService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 用密成果领用申请 Service 业务层处理。
 *
 * <p>一阶阶地挪、发回重办、双签点齐、核准锁栏、凭单放成果，章程都在这一层
 * （口径唯一出处在 {@link UseOrderFlow} 与 {@link UseOrderJudge}）；经办、
 * 时辰在落笔那一下由系统写死，替签不作数，事后由不得人回头补录，也不许挪
 * 时辰。签署链只增不改：本层对 {@code t_mgis_use_entry} 只有 insert，
 * 没有 update/delete；图幅行、清单行同理。</p>
 *
 * <p>单据上存的图幅行数、省厅已签数、清单行数都另存一份落账时的数，翻件、
 * 生清单时经 {@link UseOrderTally} 与行表、签署链当场重算对账，少一个就算
 * 这一阶/这一屏没过。</p>
 *
 * @author fuce
 * @date 2026-09-28
 */
@Service
public class TMgisUseOrderServiceImpl implements ITMgisUseOrderService {

    @javax.annotation.Resource
    private TMgisUseOrderMapper mgisUseOrderMapper;

    @javax.annotation.Resource
    private TMgisUseSheetMapper mgisUseSheetMapper;

    @javax.annotation.Resource
    private TMgisUseEntryMapper mgisUseEntryMapper;

    @javax.annotation.Resource
    private TMgisUseClaimMapper mgisUseClaimMapper;

    @javax.annotation.Resource
    private TMgisUseClaimLineMapper mgisUseClaimLineMapper;

    /**
     * 签署：唯一动作门。受理在立件那一下落，其余动作先核件还收不收笔、
     * 再核阶、再落链；同岗同一轮第二回只认头一笔。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UseOrderSignResult sign(UseOrderSignRequest req, String operator) {
        if (req == null) {
            throw new IllegalArgumentException("签署请求为空");
        }
        if (operator == null || operator.trim().isEmpty()) {
            throw new IllegalArgumentException("经办未认：签署人自登录态取，不许请求代报，替签不作数");
        }
        UseOrderAction action = UseOrderAction.require(req.getAction());
        if (action == UseOrderAction.ACCEPT) {
            return doAccept(req, operator.trim());
        }
        if (req.getOrderId() == null) {
            throw new IllegalArgumentException("申请件未指：「" + action.getLabel() + "」须指件主键");
        }
        TMgisUseOrder order = this.mgisUseOrderMapper.selectForUpdate(req.getOrderId());
        // 否决定死、核准锁栏、办结终态，先顶回
        UseOrderFlow.checkWritable(order, action);

        List<TMgisUseEntry> chain = listChain(order.getId());

        // 同岗同一轮第二回来的，只认头一笔落笔的时辰——哪怕件已挪阶，
        // 这一笔也不重落，把头一笔原样回给它
        TMgisUseEntry dup = findAt(chain, action, order.getRoundNo());
        if (dup != null) {
            return new UseOrderSignResult(dup, order, true);
        }

        // 隔着阶去点后面那一阶，不生效
        UseOrderFlow.checkStage(order, action);
        // 替签不作数：另一岗本轮已签，若是同一支笔，顶回——两岗须两个人
        if (action.isProvinceSign()) {
            UseOrderAction other = action == UseOrderAction.SIGN_SECRECY
                    ? UseOrderAction.SIGN_PURPOSE : UseOrderAction.SIGN_SECRECY;
            TMgisUseEntry otherPen = findAt(chain, other, order.getRoundNo());
            if (otherPen != null && operator.trim().equals(otherPen.getPennedBy())) {
                throw new UseOrderFlowException(UseOrderRejectReason.PROXY_SIGN,
                        UseOrderRejectReason.PROXY_SIGN.getLabel()
                                + "（另一岗「" + other.getLabel() + "」已是 " + operator.trim() + " 落的笔）");
            }
        }
        // 这一笔要说的话：两签意见、否决与发回缘由缺了顶回
        String payload = UseOrderFlow.packPayload(action, req);

        TMgisUseEntry entry = new TMgisUseEntry();
        entry.setOrderId(order.getId());
        entry.setSeq(chain.size() + 1);
        entry.setRoundNo(order.getRoundNo());
        entry.setStage(action.getStage().name());
        entry.setAction(action.name());
        entry.setPayload(payload);
        // 经办、时辰由系统在落笔这一下自己写死，不收请求里的话
        entry.setPennedBy(operator.trim());
        entry.setPennedAt(nowSecond());
        entry.setPrevHash(chain.isEmpty() ? UseOrderChainJudge.GENESIS
                : chain.get(chain.size() - 1).getHash());
        // 发回重办：新一轮同岗旧笔退出页面，链上原样留着
        entry.setSupersedesEntryId(previousRoundPen(chain, action, order.getRoundNo()));
        entry.setHash(UseOrderChainJudge.hashOf(entry));
        try {
            this.mgisUseEntryMapper.insert(entry);
        } catch (DuplicateKeyException e) {
            // 并发撞上同一点：唯一键兜底，认头一笔
            throw new UseOrderFlowException("这一阶这一笔已有人落过，只认头一笔；请刷新页面再核");
        }

        moveStage(order, action, chain, entry);
        return new UseOrderSignResult(entry, order, false);
    }

    /**
     * 受理：进门那一张五样齐着才落。系统给单号，受理笔落完件停在市局阶；
     * 图幅逐行落账，只增不改。
     */
    private UseOrderSignResult doAccept(UseOrderSignRequest req, String operator) {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        UseAcceptVerdict verdict = UseOrderJudge.judgeAccept(req, year);

        long orderId = IdWorker.getId();
        TMgisUseOrder order = new TMgisUseOrder();
        order.setId(orderId);
        // 单号系统给，不收请求里的话
        order.setOrderNo(newOrderNo(orderId));
        order.setApplicant(verdict.getApplicant());
        order.setPurpose(verdict.getPurpose());
        // 受理笔落完即停在市局阶，等成果管理科核验
        order.setStage(UseOrderStage.CITY.name());
        order.setRoundNo(1);
        order.setStatus(UseOrderFlow.STATUS_OPEN);
        order.setSheetCount(verdict.getSheetCount());
        order.setProvinceSignCount(0);
        this.mgisUseOrderMapper.insert(order);

        // 图幅逐行落账：行次、代号、密级、出图年份跟着每一行走
        for (UseSheetDraft s : verdict.getSheets()) {
            TMgisUseSheet sheet = new TMgisUseSheet();
            sheet.setOrderId(orderId);
            sheet.setRowNo(s.getRowNo());
            sheet.setSheetCode(s.getSheetCode());
            sheet.setSecretLevel(s.getSecretLevel().name());
            sheet.setMapYear(s.getMapYear());
            this.mgisUseSheetMapper.insert(sheet);
        }

        TMgisUseEntry entry = new TMgisUseEntry();
        entry.setOrderId(orderId);
        entry.setSeq(1);
        entry.setRoundNo(1);
        entry.setStage(UseOrderStage.WINDOW.name());
        entry.setAction(UseOrderAction.ACCEPT.name());
        entry.setPayload(UseOrderFlow.acceptPayload(verdict));
        entry.setPennedBy(operator);
        entry.setPennedAt(nowSecond());
        entry.setPrevHash(UseOrderChainJudge.GENESIS);
        entry.setHash(UseOrderChainJudge.hashOf(entry));
        this.mgisUseEntryMapper.insert(entry);
        return new UseOrderSignResult(entry, order, false);
    }

    /**
     * 核准件一键生成领用清单。只认已核准（或已办结）的件：图幅代号、密级、
     * 出图年份自申请行带，批准文号、申请单位、用途自核准件带，窗口一个字
     * 不经手。一件对一条，重按只回头一遍。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UseClaimView claimList(Long orderId, String operator) {
        if (orderId == null) {
            throw new IllegalArgumentException("核准件未指");
        }
        if (operator == null || operator.trim().isEmpty()) {
            throw new IllegalArgumentException("经办未认");
        }
        TMgisUseOrder order = requireOrder(orderId);
        // 重按是常态：先查清单在不在，在账即把头一张原样回
        TMgisUseClaim existing = findClaimByOrder(orderId);
        if (existing != null) {
            return claimView(existing, true);
        }
        Integer status = order.getStatus();
        if (status == null || (status != UseOrderFlow.STATUS_APPROVED && status != UseOrderFlow.STATUS_DONE)) {
            // 未核准件生不成清单——省厅两枚没齐，批准文号还没有
            throw new UseOrderFlowException(UseOrderRejectReason.NOT_APPROVED,
                    UseOrderRejectReason.NOT_APPROVED.getLabel());
        }
        List<TMgisUseSheet> sheets = listSheets(orderId);
        // 生之前先对图幅行数两处账：对不上是库里被人动过，不许拿糊涂账开清单
        UseOrderTally.requireSheetCount(storedSheetCount(order), sheets.size());

        long claimId = IdWorker.getId();
        TMgisUseClaim claim = new TMgisUseClaim();
        claim.setId(claimId);
        claim.setClaimNo(newClaimNo(claimId));
        claim.setOrderId(orderId);
        claim.setOrderNo(order.getOrderNo());
        claim.setApproveNo(order.getApproveNo());
        claim.setApplicant(order.getApplicant());
        claim.setPurpose(order.getPurpose());
        claim.setLineCount(sheets.size());
        claim.setGeneratedBy(operator.trim());
        claim.setGeneratedAt(nowSecond());
        try {
            this.mgisUseClaimMapper.insert(claim);
        } catch (DuplicateKeyException e) {
            // 并发同按：一件对一条，唯一键兜底，认头一张
            TMgisUseClaim first = requireClaimByOrder(orderId);
            return claimView(first, true);
        }

        // 逐行带：行次、代号、密级、出图年份全自申请行，不誊第二遍
        List<TMgisUseClaimLine> lines = new ArrayList<TMgisUseClaimLine>(sheets.size());
        for (TMgisUseSheet s : sheets) {
            TMgisUseClaimLine line = new TMgisUseClaimLine();
            line.setClaimId(claimId);
            line.setOrderId(orderId);
            line.setRowNo(s.getRowNo());
            line.setSheetCode(s.getSheetCode());
            line.setSecretLevel(s.getSecretLevel());
            line.setMapYear(s.getMapYear());
            this.mgisUseClaimLineMapper.insert(line);
            lines.add(line);
        }
        // 三件行数恒等，差一行这份清单都不能给窗口
        UseOrderTally.requireClaimLines(storedSheetCount(order), claim.getLineCount(), lines.size());
        return new UseClaimView(claim, lines, false);
    }

    @Override
    public TMgisUseOrder selectTMgisUseOrderById(Long id) {
        return this.mgisUseOrderMapper.selectById(id);
    }

    @Override
    public List<TMgisUseOrder> selectTMgisUseOrderList(QueryWrapper<TMgisUseOrder> queryWrapper) {
        return this.mgisUseOrderMapper.selectList(queryWrapper);
    }

    /**
     * 申请单页面：四阶表式，只开眼下阶的口子；每阶只显最新一轮算数的笔。
     * 摆出来之前把图幅行数、省厅签数两处账对一遍，对不上这一屏摆不出来。
     */
    @Override
    public UseOrderPageView pageView(Long orderId) {
        TMgisUseOrder order = requireOrder(orderId);
        List<TMgisUseSheet> sheets = listSheets(order.getId());
        List<TMgisUseEntry> chain = listChain(order.getId());
        // 两处计数对账：页面摆出来的与单据里存下的必须同一个数
        UseOrderTally.requireSheetCount(storedSheetCount(order), sheets.size());
        UseOrderTally.requireProvinceCount(storedProvinceCount(order), chain, currentRound(order));

        Map<String, List<TMgisUseEntry>> page = UseOrderFlow.pageOf(chain, currentRound(order));
        boolean writable = order.getStatus() != null && order.getStatus() == UseOrderFlow.STATUS_OPEN;
        List<UseOrderPageView.StagePage> stages = new ArrayList<UseOrderPageView.StagePage>();
        for (UseOrderStage st : new UseOrderStage[]{UseOrderStage.WINDOW, UseOrderStage.CITY,
                UseOrderStage.PROVINCE, UseOrderStage.ISSUE}) {
            // 件停在哪一阶，页面就只开哪一阶的口子；否决、核准、办结都不再开口子
            boolean open = writable && st.name().equals(order.getStage());
            List<TMgisUseEntry> entries = page.get(st.name());
            stages.add(new UseOrderPageView.StagePage(st.name(), st.getLabel(), open,
                    entries == null ? Collections.<TMgisUseEntry>emptyList() : entries));
        }
        return new UseOrderPageView(order, sheets, order.getStage(),
                UseOrderFlow.statusLabel(order.getStatus()), stages);
    }

    @Override
    public UseOrderChainReport verifyChain(Long orderId) {
        TMgisUseOrder order = requireOrder(orderId);
        List<TMgisUseEntry> chain = listChain(order.getId());
        UseOrderChainJudge.Leg forward = UseOrderChainJudge.verifyForward(chain);
        UseOrderChainJudge.Leg backward = UseOrderChainJudge.verifyBackward(chain);
        return new UseOrderChainReport(chain.size(), forward, backward);
    }

    @Override
    public UseClaimView getClaim(Long orderId) {
        TMgisUseOrder order = requireOrder(orderId);
        TMgisUseClaim claim = requireClaimByOrder(orderId);
        List<TMgisUseClaimLine> lines = listClaimLines(claim.getId());
        UseOrderTally.requireClaimLines(storedSheetCount(order), claim.getLineCount(), lines.size());
        return new UseClaimView(claim, lines, false);
    }

    // ---------- 阶内工序 ----------

    /** 落笔之后挪阶：一阶接一阶；发回则轮次加一、前签一笔勾销；两签齐核准；放成果办结 */
    private void moveStage(TMgisUseOrder order, UseOrderAction action,
                           List<TMgisUseEntry> chain, TMgisUseEntry entry) {
        int round = currentRound(order);
        boolean provinceBothIn = false;
        int provinceSignedNow = 0;
        if (action.isProvinceSign()) {
            // 省厅认两个人：把刚落的这一岗算进去，另一岗本轮也在才算两签齐
            UseOrderAction other = action == UseOrderAction.SIGN_SECRECY
                    ? UseOrderAction.SIGN_PURPOSE : UseOrderAction.SIGN_SECRECY;
            provinceBothIn = findAt(chain, other, round) != null;
            provinceSignedNow = provinceBothIn ? 2 : 1;
        }
        UseOrderStage next = UseOrderFlow.stageAfter(action, provinceBothIn);
        int newRound = round + (UseOrderFlow.isReturn(action) ? 1 : 0);
        // 两签齐即核准：批准文号落笔这一下系统给，各栏锁住
        String approveNo = action.isProvinceSign() && provinceBothIn ? newApproveNo(IdWorker.getId()) : null;

        UpdateWrapper<TMgisUseOrder> uw = new UpdateWrapper<TMgisUseOrder>();
        // 挪阶带旧阶旧轮次在比作比：被人抢先挪过即顶回，隔着阶的动作不生效；
        // 只在在办件上挪，否决、核准（放成果除外）、办结都挪不动
        uw.eq("id", order.getId())
                .eq("stage", order.getStage())
                .eq("round_no", round)
                .eq("status", UseOrderFlow.STATUS_OPEN)
                .set("stage", next.name())
                .set("round_no", newRound);
        if (action.isProvinceSign()) {
            uw.set("province_sign_count", provinceSignedNow);
        }
        if (action == UseOrderAction.RETURN) {
            // 前签一笔勾销：新一轮从市局重攒，落账签数清零（旧笔链上仍在，退出页面）
            uw.set("province_sign_count", 0);
        }
        if (action.isReject()) {
            // 写明不予核准：件就此定死，事后加不进去一枚
            uw.set("status", UseOrderFlow.STATUS_REJECTED);
        }
        if (action.isProvinceSign() && provinceBothIn) {
            // 两签齐即核准：批准文号落笔这一下系统给，各栏锁住
            uw.set("status", UseOrderFlow.STATUS_APPROVED);
            uw.set("approve_no", approveNo);
            uw.set("approved_by", entry.getPennedBy());
            uw.set("approved_at", entry.getPennedAt());
        }
        if (action == UseOrderAction.ISSUE) {
            uw.set("status", UseOrderFlow.STATUS_DONE);
            uw.set("issued_by", entry.getPennedBy());
            uw.set("issued_at", entry.getPennedAt());
        }
        int rows = this.mgisUseOrderMapper.update(null, uw);
        if (rows != 1) {
            throw new UseOrderFlowException("件已被挪动或已定局，请刷新后重来");
        }
        order.setStage(next.name());
        order.setRoundNo(newRound);
        if (action.isProvinceSign()) {
            order.setProvinceSignCount(provinceSignedNow);
        }
        if (action == UseOrderAction.RETURN) {
            order.setProvinceSignCount(0);
        }
        if (action.isReject()) {
            order.setStatus(UseOrderFlow.STATUS_REJECTED);
        }
        if (action.isProvinceSign() && provinceBothIn) {
            order.setStatus(UseOrderFlow.STATUS_APPROVED);
            order.setApproveNo(approveNo);
            order.setApprovedBy(entry.getPennedBy());
            order.setApprovedAt(entry.getPennedAt());
        }
        if (action == UseOrderAction.ISSUE) {
            order.setStatus(UseOrderFlow.STATUS_DONE);
            order.setIssuedBy(entry.getPennedBy());
            order.setIssuedAt(entry.getPennedAt());
        }
    }

    // ---------- 翻账与落笔（私有） ----------

    private TMgisUseOrder requireOrder(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("申请件未指");
        }
        TMgisUseOrder order = this.mgisUseOrderMapper.selectById(orderId);
        if (order == null) {
            throw new UseOrderFlowException(UseOrderRejectReason.ORDER_NOT_FOUND,
                    UseOrderRejectReason.ORDER_NOT_FOUND.getLabel() + "（主键：" + orderId + "）");
        }
        return order;
    }

    private TMgisUseClaim findClaimByOrder(Long orderId) {
        return this.mgisUseClaimMapper.selectOne(new QueryWrapper<TMgisUseClaim>()
                .eq("order_id", orderId));
    }

    private TMgisUseClaim requireClaimByOrder(Long orderId) {
        TMgisUseClaim claim = findClaimByOrder(orderId);
        if (claim == null) {
            throw new UseOrderFlowException("这件还没生成领用清单");
        }
        return claim;
    }

    private UseClaimView claimView(TMgisUseClaim claim, boolean duplicated) {
        return new UseClaimView(claim, listClaimLines(claim.getId()), duplicated);
    }

    /** 全链按落笔先后取出（顺拉倒拉都以此为本） */
    private List<TMgisUseEntry> listChain(Long orderId) {
        return this.mgisUseEntryMapper.selectList(new QueryWrapper<TMgisUseEntry>()
                .eq("order_id", orderId).orderByAsc("seq"));
    }

    private List<TMgisUseSheet> listSheets(Long orderId) {
        return this.mgisUseSheetMapper.selectList(new QueryWrapper<TMgisUseSheet>()
                .eq("order_id", orderId).orderByAsc("row_no"));
    }

    private List<TMgisUseClaimLine> listClaimLines(Long claimId) {
        return this.mgisUseClaimLineMapper.selectList(new QueryWrapper<TMgisUseClaimLine>()
                .eq("claim_id", claimId).orderByAsc("row_no"));
    }

    /** 同一点（同阶同动作同轮次）的那一笔，没有为 null */
    private TMgisUseEntry findAt(List<TMgisUseEntry> chain, UseOrderAction action, Integer roundNo) {
        int round = roundNo == null ? 1 : roundNo;
        for (TMgisUseEntry e : chain) {
            if (action.name().equals(e.getAction())
                    && action.getStage().name().equals(e.getStage())
                    && e.getRoundNo() != null && e.getRoundNo() == round) {
                return e;
            }
        }
        return null;
    }

    /** 上一轮同动作那一笔主键（发回重办顶替用），没有为 null */
    private Long previousRoundPen(List<TMgisUseEntry> chain, UseOrderAction action, Integer roundNo) {
        if (action != UseOrderAction.CITY_PASS
                && action != UseOrderAction.SIGN_SECRECY
                && action != UseOrderAction.SIGN_PURPOSE) {
            return null;
        }
        int round = roundNo == null ? 1 : roundNo;
        Long found = null;
        int foundRound = -1;
        for (TMgisUseEntry e : chain) {
            if (action.name().equals(e.getAction()) && e.getRoundNo() != null && e.getRoundNo() < round
                    && e.getRoundNo() > foundRound) {
                found = e.getId();
                foundRound = e.getRoundNo();
            }
        }
        return found;
    }

    private static int storedSheetCount(TMgisUseOrder order) {
        return order.getSheetCount() == null ? 0 : order.getSheetCount();
    }

    private static int storedProvinceCount(TMgisUseOrder order) {
        return order.getProvinceSignCount() == null ? 0 : order.getProvinceSignCount();
    }

    private static int currentRound(TMgisUseOrder order) {
        return order.getRoundNo() == null ? 1 : order.getRoundNo();
    }

    /** 申请单号系统给号：YM 打头 */
    private static String newOrderNo(long id) {
        return "YM" + id;
    }

    /** 批准文号系统给号：YZ 打头 */
    private static String newApproveNo(long id) {
        return "YZ" + id;
    }

    /** 领用清单号系统给号：QD 打头 */
    private static String newClaimNo(long id) {
        return "QD" + id;
    }

    /** 落笔时辰以秒计：库面 DATETIME 精确到秒，落笔与校验同一口径 */
    private static Date nowSecond() {
        return new Date(System.currentTimeMillis() / 1000L * 1000L);
    }
}
