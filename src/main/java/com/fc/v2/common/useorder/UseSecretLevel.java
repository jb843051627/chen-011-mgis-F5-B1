package com.fc.v2.common.useorder;

/**
 * 成果密级四档：绝密、机密、秘密、内部。
 * 受理时代号或中文名皆认；认不得照原值顶回，不擅自定密。
 *
 * @author fuce
 * @date 2026-09-28
 */
public enum UseSecretLevel {

    /** 绝密 */
    TOP_SECRET("绝密"),
    /** 机密 */
    SECRET("机密"),
    /** 秘密 */
    CONFIDENTIAL("秘密"),
    /** 内部 */
    INTERNAL("内部");

    private final String label;

    UseSecretLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 按代号或中文名认领；认不得返回 null（由受理裁判带原值顶回）。
     */
    public static UseSecretLevel tryOf(String code) {
        if (code != null) {
            String s = code.trim();
            for (UseSecretLevel v : values()) {
                if (v.name().equalsIgnoreCase(s) || v.label.equals(s)) {
                    return v;
                }
            }
        }
        return null;
    }
}
