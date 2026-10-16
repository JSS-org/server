package cn.iocoder.yudao.module.gym.controller.admin.store;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.store.vo.*;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理后台 - gym 门店")
@RestController
@RequestMapping("/gym/stores")
@Validated
public class GymStoreController {
    @Resource private GymStoreService service;

    @GetMapping
    @Operation(summary = "分页查询授权范围资料")
    @PreAuthorize("@ss.hasPermission('gym:stores:read')")
    public CommonResult<PageResult<GymStoreRespVO>> page(@Valid GymPageReqVO req) {
        return success(service.page(req));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询授权范围详情")
    @PreAuthorize("@ss.hasPermission('gym:stores:read')")
    public CommonResult<GymStoreRespVO> get(@PathVariable("id") @Positive Long id) {
        return success(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增资料")
    @PreAuthorize("@ss.hasPermission('gym:stores:write')")
    public CommonResult<GymStoreRespVO> create(@RequestBody GymStoreSaveReqVO req) {
        return success(service.create(req));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "部分更新资料", description = "省略或 null 字段保持原值；嵌套结构整体替换；服务端校验合并后资料")
    @PreAuthorize("@ss.hasPermission('gym:stores:write')")
    public CommonResult<GymStoreRespVO> patch(
            @PathVariable("id") @Positive Long id, @RequestBody GymStoreSaveReqVO req) {
        return success(service.patch(id, req));
    }

    @PutMapping("/{id}/media")
    @Operation(summary = "替换门店展示媒体")
    @PreAuthorize("@ss.hasPermission('gym:stores:write')")
    public CommonResult<Boolean> media(
            @PathVariable("id") @Positive Long id, @Valid @RequestBody GymStoreMediaReqVO req) {
        return success(service.saveMedia(id, req));
    }
}
