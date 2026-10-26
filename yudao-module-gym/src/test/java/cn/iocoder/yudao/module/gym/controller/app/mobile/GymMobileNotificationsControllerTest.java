package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.GymMobileNotificationsController;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileNotificationsVO.*;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileNotificationsService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobileNotificationsControllerTest {
    GymMobileNotificationsService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileNotificationsService.class);
        var member = new AppGymNotificationsController();
        var staff = new GymMobileNotificationsController();
        ReflectionTestUtils.setField(member, "service", service);
        ReflectionTestUtils.setField(staff, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(member, staff)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "notifications", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions r) throws Exception {
        return JsonUtils.parseObject(
                r.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void memberAndStaffRoutesBindOnlyFiltersAndReturnPublicNotificationFields() throws Exception {
        var page =
                new PageResult<>(
                        List.of(
                                new Notification(
                                        "1",
                                        "booking",
                                        "预约已确认",
                                        "请查看详情",
                                        "7",
                                        "101",
                                        "2026-10-25T12:00+08:00",
                                        null)),
                        1L);
        when(service.memberPage(any())).thenReturn(page);
        when(service.staffPage(any())).thenReturn(page);
        for (String path : List.of("/gym/me/notifications", "/gym/mobile/notifications")) {
            var result =
                    mvc.perform(
                            get(path)
                                    .param("type", "booking")
                                    .param("unreadOnly", "true")
                                    .param("pageSize", "3")
                                    .param("recipientUserId", "999"));
            assertEquals(0, code(result));
            var data =
                    JsonUtils.parseObject(
                            result.andReturn().getResponse().getContentAsString(),
                            "data",
                            Map.class);
            var row = (Map<?, ?>) ((List<?>) data.get("list")).getFirst();
            assertEquals(
                    Set.of(
                            "id",
                            "type",
                            "title",
                            "content",
                            "businessId",
                            "storeId",
                            "createdAt",
                            "readAt"),
                    row.keySet());
            assertEquals("7", row.get("businessId"));
        }
        verify(service)
                .memberPage(
                        argThat(
                                q ->
                                        "booking".equals(q.getType())
                                                && Boolean.TRUE.equals(q.getUnreadOnly())
                                                && q.getPageSize() == 3));
        verify(service).staffPage(argThat(q -> "booking".equals(q.getType())));
    }

    @Test
    void naturalIdempotentReadRoutesNeedNoClientIdentityOrIdempotencyKey() throws Exception {
        when(service.memberRead(1L)).thenReturn(true);
        when(service.staffRead(2L)).thenReturn(true);
        assertEquals(0, code(mvc.perform(post("/gym/me/notifications/1/read"))));
        assertEquals(0, code(mvc.perform(post("/gym/mobile/notifications/2/read"))));
        verify(service).memberRead(1L);
        verify(service).staffRead(2L);
    }

    @Test
    void preferencesSaveOnlyTemplateIntentAndRejectIdentityOrProviderState() throws Exception {
        when(service.preferences(any())).thenReturn(new Saved(true));
        var response =
                mvc.perform(
                        post("/gym/me/notification-preferences")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"templateKeys\":[\"booking\",\"entitlement\"],\"enabled\":false}"));
        assertEquals(0, code(response));
        verify(service)
                .preferences(
                        argThat(
                                r ->
                                        r.getTemplateKeys()
                                                        .equals(List.of("booking", "entitlement"))
                                                && !r.getEnabled()));
        reset(service);
        for (String body :
                List.of(
                        "{\"templateKeys\":[\"booking\"],\"enabled\":true,\"userId\":1}",
                        "{\"templateKeys\":[\"booking\"],\"enabled\":true,\"authorized\":true}",
                        "{\"templateKeys\":[],\"enabled\":true}",
                        "{\"templateKeys\":[\"booking\"]}",
                        "{\"templateKeys\":[\"provider_success\"],\"enabled\":true}"))
            assertEquals(
                    body.contains("userId") || body.contains("authorized")
                            ? cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants
                                    .MOBILE_REQUEST_INVALID
                                    .getCode()
                            : 400,
                    code(
                            mvc.perform(
                                    post("/gym/me/notification-preferences")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(body))));
        verifyNoInteractions(service);
    }

    @Test
    void invalidTypeBooleanAndUnboundedPageAreRejected() throws Exception {
        for (String path : List.of("/gym/me/notifications", "/gym/mobile/notifications")) {
            assertEquals(400, code(mvc.perform(get(path).param("type", "other"))));
            assertEquals(400, code(mvc.perform(get(path).param("unreadOnly", "maybe"))));
            assertEquals(400, code(mvc.perform(get(path).param("pageSize", "-1"))));
            assertEquals(400, code(mvc.perform(get(path).param("pageSize", "201"))));
        }
        verifyNoInteractions(service);
    }
}
