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
 * 用密成果申请图幅行对象 t_mgis_use_sheet
 *
 * <p>受理时逐行落账，只增不改：不设 update/del_flag，服务层无改删入口。
 * 核准后各栏锁住，要改图幅只能另起新件。领用清单逐行从这里带，
 * 窗口不誊第二遍。</p>
 *
 * @author fuce
 * @date 2026-09-28
 */
@TableName("t_mgis_use_sheet")
@ApiModel(value = "TMgisUseSheet", description = "用密成果申请图幅行(只增不改)")
public class TMgisUseSheet implements Serializable {
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

    /** 册内行次(自1连排) */
    @TableField("row_no")
    @ApiModelProperty(value = "册内行次")
    private Integer rowNo;

    /** 图幅代号 */
    @TableField("sheet_code")
    @ApiModelProperty(value = "图幅代号")
    private String sheetCode;

    /** 密级 TOP_SECRET绝密 SECRET机密 CONFIDENTIAL秘密 INTERNAL内部 */
    @TableField("secret_level")
    @ApiModelProperty(value = "密级")
    private String secretLevel;

    /** 图上出图年份(四位整数) */
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
