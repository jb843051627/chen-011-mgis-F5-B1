package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 用密成果领用申请单对象 t_mgis_use_order
 *
 * <p>一件对一单。申请单位、用途、图幅行受理那一下落定；件分四阶走，
 * 省厅两岗两签齐才核准——核准那一下系统给批准文号、各栏锁住；
 * 省厅发回则轮次加一、前签一笔勾销。单据上的图幅行数、省厅已签数
 * 与行表、签署链当场重算两份口径恒等。</p>
 *
 * @author fuce
 * @date 2026-09-28
 */
@TableName("t_mgis_use_order")
@ApiModel(value = "TMgisUseOrder", description = "用密成果领用申请单")
public class TMgisUseOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 申请单号(YM打头,系统给号) */
    @TableField("order_no")
    @ApiModelProperty(value = "申请单号")
    private String orderNo;

    /** 申请单位 */
    @TableField("applicant")
    @ApiModelProperty(value = "申请单位")
    private String applicant;

    /** 用途 */
    @TableField("purpose")
    @ApiModelProperty(value = "用途")
    private String purpose;

    /** 眼下停在哪一阶 WINDOW/CITY/PROVINCE/ISSUE/DONE */
    @TableField("stage")
    @ApiModelProperty(value = "眼下停在哪一阶")
    private String stage;

    /** 第几轮(省厅发回市局一轮加一) */
    @TableField("round_no")
    @ApiModelProperty(value = "第几轮")
    private Integer roundNo;

    /** 状态 0在办 1已核准 2已否决 3已办结 */
    @TableField("status")
    @ApiModelProperty(value = "状态 0在办 1已核准 2已否决 3已办结")
    private Integer status;

    /** 图幅行数(受理落账;与行表当场重算两处对账) */
    @TableField("sheet_count")
    @ApiModelProperty(value = "图幅行数")
    private Integer sheetCount;

    /** 省厅已签人数(落账;与链上最新一轮两岗笔数两处对账) */
    @TableField("province_sign_count")
    @ApiModelProperty(value = "省厅已签人数")
    private Integer provinceSignCount;

    /** 批准文号(YZ打头,省厅两签齐核准那一下系统给) */
    @TableField("approve_no")
    @ApiModelProperty(value = "批准文号")
    private String approveNo;

    /** 核准时最后落签经办(快照) */
    @TableField("approved_by")
    @ApiModelProperty(value = "核准经办(快照)")
    private String approvedBy;

    /** 核准时辰(快照) */
    @TableField("approved_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "核准时辰(快照)")
    private Date approvedAt;

    /** 放成果经办(快照) */
    @TableField("issued_by")
    @ApiModelProperty(value = "放成果经办(快照)")
    private String issuedBy;

    /** 放成果时辰(快照) */
    @TableField("issued_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "放成果时辰(快照)")
    private Date issuedAt;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
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

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getSheetCount() {
        return sheetCount;
    }

    public void setSheetCount(Integer sheetCount) {
        this.sheetCount = sheetCount;
    }

    public Integer getProvinceSignCount() {
        return provinceSignCount;
    }

    public void setProvinceSignCount(Integer provinceSignCount) {
        this.provinceSignCount = provinceSignCount;
    }

    public String getApproveNo() {
        return approveNo;
    }

    public void setApproveNo(String approveNo) {
        this.approveNo = approveNo;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Date getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Date approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public void setIssuedBy(String issuedBy) {
        this.issuedBy = issuedBy;
    }

    public Date getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Date issuedAt) {
        this.issuedAt = issuedAt;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
