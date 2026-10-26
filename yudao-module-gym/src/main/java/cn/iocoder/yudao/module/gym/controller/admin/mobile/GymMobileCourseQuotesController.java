package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCourseQuoteVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileCourseQuoteService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/mobile/course-quotes")
@Validated
@Tag(name = "移动工作台 - 私教报价")
public class GymMobileCourseQuotesController {
    @Resource private GymMobileCourseQuoteService service;

    @PostMapping
    @PreAuthorize("@ss.hasPermission('gym:courses:quote')")
    public CommonResult<Quote> create(
            @Valid @RequestBody Create req, @RequestHeader("Idempotency-Key") String key) {
        return success(service.create(req, key));
    }
}
