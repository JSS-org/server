package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileBookingVO.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileRecordPageReqVO;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileBookingsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "小程序 - 本人私教预约")
@RestController
@RequestMapping("/gym")
@Validated
public class AppGymBookingsController {
    @Resource private GymMobileBookingsService bookings;

    @PostMapping("/bookings")
    @Operation(summary = "创建本人私教预约并预占课时")
    public CommonResult<Booking> create(
            @Valid @RequestBody Create req, @RequestHeader("Idempotency-Key") String key) {
        return success(bookings.create(req, key));
    }

    @PostMapping("/bookings/{id}/cancellations")
    @Operation(summary = "截止时间前取消本人预约并释放预占")
    public CommonResult<Booking> cancel(
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody Cancel req,
            @RequestHeader("Idempotency-Key") String key) {
        return success(bookings.cancel(id, req, key));
    }

    @GetMapping("/me/bookings")
    @Operation(summary = "本人预约分页，日期按课程开始时间筛选")
    public CommonResult<PageResult<Booking>> page(@Valid GymMobileRecordPageReqVO req) {
        return success(bookings.page(req));
    }

    @GetMapping("/me/bookings/{id}")
    @Operation(summary = "本人预约详情")
    public CommonResult<Booking> get(@PathVariable("id") @Positive Long id) {
        return success(bookings.get(id));
    }

    @GetMapping("/me/lesson-records")
    @Operation(summary = "本人课消记录及消费时余额快照")
    public CommonResult<PageResult<LessonRecord>> lessons(@Valid GymMobileRecordPageReqVO req) {
        return success(bookings.lessons(req));
    }

    @GetMapping("/coaches/{id}/sessions")
    @Operation(summary = "教练排期与本人当前预约资格，最多查询31天")
    public CommonResult<PageResult<CourseSession>> sessions(
            @PathVariable("id") @Positive Long id, @Valid Sessions req) {
        return success(bookings.sessions(id, req));
    }
}
