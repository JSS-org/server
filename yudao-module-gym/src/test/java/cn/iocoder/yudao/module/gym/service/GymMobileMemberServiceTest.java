package cn.iocoder.yudao.module.gym.service;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileIdentityVO.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileRecordPageReqVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymMeasurementDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.GymTrainingPlanDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.store.GymStoreDO;
import cn.iocoder.yudao.module.gym.dal.mysql.member.*;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.mobile.*;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;

import jakarta.annotation.Resource;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Import({
    GymMobileIdentityService.class,
    GymMobileInvitationService.class,
    cn.iocoder.yudao.module.gym.framework.mobile.GymMobileInvitationProperties.class,
    GymMobileIdempotency.class,
    GymMobileRecordsService.class,
    GymMobileEntitlementsService.class,
    GymMobileFilesService.class,
    GymBusinessMutex.class,
    GymBusinessClock.class,
    GymMobileMemberServiceTest.Config.class
})
class GymMobileMemberServiceTest extends BaseDbUnitTest {
    @Resource GymMobileIdentityService identities;
    @Resource GymMobileInvitationService invitations;
    @Resource cn.iocoder.yudao.module.gym.dal.mysql.mobile.GymMobileInvitationMapper invitationRows;

    @Resource
    cn.iocoder.yudao.module.gym.framework.mobile.GymMobileInvitationProperties invitationProperties;

