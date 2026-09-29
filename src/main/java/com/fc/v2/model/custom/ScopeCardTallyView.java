package com.fc.v2.model.custom;

import com.fc.v2.common.scopecard.ScopeCardTallyRow;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.List;

/**
 * 看账一屏。省局口径、属地口径各是一屏；两口径同出一处计算，
 * 卡数总计一致这一屏才摆得出来——差一笔就算这一屏没过。
 *
 * @author fuce
 * @date 2026-09-27
 */
@ApiModel(value = "ScopeCardTallyView", description = "看账一屏")
public class ScopeCardTallyView implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 口径 PROVINCE省局 REGION属地 */
    @ApiModelProperty(value = "口径")
    private final String scope;

    /** 这一屏的卡数总计（与另一口径总计一致） */
    @ApiModelProperty(value = "卡数总计")
    private final int totalCards;

    /** 看账行 */
    @ApiModelProperty(value = "看账行")
    private final List<ScopeCardTallyRow> rows;

    public ScopeCardTallyView(String scope, int totalCards, List<ScopeCardTallyRow> rows) {
        this.scope = scope;
        this.totalCards = totalCards;
        this.rows = rows;
    }

    public String getScope() {
        return scope;
    }

    public int getTotalCards() {
        return totalCards;
    }

    public List<ScopeCardTallyRow> getRows() {
        return rows;
    }
}
