package com.fc.v2.common.useorder;

/**
 * 用密申请走阶顶回：缺项受理不下去、隔阶落笔、已否决件加签、
 * 已核准件改栏、未核准件放成果、两处计数对不上之类，一律当场顶回，
 * 一笔都不写进库里。
 *
 * @author fuce
 * @date 2026-09-28
 */
public class UseOrderFlowException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 拦下缘故代号（受理类顶回带得出，走阶类可为空） */
    private final String reasonCode;

    /** 拦在哪一栏（走阶类可为空） */
    private final String field;

    public UseOrderFlowException(String message) {
        super(message);
        this.reasonCode = null;
        this.field = null;
    }

    public UseOrderFlowException(UseOrderRejectReason reason, String message) {
        super(message);
        this.reasonCode = reason == null ? null : reason.getCode();
        this.field = reason == null ? null : reason.getField();
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getField() {
        return field;
    }
}
