package com.fc.v2.common.scopecard;

/**
 * 来文来源。市局抄送过来的与单位自报的常常是同一件事——同一来文单号
 * 两条并成一条，账上不许留两笔；来源只认这两路，别路来文不收。
 *
 * @author fuce
 * @date 2026-09-27
 */
public enum ScopeCardSource {

    /** 市局抄送 */
    CITY_FORWARD("市局抄送"),
    /** 单位自报 */
    UNIT_SELF("单位自报");

    private final String label;

    ScopeCardSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 按代号或中文名认来源；认不得即顶回，原值照留 */
    public static ScopeCardSource require(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new ScopeCardAcceptException(ScopeCardRejectReason.MISSING_SOURCE,
                    ScopeCardRejectReason.MISSING_SOURCE.getLabel());
        }
        String s = raw.trim();
        for (ScopeCardSource src : values()) {
            if (src.name().equalsIgnoreCase(s) || src.getLabel().equals(s)) {
                return src;
            }
        }
        throw new ScopeCardAcceptException(ScopeCardRejectReason.UNKNOWN_SOURCE,
                ScopeCardRejectReason.UNKNOWN_SOURCE.getLabel() + "（填报原值：" + s + "）");
    }
}
