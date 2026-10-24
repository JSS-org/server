package cn.iocoder.yudao.module.gym.service;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileBookingVO.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileIdentityVO.Initialize;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileRecordPageReqVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.*;
import cn.iocoder.yudao.module.gym.dal.mysql.course.*;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileBookingProperties;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.course.GymBookingRules;
import cn.iocoder.yudao.module.gym.service.mobile.*;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;

import jakarta.annotation.Resource;

import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Import({
    GymMobileIdentityService.class,
    GymMobileIdempotency.class,
    GymMobileBookingsService.class,
    GymBookingRules.class,
    GymMobileBookingProperties.class,
    GymBusinessMutex.class,
    GymBusinessClock.class,
    GymMobileMemberServiceTest.Config.class
})
class GymMobileBookingsServiceTest extends BaseDbUnitTest {
    static final Instant NOW = Instant.parse("2026-10-23T04:00:00Z");
    @Resource GymMobileIdentityService identities;
    @Resource GymMobileBookingsService service;
    @Resource GymCourseSessionMapper sessions;
    @Resource GymCourseProductMapper products;
    @Resource GymCoursePackMapper packs;
    @Resource GymBookingMapper bookings;
    @Resource GymLessonRecordMapper lessons;
    @Resource GymCheckinMapper checkins;
    @Resource GymMobileBookingProperties properties;
    @Resource JdbcTemplate jdbc;
    @MockitoBean MemberUserApi users;
    @MockitoBean GymBusinessClock clock;
    Long member;
    GymCourseSessionDO session;
    GymCoursePackDO pack;

    void login(long tenant, long user, int type) {
        TenantContextHolder.setTenantId(tenant);
        SecurityFrameworkUtils.setLoginUser(
                new LoginUser().setId(user).setUserType(type).setTenantId(tenant),
                new MockHttpServletRequest());
    }

