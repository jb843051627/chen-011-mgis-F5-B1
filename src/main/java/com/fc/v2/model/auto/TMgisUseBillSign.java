package com.fc.v2.model.auto;

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
 * 成果提供使用逐级核准单票籍对象 t_mgis_use_bill_sign
 *
 * <p>旧核准单每一笔记进账：谁签的、签的什么意见、打回后旧票销没销，
 * 翻这张表就查得到。票只立不删：打回销档是把 {@code active} 置 0，
 * 旧票仍留在链上。</p>
 *
 * @author fuce
 * @date 2026-09-30
 */
@TableName("t_mgis_use_bill_sign")
@ApiModel(value = "TMgisUseBillSign", description = "成果提供使用逐级核准单票籍")
public class TMgisUseBillSign implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 核准单主键 */
    @TableField("bill_id")
    @ApiModelProperty(value = "核准单主键")
    private Long billId;

    /** 落笔时所在层 */
    @TableField("node_no")
    @ApiModelProperty(value = "落笔时所在层")
    private Integer nodeNo;

    /** 第几趟到这一层（打回后再上来一趟加一） */
    @TableField("batch_no")
    @ApiModelProperty(value = "第几趟到这一层")
    private Integer batchNo;

    /** 票种 APPROVE签批 REJECT打回 ROLLBACK退回 */
    @TableField("action")
    @ApiModelProperty(value = "票种 APPROVE签批 REJECT打回 ROLLBACK退回")
    private String action;

    /** 签署经办（登录态写死） */
    @TableField("signer")
    @ApiModelProperty(value = "签署经办")
    private String signer;

    /** 签批意见 / 打回或退回缘由 */
    @TableField("comment")
    @ApiModelProperty(value = "签批意见/打回或退回缘由")
    private String comment;

    /** 还算不算数 1算数 0随打回销档 */
    @TableField("active")
    @ApiModelProperty(value = "还算不算数 1算数 0随打回销档")
    private Integer active;

    /** 创建者 */
    @TableField("create_by")
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBillId() {
        return billId;
    }

    public void setBillId(Long billId) {
        this.billId = billId;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public Integer getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(Integer batchNo) {
        this.batchNo = batchNo;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getSigner() {
        return signer;
    }

    public void setSigner(String signer) {
        this.signer = signer;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer active) {
        this.active = active;
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
