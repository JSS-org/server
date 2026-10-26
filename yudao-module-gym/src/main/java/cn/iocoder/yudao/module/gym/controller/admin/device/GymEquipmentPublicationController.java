package cn.iocoder.yudao.module.gym.controller.admin.device;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.MOBILE_REQUEST_INVALID;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileEquipmentVO.Scene;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileEquipmentService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/equipment")
@Validated
@Tag(name = "管理后台 - 器械公开教学发布")
@PreAuthorize("@ss.hasPermission('gym:equipment:write')")
public class GymEquipmentPublicationController {
    @Resource private GymMobileEquipmentService service;

    @PostMapping("/{id}/public-scenes")
    public CommonResult<Scene> publish(
            @PathVariable("id") @Positive Long id, @RequestBody(required = false) String body) {
        if (body != null && !body.isBlank()) throw exception(MOBILE_REQUEST_INVALID);
        return success(service.publish(id));
    }

    @DeleteMapping("/{id}/public-scenes")
    public CommonResult<Boolean> revoke(@PathVariable("id") @Positive Long id) {
        return success(service.revoke(id));
    }
}
