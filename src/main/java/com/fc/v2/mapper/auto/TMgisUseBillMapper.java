package com.fc.v2.mapper.auto;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fc.v2.model.auto.TMgisUseBill;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 成果提供使用逐级核准单数据层
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface TMgisUseBillMapper extends BaseMapper<TMgisUseBill> {

    /**
     * 取单并加行锁：同一张核准单的签批、打回、退回在库里串行，
     * 两人同一分钟并发落签也一笔不丢（须在事务内调用）。
     */
    @Select("SELECT * FROM t_mgis_use_bill WHERE id = #{id} FOR UPDATE")
    TMgisUseBill selectForUpdate(@Param("id") Long id);

}
