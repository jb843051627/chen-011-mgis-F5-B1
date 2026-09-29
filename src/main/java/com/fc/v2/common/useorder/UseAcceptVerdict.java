package com.fc.v2.common.useorder;

import com.fc.v2.model.custom.UseOrderSignRequest;

import java.util.Calendar;
import java.util.List;

/**
 * 受理推演定下的局面：申请单位、用途、逐行图幅定局（行次、代号、密级、出图年份）。
 * 进门那一张缺一项受理不下去——哪一栏犯规矩，在 {@link UseOrderJudge} 里即整单顶回，
 * 走到这里的是五样齐着的头一遍。
 *
 * @author fuce
 * @date 2026-09-28
 */
public class UseAcceptVerdict {

    private final String applicant;
    private final String purpose;
    private final List<UseSheetDraft> sheets;

    public UseAcceptVerdict(String applicant, String purpose, List<UseSheetDraft> sheets) {
        this.applicant = applicant;
        this.purpose = purpose;
        this.sheets = sheets;
    }

    public String getApplicant() {
        return applicant;
    }

    public String getPurpose() {
        return purpose;
    }

    public List<UseSheetDraft> getSheets() {
        return sheets;
    }

    public int getSheetCount() {
        return sheets == null ? 0 : sheets.size();
    }

    /** 受理推演便捷入口：年份顶线取今年 */
    public static UseAcceptVerdict of(UseOrderSignRequest req) {
        return UseOrderJudge.judgeAccept(req, Calendar.getInstance().get(Calendar.YEAR));
    }
}
