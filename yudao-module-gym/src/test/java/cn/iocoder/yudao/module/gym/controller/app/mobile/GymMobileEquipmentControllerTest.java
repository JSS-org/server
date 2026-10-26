package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.device.GymEquipmentPublicationController;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileEquipmentVO.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePublicVO.Media;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileEquipmentService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

class GymMobileEquipmentControllerTest {
    GymMobileEquipmentService service;
    MockMvc mvc;
    static final String SCENE = "1234567890abcdef1234567890abcdef";

    @BeforeEach
    void setup() {
        service = mock(GymMobileEquipmentService.class);
        var a = new AppGymEquipmentController();
        var b = new GymEquipmentPublicationController();
        ReflectionTestUtils.setField(a, "service", service);
        ReflectionTestUtils.setField(b, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(a, b)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "equipment-http", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions result) throws Exception {
        return JsonUtils.parseObject(
                result.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void bindsBothPublicRoutesAndExplicitPublicationRoutes() throws Exception {
        when(service.resolve(any())).thenReturn(new Resolution("equipment", "7", "101"));
        var image =
                new Media(
                        "10",
                        "image",
                        "/app-api/gym/public-media/10/content?tenantId=1",
                        null,
                        null);
        when(service.tutorials(7L))
                .thenReturn(
                        new Tutorials(
                                "7", "器械", List.of(new Tutorial("8", "教学", "纯文本", image, null))));
        var r =
                mvc.perform(
                        post("/gym/qr-resolutions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scene\":\"" + SCENE + "\"}"));
        assertEquals(0, code(r));
        verify(service).resolve(argThat(x -> SCENE.equals(x.getScene())));
        var json =
                mvc.perform(get("/gym/equipment/7/tutorials"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertTrue(json.contains("纯文本"));
        assertTrue(json.contains("public-media"));
        assertFalse(json.contains("publicScene"));
        assertEquals(0, code(mvc.perform(post("/gym/equipment/7/public-scenes"))));
        verify(service).publish(7L);
        assertEquals(0, code(mvc.perform(delete("/gym/equipment/7/public-scenes"))));
        verify(service).revoke(7L);
    }

    @Test
    void rejectsMalformedSceneExtraIdentityFieldsAndPublicationBodies() throws Exception {
        for (String body :
                List.of(
                        "{}",
                        "{\"scene\":null}",
                        "{\"scene\":\"equipment:7\"}",
                        "{\"scene\":\"" + SCENE + "\",\"storeId\":101}",
                        "{\"scene\":\"" + SCENE.toUpperCase() + "\"}"))
            assertNotEquals(
                    0,
                    code(
                            mvc.perform(
                                    post("/gym/qr-resolutions")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(body))));
        assertNotEquals(
                0,
                code(
                        mvc.perform(
                                post("/gym/equipment/7/public-scenes")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"scene\":\"" + SCENE + "\"}"))));
        verifyNoInteractions(service);
    }
}
