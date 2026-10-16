package cn.iocoder.yudao.module.gym.controller.admin.employee;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.employee.vo.*;
import cn.iocoder.yudao.module.gym.service.employee.GymEmployeeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理后台 - gym 员工")
@RestController
@RequestMapping("/gym/employees")
@Validated
public class GymEmployeeController {
    @Resource private GymEmployeeService service;

    @GetMapping
    @Operation(summary = "分页查询授权范围资料")
    @PreAuthorize("@ss.hasPermission('gym:employees:read')")
    public CommonResult<PageResult<GymEmployeeRespVO>> page(@Valid GymPageReqVO req) {
        return success(service.page(req));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询授权范围详情")
    @PreAuthorize("@ss.hasPermission('gym:employees:read')")
    public CommonResult<GymEmployeeRespVO> get(@PathVariable("id") @Positive Long id) {
        return success(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增资料")
    @PreAuthorize("@ss.hasPermission('gym:employees:write')")
    public CommonResult<GymEmployeeRespVO> create(@RequestBody GymEmployeeSaveReqVO req) {
        return success(service.create(req));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "部分更新资料", description = "省略或 null 字段保持原值；嵌套结构整体替换；服务端校验合并后资料")
    @PreAuthorize("@ss.hasPermission('gym:employees:write')")
    public CommonResult<GymEmployeeRespVO> patch(
            @PathVariable("id") @Positive Long id, @RequestBody GymEmployeeSaveReqVO req) {
        return success(service.patch(id, req));
    }
}
