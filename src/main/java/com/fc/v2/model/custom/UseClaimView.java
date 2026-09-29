package com.fc.v2.model.custom;

import com.fc.v2.model.auto.TMgisUseClaim;
import com.fc.v2.model.auto.TMgisUseClaimLine;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.List;

/**
 * 领用清单回话：清单头与逐行。图幅代号、密级、出图年份、批准文号、申请单位
 * 统统自核准那一份带，窗口不誊第二遍；一件对一条，重按 {@code duplicated} 为真，
 * 回的是头一遍那张。
 *
 * @author fuce
 * @date 2026-09-28
 */
@ApiModel(value = "UseClaimView", description = "用密成果领用清单")
public class UseClaimView implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 清单头 */
    @ApiModelProperty(value = "清单头")
    private final TMgisUseClaim claim;

    /** 清单逐行 */
    @ApiModelProperty(value = "清单逐行")
    private final List<TMgisUseClaimLine> lines;

    /** 是不是重按回头一遍（真则没新立，回的头一张） */
    @ApiModelProperty(value = "是否重按回头一张")
    private final boolean duplicated;

    public UseClaimView(TMgisUseClaim claim, List<TMgisUseClaimLine> lines, boolean duplicated) {
        this.claim = claim;
        this.lines = lines;
        this.duplicated = duplicated;
    }

    public TMgisUseClaim getClaim() {
        return claim;
    }

    public List<TMgisUseClaimLine> getLines() {
        return lines;
    }

    public boolean isDuplicated() {
        return duplicated;
    }
}
