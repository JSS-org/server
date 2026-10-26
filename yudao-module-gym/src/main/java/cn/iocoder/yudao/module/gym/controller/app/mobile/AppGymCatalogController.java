package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCatalogVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileCatalogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym")
@Validated
@PermitAll
@Tag(name = "小程序 - 公开商品目录")
public class AppGymCatalogController {
    @Resource private GymMobileCatalogService catalog;

    @GetMapping("/card-products")
    @Operation(summary = "查询门店有效发售的卡商品")
    public CommonResult<PageResult<Product>> cards(@Valid StorePage req) {
        return success(catalog.cards(req));
    }

    @GetMapping("/course-products")
    @Operation(summary = "查询当前在职教练的有效私教商品")
    public CommonResult<PageResult<Product>> courses(@Valid CoursePage req) {
        return success(catalog.courses(req));
    }

    @GetMapping("/subscription-products")
    @Operation(summary = "查询已发布续费条款的包月商品；真实代扣仍不可用")
    public CommonResult<PageResult<Product>> subscriptions(@Valid StorePage req) {
        return success(catalog.subscriptions(req));
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "公开商品详情；可指定销售门店")
    public CommonResult<Product> product(
            @PathVariable("id") @Positive Long id,
            @RequestParam("productType") @Pattern(regexp = "card|course|subscription") String type,
            @RequestParam(value = "storeId", required = false) @Positive Long store) {
        return success(catalog.product(id, type, store));
    }
}
