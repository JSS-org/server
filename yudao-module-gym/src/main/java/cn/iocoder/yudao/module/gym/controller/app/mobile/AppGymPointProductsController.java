package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointProductsVO.*;
import cn.iocoder.yudao.module.gym.service.points.GymPointProductsService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/point-products")
@Validated
@Tag(name = "会员 - 积分兑换商品")
public class AppGymPointProductsController {
    @Resource private GymPointProductsService service;

    @GetMapping
    public CommonResult<PageResult<Product>> page(@Valid Query req) {
        return success(service.catalog(req));
    }
}
