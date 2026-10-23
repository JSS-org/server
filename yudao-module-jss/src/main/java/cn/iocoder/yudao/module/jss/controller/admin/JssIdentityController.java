package cn.iocoder.yudao.module.jss.controller.admin;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.jss.dal.dataobject.JssUserIdentityDO;
import cn.iocoder.yudao.module.jss.dal.mysql.JssUserIdentityMapper;
import cn.iocoder.yudao.module.jss.service.JssIdentityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理后台 - JSS 会员员工绑定审核")
@RestController
@Validated
@RequestMapping("/jss/identity")
public class JssIdentityController {
    @Resource private JssIdentityService service;
    @Resource private JssUserIdentityMapper identities;

    public record BindReq(
            @NotNull @Positive Long memberUserId,
            @NotNull @Positive Long adminUserId,
            @NotBlank @Size(max = 500) String reason) {}

    public record RevokeReq(@NotNull @Positive Long id, @NotBlank @Size(max = 500) String reason) {}

    @PostMapping("/bind")
    @PreAuthorize("@ss.hasPermission('jss:identity:bind')")
    @Operation(summary = "审核并建立同租户会员员工关联")
    public CommonResult<Long> bind(@Valid @RequestBody BindReq req) {
        return success(service.bind(req.memberUserId(), req.adminUserId(), req.reason()));
    }

    @PostMapping("/revoke")
    @PreAuthorize("@ss.hasPermission('jss:identity:bind')")
    @Operation(summary = "撤销关联并使已有工作台会话失效")
    public CommonResult<Boolean> revoke(@Valid @RequestBody RevokeReq req) {
        service.revoke(req.id(), req.reason());
        return success(true);
    }

    @GetMapping("/get-by-member")
    @PreAuthorize("@ss.hasPermission('jss:identity:query')")
    @Operation(summary = "查询当前租户会员的关联记录")
    public CommonResult<JssUserIdentityDO> getByMember(@RequestParam @Positive Long memberUserId) {
        return success(identities.findByMember(memberUserId));
    }
}
