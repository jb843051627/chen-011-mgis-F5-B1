package com.fc.v2.controller.admin;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.usebill.LegacyBillException;
import com.fc.v2.service.ITMgisUseBillService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 早年「成果提供使用逐级核准单」回看 Controller（只读）。
 *
 * <p>旧单的签批、打回、退回仍走服务层早年三法，不辟写入门；这里只开
 * 一扇只读门，把库里历年的旧单按新流程四阶口径回出来——新判定逻辑
 * 接得住旧单，阶与状态两处口径当一个讲。</p>
 *
 * @author fuce
 * @date 2026-09-30
 */
@Api(value = "成果提供使用逐级核准单(旧)")
@Controller
@RequestMapping("/mgisUseBill")
public class MgisUseBillController extends BaseController {

    @Autowired
    private ITMgisUseBillService mgisUseBillService;

    @ApiOperation(value = "旧单按新四阶口径回看", notes = "阶折成新流程代号，状态同码同义；票籍一并带出")
    @GetMapping("/{id}")
    @RequiresPermissions("mgisUseOrder:list")
    @ResponseBody
    public AjaxResult detail(@PathVariable("id") Long id) {
        try {
            return retobject(200, mgisUseBillService.viewByNewRules(id));
        } catch (LegacyBillException e) {
            return error(e.getMessage());
        }
    }
}
