package com.fc.v2.common.useorder;

/**
 * 用密成果领用申请四阶（加办结终态）：窗口受理、市局核验、省厅核准、窗口放成果、办结。
 * 走阶只许一阶接一阶，次序写死在这里，别处不许另排。
 *
 * @author fuce
 * @date 2026-09-28
 */
public enum UseOrderStage {

    /** 窗口受理：申请单进门，五样齐着才受理得下去 */
    WINDOW("窗口受理", 0),
    /** 市局核验：成果管理科核用途真不真、与允许的作业范围搭不搭 */
    CITY("市局核验", 1),
    /** 省厅核准：密级脱密、用途两岗各签各的，两枚齐才准往下走 */
    PROVINCE("省厅核准", 2),
    /** 窗口放成果：凭核准件放，未核准点不出来 */
    ISSUE("窗口放成果", 3),
    /** 办结：成果已放，终态不收笔 */
    DONE("办结", 4);

    private final String label;
    private final int order;

    UseOrderStage(String label, int order) {
        this.label = label;
        this.order = order;
    }

    public String getLabel() {
        return label;
    }

    /** 阶次（窗口受理为 0，依次加一），只许比邻走阶 */
    public int getOrder() {
        return order;
    }

    /**
     * 按代号或中文阶名认领；认不得即驳回。
     */
    public static UseOrderStage require(String code) {
        if (code != null) {
            String s = code.trim();
            for (UseOrderStage st : values()) {
                if (st.name().equalsIgnoreCase(s) || st.label.equals(s)) {
                    return st;
                }
            }
        }
        throw new IllegalArgumentException("阶名不识：" + code);
    }
}
