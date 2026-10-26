package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCourseQuoteVO.Quote;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileCourseQuoteService;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.constraints.Positive;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/course-quotes")
@Validated
@Tag(name = "小程序 - 本人可用私教报价")
public class AppGymCourseQuotesController {
    @Resource private GymMobileCourseQuoteService service;

    @GetMapping("/{id}")
    public CommonResult<Quote> get(@PathVariable("id") @Positive Long id) {
        return success(service.get(id));
    }
}
