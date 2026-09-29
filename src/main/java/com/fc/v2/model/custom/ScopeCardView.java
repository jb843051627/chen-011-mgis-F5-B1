package com.fc.v2.model.custom;

import com.fc.v2.model.auto.TMgisScopeCard;
import com.fc.v2.model.auto.TMgisScopeCardEntry;
import com.fc.v2.model.auto.TMgisScopeCategory;
import com.fc.v2.model.auto.TMgisUnitDoc;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 卡页面。同一条卡在省局、属地两边点开是同一笔账：卡、出处档案、
 * 在挂类别、经手人落笔链一次拉齐。层级档不当卡上的死数回——
 * 当场拿眼下的挂用类别数与档案顶线重折一遍，与落库那一份并列，
 * 两边对不上即库里被人动过。
 *
 * @author fuce
 * @date 2026-09-27
 */
@ApiModel(value = "ScopeCardView", description = "卡页面")
public class ScopeCardView implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 卡 */
    @ApiModelProperty(value = "卡")
    private final TMgisScopeCard card;

    /** 出处档案（单位编号、名称、性质、注册地、三道顶线） */
    @ApiModelProperty(value = "出处档案")
    private final TMgisUnitDoc siteDoc;

    /** 在挂类别（末级，代号名称齐着） */
    @ApiModelProperty(value = "在挂类别")
    private final List<TMgisScopeCategory> categories;

    /** 经手人落笔链（每过一道手续一条，只增不改） */
    @ApiModelProperty(value = "经手人落笔链")
    private final List<TMgisScopeCardEntry> entries;

    /** 层级档当场重折（以眼下挂用类别数与档案顶线） */
    @ApiModelProperty(value = "层级档当场重折")
    private final BigDecimal currentGrade;

    /** 落库档与当场重折对得上 */
    @ApiModelProperty(value = "落库档与当场重折对得上")
    private final boolean gradeConsistent;

    public ScopeCardView(TMgisScopeCard card, TMgisUnitDoc siteDoc, List<TMgisScopeCategory> categories,
                         List<TMgisScopeCardEntry> entries, BigDecimal currentGrade, boolean gradeConsistent) {
        this.card = card;
        this.siteDoc = siteDoc;
        this.categories = categories;
        this.entries = entries;
        this.currentGrade = currentGrade;
        this.gradeConsistent = gradeConsistent;
    }

    public TMgisScopeCard getCard() {
        return card;
    }

    public TMgisUnitDoc getSiteDoc() {
        return siteDoc;
    }

    public List<TMgisScopeCategory> getCategories() {
        return categories;
    }

    public List<TMgisScopeCardEntry> getEntries() {
        return entries;
    }

    public BigDecimal getCurrentGrade() {
        return currentGrade;
    }

    public boolean isGradeConsistent() {
        return gradeConsistent;
    }
}
