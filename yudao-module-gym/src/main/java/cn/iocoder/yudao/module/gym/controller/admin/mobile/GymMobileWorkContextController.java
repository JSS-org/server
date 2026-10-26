package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileWorkContextVO;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileWorkContextService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/mobile/context")
@PreAuthorize("isAuthenticated()")
@Tag(name = "工作台 - 身份与权限上下文")
public class GymMobileWorkContextController {
    @Resource private GymMobileWorkContextService service;

    @GetMapping
    @Operation(summary = "获取本人身份、角色、菜单权限和门店入口，不授予额外权限")
    public CommonResult<GymMobileWorkContextVO> get() {
        return success(service.get());
    }
}
