package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.GymMobileConversationsController;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileConversationsVO.*;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileConversationsService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

class GymMobileConversationsControllerTest {
    GymMobileConversationsService service;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(GymMobileConversationsService.class);
        var member = new AppGymConversationsController();
        var staff = new GymMobileConversationsController();
        ReflectionTestUtils.setField(member, "service", service);
        ReflectionTestUtils.setField(staff, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(member, staff)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "conversations", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions r) throws Exception {
        return JsonUtils.parseObject(
                r.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    Message message() {
        return new Message("7", "3", "employee", "请确认安排", "2026-10-25T12:00+08:00");
    }

    @Test
    void staffCreatesWithMemberStoreAndRequiresIdempotencyHeader() throws Exception {
        when(service.create(any(), eq("conversation-mvc-key"))).thenReturn(new Created("3"));
        var result =
                mvc.perform(
                        post("/gym/mobile/member-conversations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", "conversation-mvc-key")
                                .content("{\"storeId\":101,\"memberId\":20}"));
        assertEquals(0, code(result));
        verify(service)
                .create(
                        argThat(r -> r.getStoreId() == 101L && r.getMemberId() == 20L),
                        eq("conversation-mvc-key"));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                post("/gym/mobile/member-conversations")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"storeId\":101,\"memberId\":20}"))));
    }

    @Test
    void bothHistoryRoutesBindCursorAndOnlyReturnPublicMessageFields() throws Exception {
        var page = new PageResult<>(List.of(message()), 1L);
        when(service.staffMessages(eq(3L), any())).thenReturn(page);
        when(service.memberMessages(eq(3L), any())).thenReturn(page);
        for (String path :
                List.of(
                        "/gym/mobile/member-conversations/3/messages",
                        "/gym/me/conversations/3/messages")) {
            var result = mvc.perform(get(path).param("beforeId", "9").param("pageSize", "2"));
            assertEquals(0, code(result));
            var data =
                    JsonUtils.parseObject(
                            result.andReturn().getResponse().getContentAsString(),
                            "data",
                            Map.class);
            var row = (Map<?, ?>) ((List<?>) data.get("list")).getFirst();
            assertEquals(
                    Set.of("id", "conversationId", "senderType", "content", "createdAt"),
                    row.keySet());
            assertEquals("7", row.get("id"));
        }
        verify(service)
                .staffMessages(eq(3L), argThat(q -> q.getBeforeId() == 9L && q.getPageSize() == 2));
        verify(service).memberMessages(eq(3L), argThat(q -> q.getBeforeId() == 9L));
    }

    @Test
    void sendsAcceptOnlyContentAndUseSeparateMemberAndStaffMethods() throws Exception {
        when(service.staffSend(eq(3L), any(), eq("conversation-send-key"))).thenReturn(message());
        when(service.memberSend(eq(3L), any(), eq("conversation-send-key"))).thenReturn(message());
        for (String path :
                List.of(
                        "/gym/mobile/member-conversations/3/messages",
                        "/gym/me/conversations/3/messages")) {
            assertEquals(
                    0,
                    code(
                            mvc.perform(
                                    post(path)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .header("Idempotency-Key", "conversation-send-key")
                                            .content("{\"content\":\"你好\"}"))));
            for (String body :
                    List.of(
                            "{\"content\":\"你好\",\"senderType\":\"employee\"}",
                            "{\"content\":\"你好\",\"userId\":1}",
                            "{\"content\":\"   \"}"))
                assertNotEquals(
                        0,
                        code(
                                mvc.perform(
                                        post(path)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .header("Idempotency-Key", "conversation-send-key")
                                                .content(body))));
        }
        verify(service)
                .staffSend(
                        eq(3L),
                        argThat(r -> "你好".equals(r.getContent())),
                        eq("conversation-send-key"));
        verify(service)
                .memberSend(
                        eq(3L),
                        argThat(r -> "你好".equals(r.getContent())),
                        eq("conversation-send-key"));
    }

    @Test
    void memberSummaryAndReadAcknowledgementHaveDistinctContracts() throws Exception {
        when(service.memberConversations(any()))
                .thenReturn(
                        new PageResult<>(List.of(new Conversation("3", "101", 1, message())), 1L));
        assertEquals(0, code(mvc.perform(get("/gym/me/conversations").param("memberId", "999"))));
        verify(service).memberConversations(any());
        verify(service, never()).memberRead(anyLong(), any());
        when(service.memberRead(eq(3L), any())).thenReturn(new ReadState("7", 0));
        assertEquals(
                0,
                code(
                        mvc.perform(
                                post("/gym/me/conversations/3/read")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"lastReadMessageId\":7}"))));
        verify(service).memberRead(eq(3L), argThat(r -> r.getLastReadMessageId() == 7L));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                post("/gym/me/conversations/3/read")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))));
        assertEquals(
                400,
                code(mvc.perform(get("/gym/me/conversations/3/messages").param("beforeId", "0"))));
        assertEquals(400, code(mvc.perform(get("/gym/me/conversations").param("pageSize", "-1"))));
    }

    @Test
    void everyConversationEndpointDisablesRequestAndResponseBodyLogging() {
        for (Class<?> controller :
                List.of(
                        AppGymConversationsController.class,
                        GymMobileConversationsController.class))
            for (var method : controller.getDeclaredMethods()) {
                var log = method.getAnnotation(ApiAccessLog.class);
                assertNotNull(log);
                assertFalse(log.requestEnable());
                assertFalse(log.responseEnable());
            }
    }
}
