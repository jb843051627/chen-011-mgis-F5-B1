package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.common.useorder.UseOrderFlowException;
import com.fc.v2.model.auto.TMgisUseOrder;
import com.fc.v2.model.custom.UseOrderSignRequest;
import com.fc.v2.service.ITMgisUseOrderService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 用密成果领用申请 Controller。
 *
 * <p>此路只经「签署」一道门：受理画押、市局核验、省厅两岗各签、省厅发回、
 * 写明不予核准、窗口放成果都打 {@code POST /mgisUseOrder/sign} 进，一阶阶地
 * 挪、发回重办、双签点齐、核准锁栏、凭单放成果，章程都在服务层。签署登出
 * 个什么结果，页面不预置；成与未成但凭回传定夺。</p>
 *
 * <p>绕开门面立单改单的路堵严：本控制器没有立单、改单、删单端点——立单只由
 * 受理那一笔在服务层起，落笔只经签署门。经办自登录态取，不许请求代报；
 * 落笔时辰由服务层写死。领用清单另走 {@code /claimList}，一件一生。</p>
 *
 * @author fuce
 * @date 2026-09-28
 */
@Api(value = "用密成果领用申请")
@Controller
@RequestMapping("/mgisUseOrder")
public class MgisUseOrderController extends BaseController {

    @Autowired
    private ITMgisUseOrderService mgisUseOrderService;

    @Log(title = "用密申请签署", action = "sign")
    @ApiOperation(value = "签署", notes = "唯一动作门：受理/核验通过/市局不予核准/省厅密级脱密签/"
            + "省厅用途签/省厅不予核准/发回市局/放成果。同岗同轮第二回只认头一笔；隔阶落笔、"
            + "否决定死、核准改栏、未核准放成果，一律当场顶回")
    @PostMapping("/sign")
    @RequiresPermissions("mgisUseOrder:sign")
    @ResponseBody
    public AjaxResult sign(@RequestBody UseOrderSignRequest req) {
        try {
            // 经办自登录态取，替签不作数；落笔时辰由服务层写死——请求里说了不算
            return retobject(200, mgisUseOrderService.sign(req, ShiroUtils.getLoginName()));
        } catch (UseOrderFlowException | IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @Log(title = "领用清单生成", action = "claimList")
    @ApiOperation(value = "一键生成领用清单", notes = "只认已核准件；图幅代号、密级、出图年份、"
            + "批准文号、申请单位全自核准那一份带，窗口不誊第二遍；一件一条，重按回头一遍")
    @PostMapping("/claimList")
    @RequiresPermissions("mgisUseOrder:claim")
    @ResponseBody
    public AjaxResult claimList(Long orderId) {
        try {
            return retobject(200, mgisUseOrderService.claimList(orderId, ShiroUtils.getLoginName()));
        } catch (UseOrderFlowException | IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @Log(title = "用密申请台账", action = "list")
    @ApiOperation(value = "申请台账", notes = "按申请单位、阶、状态、轮次查")
    @GetMapping("/list")
    @RequiresPermissions("mgisUseOrder:list")
    @ResponseBody
    public ResultTable list(TMgisUseOrder record) {
        QueryWrapper<TMgisUseOrder> queryWrapper = new QueryWrapper<TMgisUseOrder>();
        if (record != null) {
            if (record.getApplicant() != null && !record.getApplicant().trim().isEmpty()) {
                queryWrapper.eq("applicant", record.getApplicant().trim());
            }
            if (record.getStage() != null && !record.getStage().trim().isEmpty()) {
                queryWrapper.eq("stage", record.getStage().trim());
            }
            if (record.getStatus() != null) {
                queryWrapper.eq("status", record.getStatus());
            }
            if (record.getRoundNo() != null) {
                queryWrapper.eq("round_no", record.getRoundNo());
            }
        }
        queryWrapper.orderByDesc("id");
        startPage();
        com.github.pagehelper.PageInfo<TMgisUseOrder> page =
                new com.github.pagehelper.PageInfo<TMgisUseOrder>(
                        mgisUseOrderService.selectTMgisUseOrderList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "申请单页面", notes = "四阶表式，只开眼下阶的口子；每阶只显最新一轮落笔")
    @GetMapping("/{id}")
    @RequiresPermissions("mgisUseOrder:list")
    @ResponseBody
    public AjaxResult detail(@PathVariable("id") Long id) {
        try {
            return retobject(200, mgisUseOrderService.pageView(id));
        } catch (UseOrderFlowException | IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @ApiOperation(value = "签署链校验", notes = "顺拉倒拉各一遍：少一笔、次序错一处、只签一枚就放"
            + "成果、废票新票并排，这件申请即不合格")
    @GetMapping("/{id}/chain")
    @RequiresPermissions("mgisUseOrder:list")
    @ResponseBody
    public AjaxResult chain(@PathVariable("id") Long id) {
        try {
            return retobject(200, mgisUseOrderService.verifyChain(id));
        } catch (UseOrderFlowException | IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @ApiOperation(value = "领用清单回看", notes = "图幅代号、密级、出图年份、批准文号一次拉齐")
    @GetMapping("/{id}/claim")
    @RequiresPermissions("mgisUseOrder:list")
    @ResponseBody
    public AjaxResult claim(@PathVariable("id") Long id) {
        try {
            return retobject(200, mgisUseOrderService.getClaim(id));
        } catch (UseOrderFlowException | IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }
}
