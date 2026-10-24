package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.file.dto.GymFileContentDTO;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobilePublicService;

import jakarta.annotation.security.PermitAll;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobilePublicControllerTest {
    GymMobilePublicService content;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        content = mock(GymMobilePublicService.class);
        var handler = new GlobalExceptionHandler("public-http", mock(ApiErrorLogCommonApi.class));
        var controller = new AppGymPublicController();
        ReflectionTestUtils.setField(controller, "content", content);
        var media = new AppGymPublicMediaController();
        ReflectionTestUtils.setField(media, "content", content);
        ReflectionTestUtils.setField(media, "exceptionHandler", handler);
        mvc =
                MockMvcBuilders.standaloneSetup(controller, media)
                        .setControllerAdvice(new GymMobileExceptionAdvice(), handler)
                        .build();
    }

    int code(ResultActions result) throws Exception {
        return JsonUtils.parseObject(
                result.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void allFivePublicRoutesBindWithoutIdentityAndControllersDeclareAnonymousAccess()
            throws Exception {
        assertTrue(AppGymPublicController.class.isAnnotationPresent(PermitAll.class));
        assertTrue(AppGymPublicMediaController.class.isAnnotationPresent(PermitAll.class));
        assertEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/stores")
                                        .param("longitude", "121.5")
                                        .param("latitude", "31.2")
                                        .param("keyword", "测试")
                                        .param("pageSize", "5"))));
        verify(content)
                .stores(
                        argThat(
                                r ->
                                        r.getLongitude().toPlainString().equals("121.5")
                                                && r.getLatitude().toPlainString().equals("31.2")
                                                && r.getPageSize() == 5
                                                && "测试".equals(r.getKeyword())));
        assertEquals(0, code(mvc.perform(get("/gym/stores/101"))));
        verify(content).store(101L);
        assertEquals(0, code(mvc.perform(get("/gym/stores/101/home"))));
        verify(content).home(101L);
        assertEquals(0, code(mvc.perform(get("/gym/stores/101/occupancy"))));
        verify(content).occupancy(101L);
        assertEquals(0, code(mvc.perform(get("/gym/client-settings").param("storeId", "101"))));
        verify(content).settings(101L);
        assertEquals(0, code(mvc.perform(get("/gym/client-settings"))));
        verify(content).settings(null);
    }

    @Test
    void invalidCoordinatesAndUnboundedPagesAreRejectedBeforeService() throws Exception {
        assertEquals(400, code(mvc.perform(get("/gym/stores").param("longitude", "1"))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                get("/gym/stores")
                                        .param("longitude", "181")
                                        .param("latitude", "1"))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                get("/gym/stores")
                                        .param("longitude", "1")
                                        .param("latitude", "91"))));
        assertEquals(400, code(mvc.perform(get("/gym/stores").param("pageSize", "-1"))));
        assertEquals(400, code(mvc.perform(get("/gym/stores").param("pageSize", "201"))));
        verifyNoInteractions(content);
    }

    @Test
    void publicMediaSupportsByteRangesAndDoesNotCacheRevokedAssets() throws Exception {
        byte[] bytes = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        when(content.mediaContent(7L))
                .thenReturn(new GymFileContentDTO("video.mp4", "video/mp4", bytes));
        var full = mvc.perform(get("/gym/public-media/7/content")).andReturn().getResponse();
        assertEquals(200, full.getStatus());
        assertEquals("no-store", full.getHeader("Cache-Control"));
        assertEquals("nosniff", full.getHeader("X-Content-Type-Options"));
        assertArrayEquals(bytes, full.getContentAsByteArray());
        var partial =
                mvc.perform(get("/gym/public-media/7/content").header("Range", "bytes=2-5"))
                        .andReturn()
                        .getResponse();
        assertEquals(206, partial.getStatus());
        assertEquals("bytes 2-5/10", partial.getHeader("Content-Range"));
        assertArrayEquals(new byte[] {2, 3, 4, 5}, partial.getContentAsByteArray());
        assertEquals(
                416,
                mvc.perform(get("/gym/public-media/7/content").header("Range", "bytes=20-30"))
                        .andReturn()
                        .getResponse()
                        .getStatus());
    }

    @Test
    void unpublishedOrMissingPublicMediaReturns404() throws Exception {
        for (var error : List.of(FILE_NOT_EXISTS, FILE_CONTENT_NOT_EXISTS)) {
            doThrow(exception(error)).when(content).mediaContent(7L);
            var response =
                    mvc.perform(get("/gym/public-media/7/content")).andReturn().getResponse();
            assertEquals(404, response.getStatus());
            assertEquals("no-store", response.getHeader("Cache-Control"));
        }
    }
}
