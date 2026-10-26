package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileAttendanceVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileAttendanceService;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

class GymMobileAttendanceControllerTest {
    GymMobileAttendanceService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileAttendanceService.class);
        var controller = new AppGymAttendanceController();
        ReflectionTestUtils.setField(controller, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(
                                new GlobalExceptionHandler(
                                        "attendance-http", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions a) throws Exception {
        return JsonUtils.parseObject(
                a.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void bindsStoreAndMonthAndReturnsOnlyAggregateFields() throws Exception {
        when(service.get(any())).thenReturn(new Summary(3, 2, 1L, 4, "2026-10-24T20:00+08:00"));
        var response =
                mvc.perform(
                        get("/gym/me/attendance-summary")
                                .param("storeId", "101")
                                .param("month", "2026-10")
                                .param("memberId", "999"));
        assertEquals(0, code(response));
        verify(service).get(argThat(r -> r.getStoreId() == 101L && r.getMonth().equals("2026-10")));
        var json =
                JsonUtils.parseObject(
                        response.andReturn().getResponse().getContentAsString(),
                        "data",
                        java.util.Map.class);
        assertEquals(
                java.util.Set.of(
                        "checkinCount", "activeDays", "rank", "rankingPopulation", "updatedAt"),
                json.keySet());
        assertEquals(3, json.get("checkinCount"));
    }

    @Test
    void rejectsMissingInvalidAndOversizedMonthParameters() throws Exception {
        assertEquals(400, code(mvc.perform(get("/gym/me/attendance-summary"))));
        for (String month : List.of("2026-13", "2026-1", "10000-01", "2026-10-01"))
            assertEquals(
                    400,
                    code(
                            mvc.perform(
                                    get("/gym/me/attendance-summary")
                                            .param("storeId", "101")
                                            .param("month", month))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                get("/gym/me/attendance-summary")
                                        .param("storeId", "0")
                                        .param("month", "2026-10"))));
        verifyNoInteractions(service);
    }
}
