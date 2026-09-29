package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.scopecard.ScopeCardAcceptException;
import com.fc.v2.common.scopecard.ScopeCardJudge;
import com.fc.v2.common.scopecard.ScopeCardProcedure;
import com.fc.v2.common.scopecard.ScopeCardRejectReason;
import com.fc.v2.common.scopecard.ScopeCardTally;
import com.fc.v2.common.scopecard.ScopeCardVerdict;
import com.fc.v2.common.scopecard.ScopeGradeJudge;
import com.fc.v2.common.support.ConvertUtil;
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
import com.fc.v2.service.ITMgisScopeCardService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 作业范围标签建档卡Service业务层处理
 *
 * <p>进门只认「登记」一处：立卡、核对、定档、类别增减四道手续的章程
 * 唯一出处在 {@link ScopeCardJudge}，层级档折算唯一出处在
 * {@link ScopeGradeJudge}，看账两口径唯一出处在 {@link ScopeCardTally}。
 * 本层不另写一套。</p>
 *
 * <p>创建人、经手人两张名单各记各的：创建人在立卡那一下写死（更新路径
 * 一律不碰 create_by），经手人每过一道手续在落笔链上记一条——落笔链
 * 只增不改，本层对 {@code t_mgis_scope_card_entry} 只有 insert/select，
 * 没有 update/delete。</p>
 *
 * <p>前六个方法是早年脚手架签名，一仍其旧封存不动；登记门、看账、
 * 属地可见全走后面的新方法。</p>
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TMgisScopeCardServiceImpl extends ServiceImpl<TMgisScopeCardMapper, TMgisScopeCard> implements ITMgisScopeCardService {

    @Autowired
    private TMgisUnitDocMapper mgisUnitDocMapper;

    @javax.annotation.Resource
    private TMgisScopeCategoryMapper mgisScopeCategoryMapper;

    @javax.annotation.Resource
    private TMgisScopeCardItemMapper mgisScopeCardItemMapper;

    @javax.annotation.Resource
    private TMgisScopeCardEntryMapper mgisScopeCardEntryMapper;

    // ---------------- 旧章：脚手架签名一仍其旧（封存，门脸不再调用） ----------------

    @Override
    public TMgisScopeCard selectTMgisScopeCardById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TMgisScopeCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TMgisScopeCard> selectTMgisScopeCardList(Wrapper<TMgisScopeCard> queryWrapper) {
        QueryWrapper<TMgisScopeCard> wrapper = new QueryWrapper<TMgisScopeCard>();
        com.github.pagehelper.PageHelper.startPage(1, 10);
        wrapper.eq("status", 0);
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public int insertTMgisScopeCard(TMgisScopeCard record) {
        if (record == null) {
            return 0;
        }

        record.setCreateBy(record.getBillNo());
        TMgisUnitDoc refArch = mgisUnitDocMapper.selectOne(new QueryWrapper<TMgisUnitDoc>()
                .eq("id", record.getSiteId()).eq("del_flag", 0));
        if (refArch == null) {
            return 0;
        }
        if (refArch.getStatus() != null && refArch.getStatus() == 1) {
            return 0;
        }
        record.setSiteNo(refArch.getSiteNo());
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TMgisScopeCard>()
                    .eq("bill_no", record.getBillNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        TMgisUnitDoc bandArch = mgisUnitDocMapper.selectById(record.getSiteId());
        BigDecimal bandVal = record.getQty();
        int bandLevel = 0;
        if (bandVal != null && bandArch != null) {
            if (bandVal.compareTo(bandArch.getTh1Max()) <= 0) {
                bandLevel = 1;
            } else if (bandVal.compareTo(bandArch.getTh2Max()) <= 0) {
                bandLevel = 2;
            } else if (bandVal.compareTo(bandArch.getTh3Max()) <= 0) {
                bandLevel = 3;
            } else {
                bandLevel = 4;
            }
        }
        record.setGradeLevel(BigDecimal.valueOf(bandLevel));

        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTMgisScopeCard(TMgisScopeCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }

        if (record.getId() != null && StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TMgisScopeCard>()
                    .eq("bill_no", record.getBillNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TMgisScopeCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int deleteTMgisScopeCardByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTMgisScopeCardById(Long id) {
        return this.baseMapper.deleteById(id);
    }

    // ---------------- 新章：登记门（唯一写入门） ----------------

    /**
     * 登记。立卡先认来文单号：同一单号第二遍递进来，不管这一遍递成什么样，
     * 头一张原样回——市局抄送与单位自报并成一条，账上不许留两笔。
     * 头一遍才推演落账：系统给卡号、创建人写死、经手人链落第一笔。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScopeCardRegisterResult register(ScopeCardRegisterRequest req, String operator) {
        if (operator == null || operator.trim().isEmpty()) {
            throw new IllegalArgumentException("经办未认：经手人、创建人都自登录态取，不许请求代报");
        }
        ScopeCardProcedure proc = ScopeCardProcedure.require(req == null ? null : req.getProcedure());
        if (proc == ScopeCardProcedure.REGISTER) {
            return doRegister(req, operator.trim());
        }
        return doProceed(req, proc, operator.trim());
    }

    /** 立卡：对档案、核类别、核挂用类别数，系统给号，创建人、经手人头一笔落笔 */
    private ScopeCardRegisterResult doRegister(ScopeCardRegisterRequest req, String operator) {
        // 重递是常态：先看来文单号在不在账，在账即并一条，头一张原样回
        String claimedNo = trim(req.getSourceBillNo());
        if (claimedNo != null) {
            TMgisScopeCard existing = findBySourceBillNo(claimedNo);
            if (existing != null) {
                return new ScopeCardRegisterResult(existing, firstEntryOf(existing.getId()), true);
            }
        }

        TMgisUnitDoc siteDoc = claimedSiteDoc(req.getSiteNo());
        TMgisScopeCard dupScope = findBySiteScope(trim(req.getSiteNo()), trim(req.getScopeSeg()));
        // 立卡核成什么局面，但凭系统推演为定——哪一栏犯了规矩，整单顶回
        ScopeCardVerdict verdict = ScopeCardJudge.judgeRegister(req, siteDoc, dupScope, categoryIndex());

        long cardId = IdWorker.getId();
        TMgisScopeCard card = new TMgisScopeCard();
        card.setId(cardId);
        // 标签卡号由系统给号，不收请求里的话
        card.setBillNo(newBillNo());
        card.setSourceBillNo(verdict.getSourceBillNo());
        card.setSourceFrom(verdict.getSourceFrom().name());
        card.setSiteId(verdict.getSiteId());
        card.setSiteNo(verdict.getSiteNo());
        card.setScopeSeg(verdict.getScopeSeg());
        card.setQty(verdict.getQty());
        card.setFineAmt(verdict.getFineAmt());
        // 层级档系统折算落库，不许人手填
        card.setGradeLevel(verdict.getGradeLevel());
        card.setStatus(ScopeCardJudge.STATUS_PENDING);
        card.setDelFlag(0);
        // 创建人：按下立卡那一下是谁就是谁，后头谁重录也挪不走
        card.setCreateBy(operator);
        try {
            this.baseMapper.insert(card);
        } catch (DuplicateKeyException e) {
            // 并发同递：同一单号并一条认头一张；同单位同范围段第二张顶回
            TMgisScopeCard first = findBySourceBillNo(verdict.getSourceBillNo());
            if (first != null) {
                return new ScopeCardRegisterResult(first, firstEntryOf(first.getId()), true);
            }
            TMgisScopeCard clash = findBySiteScope(verdict.getSiteNo(), verdict.getScopeSeg());
            if (clash != null) {
                throw new ScopeCardAcceptException(ScopeCardRejectReason.DUPLICATE_SCOPE,
                        ScopeCardRejectReason.DUPLICATE_SCOPE.getLabel()
                                + "（已有卡号：" + clash.getBillNo() + "，范围段：" + verdict.getScopeSeg() + "）");
            }
            throw e;
        }

        // 在挂类别：一张卡挂了哪几末级，逐条落账
        for (String code : verdict.getCategories()) {
            TMgisScopeCardItem item = new TMgisScopeCardItem();
            item.setCardId(cardId);
            item.setCateCode(code);
            this.mgisScopeCardItemMapper.insert(item);
        }

        // 经手人链头一笔：立卡
        TMgisScopeCardEntry entry = pen(cardId, ScopeCardProcedure.REGISTER,
                "立卡：来文单号 " + verdict.getSourceBillNo() + "（" + verdict.getSourceFrom().getLabel()
                        + "），范围段 " + verdict.getScopeSeg() + "，挂用类别数 " + verdict.getQty().toPlainString()
                        + " 项，层级档 " + verdict.getGradeLevel().toPlainString()
                        + appendRemark(req.getRemark()), operator);
        return new ScopeCardRegisterResult(card, entry, false);
    }

    /** 核对、定档、类别增减：卡停在哪段进展就只开哪道手续的口子 */
    private ScopeCardRegisterResult doProceed(ScopeCardRegisterRequest req, ScopeCardProcedure proc, String operator) {
        if (req.getCardId() == null) {
            throw new IllegalArgumentException("卡未指：「" + proc.getLabel() + "」须指卡主键");
        }
        TMgisScopeCard card = requireCard(req.getCardId());
        TMgisUnitDoc siteDoc = claimedSiteDoc(card.getSiteNo());
        if (siteDoc == null) {
            // 档案对不上：出处都没了，手续没法走
            throw new ScopeCardAcceptException(ScopeCardRejectReason.SITE_DOC_NOT_FOUND,
                    ScopeCardRejectReason.SITE_DOC_NOT_FOUND.getLabel() + "（编号：" + card.getSiteNo() + "）");
        }
        // 档案已注销：老卡照旧查得到，但不能从老卡上接着添东西
        ScopeCardJudge.checkDocWritable(siteDoc);
        // 隔着站落笔当场顶回，不生效
        ScopeCardJudge.checkStation(card, proc);

        if (proc == ScopeCardProcedure.ADJUST) {
            return doAdjust(req, card, siteDoc, operator);
        }

        int expect = proc == ScopeCardProcedure.CHECK ? ScopeCardJudge.STATUS_PENDING : ScopeCardJudge.STATUS_CHECKED;
        int next = proc == ScopeCardProcedure.CHECK ? ScopeCardJudge.STATUS_CHECKED : ScopeCardJudge.STATUS_GRADED;
        UpdateWrapper<TMgisScopeCard> uw = new UpdateWrapper<TMgisScopeCard>()
                .eq("id", card.getId()).eq("status", expect).eq("del_flag", 0)
                .set("status", next)
                .set("update_by", operator)
                .set("update_time", new Date());
        String payload;
        if (proc == ScopeCardProcedure.GRADE) {
            // 定档：层级档拿眼下的挂用类别数与档案顶线再折算一次落库
            BigDecimal grade = ScopeGradeJudge.gradeOf(card.getQty(),
                    siteDoc.getTh1Max(), siteDoc.getTh2Max(), siteDoc.getTh3Max());
            uw.set("grade_level", grade);
            payload = "定档：层级档 " + grade.toPlainString();
        } else {
            payload = "核对";
        }
        int rows = this.baseMapper.update(null, uw);
        if (rows == 0) {
            // 并发撞上同一道手续：头一笔已挪站，后到的隔着站，顶回不生效
            throw new ScopeCardAcceptException(ScopeCardRejectReason.BAD_STATION,
                    ScopeCardRejectReason.BAD_STATION.getLabel() + "：卡眼下进展已挪，「" + proc.getLabel()
                            + "」那一道走不到，刷新后再看");
        }
        TMgisScopeCardEntry entry = pen(card.getId(), proc, payload + appendRemark(req.getRemark()), operator);
        return new ScopeCardRegisterResult(requireCard(card.getId()), entry, false);
    }

    /** 类别增减：在挂类别整批换一遍，挂用类别数、层级档跟着重折落库 */
    private ScopeCardRegisterResult doAdjust(ScopeCardRegisterRequest req, TMgisScopeCard card,
                                             TMgisUnitDoc siteDoc, String operator) {
        ScopeCardVerdict verdict = ScopeCardJudge.judgeAdjust(req, siteDoc, categoryIndex());

        // 整批替换：旧清单退出，新清单落账；换下来的清单在经手人链那一笔里留话
        this.mgisScopeCardItemMapper.delete(new QueryWrapper<TMgisScopeCardItem>().eq("card_id", card.getId()));
        for (String code : verdict.getCategories()) {
            TMgisScopeCardItem item = new TMgisScopeCardItem();
            item.setCardId(card.getId());
            item.setCateCode(code);
            this.mgisScopeCardItemMapper.insert(item);
        }

        UpdateWrapper<TMgisScopeCard> uw = new UpdateWrapper<TMgisScopeCard>()
                .eq("id", card.getId()).eq("del_flag", 0)
                .set("qty", verdict.getQty())
                .set("grade_level", verdict.getGradeLevel())
                .set("update_by", operator)
                .set("update_time", new Date());
        if (verdict.getFineAmt() != null) {
            uw.set("fine_amt", verdict.getFineAmt());
        }
        this.baseMapper.update(null, uw);

        StringBuilder payload = new StringBuilder("类别增减：在挂 ");
        payload.append(String.join(",", verdict.getCategories()))
                .append("，挂用类别数 ").append(verdict.getQty().toPlainString())
                .append(" 项，层级档 ").append(verdict.getGradeLevel().toPlainString());
        TMgisScopeCardEntry entry = pen(card.getId(), ScopeCardProcedure.ADJUST,
                cut(payload.append(appendRemark(req.getRemark())).toString(), 500), operator);
        return new ScopeCardRegisterResult(requireCard(card.getId()), entry, false);
    }

    // ---------------- 新章：卡页面与看账 ----------------

    /**
     * 卡页面。层级档不当卡上的死数回：当场拿眼下的挂用类别数与档案顶线
     * 重折一遍，与落库那一份并列——类别有增减、单位改了属地，这一档
     * 都得跟着重新折算，只在立卡那天折一次是远远不够的。
     */
    @Override
    public ScopeCardView cardView(Long id, String viewerCity) {
        TMgisScopeCard card = requireCard(id);
        TMgisUnitDoc siteDoc = claimedSiteDoc(card.getSiteNo());
        requireVisible(siteDoc, viewerCity);

        List<TMgisScopeCardItem> items = this.mgisScopeCardItemMapper.selectList(
                new QueryWrapper<TMgisScopeCardItem>().eq("card_id", card.getId()).orderByAsc("id"));
        Map<String, TMgisScopeCategory> index = categoryIndex();
        List<TMgisScopeCategory> categories = new ArrayList<TMgisScopeCategory>(items.size());
        for (TMgisScopeCardItem item : items) {
            TMgisScopeCategory cate = index.get(item.getCateCode());
            if (cate != null) {
                categories.add(cate);
            }
        }
        List<TMgisScopeCardEntry> entries = listEntries(card.getId());

        BigDecimal currentGrade = null;
        boolean gradeConsistent = false;
        if (siteDoc != null && card.getQty() != null) {
            try {
                currentGrade = ScopeGradeJudge.gradeOf(card.getQty(),
                        siteDoc.getTh1Max(), siteDoc.getTh2Max(), siteDoc.getTh3Max());
                gradeConsistent = card.getGradeLevel() != null
                        && currentGrade.compareTo(card.getGradeLevel()) == 0;
            } catch (ScopeCardAcceptException e) {
                // 顶线未维护齐、挂用类别数越过新顶线：当场重折不出来，照实空着
                currentGrade = null;
            }
        }
        return new ScopeCardView(card, siteDoc, categories, entries, currentGrade, gradeConsistent);
    }

    @Override
    public List<TMgisScopeCard> selectVisibleTMgisScopeCardList(QueryWrapper<TMgisScopeCard> queryWrapper, String viewerCity) {
        QueryWrapper<TMgisScopeCard> wrapper = queryWrapper == null ? new QueryWrapper<TMgisScopeCard>() : queryWrapper;
        wrapper.eq("del_flag", 0);
        if (!applyVisibility(wrapper, viewerCity)) {
            return new ArrayList<TMgisScopeCard>();
        }
        return this.baseMapper.selectList(wrapper);
    }

    /**
     * 看账·省局口径。两条路数出来的卡数必须一致：同一可见范围内，
     * 省局口径按单位归堆、属地口径按区县归堆同出一处计算，
     * 差一笔就算这一屏没过。
     */
    @Override
    public ScopeCardTallyView tallyByProvince(String viewerCity) {
        ScopeCardTally tally = tallyOfVisible(viewerCity);
        return new ScopeCardTallyView("PROVINCE", tally.getTotalCards(), tally.getProvinceRows());
    }

    @Override
    public ScopeCardTallyView tallyByRegion(String viewerCity) {
        ScopeCardTally tally = tallyOfVisible(viewerCity);
        return new ScopeCardTallyView("REGION", tally.getTotalCards(), tally.getRegionRows());
    }

    @Override
    public List<TMgisScopeCategory> listScopeCategories() {
        return this.mgisScopeCategoryMapper.selectList(new QueryWrapper<TMgisScopeCategory>()
                .eq("del_flag", 0).orderByAsc("cate_code"));
    }

    /**
     * 档案顶线、属地变了，名下各卡层级档跟着重新折算落库。
     * 挂用类别数越过新顶线的卡不重折（留原档待人处置），返回重折笔数。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recalcGradesOfSite(String siteNo) {
        String no = trim(siteNo);
        if (no == null) {
            throw new IllegalArgumentException("资质单位编号未指");
        }
        TMgisUnitDoc siteDoc = claimedSiteDoc(no);
        if (siteDoc == null) {
            throw new ScopeCardAcceptException(ScopeCardRejectReason.SITE_DOC_NOT_FOUND,
                    ScopeCardRejectReason.SITE_DOC_NOT_FOUND.getLabel() + "（填报原值：" + no + "）");
        }
        // 顶线未维护齐没法折算，整批不动
        ScopeGradeJudge.requireCeilings(siteDoc.getTh1Max(), siteDoc.getTh2Max(), siteDoc.getTh3Max());
        List<TMgisScopeCard> cards = this.baseMapper.selectList(new QueryWrapper<TMgisScopeCard>()
                .eq("site_no", no).eq("del_flag", 0));
        int folded = 0;
        for (TMgisScopeCard card : cards) {
            BigDecimal grade;
            try {
                grade = ScopeGradeJudge.gradeOf(card.getQty(),
                        siteDoc.getTh1Max(), siteDoc.getTh2Max(), siteDoc.getTh3Max());
            } catch (ScopeCardAcceptException e) {
                // 越过新顶线的卡留原档待人处置，不拦整批
                continue;
            }
            this.baseMapper.update(null, new UpdateWrapper<TMgisScopeCard>()
                    .eq("id", card.getId()).eq("del_flag", 0)
                    .set("grade_level", grade)
                    .set("update_time", new Date()));
            folded++;
        }
        return folded;
    }

    // ---------------- 翻账与落笔（私有） ----------------

    /** 同一可见范围内的两口径看账，一致性在这一处把门 */
    private ScopeCardTally tallyOfVisible(String viewerCity) {
        QueryWrapper<TMgisScopeCard> wrapper = new QueryWrapper<TMgisScopeCard>().eq("del_flag", 0);
        List<TMgisScopeCard> cards = applyVisibility(wrapper, viewerCity)
                ? this.baseMapper.selectList(wrapper) : new ArrayList<TMgisScopeCard>();
        Map<String, TMgisUnitDoc> docs = new LinkedHashMap<String, TMgisUnitDoc>();
        for (TMgisUnitDoc doc : this.mgisUnitDocMapper.selectList(
                new QueryWrapper<TMgisUnitDoc>().eq("del_flag", 0))) {
            if (doc.getSiteNo() != null) {
                docs.put(doc.getSiteNo().trim(), doc);
            }
        }
        ScopeCardTally tally = ScopeCardTally.tally(cards, docs);
        // 差一笔就算这一屏没过
        tally.requireConsistent();
        return tally;
    }

    /**
     * 属地可见：看账人是本市的，只看得见本市单位的卡；省局（viewerCity
     * 为空）不加这道箍。本市一个单位也没有时返回 false——查出来必是空，
     * 不许把箍摘了全省都漏出去。
     */
    private boolean applyVisibility(QueryWrapper<TMgisScopeCard> wrapper, String viewerCity) {
        String city = trim(viewerCity);
        if (city == null) {
            return true;
        }
        List<String> siteNos = siteNosOfCity(city);
        if (siteNos.isEmpty()) {
            return false;
        }
        wrapper.in("site_no", siteNos);
        return true;
    }

    /** 本市（注册地市段同名）单位的编号集 */
    private List<String> siteNosOfCity(String city) {
        List<TMgisUnitDoc> docs = this.mgisUnitDocMapper.selectList(
                new QueryWrapper<TMgisUnitDoc>().eq("del_flag", 0));
        List<String> siteNos = new ArrayList<String>();
        for (TMgisUnitDoc doc : docs) {
            if (city.equals(ScopeCardTally.cityOf(doc.getRoadName())) && doc.getSiteNo() != null) {
                siteNos.add(doc.getSiteNo().trim());
            }
        }
        return siteNos;
    }

    /** 属地只看得见本市：对不上本市的卡，当不在账上回，不泄露存在性 */
    private void requireVisible(TMgisUnitDoc siteDoc, String viewerCity) {
        String city = trim(viewerCity);
        if (city == null) {
            return;
        }
        if (siteDoc == null || !city.equals(ScopeCardTally.cityOf(siteDoc.getRoadName()))) {
            throw new ScopeCardAcceptException(ScopeCardRejectReason.CARD_NOT_FOUND,
                    ScopeCardRejectReason.CARD_NOT_FOUND.getLabel());
        }
    }

    private TMgisScopeCard requireCard(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("卡未指");
        }
        TMgisScopeCard card = this.baseMapper.selectOne(new QueryWrapper<TMgisScopeCard>()
                .eq("id", id).eq("del_flag", 0));
        if (card == null) {
            throw new ScopeCardAcceptException(ScopeCardRejectReason.CARD_NOT_FOUND,
                    ScopeCardRejectReason.CARD_NOT_FOUND.getLabel() + "（主键：" + id + "）");
        }
        return card;
    }

    /** 按资质单位编号对档案；编号空着、对不上都回 null，由裁判顶回 */
    private TMgisUnitDoc claimedSiteDoc(String siteNo) {
        String no = trim(siteNo);
        if (no == null) {
            return null;
        }
        return this.mgisUnitDocMapper.selectOne(new QueryWrapper<TMgisUnitDoc>()
                .eq("site_no", no).eq("del_flag", 0));
    }

    private TMgisScopeCard findBySourceBillNo(String sourceBillNo) {
        String no = trim(sourceBillNo);
        if (no == null) {
            return null;
        }
        return this.baseMapper.selectOne(new QueryWrapper<TMgisScopeCard>()
                .eq("source_bill_no", no).eq("del_flag", 0));
    }

    private TMgisScopeCard findBySiteScope(String siteNo, String scopeSeg) {
        if (siteNo == null || scopeSeg == null) {
            return null;
        }
        return this.baseMapper.selectOne(new QueryWrapper<TMgisScopeCard>()
                .eq("site_no", siteNo).eq("scope_seg", scopeSeg).eq("del_flag", 0));
    }

    /** 专业类别目录：代号 → 目录行（只装现行在册的） */
    private Map<String, TMgisScopeCategory> categoryIndex() {
        Map<String, TMgisScopeCategory> index = new LinkedHashMap<String, TMgisScopeCategory>();
        for (TMgisScopeCategory cate : this.mgisScopeCategoryMapper.selectList(
                new QueryWrapper<TMgisScopeCategory>().eq("del_flag", 0))) {
            if (cate.getCateCode() != null) {
                index.put(cate.getCateCode().trim(), cate);
            }
        }
        return index;
    }

    private List<TMgisScopeCardEntry> listEntries(Long cardId) {
        return this.mgisScopeCardEntryMapper.selectList(new QueryWrapper<TMgisScopeCardEntry>()
                .eq("card_id", cardId).orderByAsc("seq"));
    }

    private TMgisScopeCardEntry firstEntryOf(Long cardId) {
        List<TMgisScopeCardEntry> entries = this.mgisScopeCardEntryMapper.selectList(
                new QueryWrapper<TMgisScopeCardEntry>().eq("card_id", cardId).orderByAsc("seq").last("limit 1"));
        return entries.isEmpty() ? null : entries.get(0);
    }

    /**
     * 经手人落笔：每过一道手续记一条。经手人、落笔时辰在这一下系统写死，
     * 链上只增不改——与卡上的创建人各记各的，两个位置不许互相顶。
     */
    private TMgisScopeCardEntry pen(Long cardId, ScopeCardProcedure proc, String payload, String operator) {
        Integer seq = this.mgisScopeCardEntryMapper.selectCount(
                new QueryWrapper<TMgisScopeCardEntry>().eq("card_id", cardId));
        TMgisScopeCardEntry entry = new TMgisScopeCardEntry();
        entry.setCardId(cardId);
        entry.setSeq((seq == null ? 0 : seq) + 1);
        entry.setProc(proc.name());
        entry.setPayload(cut(payload, 500));
        entry.setPennedBy(operator);
        entry.setPennedAt(nowSecond());
        this.mgisScopeCardEntryMapper.insert(entry);
        return entry;
    }

    /** 标签卡号系统给号：SC 打头，号段由系统划 */
    private static String newBillNo() {
        return "SC" + IdWorker.getId();
    }

    /** 落笔时辰以秒计（与库面 DATETIME 同口径） */
    private static Date nowSecond() {
        return new Date(System.currentTimeMillis() / 1000 * 1000);
    }

    private static String appendRemark(String remark) {
        String r = trim(remark);
        return r == null ? "" : "；附言：" + r;
    }

    private static String cut(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
