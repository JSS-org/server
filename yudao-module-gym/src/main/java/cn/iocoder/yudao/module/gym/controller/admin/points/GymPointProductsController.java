package cn.iocoder.yudao.module.gym.controller.admin.points;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.points.vo.GymPointProductVO.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointProductsVO.Query;
import cn.iocoder.yudao.module.gym.service.points.GymPointProductsService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/point-products")
@Validated
@Tag(name = "管理后台 - 积分兑换商品")
@PreAuthorize("@ss.hasPermission('gym:points:products')")
public class GymPointProductsController {
    @Resource private GymPointProductsService service;

    @GetMapping
    public CommonResult<PageResult<Product>> page(@Valid Query req) {
        return success(service.manage(req));
    }

    @PostMapping
    public CommonResult<Product> create(@Valid @RequestBody Save req) {
        return success(service.save(null, req));
    }

    @PutMapping("/{id}")
    public CommonResult<Product> update(
            @PathVariable("id") @Positive Long id, @Valid @RequestBody Save req) {
        return success(service.save(id, req));
    }
}
