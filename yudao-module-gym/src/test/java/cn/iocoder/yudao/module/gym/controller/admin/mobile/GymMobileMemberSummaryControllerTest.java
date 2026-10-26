package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileMemberSummaryVO.*;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileMemberSummaryService;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GymMobileMemberSummaryControllerTest {
    GymMobileMemberSummaryService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileMemberSummaryService.class);
        var c = new GymMobileMemberSummaryController();
        ReflectionTestUtils.setField(c, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(c)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "member-summary", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions r) throws Exception {
        return JsonUtils.parseObject(
                r.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void bindsDateAndStoreAndSerializesOnlyAggregateFields() throws Exception {
        when(service.summary(any()))
                .thenReturn(new Summary(10, 3, 2, 4, 1, "2026-06-10", "2026-06-10T10:00+08:00"));
        var r =
                mvc.perform(
                        get("/gym/mobile/reports/member-summary")
                                .param("storeId", "101")
                                .param("date", "2026-06-10")
                                .param("memberId", "999"));
        assertEquals(0, code(r));
        verify(service)
                .summary(
                        argThat(
                                q ->
                                        q.getStoreId() == 101L
                                                && q.getDate()
                                                        .equals(
                                                                java.time.LocalDate.of(
                                                                        2026, 6, 10))));
        String json = r.andReturn().getResponse().getContentAsString();
        assertTrue(json.contains("\"memberCount\":10"));
        assertTrue(json.contains("\"privateCourseMemberCount\":3"));
        assertTrue(json.contains("\"updatedAt\""));
        assertFalse(json.contains("999"));
    }

    @Test
    void rejectsMissingMalformedDatesAndNonpositiveStore() throws Exception {
        assertNotEquals(0, code(mvc.perform(get("/gym/mobile/reports/member-summary"))));
        assertNotEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/mobile/reports/member-summary")
                                        .param("date", "2026-02-30"))));
        assertNotEquals(
                0,
                code(mvc.perform(get("/gym/mobile/reports/member-summary").param("date", "bad"))));
        assertNotEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/mobile/reports/member-summary")
                                        .param("date", "2026-06-10")
                                        .param("storeId", "0"))));
        verifyNoInteractions(service);
    }
}
