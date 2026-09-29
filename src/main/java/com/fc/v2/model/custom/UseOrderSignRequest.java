package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.List;

/**
 * 签署请求。此路只经「签署」一道门：受理画押、市局核验、省厅两岗各签、
 * 发回、写明不予核准、放成果都打这一个门进，动作由 {@code action} 指认。
 *
 * <p>请求只带填报原值与意见缘由，不带「通过/驳回」结果栏——成与不成、
 * 挪到哪一阶、给不给核准文号，但凭服务层回传，页面不预置。经办与落笔
 * 时辰不收请求的话，系统在落笔那一下自己写死，替签不作数。</p>
 *
 * @author fuce
 * @date 2026-09-28
 */
@ApiModel(value = "UseOrderSignRequest", description = "用密成果申请签署请求")
public class UseOrderSignRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 申请件主键（受理动作为空，落账后回话带） */
    @ApiModelProperty(value = "申请件主键(受理时空)")
    private Long orderId;

    /** 签署动作 ACCEPT/CITY_PASS/CITY_REJECT/SIGN_SECRECY/SIGN_PURPOSE/PROVINCE_REJECT/RETURN/ISSUE（中文名亦可） */
    @ApiModelProperty(value = "签署动作", required = true)
    private String action;

    /** 受理用：申请单位 */
    @ApiModelProperty(value = "申请单位(仅受理)")
    private String applicant;

    /** 受理用：用途 */
    @ApiModelProperty(value = "用途(仅受理)")
    private String purpose;

    /** 受理用：图幅行（每行图幅代号、密级、出图年份，缺一不可） */
    @ApiModelProperty(value = "图幅行(仅受理)")
    private List<Sheet> sheets;

    /** 省厅两岗签用：认定意见（必填） */
    @ApiModelProperty(value = "认定意见(省厅两岗签必填)")
    private String opinion;

    /** 不予核准、发回市局用：缘由（必填） */
    @ApiModelProperty(value = "缘由(否决/发回必填)")
    private String reason;

    /** 核验通过、放成果附言（可空） */
    @ApiModelProperty(value = "附言")
    private String note;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getApplicant() {
        return applicant;
    }

    public void setApplicant(String applicant) {
        this.applicant = applicant;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public List<Sheet> getSheets() {
        return sheets;
    }

    public void setSheets(List<Sheet> sheets) {
        this.sheets = sheets;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /** 一幅图的填报原值：图幅代号、密级（代号或中文名）、出图年份 */
    @ApiModel(value = "UseOrderSheet", description = "申请图幅行填报原值")
    public static class Sheet implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 图幅代号 */
        @ApiModelProperty(value = "图幅代号", required = true)
        private String sheetCode;

        /** 密级（绝密/机密/秘密/内部，代号或中文名皆认） */
        @ApiModelProperty(value = "密级", required = true)
        private String secretLevel;

        /** 图上那一年出图（四位年份，填报原值，系统核） */
        @ApiModelProperty(value = "出图年份", required = true)
        private String mapYear;

        public String getSheetCode() {
            return sheetCode;
        }

        public void setSheetCode(String sheetCode) {
            this.sheetCode = sheetCode;
        }

        public String getSecretLevel() {
            return secretLevel;
        }

        public void setSecretLevel(String secretLevel) {
            this.secretLevel = secretLevel;
        }

        public String getMapYear() {
            return mapYear;
        }

        public void setMapYear(String mapYear) {
            this.mapYear = mapYear;
        }
    }
}
