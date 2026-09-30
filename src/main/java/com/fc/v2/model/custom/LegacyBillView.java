package com.fc.v2.model.custom;

import com.fc.v2.model.auto.TMgisUseBill;
import com.fc.v2.model.auto.TMgisUseBillSign;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.List;

/**
 * 旧核准单回看：拿新流程（窗口受理/市局核验/省厅核准/窗口放成果）的口径
 * 回旧「逐级核准单」的话。新判定逻辑接旧单就走这一处——阶、状态两处
 * 口径当一个讲，不另说一套。
 *
 * @author fuce
 * @date 2026-09-30
 */
@ApiModel(value = "LegacyBillView", description = "旧逐级核准单回看(按新四阶口径)")
public class LegacyBillView implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 旧单据 */
    @ApiModelProperty(value = "旧核准单")
    private final TMgisUseBill bill;

    /** 折到新流程的阶代号（UseOrderStage） */
    @ApiModelProperty(value = "折到新流程的阶")
    private final String newStage;

    /** 新流程状态成文（在办/已核准/已否决） */
    @ApiModelProperty(value = "状态成文")
    private final String statusLabel;

    /** 票籍（哪位签过、意见、销没销档） */
    @ApiModelProperty(value = "票籍")
    private final List<TMgisUseBillSign> tickets;

    public LegacyBillView(TMgisUseBill bill, String newStage, String statusLabel,
                          List<TMgisUseBillSign> tickets) {
        this.bill = bill;
        this.newStage = newStage;
        this.statusLabel = statusLabel;
        this.tickets = tickets;
    }

    public TMgisUseBill getBill() {
        return bill;
    }

    public String getNewStage() {
        return newStage;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public List<TMgisUseBillSign> getTickets() {
        return tickets;
    }
}
