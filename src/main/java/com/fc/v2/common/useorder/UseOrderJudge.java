package com.fc.v2.common.useorder;

import com.fc.v2.model.custom.UseOrderSignRequest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 用密申请受理引擎：窗口进门那一张的规矩在系统里的唯一出处。
 *
 * <p>申请单位、用途、图幅行三样齐着报，每行图幅代号、密级、出图年份缺一不可；
 * 代号册内不许重号（一册图对一条清单，重号没法对）；密级只认
 * {@link UseSecretLevel} 四档，代号或中文名皆认，不识照原值顶回，不擅自定密；
 * 出图年份须是不早于 1900、不越过今年的四位整数。哪一栏犯规矩，整单顶回
 * （{@link UseOrderFlowException}），一笔不落。</p>
 *
 * @author fuce
 * @date 2026-09-28
 */
public final class UseOrderJudge {

    private UseOrderJudge() {
    }

    /** 一件图幅行数上限：越线的整单不收，一行也不落 */
    public static final int MAX_SHEETS = 2000;

    /** 出图年份最早认到这一年（近代测绘以前的年份不作数） */
    public static final int MIN_MAP_YEAR = 1900;

    /**
     * 受理推演。
     *
     * @param req       签署请求（受理动作带的填报原值）
     * @param yearCeil  出图年份顶线（今年，不越过）
     * @return 受理定局：单位、用途、逐行图幅
     */
    public static UseAcceptVerdict judgeAccept(UseOrderSignRequest req, int yearCeil) {
        if (req == null) {
            throw new UseOrderFlowException("申请未递到：签署请求为空");
        }
        String applicant = trim(req.getApplicant());
        if (applicant == null) {
            throw reject(UseOrderRejectReason.MISSING_APPLICANT);
        }
        String purpose = trim(req.getPurpose());
        if (purpose == null) {
            throw reject(UseOrderRejectReason.MISSING_PURPOSE);
        }
        List<UseOrderSignRequest.Sheet> raw = req.getSheets();
        if (raw == null || raw.isEmpty()) {
            // 一张白纸受理不下去——缺的正是要哪几幅图
            throw reject(UseOrderRejectReason.MISSING_SHEET);
        }
        if (raw.size() > MAX_SHEETS) {
            throw new UseOrderFlowException(UseOrderRejectReason.SHEET_OVER_CEILING,
                    UseOrderRejectReason.SHEET_OVER_CEILING.getLabel() + "（表上 " + raw.size()
                            + " 行，顶线 " + MAX_SHEETS + " 行），整单不收，一行也不落");
        }

        List<UseSheetDraft> sheets = new ArrayList<UseSheetDraft>(raw.size());
        Set<String> codes = new LinkedHashSet<String>();
        for (int i = 0; i < raw.size(); i++) {
            int rowNo = i + 1;
            UseOrderSignRequest.Sheet line = raw.get(i);

            String code = line == null ? null : trim(line.getSheetCode());
            if (code == null) {
                throw reject(UseOrderRejectReason.MISSING_SHEET_CODE, "第 " + rowNo + " 行");
            }
            if (!codes.add(code)) {
                // 册内重号：一册图对一条清单，重号对不上是哪一幅
                throw new UseOrderFlowException(UseOrderRejectReason.DUPLICATE_SHEET_CODE,
                        UseOrderRejectReason.DUPLICATE_SHEET_CODE.getLabel()
                                + "（第 " + rowNo + " 行，重号代号：" + code + "）");
            }

            String levelRaw = line == null ? null : trim(line.getSecretLevel());
            if (levelRaw == null) {
                throw reject(UseOrderRejectReason.MISSING_SECRET_LEVEL, "第 " + rowNo + " 行");
            }
            UseSecretLevel level = UseSecretLevel.tryOf(levelRaw);
            if (level == null) {
                throw new UseOrderFlowException(UseOrderRejectReason.UNKNOWN_SECRET_LEVEL,
                        UseOrderRejectReason.UNKNOWN_SECRET_LEVEL.getLabel()
                                + "（第 " + rowNo + " 行，填报原值：" + levelRaw + "）");
            }

            int year = parseYear(line == null ? null : line.getMapYear(), rowNo, yearCeil);
            sheets.add(new UseSheetDraft(rowNo, code, level, year));
        }
        return new UseAcceptVerdict(applicant, purpose, sheets);
    }

    /** 出图年份：四位整数，不早于 1900，不越过今年 */
    private static int parseYear(String raw, int rowNo, int yearCeil) {
        String s = trim(raw);
        if (s == null) {
            throw reject(UseOrderRejectReason.MAP_YEAR_NOT_A_NUMBER, "第 " + rowNo + " 行（填报原值：空）");
        }
        int year;
        try {
            // 不许带小数、不许带杂字符
            year = Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new UseOrderFlowException(UseOrderRejectReason.MAP_YEAR_NOT_A_NUMBER,
                    UseOrderRejectReason.MAP_YEAR_NOT_A_NUMBER.getLabel()
                            + "（第 " + rowNo + " 行，填报原值：" + s + "）");
        }
        if (year < MIN_MAP_YEAR || year > yearCeil) {
            throw new UseOrderFlowException(UseOrderRejectReason.MAP_YEAR_OUT_OF_RANGE,
                    UseOrderRejectReason.MAP_YEAR_OUT_OF_RANGE.getLabel()
                            + "（第 " + rowNo + " 行，填报原值：" + s + "，今年：" + yearCeil + "）");
        }
        return year;
    }

    private static UseOrderFlowException reject(UseOrderRejectReason reason) {
        return new UseOrderFlowException(reason, reason.getLabel());
    }

    private static UseOrderFlowException reject(UseOrderRejectReason reason, String where) {
        return new UseOrderFlowException(reason, reason.getLabel() + "（" + where + "）");
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
