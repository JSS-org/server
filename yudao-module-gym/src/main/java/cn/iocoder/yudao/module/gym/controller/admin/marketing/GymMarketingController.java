package cn.iocoder.yudao.module.gym.controller.admin.marketing;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.marketing.vo.*;
import cn.iocoder.yudao.module.gym.service.marketing.GymMarketingService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.tags.Tag;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@Tag(name="管理后台 - gym 活动与加盟") @RestController @RequestMapping("/gym") @Validated
public class GymMarketingController {
 @Resource private GymMarketingService marketing;
 @GetMapping("/activities") @PreAuthorize("@ss.hasPermission('gym:activities:manage')")
 public CommonResult<PageResult<GymActivityRespVO>> activities(@Valid GymBusinessPageReqVO req){return success(marketing.activities(req));}
 @GetMapping("/activities/{id}") @PreAuthorize("@ss.hasPermission('gym:activities:manage')")
 public CommonResult<GymActivityRespVO> activity(@PathVariable("id") @Positive Long id){return success(marketing.activity(id));}
 @PostMapping("/activities") @PreAuthorize("@ss.hasPermission('gym:activities:manage')")
 public CommonResult<GymActivityRespVO> createActivity(@Valid @RequestBody GymActivitySaveReqVO req){return success(marketing.createActivity(req));}
 @PatchMapping("/activities/{id}") @PreAuthorize("@ss.hasPermission('gym:activities:manage')")
 public CommonResult<GymActivityRespVO> patchActivity(@PathVariable("id") @Positive Long id,@RequestBody GymActivitySaveReqVO req){return success(marketing.patchActivity(id,req));}
 @GetMapping("/activities/{id}/registrations") @PreAuthorize("@ss.hasPermission('gym:activities:manage')")
 public CommonResult<PageResult<GymActivityRegistrationRespVO>> registrations(@PathVariable("id") @Positive Long id,@Valid GymBusinessPageReqVO req){return success(marketing.registrations(id,req));}
 @PostMapping("/activities/{id}/registrations") @PreAuthorize("@ss.hasPermission('gym:activities:manage')")
 public CommonResult<GymActivityRegistrationRespVO> register(@PathVariable("id") @Positive Long id,@Valid @RequestBody GymActivityRegistrationReqVO req){return success(marketing.register(id,req));}
 @DeleteMapping("/activities/{id}/registrations/{registrationId}") @PreAuthorize("@ss.hasPermission('gym:activities:manage')")
 public CommonResult<GymActivityRegistrationRespVO> cancelRegistration(@PathVariable("id") @Positive Long id,@PathVariable("registrationId") @Positive Long registration){return success(marketing.cancelRegistration(id,registration));}
 @GetMapping("/franchise-inquiries") @PreAuthorize("@ss.hasPermission('gym:franchise:manage')")
 public CommonResult<PageResult<GymFranchiseRespVO>> inquiries(@Valid GymBusinessPageReqVO req){return success(marketing.inquiries(req));}
 @GetMapping("/franchise-inquiries/{id}") @PreAuthorize("@ss.hasPermission('gym:franchise:manage')")
 public CommonResult<GymFranchiseRespVO> inquiry(@PathVariable("id") @Positive Long id){return success(marketing.inquiry(id));}
 @PostMapping("/franchise-inquiries") @PreAuthorize("@ss.hasPermission('gym:franchise:manage')")
 public CommonResult<GymFranchiseRespVO> createInquiry(@Valid @RequestBody GymFranchiseSubmitReqVO req){return success(marketing.createInquiry(req));}
 @PatchMapping("/franchise-inquiries/{id}") @PreAuthorize("@ss.hasPermission('gym:franchise:manage')")
 public CommonResult<GymFranchiseRespVO> patchInquiry(@PathVariable("id") @Positive Long id,@Valid @RequestBody GymFranchisePatchReqVO req){return success(marketing.patchInquiry(id,req));}
}
