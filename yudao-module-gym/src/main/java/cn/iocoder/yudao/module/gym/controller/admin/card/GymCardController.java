package cn.iocoder.yudao.module.gym.controller.admin.card;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.card.vo.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.service.card.GymCardService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@Tag(name="管理后台 - gym 卡模板及会员卡")
@RestController
@RequestMapping("/gym")
@Validated
public class GymCardController {
    @Resource private GymCardService cards;
    @GetMapping("/card-templates") @Operation(summary="分页查询授权卡模板")
    @PreAuthorize("@ss.hasAnyPermissions('gym:cards:read','gym:bookings:write','gym:sales:settings','gym:approvals:create')")
    public CommonResult<PageResult<GymCardTemplateRespVO>> templates(@Valid GymBusinessPageReqVO req) { return success(cards.templates(req)); }
    @GetMapping("/card-templates/{id}") @Operation(summary="卡模板详情")
    @PreAuthorize("@ss.hasPermission('gym:cards:read')")
    public CommonResult<GymCardTemplateRespVO> template(@PathVariable("id") @Positive Long id) { return success(cards.template(id)); }
    @PostMapping("/card-templates") @Operation(summary="新建卡模板")
    @PreAuthorize("@ss.hasPermission('gym:cards:write')")
    public CommonResult<GymCardTemplateRespVO> create(@RequestBody GymCardTemplateSaveReqVO req) { return success(cards.create(req)); }
    @PatchMapping("/card-templates/{id}") @Operation(summary="修改草稿或切换发布/停用")
    @PreAuthorize("@ss.hasPermission('gym:cards:write')")
    public CommonResult<GymCardTemplateRespVO> patch(@PathVariable("id") @Positive Long id,@RequestBody GymCardTemplateSaveReqVO req) { return success(cards.patch(id,req)); }
    @PostMapping("/card-templates/{id}/versions") @Operation(summary="复制卡模板为新版本草稿")
    @PreAuthorize("@ss.hasPermission('gym:cards:write')")
    public CommonResult<GymCardTemplateRespVO> version(@PathVariable("id") @Positive Long id,@RequestBody GymCardTemplateSaveReqVO req) { return success(cards.version(id,req)); }
    @GetMapping("/members/{id}/cards") @Operation(summary="分页查询会员授权卡权益")
    @PreAuthorize("@ss.hasAnyPermissions('gym:members:read','gym:bookings:write')")
    public CommonResult<PageResult<GymMemberCardRespVO>> memberCards(@PathVariable("id") @Positive Long id,@Valid GymBusinessPageReqVO req) { return success(cards.cards(id,req)); }
}
