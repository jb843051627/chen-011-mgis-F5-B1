package com.fc.v2.model.custom;

import com.fc.v2.model.auto.TMgisScopeCard;
import com.fc.v2.model.auto.TMgisScopeCardEntry;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

/**
 * 登记回话。灵不灵以服务回传为凭：回话里是卡眼下的局面与经手人链上
 * 落了的那一笔，页面只按回话刷新。同一来文单号第二遍递进来，
 * duplicated 为真，回的是头一张卡——两条并成一条，账上不留两笔。
 *
 * @author fuce
 * @date 2026-09-27
 */
@ApiModel(value = "ScopeCardRegisterResult", description = "登记回话")
public class ScopeCardRegisterResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 卡眼下的局面（含系统给的卡号、系统折算的层级档） */
    @ApiModelProperty(value = "卡眼下局面")
    private final TMgisScopeCard card;

    /** 经手人链上落了的那一笔（并一条重递时回的是头一笔） */
    @ApiModelProperty(value = "经手人链上落了的那一笔")
    private final TMgisScopeCardEntry entry;

    /** 是否同一来文单号第二回（并一条，未再立卡） */
    @ApiModelProperty(value = "是否同一单号第二回")
    private final boolean duplicated;

    public ScopeCardRegisterResult(TMgisScopeCard card, TMgisScopeCardEntry entry, boolean duplicated) {
        this.card = card;
        this.entry = entry;
        this.duplicated = duplicated;
    }

    public TMgisScopeCard getCard() {
        return card;
    }

    public TMgisScopeCardEntry getEntry() {
        return entry;
    }

    public boolean isDuplicated() {
        return duplicated;
    }
}
