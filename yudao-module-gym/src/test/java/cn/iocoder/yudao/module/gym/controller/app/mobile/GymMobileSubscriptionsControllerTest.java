package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileSubscriptionsVO.*;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileSubscriptionsService;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobileSubscriptionsControllerTest {
    GymMobileSubscriptionsService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileSubscriptionsService.class);
        var controller = new AppGymSubscriptionsController();
        ReflectionTestUtils.setField(controller, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "subscriptions", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions r) throws Exception {
        return JsonUtils.parseObject(
                r.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    Subscription record() {
        return new Subscription("77", "101", "700", "unconfigured", "119.90", null, null, null);
    }

    @Test
    void bindsOptionalStoreAndPaginationWithoutCallerSuppliedMemberIdentity() throws Exception {
        when(service.page(any())).thenReturn(new PageResult<>(List.of(record()), 1L));
        assertEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/me/subscriptions")
                                        .param("storeId", "101")
                                        .param("pageNo", "2")
                                        .param("pageSize", "10")
                                        .param("memberId", "999"))));
        verify(service)
                .page(
                        argThat(
                                r ->
                                        r.getStoreId() == 101L
                                                && r.getPageNo() == 2
                                                && r.getPageSize() == 10));
        assertEquals(0, code(mvc.perform(get("/gym/me/subscriptions"))));
        verify(service).page(argThat(r -> r.getStoreId() == null));
    }

    @Test
    void rejectsInvalidPaginationAndStoreBeforeService() throws Exception {
        for (String[] param :
                List.of(
                        new String[] {"storeId", "0"},
                        new String[] {"storeId", "bad"},
                        new String[] {"pageNo", "0"},
                        new String[] {"pageSize", "201"},
                        new String[] {"pageSize", "0"}))
            assertEquals(
                    400, code(mvc.perform(get("/gym/me/subscriptions").param(param[0], param[1]))));
        assertEquals(400, code(mvc.perform(get("/gym/me/subscriptions/bad"))));
        verifyNoInteractions(service);
    }

    @Test
    void listAndDetailExposeOnlyTheEightMemberFieldsIncludingUnknownHistoricalVersion()
            throws Exception {
        when(service.page(any())).thenReturn(new PageResult<>(List.of(record()), 1L));
        when(service.get(77L)).thenReturn(record());
        var page = mvc.perform(get("/gym/me/subscriptions"));
        var detail = mvc.perform(get("/gym/me/subscriptions/77"));
        assertEquals(0, code(page));
        assertEquals(0, code(detail));
        var data =
                JsonUtils.parseObject(
                        detail.andReturn().getResponse().getContentAsString(), "data", Map.class);
        assertEquals(
                Set.of(
                        "id",
                        "storeId",
                        "productId",
                        "status",
                        "cycleAmount",
                        "nextChargeAt",
                        "agreementVersion",
                        "terminatedAt"),
                data.keySet());
        assertEquals("119.90", data.get("cycleAmount"));
        assertTrue(data.containsKey("agreementVersion"));
        assertNull(data.get("agreementVersion"));
        var pagedata =
                JsonUtils.parseObject(
                        page.andReturn().getResponse().getContentAsString(), "data", Map.class);
        assertEquals(data, ((List<?>) pagedata.get("list")).getFirst());
        verify(service).get(77L);
    }
}
