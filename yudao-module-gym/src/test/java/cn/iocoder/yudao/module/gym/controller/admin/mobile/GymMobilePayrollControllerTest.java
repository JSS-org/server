package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobilePayrollVO.*;
import cn.iocoder.yudao.module.gym.controller.admin.payroll.GymPayrollPublicationController;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobilePayrollService;
import cn.iocoder.yudao.module.gym.service.payroll.GymPayrollPublicationService;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

class GymMobilePayrollControllerTest {
    GymMobilePayrollService service;
    GymPayrollPublicationService publications;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobilePayrollService.class);
        publications = mock(GymPayrollPublicationService.class);
        var a = new GymMobilePayrollController();
        var b = new GymPayrollPublicationController();
        ReflectionTestUtils.setField(a, "service", service);
        ReflectionTestUtils.setField(b, "service", publications);
        mvc =
                MockMvcBuilders.standaloneSetup(a, b)
                        .setControllerAdvice(
                                new GlobalExceptionHandler(
                                        "payroll-http", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions a) throws Exception {
        return JsonUtils.parseObject(
                a.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void routesUseItemIdsAndOwnRequestCannotChooseOtherEmployee() throws Exception {
        assertEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/mobile/me/payrolls")
                                        .param("month", "2026-06")
                                        .param("employeeId", "99"))));
        verify(service).mine(argThat((OwnPage r) -> r.getMonth().equals("2026-06")));
        assertEquals(0, code(mvc.perform(get("/gym/mobile/me/payrolls/7"))));
        verify(service).mine(7L);
        assertEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/mobile/payrolls")
                                        .param("storeId", "101")
                                        .param("month", "2026-06")
                                        .param("employeeId", "91"))));
        verify(service)
                .store(
                        argThat(
                                (StorePage r) ->
                                        r.getStoreId() == 101L
                                                && r.getEmployeeId() == 91L
                                                && r.getMonth().equals("2026-06")));
        assertEquals(0, code(mvc.perform(get("/gym/mobile/payrolls/8"))));
        verify(service).store(8L);
        assertEquals(0, code(mvc.perform(post("/gym/payrolls/9/publications"))));
        verify(publications).publish(9L);
    }

    @Test
    void validatesRequiredStoreMonthPagingAndOptionalMonth() throws Exception {
        assertEquals(400, code(mvc.perform(get("/gym/mobile/payrolls"))));
        for (String month : List.of("2026-13", "2026-1", "2026-06-01", "10000-01"))
            assertEquals(
                    400, code(mvc.perform(get("/gym/mobile/me/payrolls").param("month", month))));
        for (String size : List.of("-1", "0", "201"))
            assertEquals(
                    400, code(mvc.perform(get("/gym/mobile/me/payrolls").param("pageSize", size))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                get("/gym/mobile/payrolls")
                                        .param("storeId", "0")
                                        .param("month", "2026-06"))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                get("/gym/mobile/payrolls")
                                        .param("storeId", "101")
                                        .param("month", "2026-06")
                                        .param("employeeId", "0"))));
        verifyNoInteractions(service, publications);
    }

    @Test
    void moneyIsExactStringAndResponseHasNoInternalCalculationSources() throws Exception {
        var row =
                new Payroll(
                        "7",
                        "101",
                        "91",
                        "2026-06",
                        "published",
                        "5000.00",
                        "130.00",
                        "100.00",
                        "50.00",
                        "5180.00",
                        List.of(new Item("固定扣项", "-50.00", "固定扣项（明细为负值）")));
        when(service.mine(any(OwnPage.class))).thenReturn(new PageResult<>(List.of(row), 1L));
        var result = mvc.perform(get("/gym/mobile/me/payrolls"));
        assertEquals(0, code(result));
        var data =
                JsonUtils.parseObject(
                        result.andReturn().getResponse().getContentAsString(),
                        "data",
                        java.util.Map.class);
        var item = (java.util.Map<?, ?>) ((java.util.List<?>) data.get("list")).getFirst();
        assertEquals("5180.00", item.get("totalAmount"));
        assertEquals("7", item.get("id"));
        assertEquals(
                java.util.Set.of(
                        "id",
                        "storeId",
                        "employeeId",
                        "month",
                        "status",
                        "baseAmount",
                        "commissionAmount",
                        "bonusAmount",
                        "deductionAmount",
                        "totalAmount",
                        "items"),
                item.keySet());
    }
}
