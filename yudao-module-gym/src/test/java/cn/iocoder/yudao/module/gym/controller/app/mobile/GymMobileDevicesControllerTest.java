package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.GymMobileDevicesController;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileDevicesVO.*;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileDevicesService;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobileDevicesControllerTest {
    GymMobileDevicesService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileDevicesService.class);
        var controller = new GymMobileDevicesController();
        ReflectionTestUtils.setField(controller, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(
                                new GlobalExceptionHandler(
                                        "mobile-devices", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions result) throws Exception {
        return JsonUtils.parseObject(
                result.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    Map<?, ?> firstRow(ResultActions result) throws Exception {
        var data =
                JsonUtils.parseObject(
                        result.andReturn().getResponse().getContentAsString(), "data", Map.class);
        return (Map<?, ?>) ((List<?>) data.get("list")).getFirst();
    }

    @Test
    void devicesBindStoreTypePaginationAndExposeOnlyFourFields() throws Exception {
        when(service.devices(any()))
                .thenReturn(
                        new PageResult<>(
                                List.of(new Device("7", "前台柜机", "locker", "inactive")), 5L));
        var result =
                mvc.perform(
                        get("/gym/mobile/devices")
                                .param("storeId", "101")
                                .param("type", "locker")
                                .param("pageNo", "2")
                                .param("pageSize", "3"));
        assertEquals(0, code(result));
        verify(service)
                .devices(
                        argThat(
                                q ->
                                        q.getStoreId() == 101L
                                                && "locker".equals(q.getType())
                                                && q.getPageNo() == 2
                                                && q.getPageSize() == 3));
        var row = firstRow(result);
        assertEquals(Set.of("id", "name", "type", "status"), row.keySet());
        assertEquals("7", row.get("id"));
        assertEquals("inactive", row.get("status"));
    }

    @Test
    void compartmentsBindParentAndReturnStringIdsWithConfiguredState() throws Exception {
        when(service.compartments(eq(7L), any()))
                .thenReturn(
                        new PageResult<>(List.of(new Compartment("9", "A01", "available")), 1L));
        var result =
                mvc.perform(
                        get("/gym/mobile/devices/7/compartments")
                                .param("pageSize", "20")
                                .param("storeId", "999"));
        assertEquals(0, code(result));
        verify(service).compartments(eq(7L), argThat(q -> q.getPageSize() == 20));
        var row = firstRow(result);
        assertEquals(Set.of("id", "number", "status"), row.keySet());
        assertEquals("9", row.get("id"));
        assertEquals("available", row.get("status"));
    }

    @Test
    void missingStoreUnsupportedTypeAndUnboundedPagesAreRejectedBeforeService() throws Exception {
        assertEquals(400, code(mvc.perform(get("/gym/mobile/devices"))));
        assertEquals(400, code(mvc.perform(get("/gym/mobile/devices").param("storeId", "0"))));
        for (String type : List.of("camera", "face_reader", "unknown", ""))
            assertEquals(
                    400,
                    code(
                            mvc.perform(
                                    get("/gym/mobile/devices")
                                            .param("storeId", "101")
                                            .param("type", type))));
        for (String size : List.of("-1", "0", "201")) {
            assertEquals(
                    400,
                    code(
                            mvc.perform(
                                    get("/gym/mobile/devices")
                                            .param("storeId", "101")
                                            .param("pageSize", size))));
            assertEquals(
                    400,
                    code(
                            mvc.perform(
                                    get("/gym/mobile/devices/7/compartments")
                                            .param("pageSize", size))));
        }
        assertEquals(
                400,
                code(mvc.perform(get("/gym/mobile/devices/7/compartments").param("pageNo", "0"))));
        verifyNoInteractions(service);
    }
}
