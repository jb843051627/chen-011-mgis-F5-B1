package com.fc.v2.common.scopecard;

/**
 * 登记拦下缘故。保存被拦下来，得说清拦在哪一栏——只甩一句「不合法」
 * 等于没说。每条缘故都带栏位名，成文里栏位、原值、顶线齐着写。
 *
 * @author fuce
 * @date 2026-09-27
 */
public enum ScopeCardRejectReason {

    /** 手续那一栏空着 */
    MISSING_PROCEDURE("手续", "手续未指：登记门只认立卡、核对、定档、类别增减四道"),
    /** 手续那一栏写的认不得 */
    UNKNOWN_PROCEDURE("手续", "手续不识"),
    /** 来文单号空着：单号一径由人工填写，系统不代拟 */
    MISSING_SOURCE_BILL("来文单号", "来文单号未填：单号一径由人工填写"),
    /** 来文来源空着 */
    MISSING_SOURCE("来文来源", "来文来源未填：只认市局抄送、单位自报两路"),
    /** 来文来源认不得 */
    UNKNOWN_SOURCE("来文来源", "来文来源不识"),
    /** 单位编号空着：立卡先按编号对档案 */
    MISSING_SITE_NO("资质单位编号", "资质单位编号未填：立卡先按编号对档案，只按名称匹配不作数"),
    /** 编号对不上档案 */
    SITE_DOC_NOT_FOUND("资质单位编号", "资质单位编号对不上档案"),
    /** 档案已注销：不收新卡，老卡也不许接着添东西 */
    SITE_DOC_CANCELLED("资质单位编号", "档案已注销"),
    /** 范围段空着：一张卡只讲一段范围 */
    MISSING_SCOPE_SEG("作业范围段", "作业范围段未填：一张卡只讲一段范围"),
    /** 同一单位同一段范围已有卡：第二张必须顶回 */
    DUPLICATE_SCOPE("作业范围段", "同一单位同一段范围已有卡，第二张必须顶回"),
    /** 挂用类别清单空着 */
    MISSING_CATEGORY("挂用类别", "挂用类别未填：卡上只许挂末级类别"),
    /** 类别代号目录里查不到 */
    UNKNOWN_CATEGORY("挂用类别", "类别代号不识"),
    /** 挂的是大类：卡上只许挂到末级，大类与末级不许混挂 */
    MAJOR_NOT_LEAF("挂用类别", "挂的是大类不是末级"),
    /** 挂的是早已并入别处的旧类别 */
    MERGED_CATEGORY("挂用类别", "类别已并走，旧类别不许再挂"),
    /** 挂用类别数根本不是数 */
    QTY_NOT_A_NUMBER("挂用类别数", "挂用类别数不是数"),
    /** 挂用类别数填负数、填零 */
    QTY_NOT_POSITIVE("挂用类别数", "挂用类别数须为正数"),
    /** 挂用类别数越过档案允许承接的顶线 */
    QTY_OVER_CEILING("挂用类别数", "挂用类别数越过档案允许承接的顶线"),
    /** 本年度新增不是数 */
    FINE_AMT_NOT_A_NUMBER("本年度新增挂用类别数", "本年度新增挂用类别数不是数"),
    /** 本年度新增填负数 */
    FINE_AMT_NEGATIVE("本年度新增挂用类别数", "本年度新增挂用类别数不得为负"),
    /** 卡不在账上 */
    CARD_NOT_FOUND("卡", "卡不在账上"),
    /** 隔着站落笔：眼下该走哪一站写清 */
    BAD_STATION("手续", "隔站不许"),
    /** 档案三道顶线未维护齐，没法折算层级档 */
    CEILING_NOT_READY("档案顶线", "档案三道顶线未维护齐，没法折算层级档");

    private final String field;
    private final String label;

    ScopeCardRejectReason(String field, String label) {
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
