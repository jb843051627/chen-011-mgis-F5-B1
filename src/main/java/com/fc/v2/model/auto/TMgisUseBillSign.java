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
 * 成果提供使用逐级核准单签署明细对象 t_mgis_use_bill_sign。
 *
 * <p>早年的旧单只在单头存一个 sign_count，哪位签过、签的什么话无从查，
 * 联名一档落一个人也能放行。这张表把每一节点的每一票落账：节点、签人、
 * 同意与否、签的话、签批时辰一样不少；同一节点同一人只认一票
 * （唯一键兜底），退回上一环节时该节点旧票整批销籍。</p>
 *
 * @author fuce
 * @date 2026-09-29
 */
@TableName("t_mgis_use_bill_sign")
@ApiModel(value = "TMgisUseBillSign", description = "成果提供使用逐级核准单签署明细")
public class TMgisUseBillSign implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 同意 */
    public static final int VERDICT_APPROVE = 1;
    /** 否决 */
    public static final int VERDICT_REJECT = 2;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 核准单主键 */
    @TableField("bill_id")
    @ApiModelProperty(value = "核准单主键")
    private Long billId;

    /** 签在哪一层（与落笔时单据 node_no 同） */
    @TableField("node_no")
    @ApiModelProperty(value = "签批层")
    private Integer nodeNo;

    /** 签批人（登录态是谁记谁，不许代报） */
    @TableField("approver")
    @ApiModelProperty(value = "签批人")
    private String approver;

    /** 这一票 1同意 2否决 */
    @TableField("verdict")
    @ApiModelProperty(value = "1同意 2否决")
    private Integer verdict;

    /** 签的话/否决缘由 */
    @TableField("comment_text")
    @ApiModelProperty(value = "签批意见")
    private String commentText;

    /** 签批时辰（系统写死） */
    @TableField("sign_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "签批时辰")
    private Date signAt;

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

    public String getApprover() {
        return approver;
    }

    public void setApprover(String approver) {
        this.approver = approver;
    }

    public Integer getVerdict() {
        return verdict;
    }

    public void setVerdict(Integer verdict) {
        this.verdict = verdict;
    }

    public String getCommentText() {
        return commentText;
    }

    public void setCommentText(String commentText) {
        this.commentText = commentText;
    }

    public Date getSignAt() {
        return signAt;
    }

    public void setSignAt(Date signAt) {
        this.signAt = signAt;
    }
}
