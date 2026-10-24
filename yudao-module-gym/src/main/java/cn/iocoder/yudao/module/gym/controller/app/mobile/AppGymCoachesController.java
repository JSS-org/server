package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCoachVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileCoachService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "小程序 - 公开教练")
@RestController
@RequestMapping("/gym/coaches")
@PermitAll
@Validated
public class AppGymCoachesController {
    @Resource private GymMobileCoachService service;

    @GetMapping
    @Operation(summary = "分页查询本门店已发布教练")
    public CommonResult<PageResult<Coach>> page(@Valid CoachPage req) {
        return success(service.page(req));
    }

    @GetMapping("/{id}")
    @Operation(summary = "读取已发布的教练介绍和公开资质")
    public CommonResult<Coach> detail(
            @PathVariable("id") @Positive Long id,
            @RequestParam("storeId") @Positive Long storeId) {
        return success(service.detail(id, storeId));
    }
}
