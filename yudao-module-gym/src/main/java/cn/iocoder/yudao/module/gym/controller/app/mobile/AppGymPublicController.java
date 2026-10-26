package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePublicVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobilePublicService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym")
@Validated
@PermitAll
@Tag(name = "小程序 - 公开门店与展示")
public class AppGymPublicController {
    @Resource private GymMobilePublicService content;

    @GetMapping("/stores")
    @Operation(summary = "公开营业门店，位置排序先于分页")
    public CommonResult<PageResult<StoreBrief>> stores(@Valid StorePage req) {
        return success(content.stores(req));
    }

    @GetMapping("/stores/{id}")
    @Operation(summary = "公开门店详情及已发布素材")
    public CommonResult<StoreDetail> store(@PathVariable("id") @Positive Long id) {
        return success(content.store(id));
    }

    @GetMapping("/stores/{id}/home")
    @Operation(summary = "公开首页、能力开关和已生效公告")
    public CommonResult<Home> home(@PathVariable("id") @Positive Long id) {
        return success(content.home(id));
    }

    @GetMapping("/stores/{id}/occupancy")
    @Operation(summary = "成功入场次数与实时人数可用状态")
    public CommonResult<Occupancy> occupancy(@PathVariable("id") @Positive Long id) {
        return success(content.occupancy(id));
    }

    @GetMapping("/client-settings")
    @Operation(summary = "公开入口封面、App福利及加盟资料")
    public CommonResult<ClientSettings> settings(
            @RequestParam(value = "storeId", required = false) @Positive Long store) {
        return success(content.settings(store));
    }
}
