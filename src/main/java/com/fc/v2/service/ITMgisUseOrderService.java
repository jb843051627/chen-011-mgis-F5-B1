package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.useorder.UseOrderChainReport;
import com.fc.v2.model.auto.TMgisUseOrder;
import com.fc.v2.model.custom.UseClaimView;
import com.fc.v2.model.custom.UseOrderPageView;
import com.fc.v2.model.custom.UseOrderSignRequest;
import com.fc.v2.model.custom.UseOrderSignResult;

import java.util.List;

/**
 * 用密成果领用申请 Service接口。
 *
 * <p>此路只经「签署」一道门：受理画押、市局核验、省厅两岗各签、省厅发回、
 * 写明不予核准、窗口放成果都打 {@link #sign} 进，走阶、双签点齐、发回清账、
 * 锁栏的章程都在服务层（唯一出处是
 * {@link com.fc.v2.common.useorder.UseOrderFlow} 与
 * {@link com.fc.v2.common.useorder.UseOrderJudge}）。</p>
 *
 * <p>绕开门面立单改单的路堵严：控制器没有 add/edit/remove，申请图幅行、
 * 签署链、清单行只立不改。领用清单另走 {@link #claimList}，一件一生。</p>
 *
 * @author fuce
 * @date 2026-09-28
 */
public interface ITMgisUseOrderService {

    /**
     * 签署：唯一动作门，动作由 {@code req.action} 指认。
     * 经办与落笔时辰由系统在落笔那一下写死，不收请求里的话；
     * 同岗同一轮第二回来的只认头一笔。
     *
     * @param req      签署请求（受理带填报原值，其余带意见缘由）
     * @param operator 经办（控制器自登录态取，替签不作数）
     * @return 落了的那一笔与件眼下局面
     */
    UseOrderSignResult sign(UseOrderSignRequest req, String operator);

    /**
     * 核准件一键生成领用清单：一件对一条，重按只回头一遍。
     * 图幅代号、密级、出图年份、批准文号、申请单位全自核准那一份带。
     *
     * @param orderId  核准件主键
     * @param operator 经办（窗口登录态取）
     * @return 清单头与逐行（重按 duplicated 为真）
     */
    UseClaimView claimList(Long orderId, String operator);

    /** 按主键查件 */
    TMgisUseOrder selectTMgisUseOrderById(Long id);

    /** 台账查询 */
    List<TMgisUseOrder> selectTMgisUseOrderList(QueryWrapper<TMgisUseOrder> queryWrapper);

    /**
     * 申请单页面：四阶表式，只开眼下阶的口子，每阶只显最新一轮落笔；
     * 单据计数与行表、签署链当场对账。
     */
    UseOrderPageView pageView(Long orderId);

    /**
     * 签署链校验：顺拉从受理念到末笔，倒拉从末笔念回受理，
     * 两程都通这件申请才算合格。
     */
    UseOrderChainReport verifyChain(Long orderId);

    /** 领用清单回看（图幅代号、密级、出图年份、批准文号） */
    UseClaimView getClaim(Long orderId);
}
