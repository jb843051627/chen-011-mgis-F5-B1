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
 * 标签卡经手人落笔链对象 t_mgis_scope_card_entry（只增不改）
 *
 * @author fuce
 * @date 2026-09-27
 */
@TableName("t_mgis_scope_card_entry")
@ApiModel(value = "TMgisScopeCardEntry", description = "标签卡经手人落笔链")
public class TMgisScopeCardEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 卡主键 */
    @TableField("card_id")
    @ApiModelProperty(value = "卡主键")
    private Long cardId;

    /** 链上序号(自1连排) */
    @TableField("seq")
    @ApiModelProperty(value = "链上序号(自1连排)")
    private Integer seq;

    /** 手续 REGISTER立卡 CHECK核对 GRADE定档 ADJUST类别增减 */
    @TableField("proc")
    @ApiModelProperty(value = "手续 REGISTER立卡 CHECK核对 GRADE定档 ADJUST类别增减")
    private String proc;

    /** 这一笔要说的话(落笔即定,往后只读) */
    @TableField("payload")
    @ApiModelProperty(value = "这一笔要说的话(落笔即定,往后只读)")
    private String payload;

    /** 经手人(落笔那一下系统写死) */
    @TableField("penned_by")
    @ApiModelProperty(value = "经手人(落笔那一下系统写死)")
    private String pennedBy;

    /** 落笔时辰(系统写死,以秒计) */
    @TableField("penned_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "落笔时辰(系统写死,以秒计)")
    private Date pennedAt;

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

    public Long getCardId() {
        return cardId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public String getProc() {
        return proc;
    }

    public void setProc(String proc) {
        this.proc = proc;
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
