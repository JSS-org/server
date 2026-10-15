package cn.iocoder.yudao.module.gym.controller.admin.payroll;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.payroll.vo.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.service.payroll.GymPayrollService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.tags.Tag;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@Tag(name="管理后台 - gym 工资计算") @RestController @RequestMapping("/gym/payrolls") @Validated
public class GymPayrollController {
 @Resource private GymPayrollService payrolls;
 @GetMapping @PreAuthorize("@ss.hasPermission('gym:payroll:read')")
 public CommonResult<PageResult<GymPayrollRespVO>> page(@Valid GymBusinessPageReqVO req){return success(payrolls.page(req));}
 @GetMapping("/{id}") @PreAuthorize("@ss.hasPermission('gym:payroll:read')")
 public CommonResult<GymPayrollRespVO> get(@PathVariable("id") @Positive Long id){return success(payrolls.get(id));}
 @PostMapping @PreAuthorize("@ss.hasPermission('gym:payroll:calculate')")
 public CommonResult<GymPayrollRespVO> create(@Valid @RequestBody GymPayrollCalculateReqVO req){return success(payrolls.calculate(req));}
 @PostMapping("/{id}/calculations") @PreAuthorize("@ss.hasPermission('gym:payroll:calculate')")
 public CommonResult<GymPayrollRespVO> recalculate(@PathVariable("id") @Positive Long id){return success(payrolls.recalculate(id));}
 @PatchMapping("/{id}") @PreAuthorize("@ss.hasPermission('gym:payroll:confirm')")
 public CommonResult<GymPayrollRespVO> confirm(@PathVariable("id") @Positive Long id,@Valid @RequestBody GymPayrollConfirmReqVO req){return success(payrolls.confirm(id));}
}
