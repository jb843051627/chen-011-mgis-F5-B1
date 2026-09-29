package com.fc.v2.common.useorder;

/**
 * 受理推演定下的一幅图：行次、图幅代号、认下的密级、出图年份。
 * 每行三栏都过得去才进这个定局；接口怎么回、账就怎么记，逐行一一对落。
 *
 * @author fuce
 * @date 2026-09-28
 */
public class UseSheetDraft {

    private final int rowNo;
    private final String sheetCode;
    private final UseSecretLevel secretLevel;
    private final int mapYear;

    public UseSheetDraft(int rowNo, String sheetCode, UseSecretLevel secretLevel, int mapYear) {
        this.rowNo = rowNo;
        this.sheetCode = sheetCode;
        this.secretLevel = secretLevel;
        this.mapYear = mapYear;
    }

    public int getRowNo() {
        return rowNo;
    }

    public String getSheetCode() {
        return sheetCode;
    }

    public UseSecretLevel getSecretLevel() {
        return secretLevel;
    }

    public int getMapYear() {
        return mapYear;
    }
}
