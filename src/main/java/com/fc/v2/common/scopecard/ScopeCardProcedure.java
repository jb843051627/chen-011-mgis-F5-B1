package com.fc.v2.common.scopecard;

/**
 * 标签卡手续。登记门上只认这四道：立卡、核对、定档、类别增减。
 * 每过一道手续，经手人落笔链上记一条；卡上进展随之挪：
 * 立卡（0待核对）→ 核对（1已核对）→ 定档（2已定档）；类别增减不挪进展，
 * 只换在挂类别、重算挂用类别数、重折层级档。
 *
 * @author fuce
 * @date 2026-09-27
 */
public enum ScopeCardProcedure {

    /** 立卡：对档案、核类别、核挂用类别数，系统给卡号，创建人落笔 */
    REGISTER("立卡"),
    /** 核对：进展 0待核对 → 1已核对 */
    CHECK("核对"),
    /** 定档：进展 1已核对 → 2已定档，层级档再折算一次落库 */
    GRADE("定档"),
    /** 类别增减：整批换在挂类别，挂用类别数跟着重报，层级档跟着重折 */
    ADJUST("类别增减");

    private final String label;

    ScopeCardProcedure(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 按代号或中文名认手续；认不得即顶回——手续那一栏写的是什么，
     * 顶回的话里照原样带上。
     */
    public static ScopeCardProcedure require(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new ScopeCardAcceptException(ScopeCardRejectReason.MISSING_PROCEDURE,
                    ScopeCardRejectReason.MISSING_PROCEDURE.getLabel());
        }
        String s = raw.trim();
        for (ScopeCardProcedure p : values()) {
            if (p.name().equalsIgnoreCase(s) || p.getLabel().equals(s)) {
                return p;
            }
        }
        throw new ScopeCardAcceptException(ScopeCardRejectReason.UNKNOWN_PROCEDURE,
                ScopeCardRejectReason.UNKNOWN_PROCEDURE.getLabel() + "（填报原值：" + s + "）");
    }
}
