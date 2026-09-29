package com.fc.v2.common.useorder;

/**
 * 受理与走阶拦下缘故。顶回得说清拦在哪一栏、原值是什么——
 * 只甩一句「不合法」等于没说。
 *
 * @author fuce
 * @date 2026-09-28
 */
public enum UseOrderRejectReason {

    /** 申请单位空着 */
    MISSING_APPLICANT("申请单位", "申请单位未填：进门那一张缺一项受理不下去"),
    /** 用途空着 */
    MISSING_PURPOSE("用途", "用途未填：进门那一张缺一项受理不下去"),
    /** 图幅行一行都没有 */
    MISSING_SHEET("图幅行", "图幅行未填：一张白纸受理不下去"),
    /** 图幅行数越过上限 */
    SHEET_OVER_CEILING("图幅行", "图幅行数越线上限"),
    /** 某行图幅代号空着 */
    MISSING_SHEET_CODE("图幅代号", "图幅代号未填"),
    /** 册内图幅代号重号 */
    DUPLICATE_SHEET_CODE("图幅代号", "图幅代号册内重号：一册图对一条清单，重号没法对"),
    /** 密级空着 */
    MISSING_SECRET_LEVEL("密级", "密级未填"),
    /** 密级不在四档 */
    UNKNOWN_SECRET_LEVEL("密级", "密级不识：只认绝密、机密、秘密、内部四档"),
    /** 出图年份不是四位整数 */
    MAP_YEAR_NOT_A_NUMBER("出图年份", "出图年份不是四位整数"),
    /** 出图年份越界 */
    MAP_YEAR_OUT_OF_RANGE("出图年份", "出图年份越界：须是不早于 1900、不越过今年的四位年份"),
    /** 件不在账上 */
    ORDER_NOT_FOUND("件", "申请件不在账上"),
    /** 隔着阶落笔 */
    BAD_STAGE("动作", "隔阶不许：件停在哪一阶只开哪一阶的口子"),
    /** 已否决件定死 */
    DEAD_ORDER("件", "这件已写明不予核准，就此定死：一枚都加不进去，要办另起新件"),
    /** 已核准件各栏锁住 */
    APPROVED_LOCKED("件", "这件已核准，各栏锁住：只收窗口放成果与领清单，要动另起新件"),
    /** 未核准件点不出放成果、生不成清单 */
    NOT_APPROVED("件", "这件还没核准：省厅两枚没齐，窗口点不出放成果，也生不成清单"),
    /** 两签认定意见空着 */
    MISSING_OPINION("认定意见", "认定意见未填：省厅两岗各签各的，每一笔都得写清认不认定"),
    /** 同一人把两岗都签了：替签不作数 */
    PROXY_SIGN("省厅签署", "替签不作数：密级脱密与用途两岗须两个人分别签，同一支笔下两枚不算数"),
    /** 否决/发回缘由空着 */
    MISSING_REASON("缘由", "缘由未填：写明不予核准、发回市局都得把缘由写清");

    private final String field;
    private final String label;

    UseOrderRejectReason(String field, String label) {
        this.field = field;
        this.label = label;
    }

    public String getCode() {
        return name();
    }

    /** 拦在哪一栏 */
    public String getField() {
        return field;
    }

    public String getLabel() {
        return label;
    }
}
