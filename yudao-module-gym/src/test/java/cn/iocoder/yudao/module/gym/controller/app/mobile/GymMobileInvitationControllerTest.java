package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileInvitationVO.*;
import cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileInvitationService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobileInvitationControllerTest {
    GymMobileInvitationService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileInvitationService.class);
        var controller = new AppGymInvitationController();
        ReflectionTestUtils.setField(controller, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "invitations", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions result) throws Exception {
        return JsonUtils.parseObject(
                result.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void requiresIdempotencyHeaderAndPositiveStore() throws Exception {
        assertEquals(
                400,
                code(
                        mvc.perform(
                                post("/gym/me/invitations")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"storeId\":101}"))));
        for (String body : List.of("{}", "{\"storeId\":0}", "{\"storeId\":-1}"))
            assertEquals(
                    400,
                    code(
                            mvc.perform(
                                    post("/gym/me/invitations")
                                            .header("Idempotency-Key", "invite-mvc-key")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(body))));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsCallerControlledIdentityCodeAndRewardFields() throws Exception {
        for (String field :
                List.of("memberId", "inviterUserId", "inviteCode", "points", "sharePath"))
            assertEquals(
                    ErrorCodeConstants.MOBILE_REQUEST_INVALID.getCode(),
                    code(
                            mvc.perform(
                                    post("/gym/me/invitations")
                                            .header("Idempotency-Key", "invite-mvc-key")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("{\"storeId\":101,\"" + field + "\":1}"))));
        verifyNoInteractions(service);
    }

    @Test
    void returnsOnlyPublicInvitationFieldsAndDisablesBodyLogs() throws Exception {
        when(service.create(any(), eq("invite-mvc-key")))
                .thenReturn(
                        new Invitation(
                                "a".repeat(32),
                                "/pages/invitation/index?storeId=101&inviteCode=" + "a".repeat(32),
                                "2026-11-24T12:00+08:00"));
        var result =
                mvc.perform(
                        post("/gym/me/invitations")
                                .header("Idempotency-Key", "invite-mvc-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"storeId\":101}"));
        assertEquals(0, code(result));
        var data =
                JsonUtils.parseObject(
                        result.andReturn().getResponse().getContentAsString(), "data", Map.class);
        assertEquals(Set.of("inviteCode", "sharePath", "expiresAt"), data.keySet());
        verify(service).create(argThat(req -> req.getStoreId() == 101L), eq("invite-mvc-key"));
        var log =
                AppGymInvitationController.class
                        .getMethod("create", Create.class, String.class)
                        .getAnnotation(ApiAccessLog.class);
        assertFalse(log.requestEnable());
        assertFalse(log.responseEnable());
    }
}
