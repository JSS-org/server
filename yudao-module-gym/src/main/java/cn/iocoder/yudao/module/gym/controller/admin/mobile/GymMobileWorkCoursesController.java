package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileWorkCoursesVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileWorkCoursesService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/mobile")
@Validated
@Tag(name = "工作台 - 预约签到与课消查询")
public class GymMobileWorkCoursesController {
    @Resource private GymMobileWorkCoursesService service;

    @GetMapping("/bookings")
    @PreAuthorize("@ss.hasPermission('gym:bookings:read')")
    @Operation(summary = "分页查询本人授课且负责会员或授权门店的预约")
    public CommonResult<PageResult<WorkBooking>> bookings(@Valid BookingPage req) {
        return success(service.bookings(req));
    }

    @GetMapping("/bookings/{id}")
    @PreAuthorize("@ss.hasPermission('gym:bookings:read')")
    @Operation(summary = "读取权限范围内预约详情")
    public CommonResult<WorkBooking> booking(@PathVariable("id") @Positive Long id) {
        return success(service.booking(id));
    }

    @GetMapping("/checkins")
    @PreAuthorize("@ss.hasPermission('gym:checkins:read')")
    @Operation(summary = "分页查询负责会员或授权门店的成功入场记录")
    public CommonResult<PageResult<WorkCheckin>> checkins(@Valid CheckinPage req) {
        return success(service.checkins(req));
    }

    @GetMapping("/lesson-records")
    @PreAuthorize("@ss.hasPermission('gym:lessons:read')")
    @Operation(summary = "分页查询本人授课且负责会员或授权门店的已确认课消")
    public CommonResult<PageResult<WorkLessonRecord>> lessons(@Valid LessonPage req) {
        return success(service.lessons(req));
    }
}
