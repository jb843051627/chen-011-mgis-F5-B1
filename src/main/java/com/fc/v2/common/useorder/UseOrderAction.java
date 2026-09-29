package com.fc.v2.common.useorder;

/**
 * 签署动作。此路只经「签署」一道门：受理画押、市局核验、省厅两岗各签、
 * 省厅发回、写明不予核准、窗口放成果，全是签署门里的动作，成与不成、
 * 挪到哪一阶由服务层按动作推演，页面不预置结果。
 *
 * <p>每个动作落在哪一阶写死在这里：省厅两岗与登录经办焊死，密级岗落不成
 * 用途岗的签，反之亦然——替签不作数。</p>
 *
 * @author fuce
 * @date 2026-09-28
 */
public enum UseOrderAction {

    /** 窗口受理：申请单位、用途、图幅行（代号/密级/出图年份）齐着才落 */
    ACCEPT("受理", UseOrderStage.WINDOW),
    /** 市局核验通过：用途真、与允许的作业范围搭得上 */
    CITY_PASS("核验通过", UseOrderStage.CITY),
    /** 市局写明不予核准：缘由必填，落笔即定死 */
    CITY_REJECT("市局不予核准", UseOrderStage.CITY),
    /** 省厅密级脱密岗签：盯密级与脱密处理到不到位，认定意见必填 */
    SIGN_SECRECY("省厅签·密级脱密", UseOrderStage.PROVINCE),
    /** 省厅用途岗签：看这个用途该不该给，认定意见必填 */
    SIGN_PURPOSE("省厅签·用途", UseOrderStage.PROVINCE),
    /** 省厅写明不予核准：缘由必填，落笔即定死 */
    PROVINCE_REJECT("省厅不予核准", UseOrderStage.PROVINCE),
    /** 省厅发回市局：脱密说明单薄之类，缘由必填；前签一笔勾销，从市局重攒 */
    RETURN("发回市局", UseOrderStage.PROVINCE),
    /** 窗口放成果：只认已核准的件，落笔即办结 */
    ISSUE("放成果", UseOrderStage.ISSUE);

    private final String label;
    private final UseOrderStage stage;

    UseOrderAction(String label, UseOrderStage stage) {
        this.label = label;
        this.stage = stage;
    }

    public String getLabel() {
        return label;
    }

    /** 这一笔落在哪一阶 */
    public UseOrderStage getStage() {
        return stage;
    }

    /** 是不是省厅两岗的签（各签各的，不并成一条） */
    public boolean isProvinceSign() {
        return this == SIGN_SECRECY || this == SIGN_PURPOSE;
    }

    /** 是不是写明不予核准（落笔即定死） */
    public boolean isReject() {
        return this == CITY_REJECT || this == PROVINCE_REJECT;
    }

    /** 是不是省厅发回市局（前签一笔勾销，轮次加一） */
    public boolean isReturn() {
        return this == RETURN;
    }

    /**
     * 按代号或中文名认领动作；认不得即驳回。
     */
    public static UseOrderAction require(String code) {
        if (code != null) {
            String s = code.trim();
            for (UseOrderAction a : values()) {
                if (a.name().equalsIgnoreCase(s) || a.label.equals(s)) {
                    return a;
                }
            }
        }
        throw new IllegalArgumentException("签署动作不识：" + code);
    }
}
