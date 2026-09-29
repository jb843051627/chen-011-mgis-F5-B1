package com.fc.v2.common.scopecard;

/**
 * 登记顶回：哪一栏犯了哪条规矩，缘故代号与成文一起带出来。
 * 顶回即一笔不落——卡、在挂类别、经手人落笔一样都不写。
 *
 * @author fuce
 * @date 2026-09-27
 */
public class ScopeCardAcceptException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 拦下缘故代号 */
    private final String reasonCode;

    /** 拦在哪一栏 */
    private final String field;

    public ScopeCardAcceptException(ScopeCardRejectReason reason, String message) {
        super(message);
        this.reasonCode = reason == null ? null : reason.getCode();
        this.field = reason == null ? null : reason.getField();
    }

    public ScopeCardAcceptException(String message) {
        super(message);
        this.reasonCode = null;
        this.field = null;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getField() {
        return field;
    }
}
