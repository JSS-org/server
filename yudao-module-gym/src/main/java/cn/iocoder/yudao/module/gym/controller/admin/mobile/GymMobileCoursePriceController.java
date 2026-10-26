package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileApprovalsVO.Approval;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileCoursePriceVO;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileCoursePriceService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/mobile/course-price-applications")
@Validated
@Tag(name = "移动工作台 - 私教专属改价申请")
public class GymMobileCoursePriceController {
    @Resource private GymMobileCoursePriceService service;

    @PostMapping
    @PreAuthorize("@ss.hasPermission('gym:approvals:create')")
    public CommonResult<Approval> create(
            @Valid @RequestBody GymMobileCoursePriceVO req,
            @RequestHeader("Idempotency-Key") String key) {
        return success(service.create(req, key));
    }
}
