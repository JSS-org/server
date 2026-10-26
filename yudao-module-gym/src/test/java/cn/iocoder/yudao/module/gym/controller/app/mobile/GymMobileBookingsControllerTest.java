package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileBookingVO.*;
import cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileBookingsService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

class GymMobileBookingsControllerTest {
    GymMobileBookingsService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileBookingsService.class);
        var controller = new AppGymBookingsController();
        ReflectionTestUtils.setField(controller, "bookings", service);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "booking-http-test", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions result) throws Exception {
        return JsonUtils.parseObject(
                result.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void createsAndCancelsRequireKeyAndRejectForgedOwnershipFields() throws Exception {
        assertEquals(
                400,
                code(
                        mvc.perform(
                                post("/gym/bookings")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"sessionId\":1,\"coursePackId\":2}"))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                post("/gym/bookings/1/cancellations")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))));
        assertEquals(
                ErrorCodeConstants.MOBILE_REQUEST_INVALID.getCode(),
                code(
                        mvc.perform(
                                post("/gym/bookings")
                                        .header("Idempotency-Key", "test-book-key")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"sessionId\":1,\"coursePackId\":2,\"memberId\":99}"))));
        assertEquals(
                ErrorCodeConstants.MOBILE_REQUEST_INVALID.getCode(),
                code(
                        mvc.perform(
                                post("/gym/bookings/1/cancellations")
                                        .header("Idempotency-Key", "test-cancel-key")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"status\":\"completed\"}"))));
        verifyNoInteractions(service);
    }

    @Test
    void positiveIdsRequiredPackAndReasonLengthAreValidated() throws Exception {
        for (String body :
                List.of(
                        "{\"sessionId\":1}",
                        "{\"sessionId\":0,\"coursePackId\":2}",
                        "{\"sessionId\":1,\"coursePackId\":-1}")) {
            assertEquals(
                    400,
                    code(
                            mvc.perform(
                                    post("/gym/bookings")
                                            .header("Idempotency-Key", "test-book-key")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(body))));
        }
        assertEquals(
                400,
                code(
                        mvc.perform(
                                post("/gym/bookings/1/cancellations")
                                        .header("Idempotency-Key", "test-cancel-key")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                JsonUtils.toJsonString(
                                                        java.util.Map.of(
                                                                "reason", "长".repeat(501)))))));
        verifyNoInteractions(service);
    }

    @Test
    void allFourReadRoutesBindFiltersAndStringIds() throws Exception {
        var booking =
                new Booking(
                        "9007199254740993",
                        "2",
                        "3",
                        "101",
                        "201",
                        "2026-10-23T13:00+08:00",
                        "2026-10-23T14:00+08:00",
                        "confirmed",
                        false,
                        "2026-10-23T13:00+08:00",
                        null);
        when(service.get(9007199254740993L)).thenReturn(booking);
        var response =
                mvc.perform(get("/gym/me/bookings/9007199254740993"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertTrue(response.contains("\"id\":\"9007199254740993\""));
        when(service.page(any())).thenReturn(new PageResult<>(List.of(booking), 1L));
        assertEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/me/bookings")
                                        .param("status", "completed")
                                        .param("storeId", "101")
                                        .param("startDate", "2026-10-23")
                                        .param("endDate", "2026-10-24"))));
        verify(service)
                .page(
                        argThat(
                                q ->
                                        "completed".equals(q.getStatus())
                                                && Long.valueOf(101).equals(q.getStoreId())
                                                && q.getEndDate().getDayOfMonth() == 24));
        when(service.lessons(any())).thenReturn(new PageResult<>(List.of(), 0L));
        assertEquals(0, code(mvc.perform(get("/gym/me/lesson-records"))));
        when(service.sessions(eq(201L), any())).thenReturn(new PageResult<>(List.of(), 0L));
        assertEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/coaches/201/sessions")
                                        .param("storeId", "101")
                                        .param("startDate", "2026-10-23")
                                        .param("endDate", "2026-10-24"))));
        verify(service).sessions(eq(201L), argThat(q -> Long.valueOf(101).equals(q.getStoreId())));
    }

    @Test
    void sessionQueryRequiresStoreAndDatesAndBoundedPageSize() throws Exception {
        assertEquals(
                400, code(mvc.perform(get("/gym/coaches/201/sessions").param("storeId", "101"))));
        assertEquals(400, code(mvc.perform(get("/gym/me/bookings").param("pageSize", "-1"))));
        assertEquals(
                400, code(mvc.perform(get("/gym/me/lesson-records").param("pageSize", "201"))));
        verifyNoInteractions(service);
    }

    @Test
    void mutationRoutesPassTheExactIdempotencyKeyAndReason() throws Exception {
        assertEquals(
                0,
                code(
                        mvc.perform(
                                post("/gym/bookings")
                                        .header("Idempotency-Key", "create-123")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"sessionId\":\"11\",\"coursePackId\":\"22\"}"))));
        verify(service)
                .create(
                        argThat(r -> r.getSessionId() == 11L && r.getCoursePackId() == 22L),
                        eq("create-123"));
        assertEquals(
                0,
                code(
                        mvc.perform(
                                post("/gym/bookings/33/cancellations")
                                        .header("Idempotency-Key", "cancel-123")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"reason\":\"改期\"}"))));
        verify(service).cancel(eq(33L), argThat(r -> "改期".equals(r.getReason())), eq("cancel-123"));
    }
}
