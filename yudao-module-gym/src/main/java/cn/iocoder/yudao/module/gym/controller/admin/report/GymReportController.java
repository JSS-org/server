package cn.iocoder.yudao.module.gym.controller.admin.report;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.admin.report.vo.*;
import cn.iocoder.yudao.module.gym.service.report.GymReportService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.tags.Tag;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@Tag(name="管理后台 - gym 经营报表") @RestController @RequestMapping("/gym/reports") @Validated
@PreAuthorize("@ss.hasPermission('gym:reports:read')")
public class GymReportController {
 @Resource private GymReportService reports;
 @GetMapping("/performance") public CommonResult<GymReportRespVO.Page<GymReportRespVO.Performance>> performance(@Valid GymReportReqVO req){return success(reports.performance(req));}
 @GetMapping("/members") public CommonResult<GymReportRespVO.Page<GymReportRespVO.Members>> members(@Valid GymReportReqVO req){return success(reports.members(req));}
 @GetMapping("/member-cards") public CommonResult<GymReportRespVO.Page<GymReportRespVO.MemberCards>> memberCards(@Valid GymReportReqVO req){return success(reports.memberCards(req));}
 @GetMapping("/dashboard") public CommonResult<GymReportRespVO.Dashboard> dashboard(@Valid GymReportReqVO req){return success(reports.dashboard(req));}
}
