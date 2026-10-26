package cn.iocoder.yudao.module.gym.controller.admin.mobile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import cn.iocoder.yudao.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.gym.framework.mobile.GymMobileExceptionAdvice;
import cn.iocoder.yudao.module.gym.service.mobile.*;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

/** HTTP binding only; actual method permissions and database scope are tested in GymOrganizationServiceTest. */
class GymMobileWorkControllerTest {
    GymMobileWorkRecordsService records;
    GymMobileWorkMembersService members;
    GymMobileWorkMemberQueryService queries;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        records = mock(GymMobileWorkRecordsService.class);
        members = mock(GymMobileWorkMembersService.class);
        queries = mock(GymMobileWorkMemberQueryService.class);
        var rc = new GymMobileWorkRecordsController();
        ReflectionTestUtils.setField(rc, "records", records);
        var mc = new GymMobileWorkMembersController();
        ReflectionTestUtils.setField(mc, "members", members);
        ReflectionTestUtils.setField(mc, "queries", queries);
        mvc =
                MockMvcBuilders.standaloneSetup(rc, mc)
                        .setControllerAdvice(
                                new GymMobileExceptionAdvice(),
                                new GlobalExceptionHandler(
                                        "mobile-work-http", mock(ApiErrorLogCommonApi.class)))
                        .build();
    }

    int code(ResultActions result) throws Exception {
        return JsonUtils.parseObject(
                result.andReturn().getResponse().getContentAsString(), "code", Integer.class);
    }

    static final String MEASUREMENT =
            "{\"storeId\":101,\"memberId\":7,\"measuredAt\":\"2026-10-23T12:00:00+08:00\",\"weightKg\":70.01}";
    static final String PLAN =
            "{\"storeId\":101,\"memberId\":7,\"title\":\"训练\",\"content\":\"内容\",\"status\":\"active\"}";
    static final String CONTRACT =
            "{\"storeId\":101,\"memberId\":7,\"sourceType\":\"member_card\",\"sourceId\":8,\"templateId\":9}";
    static final String FOLLOWUP = "{\"storeId\":101,\"content\":\"跟进\"}";
    static final String ASSIGN = "{\"storeId\":101,\"employeeIds\":[11],\"expectedVersion\":2}";

    @Test
    void allWritesRequireIdempotencyHeader() throws Exception {
        for (var e :
                Map.of(
                                "/gym/mobile/body-measurements",
                                MEASUREMENT,
                                "/gym/mobile/training-plans",
                                PLAN,
                                "/gym/mobile/contracts",
                                CONTRACT,
                                "/gym/mobile/members/7/follow-ups",
                                FOLLOWUP)
                        .entrySet())
            assertEquals(
                    400,
                    code(
                            mvc.perform(
                                    post(e.getKey())
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(e.getValue()))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                put("/gym/mobile/members/7/assignments")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(ASSIGN))));
        verifyNoInteractions(records, members);
    }

    @Test
    void writeModelsRejectForgedIdentityAndInternalContractFields() throws Exception {
        for (var e :
                Map.of(
                                "/gym/mobile/body-measurements",
                                MEASUREMENT.replace("}", ",\"creator\":\"999\"}"),
                                "/gym/mobile/training-plans",
                                PLAN.replace("}", ",\"coachId\":999}"),
                                "/gym/mobile/contracts",
                                CONTRACT.replace("}", ",\"signingStatus\":\"signed\"}"),
                                "/gym/mobile/members/7/follow-ups",
                                FOLLOWUP.replace("}", ",\"employeeId\":999}"))
                        .entrySet())
            assertEquals(
                    ErrorCodeConstants.MOBILE_REQUEST_INVALID.getCode(),
                    code(
                            mvc.perform(
                                    post(e.getKey())
                                            .header("Idempotency-Key", "forged-key")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(e.getValue()))));
        verifyNoInteractions(records, members);
    }

    @Test
    void recordListsRequireMemberStoreAndBoundedPagination() throws Exception {
        for (String path :
                List.of(
                        "/gym/mobile/body-measurements",
                        "/gym/mobile/training-plans",
                        "/gym/mobile/contracts")) {
            assertEquals(400, code(mvc.perform(get(path).param("storeId", "101"))));
            assertEquals(
                    400,
                    code(
                            mvc.perform(
                                    get(path)
                                            .param("storeId", "101")
                                            .param("memberId", "7")
                                            .param("pageSize", "-1"))));
            assertEquals(
                    0, code(mvc.perform(get(path).param("storeId", "101").param("memberId", "7"))));
        }
        verify(records).measurements(argThat(r -> r.getStoreId() == 101L && r.getMemberId() == 7L));
        verify(records).plans(argThat(r -> r.getStoreId() == 101L && r.getMemberId() == 7L));
        verify(records).contracts(argThat(r -> r.getStoreId() == 101L && r.getMemberId() == 7L));
        assertEquals(400, code(mvc.perform(get("/gym/mobile/members/7/follow-ups"))));
        assertEquals(
                0,
                code(mvc.perform(get("/gym/mobile/members/7/follow-ups").param("storeId", "101"))));
        verify(members).followups(eq(7L), argThat(r -> r.getStoreId() == 101L));
    }

    @Test
    void eachWriteBindsItsDocumentedFieldsAndPreservesIdempotencyKey() throws Exception {
        for (var e :
                Map.of(
                                "/gym/mobile/body-measurements",
                                MEASUREMENT,
                                "/gym/mobile/training-plans",
                                PLAN,
                                "/gym/mobile/contracts",
                                CONTRACT,
                                "/gym/mobile/members/7/follow-ups",
                                FOLLOWUP)
                        .entrySet())
            assertEquals(
                    0,
                    code(
                            mvc.perform(
                                    post(e.getKey())
                                            .header("Idempotency-Key", "exact-request-key")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(e.getValue()))));
        verify(records)
                .createMeasurement(
                        argThat(
                                r ->
                                        r.getMemberId() == 7L
                                                && r.getMeasuredAt()
                                                        .equals(
                                                                java.time.Instant.parse(
                                                                        "2026-10-23T04:00:00Z"))),
                        eq("exact-request-key"));
        verify(records)
                .createPlan(
                        argThat(r -> "active".equals(r.getStatus()) && "内容".equals(r.getContent())),
                        eq("exact-request-key"));
        verify(records)
                .createContract(
                        argThat(r -> r.getSourceId() == 8L && r.getTemplateId() == 9L),
                        eq("exact-request-key"));
        verify(members)
                .createFollowup(
                        eq(7L), argThat(r -> "跟进".equals(r.getContent())), eq("exact-request-key"));
        assertEquals(
                0,
                code(
                        mvc.perform(
                                put("/gym/mobile/members/7/assignments")
                                        .header("Idempotency-Key", "assign-request-key")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(ASSIGN))));
        verify(members)
                .assign(
                        eq(7L),
                        argThat(
                                r ->
                                        r.getExpectedVersion() == 2
                                                && r.getEmployeeIds().equals(List.of(11L))),
                        eq("assign-request-key"));
    }

    @Test
    void measurementsFollowupsAndAssignmentsRejectOutOfRangeOrIncompleteValues() throws Exception {
        assertEquals(
                400,
                code(
                        mvc.perform(
                                post("/gym/mobile/body-measurements")
                                        .header("Idempotency-Key", "invalid-key")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(MEASUREMENT.replace("70.01", "501")))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                post("/gym/mobile/members/7/follow-ups")
                                        .header("Idempotency-Key", "invalid-key")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(FOLLOWUP.replace("跟进", " ")))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                put("/gym/mobile/members/7/assignments")
                                        .header("Idempotency-Key", "invalid-key")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(ASSIGN.replace(",\"expectedVersion\":2", "")))));
        assertEquals(
                400,
                code(
                        mvc.perform(
                                put("/gym/mobile/members/7/assignments")
                                        .header("Idempotency-Key", "invalid-key")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(ASSIGN.replace("[11]", "[null]")))));
        verifyNoInteractions(records, members);
    }

    @Test
    void memberReadRoutesBindFiltersAndRejectInvalidOrMissingParameters() throws Exception {
        assertEquals(
                0,
                code(
                        mvc.perform(
                                get("/gym/mobile/members")
                                        .param("storeId", "101")
                                        .param("keyword", "张")
                                        .param("segment", "card_expiring")
                                        .param("expireWithinDays", "14")
                                        .param("coachId", "11")
                                        .param("pageNo", "2")
                                        .param("pageSize", "20"))));
        verify(queries)
                .page(
                        argThat(
                                r ->
                                        r.getStoreId() == 101L
                                                && r.getCoachId() == 11L
                                                && r.getPageNo() == 2
                                                && r.getPageSize() == 20
                                                && r.getExpireWithinDays() == 14
                                                && "张".equals(r.getKeyword())
                                                && "card_expiring".equals(r.getSegment())));
        assertEquals(0, code(mvc.perform(get("/gym/mobile/members/7").param("storeId", "101"))));
        verify(queries).get(7L, 101L);
        clearInvocations(queries);
        assertEquals(400, code(mvc.perform(get("/gym/mobile/members/7"))));
        for (var entry :
                Map.of(
                                "segment",
                                "other",
                                "storeId",
                                "0",
                                "coachId",
                                "-1",
                                "pageSize",
                                "-1",
                                "expireWithinDays",
                                "366")
                        .entrySet())
            assertEquals(
                    400,
                    code(
                            mvc.perform(
                                    get("/gym/mobile/members")
                                            .param(entry.getKey(), entry.getValue()))));
        verifyNoInteractions(queries);
    }
}
