package com.fc.v2.common.usebill;

/**
 * 旧核准单走层顶回：已打回定死、已核讫锁栏、联名档一人两笔、两处签数
 * 对不上之类，当场顶回，不写票、不挪单。
 *
 * @author fuce
 * @date 2026-09-30
 */
public class LegacyBillException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LegacyBillException(String message) {
        super(message);
    }
}
