package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.common.scopecard.ScopeCardAcceptException;
import com.fc.v2.model.auto.TMgisScopeCard;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.model.custom.ScopeCardRegisterRequest;
import com.fc.v2.service.ITMgisScopeCardService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.SecurityUtils;
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
 * 作业范围标签建档卡 Controller。
 *
 * <p>进门只认「登记」一处：立卡、核对、定档、类别增减都打
 * {@code POST /mgisScopeCard/register} 进，手续由请求指认，章程都在服务层。
 * 登记成个什么样子，页面不许预填硬数——请求只带填报原值；灵不灵以服务
 * 回传为凭，页面只按回话刷新；来文单号一径由人工填写；校验的活儿不落进
 * 页面。本控制器没有 add/edit/remove 端点——单条添、单条改的门不辟。</p>
 *
 * <p>经办自登录态取，不许请求代报。属地的人打开列表只看得见本市的单位
 * 与卡，省局看得见全省；同一条卡在两边点开是同一笔账。</p>
 *
 * @author fuce
 * @date 2026-09-27
 */
@Api(value = "作业范围标签建档卡")
@Controller
@RequestMapping("/mgisScopeCard")
public class MgisScopeCardController extends BaseController {

    @Autowired
    private ITMgisScopeCardService mgisScopeCardService;

    @Log(title = "标签卡登记", action = "register")
    @ApiOperation(value = "登记", notes = "唯一写入门：立卡/核对/定档/类别增减。同一来文单号并一条；"
            + "同一单位同一段范围第二张顶回；注销档案不收新卡、老卡不许接着添东西；"
            + "卡上只许挂末级类别；挂用类别数填负数、填零、越过档案顶线当场拦下并说清拦在哪一栏；"
            + "层级档系统折算，不许人手填")
    @PostMapping("/register")
    @RequiresPermissions("mgisScopeCard:register")
    @ResponseBody
    public AjaxResult register(@RequestBody ScopeCardRegisterRequest req) {
        try {
            // 经办自登录态取，落笔时辰由服务层写死——请求里说了不算
            return retobject(200, mgisScopeCardService.register(req, ShiroUtils.getLoginName()));
        } catch (ScopeCardAcceptException | IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @Log(title = "标签卡台账", action = "list")
    @ApiOperation(value = "卡台账", notes = "属地只看得见本市的单位与卡，省局看得见全省")
    @GetMapping("/list")
    @RequiresPermissions("mgisScopeCard:list")
    @ResponseBody
    public ResultTable list(TMgisScopeCard record) {
        QueryWrapper<TMgisScopeCard> queryWrapper = new QueryWrapper<TMgisScopeCard>();
        if (record != null) {
            if (record.getBillNo() != null && !record.getBillNo().trim().isEmpty()) {
                queryWrapper.eq("bill_no", record.getBillNo().trim());
            }
            if (record.getSourceBillNo() != null && !record.getSourceBillNo().trim().isEmpty()) {
                queryWrapper.eq("source_bill_no", record.getSourceBillNo().trim());
            }
            if (record.getSiteNo() != null && !record.getSiteNo().trim().isEmpty()) {
                queryWrapper.eq("site_no", record.getSiteNo().trim());
            }
            if (record.getStatus() != null) {
                queryWrapper.eq("status", record.getStatus());
            }
        }
        queryWrapper.orderByDesc("id");
        String viewerCity;
        try {
            viewerCity = viewerCity();
        } catch (ScopeCardAcceptException e) {
            // 属地未认：台账一页空的也不许把全省漏出去
            return pageTable(new java.util.ArrayList<TMgisScopeCard>(), 0);
        }
        startPage();
        com.github.pagehelper.PageInfo<TMgisScopeCard> page =
                new com.github.pagehelper.PageInfo<TMgisScopeCard>(
                        mgisScopeCardService.selectVisibleTMgisScopeCardList(queryWrapper, viewerCity));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "卡页面", notes = "卡、出处档案、在挂类别、经手人落笔链一次拉齐；"
            + "层级档当场重折与落库档并列，两边对不上即库里被人动过")
    @GetMapping("/{id}")
    @RequiresPermissions("mgisScopeCard:list")
    @ResponseBody
    public AjaxResult detail(@PathVariable("id") Long id) {
        try {
            return retobject(200, mgisScopeCardService.cardView(id, viewerCity()));
        } catch (ScopeCardAcceptException | IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @ApiOperation(value = "看账·省局口径", notes = "从新增类别最多的单位往下排；"
            + "与属地口径同出一处计算，两条路数出来的卡数必须一致，差一笔这一屏没过")
    @GetMapping("/tallyByProvince")
    @RequiresPermissions("mgisScopeCard:list")
    @ResponseBody
    public AjaxResult tallyByProvince() {
        try {
            return retobject(200, mgisScopeCardService.tallyByProvince(viewerCity()));
        } catch (ScopeCardAcceptException | IllegalStateException e) {
            return error(e.getMessage());
        }
    }

    @ApiOperation(value = "看账·属地口径", notes = "把单位按最末一级（区县）归堆再数；"
            + "与省局口径同出一处计算，卡数总计一致这一屏才摆得出来")
    @GetMapping("/tallyByRegion")
    @RequiresPermissions("mgisScopeCard:list")
    @ResponseBody
    public AjaxResult tallyByRegion() {
        try {
            return retobject(200, mgisScopeCardService.tallyByRegion(viewerCity()));
        } catch (ScopeCardAcceptException | IllegalStateException e) {
            return error(e.getMessage());
        }
    }

    @ApiOperation(value = "专业类别目录", notes = "一级大类、二级末级整棵目录树；登记页面拉目录用，只读")
    @GetMapping("/categories")
    @RequiresPermissions("mgisScopeCard:list")
    @ResponseBody
    public AjaxResult categories() {
        return retobject(200, mgisScopeCardService.listScopeCategories());
    }

    /**
     * 看账人的属地箍：有省局权限的看全省（不加箍）；属地市局的只看得见本市
     * ——市名取自登录用户部门名，与档案注册地「省—市—区县」的市段同名。
     * 部门名没挂上的，当未认属地，看不了卡账。
     */
    private String viewerCity() {
        if (SecurityUtils.getSubject().isPermitted("mgisScopeCard:province")) {
            return null;
        }
        TSysUser user = ShiroUtils.getUser();
        String depName = user == null ? null : user.getDepName();
        if (depName == null || depName.trim().isEmpty()) {
            throw new ScopeCardAcceptException("属地未认：本账号未挂属地市（部门名为空），看不了卡账");
        }
        return depName.trim();
    }
}
