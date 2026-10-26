package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.BOOKING_NOT_EXISTS;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileWorkCoursesVO.*;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileWorkCoursesService;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

class GymMobileWorkCoursesControllerTest {
    GymMobileWorkCoursesService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileWorkCoursesService.class);
        var controller = new GymMobileWorkCoursesController();
        ReflectionTestUtils.setField(controller, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "work-courses-http", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions action) throws Exception {
        return JsonUtils.parseObject(
                action.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void allFourRoutesBindDatesAndBusinessFiltersWithExplicitResponseFields() throws Exception {
        var result =
                new WorkBooking(
                        "1",
                        "2",
                        "3",
                        "101",
                        "91",
                        "2026-10-24T10:00:00+08:00",
                        "2026-10-24T11:00:00+08:00",
                        "completed",
                        true,
                        "2026-10-24T10:00:00+08:00",
                        "4",
                        "5",
                        "会员");
        when(service.bookings(any())).thenReturn(new PageResult<>(List.of(result), 1L));
        when(service.booking(1L)).thenReturn(result);
        var response =
                mvc.perform(
                        get("/gym/mobile/bookings")
                                .param("startDate", "2026-10-24")
                                .param("endDate", "2026-10-25")
                                .param("storeId", "101")
                                .param("coachId", "91")
                                .param("status", "completed")
                                .param("pageSize", "20"));
        assertEquals(0, code(response));
        String json = response.andReturn().getResponse().getContentAsString();
        assertTrue(json.contains("\"memberId\":\"5\""));
        assertTrue(json.contains("\"checkedIn\":true"));
        assertFalse(json.contains("phone"));
        assertFalse(json.contains("note"));
        verify(service)
                .bookings(
                        argThat(
                                r ->
                                        r.getStartDate().equals(LocalDate.of(2026, 10, 24))
                                                && r.getEndDate().equals(LocalDate.of(2026, 10, 25))
                                                && r.getStoreId() == 101L
                                                && r.getCoachId() == 91L
                                                && r.getStatus().equals("completed")
                                                && r.getPageSize() == 20));
        assertEquals(0, code(mvc.perform(get("/gym/mobile/bookings/1"))));
        verify(service).booking(1L);
        assertEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/mobile/checkins")
                                        .param("startDate", "2026-10-24")
                                        .param("endDate", "2026-10-25")
                                        .param("memberId", "5"))));
        verify(service).checkins(argThat(r -> r.getMemberId() == 5L));
        assertEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/mobile/lesson-records")
                                        .param("startDate", "2026-10-24")
                                        .param("endDate", "2026-10-25")
                                        .param("coachId", "91"))));
        verify(service).lessons(argThat(r -> r.getCoachId() == 91L));
    }

    @Test
    void missingMalformedDatesAndUnboundedOrInvalidFiltersNeverReachService() throws Exception {
        for (String path : List.of("bookings", "checkins", "lesson-records")) {
            assertEquals(400, code(mvc.perform(get("/gym/mobile/" + path))));
            assertEquals(
                    400,
                    code(
                            mvc.perform(
                                    get("/gym/mobile/" + path)
                                            .param("startDate", "2026-10-24")
                                            .param("endDate", "invalid"))));
            for (String size : List.of("-1", "201"))
                assertEquals(
                        400,
                        code(
                                mvc.perform(
                                        get("/gym/mobile/" + path)
                                                .param("startDate", "2026-10-24")
                                                .param("endDate", "2026-10-25")
                                                .param("pageSize", size))));
        }
        assertEquals(
                400,
                code(
                        mvc.perform(
                                get("/gym/mobile/bookings")
                                        .param("startDate", "2026-10-24")
                                        .param("endDate", "2026-10-25")
                                        .param("status", "attended"))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                get("/gym/mobile/checkins")
                                        .param("startDate", "2026-10-24")
                                        .param("endDate", "2026-10-25")
                                        .param("memberId", "0"))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                get("/gym/mobile/lesson-records")
                                        .param("startDate", "2026-10-24")
                                        .param("endDate", "2026-10-25")
                                        .param("coachId", "-1"))));
        verifyNoInteractions(service);
    }

    @Test
    void HiddenBookingUsesSameErrorAsMissingBooking() throws Exception {
        when(service.booking(1L)).thenThrow(new ServiceException(BOOKING_NOT_EXISTS));
        assertEquals(
                BOOKING_NOT_EXISTS.getCode(), code(mvc.perform(get("/gym/mobile/bookings/1"))));
    }
}
