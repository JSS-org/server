package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileRiskVO.*;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileRiskService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobileRiskControllerTest {
    GymMobileRiskService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileRiskService.class);
        var controller = new GymMobileRiskController();
        ReflectionTestUtils.setField(controller, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "risk", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions r) throws Exception {
        return JsonUtils.parseObject(
                r.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    Alert alert() {
        return new Alert(
                "12",
                "tailgating",
                "3",
                null,
                "2026-10-26T10:00+08:00",
                "resolved",
                List.of("9"),
                2,
                new Resolution("false_positive", "已复核", "8", "2026-10-26T10:01+08:00"));
    }

    @Test
    void pageAndResolutionBindScopeVersionAndKeyWithoutExposingRawSource() throws Exception {
        when(service.page(any())).thenReturn(new PageResult<>(List.of(alert()), 1L));
        var r =
                mvc.perform(
                        get("/gym/mobile/risk-alerts")
                                .param("storeId", "101")
                                .param("status", "resolved")
                                .param("pageNo", "2")
                                .param("pageSize", "5"));
        assertEquals(0, code(r));
        verify(service)
                .page(
                        argThat(
                                q ->
                                        q.getStoreId() == 101L
                                                && q.getPageNo() == 2
                                                && q.getPageSize() == 5
                                                && "resolved".equals(q.getStatus())));
        var page =
                JsonUtils.parseObject(
                        r.andReturn().getResponse().getContentAsString(), "data", Map.class);
        var row = (Map<?, ?>) ((List<?>) page.get("list")).getFirst();
        assertEquals(
                Set.of(
                        "id",
                        "type",
                        "deviceId",
                        "memberId",
                        "occurredAt",
                        "status",
                        "evidenceFileIds",
                        "version",
                        "resolution"),
                row.keySet());
        when(service.resolve(anyLong(), any(), anyString())).thenReturn(alert());
        var result =
                mvc.perform(
                        post("/gym/mobile/risk-alerts/12/resolutions")
                                .header("Idempotency-Key", "risk-resolution-01")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"result\":\"false_positive\",\"note\":\"已复核\",\"expectedVersion\":1}"));
        assertEquals(0, code(result));
        verify(service)
                .resolve(
                        eq(12L),
                        argThat(
                                q ->
                                        q.getExpectedVersion() == 1
                                                && "false_positive".equals(q.getResult())),
                        eq("risk-resolution-01"));
    }

    @Test
    void missingStoreInvalidStatusMissingVersionAndMissingKeyRejectBeforeService()
            throws Exception {
        assertEquals(400, code(mvc.perform(get("/gym/mobile/risk-alerts"))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                get("/gym/mobile/risk-alerts")
                                        .param("storeId", "101")
                                        .param("status", "all"))));
        for (var body :
                List.of(
                        "{\"result\":\"confirmed\",\"note\":\"x\"}",
                        "{\"result\":\"freeze\",\"note\":\"x\",\"expectedVersion\":1}",
                        "{\"result\":\"confirmed\",\"note\":\" \",\"expectedVersion\":1}"))
            assertEquals(
                    400,
                    code(
                            mvc.perform(
                                    post("/gym/mobile/risk-alerts/12/resolutions")
                                            .header("Idempotency-Key", "risk-invalid-01")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(body))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                post("/gym/mobile/risk-alerts/12/resolutions")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"result\":\"confirmed\",\"note\":\"ok\",\"expectedVersion\":1}"))));
        verifyNoInteractions(service);
    }

    @Test
    void evidenceUsesPrivateDownloadAndErrorsAreNotSuccessfulBlobResponses() throws Exception {
        byte[] data = {1, 2, 3};
        when(service.attachment(12L, 9L))
                .thenReturn(
                        new cn.iocoder.yudao.module.gym.service.file.dto.GymFileContentDTO(
                                "evidence.png", "image/png", data));
        var response =
                mvc.perform(get("/gym/mobile/risk-alerts/12/evidence/9/content"))
                        .andReturn()
                        .getResponse();
        assertEquals(200, response.getStatus());
        assertArrayEquals(data, response.getContentAsByteArray());
        assertEquals("private, no-store", response.getHeader("Cache-Control"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        when(service.attachment(12L, 9L))
                .thenThrow(
                        new cn.iocoder.yudao.framework.common.exception.ServiceException(
                                cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants
                                        .FILE_NOT_EXISTS));
        assertEquals(
                404,
                mvc.perform(get("/gym/mobile/risk-alerts/12/evidence/9/content"))
                        .andReturn()
                        .getResponse()
                        .getStatus());
        doThrow(
                        new cn.iocoder.yudao.framework.common.exception.ServiceException(
                                cn.iocoder.yudao.framework.common.exception.enums
                                        .GlobalErrorCodeConstants.FORBIDDEN))
                .when(service)
                .attachment(12L, 9L);
        assertEquals(
                403,
                mvc.perform(get("/gym/mobile/risk-alerts/12/evidence/9/content"))
                        .andReturn()
                        .getResponse()
                        .getStatus());
    }
}
