package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.MOBILE_IDENTITY_INVALID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileWorkContextVO;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileWorkContextService;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

class GymMobileWorkContextControllerTest {
    GymMobileWorkContextService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileWorkContextService.class);
        var controller = new GymMobileWorkContextController();
        ReflectionTestUtils.setField(controller, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "work-context-http", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    @Test
    void contextHasOnlyExplicitFieldsAndDoesNotBindCallerSuppliedIdentity() throws Exception {
        when(service.get())
                .thenReturn(new GymMobileWorkContextVO(null, List.of(), List.of(), List.of()));
        var response =
                mvc.perform(
                                get("/gym/mobile/context")
                                        .param("userId", "999")
                                        .param("employeeId", "999")
                                        .param("storeId", "999"))
                        .andReturn()
                        .getResponse();
        assertEquals(
                0, JsonUtils.parseObject(response.getContentAsString(), "code", Integer.class));
        assertFalse(response.getContentAsString().contains("999"));
        assertTrue(response.getContentAsString().contains("\"stores\":[]"));
        verify(service).get();
    }

    @Test
    void invalidIdentityProducesExistingBusinessErrorEnvelope() throws Exception {
        when(service.get()).thenThrow(new ServiceException(MOBILE_IDENTITY_INVALID));
        var response = mvc.perform(get("/gym/mobile/context")).andReturn().getResponse();
        assertEquals(
                MOBILE_IDENTITY_INVALID.getCode(),
                JsonUtils.parseObject(response.getContentAsString(), "code", Integer.class));
    }
}
