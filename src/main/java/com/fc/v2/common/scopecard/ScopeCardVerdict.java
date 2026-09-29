package com.fc.v2.common.scopecard;

import java.math.BigDecimal;
import java.util.List;

/**
 * 登记推演定局。立卡、类别增减两道工序共用：系统核成什么局面，
 * 这一份就是全部凭据——卡号由服务层另给（系统给号），其余栏位
 * 怎么落账全照定局写，接口怎么回、账就怎么记。
 *
 * @author fuce
 * @date 2026-09-27
 */
public class ScopeCardVerdict {

    /** 来文单号（人工填写；立卡时有，类别增减不涉及） */
    private final String sourceBillNo;

    /** 来文来源（立卡时有，类别增减不涉及） */
    private final ScopeCardSource sourceFrom;

    /** 资质单位编号（以档案为准，不按名称） */
    private final String siteNo;

    /** 所属档案主键 */
    private final Integer siteId;

    /** 作业范围段代号（立卡时有，类别增减不涉及） */
    private final String scopeSeg;

    /** 在挂末级类别代号（清单内重号已并掉，次序照填报） */
    private final List<String> categories;

    /** 挂用类别数（项） */
    private final BigDecimal qty;

    /** 本年度新增挂用类别数（项）；类别增减时空着表示这一栏不动 */
    private final BigDecimal fineAmt;

    /** 层级档（系统撞三道顶线折算，留两位小数） */
    private final BigDecimal gradeLevel;

    public ScopeCardVerdict(String sourceBillNo, ScopeCardSource sourceFrom, String siteNo, Integer siteId,
                            String scopeSeg, List<String> categories, BigDecimal qty, BigDecimal fineAmt,
                            BigDecimal gradeLevel) {
        this.sourceBillNo = sourceBillNo;
        this.sourceFrom = sourceFrom;
        this.siteNo = siteNo;
        this.siteId = siteId;
        this.scopeSeg = scopeSeg;
        this.categories = categories;
        this.qty = qty;
        this.fineAmt = fineAmt;
        this.gradeLevel = gradeLevel;
    }

    public String getSourceBillNo() {
        return sourceBillNo;
    }

    public ScopeCardSource getSourceFrom() {
        return sourceFrom;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public Integer getSiteId() {
        return siteId;
    }

    public String getScopeSeg() {
        return scopeSeg;
    }

    public List<String> getCategories() {
        return categories;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public BigDecimal getFineAmt() {
        return fineAmt;
    }

    public BigDecimal getGradeLevel() {
        return gradeLevel;
    }
}
