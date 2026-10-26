package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointsVO.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointsVO.Record;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobilePointsService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/me")
@Validated
@Tag(name = "会员 - 本人积分账本")
public class AppGymPointsController {
    @Resource private GymMobilePointsService service;

    @GetMapping("/points")
    public CommonResult<Balance> balance() {
        return success(service.balance());
    }

    @GetMapping("/point-records")
    public CommonResult<PageResult<Record>> records(@Valid Query req) {
        return success(service.records(req));
    }
}
