package cn.iocoder.yudao.module.gym.controller.admin.approval;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.approval.vo.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.service.approval.GymApprovalService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.tags.Tag;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@Tag(name="管理后台 - gym 审批记录") @RestController @RequestMapping("/gym/approvals") @Validated
public class GymApprovalController {
 @Resource private GymApprovalService approvals;
 @GetMapping @PreAuthorize("@ss.hasPermission('gym:approvals:read')")
 public CommonResult<PageResult<GymApprovalRespVO>> page(@Valid GymBusinessPageReqVO req){return success(approvals.page(req));}
 @GetMapping("/{id}") @PreAuthorize("@ss.hasPermission('gym:approvals:read')")
 public CommonResult<GymApprovalRespVO> get(@PathVariable("id") @Positive Long id){return success(approvals.get(id));}
 @PostMapping @PreAuthorize("@ss.hasPermission('gym:approvals:create')")
 public CommonResult<GymApprovalRespVO> create(@Valid @RequestBody GymApprovalCreateReqVO req,@RequestHeader(value="Idempotency-Key",required=false) String key){return success(approvals.create(req,key));}
 @PostMapping("/{id}/decisions") @PreAuthorize("@ss.hasPermission('gym:approvals:decide')")
 public CommonResult<GymApprovalRespVO> decide(@PathVariable("id") @Positive Long id,@Valid @RequestBody GymApprovalDecisionReqVO req){return success(approvals.decide(id,req));}
}
