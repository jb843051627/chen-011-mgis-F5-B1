package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TMgisCheckCardMapper;
import com.fc.v2.model.auto.TMgisCheckCard;
import com.fc.v2.service.ITMgisCheckCardService;

/**
 * 不合格成果整改回执单 Service业务层处理（state-machine 形状：单据流转）
 *
 * <p>关口自 0 至 {@value #MAX_STAGE} 一关一关走：推进只挪一关，不许半道
 * 撂下哪一关；过了末关回执才落「已销号」，中段各关一律仍是在办，不许
 * 提前亮销号灯。回退同理只退一关。已销号、已封存是终局：推进、回退、
 * 改记事、删除一律拒——局结了的卡再拧不动环节。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMgisCheckCardServiceImpl implements ITMgisCheckCardService {

    /** 末关：过了这一关回执销号（关口自 0 起连排，共 6 关） */
    private static final int MAX_STAGE = 5;

    /** 在办：中段各关都是这副脸色 */
    private static final int STATUS_OPEN = 0;
    /** 已销号：末关过了才落 */
    private static final int STATUS_CLOSED = 1;
    /** 已封存：终局 */
    private static final int STATUS_SEALED = 2;

    @javax.annotation.Resource
    private TMgisCheckCardMapper mgisCheckCardMapper;

    @Override
    public TMgisCheckCard selectTMgisCheckCardById(Long id) {
        return this.mgisCheckCardMapper.selectById(id);
    }

    @Override
    public List<TMgisCheckCard> selectTMgisCheckCardList(QueryWrapper<TMgisCheckCard> queryWrapper) {
        return this.mgisCheckCardMapper.selectList(queryWrapper);
    }

    @Override
    public TMgisCheckCard advance(Long id, String remark) {
        TMgisCheckCard r = requireOpen(id);
        if (r == null) {
            return null;
        }
        int from = r.getStage() == null ? 0 : r.getStage();
        // 联动线一关接一关：一次只挪一关，不许跳
        int to = Math.min(from + 1, MAX_STAGE);
        // 收尾一站怎么断写死在这里：过了末关才销号，中段始终在办
        int status = to >= MAX_STAGE ? STATUS_CLOSED : STATUS_OPEN;
        int rows = move(r, from, to, status, remark);
        return rows == 1 ? this.mgisCheckCardMapper.selectById(id) : null;
    }

    @Override
    public TMgisCheckCard rollback(Long id, String remark) {
        TMgisCheckCard r = requireOpen(id);
        if (r == null) {
            return null;
        }
        int from = r.getStage() == null ? 0 : r.getStage();
        // 只退一关；退到头停在头一关，不退成负数
        int to = Math.max(0, from - 1);
        int rows = move(r, from, to, STATUS_OPEN, remark);
        return rows == 1 ? this.mgisCheckCardMapper.selectById(id) : null;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TMgisCheckCard r = this.mgisCheckCardMapper.selectById(id);
        if (r == null) {
            return false;
        }
        // 已销号、已封存：局结了，记事也不许再改
        if (isTerminal(r)) {
            return false;
        }
        r.setContent(remark);
        return this.mgisCheckCardMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TMgisCheckCard r = this.mgisCheckCardMapper.selectById(id);
        if (r == null) {
            return false;
        }
        // 已销号、已封存的卡留档备查，不许删
        if (isTerminal(r)) {
            return false;
        }
        return this.mgisCheckCardMapper.deleteById(id) > 0;
    }

    /**
     * 取件并核还收不收流转动作：不在账回 null，已落终局也回 null（终局拧不动）。
     * 早年实现中段推进也错写过 status=1（已销号），那种卡在末关之前其实没销号——
     * 兼容老数据：只有封存（2）、或已销号且确在末关之后才算终局。
     */
    private TMgisCheckCard requireOpen(Long id) {
        if (id == null) {
            return null;
        }
        TMgisCheckCard r = this.mgisCheckCardMapper.selectById(id);
        if (r == null || isTerminal(r)) {
            return null;
        }
        return r;
    }

    /** 条件更新挪关：旧关、在办状态作比，被人抢先挪过即顶回 */
    private int move(TMgisCheckCard r, int from, int to, int status, String remark) {
        UpdateWrapper<TMgisCheckCard> uw = new UpdateWrapper<TMgisCheckCard>()
                .eq("id", r.getId())
                .eq("stage", from)
                .eq("status", r.getStatus() == null ? STATUS_OPEN : r.getStatus())
                .set("stage", to)
                .set("status", status)
                .set("last_action", remark);
        return this.mgisCheckCardMapper.update(null, uw);
    }

    /** 终局：封存；或已销号且确过末关。中段误置「已销号」的老卡不算终局 */
    private static boolean isTerminal(TMgisCheckCard r) {
        if (r == null) {
            return true;
        }
        Integer status = r.getStatus();
        if (status != null && status == STATUS_SEALED) {
            return true;
        }
        int stage = r.getStage() == null ? 0 : r.getStage();
        return status != null && status == STATUS_CLOSED && stage >= MAX_STAGE;
    }

}
