package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileSalesReportVO.*;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileSalesReportService;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

class GymMobileSalesReportControllerTest {
    GymMobileSalesReportService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileSalesReportService.class);
        var controller = new GymMobileSalesReportController();
        ReflectionTestUtils.setField(controller, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "sales-report", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions result) throws Exception {
        return JsonUtils.parseObject(
                result.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void bindsSalesFiltersAndReturnsMoneyStringsAndFullSummary() throws Exception {
        when(service.sales(any()))
                .thenReturn(
                        new Result(
                                List.of(
                                        new SaleRow(
                                                "1",
                                                "O1",
                                                "101",
                                                null,
                                                "card",
                                                "次卡",
                                                "100.00",
                                                "20.00",
                                                "80.00",
                                                "2026-06-10T08:00:00+08:00")),
                                1,
                                new Summary(
                                        "100.00", "20.00", "80.00", 1, "80.00", 1, "80.00", "0.00",
                                        0, "0.00")));
        var r =
                mvc.perform(
                        get("/gym/mobile/reports/sales")
                                .param("startDate", "2026-06-10")
                                .param("endDate", "2026-06-11")
                                .param("storeId", "101")
                                .param("employeeId", "91")
                                .param("productType", "card")
                                .param("pageNo", "2")
                                .param("pageSize", "10"));
        assertEquals(0, code(r));
        verify(service)
                .sales(
                        argThat(
                                q ->
                                        q.getStartDate().equals(java.time.LocalDate.of(2026, 6, 10))
                                                && q.getEndDate()
                                                        .equals(java.time.LocalDate.of(2026, 6, 11))
                                                && q.getEmployeeId() == 91L
                                                && q.getStoreId() == 101L
                                                && q.getPageNo() == 2
                                                && q.getPageSize() == 10
                                                && "card".equals(q.getProductType())));
        String json = r.andReturn().getResponse().getContentAsString();
        assertTrue(json.contains("\"netAmount\":\"80.00\""));
        assertTrue(json.contains("cardAverageOrderAmount"));
        assertFalse(json.contains("memberId"));
    }

    @Test
    void rejectsMissingMalformedDateAndIllegalPagingOrFilters() throws Exception {
        assertNotEquals(0, code(mvc.perform(get("/gym/mobile/reports/sales"))));
        assertNotEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/mobile/reports/sales")
                                        .param("startDate", "not-date")
                                        .param("endDate", "2026-06-11"))));
        for (String[] pair :
                List.of(
                        new String[] {"storeId", "0"},
                        new String[] {"employeeId", "-1"},
                        new String[] {"productType", "all"},
                        new String[] {"pageSize", "201"},
                        new String[] {"pageNo", "0"}))
            assertNotEquals(
                    0,
                    code(
                            mvc.perform(
                                    get("/gym/mobile/reports/sales")
                                            .param("startDate", "2026-06-10")
                                            .param("endDate", "2026-06-11")
                                            .param(pair[0], pair[1]))));
        verifyNoInteractions(service);
    }
}
