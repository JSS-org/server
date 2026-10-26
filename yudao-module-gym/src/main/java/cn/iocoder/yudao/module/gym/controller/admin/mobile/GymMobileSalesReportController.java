package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileSalesReportVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileSalesReportService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/mobile/reports")
@Validated
@PreAuthorize("@ss.hasPermission('gym:reports:read')")
@Tag(name = "移动工作台 - 销售报表")
public class GymMobileSalesReportController {
    @Resource private GymMobileSalesReportService service;

    @GetMapping("/sales")
    public CommonResult<Result> sales(@Valid Query req) {
        return success(service.sales(req));
    }
}
