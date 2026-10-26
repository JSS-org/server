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
    cn.iocoder.yudao.module.gym.framework.mobile.GymMobileCouponProperties.class,
    cn.iocoder.yudao.module.gym.service.groupbuy.GymCouponCipher.class,
    cn.iocoder.yudao.module.gym.service.groupbuy.GymMemberCouponProvider.class,
    cn.iocoder.yudao.module.gym.service.groupbuy.GymCouponPreviewTransactions.class,
    GymMobileCouponPreviewsService.class,
    cn.iocoder.yudao.module.gym.service.points.GymPointProductsService.class,
    GymDataScope.class,
    GymMobileIdentityService.class,
    GymMobileInvitationService.class,
    GymMobileSubscriptionsService.class,
    GymMobilePointsService.class,
    cn.iocoder.yudao.module.gym.service.points.GymPointLedgerService.class,
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
    @Resource cn.iocoder.yudao.module.gym.service.points.GymPointProductsService pointProducts;
    @Resource cn.iocoder.yudao.module.gym.dal.mysql.card.GymCardTemplateMapper pointTemplates;
    @MockitoBean cn.iocoder.yudao.module.system.api.permission.PermissionApi pointPermissions;
    @Resource GymMobileIdentityService identities;
    @Resource GymMobileInvitationService invitations;
    @Resource GymMobileSubscriptionsService mobileSubscriptions;
    @Resource GymMobilePointsService mobilePoints;
    @Resource cn.iocoder.yudao.module.gym.service.points.GymPointLedgerService pointLedger;
    @Resource org.springframework.transaction.PlatformTransactionManager pointTransactions;
    @Resource cn.iocoder.yudao.module.gym.dal.mysql.sales.GymSubscriptionMapper subscriptionRows;
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
        couponProperties.setEnabled(false);
        couponProperties.setEncryptionKey(null);
        couponProperties.setMappings(new HashMap<>());
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

    cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileSubscriptionsVO.Query
            subscriptionQuery(Long store) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileSubscriptionsVO
                        .Query();
        req.setStoreId(store);
        return req;
    }

    cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymSubscriptionDO subscriptionRow(
            Long member, Long store, String status) {
        var row = new cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymSubscriptionDO();
        row.setMemberId(member);
        row.setStoreId(store);
        row.setMemberName("真实订阅持有人");
        row.setCardTemplateId(700L);
        row.setChannel("miniapp");
        row.setStatus(status);
        row.setAmount(new BigDecimal("119.90"));
        row.setBillingDay(1);
        row.setProvider("private-provider");
        row.setProviderAgreementId(UUID.randomUUID().toString());
        row.setRequestedAt(NOW);
        row.setFailureReason("internal-secret-not-public");
        subscriptionRows.insert(row);
        return row;
    }

    @Test
    void subscriptionQueriesFilterOwnerTenantStoreAndDeletionBeforePagination() {
        Long member = initialize();
        var first = subscriptionRow(member, 101L, "active");
        var second = subscriptionRow(member, 102L, "past_due");
        var third = subscriptionRow(member, 101L, "failed");
        third.setChannel("management");
        subscriptionRows.updateById(third);
        subscriptionRow(member + 100, 101L, "active");
        var deleted = subscriptionRow(member, 101L, "active");
        subscriptionRows.deleteById(deleted.getId());
        login(2L, 10L, 1);
        subscriptionRow(member, 101L, "active");
        login(1L, 10L, 1);
        var req = subscriptionQuery(null);
        req.setPageSize(1);
        var page = mobileSubscriptions.page(req);
        assertEquals(3L, page.getTotal());
        assertEquals(third.getId().toString(), page.getList().getFirst().id());
        req.setPageNo(2);
        assertEquals(
                second.getId().toString(), mobileSubscriptions.page(req).getList().getFirst().id());
        req.setPageNo(3);
        assertEquals(
                first.getId().toString(), mobileSubscriptions.page(req).getList().getFirst().id());
        req.setPageNo(1);
        req.setStoreId(101L);
        assertEquals(2L, mobileSubscriptions.page(req).getTotal());
        req.setStoreId(999L);
        assertEquals(0L, mobileSubscriptions.page(req).getTotal());
        assertServiceException(
                () -> mobileSubscriptions.get(deleted.getId()), SUBSCRIPTION_NOT_EXISTS);
    }

    @Test
    void subscriptionDetailsRequireRealMemberOwnershipAndCorrectTenantAndAccountType() {
        Long member = initialize();
        var own = subscriptionRow(member, 101L, "pending_authorization");
        var other = subscriptionRow(member + 10, 101L, "active");
        login(2L, 10L, 1);
        var foreign = subscriptionRow(member, 101L, "active");
        login(1L, 10L, 1);
        assertEquals(own.getId().toString(), mobileSubscriptions.get(own.getId()).id());
        assertServiceException(
                () -> mobileSubscriptions.get(other.getId()), SUBSCRIPTION_NOT_EXISTS);
        assertServiceException(
                () -> mobileSubscriptions.get(foreign.getId()), SUBSCRIPTION_NOT_EXISTS);
        assertServiceException(
                () -> mobileSubscriptions.get(Long.MAX_VALUE), SUBSCRIPTION_NOT_EXISTS);
        login(1L, 10L, 2);
        assertServiceException(() -> mobileSubscriptions.get(own.getId()), MOBILE_IDENTITY_INVALID);
        login(1L, 10L, 1);
        TenantContextHolder.setTenantId(2L);
        assertServiceException(() -> mobileSubscriptions.get(own.getId()), MOBILE_IDENTITY_INVALID);
    }

    @Test
    void subscriptionStatusMappingPreservesUnconfiguredFailureAndUnknownWithoutInventedSuccess() {
        Long member = initialize();
        var mapping =
                Map.ofEntries(
                        Map.entry("pending", "pending_sign"),
                        Map.entry("pending_authorization", "pending_sign"),
                        Map.entry("pending_sign", "pending_sign"),
                        Map.entry("active", "active"),
                        Map.entry("past_due", "past_due"),
                        Map.entry("terminating", "terminating"),
                        Map.entry("terminated", "terminated"),
                        Map.entry("cancelled", "terminated"),
                        Map.entry("unconfigured", "unconfigured"),
                        Map.entry("failed", "failed"),
                        Map.entry("vendor-private-state", "unknown"));
        for (var entry : mapping.entrySet()) {
            var row = subscriptionRow(member, 101L, entry.getKey());
            var result = mobileSubscriptions.get(row.getId());
            assertEquals(entry.getValue(), result.status());
            assertNull(result.agreementVersion());
            assertNull(result.nextChargeAt());
            assertNull(result.terminatedAt());
            assertEquals(entry.getKey(), subscriptionRows.selectById(row.getId()).getStatus());
        }
        var page = mobileSubscriptions.page(subscriptionQuery(null));
        assertEquals(mapping.size(), page.getTotal().intValue());
        for (var result : page.getList())
            assertEquals(result, mobileSubscriptions.get(Long.valueOf(result.id())));
    }

    @Test
    void subscriptionReadsAgreementVersionAmountAndProductFromStoredAgreementOnly() {
        Long member = initialize();
        var row = subscriptionRow(member, 101L, "active");
        row.setAgreementVersion("accepted-v3");
        row.setAmount(new BigDecimal("1234567890123456.78"));
        row.setNextChargeAt(Instant.parse("2026-11-01T16:00:00.123456Z"));
        subscriptionRows.updateById(row);
        var result = mobileSubscriptions.get(row.getId());
        assertEquals("accepted-v3", result.agreementVersion());
        assertEquals("1234567890123456.78", result.cycleAmount());
        assertEquals("700", result.productId());
        assertEquals("2026-11-02T00:00:00.123456+08:00", result.nextChargeAt());
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM gym_card_template", Integer.class));
        // Missing current template/settings must not erase the historical agreement or invent a
        // version.
        var legacy = subscriptionRow(member, 101L, "unconfigured");
        assertNull(mobileSubscriptions.get(legacy.getId()).agreementVersion());
        assertEquals("119.90", mobileSubscriptions.get(legacy.getId()).cycleAmount());
    }

    @Test
    void subscriptionHistoryRemainsReadableAfterStoreMembershipOrCatalogRemoval() {
        Long member = initialize();
        var row = subscriptionRow(member, 101L, "cancelled");
        row.setCancelledAt(NOW);
        subscriptionRows.updateById(row);
        jdbc.update("DELETE FROM gym_member_store WHERE member_id=?", member);
        jdbc.update("UPDATE gym_store SET status='inactive' WHERE id=101");
        var result = mobileSubscriptions.get(row.getId());
        assertEquals("terminated", result.status());
        assertEquals(
                NOW.atZone(GymDateRange.ZONE).toOffsetDateTime().toString(), result.terminatedAt());
        jdbc.update("UPDATE gym_store SET deleted=true WHERE id=101");
        assertEquals(result, mobileSubscriptions.get(row.getId()));
        assertEquals(1L, mobileSubscriptions.page(subscriptionQuery(101L)).getTotal());
    }

    @Test
    void subscriptionReadsRequireInitializedActiveIdentityOnEveryRequest() {
        assertServiceException(
                () -> mobileSubscriptions.page(subscriptionQuery(null)), MOBILE_IDENTITY_REQUIRED);
        Long member = initialize();
        var row = subscriptionRow(member, 101L, "active");
        assertNotNull(mobileSubscriptions.get(row.getId()));
        users.getUser(10L).setStatus(1);
        assertServiceException(
                () -> mobileSubscriptions.page(subscriptionQuery(null)), MOBILE_IDENTITY_INVALID);
        users.getUser(10L).setStatus(0);
        jdbc.update("UPDATE gym_member SET status='inactive' WHERE id=?", member);
        assertServiceException(() -> mobileSubscriptions.get(row.getId()), MOBILE_IDENTITY_INVALID);
        jdbc.update("UPDATE gym_member SET status='active' WHERE id=?", member);
        jdbc.update("UPDATE gym_mobile_identity SET deleted=true WHERE member_id=?", member);
        assertServiceException(
                () -> mobileSubscriptions.get(row.getId()), MOBILE_IDENTITY_REQUIRED);
    }

    @Test
    void subscriptionQueryValidatesPageLimitsStoreAndIdsInService() {
        initialize();
        var req = subscriptionQuery(-1L);
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> mobileSubscriptions.page(req));
        req.setStoreId(null);
        req.setPageSize(201);
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> mobileSubscriptions.page(req));
        req.setPageSize(10);
        req.setPageNo(0);
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> mobileSubscriptions.page(req));
        assertServiceException(() -> mobileSubscriptions.get(null), MOBILE_REQUEST_INVALID);
        assertServiceException(() -> mobileSubscriptions.get(0L), MOBILE_REQUEST_INVALID);
        assertServiceException(() -> mobileSubscriptions.get(-1L), MOBILE_REQUEST_INVALID);
    }

    @Test
    void subscriptionHistoryQueriesDoNotTriggerChargesTerminationContractsOrRights() {
        Long member = initialize();
        var row = subscriptionRow(member, 101L, "active");
        row.setNextChargeAt(NOW.minusSeconds(86400));
        subscriptionRows.updateById(row);
        var before = jdbc.queryForMap("SELECT * FROM gym_subscription WHERE id=?", row.getId());
        var requestCount =
                jdbc.queryForObject("SELECT COUNT(*) FROM gym_mobile_request", Integer.class);
        for (int i = 0; i < 3; i++) {
            assertEquals("active", mobileSubscriptions.get(row.getId()).status());
            assertEquals(1L, mobileSubscriptions.page(subscriptionQuery(null)).getTotal());
        }
        assertEquals(
                before, jdbc.queryForMap("SELECT * FROM gym_subscription WHERE id=?", row.getId()));
        assertEquals(
                requestCount,
                jdbc.queryForObject("SELECT COUNT(*) FROM gym_mobile_request", Integer.class));
        for (String table :
                List.of(
                        "gym_subscription_cycle",
                        "gym_member_card",
                        "gym_contract",
                        "gym_business_order",
                        "gym_order_payment"))
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
    }

    cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointsVO.Query pointQuery(
            String type) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointsVO.Query();
        req.setType(type);
        return req;
    }

    cn.iocoder.yudao.module.gym.service.points.GymPointLedgerService.Posted postPoints(
            Long member, String key, String type, long delta, Long original) {
        return postPoints(member, key, type, delta, "流水说明", original);
    }

    cn.iocoder.yudao.module.gym.service.points.GymPointLedgerService.Posted postPoints(
            Long member, String key, String type, long delta, String reason, Long original) {
        var tx = new org.springframework.transaction.support.TransactionTemplate(pointTransactions);
        tx.setIsolationLevel(
                org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx.execute(
                status ->
                        pointLedger.append(
                                new cn.iocoder.yudao.module.gym.service.points.GymPointLedgerService
                                        .Posting(member, key, type, delta, reason, original)));
    }

    @Test
    void pointsNewMemberReadsActualEmptyStateWithoutCreatingAccountOrRewards() {
        initialize();
        var before = jdbc.queryForObject("SELECT COUNT(*) FROM gym_mobile_request", Integer.class);
        assertEquals(
                new cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointsVO.Balance(
                        0, 0, 0),
                mobilePoints.balance());
        assertEquals(0L, mobilePoints.records(pointQuery(null)).getTotal());
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM gym_point_account", Integer.class));
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM gym_point_record", Integer.class));
        assertEquals(
                before,
                jdbc.queryForObject("SELECT COUNT(*) FROM gym_mobile_request", Integer.class));
    }

    @Test
    void pointsLedgerStoresRunningBalancesAndGrossIncomeExpenseIncludingReversals() {
        Long member = initialize();
        var earn = postPoints(member, "test:earn:1", "earn", 300, null);
        var spend = postPoints(member, "test:spend:1", "spend", -120, null);
        postPoints(member, "test:refund:1", "refund", 20, spend.id());
        var last = postPoints(member, "test:refund:2", "refund", -50, earn.id());
        assertEquals(150, last.balanceAfter());
        assertEquals(
                new cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointsVO.Balance(
                        150, 320, 170),
                mobilePoints.balance());
        var page = mobilePoints.records(pointQuery(null));
        assertEquals(4L, page.getTotal());
        assertEquals(
                List.of(150L, 200L, 180L, 300L),
                page.getList().stream().map(r -> r.balanceAfter()).toList());
        assertEquals(
                List.of(-50L, 20L, -120L, 300L),
                page.getList().stream().map(r -> r.delta()).toList());
        assertEquals("2026-10-23T12:00+08:00", page.getList().getFirst().createdAt());
        assertEquals(2L, mobilePoints.records(pointQuery("refund")).getTotal());
    }

    @Test
    void pointsSameBusinessEventReplaysFirstResultAndRejectsChangedContent() {
        Long member = initialize();
        var first = postPoints(member, "test:event:1", "earn", 100, "  初始积分  ", null);
        postPoints(member, "test:event:2", "earn", 50, null);
        assertEquals(first, postPoints(member, "test:event:1", "earn", 100, "初始积分", null));
        assertEquals(100, first.balanceAfter());
        assertEquals(150, mobilePoints.balance().balance());
        assertServiceException(
                () -> postPoints(member, "test:event:1", "earn", 101, "初始积分", null),
                POINT_EVENT_CONFLICT);
        assertServiceException(
                () -> postPoints(member, "test:event:1", "earn", 100, "其他说明", null),
                POINT_EVENT_CONFLICT);
        login(1L, 11L, 1);
        Long other = initialize();
        assertServiceException(
                () -> postPoints(other, "test:event:1", "earn", 100, "初始积分", null),
                POINT_EVENT_CONFLICT);
        assertEquals(
                2, jdbc.queryForObject("SELECT COUNT(*) FROM gym_point_record", Integer.class));
    }

    @Test
    void pointsPostingRequiresExistingTransactionAndRejectsInvalidShapes() {
        Long member = initialize();
        var req =
                new cn.iocoder.yudao.module.gym.service.points.GymPointLedgerService.Posting(
                        member, "test:mandatory", "earn", 1, "说明", null);
        assertThrows(
                org.springframework.transaction.IllegalTransactionStateException.class,
                () -> pointLedger.append(req));
        assertServiceException(
                () -> postPoints(member, "test:zero", "earn", 0, null), POINT_ENTRY_INVALID);
        assertServiceException(
                () -> postPoints(member, "test:earn", "earn", -1, null), POINT_ENTRY_INVALID);
        assertServiceException(
                () -> postPoints(member, "test:spend", "spend", 1, null), POINT_ENTRY_INVALID);
        assertServiceException(
                () -> postPoints(member, "test:refund", "refund", 1, null), POINT_ENTRY_INVALID);
        assertServiceException(
                () -> postPoints(member, "test:original", "earn", 1, 99L), POINT_ENTRY_INVALID);
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> postPoints(member, "test invalid", "earn", 1, null));
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> postPoints(member, "test:type", "other", 1, null));
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> postPoints(member, "test:reason", "earn", 1, " ", null));
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> postPoints(member, "test:long", "earn", 1, "x".repeat(201), null));
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> postPoints(member, "test:overflow", "earn", Long.MAX_VALUE, null));
        assertServiceException(
                () -> postPoints(Long.MAX_VALUE, "test:member", "earn", 1, null),
                POINT_ENTRY_INVALID);
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM gym_point_account", Integer.class));
    }

    @Test
    void pointsInsufficientSpendRollsBackAccountCreationAndCanRetrySameEvent() {
        Long member = initialize();
        assertServiceException(
                () -> postPoints(member, "test:spend", "spend", -10, null),
                POINT_BALANCE_INSUFFICIENT);
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM gym_point_account", Integer.class));
        postPoints(member, "test:earn", "earn", 10, null);
        assertEquals(0, postPoints(member, "test:spend", "spend", -10, null).balanceAfter());
        assertEquals(
                2, jdbc.queryForObject("SELECT COUNT(*) FROM gym_point_record", Integer.class));
    }

    @Test
    void pointsReversalsMustMatchOriginalOwnerTypeDirectionAndRemainingAmount() {
        Long member = initialize();
        var earn = postPoints(member, "test:earn", "earn", 100, null);
        var spend = postPoints(member, "test:spend", "spend", -80, null);
        var partial = postPoints(member, "test:refund:1", "refund", 30, spend.id());
        assertServiceException(
                () -> postPoints(member, "test:refund:invalid", "refund", 51, spend.id()),
                POINT_ENTRY_INVALID);
        assertServiceException(
                () -> postPoints(member, "test:refund:invalid", "refund", 1, earn.id()),
                POINT_ENTRY_INVALID);
        assertServiceException(
                () -> postPoints(member, "test:refund:invalid", "refund", -1, spend.id()),
                POINT_ENTRY_INVALID);
        assertServiceException(
                () -> postPoints(member, "test:refund:invalid", "refund", -1, partial.id()),
                POINT_ENTRY_INVALID);
        assertServiceException(
                () -> postPoints(member, "test:refund:invalid", "refund", 1, Long.MAX_VALUE),
                POINT_ENTRY_INVALID);
        login(1L, 11L, 1);
        Long other = initialize();
        assertServiceException(
                () -> postPoints(other, "test:refund:invalid", "refund", 1, spend.id()),
                POINT_ENTRY_INVALID);
        login(1L, 10L, 1);
        postPoints(member, "test:refund:2", "refund", 50, spend.id());
        assertServiceException(
                () -> postPoints(member, "test:refund:invalid", "refund", 1, spend.id()),
                POINT_ENTRY_INVALID);
        assertEquals(100, mobilePoints.balance().balance());
    }

    @Test
    void pointsEarnReversalCanRecordDebtWithoutInventingSpendableBalance() {
        Long member = initialize();
        var earn = postPoints(member, "test:earn", "earn", 100, null);
        postPoints(member, "test:spend", "spend", -80, null);
        assertEquals(
                -80, postPoints(member, "test:reversal", "refund", -100, earn.id()).balanceAfter());
        assertEquals(-80, mobilePoints.balance().balance());
        assertServiceException(
                () -> postPoints(member, "test:spend:debt", "spend", -1, null),
                POINT_BALANCE_INSUFFICIENT);
        postPoints(member, "test:earn:more", "earn", 90, null);
        assertEquals(10, mobilePoints.balance().balance());
    }

    @Test
    void pointsQueriesUseCurrentIdentityAndKeepHistoryWhenStoreRelationsDisappear() {
        assertServiceException(mobilePoints::balance, MOBILE_IDENTITY_REQUIRED);
        Long member = initialize();
        postPoints(member, "test:earn", "earn", 50, null);
        jdbc.update("DELETE FROM gym_member_store WHERE member_id=?", member);
        jdbc.update("UPDATE gym_store SET status='inactive',deleted=true WHERE id=101");
        assertEquals(50, mobilePoints.balance().balance());
        assertEquals(1L, mobilePoints.records(pointQuery(null)).getTotal());
        users.getUser(10L).setStatus(1);
        assertServiceException(mobilePoints::balance, MOBILE_IDENTITY_INVALID);
        users.getUser(10L).setStatus(0);
        jdbc.update("UPDATE gym_member SET status='inactive' WHERE id=?", member);
        assertServiceException(
                () -> mobilePoints.records(pointQuery(null)), MOBILE_IDENTITY_INVALID);
        assertServiceException(
                () -> postPoints(member, "test:inactive:spend", "spend", -1, null),
                POINT_ENTRY_INVALID);
        jdbc.update("UPDATE gym_member SET status='active' WHERE id=?", member);
        login(1L, 10L, 2);
        assertServiceException(mobilePoints::balance, MOBILE_IDENTITY_INVALID);
        login(1L, 10L, 1);
        TenantContextHolder.setTenantId(2L);
        assertServiceException(mobilePoints::balance, MOBILE_IDENTITY_INVALID);
    }

    @Test
    void pointsQueriesFilterOwnerTenantAndTypeBeforePagination() {
        Long member = initialize();
        var first = postPoints(member, "test:earn:1", "earn", 100, null);
        var second = postPoints(member, "test:spend", "spend", -10, null);
        var third = postPoints(member, "test:earn:2", "earn", 20, null);
        login(1L, 11L, 1);
        Long other = initialize();
        postPoints(other, "test:other", "earn", 999, null);
        login(2L, 10L, 1);
        // Primary keys are globally unique; use the existing owner with a different tenant only for
        // the fixture.
        jdbc.update(
                "INSERT INTO gym_point_account(member_id,balance,total_earned,total_spent,tenant_id) VALUES(?,9999,9999,0,2)",
                member);
        jdbc.update(
                "INSERT INTO gym_point_record(account_id,member_id,business_key,type,delta,balance_after,reason,posted_at,tenant_id) VALUES((SELECT id FROM gym_point_account WHERE tenant_id=2 AND member_id=?),?,'test:foreign','earn',9999,9999,'private',?,2)",
                member,
                member,
                NOW);
        login(1L, 10L, 1);
        var req = pointQuery(null);
        req.setPageSize(1);
        assertEquals(3L, mobilePoints.records(req).getTotal());
        assertEquals(third.id().toString(), mobilePoints.records(req).getList().getFirst().id());
        req.setPageNo(2);
        assertEquals(second.id().toString(), mobilePoints.records(req).getList().getFirst().id());
        req.setType("earn");
        assertEquals(2L, mobilePoints.records(req).getTotal());
        assertEquals(first.id().toString(), mobilePoints.records(req).getList().getFirst().id());
        assertEquals(110, mobilePoints.balance().balance());
        req.setPageNo(1);
        req.setType("refund");
        assertEquals(0L, mobilePoints.records(req).getTotal());
    }

    @Test
    void pointsQueryValidationAndHistoryReadsNeverMutateTheLedger() {
        Long member = initialize();
        postPoints(member, "test:earn", "earn", 100, null);
        var account = jdbc.queryForMap("SELECT * FROM gym_point_account");
        var record = jdbc.queryForMap("SELECT * FROM gym_point_record");
        for (int i = 0; i < 3; i++) {
            mobilePoints.balance();
            mobilePoints.records(pointQuery(null));
        }
        assertEquals(account, jdbc.queryForMap("SELECT * FROM gym_point_account"));
        assertEquals(record, jdbc.queryForMap("SELECT * FROM gym_point_record"));
        var req = pointQuery("unknown");
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> mobilePoints.records(req));
        req.setType(null);
        req.setPageSize(201);
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> mobilePoints.records(req));
        req.setPageSize(10);
        req.setPageNo(0);
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> mobilePoints.records(req));
    }

    @Test
    void pointsConcurrentSameEventCreditsOnceWhileDistinctEventsRemainSeparate() throws Exception {
        Long member = initialize();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Callable<Long>>();
            for (int i = 0; i < 4; i++)
                tasks.add(
                        () -> {
                            login(1L, 10L, 1);
                            try {
                                return postPoints(member, "test:same", "earn", 100, null).id();
                            } finally {
                                clear();
                            }
                        });
            var ids = new HashSet<Long>();
            for (var result : executor.invokeAll(tasks)) ids.add(result.get(10, TimeUnit.SECONDS));
            assertEquals(1, ids.size());
            tasks.clear();
            for (int i = 0; i < 4; i++) {
                final int n = i;
                tasks.add(
                        () -> {
                            login(1L, 10L, 1);
                            try {
                                return postPoints(member, "test:distinct:" + n, "earn", 10, null)
                                        .id();
                            } finally {
                                clear();
                            }
                        });
            }
            ids.clear();
            for (var result : executor.invokeAll(tasks)) ids.add(result.get(10, TimeUnit.SECONDS));
            assertEquals(4, ids.size());
        }
        assertEquals(140, mobilePoints.balance().balance());
        assertEquals(5L, mobilePoints.records(pointQuery(null)).getTotal());
    }

    @Test
    void pointsConcurrentSpendsNeverOverdrawAndFailuresLeaveNoEvents() throws Exception {
        Long member = initialize();
        postPoints(member, "test:earn", "earn", 100, null);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 4; i++) {
                final int n = i;
                tasks.add(
                        () -> {
                            login(1L, 10L, 1);
                            try {
                                try {
                                    postPoints(member, "test:spend:" + n, "spend", -40, null);
                                    return true;
                                } catch (
                                        cn.iocoder.yudao.framework.common.exception.ServiceException
                                                e) {
                                    assertEquals(POINT_BALANCE_INSUFFICIENT.getCode(), e.getCode());
                                    return false;
                                }
                            } finally {
                                clear();
                            }
                        });
            }
            int succeeded = 0;
            for (var result : executor.invokeAll(tasks))
                if (result.get(10, TimeUnit.SECONDS)) succeeded++;
            assertEquals(2, succeeded);
        }
        assertEquals(20, mobilePoints.balance().balance());
        assertEquals(3L, mobilePoints.records(pointQuery(null)).getTotal());
    }

    @Test
    void pointsConcurrentRefundsCannotReturnMoreThanOriginalSpend() throws Exception {
        Long member = initialize();
        postPoints(member, "test:earn", "earn", 100, null);
        var spend = postPoints(member, "test:spend", "spend", -100, null);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 3; i++) {
                final int n = i;
                tasks.add(
                        () -> {
                            login(1L, 10L, 1);
                            try {
                                try {
                                    postPoints(
                                            member, "test:refund:" + n, "refund", 60, spend.id());
                                    return true;
                                } catch (
                                        cn.iocoder.yudao.framework.common.exception.ServiceException
                                                e) {
                                    assertEquals(POINT_ENTRY_INVALID.getCode(), e.getCode());
                                    return false;
                                }
                            } finally {
                                clear();
                            }
                        });
            }
            int succeeded = 0;
            for (var result : executor.invokeAll(tasks))
                if (result.get(10, TimeUnit.SECONDS)) succeeded++;
            assertEquals(1, succeeded);
        }
        assertEquals(60, mobilePoints.balance().balance());
        assertEquals(1L, mobilePoints.records(pointQuery("refund")).getTotal());
    }

    @Test
    void pointsRecordAndAccountFailuresRollbackTogetherAndOriginalEventCanRetry() {
        Long member = initialize();
        jdbc.execute(
                "ALTER TABLE gym_point_record ADD CONSTRAINT test_point_record_failure CHECK (delta<>10)");
        try {
            assertThrows(
                    org.springframework.dao.DataIntegrityViolationException.class,
                    () -> postPoints(member, "test:failure", "earn", 10, null));
            assertEquals(
                    0,
                    jdbc.queryForObject("SELECT COUNT(*) FROM gym_point_account", Integer.class));
        } finally {
            jdbc.execute("ALTER TABLE gym_point_record DROP CONSTRAINT test_point_record_failure");
        }
        assertEquals(10, postPoints(member, "test:failure", "earn", 10, null).balanceAfter());
        jdbc.execute(
                "ALTER TABLE gym_point_account ADD CONSTRAINT test_point_account_failure CHECK (balance<=10)");
        try {
            assertThrows(
                    org.springframework.dao.DataIntegrityViolationException.class,
                    () -> postPoints(member, "test:failure:2", "earn", 5, null));
            assertEquals(1L, mobilePoints.records(pointQuery(null)).getTotal());
            assertEquals(10, mobilePoints.balance().balance());
        } finally {
            jdbc.execute(
                    "ALTER TABLE gym_point_account DROP CONSTRAINT test_point_account_failure");
        }
        assertEquals(15, postPoints(member, "test:failure:2", "earn", 5, null).balanceAfter());
    }

    @Test
    void pointsCallerBusinessRollbackAlsoRollsBackLedgerAndAccount() {
        Long member = initialize();
        var tx = new org.springframework.transaction.support.TransactionTemplate(pointTransactions);
        assertThrows(
                IllegalStateException.class,
                () ->
                        tx.execute(
                                status -> {
                                    pointLedger.append(
                                            new cn.iocoder.yudao.module.gym.service.points
                                                    .GymPointLedgerService.Posting(
                                                    member,
                                                    "test:rollback",
                                                    "earn",
                                                    100,
                                                    "说明",
                                                    null));
                                    throw new IllegalStateException("later business failure");
                                }));
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM gym_point_account", Integer.class));
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM gym_point_record", Integer.class));
        assertEquals(
                100, postPoints(member, "test:rollback", "earn", 100, "说明", null).balanceAfter());
    }

    @Test
    void pointsNumericBoundsPreserveExactClientIntegersAndRejectOverflowAtomically() {
        Long member = initialize();
        long max = cn.iocoder.yudao.module.gym.service.points.GymPointLedgerService.MAX_POINTS;
        postPoints(member, "test:max", "earn", max, null);
        assertEquals(max, mobilePoints.balance().balance());
        assertServiceException(
                () -> postPoints(member, "test:too-much", "earn", 1, null), POINT_ENTRY_INVALID);
        assertEquals(max, mobilePoints.balance().totalEarned());
        assertEquals(1L, mobilePoints.records(pointQuery(null)).getTotal());
    }

    void pointAdmin() {
        login(1L, 10L, 2);
        var scope =
                new cn.iocoder.yudao.framework.common.biz.system.permission.dto
                        .DeptDataPermissionRespDTO();
        scope.setDeptIds(Set.of(101L));
        when(pointPermissions.getDeptDataPermission(10L)).thenReturn(scope);
    }

    Long pointTemplate(String scope, String ids, String status) {
        var t = new cn.iocoder.yudao.module.gym.dal.dataobject.card.GymCardTemplateDO();
        t.setName("真实次卡");
        t.setKind("count");
        t.setIncludedUses(10);
        t.setPrice(new BigDecimal("100"));
        t.setAccessScope(scope);
        t.setStoreIds(ids);
        t.setStatus(status);
        t.setVersion(1);
        pointTemplates.insert(t);
        t.setFamilyId(t.getId());
        pointTemplates.updateById(t);
        return t.getId();
    }

    cn.iocoder.yudao.module.gym.controller.admin.points.vo.GymPointProductVO.Save pointProduct(
            Long card) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.points.vo.GymPointProductVO.Save();
        req.setStoreId(101L);
        req.setCardProductId(card);
        req.setName(" 次卡兑换 ");
        req.setRequiredPoints(100L);
        req.setEnabled(true);
        req.setStock(5);
        return req;
    }

    cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointProductsVO.Query
            productQuery(Long store) {
        var q =
                new cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointProductsVO
                        .Query();
        q.setStoreId(store);
        return q;
    }

    @Test
    void pointProductsConfiguredCatalogHasRealFieldsAndNoAccountingSideEffects() {
        initialize();
        pointAdmin();
        var req = pointProduct(pointTemplate("all", "[]", "published"));
        var configured = pointProducts.save(null, req);
        assertEquals(1, configured.revision());
        assertEquals("次卡兑换", configured.name());
        assertEquals(1L, pointProducts.manage(productQuery(101L)).getTotal());
        login(1L, 10L, 1);
        var result = pointProducts.catalog(productQuery(101L));
        assertEquals(1L, result.getTotal());
        var product = result.getList().getFirst();
        assertEquals(configured.id(), product.id());
        assertEquals(req.getCardProductId().toString(), product.cardProductId());
        assertEquals(100, product.requiredPoints());
        assertEquals(5, product.stock());
        pointProducts.catalog(productQuery(101L));
        assertEquals(5, jdbc.queryForObject("SELECT stock FROM gym_point_product", Integer.class));
        for (var table : List.of("gym_point_record", "gym_point_account", "gym_member_card"))
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
    }

    @Test
    void pointProductsUpdatesRequireCurrentRevisionAndPreserveExplicitUnlimitedStock() {
        pointAdmin();
        var req = pointProduct(pointTemplate("all", "[]", "published"));
        Long id = Long.valueOf(pointProducts.save(null, req).id());
        assertServiceException(() -> pointProducts.save(id, req), POINT_PRODUCT_INVALID);
        req.setRevision(1L);
        assertEquals(1, pointProducts.save(id, req).revision());
        req.setStock(null);
        req.setRequiredPoints(9007199254740991L);
        var result = pointProducts.save(id, req);
        assertEquals(2, result.revision());
        assertNull(result.stock());
        assertNull(jdbc.queryForObject("SELECT stock FROM gym_point_product", Integer.class));
        req.setStock(20);
        assertServiceException(() -> pointProducts.save(id, req), POINT_PRODUCT_CONFLICT);
        assertEquals(
                9007199254740991L,
                pointProducts.manage(productQuery(101L)).getList().getFirst().requiredPoints());
        req.setRevision(2L);
        req.setStock(0);
        assertEquals(3, pointProducts.save(id, req).revision());
    }

    @Test
    void pointProductsCannotMoveStoreOrChangeTemplateAndTenantIdsStayIsolated() {
        pointAdmin();
        var req = pointProduct(pointTemplate("all", "[]", "published"));
        Long id = Long.valueOf(pointProducts.save(null, req).id());
        req.setRevision(1L);
        req.setCardProductId(pointTemplate("all", "[]", "published"));
        assertServiceException(() -> pointProducts.save(id, req), POINT_PRODUCT_INVALID);
        req.setStoreId(102L);
        assertServiceException(() -> pointProducts.save(id, req), STORE_NO_PERMISSION);
        assertServiceException(() -> pointProducts.manage(productQuery(102L)), STORE_NO_PERMISSION);
        login(2L, 10L, 2);
        store(202L, "active");
        var otherScope =
                new cn.iocoder.yudao.framework.common.biz.system.permission.dto
                        .DeptDataPermissionRespDTO();
        otherScope.setDeptIds(Set.of(202L));
        when(pointPermissions.getDeptDataPermission(10L)).thenReturn(otherScope);
        req.setStoreId(202L);
        assertEquals(0L, pointProducts.manage(productQuery(202L)).getTotal());
        assertServiceException(() -> pointProducts.save(id, req), POINT_PRODUCT_NOT_EXISTS);
        req.setRevision(null);
        assertServiceException(() -> pointProducts.save(null, req), POINT_PRODUCT_INVALID);
        TenantContextHolder.setTenantId(1L);
        assertServiceException(() -> pointProducts.manage(productQuery(202L)), STORE_NO_PERMISSION);
    }

    @Test
    void pointProductsMemberCannotManageEvenIfSystemPermissionsWereAccidentallyPresent() {
        initialize();
        var req = pointProduct(pointTemplate("all", "[]", "published"));
        pointAdmin();
        login(1L, 10L, 1);
        assertServiceException(() -> pointProducts.save(null, req), STORE_NO_PERMISSION);
        assertServiceException(() -> pointProducts.manage(productQuery(101L)), STORE_NO_PERMISSION);
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM gym_point_product", Integer.class));
    }

    @Test
    void pointProductsValidateConfigurationAndNeverCreatePartialProduct() {
        pointAdmin();
        var req = pointProduct(pointTemplate("selected", "[102]", "published"));
        assertServiceException(() -> pointProducts.save(null, req), POINT_PRODUCT_INVALID);
        req.setCardProductId(pointTemplate("all", "[]", "draft"));
        assertServiceException(() -> pointProducts.save(null, req), POINT_PRODUCT_INVALID);
        req.setCardProductId(99999L);
        assertServiceException(() -> pointProducts.save(null, req), POINT_PRODUCT_INVALID);
        req.setCardProductId(pointTemplate("all", "[]", "published"));
        req.setStock(-1);
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> pointProducts.save(null, req));
        req.setStock(5);
        req.setRequiredPoints(0L);
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> pointProducts.save(null, req));
        req.setRequiredPoints(9007199254740992L);
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> pointProducts.save(null, req));
        req.setRequiredPoints(1L);
        req.setName(" ");
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> pointProducts.save(null, req));
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM gym_point_product", Integer.class));
    }

    @Test
    void pointProductsCatalogFiltersBeforePaginationAndIncludesOnlyEligibleTemplates() {
        initialize();
        pointAdmin();
        Long valid = pointTemplate("selected", "[101]", "published");
        var req = pointProduct(valid);
        req.setStock(0);
        pointProducts.save(null, req);
        req.setStock(5);
        req.setEnabled(false);
        pointProducts.save(null, req);
        req.setEnabled(true);
        var first = pointProducts.save(null, req);
        req.setStock(null);
        var second = pointProducts.save(null, req);
        Long archived = pointTemplate("all", "[]", "published");
        req.setCardProductId(archived);
        pointProducts.save(null, req);
        jdbc.update("UPDATE gym_card_template SET status='archived' WHERE id=?", archived);
        // Corrupt/out-of-band and other-tenant rows must not become visible or alter totals.
        jdbc.update(
                "INSERT INTO gym_point_product(store_id,card_product_id,name,required_points,stock,enabled,tenant_id) VALUES(101,?,'foreign',1,3,TRUE,2)",
                valid);
        Long outside = pointTemplate("selected", "[102]", "published");
        jdbc.update(
                "INSERT INTO gym_point_product(store_id,card_product_id,name,required_points,stock,enabled,tenant_id) VALUES(101,?,'outside',1,3,TRUE,1)",
                outside);
        login(1L, 10L, 1);
        var q = productQuery(101L);
        q.setPageSize(1);
        assertEquals(2L, pointProducts.catalog(q).getTotal());
        assertEquals(first.id(), pointProducts.catalog(q).getList().getFirst().id());
        q.setPageNo(2);
        assertEquals(second.id(), pointProducts.catalog(q).getList().getFirst().id());
        assertNull(pointProducts.catalog(q).getList().getFirst().stock());
        q.setPageNo(3);
        assertTrue(pointProducts.catalog(q).getList().isEmpty());
        assertEquals(2L, pointProducts.catalog(q).getTotal());
        jdbc.update("UPDATE gym_card_template SET deleted=TRUE WHERE id=?", valid);
        assertEquals(0L, pointProducts.catalog(productQuery(101L)).getTotal());
    }

    @Test
    void pointProductsCatalogRequiresCurrentIdentityActiveStoreAndMembership() {
        assertThrows(
                cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> pointProducts.catalog(productQuery(101L)));
        Long member = initialize();
        assertEquals(0L, pointProducts.catalog(productQuery(101L)).getTotal());
        login(1L, 10L, 2);
        assertThrows(
                cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> pointProducts.catalog(productQuery(101L)));
        login(1L, 10L, 1);
        store(102L, "active");
        assertServiceException(
                () -> pointProducts.catalog(productQuery(102L)), POINT_PRODUCT_UNAVAILABLE);
        jdbc.update("UPDATE gym_store SET status='inactive' WHERE id=101");
        assertServiceException(
                () -> pointProducts.catalog(productQuery(101L)), POINT_PRODUCT_UNAVAILABLE);
        jdbc.update("UPDATE gym_store SET status='active' WHERE id=101");
        jdbc.update("UPDATE gym_member_store SET deleted=TRUE WHERE member_id=?", member);
        assertServiceException(
                () -> pointProducts.catalog(productQuery(101L)), POINT_PRODUCT_UNAVAILABLE);
        login(2L, 10L, 1);
        assertThrows(
                cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> pointProducts.catalog(productQuery(101L)));
    }

    @Test
    void pointProductsCanDisableAfterTemplateRemovalWithoutReactivation() {
        initialize();
        pointAdmin();
        var req = pointProduct(pointTemplate("all", "[]", "published"));
        Long id = Long.valueOf(pointProducts.save(null, req).id());
        jdbc.update("UPDATE gym_card_template SET deleted=TRUE WHERE id=?", req.getCardProductId());
        req.setRevision(1L);
        req.setEnabled(false);
        assertEquals(2, pointProducts.save(id, req).revision());
        req.setRevision(2L);
        req.setEnabled(true);
        assertServiceException(() -> pointProducts.save(id, req), POINT_PRODUCT_INVALID);
        assertFalse(pointProducts.manage(productQuery(101L)).getList().getFirst().enabled());
    }

    @Test
    void pointProductsConcurrentConfigEditsCannotOverwriteEachOther() throws Exception {
        pointAdmin();
        Long card = pointTemplate("all", "[]", "published");
        Long id = Long.valueOf(pointProducts.save(null, pointProduct(card)).id());
        try (var executor = Executors.newFixedThreadPool(3)) {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 3; i++) {
                final int n = i;
                tasks.add(
                        () -> {
                            login(1L, 10L, 2);
                            try {
                                var req = pointProduct(card);
                                req.setRevision(1L);
                                req.setStock(10 + n);
                                try {
                                    pointProducts.save(id, req);
                                    return true;
                                } catch (
                                        cn.iocoder.yudao.framework.common.exception.ServiceException
                                                e) {
                                    assertEquals(POINT_PRODUCT_CONFLICT.getCode(), e.getCode());
                                    return false;
                                }
                            } finally {
                                clear();
                            }
                        });
            }
            int successes = 0;
            for (var f : executor.invokeAll(tasks)) if (f.get(10, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
        }
        assertEquals(2, pointProducts.manage(productQuery(101L)).getList().getFirst().revision());
    }

    @Test
    void pointProductsFailedUpdateRollsBackStockPriceAndRevision() {
        pointAdmin();
        var req = pointProduct(pointTemplate("all", "[]", "published"));
        Long id = Long.valueOf(pointProducts.save(null, req).id());
        req.setRevision(1L);
        req.setStock(20);
        req.setRequiredPoints(200L);
        jdbc.execute(
                "ALTER TABLE gym_point_product ADD CONSTRAINT test_point_product_failure CHECK (stock<=10)");
        try {
            assertThrows(
                    org.springframework.dao.DataIntegrityViolationException.class,
                    () -> pointProducts.save(id, req));
        } finally {
            jdbc.execute(
                    "ALTER TABLE gym_point_product DROP CONSTRAINT test_point_product_failure");
        }
        var result = pointProducts.manage(productQuery(101L)).getList().getFirst();
        assertEquals(1, result.revision());
        assertEquals(5, result.stock());
        assertEquals(100, result.requiredPoints());
        assertEquals(2, pointProducts.save(id, req).revision());
    }

    @Resource GymMobileCouponPreviewsService couponPreviews;
    @Resource cn.iocoder.yudao.module.gym.service.groupbuy.GymCouponCipher couponCipher;

    @Resource
    cn.iocoder.yudao.module.gym.framework.mobile.GymMobileCouponProperties couponProperties;

    @Resource cn.iocoder.yudao.module.gym.dal.mysql.groupbuy.GymCouponPreviewMapper couponRows;
    @Resource cn.iocoder.yudao.module.gym.dal.mysql.sales.GymSalesSettingMapper couponSettings;

    @MockitoBean
    cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.GymMeituanRetailClient couponClient;

    cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCouponPreviewVO.Request
            couponReq() {
        var req =
                new cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCouponPreviewVO
                        .Request();
        req.setStoreId(101L);
        req.setPlatform("meituan");
        req.setCouponCode("0012345678901");
        return req;
    }

    <T>
            cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.MeituanResponse<T>
                    couponResponse(T data) {
        var r = new cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.MeituanResponse<T>();
        r.setCode("OP_SUCCESS");
        r.setData(data);
        return r;
    }

    cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail.MeituanRetailResponses.Prepare
            couponFixture() {
        initialize();
        couponProperties.setEnabled(true);
        couponProperties.setEncryptionKey(
                Base64.getEncoder()
                        .encodeToString(
                                "0123456789abcdef0123456789abcdef"
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var mapping =
                new cn.iocoder.yudao.module.gym.framework.mobile.GymMobileCouponProperties
                        .Mapping();
        mapping.setEnabled(true);
        mapping.setDealGroupId(8L);
        mapping.setCardTemplateId(pointTemplate("all", "[]", "published"));
        couponProperties.setMappings(
                new HashMap<>(
                        Map.of(
                                1L,
                                new HashMap<>(Map.of(101L, new HashMap<>(Map.of(7L, mapping)))))));
        couponSetting(true, false, null, null);
        var p =
                new cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail
                        .MeituanRetailResponses.Prepare();
        p.setReceiptCode(couponReq().getCouponCode());
        p.setDealId(7L);
        p.setDealGroupId(8L);
        p.setDealTitle("真实团购次卡");
        p.setMobile("13800000001");
        p.setCount(1);
        p.setBizType(0);
        p.setDealType(0);
        p.setTgTimesCardFlag(false);
        p.setVerificationType(1);
        p.setReceiptEndDate(NOW.plusSeconds(600).toEpochMilli());
        p.setPlatform(1);
        when(couponClient.prepare(eq(1L), eq(101L), any())).thenReturn(couponResponse(p));
        return p;
    }

    void couponSetting(boolean enabled, boolean checkin, String start, String end) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.sales.vo
                        .GymVerificationSettingsReqVO();
        req.setEnabled(enabled);
        req.setRequireCheckin(checkin);
        req.setSupportedChannels(List.of("meituan"));
        req.setAllowedStart(start);
        req.setAllowedEnd(end);
        var rows = couponSettings.selectList();
        var row =
                rows.isEmpty()
                        ? new cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymSalesSettingDO()
                        : rows.getFirst();
        row.setStoreId(101L);
        row.setKind("verification-settings");
        row.setRevision(row.getRevision() == null ? 1L : row.getRevision() + 1);
        row.setContent(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(req));
        if (row.getId() == null) couponSettings.insert(row);
        else couponSettings.updateById(row);
    }

    cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail.MeituanRetailResponses
                    .MobileReceipts
            couponList(String... codes) {
        var r =
                new cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto.retail
                        .MeituanRetailResponses.MobileReceipts();
        r.setResult(
                Arrays.stream(codes)
                        .map(
                                code -> {
                                    var item =
                                            new cn.iocoder.yudao.module.gym.framework.groupbuy
                                                    .meituan.dto.retail.MeituanRetailResponses
                                                    .UserReceipt();
                                    item.setSerialNumber(code);
                                    return item;
                                })
                        .toList());
        return r;
    }

    @Test
    void couponPreviewPersistsOwnedEncryptedSnapshotWithoutConsumingOrIssuing() {
        couponFixture();
        var result = couponPreviews.preview(couponReq());
        assertTrue(result.redeemable());
        assertNull(result.reason());
        assertEquals("2026-10-23T12:05+08:00", result.expiresAt());
        var row = couponRows.selectById(result.previewId());
        assertEquals(10L, row.getUserId());
        assertEquals(101L, row.getStoreId());
        assertEquals(
                couponReq().getCouponCode(),
                couponCipher.open(row.getCouponCiphertext(), 1L, 101L, 10L));
        assertFalse(row.getCouponCiphertext().contains(couponReq().getCouponCode()));
        assertEquals(64, row.getCouponHash().length());
        assertEquals(64, row.getMappingFingerprint().length());
        assertTrue(row.getTemplateSnapshot().contains("真实次卡"));
        assertFalse(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(result)
                        .contains("13800000001"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM gym_member_card", Integer.class));
        assertEquals(
                0, jdbc.queryForObject("SELECT COUNT(*) FROM gym_verification", Integer.class));
        verify(couponClient, never()).consume(anyLong(), anyLong(), any());
        verify(couponClient, never()).mobileReceipts(anyLong(), anyLong(), any());
    }

    @Test
    void couponPreviewMaskedPhoneRequiresOwnedListAndInvertsPlatformCode() {
        var p = couponFixture();
        p.setMobile("138****0001");
        var platforms = new ArrayList<Integer>();
        var offsets = new ArrayList<Integer>();
        when(couponClient.mobileReceipts(anyLong(), anyLong(), any()))
                .thenAnswer(
                        inv -> {
                            var r =
                                    (cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto
                                                    .retail.MeituanMobileReceiptsRequest)
                                            inv.getArgument(2);
                            assertEquals("13800000001", r.getMobile());
                            assertEquals(30, r.getLimit());
                            platforms.add(r.getPlatform());
                            offsets.add(r.getOffset());
                            return couponResponse(
                                    r.getOffset() == 0
                                            ? couponList(
                                                    Collections.nCopies(30, "other")
                                                            .toArray(String[]::new))
                                            : couponList(p.getReceiptCode()));
                        });
        assertTrue(couponPreviews.preview(couponReq()).redeemable());
        assertEquals(List.of(2, 2), platforms);
        assertEquals(List.of(0, 30), offsets);
        p.setPlatform(2);
        platforms.clear();
        offsets.clear();
        assertTrue(couponPreviews.preview(couponReq()).redeemable());
        assertEquals(List.of(1, 1), platforms);
    }

    @Test
    void couponPreviewRejectsOtherFullPhoneWithoutLeakingProductOrLookingUp() {
        var p = couponFixture();
        p.setMobile("13800000002");
        assertServiceException(
                () -> couponPreviews.preview(couponReq()), COUPON_OWNERSHIP_REQUIRED);
        verify(couponClient, never()).mobileReceipts(anyLong(), anyLong(), any());
        assertEquals(0, couponRows.selectCount());
    }

    @Test
    void couponPreviewOwnershipLookupIsBoundedAndNeverAcceptsSuffixMatch() {
        var p = couponFixture();
        p.setMobile("138****0001");
        when(couponClient.mobileReceipts(anyLong(), anyLong(), any()))
                .thenReturn(
                        couponResponse(
                                couponList(
                                        Collections.nCopies(30, "other").toArray(String[]::new))));
        assertServiceException(
                () -> couponPreviews.preview(couponReq()), COUPON_OWNERSHIP_REQUIRED);
        verify(couponClient, times(10)).mobileReceipts(anyLong(), anyLong(), any());
        assertEquals(0, couponRows.selectCount());
    }

    @Test
    void couponPreviewRejectsMalformedProviderResultAndNeverSavesAnAssumedSuccess() {
        var p = couponFixture();
        p.setReceiptCode("other");
        assertServiceException(() -> couponPreviews.preview(couponReq()), COUPON_PROVIDER_INVALID);
        p.setReceiptCode(couponReq().getCouponCode());
        p.setMobile(null);
        p.setPlatform(9);
        assertServiceException(
                () -> couponPreviews.preview(couponReq()), COUPON_OWNERSHIP_REQUIRED);
        p.setPlatform(1);
        var invalid = couponList();
        invalid.setResult(null);
        when(couponClient.mobileReceipts(anyLong(), anyLong(), any()))
                .thenReturn(couponResponse(invalid));
        assertServiceException(() -> couponPreviews.preview(couponReq()), COUPON_PROVIDER_INVALID);
        assertEquals(0, couponRows.selectCount());
    }

    @Test
    void couponPreviewDisabledMissingKeyAndTenantMappingFailBeforeNetwork() {
        couponFixture();
        couponProperties.setEnabled(false);
        assertServiceException(() -> couponPreviews.preview(couponReq()), COUPON_NOT_CONFIGURED);
        couponProperties.setEnabled(true);
        couponProperties.setEncryptionKey("invalid-key");
        assertServiceException(() -> couponPreviews.preview(couponReq()), COUPON_NOT_CONFIGURED);
        couponProperties.setMappings(
                new HashMap<>(Map.of(2L, couponProperties.getMappings().get(1L))));
        assertServiceException(() -> couponPreviews.preview(couponReq()), COUPON_NOT_CONFIGURED);
        verifyNoInteractions(couponClient);
        assertEquals(0, couponRows.selectCount());
    }

    @Test
    void couponPreviewRespectsStoreVerificationConfigurationAndTimeWindow() {
        couponFixture();
        couponSetting(false, false, null, null);
        assertServiceException(() -> couponPreviews.preview(couponReq()), COUPON_NOT_CONFIGURED);
        couponSetting(true, true, null, null);
        assertServiceException(() -> couponPreviews.preview(couponReq()), COUPON_NOT_CONFIGURED);
        couponSetting(true, false, "12:01", "13:00");
        assertServiceException(() -> couponPreviews.preview(couponReq()), COUPON_NOT_CONFIGURED);
        couponSetting(true, false, "11:00", "12:00");
        assertServiceException(() -> couponPreviews.preview(couponReq()), COUPON_NOT_CONFIGURED);
        verifyNoInteractions(couponClient);
        couponSetting(true, false, "12:00", "13:00");
        assertTrue(couponPreviews.preview(couponReq()).redeemable());
    }

    @Test
    void couponPreviewRejectsInactiveStoreAndRevokedMembership() {
        couponFixture();
        jdbc.update("UPDATE gym_store SET status='inactive' WHERE id=101");
        assertServiceException(() -> couponPreviews.preview(couponReq()), COUPON_PREVIEW_INVALID);
        jdbc.update("UPDATE gym_store SET status='active' WHERE id=101");
        jdbc.update("UPDATE gym_member_store SET deleted=true");
        assertServiceException(() -> couponPreviews.preview(couponReq()), COUPON_PREVIEW_INVALID);
        verifyNoInteractions(couponClient);
    }

    @Test
    void couponPreviewRejectsEffectivePurchaseBlacklistButNotExpiredRestriction() {
        couponFixture();
        Long member = identities.requireMemberId();
        jdbc.update(
                "INSERT INTO gym_blacklist(member_id,block_entry,block_purchase,reason,starts_at,ends_at,tenant_id) VALUES(?,false,true,'fixture',?,?,1)",
                member,
                java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW.plusSeconds(1)));
        assertServiceException(() -> couponPreviews.preview(couponReq()), COUPON_PREVIEW_INVALID);
        verifyNoInteractions(couponClient);
        jdbc.update("UPDATE gym_blacklist SET ends_at=?", java.sql.Timestamp.from(NOW));
        assertTrue(couponPreviews.preview(couponReq()).redeemable());
    }

    @Test
    void couponPreviewOnlyMappedPublishedApplicableCardsAreRedeemable() {
        var p = couponFixture();
        var mapping = couponProperties.store(1L, 101L).get(7L);
        mapping.setEnabled(false);
        assertFalse(couponPreviews.preview(couponReq()).redeemable());
        mapping.setEnabled(true);
        mapping.setDealGroupId(9L);
        assertFalse(couponPreviews.preview(couponReq()).redeemable());
        mapping.setDealGroupId(8L);
        var card = pointTemplates.selectById(mapping.getCardTemplateId());
        card.setStatus("draft");
        pointTemplates.updateById(card);
        assertFalse(couponPreviews.preview(couponReq()).redeemable());
        card.setStatus("published");
        card.setAccessScope("selected");
        card.setStoreIds("[102]");
        pointTemplates.updateById(card);
        assertFalse(couponPreviews.preview(couponReq()).redeemable());
        card.setStoreIds("[101]");
        pointTemplates.updateById(card);
        assertTrue(couponPreviews.preview(couponReq()).redeemable());
        var result = couponRows.selectList().getFirst();
        assertNull(result.getCardTemplateId());
        assertNull(result.getTemplateSnapshot());
    }

    @Test
    void couponPreviewUnsupportedVoucherExpiredOrNotInWindowIsExplicitlyNonRedeemable() {
        var p = couponFixture();
        p.setCount(2);
        assertFalse(couponPreviews.preview(couponReq()).redeemable());
        p.setCount(1);
        p.setBizType(205);
        assertFalse(couponPreviews.preview(couponReq()).redeemable());
        p.setBizType(0);
        p.setTgTimesCardFlag(true);
        assertFalse(couponPreviews.preview(couponReq()).redeemable());
        p.setTgTimesCardFlag(false);
        p.setVerificationType(2);
        assertFalse(couponPreviews.preview(couponReq()).redeemable());
        p.setVerificationType(1);
        p.setReceiptEndDate(NOW.toEpochMilli());
        assertFalse(couponPreviews.preview(couponReq()).redeemable());
        p.setReceiptEndDate(NOW.plusSeconds(2).toEpochMilli());
        assertEquals("2026-10-23T12:00:02+08:00", couponPreviews.preview(couponReq()).expiresAt());
        verify(couponClient, never()).consume(anyLong(), anyLong(), any());
    }

    @Test
    void couponPreviewRechecksIdentityAndConfigurationAfterNetwork() {
        var p = couponFixture();
        when(couponClient.prepare(anyLong(), anyLong(), any()))
                .thenAnswer(
                        inv -> {
                            couponSetting(true, false, null, null);
                            return couponResponse(p);
                        });
        assertServiceException(() -> couponPreviews.preview(couponReq()), COUPON_PREVIEW_INVALID);
        when(couponClient.prepare(anyLong(), anyLong(), any()))
                .thenAnswer(
                        inv -> {
                            couponProperties.store(1L, 101L).get(7L).setEnabled(false);
                            return couponResponse(p);
                        });
        assertServiceException(() -> couponPreviews.preview(couponReq()), COUPON_PREVIEW_INVALID);
        when(couponClient.prepare(anyLong(), anyLong(), any()))
                .thenAnswer(
                        inv -> {
                            users.getUser(10L).setStatus(1);
                            return couponResponse(p);
                        });
        assertServiceException(() -> couponPreviews.preview(couponReq()), MOBILE_IDENTITY_INVALID);
        assertEquals(0, couponRows.selectCount());
    }

    @Test
    void couponPreviewEncryptionAuthenticatesContextUsesFreshNonceAndKeepsLeadingZero() {
        couponFixture();
        var req = couponReq();
        String a = couponCipher.seal(req.getCouponCode(), 1L, 101L, 10L),
                b = couponCipher.seal(req.getCouponCode(), 1L, 101L, 10L);
        assertNotEquals(a, b);
        assertEquals(req.getCouponCode(), couponCipher.open(a, 1L, 101L, 10L));
        assertServiceException(() -> couponCipher.open(a, 2L, 101L, 10L), COUPON_PREVIEW_INVALID);
        assertServiceException(() -> couponCipher.open(a, 1L, 102L, 10L), COUPON_PREVIEW_INVALID);
        assertServiceException(() -> couponCipher.open(a, 1L, 101L, 11L), COUPON_PREVIEW_INVALID);
        assertServiceException(
                () -> couponCipher.open("v1.bad.bad", 1L, 101L, 10L), COUPON_PREVIEW_INVALID);
        assertNotEquals(
                couponCipher.hash(1L, "meituan", req.getCouponCode()),
                couponCipher.hash(2L, "meituan", req.getCouponCode()));
    }

    @Test
    void couponPreviewNetworkRunsOutsideTransactionAndSaveFailureLeavesNoLocalRecord() {
        var p = couponFixture();
        when(couponClient.prepare(anyLong(), anyLong(), any()))
                .thenAnswer(
                        inv -> {
                            assertFalse(
                                    org.springframework.transaction.support
                                            .TransactionSynchronizationManager
                                            .isActualTransactionActive());
                            return couponResponse(p);
                        });
        jdbc.execute(
                "ALTER TABLE gym_coupon_preview ADD CONSTRAINT test_no_preview CHECK (user_id<0)");
        try {
            assertThrows(
                    org.springframework.dao.DataIntegrityViolationException.class,
                    () -> couponPreviews.preview(couponReq()));
        } finally {
            jdbc.execute("ALTER TABLE gym_coupon_preview DROP CONSTRAINT test_no_preview");
        }
        assertEquals(0, couponRows.selectCount());
        assertTrue(couponPreviews.preview(couponReq()).redeemable());
        var tx = new org.springframework.transaction.support.TransactionTemplate(pointTransactions);
        assertThrows(
                org.springframework.transaction.IllegalTransactionStateException.class,
                () -> tx.execute(s -> couponPreviews.preview(couponReq())));
    }

    @Test
    void couponPreviewRejectsUninitializedAdminDisabledAndInvalidRequestBeforeNetwork() {
        assertServiceException(() -> couponPreviews.preview(couponReq()), MOBILE_IDENTITY_REQUIRED);
        couponFixture();
        login(1L, 10L, 2);
        assertServiceException(() -> couponPreviews.preview(couponReq()), MOBILE_IDENTITY_INVALID);
        login(1L, 10L, 1);
        users.getUser(10L).setStatus(1);
        assertServiceException(() -> couponPreviews.preview(couponReq()), MOBILE_IDENTITY_INVALID);
        users.getUser(10L).setStatus(0);
        var req = couponReq();
        req.setPlatform("douyin");
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> couponPreviews.preview(req));
        req.setPlatform("meituan");
        req.setCouponCode("abc xyz");
        assertThrows(
                jakarta.validation.ConstraintViolationException.class,
                () -> couponPreviews.preview(req));
        verifyNoInteractions(couponClient);
    }
}
