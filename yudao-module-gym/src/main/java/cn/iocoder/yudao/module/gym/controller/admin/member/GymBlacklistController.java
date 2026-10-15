package cn.iocoder.yudao.module.gym.controller.admin.member;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.member.vo.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.service.member.GymMemberRecordsService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@Tag(name="管理后台 - gym Blacklist")
@RestController
@RequestMapping("/gym/blacklists")
@Validated
public class GymBlacklistController {
    @Resource private GymMemberRecordsService records;
    @GetMapping @Operation(summary="分页查询授权范围记录")
    @PreAuthorize("@ss.hasPermission('gym:blacklist:read')")
    public CommonResult<PageResult<GymBlacklistRespVO>> page(@Valid GymBusinessPageReqVO req) { return success(records.blacklists(req)); }
    @GetMapping("/{id}") @Operation(summary="查询授权记录详情")
    @PreAuthorize("@ss.hasPermission('gym:blacklist:read')")
    public CommonResult<GymBlacklistRespVO> get(@PathVariable("id") @Positive Long id) { return success(records.blacklist(id)); }
    @PostMapping @Operation(summary="新增业务记录")
    @PreAuthorize("@ss.hasPermission('gym:blacklist:write')")
    public CommonResult<GymBlacklistRespVO> create(@RequestBody @Valid GymBlacklistSaveReqVO req) { return success(records.createBlacklist(req)); }
    @PatchMapping("/{id}") @Operation(summary="部分更新业务记录")
    @PreAuthorize("@ss.hasPermission('gym:blacklist:write')")
    public CommonResult<GymBlacklistRespVO> patch(@PathVariable("id") @Positive Long id,@RequestBody GymBlacklistSaveReqVO req) { return success(records.patchBlacklist(id,req)); }
}
