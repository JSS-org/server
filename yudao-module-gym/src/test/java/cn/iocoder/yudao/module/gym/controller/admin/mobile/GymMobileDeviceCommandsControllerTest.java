package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileDeviceCommandVO;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileDeviceCommandsService;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobileDeviceCommandsControllerTest {
    GymMobileDeviceCommandsService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileDeviceCommandsService.class);
        var controller = new GymMobileDeviceCommandsController();
        ReflectionTestUtils.setField(controller, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(
                                new GlobalExceptionHandler(
                                        "device-result", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    @Test
    void returnsExactProjectionAndBindsLongIdentifier() throws Exception {
        when(service.get(9007199254740993L))
                .thenReturn(
                        new GymMobileDeviceCommandVO(
                                "9007199254740993",
                                "4",
                                "open_door",
                                "unknown",
                                "2026-10-26T09:02:03.123456+08:00",
                                null,
                                "暂无可信执行结果"));
        var body =
                mvc.perform(get("/gym/mobile/device-commands/9007199254740993"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertEquals(0, JsonUtils.parseObject(body, "code", Integer.class));
        var data = JsonUtils.parseObject(body, "data", Map.class);
        assertEquals("9007199254740993", data.get("id"));
        assertNull(data.get("completedAt"));
        assertEquals(
                Set.of(
                        "id",
                        "deviceId",
                        "command",
                        "status",
                        "requestedAt",
                        "completedAt",
                        "failureReason"),
                data.keySet());
        verify(service).get(9007199254740993L);
    }

    @Test
    void rejectsMalformedIdAndPreservesBusinessNotFound() throws Exception {
        var invalid =
                mvc.perform(get("/gym/mobile/device-commands/not-an-id")).andReturn().getResponse();
        assertEquals(
                400, JsonUtils.parseObject(invalid.getContentAsString(), "code", Integer.class));
        verifyNoInteractions(service);
        when(service.get(12L))
                .thenThrow(
                        new cn.iocoder.yudao.framework.common.exception.ServiceException(
                                cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants
                                        .DEVICE_COMMAND_NOT_EXISTS));
        var missing = mvc.perform(get("/gym/mobile/device-commands/12")).andReturn().getResponse();
        assertEquals(
                1070017008,
                JsonUtils.parseObject(missing.getContentAsString(), "code", Integer.class));
    }
}
