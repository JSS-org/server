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
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileWorkFilesService;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

class GymMobileWorkFilesControllerTest {
    GymMobileWorkFilesService service;
    MockMvc mvc;
    static final String PATH = "/gym/mobile/files";

    @BeforeEach
    void setup() {
        service = mock(GymMobileWorkFilesService.class);
        var controller = new GymMobileWorkFilesController();
        var handler =
                new GlobalExceptionHandler("work-files-http", mock(ApiErrorLogCommonApi.class));
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "exceptionHandler", handler);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GymMobileExceptionAdvice(), handler)
                        .build();
    }

    MockMultipartFile file() {
        return new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2});
    }

    int code(ResultActions result) throws Exception {
        return JsonUtils.parseObject(
                result.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void multipartBindsPurposeAndBusinessTargetWithoutAcceptingUploaderOrCategory()
            throws Exception {
        assertEquals(
                0,
                code(
                        mvc.perform(
                                multipart(PATH)
                                        .file(file())
                                        .param("purpose", "body_report")
                                        .param("storeId", "101")
                                        .param("memberId", "7"))));
        verify(service)
                .upload(
                        argThat(
                                r ->
                                        "body_report".equals(r.getPurpose())
                                                && r.getStoreId() == 101L
                                                && r.getMemberId() == 7L
                                                && r.getFile() != null));
        for (String field : List.of("uploaderId", "employeeId", "category", "tenantId")) {
            assertEquals(
                    MOBILE_REQUEST_INVALID.getCode(),
                    code(
                            mvc.perform(
                                    multipart(PATH)
                                            .file(file())
                                            .param("purpose", "coach_profile")
                                            .param("storeId", "101")
                                            .param(field, "91"))));
        }
        verifyNoMoreInteractions(service);
    }

    @Test
    void requiredAndInvalidMultipartValuesFailBeforeService() throws Exception {
        assertEquals(
                400,
                code(
                        mvc.perform(
                                multipart(PATH)
                                        .param("purpose", "body_report")
                                        .param("storeId", "101"))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                multipart(PATH)
                                        .file(file())
                                        .param("purpose", "other")
                                        .param("storeId", "101"))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                multipart(PATH)
                                        .file(file())
                                        .param("purpose", "contract")
                                        .param("storeId", "0"))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                multipart(PATH)
                                        .file(file())
                                        .param("purpose", "contract")
                                        .param("storeId", "101")
                                        .param("memberId", "-1"))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                multipart(PATH)
                                        .file(file())
                                        .param("purpose", "coach_profile")
                                        .header("storeId", "101"))));
        verifyNoInteractions(service);
    }

    @Test
    void downloadStreamsAttachmentWithNoStoreAndUsesNonSuccessStatusForDeniedBytes()
            throws Exception {
        when(service.content(7L))
                .thenReturn(
                        new GymFileContentDTO(
                                "report.pdf", "application/pdf", new byte[] {1, 2, 3}));
        var response = mvc.perform(get(PATH + "/7/content")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertArrayEquals(new byte[] {1, 2, 3}, response.getContentAsByteArray());
        assertEquals("application/pdf", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").contains("attachment"));
        assertEquals("private, no-store", response.getHeader("Cache-Control"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        when(service.content(8L)).thenThrow(new ServiceException(FILE_NOT_EXISTS));
        assertEquals(
                404, mvc.perform(get(PATH + "/8/content")).andReturn().getResponse().getStatus());
        when(service.content(9L)).thenThrow(new ServiceException(MOBILE_FILE_NOT_OWNED));
        assertEquals(
                403, mvc.perform(get(PATH + "/9/content")).andReturn().getResponse().getStatus());
    }
}
