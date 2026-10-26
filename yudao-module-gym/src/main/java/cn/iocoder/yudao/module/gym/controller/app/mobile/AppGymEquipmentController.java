package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileEquipmentVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileEquipmentService;

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
@Tag(name = "小程序 - 器械扫码与教学")
public class AppGymEquipmentController {
    @Resource private GymMobileEquipmentService service;

    @PostMapping("/qr-resolutions")
    public CommonResult<Resolution> resolve(@Valid @RequestBody Resolve req) {
        return success(service.resolve(req));
    }

    @GetMapping("/equipment/{id}/tutorials")
    public CommonResult<Tutorials> tutorials(@PathVariable("id") @Positive Long id) {
        return success(service.tutorials(id));
    }
}
