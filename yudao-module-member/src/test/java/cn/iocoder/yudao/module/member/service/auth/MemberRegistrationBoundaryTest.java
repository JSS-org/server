package cn.iocoder.yudao.module.member.service.auth;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.system.api.logger.LoginLogApi;
import cn.iocoder.yudao.module.system.api.sms.SmsCodeApi;
import cn.iocoder.yudao.module.system.enums.sms.SmsSceneEnum;
import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.service.CaptchaService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class MemberRegistrationBoundaryTest extends BaseMockitoUnitTest {
    @InjectMocks private MemberAuthServiceImpl service;
    @Mock private MemberUserService users;
    @Mock private SmsCodeApi sms;
    @Mock private CaptchaService captcha;
    @Mock private OAuth2TokenCommonApi tokens;
    @Mock private LoginLogApi logs;

    private AppAuthRegisterReqVO request() {
        return new AppAuthRegisterReqVO().setMobile("15601691300").setCode("123456")
                .setPassword("fixture-only").setNickname("新会员");
    }

    @Test void createsMemberTokenOnlyAfterSmsProof() {
        long id = 9007199254740993L;
        when(users.registerUser(anyString(), anyString(), anyString(), isNull(), anyInt()))
                .thenReturn(new MemberUserDO().setId(id).setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(tokens.createAccessToken(any())).thenReturn(new OAuth2AccessTokenRespDTO()
                .setUserId(id).setUserType(UserTypeEnum.MEMBER.getValue())
                .setAccessToken("fixture-access").setRefreshToken("fixture-refresh")
                .setExpiresTime(LocalDateTime.now().plusHours(1)));
        var result = service.register(request());
        assertEquals(id, result.getUserId());
        var order = inOrder(users, sms, tokens);
        order.verify(users).getUserByMobile("15601691300");
        order.verify(sms).useSmsCode(argThat(r -> r.getMobile().equals("15601691300")
                && r.getCode().equals("123456") && r.getScene().equals(SmsSceneEnum.MEMBER_LOGIN.getScene())));
        order.verify(users).registerUser(eq("15601691300"), eq("fixture-only"), eq("新会员"), isNull(), anyInt());
        order.verify(tokens).createAccessToken(argThat(r -> r.getUserId().equals(id)
                && r.getUserType().equals(UserTypeEnum.MEMBER.getValue())));
        verify(users, never()).createUserIfAbsent(anyString(), any(), anyInt());
    }

    @Test void existingMobileCannotBeOverwritten() {
        when(users.getUserByMobile(anyString())).thenReturn(new MemberUserDO().setId(41L));
        assertServiceException(() -> service.register(request()), AUTH_MOBILE_USED);
        verifyNoInteractions(sms, tokens);
        verify(users, never()).registerUser(anyString(), anyString(), anyString(), any(), anyInt());
    }

    @Test void rejectedSmsCannotCreateAnAccount() {
        doThrow(new IllegalArgumentException("fixture invalid code")).when(sms).useSmsCode(any());
        assertThrows(IllegalArgumentException.class, () -> service.register(request()));
        verify(users, never()).registerUser(anyString(), anyString(), anyString(), any(), anyInt());
        verifyNoInteractions(tokens);
    }

    @Test void submittedInvalidCaptchaStopsRegistration() {
        when(captcha.verification(any())).thenReturn(ResponseModel.errorMsg("fixture invalid captcha"));
        assertServiceException(() -> service.register(request().setCaptchaVerification("invalid")), AUTH_CAPTCHA_INVALID);
        verifyNoInteractions(sms, tokens);
    }

    @ParameterizedTest @ValueSource(ints = {21, 22, 23})
    void memberEndpointsRejectAdminSmsScenes(int scene) {
        assertServiceException(() -> service.sendSmsCode(null,
                new AppAuthSmsSendReqVO().setMobile("15601691300").setScene(scene)), AUTH_SMS_SCENE_INVALID);
        verifyNoInteractions(sms, users);
    }

    @Test void accountMutationSmsRequiresMemberLogin() {
        assertServiceException(() -> service.sendSmsCode(null,
                new AppAuthSmsSendReqVO().setScene(SmsSceneEnum.MEMBER_UPDATE_PASSWORD.getScene())),
                cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED);
        verifyNoInteractions(sms, users);
    }
}
