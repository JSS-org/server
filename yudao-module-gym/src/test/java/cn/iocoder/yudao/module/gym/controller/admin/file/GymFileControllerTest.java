package cn.iocoder.yudao.module.gym.controller.admin.file;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.service.file.GymFileService;
import cn.iocoder.yudao.module.gym.service.file.dto.GymFileContentDTO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 实际 MVC 异常解析、全局错误转换及方法权限代理，业务文件服务为 mock。 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = GymFileControllerTest.Config.class)
class GymFileControllerTest {

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import(GymFileController.class)
    static class Config {
        @Bean
        GlobalExceptionHandler globalExceptionHandler(ApiErrorLogCommonApi api) {
            return new GlobalExceptionHandler("gym-controller-test", api);
        }
    }

    @Resource private GymFileController controller;
    @Resource private GlobalExceptionHandler globalExceptionHandler;
    @MockitoBean private GymFileService service;
    @MockitoBean private ApiErrorLogCommonApi errorLogApi;
    @MockitoBean(name = "ss") private SecurityFrameworkService security;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        when(security.hasPermission(anyString())).thenReturn(true);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(globalExceptionHandler).build();
    }

    @Test
    void successfulDownloadPreservesBinaryContentAndHeaders() throws Exception {
        byte[] content = "%PDF-1.7\nTest".getBytes(StandardCharsets.UTF_8);
        when(service.getFileContent(1L)).thenReturn(GymFileContentDTO.builder()
                .name("合同.pdf").mimeType("application/pdf").content(content).build());
        var response = mvc.perform(get("/gym/files/1/content")).andExpect(status().isOk()).andReturn().getResponse();
        assertArrayEquals(content, response.getContentAsByteArray());
        assertEquals("application/pdf", response.getContentType());
        assertEquals(String.valueOf(content.length), response.getHeader("Content-Length"));
        assertTrue(response.getHeader("Content-Disposition").startsWith("attachment;"));
    }

    @Test
    void missingRecordReturns404WithBusinessCode() throws Exception {
        when(service.getFileContent(1L)).thenThrow(exception(FILE_NOT_EXISTS));
        assertError(mvc.perform(get("/gym/files/1/content")).andExpect(status().isNotFound()).andReturn(), FILE_NOT_EXISTS.getCode());
    }

    @Test
    void missingStoredContentReturns404WithBusinessCode() throws Exception {
        when(service.getFileContent(1L)).thenThrow(exception(FILE_CONTENT_NOT_EXISTS));
        assertError(mvc.perform(get("/gym/files/1/content")).andExpect(status().isNotFound()).andReturn(), FILE_CONTENT_NOT_EXISTS.getCode());
    }

    @Test
    void crossStoreDownloadReturns403() throws Exception {
        when(service.getFileContent(1L)).thenThrow(exception(FILE_STORE_NO_PERMISSION, 2L));
        assertError(mvc.perform(get("/gym/files/1/content")).andExpect(status().isForbidden()).andReturn(), FILE_STORE_NO_PERMISSION.getCode());
    }

    @Test
    void methodPermissionFailureReturns403WithoutReadingStorage() throws Exception {
        when(security.hasPermission("gym:files:read")).thenReturn(false);
        assertError(mvc.perform(get("/gym/files/1/content")).andExpect(status().isForbidden()).andReturn(), 403);
        verifyNoInteractions(service);
    }

    @Test
    void invalidFileIdReturns400() throws Exception {
        assertError(mvc.perform(get("/gym/files/not-a-number/content")).andExpect(status().isBadRequest()).andReturn(), 400);
        verifyNoInteractions(service);
    }

    @Test
    void unexpectedStorageFailureReturns500() throws Exception {
        when(service.getFileContent(1L)).thenThrow(new IllegalStateException("storage unavailable"));
        assertError(mvc.perform(get("/gym/files/1/content")).andExpect(status().isInternalServerError()).andReturn(), 500);
    }

    @Test
    void wrappedMissingContentStillReturns404() throws Exception {
        when(service.getFileContent(1L)).thenThrow(new IllegalStateException(exception(FILE_CONTENT_NOT_EXISTS)));
        assertError(mvc.perform(get("/gym/files/1/content")).andExpect(status().isNotFound()).andReturn(), FILE_CONTENT_NOT_EXISTS.getCode());
    }

    @Test
    void metadataErrorsKeepFrameworkHttp200Contract() throws Exception {
        when(service.getFile(1L)).thenThrow(exception(FILE_NOT_EXISTS));
        assertError(mvc.perform(get("/gym/files/1")).andExpect(status().isOk()).andReturn(), FILE_NOT_EXISTS.getCode());
    }

    private void assertError(MvcResult result, int code) throws Exception {
        var response = result.getResponse();
        assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals(code, JsonUtils.parseTree(response.getContentAsString(StandardCharsets.UTF_8)).get("code").asInt());
        assertNull(response.getHeader("Content-Disposition"));
    }
}
