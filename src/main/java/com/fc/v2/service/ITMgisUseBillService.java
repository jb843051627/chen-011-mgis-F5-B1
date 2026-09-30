package com.fc.v2.service;

import com.fc.v2.model.auto.TMgisUseBill;
import com.fc.v2.model.auto.TMgisUseBillSign;
import com.fc.v2.model.custom.LegacyBillView;

import java.util.List;

/**
 * 成果提供使用逐级核准单 Service接口（approval-chain 形状：多阶段签批，无增删改查入口）
 *
 * <p>早年六个方法签名一仍其旧；票籍落账、带经办人的退回、翻账对账走
 * 后面新添的方法。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITMgisUseBillService {

    /** 按主键回查单据 */
    TMgisUseBill selectTMgisUseBillById(Long id);

    /**
     * 签批一票：落票籍；联名档须不同的人签齐本层应签数才往下一层挪，
     * 同一人同层同趟第二回只认头一张；两层签齐即核讫。返回更新后的单据；
     * 件不在账返回 null。
     */
    TMgisUseBill approve(Long id, String approver, String comment);

    /**
     * 写明不予核准：落票籍即置「已打回」，件定死；返回单据，件不在账返回 null。
     */
    TMgisUseBill reject(Long id, String approver, String comment);

    /**
     * 退回上一层（旧签名：退回票经办记系统）：离开层、退回层算数旧票销档，
     * 从上一层另起一趟重签。
     */
    TMgisUseBill rollback(Long id, String comment);

    /**
     * 退回上一层（新重载：经办人自登录态取）。旧票籍销档、另起一趟重签，
     * 与旧签名同一走法，只多认了落笔的人。
     */
    TMgisUseBill rollback(Long id, String operator, String comment);

    /** 翻一件的票籍：哪位签过、签的什么意见、旧票销没销，全在这上头查 */
    List<TMgisUseBillSign> listSigns(Long id);

    /**
     * 翻件前对两本账：单据上存的本层签数与票籍当场按人重算的对不上即抛错；
     * 对得上回单据。
     */
    TMgisUseBill requireConsistent(Long id);

    /**
     * 按新流程四阶口径回看旧单：旧单据折成「市局核验/省厅核准/窗口放成果」
     * 的阶与在办/已核准/已否决的状态，票籍一并带出——新判定接旧单的门，
     * 两处口径当一个讲。
     */
    LegacyBillView viewByNewRules(Long id);
}
