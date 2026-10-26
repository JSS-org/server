package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileWorkCourseWriteService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

class GymMobileWorkCourseWriteControllerTest {
    static final String SESSION =
            "{\"storeId\":101,\"coachId\":91,\"productId\":2,\"startsAt\":\"2026-10-24T10:00:00+08:00\",\"endsAt\":\"2026-10-24T11:00:00+08:00\"}";
    static final String BOOKING = "{\"memberId\":5,\"sessionId\":6,\"coursePackId\":7}";
    GymMobileWorkCourseWriteService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileWorkCourseWriteService.class);
        var controller = new GymMobileWorkCourseWriteController();
        ReflectionTestUtils.setField(controller, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "work-course-write-http", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions action) throws Exception {
        return JsonUtils.parseObject(
                action.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    ResultActions call(String path, String body) throws Exception {
        return mvc.perform(
                post("/gym/mobile/" + path)
                        .header("Idempotency-Key", "work-http-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    @Test
    void writesBindBusinessIdsOffsetDatesAndIdempotencyKey() throws Exception {
        assertEquals(0, code(call("course-sessions", SESSION)));
        verify(service)
                .session(
                        argThat(
                                r ->
                                        r.getStoreId() == 101L
                                                && r.getCoachId() == 91L
                                                && r.getProductId() == 2L
                                                && r.getStartsAt()
                                                        .equals(
                                                                Instant.parse(
                                                                        "2026-10-24T02:00:00Z"))
                                                && r.getEndsAt()
                                                        .equals(
                                                                Instant.parse(
                                                                        "2026-10-24T03:00:00Z"))),
                        eq("work-http-key"));
        assertEquals(0, code(call("bookings", BOOKING)));
        verify(service)
                .booking(
                        argThat(
                                r ->
                                        r.getMemberId() == 5L
                                                && r.getSessionId() == 6L
                                                && r.getCoursePackId() == 7L),
                        eq("work-http-key"));
    }

    @Test
    void missingKeyInvalidDatesAndForgedStateNeverReachBusinessService() throws Exception {
        assertEquals(
                400,
                code(
                        mvc.perform(
                                post("/gym/mobile/course-sessions")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(SESSION))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                post("/gym/mobile/bookings")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(BOOKING))));
        for (String field : List.of("capacity", "reservedCount", "status", "employeeId", "price"))
            assertEquals(
                    MOBILE_REQUEST_INVALID.getCode(),
                    code(
                            call(
                                    "course-sessions",
                                    SESSION.substring(0, SESSION.length() - 1)
                                            + ",\""
                                            + field
                                            + "\":1}")));
        for (String field : List.of("userId", "status", "memberCardId", "reservedSessions", "note"))
            assertEquals(
                    MOBILE_REQUEST_INVALID.getCode(),
                    code(
                            call(
                                    "bookings",
                                    BOOKING.substring(0, BOOKING.length() - 1)
                                            + ",\""
                                            + field
                                            + "\":1}")));
        assertEquals(
                400,
                code(
                        call(
                                "course-sessions",
                                SESSION.replace("2026-10-24T10:00:00+08:00", "not-a-date"))));
        assertEquals(
                400, code(call("bookings", BOOKING.replace("\"memberId\":5", "\"memberId\":0"))));
        assertEquals(400, code(call("course-sessions", "{}")));
        assertEquals(400, code(call("bookings", "{}")));
        verifyNoInteractions(service);
    }

    @Test
    void conflictsAndUnavailableRightsUseBusinessErrorEnvelope() throws Exception {
        when(service.session(any(), anyString()))
                .thenThrow(new ServiceException(COURSE_SESSION_CONFLICT));
        when(service.booking(any(), anyString()))
                .thenThrow(new ServiceException(COURSE_PACK_INSUFFICIENT));
        assertEquals(COURSE_SESSION_CONFLICT.getCode(), code(call("course-sessions", SESSION)));
        assertEquals(COURSE_PACK_INSUFFICIENT.getCode(), code(call("bookings", BOOKING)));
    }
}
