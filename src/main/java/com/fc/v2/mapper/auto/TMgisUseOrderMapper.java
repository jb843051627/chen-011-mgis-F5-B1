package com.fc.v2.mapper.auto;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fc.v2.model.auto.TMgisUseOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用密成果领用申请单数据层
 *
 * @author fuce
 * @date 2026-09-28
 */
public interface TMgisUseOrderMapper extends BaseMapper<TMgisUseOrder> {

    /**
     * 取件并加行锁：同一件的签署在库里串行，省厅两岗同一分钟并发落签
     * 也一笔不丢、序号不乱（须在事务内调用）。
     */
    @Select("SELECT * FROM t_mgis_use_order WHERE id = #{id} FOR UPDATE")
    TMgisUseOrder selectForUpdate(@Param("id") Long id);

}
