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
 * 用密成果申请签署落笔链对象 t_mgis_use_entry
 *
 * <p>只增不改：经办、时辰在落笔那一下由系统写死，事后由不得人回头补录，
 * 也不许挪时辰。本表不设 update_by/update_time/del_flag，服务层不提供
 * 改、删入口。省厅发回重办时，市局、省厅两阶旧笔不删不改，由重办新笔的
 * {@code supersedesEntryId} 指向上一轮同岗旧笔；旧笔退出页面，链上原样
 * 留着——顺拉倒拉一笔都不能少。</p>
 *
 * @author fuce
 * @date 2026-09-28
 */
@TableName("t_mgis_use_entry")
@ApiModel(value = "TMgisUseEntry", description = "用密成果申请签署落笔链(只增不改)")
public class TMgisUseEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 申请单主键 */
    @TableField("order_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "申请单主键")
    private Long orderId;

    /** 链上序号(自1连排) */
    @TableField("seq")
    @ApiModelProperty(value = "链上序号(自1连排)")
    private Integer seq;

    /** 第几轮(省厅发回市局一轮加一) */
    @TableField("round_no")
    @ApiModelProperty(value = "第几轮")
    private Integer roundNo;

    /** 落笔阶 WINDOW/CITY/PROVINCE/ISSUE */
    @TableField("stage")
    @ApiModelProperty(value = "落笔阶")
    private String stage;

    /** 落笔动作 ACCEPT/CITY_PASS/CITY_REJECT/SIGN_SECRECY/SIGN_PURPOSE/PROVINCE_REJECT/RETURN/ISSUE */
    @TableField("action")
    @ApiModelProperty(value = "落笔动作")
    private String action;

    /** 这一笔写的话(JSON,落笔即定) */
    @TableField("payload")
    @ApiModelProperty(value = "这一笔写的话(JSON)")
    private String payload;

    /** 经办(落笔那一下系统写死,替签不作数) */
    @TableField("penned_by")
    @ApiModelProperty(value = "经办")
    private String pennedBy;

    /** 落笔时辰(系统写死,以秒计) */
    @TableField("penned_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "落笔时辰")
    private Date pennedAt;

    /** 前一笔哈希(头一笔为64个0) */
    @TableField("prev_hash")
    @ApiModelProperty(value = "前一笔哈希")
    private String prevHash;

    /** 本笔哈希 */
    @TableField("hash")
    @ApiModelProperty(value = "本笔哈希")
    private String hash;

    /** 发回重办顶替的旧笔(旧笔退出页面,链上仍在) */
    @TableField("supersedes_entry_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "重办顶替的那一笔")
    private Long supersedesEntryId;

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

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getPennedBy() {
        return pennedBy;
    }

    public void setPennedBy(String pennedBy) {
        this.pennedBy = pennedBy;
    }

    public Date getPennedAt() {
        return pennedAt;
    }

    public void setPennedAt(Date pennedAt) {
        this.pennedAt = pennedAt;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public void setPrevHash(String prevHash) {
        this.prevHash = prevHash;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public Long getSupersedesEntryId() {
        return supersedesEntryId;
    }

    public void setSupersedesEntryId(Long supersedesEntryId) {
        this.supersedesEntryId = supersedesEntryId;
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
