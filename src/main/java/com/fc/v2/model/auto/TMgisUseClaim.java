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
 * 用密成果领用清单对象 t_mgis_use_claim
 *
 * <p>核准件一键生成，一件对一条，重按只回头一遍。申请单位、用途、批准文号、
 * 申请单号全自核准那一份带，窗口不誊第二遍；清单只立不改。</p>
 *
 * @author fuce
 * @date 2026-09-28
 */
@TableName("t_mgis_use_claim")
@ApiModel(value = "TMgisUseClaim", description = "用密成果领用清单(一件一条)")
public class TMgisUseClaim implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 领用清单号(QD打头,系统给号) */
    @TableField("claim_no")
    @ApiModelProperty(value = "领用清单号")
    private String claimNo;

    /** 核准申请单主键(一件对一条) */
    @TableField("order_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "核准申请单主键")
    private Long orderId;

    /** 申请单号(自核准件带) */
    @TableField("order_no")
    @ApiModelProperty(value = "申请单号")
    private String orderNo;

    /** 批准文号(自核准件带) */
    @TableField("approve_no")
    @ApiModelProperty(value = "批准文号")
    private String approveNo;

    /** 申请单位(自核准件带) */
    @TableField("applicant")
    @ApiModelProperty(value = "申请单位")
    private String applicant;

    /** 用途(自核准件带) */
    @TableField("purpose")
    @ApiModelProperty(value = "用途")
    private String purpose;

    /** 清单行数(与行表、核准件图幅行三处对账) */
    @TableField("line_count")
    @ApiModelProperty(value = "清单行数")
    private Integer lineCount;

    /** 生成清单经办(登录态取) */
    @TableField("generated_by")
    @ApiModelProperty(value = "生成清单经办")
    private String generatedBy;

    /** 生成时辰(系统写死,以秒计) */
    @TableField("generated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "生成时辰")
    private Date generatedAt;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClaimNo() {
        return claimNo;
    }

    public void setClaimNo(String claimNo) {
        this.claimNo = claimNo;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getApproveNo() {
        return approveNo;
    }

    public void setApproveNo(String approveNo) {
        this.approveNo = approveNo;
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

    public Integer getLineCount() {
        return lineCount;
    }

    public void setLineCount(Integer lineCount) {
        this.lineCount = lineCount;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(String generatedBy) {
        this.generatedBy = generatedBy;
    }

    public Date getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Date generatedAt) {
        this.generatedAt = generatedAt;
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
}
