package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.file.dto.GymFileContentDTO;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileCoachProfileService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

class GymMobileCoachProfileControllerTest {
    static final String PATH = "/gym/mobile/me/coach-profile";
    GymMobileCoachProfileService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileCoachProfileService.class);
        var controller = new GymMobileCoachProfileController();
        var handler =
                new GlobalExceptionHandler("coach-profile-http", mock(ApiErrorLogCommonApi.class));
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "exceptionHandler", handler);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GymMobileExceptionAdvice(), handler)
                        .build();
    }

    int code(ResultActions result) throws Exception {
        return JsonUtils.parseObject(
                result.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void profileRoutesBindSparseFieldsAndDistinguishOmittedFromExplicitNull() throws Exception {
        assertEquals(0, code(mvc.perform(get(PATH))));
        verify(service).get();
        assertEquals(
                0,
                code(
                        mvc.perform(
                                patch(PATH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"introduction\":\"介绍\",\"specialties\":[],\"qualificationFileIds\":[]}"))));
        verify(service)
                .patch(
                        argThat(
                                r ->
                                        !r.isAvatarPresent()
                                                && r.isIntroductionPresent()
                                                && r.getIntroduction().equals("介绍")
                                                && r.isSpecialtiesPresent()
                                                && r.isQualificationsPresent()));
        assertEquals(
                0,
                code(
                        mvc.perform(
                                patch(PATH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"avatarFileId\":null}"))));
        verify(service)
                .patch(
                        argThat(
                                r ->
                                        r.isAvatarPresent()
                                                && r.getAvatarFileId() == null
                                                && !r.isIntroductionPresent()));
    }

    @Test
    void profileCannotAcceptForgedIdentityPublicationOrUnboundedFields() throws Exception {
        for (String field :
                List.of(
                        "employeeId",
                        "userId",
                        "name",
                        "published",
                        "coachPublished",
                        "publicCoachProfile",
                        "storeId")) {
            assertEquals(
                    MOBILE_REQUEST_INVALID.getCode(),
                    code(
                            mvc.perform(
                                    patch(PATH)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("{\"" + field + "\":1}"))));
        }
        assertEquals(
                400,
                code(
                        mvc.perform(
                                patch(PATH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"avatarFileId\":0}"))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                patch(PATH)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"introduction\":\""
                                                        + "a".repeat(4001)
                                                        + "\"}"))));
        verifyNoInteractions(service);
    }

    @Test
    void privatePreviewReturnsBytesWithoutCachingAndMapsMissingOrUnownedFiles() throws Exception {
        byte[] bytes = {1, 2, 3, 4};
        when(service.content(7L))
                .thenReturn(new GymFileContentDTO("photo.png", "image/png", bytes));
        var result = mvc.perform(get(PATH + "/files/7/content")).andReturn().getResponse();
        assertEquals(200, result.getStatus());
        assertArrayEquals(bytes, result.getContentAsByteArray());
        assertEquals("image/png", result.getContentType());
        assertEquals("no-store", result.getHeader("Cache-Control"));
        assertEquals("nosniff", result.getHeader("X-Content-Type-Options"));
        when(service.content(8L)).thenThrow(new ServiceException(FILE_NOT_EXISTS));
        assertEquals(
                404,
                mvc.perform(get(PATH + "/files/8/content")).andReturn().getResponse().getStatus());
        when(service.content(9L)).thenThrow(new ServiceException(MOBILE_FILE_NOT_OWNED));
        assertEquals(
                403,
                mvc.perform(get(PATH + "/files/9/content")).andReturn().getResponse().getStatus());
    }
}