    @BeforeEach
    void setup() {
        login(1, 10, 1);
        when(clock.now()).thenReturn(NOW);
        properties.setCancelBeforeMinutes(0);
        jdbc.update(
                "INSERT INTO gym_store(id,code,name,description,area_sqm,features,address,opens,closes,status,tenant_id) VALUES (101,'S101','门店','',100,'[]','地址','06:00','23:00','active',1)");
        coach(201L);
        member = initialize(10L);
        session = session(201L, NOW.plusSeconds(3600));
        pack = pack(member, session);
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    Long initialize(Long user) {
        login(1, user, 1);
        var profile = new MemberUserRespDTO();
        profile.setId(user);
        profile.setStatus(0);
        profile.setMobile("138000000" + user);
        profile.setNickname("会员" + user);
        when(users.getUser(user)).thenReturn(profile);
        var req = new Initialize();
        req.setStoreId(101L);
        req.setSource("test");
        return Long.valueOf(identities.initialize(req, "identity-" + user).memberId());
    }

    void coach(Long id) {
        jdbc.update(
                "INSERT INTO gym_employee(id,employee_no,name,phone,hired_on,status,tenant_id) VALUES (?,?,'教练','13900000001','2020-01-01','active',1)",
                id,
                "C" + id);
        jdbc.update(
                "INSERT INTO gym_employee_store(employee_id,store_id,is_primary,is_manager,joined_on,tenant_id) VALUES (?,101,TRUE,FALSE,'2020-01-01',1)",
                id);
    }

    GymCourseSessionDO session(Long coach, Instant from) {
        var product = new GymCourseProductDO();
        product.setStoreId(101L);
        product.setCoachId(coach);
        product.setName("私教");
        product.setKind("private");
        product.setSessionCount(10);
        product.setDurationMinutes(60);
        product.setPrice(BigDecimal.TEN);
        product.setStatus("active");
        products.insert(product);
        var s = new GymCourseSessionDO();
        s.setStoreId(101L);
        s.setCoachId(coach);
        s.setCourseProductId(product.getId());
        s.setStartsAt(from);
        s.setEndsAt(from.plusSeconds(3600));
        s.setCapacity(1);
        s.setReservedCount(0);
        s.setStatus("scheduled");
        sessions.insert(s);
        return s;
    }

    GymCoursePackDO pack(Long owner, GymCourseSessionDO s) {
        var row = new GymCoursePackDO();
        row.setMemberId(owner);
        row.setStoreId(s.getStoreId());
        row.setAssignedCoachId(s.getCoachId());
        row.setCourseProductId(s.getCourseProductId());
        row.setName("课包");
        row.setStartsAt(NOW.minusSeconds(3600));
        row.setExpiresAt(NOW.plusSeconds(86400));
        row.setTotalSessions(10);
        row.setRemainingSessions(10);
        row.setReservedSessions(0);
        row.setStatus("active");
        row.setPriceSnapshot(BigDecimal.TEN);
        row.setSourceType("test");
        row.setSourceId(UUID.randomUUID().toString());
        packs.insert(row);
        return row;
    }

    Create request(GymCourseSessionDO s, GymCoursePackDO p) {
        var req = new Create();
        req.setSessionId(s.getId());
        req.setCoursePackId(p.getId());
        return req;
    }

    Booking create() {
        return service.create(request(session, pack), "book-first");
    }

    Cancel reason(String text) {
        var req = new Cancel();
        req.setReason(text);
        return req;
    }

    void assertBalance(int reserved) {
        assertEquals(reserved, packs.selectById(pack.getId()).getReservedSessions());
        assertEquals(10, packs.selectById(pack.getId()).getRemainingSessions());
        assertEquals(reserved, sessions.selectById(session.getId()).getReservedCount());
    }

    @Test
    void createReplaysAndNewKeyDoesNotReserveTwice() {
        var first = create();
        assertEquals(first, create());
        assertEquals(first, service.create(request(session, pack), "book-second"));
        assertBalance(1);
        assertFalse(first.checkedIn());
        assertEquals("2026-10-23T13:00+08:00", first.cancelDeadline());
        var another = pack(member, session);
        assertServiceException(
                () -> service.create(request(session, another), "book-first"),
                MOBILE_IDEMPOTENCY_CONFLICT);
        assertServiceException(
                () -> service.create(request(session, another), "book-third"), BOOKING_INVALID);
        assertBalance(1);
    }

    @Test
    void cancelReleasesOncePersistsReasonAndOldRetryCannotCancelRebooking() {
        var first = create();
        Long id = Long.valueOf(first.id());
        var result = service.cancel(id, reason("临时有事"), "cancel-first");
        assertEquals("cancelled", result.status());
        assertBalance(0);
        assertEquals("临时有事", bookings.selectById(id).getCancellationReason());
        assertEquals(result, service.cancel(id, reason("临时有事"), "cancel-first"));
        assertEquals(result, service.cancel(id, reason("新请求"), "cancel-again"));
        assertBalance(0);
        assertServiceException(
                () -> service.cancel(id, reason("改变原因"), "cancel-first"),
                MOBILE_IDEMPOTENCY_CONFLICT);
        assertEquals(first.id(), service.create(request(session, pack), "rebook-key").id());
        assertBalance(1);
        assertNull(bookings.selectById(id).getCancellationReason());
        assertEquals(result, service.cancel(id, reason("临时有事"), "cancel-first"));
        assertBalance(1);
        assertEquals("confirmed", service.get(id).status());
    }

    @Test
    void cancellationDeadlineIsExclusiveAndConfigurableWithoutAutomaticConsumption() {
        properties.setCancelBeforeMinutes(30);
        var booking = create();
        Long id = Long.valueOf(booking.id());
        assertEquals("2026-10-23T12:30+08:00", booking.cancelDeadline());
        when(clock.now()).thenReturn(NOW.plusSeconds(1800));
        assertServiceException(
                () -> service.cancel(id, reason("过期"), "late-cancel"),
                MOBILE_CANCEL_DEADLINE_PASSED);
        assertBalance(1);
        when(clock.now()).thenReturn(NOW.plusSeconds(1799));
        service.cancel(id, reason("提前取消"), "early-cancel");
        assertBalance(0);
    }

    @Test
    void cancellationStillWorksAfterCoachDepartureOrStoreDeactivation() {
        Long id = Long.valueOf(create().id());
        jdbc.update("UPDATE gym_employee SET status='inactive' WHERE id=201");
        jdbc.update("UPDATE gym_store SET status='inactive' WHERE id=101");
        assertEquals("confirmed", service.get(id).status());
        service.cancel(id, reason("改期"), "departed-cancel");
        assertBalance(0);
    }

    @Test
    void releaseFailureRollsBackBalanceBookingAndRequestAndCanRetry() {
        Long id = Long.valueOf(create().id());
        jdbc.update("UPDATE gym_course_session SET reserved_count=0 WHERE id=?", session.getId());
        assertServiceException(
                () -> service.cancel(id, reason("取消"), "atomic-cancel"), COURSE_SESSION_INVALID);
        assertEquals(1, packs.selectById(pack.getId()).getReservedSessions());
        assertEquals("confirmed", bookings.selectById(id).getStatus());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM gym_mobile_request WHERE operation=?",
                        Integer.class,
                        "booking-cancel:" + id));
        jdbc.update("UPDATE gym_course_session SET reserved_count=1 WHERE id=?", session.getId());
        service.cancel(id, reason("取消"), "atomic-cancel");
        assertBalance(0);
    }

    @Test
    void foreignPackDisabledProductInvalidMembershipAndBlacklistNeverReserve() {
        var foreign = pack(member + 100, session);
        assertServiceException(
                () -> service.create(request(session, foreign), "foreign-pack"),
                COURSE_PACK_NOT_EXISTS);
        jdbc.update(
                "UPDATE gym_course_product SET status='inactive' WHERE id=?",
                session.getCourseProductId());
        assertServiceException(this::create, COURSE_PRODUCT_INVALID);
        jdbc.update(
                "UPDATE gym_course_product SET status='active' WHERE id=?",
                session.getCourseProductId());
        jdbc.update("UPDATE gym_member_store SET deleted=TRUE WHERE member_id=?", member);
        assertServiceException(this::create, BOOKING_INVALID);
        jdbc.update("UPDATE gym_member_store SET deleted=FALSE WHERE member_id=?", member);
        jdbc.update(
                "INSERT INTO gym_blacklist(member_id,block_entry,block_purchase,reason,starts_at,tenant_id) VALUES (?,TRUE,FALSE,'测试',?,1)",
                member,
                NOW.minusSeconds(1));
        assertServiceException(this::create, MEMBER_ENTRY_BLOCKED);
        assertBalance(0);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM gym_booking", Integer.class));
    }

    @Test
    void packMustCoverEntireSessionAndHaveUnreservedBalance() {
        pack.setExpiresAt(session.getEndsAt());
        packs.updateById(pack);
        assertServiceException(this::create, COURSE_PACK_INVALID);
        pack.setExpiresAt(session.getEndsAt().plusSeconds(1));
        pack.setReservedSessions(10);
        packs.updateById(pack);
        assertServiceException(this::create, COURSE_PACK_INSUFFICIENT);
        pack.setReservedSessions(0);
        pack.setStartsAt(session.getStartsAt().plusSeconds(1));
        packs.updateById(pack);
        assertServiceException(this::create, COURSE_PACK_INVALID);
        pack.setStartsAt(NOW);
        pack.setStatus("inactive");
        packs.updateById(pack);
        assertServiceException(this::create, COURSE_PACK_INVALID);
        assertEquals(0, sessions.selectById(session.getId()).getReservedCount());
    }

    @Test
    void bookingReadCancelAndPaginationAreMemberAndTenantScoped() {
        Long own = Long.valueOf(create().id());
        Long other = initialize(11L);
        var otherSession = session(201L, NOW.plusSeconds(10000));
        var otherPack = pack(other, otherSession);
        Long foreign =
                Long.valueOf(
                        service.create(request(otherSession, otherPack), "other-booking").id());
        login(1, 10, 1);
        assertServiceException(() -> service.get(foreign), BOOKING_NOT_EXISTS);
        assertServiceException(
                () -> service.cancel(foreign, reason("越权"), "foreign-cancel"), BOOKING_NOT_EXISTS);
        var req = new GymMobileRecordPageReqVO();
        req.setPageSize(1);
        var page = service.page(req);
        assertEquals(1L, page.getTotal());
        assertEquals(own.toString(), page.getList().getFirst().id());
        jdbc.update("UPDATE gym_booking SET tenant_id=2 WHERE id=?", own);
        assertServiceException(() -> service.get(own), BOOKING_NOT_EXISTS);
        assertEquals(0L, service.page(req).getTotal());
        login(1, 10, 2);
        assertServiceException(this::create, MOBILE_IDENTITY_INVALID);
        login(2, 10, 1);
        assertServiceException(this::create, MOBILE_IDENTITY_REQUIRED);
    }

    @Test
    void dateFilterUsesSessionTimeAndCompletedStatusMapsWithoutLeakingForeignCheckin() {
        Long id = Long.valueOf(create().id());
        var c = new GymCheckinDO();
        c.setStoreId(101L);
        c.setMemberId(member + 100);
        c.setBookingId(id);
        c.setEnteredAt(NOW);
        c.setMethod("manual");
        c.setStatus("succeeded");
        c.setVisitorType("member");
        checkins.insert(c);
        jdbc.update(
                "UPDATE gym_booking SET checkin_id=?,status='attended',booked_at=? WHERE id=?",
                c.getId(),
                NOW.minusSeconds(86400),
                id);
        var req = new GymMobileRecordPageReqVO();
        req.setStartDate(LocalDate.parse("2026-10-23"));
        req.setEndDate(LocalDate.parse("2026-10-24"));
        req.setStatus("completed");
        var page = service.page(req);
        assertEquals(1L, page.getTotal());
        assertEquals("completed", page.getList().getFirst().status());
        assertFalse(page.getList().getFirst().checkedIn());
        c.setMemberId(member);
        checkins.updateById(c);
        assertTrue(service.get(id).checkedIn());
        assertServiceException(
                () -> service.cancel(id, reason("已签课"), "completed-cancel"), BOOKING_INVALID);
        req.setStartDate(LocalDate.parse("2026-10-24"));
        req.setEndDate(LocalDate.parse("2026-10-25"));
        assertEquals(0L, service.page(req).getTotal());
    }

    @Test
    void lessonsReturnPersistedHistoricalBalanceNotCurrentPackBalance() {
        Long id = Long.valueOf(create().id());
        var lesson = new GymLessonRecordDO();
        lesson.setMemberId(member);
        lesson.setStoreId(101L);
        lesson.setCoachId(201L);
        lesson.setBookingId(id);
        lesson.setCheckinId(10L);
        lesson.setMemberCoursePackId(pack.getId());
        lesson.setSessionUnits(1);
        lesson.setConfirmedAt(NOW);
        lesson.setMethod("manual");
        lesson.setStatus("confirmed");
        lessons.insert(lesson);
        var req = new GymMobileRecordPageReqVO();
        assertNull(service.lessons(req).getList().getFirst().remainingLessons());
        lesson.setRemainingLessonsAfter(9);
        lessons.updateById(lesson);
        jdbc.update("UPDATE gym_course_pack SET remaining_sessions=2 WHERE id=?", pack.getId());
        assertEquals(9, service.lessons(req).getList().getFirst().remainingLessons());
        lesson.setMemberId(member + 100);
        lessons.updateById(lesson);
        assertEquals(0L, service.lessons(req).getTotal());
        assertNull(service.get(id).lessonRecordId());
    }

    Sessions sessionQuery() {
        var req = new Sessions();
        req.setStoreId(101L);
        req.setStartDate(LocalDate.parse("2026-10-23"));
        req.setEndDate(LocalDate.parse("2026-10-24"));
        return req;
    }

    @Test
    void sessionAvailabilityUsesOwnPackActualCapacityAndValidCoachMembership() {
        var req = sessionQuery();
        var available = service.sessions(201L, req);
        assertEquals(1L, available.getTotal());
        assertTrue(available.getList().getFirst().bookable());
        pack.setReservedSessions(10);
        packs.updateById(pack);
        assertEquals(
                "course_pack_required",
                service.sessions(201L, req).getList().getFirst().unavailableReason());
        pack.setReservedSessions(0);
        packs.updateById(pack);
        create();
        assertEquals(
                "capacity_full",
                service.sessions(201L, req).getList().getFirst().unavailableReason());
        jdbc.update("UPDATE gym_employee_store SET joined_on='2027-01-01' WHERE employee_id=201");
        assertServiceException(() -> service.sessions(201L, req), COURSE_COACH_INVALID);
        req.setEndDate(req.getStartDate().plusDays(32));
        assertServiceException(() -> service.sessions(201L, req), BUSINESS_DATE_INVALID);
    }

    @Test
    void twoMembersCompetingForOneSessionCreateOnlyOneReservation() throws Exception {
        Long other = initialize(11L);
        var otherPack = pack(other, session);
        login(1, 10, 1);
        var results =
                race(
                        () -> {
                            login(1, 10, 1);
                            return service.create(request(session, pack), "compete-first");
                        },
                        () -> {
                            login(1, 11, 1);
                            return service.create(request(session, otherPack), "compete-other");
                        });
        assertEquals(1, results.stream().filter(Booking.class::isInstance).count());
        assertEquals(
                1,
                results.stream()
                        .filter(
                                x ->
                                        x instanceof ServiceException e
                                                && e.getCode()
                                                        .equals(BOOKING_CAPACITY_FULL.getCode()))
                        .count());
        assertEquals(1, sessions.selectById(session.getId()).getReservedCount());
        assertEquals(
                1,
                packs.selectById(pack.getId()).getReservedSessions()
                        + packs.selectById(otherPack.getId()).getReservedSessions());
    }

    @Test
    void concurrentOverlappingSessionsForOneMemberCannotBothReserve() throws Exception {
        coach(202L);
        var s2 = session(202L, session.getStartsAt().plusSeconds(1800));
        var p2 = pack(member, s2);
        var results =
                race(
                        () -> {
                            login(1, 10, 1);
                            return service.create(request(session, pack), "overlap-first");
                        },
                        () -> {
                            login(1, 10, 1);
                            return service.create(request(s2, p2), "overlap-second");
                        });
        assertEquals(1, results.stream().filter(Booking.class::isInstance).count());
        assertEquals(
                1,
                results.stream()
                        .filter(
                                x ->
                                        x instanceof ServiceException e
                                                && e.getCode()
                                                        .equals(
                                                                MOBILE_BOOKING_TIME_CONFLICT
                                                                        .getCode()))
                        .count());
        assertEquals(
                1,
                packs.selectById(pack.getId()).getReservedSessions()
                        + packs.selectById(p2.getId()).getReservedSessions());
    }

    @Test
    void concurrentNonOverlappingBookingsCannotOverReserveOneRemainingLesson() throws Exception {
        var later = session(201L, session.getEndsAt());
        later.setCourseProductId(session.getCourseProductId());
        sessions.updateById(later);
        pack.setRemainingSessions(1);
        packs.updateById(pack);
        var results =
                race(
                        () -> {
                            login(1, 10, 1);
                            return service.create(request(session, pack), "last-first");
                        },
                        () -> {
                            login(1, 10, 1);
                            return service.create(request(later, pack), "last-second");
                        });
        assertEquals(1, results.stream().filter(Booking.class::isInstance).count());
        assertEquals(
                1,
                results.stream()
                        .filter(
                                x ->
                                        x instanceof ServiceException e
                                                && e.getCode()
                                                        .equals(COURSE_PACK_INSUFFICIENT.getCode()))
                        .count());
        assertEquals(1, packs.selectById(pack.getId()).getReservedSessions());
    }

    @Test
    void concurrentCancelRequestsReleaseOnlyOnce() throws Exception {
        Long id = Long.valueOf(create().id());
        var results =
                race(
                        () -> {
                            login(1, 10, 1);
                            return service.cancel(id, reason("A"), "cancel-key-a");
                        },
                        () -> {
                            login(1, 10, 1);
                            return service.cancel(id, reason("B"), "cancel-key-b");
                        });
        assertEquals(2, results.stream().filter(Booking.class::isInstance).count());
        assertBalance(0);
    }

    List<Object> race(Callable<Booking> first, Callable<Booking> second) throws Exception {
        var start = new CountDownLatch(1);
        var ready = new CountDownLatch(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = new ArrayList<Future<Object>>();
            for (var task : List.of(first, second))
                tasks.add(
                        executor.submit(
                                () -> {
                                    ready.countDown();
                                    assertTrue(start.await(3, TimeUnit.SECONDS));
                                    try {
                                        return task.call();
                                    } catch (ServiceException e) {
                                        return e;
                                    } finally {
                                        clear();
                                    }
                                }));
            assertTrue(ready.await(3, TimeUnit.SECONDS));
            start.countDown();
            return List.of(
                    tasks.get(0).get(8, TimeUnit.SECONDS), tasks.get(1).get(8, TimeUnit.SECONDS));
        }
    }
}
