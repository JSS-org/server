package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileDashboardVO.*;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileDashboardService;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

class GymMobileDashboardControllerTest {
    GymMobileDashboardService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileDashboardService.class);
        var c = new GymMobileDashboardController();
        ReflectionTestUtils.setField(c, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(c)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "dashboard", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions r) throws Exception {
        return JsonUtils.parseObject(
                r.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void bindsOptionalDateAndStorePreservesNullMetricsAndCannotChooseScopeOrEmployee()
            throws Exception {
        when(service.dashboard(any()))
                .thenReturn(
                        new Dashboard(
                                new Scope("self", List.of("101"), "91"),
                                "2026-06-10",
                                null,
                                new Checkins(2, 1),
                                null,
                                new Members(1, 1L, null),
                                null,
                                "2026-06-10T10:00+08:00"));
        var r =
                mvc.perform(
                        get("/gym/mobile/dashboard")
                                .param("date", "2026-06-10")
                                .param("storeId", "101")
                                .param("employeeId", "999")
                                .param("role", "headquarters"));
        assertEquals(0, code(r));
        verify(service)
                .dashboard(
                        argThat(
                                q ->
                                        q.getStoreId() == 101L
                                                && q.getDate()
                                                        .equals(
                                                                java.time.LocalDate.of(
                                                                        2026, 6, 10))));
        String json = r.andReturn().getResponse().getContentAsString();
        assertTrue(json.contains("\"performance\":null"));
        assertTrue(json.contains("\"storeTotalCount\":null"));
        assertFalse(json.contains("999"));
        assertFalse(json.contains("headquarters"));
        assertEquals(0, code(mvc.perform(get("/gym/mobile/dashboard"))));
        verify(service).dashboard(argThat(q -> q.getDate() == null && q.getStoreId() == null));
    }

    @Test
    void rejectsMalformedDatesAndInvalidStoreParameters() throws Exception {
        assertNotEquals(
                0, code(mvc.perform(get("/gym/mobile/dashboard").param("date", "2026-02-30"))));
        assertNotEquals(0, code(mvc.perform(get("/gym/mobile/dashboard").param("date", "bad"))));
        assertNotEquals(0, code(mvc.perform(get("/gym/mobile/dashboard").param("storeId", "0"))));
        verifyNoInteractions(service);
    }
}
