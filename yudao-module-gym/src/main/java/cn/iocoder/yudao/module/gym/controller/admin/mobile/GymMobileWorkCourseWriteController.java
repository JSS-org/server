package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileWorkCourseWriteVO.*;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileWorkCoursesVO.WorkBooking;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileBookingVO.CourseSession;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileWorkCourseWriteService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/gym/mobile")
@Tag(name = "工作台 - 排课与代会员预约")
public class GymMobileWorkCourseWriteController {
    @Resource private GymMobileWorkCourseWriteService service;

    @PostMapping("/course-sessions")
    @PreAuthorize("@ss.hasPermission('gym:courses:write')")
    @Operation(summary = "幂等创建本人或授权门店私教排期")
    public CommonResult<CourseSession> session(
            @Valid @RequestBody SessionCreate req, @RequestHeader("Idempotency-Key") String key) {
        return success(service.session(req, key));
    }

    @PostMapping("/bookings")
    @PreAuthorize("@ss.hasPermission('gym:bookings:write')")
    @Operation(summary = "幂等代会员预约并预占实际课包余额")
    public CommonResult<WorkBooking> booking(
            @Valid @RequestBody BookingCreate req, @RequestHeader("Idempotency-Key") String key) {
        return success(service.booking(req, key));
    }
}
