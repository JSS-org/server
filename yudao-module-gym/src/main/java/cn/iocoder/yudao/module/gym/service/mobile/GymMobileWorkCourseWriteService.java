package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.module.gym.controller.admin.course.vo.GymBookingSaveReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileWorkCourseWriteVO.*;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileWorkCoursesVO.WorkBooking;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileBookingVO.CourseSession;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.GymEmployeeStoreDO;
import cn.iocoder.yudao.module.gym.dal.mysql.course.*;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.GymEmployeeStoreMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.course.*;
import cn.iocoder.yudao.module.gym.service.member.GymMemberCoachService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.time.temporal.ChronoUnit;

@Service
public class GymMobileWorkCourseWriteService {
    @Resource private GymMobileWorkAccess access;
    @Resource private GymDataScope scope;
    @Resource private GymMemberCoachService memberScope;
    @Resource private GymMobileIdempotency idempotency;
    @Resource private GymBookingRules rules;
    @Resource private GymCourseService courses;
    @Resource private GymMobileWorkCoursesService queries;
    @Resource private GymCourseSessionMapper sessions;
    @Resource private GymCourseProductMapper products;
    @Resource private GymEmployeeStoreMapper employments;
    @Resource private GymStoreMapper stores;
    @Resource private GymBusinessClock clock;
    @Resource private Validator validator;

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public CourseSession session(SessionCreate req, String key) {
        validate(req);
        access.requireAdmin();
        authorizeSession(req);
        return idempotency.execute(
                "work-session-create",
                key,
                req,
                CourseSession.class,
                () -> {
                    rules.requireCoach(req.getCoachId(), req.getStoreId());
                    // Recheck employee ownership after acquiring the shared coach lock.
                    authorizeSession(req);
                    var product = products.selectForUpdate(req.getProductId());
                    if (product == null) throw exception(COURSE_PRODUCT_NOT_EXISTS);
                    validateProduct(product, req.getCoachId(), req.getStoreId());
                    var store = stores.selectById(req.getStoreId());
                    if (store == null || !"active".equals(store.getStatus()))
                        throw exception(MEMBER_STORE_INACTIVE);
                    var from = req.getStartsAt().truncatedTo(ChronoUnit.MICROS);
                    var to = req.getEndsAt().truncatedTo(ChronoUnit.MICROS);
                    if (!from.isAfter(clock.now())
                            || !to.isAfter(from)
                            || !Duration.between(from, to)
                                    .equals(Duration.ofMinutes(product.getDurationMinutes())))
                        throw exception(COURSE_SESSION_INVALID);
                    rules.rejectOverlappingSession(req.getCoachId(), from, to, null);
                    var row = new GymCourseSessionDO();
                    row.setStoreId(req.getStoreId());
                    row.setCoachId(req.getCoachId());
                    row.setCourseProductId(req.getProductId());
                    row.setStartsAt(from);
                    row.setEndsAt(to);
                    row.setCapacity(1);
                    row.setReservedCount(0);
                    row.setStatus("scheduled");
                    sessions.insert(row);
                    return new CourseSession(
                            row.getId().toString(),
                            row.getStoreId().toString(),
                            row.getCoachId().toString(),
                            row.getCourseProductId().toString(),
                            time(from),
                            time(to),
                            1,
                            1,
                            false,
                            "member_context_required");
                });
    }

    private void authorizeSession(SessionCreate req) {
        var ids = scope.readableStoreIds();
        if (ids == null || ids.contains(req.getStoreId())) return;
        if (!req.getCoachId().equals(access.employee())) throw exception(STORE_NO_PERMISSION);
        if (employments.selectCount(
                        new LambdaQueryWrapper<GymEmployeeStoreDO>()
                                .eq(GymEmployeeStoreDO::getEmployeeId, req.getCoachId())
                                .eq(GymEmployeeStoreDO::getStoreId, req.getStoreId())
                                .le(
                                        GymEmployeeStoreDO::getJoinedOn,
                                        clock.now().atZone(GymDateRange.ZONE).toLocalDate()))
                == 0) throw exception(STORE_NO_PERMISSION);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public WorkBooking booking(BookingCreate req, String key) {
        validate(req);
        access.requireAdmin();
        var initial = sessions.selectById(req.getSessionId());
        if (initial == null) throw exception(COURSE_SESSION_NOT_EXISTS);
        access.member(req.getMemberId(), initial.getStoreId());
        memberScope.checkCoachScope(initial.getCoachId(), initial.getStoreId());
        return idempotency.execute(
                "work-booking-create",
                key,
                req,
                WorkBooking.class,
                () -> {
                    rules.requireCoach(initial.getCoachId(), initial.getStoreId());
                    // Product updates also lock the coach first; keep the product active through
                    // booking.
                    var product = products.selectById(initial.getCourseProductId());
                    if (product == null) throw exception(COURSE_PRODUCT_NOT_EXISTS);
                    validateProduct(product, initial.getCoachId(), initial.getStoreId());
                    var request = new GymBookingSaveReqVO();
                    request.setMemberId(req.getMemberId());
                    request.setSessionId(req.getSessionId());
                    request.setMemberCoursePackId(req.getCoursePackId());
                    var result = courses.createBooking(request);
                    return queries.booking(Long.valueOf(result.getId()));
                });
    }

    private void validateProduct(GymCourseProductDO product, Long coach, Long store) {
        if (!"active".equals(product.getStatus())
                || !"private".equals(product.getKind())
                || !coach.equals(product.getCoachId())
                || !store.equals(product.getStoreId())) throw exception(COURSE_PRODUCT_INVALID);
    }

    private void validate(Object req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    }

    private static String time(Instant value) {
        return value.atZone(GymDateRange.ZONE).toOffsetDateTime().toString();
    }
}
