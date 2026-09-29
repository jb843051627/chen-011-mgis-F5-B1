package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.List;

/**
 * 登记请求。进门只认「登记」一处：立卡、核对、定档、类别增减都打
 * 这一个门进，手续由 procedure 指认。登记成个什么样子，页面不许预填
 * 硬数——本请求只带填报原值；来文单号一径由人工填写，系统不代拟；
 * 层级档不许人手填，请求里带不带这一栏系统都不认。经办自登录态取、
 * 落笔时辰由服务层写死，请求里说了不算。
 *
 * @author fuce
 * @date 2026-09-27
 */
@ApiModel(value = "ScopeCardRegisterRequest", description = "登记请求")
public class ScopeCardRegisterRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 手续 REGISTER立卡 CHECK核对 GRADE定档 ADJUST类别增减（中文名亦可） */
    @ApiModelProperty(value = "手续", required = true)
    private String procedure;

    /** 卡主键（核对、定档、类别增减必填；立卡不填） */
    @ApiModelProperty(value = "卡主键(核对/定档/类别增减必填)")
    private Long cardId;

    /** 来文单号（立卡必填；一径由人工填写，同一单号并一条） */
    @ApiModelProperty(value = "来文单号(立卡必填,人工填写)")
    private String sourceBillNo;

    /** 来文来源 CITY_FORWARD市局抄送 UNIT_SELF单位自报（立卡必填，中文名亦可） */
    @ApiModelProperty(value = "来文来源(立卡必填)")
    private String sourceFrom;

    /** 资质单位编号（立卡必填；只按编号对档案，只按名称匹配不作数） */
    @ApiModelProperty(value = "资质单位编号(立卡必填)")
    private String siteNo;

    /** 作业范围段代号（立卡必填；同一单位同一段范围只许一张卡） */
    @ApiModelProperty(value = "作业范围段代号(立卡必填)")
    private String scopeSeg;

    /** 挂用的末级类别代号清单（立卡、类别增减必填；只许末级，不许挂大类或已并走的旧类别） */
    @ApiModelProperty(value = "挂用末级类别代号清单(立卡/类别增减必填)")
    private List<String> categories;

    /** 挂用类别数（立卡、类别增减必填；照填报原值核，填负数、填零、越过档案顶线当场拦下） */
    @ApiModelProperty(value = "挂用类别数(立卡/类别增减必填)")
    private String qty;

    /** 本年度新增挂用类别数（立卡空着按 0 计；类别增减空着表示这一栏不动） */
    @ApiModelProperty(value = "本年度新增挂用类别数")
    private String fineAmt;

    /** 附言（落笔时记进经手人链那一笔） */
    @ApiModelProperty(value = "附言")
    private String remark;

    public String getProcedure() {
        return procedure;
    }

    public void setProcedure(String procedure) {
        this.procedure = procedure;
    }

    public Long getCardId() {
        return cardId;
    }

    public void setCardId(Long cardId) {
        this.cardId = cardId;
    }

    public String getSourceBillNo() {
        return sourceBillNo;
    }

    public void setSourceBillNo(String sourceBillNo) {
        this.sourceBillNo = sourceBillNo;
    }

    public String getSourceFrom() {
        return sourceFrom;
    }

    public void setSourceFrom(String sourceFrom) {
        this.sourceFrom = sourceFrom;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public String getScopeSeg() {
        return scopeSeg;
    }

    public void setScopeSeg(String scopeSeg) {
        this.scopeSeg = scopeSeg;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public String getQty() {
        return qty;
    }

    public void setQty(String qty) {
        this.qty = qty;
    }

    public String getFineAmt() {
        return fineAmt;
    }

    public void setFineAmt(String fineAmt) {
        this.fineAmt = fineAmt;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
