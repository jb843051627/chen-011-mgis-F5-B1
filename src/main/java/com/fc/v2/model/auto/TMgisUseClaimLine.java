package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 用密成果领用清单行对象 t_mgis_use_claim_line
 *
 * <p>只立不改：图幅代号、密级、出图年份全自核准那一份申请行带，行次与申请行
 * 同序，窗口一个字不经手誊抄。清单行数与核准件图幅行数恒等。</p>
 *
 * @author fuce
 * @date 2026-09-28
 */
@TableName("t_mgis_use_claim_line")
@ApiModel(value = "TMgisUseClaimLine", description = "用密成果领用清单行(只立不改)")
public class TMgisUseClaimLine implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 清单主键 */
    @TableField("claim_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "清单主键")
    private Long claimId;

    /** 核准申请单主键 */
    @TableField("order_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "核准申请单主键")
    private Long orderId;

    /** 行次(与申请行同序) */
    @TableField("row_no")
    @ApiModelProperty(value = "行次")
    private Integer rowNo;

    /** 图幅代号(自申请行带) */
    @TableField("sheet_code")
    @ApiModelProperty(value = "图幅代号")
    private String sheetCode;

    /** 密级(自申请行带) */
    @TableField("secret_level")
    @ApiModelProperty(value = "密级")
    private String secretLevel;

    /** 出图年份(自申请行带) */
    @TableField("map_year")
    @ApiModelProperty(value = "出图年份")
    private Integer mapYear;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getClaimId() {
        return claimId;
    }

    public void setClaimId(Long claimId) {
        this.claimId = claimId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

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

    public Integer getMapYear() {
        return mapYear;
    }

    public void setMapYear(Integer mapYear) {
        this.mapYear = mapYear;
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
