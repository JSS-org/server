package cn.iocoder.yudao.module.gym.controller.admin.groupbuy;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.admin.groupbuy.vo.GymMeituanCouponHistoryReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.groupbuy.vo.GymMeituanCouponHistoryRespVO;
import cn.iocoder.yudao.module.gym.service.groupbuy.GymGroupBuyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理后台 - gym 团购验券")
@RestController
@RequestMapping("/gym/group-buy")
@Validated
public class GymGroupBuyController {
    @Resource private GymGroupBuyService service;

    @PostMapping("/meituan/coupon-history/query")
    @PreAuthorize("@ss.hasPermission('gym:sales:read')")
    @Operation(summary = "旧餐饮门店验券历史，健身请使用 retail 接口", deprecated = true)
    @ApiAccessLog(responseEnable = false)
    public CommonResult<GymMeituanCouponHistoryRespVO> queryMeituanHistory(
            @Valid @RequestBody GymMeituanCouponHistoryReqVO req) {
        return success(service.queryMeituanHistory(req));
    }
}
