package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TMgisScopeCard;
import com.fc.v2.model.auto.TMgisScopeCategory;
import com.fc.v2.model.custom.ScopeCardRegisterRequest;
import com.fc.v2.model.custom.ScopeCardRegisterResult;
import com.fc.v2.model.custom.ScopeCardTallyView;
import com.fc.v2.model.custom.ScopeCardView;

import java.util.List;

/**
 * 作业范围标签建档卡 Service接口
 *
 * <p>原有签名一仍其旧（前六个方法封存不动）；登记门、看账、属地可见
 * 这些新意思全走后面新加的方法，已发布的方法签名一个不动。</p>
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITMgisScopeCardService {

    /** 按主键查询 */
    TMgisScopeCard selectTMgisScopeCardById(Long id);

    /** 按条件查询列表（分页由调用方统一处理） */
    List<TMgisScopeCard> selectTMgisScopeCardList(Wrapper<TMgisScopeCard> queryWrapper);

    /** 新增 */
    int insertTMgisScopeCard(TMgisScopeCard record);

    /** 修改 */
    int updateTMgisScopeCard(TMgisScopeCard record);

    /** 批量删除 */
    int deleteTMgisScopeCardByIds(String ids);

    /** 按主键删除 */
    int deleteTMgisScopeCardById(Long id);

    // ---------------- 登记门（新章，旧签名照封） ----------------

    /**
     * 登记：唯一写入门。立卡、核对、定档、类别增减四道手续都打这一处进；
     * 同一来文单号第二遍递进来并成一条（头一张原样回），同一单位同一段
     * 范围的第二张顶回。经办自登录态取，不许请求代报。
     *
     * @param req      登记请求（只带填报原值）
     * @param operator 经办（登录态）
     * @return 登记回话：卡眼下局面与经手人链上落了的那一笔
     */
    ScopeCardRegisterResult register(ScopeCardRegisterRequest req, String operator);

    /**
     * 卡页面：卡、出处档案、在挂类别、经手人落笔链一次拉齐；
     * 层级档当场重折与落库档并列。属地只看得见本市的卡。
     *
     * @param id         卡主键
     * @param viewerCity 看账人属地市（省局传 null 看全省）
     */
    ScopeCardView cardView(Long id, String viewerCity);

    /**
     * 可见卡列表：属地的人只看得见本市的卡，省局（viewerCity 为 null）看全省。
     */
    List<TMgisScopeCard> selectVisibleTMgisScopeCardList(QueryWrapper<TMgisScopeCard> queryWrapper, String viewerCity);

    /**
     * 看账·省局口径：从新增类别最多的单位往下排；与属地口径同出一处计算，
     * 两条路数出来的卡数不一致，这一屏不过。
     */
    ScopeCardTallyView tallyByProvince(String viewerCity);

    /**
     * 看账·属地口径：把单位按最末一级（区县）归堆再数；与省局口径同出一处计算。
     */
    ScopeCardTallyView tallyByRegion(String viewerCity);

    /** 专业类别目录树（一级大类、二级末级；登记页面拉目录用，只读） */
    List<TMgisScopeCategory> listScopeCategories();

    /**
     * 档案顶线、属地变了，该单位名下各卡的层级档跟着重新折算落库——
     * 只在立卡那天折一次是远远不够的。挂用类别数越过新顶线的卡不重折，
     * 留原档待人处置；返回重折落库的笔数。
     */
    int recalcGradesOfSite(String siteNo);
}
