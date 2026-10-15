package cn.iocoder.yudao.module.gym.controller.admin.course;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.gym.controller.admin.course.vo.*;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO;
import cn.iocoder.yudao.module.gym.service.course.GymCourseService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@Tag(name="管理后台 - gym 私教、预约及签课")
@RestController
@RequestMapping("/gym")
@Validated
public class GymCourseController {
    @Resource private GymCourseService courses;
    @GetMapping("/coaches") @Operation(summary="查询授权门店可指派教练员工")
    @PreAuthorize("@ss.hasAnyPermissions('gym:courses:read','gym:courses:write')")
    public CommonResult<PageResult<Map<String,Object>>> coaches(@Valid GymBusinessPageReqVO req) { return success(courses.coaches(req)); }
    @GetMapping("/course-products") @Operation(summary="私教产品分页")
    @PreAuthorize("@ss.hasAnyPermissions('gym:courses:read','gym:approvals:create')")
    public CommonResult<PageResult<GymCourseProductRespVO>> products(@Valid GymBusinessPageReqVO req) { return success(courses.products(req)); }
    @GetMapping("/course-products/{id}") @Operation(summary="私教产品详情")
    @PreAuthorize("@ss.hasPermission('gym:courses:read')")
    public CommonResult<GymCourseProductRespVO> product(@PathVariable("id") @Positive Long id) { return success(courses.product(id)); }
    @PostMapping("/course-products") @Operation(summary="新增私教产品")
    @PreAuthorize("@ss.hasPermission('gym:courses:write')")
    public CommonResult<GymCourseProductRespVO> createProduct(@RequestBody GymCourseProductSaveReqVO req) { return success(courses.createProduct(req)); }
    @PatchMapping("/course-products/{id}") @Operation(summary="修改私教资料；价格变更须审批")
    @PreAuthorize("@ss.hasPermission('gym:courses:write')")
    public CommonResult<GymCourseProductRespVO> patchProduct(@PathVariable("id") @Positive Long id,@RequestBody GymCourseProductSaveReqVO req) { return success(courses.patchProduct(id,req)); }
    @GetMapping("/course-sessions") @Operation(summary="私教排期分页")
    @PreAuthorize("@ss.hasAnyPermissions('gym:courses:read','gym:bookings:write')")
    public CommonResult<PageResult<GymCourseSessionRespVO>> sessions(@Valid GymBusinessPageReqVO req) { return success(courses.sessions(req)); }
    @GetMapping("/course-sessions/{id}") @Operation(summary="私教排期详情")
    @PreAuthorize("@ss.hasPermission('gym:courses:read')")
    public CommonResult<GymCourseSessionRespVO> session(@PathVariable("id") @Positive Long id) { return success(courses.session(id)); }
    @PostMapping("/course-sessions") @Operation(summary="新增容量1的私教排期；校验教练时间冲突")
    @PreAuthorize("@ss.hasPermission('gym:courses:write')")
    public CommonResult<GymCourseSessionRespVO> createSession(@RequestBody GymCourseSessionSaveReqVO req) { return success(courses.createSession(req)); }
    @PatchMapping("/course-sessions/{id}") @Operation(summary="修改或取消排期；取消原子释放未签课预约权益")
    @PreAuthorize("@ss.hasPermission('gym:courses:write')")
    public CommonResult<GymCourseSessionRespVO> patchSession(@PathVariable("id") @Positive Long id,@RequestBody GymCourseSessionSaveReqVO req) { return success(courses.patchSession(id,req)); }
    @GetMapping("/members/{id}/course-packs") @Operation(summary="会员授权范围私教权益分页")
    @PreAuthorize("@ss.hasAnyPermissions('gym:members:read','gym:bookings:write')")
    public CommonResult<PageResult<GymCoursePackRespVO>> packs(@PathVariable("id") @Positive Long id,@Valid GymBusinessPageReqVO req) { return success(courses.packs(id,req)); }
    @GetMapping("/bookings") @Operation(summary="预约分页")
    @PreAuthorize("@ss.hasPermission('gym:bookings:read')")
    public CommonResult<PageResult<GymBookingRespVO>> bookings(@Valid GymBusinessPageReqVO req) { return success(courses.bookings(req)); }
    @GetMapping("/bookings/{id}") @Operation(summary="预约详情")
    @PreAuthorize("@ss.hasPermission('gym:bookings:read')")
    public CommonResult<GymBookingRespVO> booking(@PathVariable("id") @Positive Long id) { return success(courses.booking(id)); }
    @PostMapping("/bookings") @Operation(summary="预约并原子预占卡或私教课时")
    @PreAuthorize("@ss.hasPermission('gym:bookings:write')")
    public CommonResult<GymBookingRespVO> createBooking(@RequestBody @Valid GymBookingSaveReqVO req) { return success(courses.createBooking(req)); }
    @PatchMapping("/bookings/{id}") @Operation(summary="取消预约并释放预占；已签课不可取消")
    @PreAuthorize("@ss.hasPermission('gym:bookings:write')")
    public CommonResult<GymBookingRespVO> patchBooking(@PathVariable("id") @Positive Long id,@RequestBody GymBookingSaveReqVO req) { return success(courses.patchBooking(id,req)); }
    @PostMapping("/bookings/{id}/checkins") @Operation(summary="有权管理人员记录本课程当日入场签到")
    @PreAuthorize("@ss.hasPermission('gym:bookings:write')")
    public CommonResult<GymCheckinRespVO> checkIn(@PathVariable("id") @Positive Long id) { return success(courses.checkIn(id)); }
    @PostMapping("/bookings/{id}/lessons") @Operation(summary="签到后确认签课并幂等扣课；人工确认留痕")
    @PreAuthorize("@ss.hasPermission('gym:bookings:write')")
    public CommonResult<GymLessonRecordRespVO> confirmLesson(@PathVariable("id") @Positive Long id) { return success(courses.confirmLesson(id)); }
    @GetMapping("/checkins") @Operation(summary="入场记录分页")
    @PreAuthorize("@ss.hasPermission('gym:checkins:read')")
    public CommonResult<PageResult<GymCheckinRespVO>> checkins(@Valid GymBusinessPageReqVO req) { return success(courses.checkins(req)); }
    @GetMapping("/checkins/summary") @Operation(summary="授权门店当天和昨日签到汇总")
    @PreAuthorize("@ss.hasPermission('gym:checkins:read')")
    public CommonResult<Map<String,Long>> summary(@Valid GymBusinessPageReqVO req) { return success(courses.checkinSummary(req)); }
    @GetMapping("/lesson-records") @Operation(summary="授权范围签课记录分页")
    @PreAuthorize("@ss.hasPermission('gym:lessons:read')")
    public CommonResult<PageResult<GymLessonRecordRespVO>> lessons(@Valid GymBusinessPageReqVO req) { return success(courses.lessons(req)); }
}
