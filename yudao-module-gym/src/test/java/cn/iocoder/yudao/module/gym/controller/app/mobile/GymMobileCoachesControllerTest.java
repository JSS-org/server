package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileCoachService;

import jakarta.annotation.security.PermitAll;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GymMobileCoachesControllerTest {
    GymMobileCoachService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileCoachService.class);
        var controller = new AppGymCoachesController();
        ReflectionTestUtils.setField(controller, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "coach-http", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions result) throws Exception {
        return JsonUtils.parseObject(
                result.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void anonymousRoutesBindPagingAndRequiredStore() throws Exception {
        assertTrue(AppGymCoachesController.class.isAnnotationPresent(PermitAll.class));
        assertEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/coaches")
                                        .param("storeId", "101")
                                        .param("pageNo", "2")
                                        .param("pageSize", "3"))));
        verify(service)
                .page(
                        argThat(
                                r ->
                                        r.getStoreId() == 101L
                                                && r.getPageNo() == 2
                                                && r.getPageSize() == 3));
        assertEquals(0, code(mvc.perform(get("/gym/coaches/7").param("storeId", "101"))));
        verify(service).detail(7L, 101L);
    }

    @Test
    void missingStoreAndUnboundedPagingAreRejectedBeforeService() throws Exception {
        assertEquals(400, code(mvc.perform(get("/gym/coaches"))));
        assertEquals(400, code(mvc.perform(get("/gym/coaches").param("storeId", "0"))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                get("/gym/coaches")
                                        .param("storeId", "101")
                                        .param("pageSize", "-1"))));
        assertEquals(400, code(mvc.perform(get("/gym/coaches/7"))));
        verifyNoInteractions(service);
    }
}
