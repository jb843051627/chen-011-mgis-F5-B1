package com.fc.v2.mapper.auto;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fc.v2.model.auto.TMgisRectifyOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 整改回执单数据层
 *
 * @author fuce
 * @date 2026-09-26
 */
public interface TMgisRectifyOrderMapper extends BaseMapper<TMgisRectifyOrder> {

    /**
     * 取单并加行锁：同一张单的承办在库里串行，复测两名同一分钟并发落笔
     * 也一笔不丢、序号不乱（须在事务内调用）。与用密申请签署门同一口径，
     * 两处并发收局不许两样。
     */
    @Select("SELECT * FROM t_mgis_rectify_order WHERE id = #{id} FOR UPDATE")
    TMgisRectifyOrder selectForUpdate(@Param("id") Long id);

}
