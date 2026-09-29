package com.fc.v2.model.custom;

import com.fc.v2.model.auto.TMgisUseEntry;
import com.fc.v2.model.auto.TMgisUseOrder;
import com.fc.v2.model.auto.TMgisUseSheet;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.List;

/**
 * 申请单页面：表式就用进门那一张申请单——抬头（单位、用途、批准文号）、
 * 图幅行、四阶签署各落过谁的签，不另起炉灶。件停在哪一阶，就只开哪一阶
 * 的口子；每一阶只显最新一轮算数的笔，发回重办后旧轮的笔退出页面，
 * 链上两辈的笔都还在（查链走 /mgisUseOrder/{id}/chain）。
 *
 * @author fuce
 * @date 2026-09-28
 */
@ApiModel(value = "UseOrderPageView", description = "用密申请单页面")
public class UseOrderPageView implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 申请单抬头与状态 */
    @ApiModelProperty(value = "申请单")
    private final TMgisUseOrder order;

    /** 图幅行（受理定局，核准后锁住） */
    @ApiModelProperty(value = "图幅行")
    private final List<TMgisUseSheet> sheets;

    /** 眼下停在哪一阶 */
    @ApiModelProperty(value = "眼下停在哪一阶")
    private final String currentStage;

    /** 状态成文 */
    @ApiModelProperty(value = "状态成文")
    private final String statusLabel;

    /** 逐阶页面（四阶全列，未到的阶空着） */
    @ApiModelProperty(value = "逐阶页面")
    private final List<StagePage> stages;

    public UseOrderPageView(TMgisUseOrder order, List<TMgisUseSheet> sheets, String currentStage,
                            String statusLabel, List<StagePage> stages) {
        this.order = order;
        this.sheets = sheets;
        this.currentStage = currentStage;
        this.statusLabel = statusLabel;
        this.stages = stages;
    }

    public TMgisUseOrder getOrder() {
        return order;
    }

    public List<TMgisUseSheet> getSheets() {
        return sheets;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public List<StagePage> getStages() {
        return stages;
    }

    /**
     * 一阶的页面：开不开口子、这一阶算数的几笔。
     */
    @ApiModel(value = "UseOrderStagePage", description = "一阶的页面")
    public static class StagePage implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 阶代号 */
        @ApiModelProperty(value = "阶代号")
        private final String stage;

        /** 阶名 */
        @ApiModelProperty(value = "阶名")
        private final String label;

        /** 这一阶眼下开不开口子（在办时只开眼下停的那一阶） */
        @ApiModelProperty(value = "这一阶开不开口子")
        private final boolean open;

        /** 这一阶算数的落笔（最新一轮；发回前的旧笔已退出页面） */
        @ApiModelProperty(value = "这一阶算数的落笔")
        private final List<TMgisUseEntry> entries;

        public StagePage(String stage, String label, boolean open, List<TMgisUseEntry> entries) {
            this.stage = stage;
            this.label = label;
            this.open = open;
            this.entries = entries;
        }

        public String getStage() {
            return stage;
        }

        public String getLabel() {
            return label;
        }

        public boolean isOpen() {
            return open;
        }

        public List<TMgisUseEntry> getEntries() {
            return entries;
        }
    }
}
