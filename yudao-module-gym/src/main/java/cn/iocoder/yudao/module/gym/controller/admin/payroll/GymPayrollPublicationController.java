package cn.iocoder.yudao.module.gym.controller.admin.payroll;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.service.payroll.GymPayrollPublicationService;
import cn.iocoder.yudao.module.gym.service.payroll.GymPayrollPublicationService.Publication;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/payrolls")
@Validated
@Tag(name = "管理后台 - 工资发布")
public class GymPayrollPublicationController {
    @Resource private GymPayrollPublicationService service;

    @PostMapping("/{id}/publications")
    @PreAuthorize("@ss.hasPermission('gym:payroll:confirm')")
    @Operation(summary = "发布已确认工资供员工本人查看（可重试）")
    public CommonResult<Publication> publish(@PathVariable("id") @Positive Long id) {
        return success(service.publish(id));
    }
}
