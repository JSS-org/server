package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileIdentityVO.*;
import cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileWebConfiguration;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileIdentityService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

/** HTTP binding checks in addition to database authorization tests. */
class GymMobileIdentityControllerTest {
    GymMobileIdentityService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileIdentityService.class);
        var controller = new AppGymIdentityController();
        ReflectionTestUtils.setField(controller, "identities", service);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(
                                new cn.iocoder.yudao.module.gym.framework.mobile
                                        .GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "mobile-http-test", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return JsonUtils.parseObject(
                result.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void initializationRequiresKeyAndPositiveStore() throws Exception {
        assertEquals(
                400,
                code(
                        mvc.perform(
                                post("/gym/me/profile-initializations")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"storeId\":101,\"source\":\"miniapp\"}"))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                post("/gym/me/profile-initializations")
                                        .header("Idempotency-Key", "http-test-key")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"storeId\":-1,\"source\":\"miniapp\"}"))));
        verifyNoInteractions(service);
    }

    @Test
    void profileRejectsPhoneAndUnknownFieldsBeforeCallingService() throws Exception {
        assertEquals(
                ErrorCodeConstants.MOBILE_PHONE_IMMUTABLE.getCode(),
                code(
                        mvc.perform(
                                patch("/gym/me/profile")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"phone\":\"13900000000\"}"))));
        assertEquals(
                ErrorCodeConstants.MOBILE_REQUEST_INVALID.getCode(),
                code(
                        mvc.perform(
                                patch("/gym/me/profile")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"memberId\":12}"))));
        verifyNoInteractions(service);
    }

    @Test
    void explicitAvatarNullClearsButOmissionKeepsExisting() throws Exception {
        when(service.updateProfile(any()))
                .thenReturn(new Profile("1", "昵称", null, "138****0001", "unregistered"));
        assertEquals(
                0,
                code(
                        mvc.perform(
                                patch("/gym/me/profile")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"avatarFileId\":null}"))));
        verify(service)
                .updateProfile(argThat(p -> p.isAvatarPresent() && p.getAvatarFileId() == null));
        reset(service);
        assertEquals(
                0,
                code(
                        mvc.perform(
                                patch("/gym/me/profile")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"nickname\":\"昵称\"}"))));
        verify(service).updateProfile(argThat(p -> !p.isAvatarPresent()));
    }

    @RestController
    static class GenericPhoneController {
        @PutMapping({
            "/app-api/member/user/update-mobile",
            "/app-api/member/user/update-mobile-by-weixin"
        })
        public int update() {
            fail("generic member phone mutation must be intercepted");
            return 0;
        }
    }

    @Test
    void genericMemberPhoneEndpointsCannotBypassGymRestriction() throws Exception {
        var interceptor =
                new GymMobileWebConfiguration().gymImmutableMemberPhone(new WebProperties());
        var phoneMvc =
                MockMvcBuilders.standaloneSetup(new GenericPhoneController())
                        .addMappedInterceptors(
                                interceptor.getIncludePathPatterns(), interceptor.getInterceptor())
                        .setControllerAdvice(
                                new GlobalExceptionHandler(
                                        "phone-test", mock(ApiErrorLogCommonApi.class)))
                        .build();
        for (String path : new String[] {"update-mobile", "update-mobile-by-weixin"}) {
            assertEquals(
                    ErrorCodeConstants.MOBILE_PHONE_IMMUTABLE.getCode(),
                    code(phoneMvc.perform(put("/app-api/member/user/" + path))));
        }
    }
}