    @Resource GymMobileRecordsService records;
    @Resource GymMobileEntitlementsService entitlements;
    @Resource cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberCardMapper cards;
    @Resource cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberCardStoreMapper cardStores;
    @Resource cn.iocoder.yudao.module.gym.dal.mysql.course.GymCoursePackMapper packs;
    @MockitoBean GymBusinessClock clock;
    static final Instant NOW = Instant.parse("2026-10-23T04:00:00Z");
    @Resource GymMobileFilesService mobileFiles;
    @Resource cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper files;
    @MockitoBean cn.iocoder.yudao.module.infra.api.file.FileApi storage;
    @Resource GymStoreMapper stores;
    @Resource GymMemberMapper members;
    @Resource GymMeasurementMapper measurements;
    @Resource GymTrainingPlanMapper plans;
    @Resource JdbcTemplate jdbc;
    @MockitoBean MemberUserApi users;

    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean
        JdbcTemplate jdbcTemplate(javax.sql.DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        LocalValidatorFactoryBean validator() {
            return new LocalValidatorFactoryBean();
        }

        @Bean
        static BeanPostProcessor tenantInterceptor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (bean instanceof MybatisPlusInterceptor interceptor) {
                        var chain = new ArrayList<>(interceptor.getInterceptors());
                        chain.addFirst(
                                new TenantLineInnerInterceptor(
                                        new TenantDatabaseInterceptor(new TenantProperties())));
                        interceptor.setInterceptors(chain);
                    }
                    return bean;
                }
            };
        }
    }

    @BeforeEach
    void setup() {
        login(1L, 10L, 1);
        invitationProperties.setSharePage("/pages/invitation/index");
        invitationProperties.setValidDays(30);
        when(clock.now()).thenReturn(NOW);
        var user = new MemberUserRespDTO();
        user.setId(10L);
        user.setStatus(0);
        user.setMobile("13800000001");
        user.setNickname("会员甲");
        when(users.getUser(10L)).thenReturn(user);
        var other = new MemberUserRespDTO();
        other.setId(11L);
        other.setStatus(0);
        other.setMobile("13800000002");
        other.setNickname("会员乙");
        when(users.getUser(11L)).thenReturn(other);
        store(101L, "active");
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    void login(Long tenant, Long user, int type) {
        TenantContextHolder.setTenantId(tenant);
        SecurityFrameworkUtils.setLoginUser(
                new LoginUser().setId(user).setUserType(type).setTenantId(tenant),
                new MockHttpServletRequest());
    }

    void store(Long id, String status) {
        var row = new GymStoreDO();
        row.setId(id);
        row.setCode("S" + id);
        row.setName("门店" + id);
        row.setDescription("");
        row.setAreaSqm(new BigDecimal("100"));
        row.setFeatures("[]");
        row.setAddress("测试地址");
        row.setOpens("06:00");
        row.setCloses("23:00");
        row.setStatus(status);
        stores.insert(row);
    }

    Initialize request() {
        var req = new Initialize();
        req.setStoreId(101L);
        req.setSource("miniapp");
        return req;
    }

    Long initialize() {
        return Long.valueOf(identities.initialize(request(), "first-request").memberId());
    }

    GymMeasurementDO measure(Long member, Long store, Instant at) {
        var row = new GymMeasurementDO();
        row.setMemberId(member);
        row.setStoreId(store);
        row.setMeasuredAt(at);
        row.setWeightKg(new BigDecimal("70.00"));
        measurements.insert(row);
        return row;
    }

    GymTrainingPlanDO plan(Long member, Long store, String status) {
        var row = new GymTrainingPlanDO();
        row.setMemberId(member);
        row.setStoreId(store);
        row.setTitle("力量训练");
        row.setStatus(status);
        plans.insert(row);
        return row;
    }

    @Test
    void initializationReplaysExactResultAndDoesNotRecreateOrReassign() {
        assertFalse(identities.context().profileComplete());
        var first = identities.initialize(request(), "first-request");
        assertTrue(first.created());
        assertEquals("prospect", first.customerType());
        assertEquals(first, identities.initialize(request(), "first-request"));
        var req = request();
        req.setStoreId(999L);
        var second = identities.initialize(req, "second-request");
        assertEquals(first.memberId(), second.memberId());
        assertFalse(second.created());
        assertEquals("101", identities.context().defaultStoreId());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM gym_member", Integer.class));
        assertEquals(
                1, jdbc.queryForObject("SELECT COUNT(*) FROM gym_member_store", Integer.class));
    }

    @Test
    void idempotencyRejectsChangedBodyAndInvalidKeysWithoutSideEffects() {
        initialize();
        var req = request();
        req.setSource("different");
        assertServiceException(
                () -> identities.initialize(req, "first-request"), MOBILE_IDEMPOTENCY_CONFLICT);
        assertServiceException(() -> identities.initialize(req, "短键"), MOBILE_REQUEST_INVALID);
        assertEquals(
                1, jdbc.queryForObject("SELECT COUNT(*) FROM gym_mobile_request", Integer.class));
    }

    @Test
    void initializationFailureRollsBackRequestAndCanBeRetried() {
        jdbc.update("UPDATE gym_store SET status='inactive' WHERE id=101");
        assertServiceException(
                () -> identities.initialize(request(), "retry-request"), MEMBER_STORE_INACTIVE);
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM gym_mobile_request", Integer.class));
        jdbc.update("UPDATE gym_store SET status='active' WHERE id=101");
        assertTrue(identities.initialize(request(), "retry-request").created());
    }

    @Test
    void existingCustomerStageAndResponsibleRelationshipsArePreserved() {
        Long member = initialize();
        jdbc.update("UPDATE gym_member SET customer_stage='customer' WHERE id=?", member);
        assertEquals(
                "customer", identities.initialize(request(), "another-request").customerType());
        assertEquals(member, identities.requireMemberId());
        assertEquals("customer", members.selectById(member).getCustomerStage());
    }

    @Test
    void adminTokenUnverifiedPhoneAndDisabledAccountCannotClaimIdentity() {
        login(1L, 10L, 2);
        assertServiceException(this::initialize, MOBILE_IDENTITY_INVALID);
        login(1L, 10L, 1);
        users.getUser(10L).setMobile(null);
        assertServiceException(this::initialize, MOBILE_IDENTITY_INVALID);
        users.getUser(10L).setMobile("13800000001");
        users.getUser(10L).setStatus(1);
        assertServiceException(this::initialize, MOBILE_IDENTITY_INVALID);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM gym_member", Integer.class));
    }

    @Test
    void anotherAccountCannotClaimAlreadyBoundMember() {
        initialize();
        users.getUser(11L).setMobile("13800000001");
        login(1L, 11L, 1);
        assertServiceException(this::initialize, MOBILE_IDENTITY_CONFLICT);
        assertEquals(
                1, jdbc.queryForObject("SELECT COUNT(*) FROM gym_mobile_identity", Integer.class));
    }

    @Test
    void measurementsAndTotalsAreFilteredBeforePaginationIncludingTenantIsolation() {
        Long member = initialize();
        var own = measure(member, 101L, Instant.parse("2026-10-23T01:00:00Z"));
        var other = measure(member + 100, 101L, Instant.parse("2026-10-23T02:00:00Z"));
        login(2L, 10L, 1);
        var crossTenant = measure(member, 101L, Instant.parse("2026-10-23T03:00:00Z"));
        login(1L, 10L, 1);
        var page = records.measurements(new GymMobileRecordPageReqVO());
        assertEquals(1L, page.getTotal());
        assertEquals(own.getId().toString(), page.getList().getFirst().id());
        assertServiceException(() -> records.measurement(other.getId()), MEASUREMENT_NOT_EXISTS);
        assertServiceException(
                () -> records.measurement(crossTenant.getId()), MEASUREMENT_NOT_EXISTS);
        assertEquals("2026-10-23T09:00+08:00", page.getList().getFirst().measuredAt());
        // The token cannot claim another tenant via request context.
        TenantContextHolder.setTenantId(2L);
        assertServiceException(identities::requireMemberId, MOBILE_IDENTITY_INVALID);
    }

    @Test
    void historicalRecordsSurviveStoreAndCoachChanges() {
        Long member = initialize();
        var archived = plan(member, 102L, "archived");
        measure(member, 102L, Instant.parse("2025-01-01T00:00:00Z"));
        jdbc.update("UPDATE gym_store SET status='inactive' WHERE id=101");
        assertEquals(archived.getId().toString(), records.plan(archived.getId()).id());
        var req = new GymMobileRecordPageReqVO();
        req.setStoreId(102L);
        req.setStatus("archived");
        assertEquals(1L, records.plans(req).getTotal());
        assertEquals(1L, records.measurements(new GymMobileRecordPageReqVO()).getTotal());
        jdbc.update("UPDATE gym_member SET status='inactive' WHERE id=?", member);
        assertServiceException(() -> records.plan(archived.getId()), MOBILE_IDENTITY_INVALID);
    }

    @Test
    void dateRangeUsesShanghaiAndExclusiveEnd() {
        Long member = initialize();
        measure(member, 101L, Instant.parse("2026-10-22T15:59:59Z"));
        measure(member, 101L, Instant.parse("2026-10-22T16:00:00Z"));
        measure(member, 101L, Instant.parse("2026-10-23T16:00:00Z"));
        var req = new GymMobileRecordPageReqVO();
        req.setStartDate(LocalDate.parse("2026-10-23"));
        req.setEndDate(LocalDate.parse("2026-10-24"));
        assertEquals(1L, records.measurements(req).getTotal());
        req.setEndDate(req.getStartDate());
        assertServiceException(() -> records.measurements(req), BUSINESS_DATE_INVALID);
        var invalid = new GymMobileRecordPageReqVO();
        invalid.setPageSize(201);
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> records.measurements(invalid));
    }

    @Test
    void profileIsMaskedAndRejectsUnownedAvatarAtomically() {
        Long member = initialize();
        assertEquals("138****0001", identities.profile().maskedPhone());
        var patch = new ProfilePatch();
        patch.setNickname("新昵称");
        patch.setAvatarFileId(123L);
        assertServiceException(() -> identities.updateProfile(patch), MOBILE_FILE_NOT_OWNED);
        assertEquals("会员甲", members.selectById(member).getNickname());
        var validPatch = new ProfilePatch();
        validPatch.setNickname("新昵称");
        assertEquals("新昵称", identities.updateProfile(validPatch).nickname());
        var forbidden = new ProfilePatch();
        assertServiceException(
                () -> forbidden.unknown("mobile", "13900000000"), MOBILE_PHONE_IMMUTABLE);
    }

    @Test
    void fitnessSummaryCountsOnlySuccessfulOwnEventsAndActualLessonUnits() {
        Long member = initialize();
        plan(member, 101L, "active");
        plan(member, 102L, "archived");
        plan(member + 1, 101L, "active");
        jdbc.update(
                "INSERT INTO gym_checkin (store_id,member_id,booking_id,entered_at,method,visitor_type,status,tenant_id) VALUES (101,?,1,CURRENT_TIMESTAMP,'manual','member','succeeded',1),(101,?,2,CURRENT_TIMESTAMP,'manual','member','failed',1)",
                member,
                member);
        jdbc.update(
                "INSERT INTO gym_lesson_record (store_id,member_id,coach_id,booking_id,checkin_id,session_units,confirmed_at,method,status,tenant_id) VALUES (101,?,1,1,1,2,CURRENT_TIMESTAMP,'manual','confirmed',1),(101,?,1,2,2,10,CURRENT_TIMESTAMP,'manual','confirmed',1)",
                member,
                member + 1);
        var summary = records.summary(null);
        assertEquals(1, summary.activePlanCount());
        assertEquals(1, summary.totalCheckins());
        assertEquals(2, summary.totalLessons());
        assertNull(summary.latestMeasurement());
        assertEquals(1L, records.checkins(new GymMobileRecordPageReqVO()).getTotal());
    }

    @Test
    void concurrentFirstLoginsCreateOneIdentityAndOneMember() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Callable<Initialization>>();
            for (int i = 0; i < 4; i++) {
                final int n = i;
                tasks.add(
                        () -> {
                            login(1L, 10L, 1);
                            try {
                                return identities.initialize(request(), "parallel-key-" + n);
                            } finally {
                                clear();
                            }
                        });
            }
            var results = executor.invokeAll(tasks);
            var ids = new HashSet<String>();
            int created = 0;
            for (var result : results) {
                var r = result.get(10, TimeUnit.SECONDS);
                ids.add(r.memberId());
                if (r.created()) created++;
            }
            assertEquals(1, ids.size());
            assertEquals(1, created);
        }
        assertEquals(
                1, jdbc.queryForObject("SELECT COUNT(*) FROM gym_mobile_identity", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM gym_member", Integer.class));
    }

    private static final byte[] PNG =
            new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0};

    private GymMobileFilesService.UploadedFile upload(String purpose) {
        when(storage.createFile(
                        org.mockito.ArgumentMatchers.any(byte[].class),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.isNull()))
                .thenReturn("private-storage-reference");
        var req =
                new cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileFileUploadReqVO();
        req.setPurpose(purpose);
        req.setFile(
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "avatar.png", "image/png", PNG));
        return mobileFiles.upload(req);
    }

    @Test
    void memberFileOwnershipDoesNotReuseAdminUserIdAndAvatarCanBeCleared() {
        initialize();
        var file = upload("avatar");
        assertNull(files.selectById(Long.valueOf(file.fileId())).getUploaderId());
        assertFalse(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(file)
                        .contains("private-storage"));
        var patch = new ProfilePatch();
        patch.setAvatarFileId(Long.valueOf(file.fileId()));
        assertEquals(file.fileId(), identities.updateProfile(patch).avatarFileId());
        when(storage.getFileContentByUrl("private-storage-reference")).thenReturn(PNG);
        assertArrayEquals(PNG, mobileFiles.content(Long.valueOf(file.fileId())).getContent());
        patch.setAvatarFileId(null);
        assertNull(identities.updateProfile(patch).avatarFileId());
        login(1L, 11L, 1);
        initialize();
        assertServiceException(
                () -> mobileFiles.content(Long.valueOf(file.fileId())), MOBILE_FILE_NOT_OWNED);
    }

    @Test
    void faceOriginalNeverDownloadsAndCannotBeReusedAsAvatar() {
        initialize();
        var face = upload("face");
        var patch = new ProfilePatch();
        patch.setAvatarFileId(Long.valueOf(face.fileId()));
        assertServiceException(
                () -> mobileFiles.content(Long.valueOf(face.fileId())), MOBILE_FILE_NOT_OWNED);
        assertServiceException(() -> identities.updateProfile(patch), MOBILE_FILE_NOT_OWNED);
        verify(storage, never()).getFileContentByUrl(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void uploadRejectsMismatchedMimeBeforeStorage() {
        initialize();
        var req =
                new cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileFileUploadReqVO();
        req.setPurpose("avatar");
        req.setFile(
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "avatar.jpg", "image/jpeg", PNG));
        assertServiceException(
                () -> mobileFiles.upload(req), FILE_TYPE_NOT_ALLOWED, "文件声明类型与内容不一致");
        verifyNoInteractions(storage);
    }

    @Test
    void bodyReportDownloadRequiresActualOwnedRecordAndMissingBytesAreNotSuccess() {
        Long member = initialize();
        var file =
                cn.iocoder.yudao.module.gym.dal.dataobject.file.GymFileDO.builder()
                        .storeId(101L)
                        .category("body_report")
                        .name("report.pdf")
                        .url("private-report")
                        .mimeType("application/pdf")
                        .size(5L)
                        .build();
        files.insert(file);
        assertServiceException(() -> mobileFiles.content(file.getId()), MOBILE_FILE_NOT_OWNED);
        var measurement = measure(member, 101L, Instant.now());
        measurement.setReportFileId(file.getId());
        measurements.updateById(measurement);
        assertServiceException(() -> mobileFiles.content(file.getId()), FILE_CONTENT_NOT_EXISTS);
        when(storage.getFileContentByUrl("private-report")).thenReturn(new byte[] {1, 2, 3});
        assertArrayEquals(new byte[] {1, 2, 3}, mobileFiles.content(file.getId()).getContent());
    }

    @Test
    void contractsAreOwnOnlyAndDoNotExposeInternalSnapshots() {
        Long member = initialize();
        jdbc.update(
                "INSERT INTO gym_contract (contract_no,name,store_id,member_id,template_id,template_snapshot,business_snapshot,source_type,source_id,unsigned_file_id,status,approval_status,signing_status,due_at,issued_at,tenant_id) VALUES ('own','本人合同',101,?,1,'{\"version\":2}','{\"phone\":\"secret\"}','card',1,99,'unsigned','pending','unconfigured',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1),('other','他人合同',101,?,1,'{\"version\":1}','{}','card',2,100,'signed','approved','signed',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1)",
                member,
                member + 1);
        var result = records.contracts(new GymMobileRecordPageReqVO());
        assertEquals(1L, result.getTotal());
        var contract = result.getList().getFirst();
        assertEquals("2", contract.agreementVersion());
        assertEquals("unsigned", contract.status());
        assertFalse(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(contract)
                        .contains("secret"));
        assertEquals(contract, records.contract(Long.valueOf(contract.id())));
        Long other =
                jdbc.queryForObject(
                        "SELECT id FROM gym_contract WHERE contract_no='other'", Long.class);
        assertServiceException(() -> records.contract(other), CONTRACT_NOT_EXISTS);
    }

    cn.iocoder.yudao.module.gym.dal.dataobject.card.GymMemberCardDO card(
            Long member, String status, String kind) {
        var row = new cn.iocoder.yudao.module.gym.dal.dataobject.card.GymMemberCardDO();
        row.setMemberId(member);
        row.setIssuedStoreId(101L);
        row.setTemplateId(1L);
        row.setCardNo(UUID.randomUUID().toString());
        row.setDisplayPrefix("卡");
        row.setName("本人测试卡");
        row.setKind(kind);
        row.setAccessScope("all");
        row.setStoreIds("[101]");
        row.setStartsAt(NOW.minusSeconds(3600));
        row.setExpiresAt(NOW.plusSeconds(3600));
        row.setRemainingUses("count".equals(kind) ? 10 : null);
        row.setTotalUses("count".equals(kind) ? 10 : null);
        row.setReservedUses(0);
        row.setStatus(status);
        row.setTemplateSnapshot("{}");
        row.setSourceType("test");
        row.setSourceId(UUID.randomUUID().toString());
        cards.insert(row);
        return row;
    }

    void cardStore(Long card, Long store) {
        var row = new cn.iocoder.yudao.module.gym.dal.dataobject.card.GymMemberCardStoreDO();
        row.setCardId(card);
        row.setStoreId(store);
        cardStores.insert(row);
    }

    cn.iocoder.yudao.module.gym.dal.dataobject.course.GymCoursePackDO pack(
            Long member, Long coach) {
        var row = new cn.iocoder.yudao.module.gym.dal.dataobject.course.GymCoursePackDO();
        row.setMemberId(member);
        row.setStoreId(101L);
        row.setCourseProductId(500L);
        row.setAssignedCoachId(coach);
        row.setName("私教课包");
        row.setStartsAt(NOW.minusSeconds(3600));
        row.setExpiresAt(NOW.plusSeconds(3600));
        row.setTotalSessions(20);
        row.setRemainingSessions(10);
        row.setReservedSessions(3);
        row.setStatus("active");
        row.setPriceSnapshot(new BigDecimal("1000.00"));
        row.setSourceType("test");
        row.setSourceId(UUID.randomUUID().toString());
        packs.insert(row);
        return row;
    }

    @Test
    void cardsConstrainOwnershipAndTenantBeforePagination() {
        Long member = initialize();
        var own = card(member, "active", "period");
        var other = card(member + 100, "active", "count");
        login(2L, 10L, 1);
        var crossTenant = card(member, "active", "count");
        login(1L, 10L, 1);
        var req =
                new cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileEntitlementsVO
                        .CardPage();
        req.setPageSize(1);
        var page = entitlements.cards(req);
        assertEquals(1L, page.getTotal());
        assertEquals(own.getId().toString(), page.getList().getFirst().id());
        assertEquals("duration", page.getList().getFirst().type());
        assertNull(page.getList().getFirst().remainingTimes());
        assertServiceException(() -> entitlements.card(other.getId()), CARD_NOT_EXISTS);
        assertServiceException(() -> entitlements.card(crossTenant.getId()), CARD_NOT_EXISTS);
    }

    @Test
    void cardStoreFilterUsesCurrentEntitlementRowsAndRejectsCrossTenantRelations() {
        Long member = initialize();
        var own = card(member, "active", "count");
        cardStore(own.getId(), 101L);
        cardStore(own.getId(), 102L);
        login(2L, 10L, 1);
        cardStore(own.getId(), 103L);
        login(1L, 10L, 1);
        var req =
                new cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileEntitlementsVO
                        .CardPage();
        req.setStoreId(102L);
        var page = entitlements.cards(req);
        assertEquals(1L, page.getTotal());
        assertEquals(List.of("101", "102"), page.getList().getFirst().storeIds());
        req.setStoreId(103L);
        assertEquals(0L, entitlements.cards(req).getTotal());
        jdbc.update("UPDATE gym_store SET status='inactive' WHERE id=101");
        assertEquals("times", entitlements.card(own.getId()).type());
    }

    @Test
    void effectiveCardStatesHaveMatchingSqlTotalsAtTimeBoundaries() {
        Long member = initialize();
        var active = card(member, "active", "period");
        var expired = card(member, "active", "count");
        expired.setExpiresAt(NOW);
        expired.setRemainingUses(0);
        cards.updateById(expired);
        var pending = card(member, "active", "count");
        pending.setStartsAt(NOW.plusSeconds(1));
        pending.setRemainingUses(0);
        cards.updateById(pending);
        var exhausted = card(member, "active", "count");
        exhausted.setRemainingUses(0);
        cards.updateById(exhausted);
        var frozen = card(member, "frozen", "period");
        frozen.setExpiresAt(NOW.minusSeconds(1));
        cards.updateById(frozen);
        var inactive = card(member, "inactive", "period");
        Map<String, Long> expected =
                Map.of(
                        "active",
                        active.getId(),
                        "expired",
                        expired.getId(),
                        "pending",
                        pending.getId(),
                        "exhausted",
                        exhausted.getId(),
                        "frozen",
                        frozen.getId(),
                        "inactive",
                        inactive.getId());
        var req =
                new cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileEntitlementsVO
                        .CardPage();
        req.setPageSize(1);
        expected.forEach(
                (status, id) -> {
                    req.setStatus(status);
                    var page = entitlements.cards(req);
                    assertEquals(1L, page.getTotal(), status);
                    assertEquals(id.toString(), page.getList().getFirst().id(), status);
                    assertEquals(status, entitlements.card(id).status());
                    assertEquals(status, page.getList().getFirst().status());
                });
        assertEquals("active", cards.selectById(expired.getId()).getStatus());
    }

    @Test
    void packsApplyMemberTenantCoachAndStoreFiltersAndExposeReservationBalance() {
        Long member = initialize();
        var own = pack(member, 201L);
        pack(member, 202L);
        pack(member + 100, 201L);
        login(2L, 10L, 1);
        pack(member, 201L);
        login(1L, 10L, 1);
        var req =
                new cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileEntitlementsVO
                        .PackPage();
        req.setCoachId(201L);
        req.setStoreId(101L);
        var page = entitlements.packs(req);
        assertEquals(1L, page.getTotal());
        var result = page.getList().getFirst();
        assertEquals(own.getId().toString(), result.id());
        assertEquals(10, result.remainingLessons());
        assertEquals(3, result.reservedLessons());
        assertEquals(7, result.availableLessons());
        assertEquals("2026-10-23T13:00+08:00", result.validUntil());
        req.setStoreId(102L);
        assertEquals(0L, entitlements.packs(req).getTotal());
    }

    @Test
    void packsDistinguishFutureExpiredExhaustedAndRevokedHistory() {
        Long member = initialize();
        var row = pack(member, 201L);
        var req =
                new cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileEntitlementsVO
                        .PackPage();
        row.setStartsAt(NOW.plusSeconds(1));
        packs.updateById(row);
        assertEquals("pending", entitlements.packs(req).getList().getFirst().status());
        row.setStartsAt(NOW);
        row.setRemainingSessions(0);
        row.setReservedSessions(0);
        packs.updateById(row);
        assertEquals("exhausted", entitlements.packs(req).getList().getFirst().status());
        row.setExpiresAt(NOW);
        packs.updateById(row);
        assertEquals("expired", entitlements.packs(req).getList().getFirst().status());
        row.setStatus("inactive");
        packs.updateById(row);
        assertEquals("inactive", entitlements.packs(req).getList().getFirst().status());
    }

    @Test
    void entitlementFiltersAndPageBoundsRejectInvalidValues() {
        initialize();
        var cards =
                new cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileEntitlementsVO
                        .CardPage();
        cards.setStatus("anything");
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> entitlements.cards(cards));
        cards.setStatus(null);
        cards.setPageSize(-1);
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> entitlements.cards(cards));
        var packs =
                new cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileEntitlementsVO
                        .PackPage();
        packs.setCoachId(0L);
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> entitlements.packs(packs));
        login(1L, 10L, 2);
        assertServiceException(() -> entitlements.cards(cards), MOBILE_IDENTITY_INVALID);
    }

    cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileInvitationVO.Create invitationReq(
            Long store) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileInvitationVO
                        .Create();
        req.setStoreId(store);
        return req;
    }

    @Test
    void invitationCreationPersistsRandomCodeAndReplaysOriginalPathAndExpiry() {
        Long member = initialize();
        var result = invitations.create(invitationReq(101L), "invitation-key-1");
        assertTrue(result.inviteCode().matches("[a-f0-9]{32}"));
        assertEquals(
                "/pages/invitation/index?storeId=101&inviteCode=" + result.inviteCode(),
                result.sharePath());
        assertEquals(
                NOW.plusSeconds(30 * 86400).atZone(GymDateRange.ZONE).toOffsetDateTime().toString(),
                result.expiresAt());
        var row =
                invitationRows.selectOne(
                        cn.iocoder.yudao.module.gym.dal.dataobject.mobile.GymMobileInvitationDO
                                ::getInviteCode,
                        result.inviteCode());
        assertEquals(member, row.getInviterMemberId());
        assertEquals(10L, row.getInviterUserId());
        invitationProperties.setSharePage("/pages/other/join");
        when(clock.now()).thenReturn(NOW.plusSeconds(31 * 86400));
        assertEquals(result, invitations.create(invitationReq(101L), "invitation-key-1"));
        var next = invitations.create(invitationReq(101L), "invitation-key-2");
        assertNotEquals(result.inviteCode(), next.inviteCode());
        assertTrue(next.sharePath().startsWith("/pages/other/join?"));
        assertEquals(2, invitationRows.selectCount().intValue());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM gym_member_card", Integer.class));
    }

    @Test
    void invitationCreationRechecksCurrentAccountStoreAndMembershipBeforeReplay() {
        Long member = initialize();
        var req = invitationReq(101L);
        invitations.create(req, "invitation-key-1");
        users.getUser(10L).setStatus(1);
        assertServiceException(
                () -> invitations.create(req, "invitation-key-1"), MOBILE_IDENTITY_INVALID);
        users.getUser(10L).setStatus(0);
        jdbc.update("UPDATE gym_member SET status='inactive' WHERE id=?", member);
        assertServiceException(
                () -> invitations.create(req, "invitation-key-1"), MOBILE_IDENTITY_INVALID);
        jdbc.update("UPDATE gym_member SET status='active' WHERE id=?", member);
        jdbc.update("UPDATE gym_store SET status='inactive' WHERE id=101");
        assertServiceException(
                () -> invitations.create(req, "invitation-key-1"), MOBILE_INVITATION_UNAVAILABLE);
        jdbc.update("UPDATE gym_store SET status='active' WHERE id=101");
        jdbc.update("DELETE FROM gym_member_store WHERE member_id=?", member);
        assertServiceException(
                () -> invitations.create(req, "invitation-key-1"), MOBILE_INVITATION_UNAVAILABLE);
        assertServiceException(
                () -> invitations.create(invitationReq(999L), "invitation-key-2"),
                MOBILE_INVITATION_UNAVAILABLE);
        login(1L, 10L, 2);
        assertServiceException(
                () -> invitations.create(req, "invitation-key-1"), MOBILE_IDENTITY_INVALID);
        assertEquals(1, invitationRows.selectCount().intValue());
    }

    @Test
    void invitationCreationValidatesKeysParametersAndSafePageConfiguration() {
        Long member = initialize();
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> invitations.create(invitationReq(-1L), "invitation-key-1"));
        assertServiceException(
                () -> invitations.create(invitationReq(101L), "short"), MOBILE_REQUEST_INVALID);
        invitations.create(invitationReq(101L), "invitation-key-1");
        store(102L, "active");
        jdbc.update(
                "INSERT INTO gym_member_store(member_id,store_id,joined_at,tenant_id) VALUES(?,102,?,1)",
                member,
                NOW);
        assertServiceException(
                () -> invitations.create(invitationReq(102L), "invitation-key-1"),
                MOBILE_IDEMPOTENCY_CONFLICT);
        invitationProperties.setSharePage("https://example.com/steal");
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> invitations.create(invitationReq(101L), "invitation-key-2"));
        invitationProperties.setSharePage("/pages/invitation/index");
        invitationProperties.setValidDays(0);
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> invitations.create(invitationReq(101L), "invitation-key-2"));
        invitationProperties.setValidDays(30);
        assertNotNull(invitations.create(invitationReq(101L), "invitation-key-2"));
        assertEquals(2, invitationRows.selectCount().intValue());
    }

    @Test
    void invitationCreatesVerifiedFirstAttributionWithoutRewardsAndDoesNotOverwriteIt() {
        Long inviter = initialize();
        var invite = invitations.create(invitationReq(101L), "invitation-key-1");
        login(1L, 11L, 1);
        var req = request();
        req.setInviteCode(invite.inviteCode());
        var result = identities.initialize(req, "invited-member-1");
        assertTrue(result.created());
        var identity = identities.requireIdentity();
        assertEquals(inviter, identity.getInviterMemberId());
        assertEquals(10L, identity.getInviterUserId());
        assertEquals(invite.inviteCode(), identity.getInviteCode());
        assertEquals(Boolean.TRUE, identity.getInvitationNewMember());
        assertNotNull(identity.getInvitationId());
        assertEquals(result, identities.initialize(req, "invited-member-1"));
        var altered = request();
        altered.setInviteCode("not-a-real-invitation");
        assertFalse(identities.initialize(altered, "invited-member-2").created());
        assertEquals(identity.getInvitationId(), identities.requireIdentity().getInvitationId());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM gym_member", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM gym_member_card", Integer.class));
    }

    @Test
    void invitationExistingCustomerBindingRecordsNonNewMemberWithoutMovingStoreOrStage() {
        Long inviter = initialize();
        var invite = invitations.create(invitationReq(101L), "invitation-key-1");
        var old = new cn.iocoder.yudao.module.gym.dal.dataobject.member.GymMemberDO();
        old.setStoreId(102L);
        old.setMemberNo("old-invited");
        old.setName("老客");
        old.setPhone("13800000002");
        old.setGender("unspecified");
        old.setStatus("active");
        old.setCustomerStage("customer");
        old.setRegisteredAt(NOW.minusSeconds(86400));
        members.insert(old);
        login(1L, 11L, 1);
        var req = request();
        req.setInviteCode(invite.inviteCode());
        var result = identities.initialize(req, "invited-old-member");
        assertFalse(result.created());
        assertEquals("customer", result.customerType());
        var identity = identities.requireIdentity();
        assertEquals(Boolean.FALSE, identity.getInvitationNewMember());
        assertEquals(inviter, identity.getInviterMemberId());
        assertEquals(102L, members.selectById(old.getId()).getStoreId());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM gym_member_store WHERE member_id=?",
                        Integer.class,
                        old.getId()));
    }

    @Test
    void invitationInvalidWrongStoreExpiredDeletedAndForeignTenantCodesLeaveNoNewIdentity() {
        initialize();
        var invite = invitations.create(invitationReq(101L), "invitation-key-1");
        login(1L, 11L, 1);
        var req = request();
        for (String code : List.of("not-a-code", "a".repeat(32))) {
            req.setInviteCode(code);
            assertServiceException(
                    () -> identities.initialize(req, "invalid-invite-code"),
                    MOBILE_INVITATION_INVALID);
        }
        req.setInviteCode(invite.inviteCode());
        store(102L, "active");
        req.setStoreId(102L);
        assertServiceException(
                () -> identities.initialize(req, "invalid-invite-code"), MOBILE_INVITATION_INVALID);
        req.setStoreId(101L);
        when(clock.now()).thenReturn(NOW.plusSeconds(30 * 86400));
        assertServiceException(
                () -> identities.initialize(req, "invalid-invite-code"), MOBILE_INVITATION_INVALID);
        when(clock.now()).thenReturn(NOW);
        jdbc.update("UPDATE gym_mobile_invitation SET deleted=true");
        assertServiceException(
                () -> identities.initialize(req, "invalid-invite-code"), MOBILE_INVITATION_INVALID);
        jdbc.update("UPDATE gym_mobile_invitation SET deleted=false,tenant_id=2");
        assertServiceException(
                () -> identities.initialize(req, "invalid-invite-code"), MOBILE_INVITATION_INVALID);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM gym_member", Integer.class));
        assertEquals(
                1, jdbc.queryForObject("SELECT COUNT(*) FROM gym_mobile_identity", Integer.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM gym_mobile_request WHERE request_key='invalid-invite-code'",
                        Integer.class));
        jdbc.update("UPDATE gym_mobile_invitation SET tenant_id=1");
        assertTrue(identities.initialize(req, "invalid-invite-code").created());
    }

    @Test
    void invitationClaimRejectsDisabledUnboundOrRemovedInviterAndSelfReferral() {
        Long inviter = initialize();
        var invite = invitations.create(invitationReq(101L), "invitation-key-1");
        login(1L, 11L, 1);
        var req = request();
        req.setInviteCode(invite.inviteCode());
        users.getUser(10L).setStatus(1);
        assertServiceException(
                () -> identities.initialize(req, "claim-invitation"), MOBILE_INVITATION_INVALID);
        users.getUser(10L).setStatus(0);
        jdbc.update("UPDATE gym_member SET status='inactive' WHERE id=?", inviter);
        assertServiceException(
                () -> identities.initialize(req, "claim-invitation"), MOBILE_INVITATION_INVALID);
        jdbc.update("UPDATE gym_member SET status='active' WHERE id=?", inviter);
        jdbc.update("UPDATE gym_mobile_identity SET deleted=true WHERE user_id=10");
        assertServiceException(
                () -> identities.initialize(req, "claim-invitation"), MOBILE_INVITATION_INVALID);
        jdbc.update("UPDATE gym_mobile_identity SET deleted=false WHERE user_id=10");
        jdbc.update("UPDATE gym_member_store SET deleted=true WHERE member_id=?", inviter);
        assertServiceException(
                () -> identities.initialize(req, "claim-invitation"), MOBILE_INVITATION_INVALID);
        jdbc.update("UPDATE gym_member_store SET deleted=false WHERE member_id=?", inviter);
        users.getUser(11L).setMobile("13800000001");
        assertServiceException(
                () -> identities.initialize(req, "claim-invitation"), MOBILE_INVITATION_INVALID);
        users.getUser(11L).setMobile("13800000002");
        assertTrue(identities.initialize(req, "claim-invitation").created());
    }

    @Test
    void invitationEmptyAndLegacyFreeTextDoNotBecomeVerifiedAttribution() {
        var req = request();
        req.setInviteCode("  ");
        Long member = Long.valueOf(identities.initialize(req, "empty-invitation").memberId());
        assertNull(identities.requireIdentity().getInvitationId());
        assertNull(identities.requireIdentity().getInviteCode());
        jdbc.update(
                "UPDATE gym_mobile_identity SET invite_code='legacy-free-text' WHERE member_id=?",
                member);
        assertFalse(identities.initialize(request(), "legacy-invitation").created());
        assertEquals("legacy-free-text", identities.requireIdentity().getInviteCode());
        assertNull(identities.requireIdentity().getInviterMemberId());
    }

    @Test
    void invitationConcurrentSameKeyCreatesOneCodeAndSeparateKeysCreateDistinctCodes()
            throws Exception {
        initialize();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Callable<String>>();
            for (int i = 0; i < 4; i++)
                tasks.add(
                        () -> {
                            login(1L, 10L, 1);
                            try {
                                return invitations
                                        .create(invitationReq(101L), "parallel-invite-key")
                                        .inviteCode();
                            } finally {
                                clear();
                            }
                        });
            var codes = new HashSet<String>();
            for (var result : executor.invokeAll(tasks))
                codes.add(result.get(10, TimeUnit.SECONDS));
            assertEquals(1, codes.size());
            tasks.clear();
            for (int i = 0; i < 4; i++) {
                final int n = i;
                tasks.add(
                        () -> {
                            login(1L, 10L, 1);
                            try {
                                return invitations
                                        .create(invitationReq(101L), "parallel-invite-" + n)
                                        .inviteCode();
                            } finally {
                                clear();
                            }
                        });
            }
            codes.clear();
            for (var result : executor.invokeAll(tasks))
                codes.add(result.get(10, TimeUnit.SECONDS));
            assertEquals(4, codes.size());
        }
        assertEquals(5, invitationRows.selectCount().intValue());
    }

    @Test
    void invitationConcurrentFirstClaimsKeepOneAttributionAndOriginalMember() throws Exception {
        Long inviter = initialize();
        var first = invitations.create(invitationReq(101L), "invitation-key-1");
        var second = invitations.create(invitationReq(101L), "invitation-key-2");
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Callable<Initialization>>();
            for (int i = 0; i < 4; i++) {
                final int n = i;
                tasks.add(
                        () -> {
                            login(1L, 11L, 1);
                            try {
                                var req = request();
                                req.setInviteCode(
                                        n % 2 == 0 ? first.inviteCode() : second.inviteCode());
                                return identities.initialize(req, "parallel-claim-" + n);
                            } finally {
                                clear();
                            }
                        });
            }
            int count = 0;
            var ids = new HashSet<String>();
            for (var result : executor.invokeAll(tasks)) {
                var value = result.get(10, TimeUnit.SECONDS);
                ids.add(value.memberId());
                if (value.created()) count++;
            }
            assertEquals(1, count);
            assertEquals(1, ids.size());
        }
        login(1L, 11L, 1);
        var identity = identities.requireIdentity();
        assertEquals(inviter, identity.getInviterMemberId());
        assertTrue(
                Set.of(first.inviteCode(), second.inviteCode()).contains(identity.getInviteCode()));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM gym_member", Integer.class));
    }

    @Test
    void invitationInsertFailureRollsBackIdempotencyAndOriginalKeyCanRetry() {
        initialize();
        jdbc.execute(
                "ALTER TABLE gym_mobile_invitation ADD CONSTRAINT test_invite_failure CHECK (store_id<>101)");
        try {
            assertThrows(
                    org.springframework.dao.DataIntegrityViolationException.class,
                    () -> invitations.create(invitationReq(101L), "failing-invitation"));
            assertEquals(0, invitationRows.selectCount().intValue());
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM gym_mobile_request WHERE request_key='failing-invitation'",
                            Integer.class));
        } finally {
            jdbc.execute("ALTER TABLE gym_mobile_invitation DROP CONSTRAINT test_invite_failure");
        }
        assertNotNull(invitations.create(invitationReq(101L), "failing-invitation"));
    }

    @Test
    void invitationAttributionFailureRollsBackMemberMembershipIdentityAndRequest() {
        initialize();
        var invite = invitations.create(invitationReq(101L), "invitation-key-1");
        login(1L, 11L, 1);
        var req = request();
        req.setInviteCode(invite.inviteCode());
        jdbc.execute(
                "ALTER TABLE gym_mobile_identity ADD CONSTRAINT test_attribution_failure CHECK (invitation_id IS NULL)");
        try {
            assertThrows(
                    org.springframework.dao.DataIntegrityViolationException.class,
                    () -> identities.initialize(req, "failing-attribution"));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM gym_member", Integer.class));
            assertEquals(
                    1, jdbc.queryForObject("SELECT COUNT(*) FROM gym_member_store", Integer.class));
            assertEquals(
                    1,
                    jdbc.queryForObject("SELECT COUNT(*) FROM gym_mobile_identity", Integer.class));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM gym_mobile_request WHERE request_key='failing-attribution'",
                            Integer.class));
        } finally {
            jdbc.execute(
                    "ALTER TABLE gym_mobile_identity DROP CONSTRAINT test_attribution_failure");
        }
        assertTrue(identities.initialize(req, "failing-attribution").created());
    }
}
