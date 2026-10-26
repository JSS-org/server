package cn.iocoder.yudao.module.gym.controller.app.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.GymMobileActivityController;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.GymMobileActivityService;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

class GymMobileActivitiesControllerTest {
    GymMobileActivityService service;
    MockMvc mvc;
    static final String BODY =
            "{\"storeId\":101,\"title\":\"活动\",\"description\":\"介绍\",\"startsAt\":\"2026-10-25T10:00:00+08:00\",\"endsAt\":\"2026-10-25T12:00:00+08:00\",\"registrationDeadline\":\"2026-10-25T09:00:00+08:00\",\"capacity\":10}";

    @BeforeEach
    void setup() {
        service = mock(GymMobileActivityService.class);
        var a = new AppGymActivitiesController();
        var b = new GymMobileActivityController();
        ReflectionTestUtils.setField(a, "service", service);
        ReflectionTestUtils.setField(b, "service", service);
        mvc =
                MockMvcBuilders.standaloneSetup(a, b)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "activities-http", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions a) throws Exception {
        return JsonUtils.parseObject(
                a.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    @Test
    void bindsAllSixRoutesAndRequiredIdempotencyKeys() throws Exception {
        assertEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/activities")
                                        .param("storeId", "101")
                                        .param("status", "open"))));
        verify(service).page(argThat(r -> r.getStoreId() == 101L && r.getStatus().equals("open")));
        assertEquals(0, code(mvc.perform(get("/gym/activities/7"))));
        verify(service).get(7L);
        assertEquals(
                0,
                code(
                        mvc.perform(
                                post("/gym/activities/7/registrations")
                                        .header("Idempotency-Key", "activity-register-http"))));
        verify(service).register(7L, "activity-register-http");
        assertEquals(0, code(mvc.perform(delete("/gym/activities/7/registrations/me"))));
        verify(service).cancel(7L);
        assertEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/me/activity-registrations")
                                        .param("status", "cancelled")
                                        .param("memberId", "99"))));
        verify(service).history(argThat(r -> r.getStatus().equals("cancelled")));
        assertEquals(
                0,
                code(
                        mvc.perform(
                                post("/gym/mobile/activities")
                                        .header("Idempotency-Key", "activity-create-http")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(BODY))));
        verify(service)
                .create(
                        argThat(
                                r ->
                                        r.getStartsAt()
                                                        .equals(
                                                                java.time.Instant.parse(
                                                                        "2026-10-25T02:00:00Z"))
                                                && r.getCapacity() == 10),
                        eq("activity-create-http"));
    }

    @Test
    void rejectsMalformedPublicFiltersAndCannotInjectMemberIntoBody() throws Exception {
        assertEquals(400, code(mvc.perform(get("/gym/activities"))));
        assertEquals(400, code(mvc.perform(get("/gym/activities").param("storeId", "0"))));
        for (String field : List.of("draft", "cancelled", "invalid"))
            assertEquals(
                    400,
                    code(
                            mvc.perform(
                                    get("/gym/activities")
                                            .param("storeId", "101")
                                            .param("status", field))));
        assertEquals(
                400,
                code(mvc.perform(get("/gym/me/activity-registrations").param("pageSize", "201"))));
        assertEquals(400, code(mvc.perform(post("/gym/activities/7/registrations"))));
        assertEquals(
                cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.MOBILE_REQUEST_INVALID
                        .getCode(),
                code(
                        mvc.perform(
                                post("/gym/activities/7/registrations")
                                        .header("Idempotency-Key", "activity-forged-body")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"memberId\":99}"))));
        verifyNoInteractions(service);
    }

    @Test
    void createRejectsMissingKeyInvalidCapacityAndForgedPublicationState() throws Exception {
        assertEquals(
                400,
                code(
                        mvc.perform(
                                post("/gym/mobile/activities")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(BODY))));
        for (String body :
                List.of(
                        BODY.replace("\"capacity\":10", "\"capacity\":0"),
                        BODY.replace("\"capacity\":10", "\"capacity\":100001"),
                        BODY.replace("\"title\":\"活动\"", "\"title\":\"\"")))
            assertEquals(
                    400,
                    code(
                            mvc.perform(
                                    post("/gym/mobile/activities")
                                            .header("Idempotency-Key", "activity-invalid-http")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(body))));
        assertEquals(
                cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.MOBILE_REQUEST_INVALID
                        .getCode(),
                code(
                        mvc.perform(
                                post("/gym/mobile/activities")
                                        .header("Idempotency-Key", "activity-invalid-http")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                BODY.replace(
                                                        "\"capacity\":10",
                                                        "\"capacity\":10,\"status\":\"published\"")))));
        verifyNoInteractions(service);
    }
}
