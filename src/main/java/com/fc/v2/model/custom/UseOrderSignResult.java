package com.fc.v2.model.custom;

import com.fc.v2.model.auto.TMgisUseEntry;
import com.fc.v2.model.auto.TMgisUseOrder;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

/**
 * 签署回话：落了的那一笔、件眼下的局面。成与未成但凭这一份回传定夺，
 * 页面不预置结果；同一岗同一轮第二回来的，{@code duplicated} 为真，
 * 回的是头一笔，不再落笔。
 *
 * @author fuce
 * @date 2026-09-28
 */
@ApiModel(value = "UseOrderSignResult", description = "用密申请签署回话")
public class UseOrderSignResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 落了的那一笔（重按回的是头一笔） */
    @ApiModelProperty(value = "落了的那一笔")
    private final TMgisUseEntry entry;

    /** 件眼下局面 */
    @ApiModelProperty(value = "件眼下局面")
    private final TMgisUseOrder order;

    /** 是不是同一岗同一轮第二回（真则没落笔，回的头一笔） */
    @ApiModelProperty(value = "是否重按回头一笔")
    private final boolean duplicated;

    public UseOrderSignResult(TMgisUseEntry entry, TMgisUseOrder order, boolean duplicated) {
        this.entry = entry;
        this.order = order;
        this.duplicated = duplicated;
    }

    public TMgisUseEntry getEntry() {
        return entry;
    }

    public TMgisUseOrder getOrder() {
        return order;
    }

    public boolean isDuplicated() {
        return duplicated;
    }
}
