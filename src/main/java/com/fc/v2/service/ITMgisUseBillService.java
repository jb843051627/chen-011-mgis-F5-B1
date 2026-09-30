package com.fc.v2.service;

import com.fc.v2.model.auto.TMgisUseBill;
import com.fc.v2.model.auto.TMgisUseBillSign;

import java.util.List;

/**
 * 成果提供使用逐级核准单 Service接口（approval-chain 形状：多阶段签批，无增删改查入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITMgisUseBillService {

    /** 按主键回查单据 */
    TMgisUseBill selectTMgisUseBillById(Long id);

    /** 签批一票：返回更新后的单据；被拒返回 null */
    TMgisUseBill approve(Long id, String approver, String comment);

    /** 否决：返回更新后的单据；被拒返回 null */
    TMgisUseBill reject(Long id, String approver, String comment);

    /** 退回上一环节：返回更新后的单据；被拒返回 null */
    TMgisUseBill rollback(Long id, String comment);

    /**
     * 回查这件各节点的签署票（新添能力，不改旧签名）：哪位签的、同意与否、
     * 签的什么话、何时签的，逐票查得到——早年只有单头 sign_count，
     * 哪位签过无从查，这一处补齐。
     */
    List<TMgisUseBillSign> listSigns(Long id);
}

