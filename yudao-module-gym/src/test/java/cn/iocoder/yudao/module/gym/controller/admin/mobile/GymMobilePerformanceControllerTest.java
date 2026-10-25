package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobilePerformanceVO.*;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobilePerformanceService;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

class GymMobilePerformanceControllerTest {
    GymMobilePerformanceService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobilePerformanceService.class);
        var c = new GymMobilePerformanceController();
        ReflectionTestUtils.setField(c, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(c)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "performance", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions r) throws Exception {
        return JsonUtils.parseObject(
                r.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void bindsGroupAndScopeAndSerializesAmountsAndNullableSelfRank() throws Exception {
        when(service.performance(any()))
                .thenReturn(
                        new Result(
                                List.of(new Row("101", "91", "本人", null, "80.00", 1, 2, "40.00")),
                                1,
                                new Summary("80.00", 1, 1, "80.00", "80.00", 2)));
        var result =
                mvc.perform(
                        get("/gym/mobile/reports/performance")
                                .param("startDate", "2026-06-10")
                                .param("endDate", "2026-06-12")
                                .param("storeId", "101")
                                .param("groupBy", "employee")
                                .param("employeeId", "999"));
        assertEquals(0, code(result));
        verify(service)
                .performance(
                        argThat(
                                q ->
                                        "employee".equals(q.getGroupBy())
                                                && q.getStoreId() == 101L
                                                && q.getStartDate()
                                                        .equals(
                                                                java.time.LocalDate.of(
                                                                        2026, 6, 10))));
        String json = result.andReturn().getResponse().getContentAsString();
        assertTrue(json.contains("\"netAmount\":\"80.00\""));
        assertFalse(json.contains("999"));
    }

    @Test
    void rejectsMissingDatesGroupingAndInvalidLimits() throws Exception {
        assertNotEquals(0, code(mvc.perform(get("/gym/mobile/reports/performance"))));
        for (String[] pair :
                List.of(
                        new String[] {"storeId", "-1"},
                        new String[] {"pageSize", "201"},
                        new String[] {"groupBy", "all"},
                        new String[] {"startDate", "bad-date"})) {
            var parameters = new org.springframework.util.LinkedMultiValueMap<String, String>();
            parameters.set("startDate", "2026-06-10");
            parameters.set("endDate", "2026-06-12");
            parameters.set("groupBy", "employee");
            parameters.set(pair[0], pair[1]);
            var r = get("/gym/mobile/reports/performance").params(parameters);
            assertNotEquals(0, code(mvc.perform(r)));
        }
        verifyNoInteractions(service);
    }
}
