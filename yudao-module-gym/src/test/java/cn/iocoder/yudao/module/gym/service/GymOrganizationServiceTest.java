package cn.iocoder.yudao.module.gym.service;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.employee.vo.*;
import cn.iocoder.yudao.module.gym.controller.admin.store.vo.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.file.GymFileDO;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.GymDataScope;
import cn.iocoder.yudao.module.gym.service.employee.GymEmployeeService;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;

import jakarta.annotation.Resource;
import jakarta.validation.ConstraintViolationException;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** 实际 H2 / MyBatis / 事务 / 租户拦截器；只 mock 跨模块 system 服务。 */
@Import({
    cn.iocoder.yudao.module.gym.service.member.GymTrainingPlanService.class,
    cn.iocoder.yudao.module.gym.controller.admin.member.GymTrainingPlanController.class,
    cn.iocoder.yudao.module.gym.service.file.GymMemberFileAccess.class,
    cn.iocoder.yudao.module.gym.service.member.GymMemberCoachService.class,
    cn.iocoder.yudao.module.gym.service.contract.GymContractReminderWorker.class,
    cn.iocoder.yudao.module.gym.service.contract.GymContractReminderScheduler.class,
    cn.iocoder.yudao.module.gym.service.sales.GymFulfillmentQueueService.class,
    cn.iocoder.yudao.module.gym.service.sales.GymFulfillmentWorker.class,
    cn.iocoder.yudao.module.gym.service.sales.GymFulfillmentScheduler.class,
    cn.iocoder.yudao.module.gym.service.sales.GymRefundReviewService.class,
    cn.iocoder.yudao.module.gym.service.sales.GymCommerceService.class,
    cn.iocoder.yudao.module.gym.service.sales.GymSalesRecordsService.class,
    cn.iocoder.yudao.module.gym.controller.admin.sales.GymSalesRecordsController.class,
    cn.iocoder.yudao.module.gym.service.content.GymContentService.class,
    cn.iocoder.yudao.module.gym.controller.admin.content.GymContentController.class,
    cn.iocoder.yudao.module.gym.service.device.GymDeviceService.class,
    cn.iocoder.yudao.module.gym.controller.admin.device.GymDeviceController.class,
    cn.iocoder.yudao.module.gym.service.marketing.GymMarketingService.class,
    cn.iocoder.yudao.module.gym.controller.admin.marketing.GymMarketingController.class,
    cn.iocoder.yudao.module.gym.controller.app.marketing.AppGymFranchiseController.class,
    cn.iocoder.yudao.module.gym.service.settlement.GymSettlementService.class,
    cn.iocoder.yudao.module.gym.controller.admin.settlement.GymSettlementController.class,
    cn.iocoder.yudao.module.gym.service.sales.GymSalesSettingsService.class,
    cn.iocoder.yudao.module.gym.controller.admin.sales.GymSalesSettingsController.class,
    cn.iocoder.yudao.module.gym.service.report.GymReportService.class,
    cn.iocoder.yudao.module.gym.controller.admin.report.GymReportController.class,
    cn.iocoder.yudao.module.gym.service.payroll.GymPayrollService.class,
    cn.iocoder.yudao.module.gym.controller.admin.payroll.GymPayrollController.class,
    cn.iocoder.yudao.module.gym.service.contract.GymContractService.class,
    cn.iocoder.yudao.module.gym.controller.admin.contract.GymContractController.class,
    cn.iocoder.yudao.module.gym.service.contract.GymContractTemplateService.class,
    cn.iocoder.yudao.module.gym.controller.admin.contract.GymContractTemplateController.class,
    cn.iocoder.yudao.module.gym.service.approval.GymApprovalService.class,
    cn.iocoder.yudao.module.gym.controller.admin.approval.GymApprovalController.class,
    cn.iocoder.yudao.module.gym.service.approval.GymApprovalTemplateService.class,
    cn.iocoder.yudao.module.gym.controller.admin.approval.GymApprovalTemplateController.class,
    cn.iocoder.yudao.module.gym.service.common.GymBusinessMutex.class,
    cn.iocoder.yudao.module.gym.service.course.GymCourseService.class,
    cn.iocoder.yudao.module.gym.service.common.GymBusinessClock.class,
    cn.iocoder.yudao.module.gym.controller.admin.course.GymCourseController.class,
    cn.iocoder.yudao.module.gym.service.card.GymCardService.class,
    cn.iocoder.yudao.module.gym.controller.admin.card.GymCardController.class,
    cn.iocoder.yudao.module.gym.service.member.GymMemberRecordsService.class,
    cn.iocoder.yudao.module.gym.controller.admin.member.GymBlacklistController.class,
    cn.iocoder.yudao.module.gym.controller.admin.member.GymMeasurementController.class,
    cn.iocoder.yudao.module.gym.service.member.GymMemberService.class,
    cn.iocoder.yudao.module.gym.controller.admin.member.GymMemberController.class,
    cn.iocoder.yudao.module.gym.service.salary.GymSalaryService.class,
    cn.iocoder.yudao.module.gym.controller.admin.salary.GymSalaryController.class,
    GymStoreService.class,
    GymEmployeeService.class,
    GymDataScope.class,
    cn.iocoder.yudao.module.gym.controller.admin.store.GymStoreController.class,
    cn.iocoder.yudao.module.gym.controller.admin.employee.GymEmployeeController.class,
    GymOrganizationServiceTest.Config.class
})
public class GymOrganizationServiceTest extends BaseDbUnitTest {
    @Resource GymStoreService stores;
    @Resource GymEmployeeService employees;
    @Resource GymFileMapper files;
    @Resource GymStoreMapper storeMapper;
    @Resource org.springframework.context.ApplicationContext applicationContext;
    @Resource cn.iocoder.yudao.module.gym.controller.admin.store.GymStoreController storeController;

    @Resource
    cn.iocoder.yudao.module.gym.controller.admin.employee.GymEmployeeController employeeController;

    @MockitoBean cn.iocoder.yudao.module.infra.api.file.FileApi fileApi;

    @MockitoBean
    cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService tenantFrameworkService;

    @MockitoBean DeptService depts;
    @MockitoBean AdminUserService users;
    @MockitoBean PermissionApi permissions;
    @MockitoBean cn.iocoder.yudao.module.gym.service.common.GymBusinessClock businessClock;

    @Configuration(proxyBeanMethods = false)
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    @org.mybatis.spring.annotation.MapperScan(
            basePackageClasses = cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper.class)
    static class Config {
        @Bean(name = "ss")
        cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService security(
                PermissionApi api) {
            return new cn.iocoder.yudao.framework.security.core.service
                    .SecurityFrameworkServiceImpl(api);
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
                        chain.add(
                                0,
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
        TenantContextHolder.setTenantId(1L);
        var login = new cn.iocoder.yudao.framework.security.core.LoginUser();
        login.setId(1L);
        login.setUserType(2);
        login.setTenantId(1L);
        cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.setLoginUser(
                login, new org.springframework.mock.web.MockHttpServletRequest());
        headquarters();
        when(businessClock.now()).thenAnswer(i -> java.time.Instant.now());
        when(depts.getDept(anyLong()))
                .thenAnswer(
                        i -> {
                            DeptDO d = new DeptDO();
                            d.setId(i.getArgument(0));
                            d.setStatus(0);
                            return d;
                        });
    }

    @AfterEach
    void cleanupContext() {
        TenantContextHolder.clear();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void bothFileMappersCoexistAndStoreMediaUsesGymMapper() {
        assertInstanceOf(
                cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper.class,
                applicationContext.getBean("fileMapper"));
        assertInstanceOf(GymFileMapper.class, applicationContext.getBean("gymFileMapper"));
        stores.create(store(101L, "A"));
        var file = file(101L, "image");
        stores.saveMedia(101L, media(file.getId(), true));
        assertEquals(file.getId().toString(), stores.get(101L).getMedia().getFirst().getFileId());
    }

    void headquarters() {
        DeptDataPermissionRespDTO data = new DeptDataPermissionRespDTO();
        data.setAll(true);
        when(permissions.getDeptDataPermission(any())).thenReturn(data);
    }

    void scoped(Long... ids) {
        DeptDataPermissionRespDTO data = new DeptDataPermissionRespDTO();
        data.setAll(false);
        data.setDeptIds(new HashSet<>(Arrays.asList(ids)));
        when(permissions.getDeptDataPermission(any())).thenReturn(data);
    }

    GymStoreSaveReqVO store(Long dept, String code) {
        GymStoreSaveReqVO req = new GymStoreSaveReqVO();
        req.setDeptId(dept);
        req.setCode(code);
        req.setName("门店" + code);
        req.setAreaSqm(new BigDecimal("123.45"));
        req.setAddress("测试地址");
        req.setFeatures(List.of("私教", "力量,训练"));
        GymStoreSaveReqVO.BusinessHours h = new GymStoreSaveReqVO.BusinessHours();
        h.setOpens("06:00");
        h.setCloses("23:00");
        req.setBusinessHours(h);
        return req;
    }

    GymEmployeeSaveReqVO employee(String no, Long... ids) {
        GymEmployeeSaveReqVO req = new GymEmployeeSaveReqVO();
        req.setEmployeeNo(no);
        req.setName("测试员工");
        req.setPhone("13800000000");
        req.setHiredOn(LocalDate.of(2026, 1, 1));
        List<GymEmployeeSaveReqVO.StoreAssignment> list = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            var a = new GymEmployeeSaveReqVO.StoreAssignment();
            a.setStoreId(ids[i]);
            a.setIsPrimary(i == 0);
            a.setIsManager(false);
            a.setJoinedOn(req.getHiredOn());
            list.add(a);
        }
        req.setStoreAssignments(list);
        return req;
    }

    @Test
    void storeRoundTripAndPartialUpdate() {
        var created = stores.create(store(101L, "A"));
        assertEquals("101", created.getId());
        assertEquals(List.of("私教", "力量,训练"), stores.get(101L).getFeatures());
        var patch = new GymStoreSaveReqVO();
        patch.setName("新名称");
        patch.setFeatures(List.of());
        stores.patch(101L, patch);
        var saved = stores.get(101L);
        assertEquals("新名称", saved.getName());
        assertEquals("A", saved.getCode());
        assertEquals("06:00", saved.getBusinessHours().getOpens());
        assertTrue(saved.getFeatures().isEmpty());
    }

    @Test
    void storeCreationRequiresAllScopeAndUsesSystemDept() {
        scoped(101L);
        assertServiceException(() -> stores.create(store(101L, "A")), STORE_NO_PERMISSION);
        headquarters();
        when(depts.createDept(any())).thenReturn(102L);
        var created = stores.create(store(null, "NEW"));
        assertEquals("102", created.getId());
        verify(depts).createDept(any());
    }

    @Test
    void storeScopeFiltersTotalsAndRejectsDirectAccess() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        scoped(101L);
        var page = stores.page(new GymPageReqVO());
        assertEquals(1L, page.getTotal());
        assertEquals("101", page.getList().getFirst().getId());
        assertServiceException(() -> stores.get(102L), STORE_NO_PERMISSION);
        assertServiceException(
                () -> stores.patch(102L, new GymStoreSaveReqVO()), STORE_NO_PERMISSION);
        var filter = new GymPageReqVO();
        filter.setStoreId(102L);
        assertServiceException(() -> stores.page(filter), STORE_NO_PERMISSION);
        scoped();
        assertEquals(0L, stores.page(new GymPageReqVO()).getTotal());
    }

    @Test
    void storeValidationAndUniqueness() {
        stores.create(store(101L, "A"));
        assertServiceException(() -> stores.create(store(102L, "A")), STORE_CODE_DUPLICATE);
        var bad = store(102L, "B");
        bad.getBusinessHours().setOpens("25:99");
        assertThrows(ConstraintViolationException.class, () -> stores.create(bad));
        var finalBad = store(102L, "B");
        finalBad.setAreaSqm(new BigDecimal("1.234"));
        assertThrows(ConstraintViolationException.class, () -> stores.create(finalBad));
        var invalid = new GymStoreSaveReqVO();
        invalid.setStatus("unknown");
        assertThrows(ConstraintViolationException.class, () -> stores.patch(101L, invalid));
        assertEquals("active", stores.get(101L).getStatus());
    }

    @Test
    void storeTenantIsolationIncludingLocking() {
        stores.create(store(101L, "A"));
        TenantContextHolder.setTenantId(2L);
        assertEquals(0L, stores.page(new GymPageReqVO()).getTotal());
        assertServiceException(() -> stores.get(101L), STORE_NOT_EXISTS);
        assertServiceException(() -> stores.patch(101L, new GymStoreSaveReqVO()), STORE_NOT_EXISTS);
        stores.create(store(102L, "A"));
        assertEquals("102", stores.page(new GymPageReqVO()).getList().getFirst().getId());
    }

    GymFileDO file(Long storeId, String category) {
        GymFileDO f = new GymFileDO();
        f.setStoreId(storeId);
        f.setCategory(category);
        f.setName("测试.png");
        f.setMimeType("image/png");
        f.setSize(10L);
        files.insert(f);
        return f;
    }

    GymStoreMediaReqVO media(Long fileId, boolean cover) {
        var item = new GymStoreMediaReqVO.Item();
        item.setFileId(fileId);
        item.setIsCover(cover);
        item.setSortOrder(0);
        item.setCaption("封面");
        var req = new GymStoreMediaReqVO();
        req.setItems(List.of(item));
        return req;
    }

    @Test
    void mediaRoundTripReplaceAndClear() {
        stores.create(store(101L, "A"));
        var f = file(101L, "image");
        stores.saveMedia(101L, media(f.getId(), true));
        var item = stores.get(101L).getMedia().getFirst();
        assertEquals(f.getId().toString(), item.getFileId());
        assertEquals("测试.png", item.getOriginalName());
        stores.saveMedia(101L, media(f.getId(), true));
        assertEquals(1, stores.get(101L).getMedia().size());
        var empty = new GymStoreMediaReqVO();
        empty.setItems(List.of());
        stores.saveMedia(101L, empty);
        assertTrue(stores.get(101L).getMedia().isEmpty());
    }

    @Test
    void invalidMediaDoesNotReplaceExisting() {
        stores.create(store(101L, "A"));
        var f = file(101L, "image");
        stores.saveMedia(101L, media(f.getId(), true));
        var other = file(102L, "image");
        assertServiceException(
                () -> stores.saveMedia(101L, media(other.getId(), false)), STORE_MEDIA_INVALID);
        var video = file(101L, "video");
        assertServiceException(
                () -> stores.saveMedia(101L, media(video.getId(), true)), STORE_MEDIA_INVALID);
        var req = media(f.getId(), true);
        req.setItems(List.of(req.getItems().getFirst(), req.getItems().getFirst()));
        assertServiceException(() -> stores.saveMedia(101L, req), STORE_MEDIA_INVALID);
        assertEquals(1, stores.get(101L).getMedia().size());
    }

    @Test
    void employeeRoundTripPatchAndAssignments() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        var e = employees.create(employee("E1", 101L));
        Long id = Long.valueOf(e.getId());
        var patch = new GymEmployeeSaveReqVO();
        patch.setJobTitle("教练");
        employees.patch(id, patch);
        assertEquals("教练", employees.get(id).getJobTitle());
        assertEquals(List.of("101"), employees.get(id).getStoreIds());
        patch = new GymEmployeeSaveReqVO();
        patch.setStoreAssignments(employee("E1", 102L).getStoreAssignments());
        employees.patch(id, patch);
        assertEquals(List.of("102"), employees.get(id).getStoreIds());
        employees.patch(id, patch);
        assertEquals(1, employees.get(id).getStoreAssignments().size());
    }

    @Test
    void employeeScopeMasksOtherStoresAndPreventsSharedWrites() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        var shared = employees.create(employee("E1", 101L, 102L));
        var other = employees.create(employee("E2", 102L));
        scoped(101L);
        assertEquals(1L, employees.page(new GymPageReqVO()).getTotal());
        assertEquals(List.of("101"), employees.get(Long.valueOf(shared.getId())).getStoreIds());
        assertServiceException(
                () -> employees.get(Long.valueOf(other.getId())), STORE_NO_PERMISSION);
        var patch = new GymEmployeeSaveReqVO();
        patch.setName("越权修改");
        assertServiceException(
                () -> employees.patch(Long.valueOf(shared.getId()), patch), STORE_NO_PERMISSION);
        assertServiceException(() -> employees.create(employee("E3", 102L)), STORE_NO_PERMISSION);
    }

    @Test
    void employeeImmutableAndUniqueNumberAndSalaryRejection() {
        stores.create(store(101L, "A"));
        var e = employees.create(employee("E1", 101L));
        Long id = Long.valueOf(e.getId());
        assertServiceException(() -> employees.create(employee("E1", 101L)), EMPLOYEE_NO_DUPLICATE);
        var patch = new GymEmployeeSaveReqVO();
        patch.setEmployeeNo("E2");
        assertServiceException(() -> employees.patch(id, patch), EMPLOYEE_NO_IMMUTABLE);
        var salary = new GymEmployeeSaveReqVO();
        salary.setName("不能落库");
        var assign =
                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                        .GymSalaryAssignmentReqVO();
        assign.setTemplateId(1L);
        salary.setSalaryAssignment(assign);
        assertThrows(ConstraintViolationException.class, () -> employees.patch(id, salary));
        assertEquals("测试员工", employees.get(id).getName());
    }

    @Test
    void employeeRequiresOnePrimaryAndValidDates() {
        stores.create(store(101L, "A"));
        var dup = employee("E1", 101L, 101L);
        assertServiceException(() -> employees.create(dup), EMPLOYEE_ASSIGNMENT_INVALID);
        var noPrimary = employee("E2", 101L);
        noPrimary.getStoreAssignments().getFirst().setIsPrimary(false);
        assertServiceException(() -> employees.create(noPrimary), EMPLOYEE_ASSIGNMENT_INVALID);
        var early = employee("E3", 101L);
        early.getStoreAssignments().getFirst().setJoinedOn(LocalDate.of(2025, 1, 1));
        assertServiceException(() -> employees.create(early), EMPLOYEE_ASSIGNMENT_INVALID);
        var empty = employee("E4");
        assertThrows(ConstraintViolationException.class, () -> employees.create(empty));
    }

    @Test
    void employeeUserLinkRequiresPermissionAndPrimaryDept() {
        stores.create(store(101L, "A"));
        var req = employee("E1", 101L);
        req.setUserId(900L);
        assertServiceException(() -> employees.create(req), EMPLOYEE_USER_NO_PERMISSION);
        when(permissions.hasAnyPermissions(any(), eq("system:user:update"))).thenReturn(true);
        var user = new AdminUserDO();
        user.setId(900L);
        user.setDeptId(102L);
        user.setStatus(0);
        user.setUsername("linked-user");
        when(users.getUser(900L)).thenReturn(user);
        assertServiceException(() -> employees.create(req), EMPLOYEE_USER_INVALID);
        user.setDeptId(101L);
        var e = employees.create(req);
        assertEquals("900", e.getUserId());
        assertEquals("linked-user", e.getUsername());
        var second = employee("E2", 101L);
        second.setUserId(900L);
        assertServiceException(() -> employees.create(second), EMPLOYEE_NO_DUPLICATE);
    }

    @Test
    void employeeTenantIsolationAndScopedPagination() {
        stores.create(store(101L, "A"));
        var e = employees.create(employee("E1", 101L));
        Long id = Long.valueOf(e.getId());
        TenantContextHolder.setTenantId(2L);
        assertEquals(0L, employees.page(new GymPageReqVO()).getTotal());
        assertServiceException(() -> employees.get(id), EMPLOYEE_NOT_EXISTS);
        assertServiceException(
                () -> employees.patch(id, new GymEmployeeSaveReqVO()), EMPLOYEE_NOT_EXISTS);
        TenantContextHolder.setTenantId(1L);
        scoped(101L);
        var req = new GymPageReqVO();
        req.setPageSize(1);
        req.setKeyword("E1");
        assertEquals(1L, employees.page(req).getTotal());
        req.setKeyword("missing");
        assertEquals(0L, employees.page(req).getTotal());
    }

    @Test
    void readOnlyRoleCannotWriteThroughControllers() {
        stores.create(store(101L, "A"));
        when(permissions.hasAnyPermissions(eq(1L), eq("gym:stores:read"))).thenReturn(true);
        when(permissions.hasAnyPermissions(eq(1L), eq("gym:employees:read"))).thenReturn(true);
        assertEquals(0, storeController.get(101L).getCode());
        assertEquals(0, employeeController.page(new GymPageReqVO()).getCode());
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> storeController.create(store(102L, "B")));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> storeController.patch(101L, new GymStoreSaveReqVO()));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> employeeController.create(employee("E1", 101L)));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> employeeController.patch(1L, new GymEmployeeSaveReqVO()));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> storeController.media(101L, new GymStoreMediaReqVO()));
    }

    @Test
    void noPermissionCannotReadThroughControllers() {
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> storeController.page(new GymPageReqVO()));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> storeController.get(101L));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> employeeController.page(new GymPageReqVO()));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> employeeController.get(1L));
    }

    @Test
    void employeeRelationReplacementRollsBackOnDatabaseFailure() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        var e = employees.create(employee("E1", 101L));
        Long id = Long.valueOf(e.getId());
        // 强制在主档案写入之后、替换关系过程中触发数据库约束失败。
        // 不 mock 数据库：使用 H2 CHECK 约束模拟写入异常。
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.execute(
                "ALTER TABLE gym_employee_store ADD CONSTRAINT reject_store_b CHECK (store_id <> 102)");
        try {
            var patch = new GymEmployeeSaveReqVO();
            patch.setName("应回滚");
            patch.setStoreAssignments(employee("E1", 102L).getStoreAssignments());
            assertThrows(
                    org.springframework.dao.DataIntegrityViolationException.class,
                    () -> employees.patch(id, patch));
            assertEquals("测试员工", employees.get(id).getName());
            assertEquals(List.of("101"), employees.get(id).getStoreIds());
        } finally {
            jdbc.execute("ALTER TABLE gym_employee_store DROP CONSTRAINT reject_store_b");
        }
    }

    @Resource javax.sql.DataSource dataSource;

    @Test
    void fileResponseMatchesCurrentFrontendContract() {
        var file = file(101L, "image");
        file.setUploaderId(1L);
        var vo =
                cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(
                        file,
                        cn.iocoder.yudao.module.gym.controller.admin.file.vo.GymFileRespVO.class);
        var json =
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(
                        cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(vo));
        assertTrue(json.get("id").isString());
        assertEquals("101", json.get("storeId").asString());
        assertEquals("测试.png", json.get("originalName").asString());
        assertEquals(10L, json.get("byteSize").asLong());
        assertNull(json.get("url"));
    }

    @Resource cn.iocoder.yudao.module.gym.service.salary.GymSalaryService salaries;

    @Resource
    cn.iocoder.yudao.module.gym.controller.admin.salary.GymSalaryController salaryController;

    cn.iocoder.yudao.module.gym.controller.admin.salary.vo.GymSalaryTemplateSaveReqVO
            salaryTemplate() {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                        .GymSalaryTemplateSaveReqVO();
        req.setName("工资模板");
        req.setBaseSalary(new BigDecimal("5000.00"));
        req.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        req.setRules(List.of());
        return req;
    }

    Long publishedSalary() {
        Long id = Long.valueOf(salaries.create(salaryTemplate()).getId());
        var patch =
                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                        .GymSalaryTemplateSaveReqVO();
        patch.setStatus("published");
        salaries.patch(id, patch);
        return id;
    }

    cn.iocoder.yudao.module.gym.controller.admin.salary.vo.GymSalaryAssignmentReqVO
            salaryAssignment(Long employee, Long template, Long store, LocalDate from) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                        .GymSalaryAssignmentReqVO();
        req.setEmployeeId(employee);
        req.setTemplateId(template);
        req.setStoreId(store);
        req.setEffectiveFrom(from);
        return req;
    }

    void salaryPermission() {
        when(permissions.hasAnyPermissions(any(), eq("gym:payroll:templates"))).thenReturn(true);
    }

    @Test
    void salaryTemplateRoundTripPublishAndImmutableVersions() {
        var req = salaryTemplate();
        var rule =
                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                        .GymSalaryTemplateSaveReqVO.Rule();
        rule.setComponent("card_sales");
        rule.setCalculationType("rate");
        rule.setRate(new BigDecimal("0.05"));
        req.setRules(List.of(rule));
        req.setEffectiveTo(LocalDate.of(2027, 1, 1));
        var created = salaries.create(req);
        Long id = Long.valueOf(created.getId());
        assertEquals(new BigDecimal("0.05"), salaries.template(id).getRules().getFirst().getRate());
        var publish =
                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                        .GymSalaryTemplateSaveReqVO();
        publish.setStatus("published");
        assertEquals(req.getEffectiveTo(), salaries.patch(id, publish).getEffectiveTo());
        assertServiceException(
                () -> salaries.patch(id, salaryTemplate()), SALARY_TEMPLATE_IMMUTABLE);
        assertEquals(2, salaries.version(id, salaryTemplate()).getVersion());
        assertEquals(3, salaries.version(id, salaryTemplate()).getVersion());
        assertEquals("published", salaries.template(id).getStatus());
        assertEquals(1, salaries.template(id).getVersion());
    }

    @Test
    void salaryRulesRejectBadAmountsIntervalsAndOverlappingTiers() {
        var req = salaryTemplate();
        req.setBaseSalary(new BigDecimal("-1"));
        assertThrows(ConstraintViolationException.class, () -> salaries.create(req));
        var invalid = salaryTemplate();
        invalid.setEffectiveTo(invalid.getEffectiveFrom());
        assertServiceException(() -> salaries.create(invalid), SALARY_INTERVAL_INVALID);
        var rule =
                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                        .GymSalaryTemplateSaveReqVO.Rule();
        rule.setComponent("lessons");
        rule.setCalculationType("fixed");
        rule.setFixedAmount(BigDecimal.TEN);
        var badRule = salaryTemplate();
        badRule.setRules(List.of(rule));
        assertServiceException(() -> salaries.create(badRule), SALARY_RULE_INVALID);
        rule.setComponent("card_sales");
        rule.setCalculationType("rate");
        rule.setFixedAmount(null);
        rule.setRate(new BigDecimal("0.05"));
        var duplicate = salaryTemplate();
        duplicate.setRules(List.of(rule, rule));
        assertServiceException(() -> salaries.create(duplicate), SALARY_RULE_INVALID);
    }

    @Test
    void salaryTemplatesWriteNeedsHeadquartersAndControllerPermissions() {
        scoped(101L);
        assertServiceException(() -> salaries.create(salaryTemplate()), STORE_NO_PERMISSION);
        headquarters();
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> salaryController.create(salaryTemplate()));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () ->
                        salaryController.assignments(
                                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                                        .GymSalaryPageReqVO()));
    }

    @Test
    void salaryAssignmentChangesAreAtomicAndIdempotent() {
        stores.create(store(101L, "A"));
        var employee = employees.create(employee("ES", 101L));
        Long eid = Long.valueOf(employee.getId());
        Long tid = publishedSalary();
        salaryPermission();
        var first = salaryAssignment(eid, tid, 101L, LocalDate.of(2026, 1, 1));
        var a = salaries.createAssignment(first);
        assertEquals(a.getId(), salaries.createAssignment(first).getId());
        var next = salaryAssignment(eid, tid, 101L, LocalDate.of(2026, 6, 1));
        salaries.createAssignment(next);
        assertEquals(
                LocalDate.of(2026, 6, 1),
                salaries.assignment(Long.valueOf(a.getId())).getEffectiveTo());
        assertEquals(
                2L,
                salaries.assignments(
                                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                                        .GymSalaryPageReqVO())
                        .getTotal());
        var overlap = salaryAssignment(eid, tid, 101L, LocalDate.of(2026, 5, 1));
        overlap.setEffectiveTo(LocalDate.of(2026, 7, 1));
        assertServiceException(() -> salaries.createAssignment(overlap), SALARY_ASSIGNMENT_OVERLAP);
        var patch =
                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                        .GymSalaryAssignmentReqVO();
        patch.setEffectiveTo(LocalDate.of(2026, 7, 1));
        assertServiceException(
                () -> salaries.patchAssignment(Long.valueOf(a.getId()), patch),
                SALARY_ASSIGNMENT_OVERLAP);
    }

    @Test
    void salaryAssignmentValidatesPublishedTemplateDatesAndStoreRelation() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long eid = Long.valueOf(employees.create(employee("ES", 101L)).getId());
        Long draft = Long.valueOf(salaries.create(salaryTemplate()).getId());
        salaryPermission();
        var req = salaryAssignment(eid, draft, 101L, LocalDate.of(2026, 1, 1));
        assertServiceException(() -> salaries.createAssignment(req), SALARY_ASSIGNMENT_INVALID);
        Long published = publishedSalary();
        req.setTemplateId(published);
        req.setStoreId(102L);
        assertServiceException(() -> salaries.createAssignment(req), SALARY_ASSIGNMENT_INVALID);
        req.setStoreId(101L);
        req.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        assertServiceException(() -> salaries.createAssignment(req), SALARY_ASSIGNMENT_INVALID);
    }

    @Test
    void salaryInEmployeePatchRequiresExtraPermissionAndRollsBack() {
        stores.create(store(101L, "A"));
        Long eid = Long.valueOf(employees.create(employee("ES", 101L)).getId());
        Long template = publishedSalary();
        var patch = new GymEmployeeSaveReqVO();
        patch.setName("应回滚");
        patch.setSalaryAssignment(salaryAssignment(eid, template, 101L, LocalDate.of(2026, 1, 1)));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> employees.patch(eid, patch));
        assertEquals("测试员工", employees.get(eid).getName());
        salaryPermission();
        employees.patch(eid, patch);
        assertEquals("应回滚", employees.get(eid).getName());
        assertEquals(
                1L,
                salaries.assignments(
                                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                                        .GymSalaryPageReqVO())
                        .getTotal());
    }

    @Test
    void salaryHistoryFiltersStoresAndTenantsIncludingDirectIds() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long a = Long.valueOf(employees.create(employee("EA", 101L)).getId()),
                b = Long.valueOf(employees.create(employee("EB", 102L)).getId());
        Long tid = publishedSalary();
        salaryPermission();
        salaries.createAssignment(salaryAssignment(a, tid, 101L, LocalDate.of(2026, 1, 1)));
        var other =
                salaries.createAssignment(salaryAssignment(b, tid, 102L, LocalDate.of(2026, 1, 1)));
        scoped(101L);
        var req = new cn.iocoder.yudao.module.gym.controller.admin.salary.vo.GymSalaryPageReqVO();
        assertEquals(1L, salaries.assignments(req).getTotal());
        assertServiceException(
                () -> salaries.assignment(Long.valueOf(other.getId())), STORE_NO_PERMISSION);
        req.setStoreId(102L);
        assertServiceException(() -> salaries.assignments(req), STORE_NO_PERMISSION);
        TenantContextHolder.setTenantId(2L);
        headquarters();
        assertServiceException(
                () -> salaries.assignment(Long.valueOf(other.getId())),
                SALARY_ASSIGNMENT_NOT_EXISTS);
        assertServiceException(() -> salaries.template(tid), SALARY_TEMPLATE_NOT_EXISTS);
    }

    @Test
    void salaryAssignmentCannotCloseOtherStoresHistory() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long eid = Long.valueOf(employees.create(employee("ES", 101L, 102L)).getId());
        Long tid = publishedSalary();
        salaryPermission();
        var old =
                salaries.createAssignment(
                        salaryAssignment(eid, tid, 102L, LocalDate.of(2026, 1, 1)));
        scoped(101L);
        assertServiceException(
                () ->
                        salaries.createAssignment(
                                salaryAssignment(eid, tid, 101L, LocalDate.of(2026, 6, 1))),
                STORE_NO_PERMISSION);
        headquarters();
        assertNull(salaries.assignment(Long.valueOf(old.getId())).getEffectiveTo());
    }

    @Test
    void salaryPatchDistinguishesOmittedAndExplicitNullEndDates() {
        var req = salaryTemplate();
        req.setEffectiveTo(LocalDate.of(2027, 1, 1));
        Long id = Long.valueOf(salaries.create(req).getId());
        var patch =
                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                        .GymSalaryTemplateSaveReqVO();
        patch.setName("改名");
        assertEquals(req.getEffectiveTo(), salaries.patch(id, patch).getEffectiveTo());
        patch.setEffectiveTo(null);
        assertNull(salaries.patch(id, patch).getEffectiveTo());
        stores.create(store(101L, "A"));
        Long eid = Long.valueOf(employees.create(employee("ES", 101L)).getId());
        salaryPermission();
        var a = salaryAssignment(eid, publishedSalary(), 101L, LocalDate.of(2026, 1, 1));
        a.setEffectiveTo(LocalDate.of(2026, 6, 1));
        Long aid = Long.valueOf(salaries.createAssignment(a).getId());
        var end =
                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                        .GymSalaryAssignmentReqVO();
        assertEquals(a.getEffectiveTo(), salaries.patchAssignment(aid, end).getEffectiveTo());
        end.setEffectiveTo(null);
        assertNull(salaries.patchAssignment(aid, end).getEffectiveTo());
    }

    @Resource cn.iocoder.yudao.module.gym.service.member.GymMemberService members;

    @Resource
    cn.iocoder.yudao.module.gym.controller.admin.member.GymMemberController memberController;

    cn.iocoder.yudao.module.gym.controller.admin.member.vo.GymMemberSaveReqVO member(
            Long store, String phone) {
        var req = new cn.iocoder.yudao.module.gym.controller.admin.member.vo.GymMemberSaveReqVO();
        req.setStoreId(store);
        req.setPhone(phone);
        req.setName("联调会员");
        return req;
    }

    @Test
    void memberRoundTripUniquePhoneAndAdminProfileUpdate() {
        stores.create(store(101L, "A"));
        var created = members.create(member(101L, "13800000001"));
        Long id = Long.valueOf(created.getId());
        assertTrue(created.getMemberNo().startsWith("A-"));
        assertNotNull(created.getRegisteredAt());
        assertEquals("unspecified", created.getGender());
        assertEquals("门店A", created.getStoreNames());
        var patch = new cn.iocoder.yudao.module.gym.controller.admin.member.vo.GymMemberSaveReqVO();
        patch.setNickname("昵称");
        patch.setPhone("13800000002");
        members.patch(id, patch);
        assertEquals("13800000002", members.get(id).getPhone());
        assertEquals("昵称", members.get(id).getNickname());
        assertEquals(created.getMemberNo(), members.get(id).getMemberNo());
        assertServiceException(
                () -> members.create(member(101L, "13800000002")), MEMBER_PHONE_DUPLICATE);
        var invalid = member(101L, "123");
        assertThrows(ConstraintViolationException.class, () -> members.create(invalid));
    }

    @Test
    void memberScopeAndTenantIsolationApplyToReadsAndWrites() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        var own = members.create(member(101L, "13800000001"));
        var other = members.create(member(102L, "13800000002"));
        Long oid = Long.valueOf(other.getId());
        scoped(101L);
        var req = new cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO();
        assertEquals(1L, members.page(req).getTotal());
        assertEquals(own.getId(), members.page(req).getList().getFirst().getId());
        assertServiceException(() -> members.get(oid), STORE_NO_PERMISSION);
        assertServiceException(
                () ->
                        members.patch(
                                oid,
                                new cn.iocoder.yudao.module.gym.controller.admin.member.vo
                                        .GymMemberSaveReqVO()),
                STORE_NO_PERMISSION);
        assertServiceException(
                () -> members.create(member(102L, "13800000003")), STORE_NO_PERMISSION);
        req.setStoreId(102L);
        assertServiceException(() -> members.page(req), STORE_NO_PERMISSION);
        headquarters();
        TenantContextHolder.setTenantId(2L);
        req.setStoreId(null);
        assertEquals(0L, members.page(req).getTotal());
        assertServiceException(() -> members.get(oid), MEMBER_NOT_EXISTS);
    }

    @Test
    void memberDateFilterAndPermissionsAreEnforced() {
        stores.create(store(101L, "A"));
        members.create(member(101L, "13800000001"));
        var req = new cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO();
        req.setDate(LocalDate.now(cn.iocoder.yudao.module.gym.service.common.GymDateRange.ZONE));
        assertEquals(1L, members.page(req).getTotal());
        req.setDate(LocalDate.of(2020, 1, 1));
        assertEquals(0L, members.page(req).getTotal());
        req.setMonth("2020-01");
        assertServiceException(() -> members.page(req), BUSINESS_DATE_INVALID);
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () ->
                        memberController.page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO()));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> memberController.create(member(101L, "13800000002")));
    }

    @Resource cn.iocoder.yudao.module.gym.service.member.GymMemberRecordsService records;

    @Resource
    cn.iocoder.yudao.module.gym.controller.admin.member.GymBlacklistController blacklistController;

    @Resource
    cn.iocoder.yudao.module.gym.controller.admin.member.GymMeasurementController
            measurementController;

    cn.iocoder.yudao.module.gym.controller.admin.member.vo.GymBlacklistSaveReqVO blacklist(
            Long member) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.member.vo.GymBlacklistSaveReqVO();
        req.setMemberId(member);
        req.setBlockEntry(true);
        req.setBlockPurchase(true);
        req.setStartsAt(java.time.Instant.now().minusSeconds(60));
        req.setReason("测试限制");
        return req;
    }

    cn.iocoder.yudao.module.gym.controller.admin.member.vo.GymMeasurementSaveReqVO measurement(
            Long member, Long store) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.member.vo
                        .GymMeasurementSaveReqVO();
        req.setMemberId(member);
        req.setStoreId(store);
        req.setMeasuredAt(java.time.Instant.now());
        req.setWeightKg(new BigDecimal("70.50"));
        return req;
    }

    @Test
    void blacklistPersistsBlocksAndRevokesWithRealTimeBoundaries() {
        stores.create(store(101L, "A"));
        Long mid = Long.valueOf(members.create(member(101L, "13800000001")).getId());
        var created = records.createBlacklist(blacklist(mid));
        Long id = Long.valueOf(created.getId());
        assertEquals("active", created.getStatus());
        assertServiceException(() -> records.checkEntry(mid), MEMBER_ENTRY_BLOCKED);
        assertServiceException(() -> records.checkPurchase(mid), MEMBER_PURCHASE_BLOCKED);
        var patch =
                new cn.iocoder.yudao.module.gym.controller.admin.member.vo.GymBlacklistSaveReqVO();
        patch.setBlockPurchase(false);
        records.patchBlacklist(id, patch);
        assertDoesNotThrow(() -> records.checkPurchase(mid));
        patch.setRevoked(true);
        assertEquals("revoked", records.patchBlacklist(id, patch).getStatus());
        assertDoesNotThrow(() -> records.checkEntry(mid));
        var revoke =
                new cn.iocoder.yudao.module.gym.controller.admin.member.vo.GymBlacklistSaveReqVO();
        revoke.setRevoked(true);
        assertEquals("revoked", records.patchBlacklist(id, revoke).getStatus());
        assertServiceException(() -> records.patchBlacklist(id, blacklist(mid)), BLACKLIST_REVOKED);
    }

    @Test
    void blacklistFutureExpiredAndInvalidIntervals() {
        stores.create(store(101L, "A"));
        Long mid = Long.valueOf(members.create(member(101L, "13800000001")).getId());
        var future = blacklist(mid);
        future.setStartsAt(java.time.Instant.now().plusSeconds(3600));
        records.createBlacklist(future);
        assertDoesNotThrow(() -> records.checkEntry(mid));
        var expired = blacklist(mid);
        expired.setStartsAt(java.time.Instant.now().minusSeconds(3600));
        expired.setEndsAt(java.time.Instant.now().minusSeconds(60));
        assertEquals("expired", records.createBlacklist(expired).getStatus());
        assertDoesNotThrow(() -> records.checkPurchase(mid));
        var invalid = blacklist(mid);
        invalid.setEndsAt(invalid.getStartsAt());
        assertServiceException(() -> records.createBlacklist(invalid), BLACKLIST_INVALID);
        invalid.setEndsAt(null);
        invalid.setBlockEntry(false);
        invalid.setBlockPurchase(false);
        assertServiceException(() -> records.createBlacklist(invalid), BLACKLIST_INVALID);
    }

    @Test
    void blacklistWriteNeedsHeadquartersAndReadsRespectTenantAndStore() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long mid = Long.valueOf(members.create(member(102L, "13800000001")).getId());
        Long id = Long.valueOf(records.createBlacklist(blacklist(mid)).getId());
        scoped(101L);
        assertEquals(
                0L,
                records.blacklists(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        assertServiceException(() -> records.blacklist(id), STORE_NO_PERMISSION);
        assertServiceException(() -> records.createBlacklist(blacklist(mid)), STORE_NO_PERMISSION);
        headquarters();
        TenantContextHolder.setTenantId(2L);
        assertServiceException(() -> records.blacklist(id), BLACKLIST_NOT_EXISTS);
        assertDoesNotThrow(() -> records.checkEntry(mid));
    }

    @Test
    void measurementsRoundTripAndReportMustBeSameStoreCorrectCategory() {
        stores.create(store(101L, "A"));
        Long mid = Long.valueOf(members.create(member(101L, "13800000001")).getId());
        var created = records.createMeasurement(measurement(mid, 101L));
        Long id = Long.valueOf(created.getId());
        assertEquals(new BigDecimal("70.50"), records.measurement(id).getWeightKg());
        var report = file(101L, "body_report");
        var patch =
                new cn.iocoder.yudao.module.gym.controller.admin.member.vo
                        .GymMeasurementSaveReqVO();
        patch.setReportFileId(report.getId());
        records.patchMeasurement(id, patch);
        assertEquals(report.getId().toString(), records.measurement(id).getReportFileId());
        var wrong = file(102L, "body_report");
        patch.setReportFileId(wrong.getId());
        assertServiceException(() -> records.patchMeasurement(id, patch), MEASUREMENT_FILE_INVALID);
        wrong = file(101L, "image");
        patch.setReportFileId(wrong.getId());
        assertServiceException(() -> records.patchMeasurement(id, patch), MEASUREMENT_FILE_INVALID);
        assertEquals(report.getId().toString(), records.measurement(id).getReportFileId());
        var invalid =
                new cn.iocoder.yudao.module.gym.controller.admin.member.vo
                        .GymMeasurementSaveReqVO();
        invalid.setBodyFatPercent(new BigDecimal("101"));
        assertThrows(
                ConstraintViolationException.class, () -> records.patchMeasurement(id, invalid));
    }

    @Test
    void measurementImmutableIdentityScopeAndTenant() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long mid = Long.valueOf(members.create(member(101L, "13800000001")).getId());
        var created = records.createMeasurement(measurement(mid, 101L));
        Long id = Long.valueOf(created.getId());
        var patch =
                new cn.iocoder.yudao.module.gym.controller.admin.member.vo
                        .GymMeasurementSaveReqVO();
        patch.setStoreId(102L);
        assertServiceException(() -> records.patchMeasurement(id, patch), MEASUREMENT_INVALID);
        assertServiceException(
                () -> records.createMeasurement(measurement(mid, 102L)), MEASUREMENT_INVALID);
        scoped(102L);
        assertServiceException(() -> records.measurement(id), STORE_NO_PERMISSION);
        assertEquals(
                0L,
                records.measurements(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        headquarters();
        TenantContextHolder.setTenantId(2L);
        assertServiceException(() -> records.measurement(id), MEASUREMENT_NOT_EXISTS);
    }

    @Test
    void memberRecordsControllerPermissionsCannotBeBypassed() {
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () ->
                        blacklistController.page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO()));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> blacklistController.create(blacklist(1L)));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () ->
                        measurementController.page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO()));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> measurementController.create(measurement(1L, 101L)));
    }

    @Resource cn.iocoder.yudao.module.gym.service.card.GymCardService cards;
    @Resource cn.iocoder.yudao.module.gym.controller.admin.card.GymCardController cardController;

    cn.iocoder.yudao.module.gym.controller.admin.card.vo.GymCardTemplateSaveReqVO cardTemplate(
            String kind) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.card.vo.GymCardTemplateSaveReqVO();
        req.setName("联调卡模板");
        req.setKind(kind);
        req.setDurationDays(30);
        req.setIncludedUses(10);
        req.setPrice(new BigDecimal("100.00"));
        req.setAccessScope("all");
        req.setStatus("published");
        return req;
    }

    @Test
    void cardTemplateVersionAndStateCannotRewritePublishedSnapshot() {
        var created = cards.create(cardTemplate("period"));
        Long id = Long.valueOf(created.getId());
        assertNull(created.getIncludedUses());
        assertEquals(30, created.getDurationDays());
        var patch =
                new cn.iocoder.yudao.module.gym.controller.admin.card.vo.GymCardTemplateSaveReqVO();
        patch.setPrice(BigDecimal.ONE);
        assertServiceException(() -> cards.patch(id, patch), CARD_TEMPLATE_IMMUTABLE);
        patch.setPrice(null);
        patch.setStatus("inactive");
        assertEquals("inactive", cards.patch(id, patch).getStatus());
        patch.setStatus("draft");
        assertServiceException(() -> cards.patch(id, patch), CARD_TEMPLATE_IMMUTABLE);
        var next =
                cards.version(
                        id,
                        new cn.iocoder.yudao.module.gym.controller.admin.card.vo
                                .GymCardTemplateSaveReqVO());
        assertEquals(2, next.getVersion());
        assertEquals("draft", next.getStatus());
        assertEquals(new BigDecimal("100.00"), next.getPrice());
        assertEquals(
                3,
                cards.version(
                                id,
                                new cn.iocoder.yudao.module.gym.controller.admin.card.vo
                                        .GymCardTemplateSaveReqVO())
                        .getVersion());
    }

    @Test
    void cardTemplateSelectedStoresAndScopeBeforePagination() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        var req = cardTemplate("count");
        req.setAccessScope("selected");
        req.setStoreIds(List.of(102L));
        var b = cards.create(req);
        req.setStoreIds(List.of(101L));
        var a = cards.create(req);
        scoped(101L);
        var page = new cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO();
        page.setPageSize(1);
        assertEquals(1L, cards.templates(page).getTotal());
        assertEquals(a.getId(), cards.templates(page).getList().getFirst().getId());
        assertServiceException(() -> cards.template(Long.valueOf(b.getId())), STORE_NO_PERMISSION);
        assertServiceException(() -> cards.create(cardTemplate("count")), STORE_NO_PERMISSION);
        headquarters();
        req.setStoreIds(List.of(101L, 101L));
        assertServiceException(() -> cards.create(req), CARD_TEMPLATE_INVALID);
    }

    @Test
    void cardIssuanceHasSnapshotSourceIdempotencyAndGlobalMemberships() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long mid = Long.valueOf(members.create(member(101L, "13800000001")).getId());
        Long tid = Long.valueOf(cards.create(cardTemplate("period")).getId());
        var start = java.time.Instant.now();
        var issued = cards.issue(mid, 101L, tid, start, "approval", "approval-1");
        assertEquals(
                issued.getId(),
                cards.issue(mid, 101L, tid, start, "approval", "approval-1").getId());
        assertEquals(
                issued.getStartsAt().plus(30, java.time.temporal.ChronoUnit.DAYS),
                issued.getExpiresAt());
        assertEquals(
                1L,
                cards.cards(
                                mid,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        assertEquals(2, members.get(mid).getStoreIds().size());
        assertEquals("member", members.get(mid).getCustomerStage());
        assertTrue(issued.getTemplateSnapshot().contains("100.00"));
        scoped(102L);
        assertEquals(List.of("102"), members.get(mid).getStoreIds());
        assertEquals(
                1L,
                members.page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        var patch = new cn.iocoder.yudao.module.gym.controller.admin.member.vo.GymMemberSaveReqVO();
        patch.setName("跨店改名");
        assertServiceException(() -> members.patch(mid, patch), STORE_NO_PERMISSION);
        TenantContextHolder.setTenantId(2L);
        headquarters();
        assertServiceException(() -> cards.template(tid), CARD_TEMPLATE_NOT_EXISTS);
    }

    @Test
    void cardIssuanceRejectsDraftBlacklistedAndWrongStores() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long mid = Long.valueOf(members.create(member(101L, "13800000001")).getId());
        var req = cardTemplate("count");
        req.setAccessScope("selected");
        req.setStoreIds(List.of(101L));
        req.setStatus("draft");
        Long tid = Long.valueOf(cards.create(req).getId());
        var start = java.time.Instant.now();
        assertServiceException(
                () -> cards.issue(mid, 101L, tid, start, "approval", "a"), CARD_RIGHT_INVALID);
        var patch =
                new cn.iocoder.yudao.module.gym.controller.admin.card.vo.GymCardTemplateSaveReqVO();
        patch.setStatus("published");
        cards.patch(tid, patch);
        assertServiceException(
                () -> cards.issue(mid, 102L, tid, start, "approval", "a"), CARD_RIGHT_INVALID);
        records.createBlacklist(blacklist(mid));
        assertServiceException(
                () -> cards.issue(mid, 101L, tid, start, "approval", "a"), MEMBER_PURCHASE_BLOCKED);
        assertEquals(
                0L,
                cards.cards(
                                mid,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
    }

    @Test
    void cardExpiryFilterUsesRealRightsAndTime() {
        stores.create(store(101L, "A"));
        Long mid = Long.valueOf(members.create(member(101L, "13800000001")).getId());
        var template = cardTemplate("period");
        template.setDurationDays(5);
        Long tid = Long.valueOf(cards.create(template).getId());
        cards.issue(mid, 101L, tid, java.time.Instant.now(), "approval", "expiry");
        var req = new cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO();
        req.setCardExpiresWithinDays(7);
        assertEquals(1L, members.page(req).getTotal());
        req.setCardExpiresWithinDays(1);
        assertEquals(0L, members.page(req).getTotal());
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> cardController.create(cardTemplate("count")));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () ->
                        cardController.memberCards(
                                mid,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO()));
    }

    @Resource cn.iocoder.yudao.module.gym.service.course.GymCourseService courses;

    @Resource
    cn.iocoder.yudao.module.gym.controller.admin.course.GymCourseController courseController;

    cn.iocoder.yudao.module.gym.controller.admin.course.vo.GymCourseProductSaveReqVO courseProduct(
            Long store, Long coach) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.course.vo
                        .GymCourseProductSaveReqVO();
        req.setStoreId(store);
        req.setCoachId(coach);
        req.setName("私教产品");
        req.setSessionCount(2);
        req.setDurationMinutes(60);
        req.setPrice(new BigDecimal("200.00"));
        return req;
    }

    cn.iocoder.yudao.module.gym.controller.admin.course.vo.GymCourseSessionSaveReqVO courseSession(
            Long product, Long coach, java.time.Instant from) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.course.vo
                        .GymCourseSessionSaveReqVO();
        req.setStoreId(101L);
        req.setCourseProductId(product);
        req.setCoachId(coach);
        req.setStartsAt(from);
        req.setEndsAt(from.plusSeconds(3600));
        return req;
    }

    cn.iocoder.yudao.module.gym.controller.admin.course.vo.GymBookingSaveReqVO bookingRequest(
            Long member, Long session, Long pack) {
        var req = new cn.iocoder.yudao.module.gym.controller.admin.course.vo.GymBookingSaveReqVO();
        req.setMemberId(member);
        req.setSessionId(session);
        req.setMemberCoursePackId(pack);
        return req;
    }

    @Test
    void privateProductCoachValidationAndPriceMustUseApproval() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long coach = Long.valueOf(employees.create(employee("PC", 101L)).getId());
        var created = courses.createProduct(courseProduct(101L, coach));
        Long id = Long.valueOf(created.getId());
        assertEquals("private", created.getKind());
        assertServiceException(
                () -> courses.createProduct(courseProduct(102L, coach)), COURSE_COACH_INVALID);
        var patch =
                new cn.iocoder.yudao.module.gym.controller.admin.course.vo
                        .GymCourseProductSaveReqVO();
        patch.setPrice(BigDecimal.ONE);
        assertServiceException(
                () -> courses.patchProduct(id, patch), COURSE_PRICE_APPROVAL_REQUIRED);
        courses.applyApprovedPrice(id, new BigDecimal("200.00"), new BigDecimal("180.00"));
        assertEquals(new BigDecimal("180.00"), courses.product(id).getPrice());
        assertServiceException(
                () -> courses.applyApprovedPrice(id, new BigDecimal("200.00"), BigDecimal.TEN),
                COURSE_PRICE_APPROVAL_REQUIRED);
        assertEquals(
                1L,
                courses.coaches(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
    }

    @Test
    void privateScheduleHasUnitCapacityAndCoachConflictAcrossStores() {
        stores.create(store(101L, "A"));
        Long coach = Long.valueOf(employees.create(employee("PC", 101L)).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        var from = java.time.Instant.now().plusSeconds(7200);
        var created = courses.createSession(courseSession(product, coach, from));
        assertEquals(1, created.getCapacity());
        assertServiceException(
                () -> courses.createSession(courseSession(product, coach, from.plusSeconds(1800))),
                COURSE_SESSION_CONFLICT);
        var bad = courseSession(product, coach, from.plusSeconds(7200));
        bad.setCapacity(2);
        assertThrows(ConstraintViolationException.class, () -> courses.createSession(bad));
        var query = new cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO();
        query.setFrom(from.minusSeconds(1).toString());
        query.setTo(from.plusSeconds(3601).toString());
        assertEquals(1L, courses.sessions(query).getTotal());
        query.setKind("group");
        assertEquals(0L, courses.sessions(query).getTotal());
    }

    @Test
    void bookingReservesReplaysCancelsAndRebooksWithoutStaleCheckin() {
        stores.create(store(101L, "A"));
        Long coach = Long.valueOf(employees.create(employee("PC", 101L)).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        var from = java.time.Instant.now().plusSeconds(7200);
        Long session =
                Long.valueOf(courses.createSession(courseSession(product, coach, from)).getId());
        Long member = Long.valueOf(members.create(member(101L, "13800000001")).getId());
        var pack =
                courses.issuePack(
                        member,
                        product,
                        java.time.Instant.now().minusSeconds(60),
                        from.plusSeconds(7200),
                        "test-payment",
                        "pack-1");
        var req = bookingRequest(member, session, pack.getId());
        var created = courses.createBooking(req);
        Long bid = Long.valueOf(created.getId());
        assertEquals("confirmed", created.getStatus());
        assertEquals(created.getId(), courses.createBooking(req).getId());
        assertEquals(
                1,
                courses.allPacks(
                                member,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getFirst()
                        .getReservedSessions());
        when(businessClock.now()).thenReturn(from.minusSeconds(300));
        var checkin = courses.checkIn(bid);
        assertEquals(checkin.getId(), courses.checkIn(bid).getId());
        var cancel =
                new cn.iocoder.yudao.module.gym.controller.admin.course.vo.GymBookingSaveReqVO();
        cancel.setStatus("cancelled");
        assertEquals("cancelled", courses.patchBooking(bid, cancel).getStatus());
        courses.patchBooking(bid, cancel);
        assertEquals(
                0,
                courses.allPacks(
                                member,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getFirst()
                        .getReservedSessions());
        var rebook = courses.createBooking(req);
        assertEquals(created.getId(), rebook.getId());
        assertNull(rebook.getCheckinId());
        assertNull(rebook.getCancelledAt());
        when(businessClock.now()).thenReturn(from.plusSeconds(60));
        assertServiceException(() -> courses.confirmLesson(bid), LESSON_CHECKIN_REQUIRED);
    }

    @Test
    void bookingCapacityAndRightsFailuresDoNotReserveAnything() {
        stores.create(store(101L, "A"));
        Long coach = Long.valueOf(employees.create(employee("PC", 101L)).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        var from = java.time.Instant.now().plusSeconds(7200);
        Long session =
                Long.valueOf(courses.createSession(courseSession(product, coach, from)).getId());
        Long a = Long.valueOf(members.create(member(101L, "13800000001")).getId()),
                b = Long.valueOf(members.create(member(101L, "13800000002")).getId());
        var pack =
                courses.issuePack(
                        a,
                        product,
                        java.time.Instant.now().minusSeconds(60),
                        from.plusSeconds(7200),
                        "test-payment",
                        "pack-a");
        assertServiceException(
                () -> courses.createBooking(bookingRequest(b, session, pack.getId())),
                COURSE_PACK_INVALID);
        assertEquals(
                0,
                courses.allPacks(
                                a,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getFirst()
                        .getReservedSessions());
        courses.createBooking(bookingRequest(a, session, pack.getId()));
        var packb =
                courses.issuePack(
                        b,
                        product,
                        java.time.Instant.now().minusSeconds(60),
                        from.plusSeconds(7200),
                        "test-payment",
                        "pack-b");
        assertServiceException(
                () -> courses.createBooking(bookingRequest(b, session, packb.getId())),
                BOOKING_CAPACITY_FULL);
        assertEquals(
                0,
                courses.allPacks(
                                b,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getFirst()
                        .getReservedSessions());
    }

    @Test
    void lessonRequiresCheckinAndDeductsOnlyOnce() {
        stores.create(store(101L, "A"));
        Long coach = Long.valueOf(employees.create(employee("PC", 101L)).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        var from = java.time.Instant.now().plusSeconds(7200);
        Long session =
                Long.valueOf(courses.createSession(courseSession(product, coach, from)).getId());
        Long member = Long.valueOf(members.create(member(101L, "13800000001")).getId());
        var pack =
                courses.issuePack(
                        member,
                        product,
                        java.time.Instant.now().minusSeconds(60),
                        from.plusSeconds(7200),
                        "test-payment",
                        "pack-lesson");
        Long bid =
                Long.valueOf(
                        courses.createBooking(bookingRequest(member, session, pack.getId()))
                                .getId());
        assertServiceException(() -> courses.confirmLesson(bid), LESSON_CHECKIN_REQUIRED);
        when(businessClock.now()).thenReturn(from.minusSeconds(300));
        courses.checkIn(bid);
        assertServiceException(() -> courses.confirmLesson(bid), LESSON_TIME_INVALID);
        when(businessClock.now()).thenReturn(from.plusSeconds(60));
        var lesson = courses.confirmLesson(bid);
        assertEquals(lesson.getId(), courses.confirmLesson(bid).getId());
        var remaining =
                courses.allPacks(
                                member,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getFirst();
        assertEquals(1, remaining.getRemainingSessions());
        assertEquals(0, remaining.getReservedSessions());
        assertEquals("attended", courses.booking(bid).getStatus());
        assertEquals("completed", courses.session(session).getStatus());
        assertEquals(courses.booking(bid).getCheckinId(), courses.checkIn(bid).getId());
        assertEquals(
                1L,
                courses.lessons(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        assertEquals(
                1L,
                courses.checkinSummary(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .get("entries"));
        var cancel =
                new cn.iocoder.yudao.module.gym.controller.admin.course.vo.GymBookingSaveReqVO();
        cancel.setStatus("cancelled");
        assertServiceException(() -> courses.patchBooking(bid, cancel), BOOKING_INVALID);
        var cancelSession =
                new cn.iocoder.yudao.module.gym.controller.admin.course.vo
                        .GymCourseSessionSaveReqVO();
        cancelSession.setStatus("cancelled");
        assertServiceException(
                () -> courses.patchSession(session, cancelSession), COURSE_SESSION_INVALID);
    }

    @Test
    void existingSessionRejectsNewBookingsWhenCoachEmploymentIsNoLongerEligible() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long coach = Long.valueOf(employees.create(employee("PC-EMPLOYMENT", 101L)).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        var from = java.time.Instant.now().plusSeconds(7200);
        Long session =
                Long.valueOf(courses.createSession(courseSession(product, coach, from)).getId());
        Long member = Long.valueOf(members.create(member(101L, "13800000840")).getId());
        var pack =
                courses.issuePack(
                        member,
                        product,
                        java.time.Instant.now().minusSeconds(60),
                        from.plusSeconds(7200),
                        "test-payment",
                        "employment-booking");
        var req = bookingRequest(member, session, pack.getId());
        var patch = new GymEmployeeSaveReqVO();
        patch.setStatus("inactive");
        employees.patch(coach, patch);
        assertServiceException(() -> courses.createBooking(req), COURSE_COACH_INVALID);
        patch.setStatus("active");
        employees.patch(coach, patch);
        var relationMapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.employee.GymEmployeeStoreMapper
                                .class);
        var relation =
                relationMapper
                        .selectList(
                                new com.baomidou.mybatisplus.core.conditions.query
                                                        .LambdaQueryWrapper<
                                                cn.iocoder.yudao.module.gym.dal.dataobject.employee
                                                        .GymEmployeeStoreDO>()
                                        .eq(
                                                cn.iocoder.yudao.module.gym.dal.dataobject.employee
                                                                .GymEmployeeStoreDO
                                                        ::getEmployeeId,
                                                coach))
                        .getFirst();
        var original = relation.getJoinedOn();
        relation.setJoinedOn(
                java.time.LocalDate.now(
                                cn.iocoder.yudao.module.gym.service.common.GymDateRange.ZONE)
                        .plusDays(1));
        relationMapper.updateById(relation);
        assertServiceException(() -> courses.createBooking(req), COURSE_COACH_INVALID);
        relation.setJoinedOn(original);
        relationMapper.updateById(relation);
        relationMapper.deleteRelations(coach);
        assertServiceException(() -> courses.createBooking(req), COURSE_COACH_INVALID);
        assertEquals(0, courses.session(session).getReservedCount());
        assertEquals(
                0,
                courses.allPacks(
                                member,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getFirst()
                        .getReservedSessions());
    }

    @Test
    void managerCanCancelInactiveCoachSessionAndReleaseCoursePackReservation() {
        stores.create(store(101L, "A"));
        Long coach = Long.valueOf(employees.create(employee("PC-INACTIVE-CANCEL", 101L)).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        var from = java.time.Instant.now().plusSeconds(7200);
        Long session =
                Long.valueOf(courses.createSession(courseSession(product, coach, from)).getId());
        Long member = Long.valueOf(members.create(member(101L, "13800000841")).getId());
        var pack =
                courses.issuePack(
                        member,
                        product,
                        java.time.Instant.now().minusSeconds(60),
                        from.plusSeconds(7200),
                        "test-payment",
                        "inactive-cancel");
        Long booking =
                Long.valueOf(
                        courses.createBooking(bookingRequest(member, session, pack.getId()))
                                .getId());
        var patch = new GymEmployeeSaveReqVO();
        patch.setStatus("inactive");
        employees.patch(coach, patch);
        var cancel =
                new cn.iocoder.yudao.module.gym.controller.admin.course.vo
                        .GymCourseSessionSaveReqVO();
        cancel.setStatus("cancelled");
        assertEquals("cancelled", courses.patchSession(session, cancel).getStatus());
        assertEquals("cancelled", courses.booking(booking).getStatus());
        assertEquals(0, courses.session(session).getReservedCount());
        var restored =
                courses.allPacks(
                                member,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getFirst();
        assertEquals(0, restored.getReservedSessions());
        assertEquals(2, restored.getRemainingSessions());
    }

    @Test
    void managerCanCancelRemovedCoachAssignmentAndReleaseCardReservation() {
        stores.create(store(101L, "A"));
        Long coach = Long.valueOf(employees.create(employee("PC-REMOVED-CANCEL", 101L)).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        var from = java.time.Instant.now().plusSeconds(7200);
        Long session =
                Long.valueOf(courses.createSession(courseSession(product, coach, from)).getId());
        Long member = Long.valueOf(members.create(member(101L, "13800000842")).getId());
        var template = cardTemplate("count");
        template.setStatus("published");
        Long templateId = Long.valueOf(cards.create(template).getId());
        var card =
                cards.issue(
                        member,
                        101L,
                        templateId,
                        java.time.Instant.now().minusSeconds(60),
                        "test-payment",
                        "removed-cancel");
        var req = bookingRequest(member, session, null);
        req.setMemberCardId(card.getId());
        Long booking = Long.valueOf(courses.createBooking(req).getId());
        applicationContext
                .getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.employee.GymEmployeeStoreMapper.class)
                .deleteRelations(coach);
        var cancel =
                new cn.iocoder.yudao.module.gym.controller.admin.course.vo
                        .GymCourseSessionSaveReqVO();
        cancel.setStatus("cancelled");
        assertEquals("cancelled", courses.patchSession(session, cancel).getStatus());
        assertEquals("cancelled", courses.booking(booking).getStatus());
        assertEquals(0, courses.session(session).getReservedCount());
        var restored =
                cards.allCards(
                                member,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getFirst();
        assertEquals(0, restored.getReservedUses());
        assertEquals(card.getRemainingUses(), restored.getRemainingUses());
    }

    @Test
    void cancellingPrivateSessionReleasesReservationsAtomically() {
        stores.create(store(101L, "A"));
        Long coach = Long.valueOf(employees.create(employee("PC", 101L)).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        var from = java.time.Instant.now().plusSeconds(7200);
        Long session =
                Long.valueOf(courses.createSession(courseSession(product, coach, from)).getId());
        Long member = Long.valueOf(members.create(member(101L, "13800000001")).getId());
        var pack =
                courses.issuePack(
                        member,
                        product,
                        java.time.Instant.now().minusSeconds(60),
                        from.plusSeconds(7200),
                        "test-payment",
                        "pack-cancel");
        Long bid =
                Long.valueOf(
                        courses.createBooking(bookingRequest(member, session, pack.getId()))
                                .getId());
        var shift =
                new cn.iocoder.yudao.module.gym.controller.admin.course.vo
                        .GymCourseSessionSaveReqVO();
        shift.setStartsAt(from.plusSeconds(60));
        shift.setEndsAt(from.plusSeconds(3660));
        assertServiceException(() -> courses.patchSession(session, shift), COURSE_SESSION_INVALID);
        var cancel =
                new cn.iocoder.yudao.module.gym.controller.admin.course.vo
                        .GymCourseSessionSaveReqVO();
        cancel.setStatus("cancelled");
        courses.patchSession(session, cancel);
        assertEquals("cancelled", courses.booking(bid).getStatus());
        assertEquals(0, courses.session(session).getReservedCount());
        assertEquals(
                0,
                courses.allPacks(
                                member,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getFirst()
                        .getReservedSessions());
    }

    @Test
    void privateBookingCanConsumeCountCardWithoutNegativeBalance() {
        stores.create(store(101L, "A"));
        Long coach = Long.valueOf(employees.create(employee("PC", 101L)).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        var from = java.time.Instant.now().plusSeconds(7200);
        Long session =
                Long.valueOf(courses.createSession(courseSession(product, coach, from)).getId());
        Long member = Long.valueOf(members.create(member(101L, "13800000001")).getId());
        var template = cardTemplate("count");
        template.setIncludedUses(1);
        Long tid = Long.valueOf(cards.create(template).getId());
        var card =
                cards.issue(
                        member,
                        101L,
                        tid,
                        java.time.Instant.now().minusSeconds(60),
                        "test-payment",
                        "card-booking");
        var req = bookingRequest(member, session, null);
        req.setMemberCardId(card.getId());
        Long bid = Long.valueOf(courses.createBooking(req).getId());
        when(businessClock.now()).thenReturn(from.minusSeconds(300));
        courses.checkIn(bid);
        when(businessClock.now()).thenReturn(from.plusSeconds(60));
        courses.confirmLesson(bid);
        courses.confirmLesson(bid);
        var rights =
                cards.allCards(
                                member,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getFirst();
        assertEquals(0, rights.getRemainingUses());
        assertEquals(0, rights.getReservedUses());
        when(businessClock.now()).thenReturn(java.time.Instant.now());
        Long next =
                Long.valueOf(
                        courses.createSession(courseSession(product, coach, from.plusSeconds(7200)))
                                .getId());
        req.setSessionId(next);
        assertServiceException(() -> courses.createBooking(req), CARD_RIGHT_INSUFFICIENT);
        assertEquals(0, courses.session(next).getReservedCount());
    }

    @Test
    void coursePackFiltersAndRecordsRespectStoreScopeAndTenant() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long coach = Long.valueOf(employees.create(employee("PC", 101L)).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        Long member = Long.valueOf(members.create(member(101L, "13800000001")).getId());
        courses.issuePack(
                member,
                product,
                java.time.Instant.now().minusSeconds(60),
                java.time.Instant.now().plusSeconds(3 * 86400),
                "test-payment",
                "filtered");
        var req = new cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO();
        req.setHasPersonalTraining(true);
        assertEquals(1L, members.page(req).getTotal());
        req.setCourseExpiresWithinDays(7);
        assertEquals(1L, members.page(req).getTotal());
        req.setCourseExpiresWithinDays(1);
        assertEquals(0L, members.page(req).getTotal());
        scoped(102L);
        assertEquals(
                0L,
                courses.products(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        assertServiceException(() -> courses.product(product), STORE_NO_PERMISSION);
        TenantContextHolder.setTenantId(2L);
        headquarters();
        assertServiceException(() -> courses.product(product), COURSE_PRODUCT_NOT_EXISTS);
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> courseController.createProduct(courseProduct(101L, coach)));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> courseController.checkIn(1L));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> courseController.confirmLesson(1L));
    }

    @Test
    void bookingReservationAndInsertRollBackWhenSessionWriteFails() {
        stores.create(store(101L, "A"));
        Long coach = Long.valueOf(employees.create(employee("PC", 101L)).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        var from = java.time.Instant.now().plusSeconds(7200);
        Long session =
                Long.valueOf(courses.createSession(courseSession(product, coach, from)).getId());
        Long member = Long.valueOf(members.create(member(101L, "13800000001")).getId());
        var pack =
                courses.issuePack(
                        member,
                        product,
                        java.time.Instant.now().minusSeconds(60),
                        from.plusSeconds(7200),
                        "test-payment",
                        "rollback");
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.execute(
                "ALTER TABLE gym_course_session ADD CONSTRAINT forbid_reservation CHECK (reserved_count=0)");
        try {
            assertThrows(
                    org.springframework.dao.DataIntegrityViolationException.class,
                    () -> courses.createBooking(bookingRequest(member, session, pack.getId())));
            assertEquals(
                    0L,
                    courses.bookings(
                                    new cn.iocoder.yudao.module.gym.controller.admin.common
                                            .GymBusinessPageReqVO())
                            .getTotal());
            assertEquals(0, courses.session(session).getReservedCount());
            assertEquals(
                    0,
                    courses.allPacks(
                                    member,
                                    new cn.iocoder.yudao.module.gym.controller.admin.common
                                            .GymBusinessPageReqVO())
                            .getFirst()
                            .getReservedSessions());
        } finally {
            jdbc.execute("ALTER TABLE gym_course_session DROP CONSTRAINT forbid_reservation");
        }
    }

    @Test
    void allStoreCardAlsoGrantsNewlyOpenedStoreButSelectedCardDoesNot() {
        stores.create(store(101L, "A"));
        Long a = Long.valueOf(members.create(member(101L, "13800000001")).getId());
        Long b = Long.valueOf(members.create(member(101L, "13800000002")).getId());
        Long global = Long.valueOf(cards.create(cardTemplate("period")).getId());
        var selected = cardTemplate("period");
        selected.setAccessScope("selected");
        selected.setStoreIds(List.of(101L));
        Long sid = Long.valueOf(cards.create(selected).getId());
        cards.issue(
                a, 101L, global, java.time.Instant.now(), "test-payment", "global-future-store");
        cards.issue(b, 101L, sid, java.time.Instant.now(), "test-payment", "selected-future-store");
        stores.create(store(102L, "NEW"));
        scoped(102L);
        assertEquals(List.of("102"), members.get(a).getStoreIds());
        assertServiceException(() -> members.get(b), STORE_NO_PERMISSION);
        assertEquals(
                1L,
                members.page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
    }

    @Test
    void cardExpiryFilteringCannotRevealAnotherStoresPrivateRights() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long member = Long.valueOf(members.create(member(101L, "13800000001")).getId());
        Long global = Long.valueOf(cards.create(cardTemplate("count")).getId());
        cards.issue(
                member, 101L, global, java.time.Instant.now(), "test-payment", "membership-both");
        var selected = cardTemplate("period");
        selected.setDurationDays(3);
        selected.setAccessScope("selected");
        selected.setStoreIds(List.of(102L));
        Long tid = Long.valueOf(cards.create(selected).getId());
        cards.issue(member, 102L, tid, java.time.Instant.now(), "test-payment", "private-expiry");
        scoped(101L);
        var req = new cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO();
        req.setCardExpiresWithinDays(7);
        assertEquals(0L, members.page(req).getTotal());
        scoped(102L);
        assertEquals(1L, members.page(req).getTotal());
    }

    @Test
    void openingStoreWhileBookingHoldsMemberLockDoesNotDeadlock() throws Exception {
        stores.create(store(101L, "A"));
        Long coach = Long.valueOf(employees.create(employee("PC", 101L)).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        var from = java.time.Instant.now().plusSeconds(7200);
        Long session =
                Long.valueOf(courses.createSession(courseSession(product, coach, from)).getId());
        Long mid = Long.valueOf(members.create(member(101L, "13800000001")).getId());
        var template = cardTemplate("count");
        template.setIncludedUses(1);
        Long tid = Long.valueOf(cards.create(template).getId());
        var card =
                cards.issue(
                        mid,
                        101L,
                        tid,
                        java.time.Instant.now().minusSeconds(60),
                        "test-payment",
                        "concurrent-store");
        var req = bookingRequest(mid, session, null);
        req.setMemberCardId(card.getId());
        var held = new java.util.concurrent.CountDownLatch(1);
        var started = new java.util.concurrent.CountDownLatch(1);
        var openingThread = new java.util.concurrent.atomic.AtomicReference<Thread>();
        var openingFinished = new java.util.concurrent.atomic.AtomicBoolean(false);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var booking =
                    executor.submit(
                            () -> {
                                setupThreadContext();
                                try {
                                    var tx =
                                            new org.springframework.transaction.support
                                                    .TransactionTemplate(
                                                    applicationContext.getBean(
                                                            org.springframework.transaction
                                                                    .PlatformTransactionManager
                                                                    .class));
                                    return tx.execute(
                                            status -> {
                                                applicationContext
                                                        .getBean(
                                                                cn.iocoder.yudao.module.gym.dal
                                                                        .mysql.member
                                                                        .GymMemberMapper.class)
                                                        .selectForUpdate(mid);
                                                held.countDown();
                                                try {
                                                    assertTrue(
                                                            started.await(
                                                                    3,
                                                                    java.util.concurrent.TimeUnit
                                                                            .SECONDS));
                                                    long deadline =
                                                            System.nanoTime()
                                                                    + java.util.concurrent.TimeUnit
                                                                            .SECONDS
                                                                            .toNanos(3);
                                                    while (System.nanoTime() < deadline) {
                                                        Thread thread = openingThread.get();
                                                        if (thread != null
                                                                && (thread.getState()
                                                                                == Thread.State
                                                                                        .TIMED_WAITING
                                                                        || thread.getState()
                                                                                == Thread.State
                                                                                        .WAITING))
                                                            break;
                                                        java.util.concurrent.locks.LockSupport
                                                                .parkNanos(
                                                                        java.util.concurrent
                                                                                .TimeUnit
                                                                                .MILLISECONDS
                                                                                .toNanos(5));
                                                    }
                                                    assertFalse(
                                                            openingFinished.get(),
                                                            "new store must wait for the member transaction before extending its scope");
                                                    return courses.createBooking(req);
                                                } catch (InterruptedException e) {
                                                    Thread.currentThread().interrupt();
                                                    throw new RuntimeException(e);
                                                }
                                            });
                                } finally {
                                    TenantContextHolder.clear();
                                    org.springframework.security.core.context.SecurityContextHolder
                                            .clearContext();
                                }
                            });
            var opening =
                    executor.submit(
                            () -> {
                                setupThreadContext();
                                try {
                                    assertTrue(
                                            held.await(3, java.util.concurrent.TimeUnit.SECONDS));
                                    openingThread.set(Thread.currentThread());
                                    started.countDown();
                                    return stores.create(store(102L, "NEW"));
                                } finally {
                                    openingFinished.set(true);
                                    TenantContextHolder.clear();
                                    org.springframework.security.core.context.SecurityContextHolder
                                            .clearContext();
                                }
                            });
            assertEquals(
                    "confirmed",
                    booking.get(10, java.util.concurrent.TimeUnit.SECONDS).getStatus());
            assertEquals("102", opening.get(10, java.util.concurrent.TimeUnit.SECONDS).getId());
            assertTrue(members.get(mid).getStoreIds().contains("102"));
        } finally {
            executor.shutdownNow();
        }
    }

    private void setupThreadContext() {
        TenantContextHolder.setTenantId(1L);
        var login = new cn.iocoder.yudao.framework.security.core.LoginUser();
        login.setId(1L);
        login.setUserType(2);
        login.setTenantId(1L);
        cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.setLoginUser(
                login, new org.springframework.mock.web.MockHttpServletRequest());
    }

    @Test
    void privateProductTimeFilterAndDisablingDepartedCoachProduct() {
        stores.create(store(101L, "A"));
        Long coach = Long.valueOf(employees.create(employee("PC", 101L)).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        var query = new cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO();
        query.setDate(LocalDate.now(cn.iocoder.yudao.module.gym.service.common.GymDateRange.ZONE));
        assertEquals(1L, courses.products(query).getTotal());
        query.setDate(LocalDate.of(2020, 1, 1));
        assertEquals(0L, courses.products(query).getTotal());
        var departure = new GymEmployeeSaveReqVO();
        departure.setStatus("inactive");
        employees.patch(coach, departure);
        var disable =
                new cn.iocoder.yudao.module.gym.controller.admin.course.vo
                        .GymCourseProductSaveReqVO();
        disable.setStatus("inactive");
        assertEquals("inactive", courses.patchProduct(product, disable).getStatus());
        disable.setStatus("active");
        assertServiceException(() -> courses.patchProduct(product, disable), COURSE_COACH_INVALID);
    }

    @Test
    void inactiveProductCreationStillValidatesCoachStoreAssociation() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long coach = Long.valueOf(employees.create(employee("PB", 102L)).getId());
        scoped(101L);
        var req = courseProduct(101L, coach);
        req.setStatus("inactive");
        assertServiceException(() -> courses.createProduct(req), COURSE_COACH_INVALID);
        assertEquals(
                0L,
                courses.products(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
    }

    @Resource
    cn.iocoder.yudao.module.gym.service.approval.GymApprovalTemplateService approvalTemplates;

    @Resource
    cn.iocoder.yudao.module.gym.controller.admin.approval.GymApprovalTemplateController
            approvalTemplateController;

    cn.iocoder.yudao.module.gym.controller.admin.approval.vo.GymApprovalTemplateSaveReqVO
            approvalTemplate(Long employee) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.approval.vo
                        .GymApprovalTemplateSaveReqVO();
        req.setName("开卡审批模板");
        req.setBusinessType("card_opening");
        var step =
                new cn.iocoder.yudao.module.gym.controller.admin.approval.vo
                        .GymApprovalTemplateSaveReqVO.Step();
        step.setStepNo(1);
        step.setApproverEmployeeId(employee);
        req.setSteps(List.of(step));
        return req;
    }

    @Test
    void approvalPublishingRequiresLiveLinkedAccountAndFreezesSteps() {
        stores.create(store(101L, "A"));
        Long eid = Long.valueOf(employees.create(employee("APPROVER", 101L)).getId());
        Long id = Long.valueOf(approvalTemplates.create(approvalTemplate(eid)).getId());
        var publish =
                new cn.iocoder.yudao.module.gym.controller.admin.approval.vo
                        .GymApprovalTemplateSaveReqVO();
        publish.setStatus("published");
        assertServiceException(
                () -> approvalTemplates.patch(id, publish), APPROVAL_APPROVER_INVALID);
        var mapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.employee.GymEmployeeMapper.class);
        var emp = mapper.selectById(eid);
        emp.setUserId(91L);
        mapper.updateById(emp);
        var user = new AdminUserDO();
        user.setId(91L);
        user.setStatus(0);
        when(users.getUser(91L)).thenReturn(user);
        assertServiceException(
                () -> approvalTemplates.patch(id, publish), APPROVAL_APPROVER_INVALID);
        when(permissions.hasAnyPermissions(eq(91L), eq("gym:approvals:decide"))).thenReturn(true);
        approvalTemplates.patch(id, publish);
        assertEquals(1, approvalTemplates.options().size());
        var patch =
                new cn.iocoder.yudao.module.gym.controller.admin.approval.vo
                        .GymApprovalTemplateSaveReqVO();
        patch.setName("篡改");
        assertServiceException(
                () -> approvalTemplates.patch(id, patch), APPROVAL_TEMPLATE_IMMUTABLE);
        patch.setName(null);
        patch.setStatus("draft");
        assertServiceException(
                () -> approvalTemplates.patch(id, patch), APPROVAL_TEMPLATE_IMMUTABLE);
        var clone =
                approvalTemplates.version(
                        id,
                        new cn.iocoder.yudao.module.gym.controller.admin.approval.vo
                                .GymApprovalTemplateSaveReqVO());
        assertEquals(2, clone.getVersion());
        assertEquals("draft", clone.getStatus());
        var disable =
                new cn.iocoder.yudao.module.gym.controller.admin.approval.vo
                        .GymApprovalTemplateSaveReqVO();
        disable.setStatus("inactive");
        approvalTemplates.patch(id, disable);
        assertTrue(approvalTemplates.options().isEmpty());
    }

    @Test
    void approvalTemplateStepsRejectDuplicateAndGapsBeforeSaving() {
        stores.create(store(101L, "A"));
        Long eid = Long.valueOf(employees.create(employee("APPROVER", 101L)).getId());
        var req = approvalTemplate(eid);
        req.getSteps().getFirst().setStepNo(2);
        assertServiceException(() -> approvalTemplates.create(req), APPROVAL_STEPS_INVALID);
        req.getSteps().getFirst().setStepNo(1);
        var second =
                new cn.iocoder.yudao.module.gym.controller.admin.approval.vo
                        .GymApprovalTemplateSaveReqVO.Step();
        second.setStepNo(2);
        second.setApproverEmployeeId(eid);
        req.setSteps(List.of(req.getSteps().getFirst(), second));
        assertServiceException(() -> approvalTemplates.create(req), APPROVAL_STEPS_INVALID);
        assertEquals(
                0L,
                approvalTemplates
                        .page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
    }

    @Test
    void approvalTemplatesEnforceHeadquartersTenantAndMethodPermission() {
        stores.create(store(101L, "A"));
        Long eid = Long.valueOf(employees.create(employee("APPROVER", 101L)).getId());
        Long id = Long.valueOf(approvalTemplates.create(approvalTemplate(eid)).getId());
        scoped(101L);
        assertServiceException(() -> approvalTemplates.get(id), STORE_NO_PERMISSION);
        assertTrue(approvalTemplates.options().isEmpty());
        assertServiceException(
                () -> approvalTemplates.create(approvalTemplate(eid)), STORE_NO_PERMISSION);
        headquarters();
        TenantContextHolder.setTenantId(2L);
        assertServiceException(() -> approvalTemplates.get(id), APPROVAL_TEMPLATE_NOT_EXISTS);
        TenantContextHolder.setTenantId(1L);
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> approvalTemplateController.get(id));
    }

    @Resource cn.iocoder.yudao.module.gym.service.approval.GymApprovalService approvals;
    @Resource cn.iocoder.yudao.module.gym.dal.mysql.approval.GymApprovalTaskMapper approvalTasks;

    Long approvalEmployee(String no, Long user) {
        Long eid = Long.valueOf(employees.create(employee(no, 101L)).getId());
        var mapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.employee.GymEmployeeMapper.class);
        var row = mapper.selectById(eid);
        row.setUserId(user);
        mapper.updateById(row);
        var account = new AdminUserDO();
        account.setId(user);
        account.setTenantId(TenantContextHolder.getRequiredTenantId());
        account.setStatus(0);
        account.setNickname(no);
        when(users.getUser(user)).thenReturn(account);
        when(permissions.hasAnyPermissions(eq(user), eq("gym:approvals:decide"))).thenReturn(true);
        return eid;
    }

    Long publishedApproval(String type, Long... employeeIds) {
        var req = approvalTemplate(employeeIds[0]);
        req.setBusinessType(type);
        req.setStatus("published");
        var steps =
                new ArrayList<
                        cn.iocoder.yudao.module.gym.controller.admin.approval.vo
                                .GymApprovalTemplateSaveReqVO.Step>();
        for (int i = 0; i < employeeIds.length; i++) {
            var step =
                    new cn.iocoder.yudao.module.gym.controller.admin.approval.vo
                            .GymApprovalTemplateSaveReqVO.Step();
            step.setStepNo(i + 1);
            step.setApproverEmployeeId(employeeIds[i]);
            steps.add(step);
        }
        req.setSteps(steps);
        return Long.valueOf(approvalTemplates.create(req).getId());
    }

    void loginAs(Long user) {
        var login = new cn.iocoder.yudao.framework.security.core.LoginUser();
        login.setId(user);
        login.setUserType(2);
        login.setTenantId(1L);
        cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.setLoginUser(
                login, new org.springframework.mock.web.MockHttpServletRequest());
    }

    cn.iocoder.yudao.module.gym.controller.admin.approval.vo.GymApprovalDecisionReqVO decision(
            String value) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.approval.vo
                        .GymApprovalDecisionReqVO();
        req.setDecision(value);
        return req;
    }

    cn.iocoder.yudao.module.gym.controller.admin.approval.vo.GymApprovalCreateReqVO opening(
            Long template, Long member, Long card) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.approval.vo
                        .GymApprovalCreateReqVO();
        req.setStoreId(101L);
        req.setTemplateId(template);
        req.setBusinessType("card_opening");
        req.setTitle("开卡申请");
        req.setMemberId(member);
        req.setCardTemplateId(card);
        return req;
    }

    @Test
    void approvalFinalStepIssuesOnceWithImmutableAccountSnapshots() {
        stores.create(store(101L, "A"));
        Long first = approvalEmployee("FIRST", 91L), second = approvalEmployee("SECOND", 92L);
        Long template = publishedApproval("card_opening", first, second);
        Long member = Long.valueOf(members.create(member(101L, "13800000511")).getId());
        Long card = Long.valueOf(cards.create(cardTemplate("count")).getId());
        var req = opening(template, member, card);
        var created = approvals.create(req, "opening-1");
        Long id = Long.valueOf(created.getId());
        assertFalse(created.getCanDecide());
        assertEquals(created.getId(), approvals.create(req, "opening-1").getId());
        req.setTitle("不同申请");
        assertServiceException(
                () -> approvals.create(req, "opening-1"), APPROVAL_IDEMPOTENCY_CONFLICT);
        assertServiceException(
                () -> approvals.decide(id, decision("approve")), APPROVAL_DECISION_FORBIDDEN);
        loginAs(92L);
        assertServiceException(
                () -> approvals.decide(id, decision("approve")), APPROVAL_DECISION_INVALID);
        loginAs(91L);
        assertTrue(approvals.get(id).getCanDecide());
        var middle = approvals.decide(id, decision("approve"));
        assertEquals("pending", middle.getStatus());
        assertEquals(2, middle.getCurrentStep());
        assertEquals(
                0L,
                cards.cards(
                                member,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        approvals.decide(id, decision("approve"));
        assertServiceException(
                () -> approvals.decide(id, decision("reject")), APPROVAL_DECISION_INVALID);
        loginAs(92L);
        var done = approvals.decide(id, decision("approve"));
        assertEquals("approved", done.getStatus());
        assertNotNull(done.getBusinessResult().get("memberCardId"));
        approvals.decide(id, decision("approve"));
        assertEquals(
                1L,
                cards.cards(
                                member,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        assertTrue(done.getTasks().stream().allMatch(t -> t.getStatus().equals("approved")));
        scoped(202L);
        assertServiceException(() -> approvals.get(id), STORE_NO_PERMISSION);
        headquarters();
        TenantContextHolder.setTenantId(2L);
        assertServiceException(() -> approvals.get(id), APPROVAL_NOT_EXISTS);
    }

    @Test
    void failedApprovalBusinessEffectRollsBackDecisionAndCanRetry() {
        stores.create(store(101L, "A"));
        Long approver = approvalEmployee("FIRST", 91L);
        Long template = publishedApproval("card_opening", approver);
        Long member = Long.valueOf(members.create(member(101L, "13800000512")).getId()),
                card = Long.valueOf(cards.create(cardTemplate("count")).getId());
        Long id =
                Long.valueOf(
                        approvals.create(opening(template, member, card), "rollback-1").getId());
        var blacklist = blacklist(member);
        blacklist.setBlockPurchase(true);
        blacklist.setStartsAt(java.time.Instant.now().minusSeconds(30));
        var blocked = records.createBlacklist(blacklist);
        loginAs(91L);
        assertServiceException(
                () -> approvals.decide(id, decision("approve")), MEMBER_PURCHASE_BLOCKED);
        assertEquals("pending", approvals.get(id).getStatus());
        assertEquals("pending", approvals.get(id).getTasks().getFirst().getStatus());
        assertNull(approvals.get(id).getTasks().getFirst().getDecidedAt());
        var revoke =
                new cn.iocoder.yudao.module.gym.controller.admin.member.vo.GymBlacklistSaveReqVO();
        revoke.setRevoked(true);
        records.patchBlacklist(Long.valueOf(blocked.getId()), revoke);
        assertEquals("approved", approvals.decide(id, decision("approve")).getStatus());
    }

    @Test
    void approvalRejectionCancelsFutureTasksWithoutIssuingRights() {
        stores.create(store(101L, "A"));
        Long a = approvalEmployee("FIRST", 91L), b = approvalEmployee("SECOND", 92L);
        Long template = publishedApproval("card_opening", a, b),
                member = Long.valueOf(members.create(member(101L, "13800000513")).getId()),
                card = Long.valueOf(cards.create(cardTemplate("count")).getId());
        Long id =
                Long.valueOf(approvals.create(opening(template, member, card), "reject-1").getId());
        loginAs(91L);
        var rejected = approvals.decide(id, decision("reject"));
        assertEquals("rejected", rejected.getStatus());
        assertEquals("cancelled", rejected.getTasks().get(1).getStatus());
        approvals.decide(id, decision("reject"));
        loginAs(92L);
        assertServiceException(
                () -> approvals.decide(id, decision("approve")), APPROVAL_DECISION_INVALID);
        assertEquals(
                0L,
                cards.cards(
                                member,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
    }

    @Test
    void approvedPriceUsesOriginalSnapshotAndStaleApprovalStaysPending() {
        stores.create(store(101L, "A"));
        Long approver = approvalEmployee("FIRST", 91L),
                template = publishedApproval("price_change", approver);
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, approver)).getId());
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.approval.vo
                        .GymApprovalCreateReqVO();
        req.setStoreId(101L);
        req.setTemplateId(template);
        req.setBusinessType("price_change");
        req.setTitle("改价申请");
        req.setSourceType("course-products");
        req.setSourceId(product);
        req.setRequestedPrice(new BigDecimal("150.00"));
        Long a = Long.valueOf(approvals.create(req, "price-a").getId());
        req.setRequestedPrice(new BigDecimal("100.00"));
        Long b = Long.valueOf(approvals.create(req, "price-b").getId());
        loginAs(91L);
        assertEquals("approved", approvals.decide(a, decision("approve")).getStatus());
        assertEquals(new BigDecimal("150.00"), courses.product(product).getPrice());
        assertServiceException(
                () -> approvals.decide(b, decision("approve")), COURSE_PRICE_APPROVAL_REQUIRED);
        assertEquals("pending", approvals.get(b).getTasks().getFirst().getStatus());
        assertEquals("pending", approvals.get(b).getStatus());
        assertEquals("rejected", approvals.decide(b, decision("reject")).getStatus());
        assertEquals(new BigDecimal("150.00"), courses.product(product).getPrice());
    }

    @Test
    void approvalAttachmentScopeAndAccountRebindingCannotTransferAuditAuthority() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long employee = approvalEmployee("FIRST", 91L),
                template = publishedApproval("card_opening", employee);
        Long member = Long.valueOf(members.create(member(101L, "13800000514")).getId()),
                card = Long.valueOf(cards.create(cardTemplate("count")).getId());
        var req = opening(template, member, card);
        var foreign = file(102L, "approval");
        req.setAttachmentIds(List.of(foreign.getId()));
        assertServiceException(() -> approvals.create(req, "file-a"), APPROVAL_ATTACHMENT_INVALID);
        var correct = file(101L, "approval");
        req.setAttachmentIds(List.of(correct.getId()));
        Long id = Long.valueOf(approvals.create(req, "file-a").getId());
        assertEquals(
                correct.getName(), approvals.get(id).getAttachments().getFirst().getOriginalName());
        var mapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.employee.GymEmployeeMapper.class);
        var emp = mapper.selectById(employee);
        emp.setUserId(93L);
        mapper.updateById(emp);
        loginAs(93L);
        assertServiceException(
                () -> approvals.decide(id, decision("approve")), APPROVAL_DECISION_FORBIDDEN);
        loginAs(91L);
        assertServiceException(
                () -> approvals.decide(id, decision("approve")), APPROVAL_APPROVER_INVALID);
        assertEquals("pending", approvals.get(id).getStatus());
    }

    @Resource
    cn.iocoder.yudao.module.gym.service.contract.GymContractTemplateService contractTemplates;

    @Resource
    cn.iocoder.yudao.module.gym.controller.admin.contract.GymContractTemplateController
            contractTemplateController;

    @Resource
    cn.iocoder.yudao.module.gym.dal.mysql.contract.GymContractTemplateMapper contractTemplateMapper;

    Long contractPdf(Long store) {
        var file = file(store, "contract");
        file.setMimeType("application/pdf");
        file.setName("测试合同.pdf");
        files.updateById(file);
        return file.getId();
    }

    cn.iocoder.yudao.module.gym.controller.admin.contract.vo.GymContractTemplateSaveReqVO
            contractTemplate(Long file) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.contract.vo
                        .GymContractTemplateSaveReqVO();
        req.setName("会员合同");
        req.setFileId(file);
        req.setApplicableType("card");
        req.setReminderDays(3);
        return req;
    }

    @Test
    void contractPublishedPdfAndConfigurationAreFrozenAndVersionDraftCanReplace() {
        Long pdf = contractPdf(101L),
                replacement = contractPdf(101L),
                id = Long.valueOf(contractTemplates.create(contractTemplate(pdf)).getId());
        var publish =
                new cn.iocoder.yudao.module.gym.controller.admin.contract.vo
                        .GymContractTemplateSaveReqVO();
        publish.setStatus("published");
        contractTemplates.patch(id, publish);
        assertTrue(contractTemplateMapper.isPublishedFile(pdf));
        var patch =
                new cn.iocoder.yudao.module.gym.controller.admin.contract.vo
                        .GymContractTemplateSaveReqVO();
        patch.setFileId(replacement);
        assertServiceException(
                () -> contractTemplates.patch(id, patch), CONTRACT_TEMPLATE_IMMUTABLE);
        patch.setFileId(null);
        patch.setStatus("draft");
        assertServiceException(
                () -> contractTemplates.patch(id, patch), CONTRACT_TEMPLATE_IMMUTABLE);
        var clone =
                contractTemplates.version(
                        id,
                        new cn.iocoder.yudao.module.gym.controller.admin.contract.vo
                                .GymContractTemplateSaveReqVO());
        assertEquals(2, clone.getVersion());
        assertEquals(pdf.toString(), clone.getFileId());
        patch.setStatus(null);
        patch.setFileId(replacement);
        contractTemplates.patch(Long.valueOf(clone.getId()), patch);
        assertFalse(contractTemplateMapper.isPublishedFile(replacement));
        assertEquals(pdf.toString(), contractTemplates.get(id).getFileId());
        patch.setFileId(null);
        patch.setStatus("inactive");
        contractTemplates.patch(id, patch);
        assertFalse(contractTemplateMapper.isPublishedFile(pdf));
    }

    @Test
    void contractTemplatesRejectNonPdfForeignCategoryAndTenantReferences() {
        Long image = file(101L, "contract").getId();
        assertServiceException(
                () -> contractTemplates.create(contractTemplate(image)),
                CONTRACT_TEMPLATE_FILE_INVALID);
        Long wrong = contractPdf(101L);
        var file = files.selectById(wrong);
        file.setCategory("approval");
        files.updateById(file);
        assertServiceException(
                () -> contractTemplates.create(contractTemplate(wrong)),
                CONTRACT_TEMPLATE_FILE_INVALID);
        Long pdf = contractPdf(101L);
        TenantContextHolder.setTenantId(2L);
        assertServiceException(
                () -> contractTemplates.create(contractTemplate(pdf)),
                CONTRACT_TEMPLATE_FILE_INVALID);
    }

    @Test
    void contractTemplateSelectorsHideDraftAndEnforceHeadquartersAndPermission() {
        Long pdf = contractPdf(101L),
                id = Long.valueOf(contractTemplates.create(contractTemplate(pdf)).getId());
        scoped(102L);
        assertServiceException(() -> contractTemplates.get(id), STORE_NO_PERMISSION);
        assertServiceException(
                () -> contractTemplates.create(contractTemplate(pdf)), STORE_NO_PERMISSION);
        assertEquals(
                0L,
                contractTemplates
                        .page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        headquarters();
        var patch =
                new cn.iocoder.yudao.module.gym.controller.admin.contract.vo
                        .GymContractTemplateSaveReqVO();
        patch.setStatus("published");
        contractTemplates.patch(id, patch);
        scoped(102L);
        assertEquals(
                1L,
                contractTemplates
                        .page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        scoped();
        assertEquals(
                0L,
                contractTemplates
                        .page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        headquarters();
        TenantContextHolder.setTenantId(2L);
        assertFalse(contractTemplateMapper.isPublishedFile(pdf));
        assertServiceException(() -> contractTemplates.get(id), CONTRACT_TEMPLATE_NOT_EXISTS);
        TenantContextHolder.setTenantId(1L);
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> contractTemplateController.get(id));
    }

    @Resource cn.iocoder.yudao.module.gym.service.contract.GymContractService contracts;
    @Resource cn.iocoder.yudao.module.gym.dal.mysql.contract.GymContractMapper contractMapper;

    Long publishedContractPdf() {
        Long pdf = contractPdf(101L);
        var file = files.selectById(pdf);
        file.setUrl("private-template-" + pdf);
        files.updateById(file);
        when(fileApi.getFileContentByUrl(anyString()))
                .thenReturn(
                        "%PDF-1.4\nlocal-test\n%%EOF"
                                .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        when(fileApi.createFile(
                        any(byte[].class), anyString(), eq("gym/contract"), eq("application/pdf")))
                .thenAnswer(a -> "private-instance-" + a.getArgument(1));
        var req = contractTemplate(pdf);
        req.setStatus("published");
        return Long.valueOf(contractTemplates.create(req).getId());
    }

    @Test
    void cardIssuanceCreatesIndependentContractOnceAndTemplateDisableDoesNotInvalidateIt() {
        stores.create(store(101L, "A"));
        Long template = publishedContractPdf();
        Long member = Long.valueOf(members.create(member(101L, "13800000701")).getId());
        Long cardTemplate = Long.valueOf(cards.create(cardTemplate("count")).getId());
        var start = java.time.Instant.now();
        var card = cards.issue(member, 101L, cardTemplate, start, "approval", "contract-source-1");
        var page =
                contracts.page(
                        new cn.iocoder.yudao.module.gym.controller.admin.common
                                .GymBusinessPageReqVO());
        assertEquals(1L, page.getTotal());
        var contract = page.getList().getFirst();
        assertEquals("unsigned", contract.getStatus());
        assertInstanceOf(
                String.class,
                ((Map<?, ?>) contract.getBusinessSnapshot().get("entitlement")).get("id"));
        assertNotEquals(contractTemplates.get(template).getFileId(), contract.getUnsignedFileId());
        assertEquals(
                101L, files.selectById(Long.valueOf(contract.getUnsignedFileId())).getStoreId());
        assertEquals("member-cards", contract.getSourceType());
        assertEquals(card.getId().toString(), contract.getSourceId());
        cards.issue(member, 101L, cardTemplate, start, "approval", "contract-source-1");
        assertEquals(
                1L,
                contracts
                        .page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        var disable =
                new cn.iocoder.yudao.module.gym.controller.admin.contract.vo
                        .GymContractTemplateSaveReqVO();
        disable.setStatus("inactive");
        contractTemplates.patch(template, disable);
        assertEquals(
                contract.getUnsignedFileId(),
                contracts.get(Long.valueOf(contract.getId())).getUnsignedFileId());
        scoped(102L);
        assertServiceException(
                () -> contracts.get(Long.valueOf(contract.getId())), STORE_NO_PERMISSION);
        headquarters();
        TenantContextHolder.setTenantId(2L);
        assertServiceException(
                () -> contracts.get(Long.valueOf(contract.getId())), CONTRACT_NOT_EXISTS);
    }

    @Test
    void missingConfiguredContractPdfRollsBackCardAndApprovalEffect() {
        stores.create(store(101L, "A"));
        publishedContractPdf();
        Long approver = approvalEmployee("FIRST", 91L),
                approval = publishedApproval("card_opening", approver);
        Long member = Long.valueOf(members.create(member(101L, "13800000702")).getId()),
                cardTemplate = Long.valueOf(cards.create(cardTemplate("count")).getId());
        Long request =
                Long.valueOf(
                        approvals
                                .create(opening(approval, member, cardTemplate), "missing-pdf")
                                .getId());
        when(fileApi.getFileContentByUrl(anyString())).thenReturn(null);
        loginAs(91L);
        assertServiceException(
                () -> approvals.decide(request, decision("approve")), FILE_CONTENT_NOT_EXISTS);
        assertEquals("pending", approvals.get(request).getStatus());
        assertEquals(
                0L,
                cards.cards(
                                member,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        assertEquals(
                0L,
                contracts
                        .page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
    }

    @Test
    void signingApprovalSavesAuthorizationWithoutInventingSignatureOrProviderReceipt() {
        stores.create(store(101L, "A"));
        publishedContractPdf();
        Long approver = approvalEmployee("FIRST", 91L),
                template = publishedApproval("contract", approver);
        Long member = Long.valueOf(members.create(member(101L, "13800000703")).getId()),
                cardTemplate = Long.valueOf(cards.create(cardTemplate("count")).getId());
        cards.issue(
                member,
                101L,
                cardTemplate,
                java.time.Instant.now(),
                "approval",
                "contract-approval-1");
        var contract =
                contracts
                        .page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getList()
                        .getFirst();
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.approval.vo
                        .GymApprovalCreateReqVO();
        req.setStoreId(101L);
        req.setTemplateId(template);
        req.setBusinessType("contract");
        req.setTitle("签约审批");
        req.setSourceType("contracts");
        req.setSourceId(Long.valueOf(contract.getId()));
        assertServiceException(
                () -> approvals.create(req, "contract-approval"), APPROVAL_REQUEST_INVALID);
        req.setAttachmentIds(List.of(Long.valueOf(contract.getUnsignedFileId())));
        Long id = Long.valueOf(approvals.create(req, "contract-approval").getId());
        loginAs(91L);
        var approved = approvals.decide(id, decision("approve"));
        assertEquals("approved", approved.getStatus());
        assertEquals("unconfigured", approved.getBusinessResult().get("signingStatus"));
        var record = contracts.get(Long.valueOf(contract.getId()));
        assertEquals("approved", record.getApprovalStatus());
        assertEquals("unsigned", record.getStatus());
        assertNull(record.getSignedAt());
        assertNull(record.getSignedFileId());
        assertNull(record.getSignIp());
        assertTrue(
                record.getEvents().stream().anyMatch(e -> e.getType().equals("signing_approved")));
        approvals.decide(id, decision("approve"));
        assertEquals(2, contracts.get(Long.valueOf(contract.getId())).getEvents().size());
    }

    @Test
    void unconfiguredReminderIsPersistedFailedAndKeyConflictCannotOverwriteIt() {
        stores.create(store(101L, "A"));
        publishedContractPdf();
        Long member = Long.valueOf(members.create(member(101L, "13800000704")).getId()),
                template = Long.valueOf(cards.create(cardTemplate("count")).getId());
        cards.issue(member, 101L, template, java.time.Instant.now(), "approval", "reminder-1");
        Long id =
                Long.valueOf(
                        contracts
                                .page(
                                        new cn.iocoder.yudao.module.gym.controller.admin.common
                                                .GymBusinessPageReqVO())
                                .getList()
                                .getFirst()
                                .getId());
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.contract.vo
                        .GymContractReminderReqVO();
        req.setChannel("sms");
        var failed = contracts.remind(id, req, "remind-1");
        assertEquals("failed", failed.getStatus());
        assertTrue(failed.getFailureReason().contains("未发送"));
        assertEquals(failed.getId(), contracts.remind(id, req, "remind-1").getId());
        req.setChannel("app");
        assertServiceException(
                () -> contracts.remind(id, req, "remind-1"), CONTRACT_SOURCE_CONFLICT);
        assertEquals(
                1L,
                contracts
                        .reminders(
                                id,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        scoped(102L);
        assertServiceException(() -> contracts.remind(id, req, "other"), STORE_NO_PERMISSION);
    }

    @Test
    void courseEntitlementCreatesMatchingCourseContractAndCardNumberFitsLongestStoreCode() {
        var storeReq = store(101L, "A".repeat(64));
        storeReq.setName("长编码门店");
        stores.create(storeReq);
        Long published = publishedContractPdf();
        var version =
                new cn.iocoder.yudao.module.gym.controller.admin.contract.vo
                        .GymContractTemplateSaveReqVO();
        version.setApplicableType("course");
        Long courseTemplate = Long.valueOf(contractTemplates.version(published, version).getId());
        version.setApplicableType(null);
        version.setStatus("published");
        contractTemplates.patch(courseTemplate, version);
        Long coach = Long.valueOf(employees.create(employee("COURSE-CONTRACT", 101L)).getId()),
                product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        Long member = Long.valueOf(members.create(member(101L, "13800000705")).getId());
        var start = java.time.Instant.now();
        var pack =
                courses.issuePack(
                        member,
                        product,
                        start,
                        start.plusSeconds(86400),
                        "test-payment",
                        "course-contract-1");
        var records =
                contracts.page(
                        new cn.iocoder.yudao.module.gym.controller.admin.common
                                .GymBusinessPageReqVO());
        assertEquals(1L, records.getTotal());
        assertEquals("course-packs", records.getList().getFirst().getSourceType());
        assertEquals(courseTemplate.toString(), records.getList().getFirst().getTemplateId());
        var card =
                cards.issue(
                        member,
                        101L,
                        Long.valueOf(cards.create(cardTemplate("count")).getId()),
                        start,
                        "approval",
                        "long-code-card");
        assertTrue(card.getCardNo().length() <= 64);
        assertEquals(
                2L,
                contracts
                        .page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        var unicodeStore = store(102L, "😀".repeat(32));
        unicodeStore.setName("Unicode门店");
        stores.create(unicodeStore);
        var unicodeMember = members.create(member(102L, "13800000706"));
        assertTrue(
                java.nio.charset.StandardCharsets.UTF_8
                        .newEncoder()
                        .canEncode(unicodeMember.getMemberNo()));
        assertTrue(unicodeMember.getMemberNo().length() <= 64);
        var unicodeCard =
                cards.issue(
                        Long.valueOf(unicodeMember.getId()),
                        102L,
                        Long.valueOf(cards.create(cardTemplate("count")).getId()),
                        start,
                        "approval",
                        "unicode-code-card");
        assertTrue(
                java.nio.charset.StandardCharsets.UTF_8
                        .newEncoder()
                        .canEncode(unicodeCard.getCardNo()));
        assertTrue(unicodeCard.getCardNo().length() <= 64);
    }

    @Resource cn.iocoder.yudao.module.gym.service.payroll.GymPayrollService payrolls;

    @Resource
    cn.iocoder.yudao.module.gym.controller.admin.payroll.GymPayrollController payrollController;

    @Resource cn.iocoder.yudao.module.gym.dal.mysql.finance.GymFinancialEventMapper financialEvents;

    cn.iocoder.yudao.module.gym.controller.admin.payroll.vo.GymPayrollCalculateReqVO payrollRequest(
            Long store) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.payroll.vo
                        .GymPayrollCalculateReqVO();
        req.setStoreId(store);
        req.setMonth("2026-06");
        return req;
    }

    Long payrollTemplate() {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                        .GymSalaryTemplateSaveReqVO();
        req.setName("工资全量规则");
        req.setBaseSalary(new BigDecimal("5000.00"));
        req.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        var rules =
                new ArrayList<
                        cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                                .GymSalaryTemplateSaveReqVO.Rule>();
        var first =
                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                        .GymSalaryTemplateSaveReqVO.Rule();
        first.setComponent("card_sales");
        first.setCalculationType("rate");
        first.setRate(new BigDecimal("0.1"));
        first.setUpperBound(new BigDecimal("1000"));
        rules.add(first);
        var second =
                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                        .GymSalaryTemplateSaveReqVO.Rule();
        second.setComponent("card_sales");
        second.setCalculationType("rate");
        second.setRate(new BigDecimal("0.2"));
        second.setLowerBound(new BigDecimal("1000"));
        rules.add(second);
        var pt =
                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                        .GymSalaryTemplateSaveReqVO.Rule();
        pt.setComponent("pt_sales");
        pt.setCalculationType("rate");
        pt.setRate(new BigDecimal("0.15"));
        rules.add(pt);
        var lesson =
                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                        .GymSalaryTemplateSaveReqVO.Rule();
        lesson.setComponent("lessons");
        lesson.setCalculationType("per_unit");
        lesson.setUnitAmount(new BigDecimal("30"));
        rules.add(lesson);
        var add =
                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                        .GymSalaryTemplateSaveReqVO.Rule();
        add.setComponent("allowance");
        add.setCalculationType("fixed");
        add.setFixedAmount(new BigDecimal("100"));
        rules.add(add);
        var deduct =
                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                        .GymSalaryTemplateSaveReqVO.Rule();
        deduct.setComponent("deduction");
        deduct.setCalculationType("fixed");
        deduct.setFixedAmount(new BigDecimal("50"));
        rules.add(deduct);
        req.setRules(rules);
        Long id = Long.valueOf(salaries.create(req).getId());
        var publish =
                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                        .GymSalaryTemplateSaveReqVO();
        publish.setStatus("published");
        salaries.patch(id, publish);
        return id;
    }

    void payrollAssign(Long employee, Long template, Long store) {
        salaryPermission();
        salaries.assign(
                employee, salaryAssignment(employee, template, store, LocalDate.of(2026, 1, 1)));
    }

    void financialEvent(
            Long employee,
            Long store,
            String product,
            String type,
            String amount,
            String source,
            java.time.Instant at) {
        var event = new cn.iocoder.yudao.module.gym.dal.dataobject.finance.GymFinancialEventDO();
        event.setEmployeeId(employee);
        event.setStoreId(store);
        event.setProductType(product);
        event.setEventType(type);
        event.setAmount(new BigDecimal(amount));
        event.setBusinessOrderId(1L);
        event.setSourceType(type.equals("payment") ? "pay_order" : "pay_refund");
        event.setSourceId(source);
        event.setOccurredAt(at);
        financialEvents.insert(event);
    }

    @Test
    void payrollUsesNetVerifiedRevenueAndRefundTiersInsteadOfCardIssuance() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long e = Long.valueOf(employees.create(employee("PAY", 101L, 102L)).getId());
        payrollAssign(e, payrollTemplate(), 101L);
        var at = java.time.Instant.parse("2026-06-10T02:00:00Z");
        financialEvent(e, 101L, "card", "payment", "2000", "paid-1", at);
        financialEvent(e, 101L, "card", "refund", "300", "refund-1", at);
        financialEvent(e, 101L, "private", "payment", "600", "paid-2", at);
        financialEvent(
                e,
                101L,
                "card",
                "payment",
                "9999",
                "other-month",
                java.time.Instant.parse("2026-05-01T00:00:00Z"));
        financialEvent(e, 102L, "card", "payment", "100", "other-store", at);
        var row = payrolls.calculate(payrollRequest(101L));
        var item = row.getItems().getFirst();
        assertEquals(new BigDecimal("260.00"), item.getCardCommission());
        assertEquals(new BigDecimal("90.00"), item.getPtCommission());
        assertEquals(new BigDecimal("5400.00"), item.getNetAmount());
        assertEquals(7, item.getComponents().size());
        assertEquals(
                "工资全量规则", ((Map<?, ?>) item.getCalculationSnapshot().get("template")).get("name"));
        scoped(101L);
        assertServiceException(
                () -> payrolls.recalculate(Long.valueOf(row.getId())),
                PAYROLL_SOURCE_NO_PERMISSION);
        var restricted = payrolls.get(Long.valueOf(row.getId())).getItems().getFirst();
        assertEquals(new BigDecimal("5400.00"), restricted.getNetAmount());
        var source =
                restricted.getComponents().stream()
                        .filter(c -> "card_sales".equals(c.getComponent()))
                        .findFirst()
                        .orElseThrow()
                        .getSourceSnapshot();
        assertEquals(1, ((Number) source.get("hiddenSourceCount")).intValue());
        assertEquals(2, ((List<?>) source.get("events")).size());
        headquarters();
        financialEvent(e, 101L, "card", "refund", "2200", "refund-2", at);
        var again = payrolls.recalculate(Long.valueOf(row.getId()));
        assertEquals(new BigDecimal("-40.00"), again.getItems().getFirst().getCardCommission());
        assertEquals(1, again.getEmployeeCount());
    }

    @Test
    void payrollFailureKeepsPreviousItemsAndConfirmationFreezesSnapshot() {
        stores.create(store(101L, "A"));
        Long a = Long.valueOf(employees.create(employee("PA", 101L)).getId()),
                template = payrollTemplate();
        payrollAssign(a, template, 101L);
        var first = payrolls.calculate(payrollRequest(101L));
        Long id = Long.valueOf(first.getId());
        String oldItem = first.getItems().getFirst().getId();
        employees.create(employee("MISSING", 101L));
        assertServiceException(() -> payrolls.recalculate(id), PAYROLL_ASSIGNMENT_INVALID, "测试员工");
        var kept = payrolls.get(id);
        assertEquals(oldItem, kept.getItems().getFirst().getId());
        assertEquals(first.getNetAmount(), kept.getNetAmount());
        var confirmed = payrolls.confirm(id);
        assertEquals("confirmed", confirmed.getStatus());
        assertEquals(confirmed.getConfirmedAt(), payrolls.confirm(id).getConfirmedAt());
        assertServiceException(() -> payrolls.recalculate(id), PAYROLL_FROZEN);
        financialEvent(
                a,
                101L,
                "card",
                "payment",
                "9999",
                "late-payment",
                java.time.Instant.parse("2026-06-10T02:00:00Z"));
        assertEquals(first.getNetAmount(), payrolls.get(id).getNetAmount());
    }

    @Test
    void confirmedPayrollPreventsShorteningSalaryAssignmentAndStaleDraftCannotConfirm() {
        stores.create(store(101L, "A"));
        Long e = Long.valueOf(employees.create(employee("PAY-FROZEN", 101L)).getId()),
                template = payrollTemplate();
        salaryPermission();
        var assignment =
                salaries.createAssignment(
                        salaryAssignment(e, template, 101L, LocalDate.of(2026, 1, 1)));
        Long aid = Long.valueOf(assignment.getId());
        var payroll = payrolls.calculate(payrollRequest(101L));
        var end =
                new cn.iocoder.yudao.module.gym.controller.admin.salary.vo
                        .GymSalaryAssignmentReqVO();
        end.setEffectiveTo(LocalDate.of(2026, 6, 15));
        salaries.patchAssignment(aid, end);
        assertServiceException(
                () -> payrolls.confirm(Long.valueOf(payroll.getId())),
                PAYROLL_ASSIGNMENT_INVALID,
                "测试员工");
        end.setEffectiveTo(null);
        salaries.patchAssignment(aid, end);
        payrolls.confirm(Long.valueOf(payroll.getId()));
        end.setEffectiveTo(LocalDate.of(2026, 6, 15));
        assertServiceException(() -> salaries.patchAssignment(aid, end), PAYROLL_ASSIGNMENT_FROZEN);
        assertServiceException(
                () ->
                        salaries.createAssignment(
                                salaryAssignment(e, template, 101L, LocalDate.of(2026, 6, 15))),
                PAYROLL_ASSIGNMENT_FROZEN);
        assertNull(salaries.assignment(aid).getEffectiveTo());
        salaries.createAssignment(salaryAssignment(e, template, 101L, LocalDate.of(2026, 7, 1)));
        assertEquals(LocalDate.of(2026, 7, 1), salaries.assignment(aid).getEffectiveTo());
    }

    @Test
    void payrollSelfScopeReturnsOnlyOwnItemAndCannotCalculateOrConfirm() {
        stores.create(store(101L, "A"));
        Long a = approvalEmployee("SELF", 91L),
                b = Long.valueOf(employees.create(employee("OTHER", 101L)).getId()),
                template = payrollTemplate();
        payrollAssign(a, template, 101L);
        payrollAssign(b, template, 101L);
        var full = payrolls.calculate(payrollRequest(101L));
        Long id = Long.valueOf(full.getId());
        assertEquals(2, full.getEmployeeCount());
        loginAs(91L);
        var self = new DeptDataPermissionRespDTO();
        self.setSelf(true);
        when(permissions.getDeptDataPermission(any())).thenReturn(self);
        var own = payrolls.get(id);
        assertEquals(1, own.getEmployeeCount());
        assertEquals(a.toString(), own.getItems().getFirst().getEmployeeId());
        assertEquals(new BigDecimal("5050.00"), own.getNetAmount());
        assertEquals(
                1L,
                payrolls.page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        assertServiceException(() -> payrolls.calculate(payrollRequest(101L)), STORE_NO_PERMISSION);
        assertServiceException(() -> payrolls.confirm(id), STORE_NO_PERMISSION);
        loginAs(92L);
        assertEquals(
                0L,
                payrolls.page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        assertServiceException(() -> payrolls.get(id), STORE_NO_PERMISSION);
        headquarters();
        TenantContextHolder.setTenantId(2L);
        assertServiceException(() -> payrolls.get(id), PAYROLL_NOT_EXISTS);
    }

    @Test
    void payrollSinglePayingStoreAvoidsDuplicateBaseAndWholeMonthCoverageIsRequired() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long employee = Long.valueOf(employees.create(employee("MULTI", 101L, 102L)).getId()),
                template = payrollTemplate();
        payrollAssign(employee, template, 102L);
        assertEquals(0, payrolls.calculate(payrollRequest(101L)).getEmployeeCount());
        assertEquals(1, payrolls.calculate(payrollRequest(102L)).getEmployeeCount());
        scoped(101L);
        assertServiceException(() -> payrolls.calculate(payrollRequest(102L)), STORE_NO_PERMISSION);
        headquarters();
        var request = salaryAssignment(employee, template, 102L, LocalDate.of(2026, 6, 15));
        salaryPermission();
        salaries.assign(employee, request);
        assertServiceException(
                () ->
                        payrolls.recalculate(
                                Long.valueOf(
                                        payrolls.page(
                                                        new cn.iocoder.yudao.module.gym.controller
                                                                .admin.common
                                                                .GymBusinessPageReqVO())
                                                .getList()
                                                .getFirst()
                                                .getId())),
                PAYROLL_ASSIGNMENT_INVALID,
                "测试员工");
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> payrollController.create(payrollRequest(101L)));
    }

    @Test
    void payrollLessonCommissionUsesOnlyActualConfirmedUnits() {
        stores.create(store(101L, "A"));
        Long coach = Long.valueOf(employees.create(employee("PAY-COACH", 101L)).getId());
        payrollAssign(coach, payrollTemplate(), 101L);
        var start = java.time.Instant.parse("2026-06-10T02:00:00Z");
        when(businessClock.now()).thenReturn(start);
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId()),
                member = Long.valueOf(members.create(member(101L, "13800000801")).getId());
        var pack =
                courses.issuePack(
                        member,
                        product,
                        start.minusSeconds(60),
                        start.plusSeconds(86400),
                        "test-payment",
                        "payroll-lessons");
        var bookings = new ArrayList<Long>();
        for (int i = 1; i <= 2; i++) {
            var at = start.plusSeconds(i * 7200);
            Long session =
                    Long.valueOf(courses.createSession(courseSession(product, coach, at)).getId());
            bookings.add(
                    Long.valueOf(
                            courses.createBooking(bookingRequest(member, session, pack.getId()))
                                    .getId()));
        }
        assertEquals(
                new BigDecimal("0.00"),
                payrolls.calculate(payrollRequest(101L))
                        .getItems()
                        .getFirst()
                        .getLessonCommission());
        for (int i = 0; i < 2; i++) {
            when(businessClock.now()).thenReturn(start.plusSeconds((i + 1) * 7200 + 60));
            courses.checkIn(bookings.get(i));
            courses.confirmLesson(bookings.get(i));
            courses.confirmLesson(bookings.get(i));
        }
        var item = payrolls.calculate(payrollRequest(101L)).getItems().getFirst();
        assertEquals(new BigDecimal("60.00"), item.getLessonCommission());
        assertEquals(new BigDecimal("5110.00"), item.getNetAmount());
        var component =
                item.getComponents().stream()
                        .filter(c -> "lessons".equals(c.getComponent()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(2L, component.getBasisQuantity());
        assertEquals(2, ((List<?>) component.getSourceSnapshot().get("lessons")).size());
    }

    @Test
    void concurrentPayrollCalculationsKeepOnePeriodAndOneActiveItemPerEmployee() throws Exception {
        stores.create(store(101L, "A"));
        Long employee = Long.valueOf(employees.create(employee("PAY-CONCURRENT", 101L)).getId());
        payrollAssign(employee, payrollTemplate(), 101L);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        var futures = new ArrayList<java.util.concurrent.Future<String>>();
        try {
            for (int i = 0; i < 4; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    TenantContextHolder.setTenantId(1L);
                                    loginAs(1L);
                                    try {
                                        return payrolls.calculate(payrollRequest(101L)).getId();
                                    } finally {
                                        TenantContextHolder.clear();
                                        org.springframework.security.core.context
                                                .SecurityContextHolder.clearContext();
                                    }
                                }));
            var ids = new HashSet<String>();
            for (var f : futures) ids.add(f.get(15, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, ids.size());
            var detail = payrolls.get(Long.valueOf(ids.iterator().next()));
            assertEquals(1, detail.getItems().size());
            assertEquals(new BigDecimal("5050.00"), detail.getNetAmount());
        } finally {
            pool.shutdownNow();
        }
    }

    @Resource cn.iocoder.yudao.module.gym.service.report.GymReportService reports;

    @Resource
    cn.iocoder.yudao.module.gym.controller.admin.report.GymReportController reportController;

    cn.iocoder.yudao.module.gym.controller.admin.report.vo.GymReportReqVO reportRange() {
        var r = new cn.iocoder.yudao.module.gym.controller.admin.report.vo.GymReportReqVO();
        r.setFrom("2026-06-01");
        r.setTo("2026-07-01");
        return r;
    }

    @Test
    void performanceUsesActualNetEventsDistinctPaidOrdersAndScopedSummaryBeforePagination() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long employee = Long.valueOf(employees.create(employee("REPORT", 101L, 102L)).getId());
        var start = java.time.Instant.parse("2026-05-31T16:00:00Z");
        financialEvent(employee, 101L, "card", "payment", "1000", "report-pay", start);
        financialEvent(
                employee, 101L, "card", "payment", "100", "report-pay-2", start.plusSeconds(60));
        financialEvent(
                employee, 101L, "card", "refund", "300", "report-refund", start.plusSeconds(120));
        financialEvent(
                null,
                102L,
                "private",
                "refund",
                "200",
                "report-refund-only",
                start.plusSeconds(180));
        financialEvent(
                employee, 101L, "card", "payment", "9999", "report-before", start.minusSeconds(1));
        financialEvent(
                employee,
                101L,
                "card",
                "payment",
                "9999",
                "report-after",
                java.time.Instant.parse("2026-06-30T16:00:00Z"));
        var req = reportRange();
        req.setPageSize(1);
        var result = reports.performance(req);
        assertEquals(2, result.total());
        assertEquals(new BigDecimal("600.00"), result.summary().netAmount());
        assertEquals(1, result.summary().orderCount());
        var row = result.list().getFirst();
        assertEquals("101", row.getId());
        assertEquals(new BigDecimal("800.00"), row.getNetAmount());
        assertEquals(new BigDecimal("800.00"), row.getAverageOrderAmount());
        assertEquals(1, row.getEmployeeCount());
        req.setPageNo(2);
        assertEquals(
                new BigDecimal("-200.00"),
                reports.performance(req).list().getFirst().getNetAmount());
        req.setPageNo(1);
        req.setPageSize(20);
        req.setGroupBy("employee");
        var byEmployee = reports.performance(req);
        assertEquals(2, byEmployee.total());
        assertEquals("unassigned", byEmployee.list().getLast().getId());
        scoped(101L);
        var scoped = reports.performance(req);
        assertEquals(1, scoped.total());
        assertEquals(new BigDecimal("800.00"), scoped.summary().netAmount());
        req.setStoreId(102L);
        assertServiceException(() -> reports.performance(req), STORE_NO_PERMISSION);
        headquarters();
        TenantContextHolder.setTenantId(2L);
        req.setStoreId(null);
        assertEquals(0, reports.performance(req).total());
    }

    void reportMemberDate(Long id, java.time.Instant at) {
        var mapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberMapper.class);
        var m = mapper.selectById(id);
        m.setRegisteredAt(at);
        mapper.updateById(m);
    }

    @Test
    void dashboardDeduplicatesCrossStoreMembersFillsShanghaiDaysAndIgnoresFailedCheckins() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        var now = java.time.Instant.parse("2026-06-10T02:00:00Z");
        when(businessClock.now()).thenReturn(now);
        Long m = Long.valueOf(members.create(member(101L, "13800000901")).getId());
        reportMemberDate(m, java.time.Instant.parse("2026-06-09T16:00:00Z"));
        Long template = Long.valueOf(cards.create(cardTemplate("period")).getId());
        cards.issue(m, 101L, template, now.minusSeconds(60), "test", "report-member-both");
        var mapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.course.GymCheckinMapper.class);
        for (String status : List.of("succeeded", "failed")) {
            var c = new cn.iocoder.yudao.module.gym.dal.dataobject.course.GymCheckinDO();
            c.setStoreId(101L);
            c.setMemberId(m);
            c.setBookingId("succeeded".equals(status) ? 1L : 2L);
            c.setEnteredAt(now);
            c.setMethod("manual");
            c.setVisitorType("member");
            c.setStatus(status);
            mapper.insert(c);
        }
        financialEvent(null, 102L, "card", "refund", "20", "dashboard-refund", now);
        var req = new cn.iocoder.yudao.module.gym.controller.admin.report.vo.GymReportReqVO();
        req.setDays(7);
        var result = reports.dashboard(req);
        assertEquals("2026-06-04", result.from());
        assertEquals("2026-06-10", result.to());
        assertEquals(7, result.trend().size());
        assertEquals(1, result.metrics().activeMembers());
        assertEquals(1, result.metrics().newMembers());
        assertEquals(1, result.metrics().entries());
        assertEquals(new BigDecimal("-20.00"), result.metrics().netAmount());
        assertEquals(new BigDecimal("0.00"), result.trend().getFirst().netAmount());
        assertEquals(1, result.trend().getLast().entries());
        scoped(101L);
        assertEquals(new BigDecimal("0.00"), reports.dashboard(req).metrics().netAmount());
        var self = new DeptDataPermissionRespDTO();
        self.setSelf(true);
        when(permissions.getDeptDataPermission(any())).thenReturn(self);
        assertEquals(0, reports.dashboard(req).metrics().activeMembers());
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> reportController.dashboard(req));
    }

    @Test
    void memberReportDeduplicatesExpiringRightsPerPersonAndKeepsStoreIsolation() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        var now = java.time.Instant.parse("2026-06-10T02:00:00Z");
        when(businessClock.now()).thenReturn(now);
        Long m = Long.valueOf(members.create(member(101L, "13800000902")).getId());
        reportMemberDate(m, now);
        var templateReq = cardTemplate("period");
        templateReq.setDurationDays(10);
        Long template = Long.valueOf(cards.create(templateReq).getId());
        cards.issue(m, 101L, template, now.minusSeconds(60), "test", "expiry-a");
        cards.issue(m, 101L, template, now.minusSeconds(60), "test", "expiry-b");
        Long lead = Long.valueOf(members.create(member(101L, "13800000903")).getId());
        reportMemberDate(lead, java.time.Instant.parse("2026-05-01T00:00:00Z"));
        Long coach = Long.valueOf(employees.create(employee("REPORT-COACH", 101L)).getId()),
                product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        courses.issuePack(
                m, product, now.minusSeconds(60), now.plusSeconds(86400), "test", "report-pack-1");
        courses.issuePack(
                m, product, now.minusSeconds(60), now.plusSeconds(172800), "test", "report-pack-2");
        var result = reports.members(reportRange());
        var a = result.list().getFirst();
        assertEquals(2, a.totalMembers());
        assertEquals(1, a.newMembers());
        assertEquals(1, a.leads());
        assertEquals(1, a.expiringCards());
        assertEquals(1, a.expiringCourses());
        assertEquals(1, result.list().getLast().totalMembers());
        scoped(102L);
        var b = reports.members(reportRange());
        assertEquals(1, b.total());
        assertEquals(1, b.list().getFirst().expiringCards());
    }

    @Test
    void dashboardPendingApprovalsShowsOnlyCurrentAssignedStepAndValidEmployeeBinding() {
        stores.create(store(101L, "A"));
        Long first = approvalEmployee("REPORT-FIRST", 91L),
                second = approvalEmployee("REPORT-SECOND", 92L);
        Long template = publishedApproval("card_opening", first, second);
        Long m = Long.valueOf(members.create(member(101L, "13800000904")).getId());
        Long card = Long.valueOf(cards.create(cardTemplate("count")).getId());
        var approval = approvals.create(opening(template, m, card), "report-pending");
        when(permissions.hasAnyPermissions(eq(91L), eq("gym:approvals:read"))).thenReturn(true);
        when(permissions.hasAnyPermissions(eq(92L), eq("gym:approvals:read"))).thenReturn(true);
        loginAs(92L);
        assertTrue(reports.dashboard(reportRange()).pendingApprovals().isEmpty());
        loginAs(91L);
        var pending = reports.dashboard(reportRange()).pendingApprovals();
        assertEquals(1, pending.size());
        assertEquals(approval.getId(), pending.getFirst().id());
        approvals.decide(Long.valueOf(approval.getId()), decision("approve"));
        assertTrue(reports.dashboard(reportRange()).pendingApprovals().isEmpty());
        loginAs(92L);
        assertEquals(1, reports.dashboard(reportRange()).pendingApprovals().size());
        var mapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.employee.GymEmployeeMapper.class);
        var employee = mapper.selectById(second);
        employee.setUserId(93L);
        mapper.updateById(employee);
        assertTrue(reports.dashboard(reportRange()).pendingApprovals().isEmpty());
    }

    @Test
    void reportsValidateGroupRangeAndNoPermissionWithoutLeakingRows() {
        stores.create(store(101L, "A"));
        var req = reportRange();
        req.setGroupBy("arbitrary");
        assertThrows(ConstraintViolationException.class, () -> reports.performance(req));
        req.setGroupBy(null);
        req.setFrom("2020-01-01");
        assertServiceException(() -> reports.members(req), BUSINESS_DATE_INVALID);
        req.setFrom("2026-07-01");
        assertServiceException(() -> reports.performance(req), BUSINESS_DATE_INVALID);
        financialEvent(
                null,
                101L,
                "card",
                "payment",
                "25",
                "report-old-history",
                java.time.Instant.parse("2020-01-01T00:00:00Z"));
        var all = new cn.iocoder.yudao.module.gym.controller.admin.report.vo.GymReportReqVO();
        assertEquals(new BigDecimal("25.00"), reports.performance(all).summary().netAmount());
        req.setFrom("2026-06-01");
        scoped();
        assertEquals(0, reports.members(req).total());
        assertEquals(new BigDecimal("0.00"), reports.performance(req).summary().netAmount());
    }

    @Resource cn.iocoder.yudao.module.gym.service.sales.GymSalesSettingsService salesSettings;

    @Resource
    cn.iocoder.yudao.module.gym.controller.admin.sales.GymSalesSettingsController
            salesSettingsController;

    cn.iocoder.yudao.module.gym.controller.admin.sales.vo.GymOnlineSalesSettingsReqVO
            onlineSettings(Long template) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.sales.vo
                        .GymOnlineSalesSettingsReqVO();
        var offer =
                new cn.iocoder.yudao.module.gym.controller.admin.sales.vo
                        .GymOnlineSalesSettingsReqVO.Offer();
        offer.setCardTemplateId(template);
        offer.setSalePrice(new BigDecimal("99.99"));
        offer.setEnabled(true);
        offer.setContractRequired(true);
        offer.setApprovalRequired(false);
        req.setOffers(List.of(offer));
        return req;
    }

    cn.iocoder.yudao.module.gym.controller.admin.sales.vo.GymSubscriptionSettingsReqVO
            subscriptionSettings(Long template) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.sales.vo
                        .GymSubscriptionSettingsReqVO();
        var item =
                new cn.iocoder.yudao.module.gym.controller.admin.sales.vo
                        .GymSubscriptionSettingsReqVO.Item();
        item.setCardTemplateId(template);
        item.setPrice(new BigDecimal("59.99"));
        item.setBillingDay(null);
        item.setRenewalNoticeDays(3);
        item.setGraceDays(3);
        item.setEnabled(true);
        item.setContractRequired(true);
        item.setApprovalRequired(false);
        req.setItems(List.of(item));
        return req;
    }

    cn.iocoder.yudao.module.gym.controller.admin.sales.vo.GymVerificationSettingsReqVO
            verificationSettings() {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.sales.vo
                        .GymVerificationSettingsReqVO();
        req.setEnabled(true);
        req.setRequireCheckin(true);
        req.setSupportedChannels(List.of("miniapp", "meituan"));
        return req;
    }

    @Test
    void salesSettingsPersistReplaceAndNeverRewriteCardTemplatePrice() {
        stores.create(store(101L, "A"));
        Long template = Long.valueOf(cards.create(cardTemplate("period")).getId());
        assertEquals(List.of(), salesSettings.get(101L, "online-sales-settings").get("offers"));
        assertEquals(false, salesSettings.get(101L, "verification-settings").get("enabled"));
        var req = onlineSettings(template);
        var start = java.time.Instant.parse("2026-11-01T00:00:00Z");
        req.getOffers().getFirst().setStartsAt(start);
        req.getOffers().getFirst().setEndsAt(start.plusSeconds(86400));
        var saved = salesSettings.saveOnline(101L, req);
        assertEquals(1L, saved.get("revision"));
        assertEquals(1L, salesSettings.saveOnline(101L, req).get("revision"));
        var offer = (Map<?, ?>) ((List<?>) saved.get("offers")).getFirst();
        assertEquals(template.toString(), offer.get("cardTemplateId"));
        assertEquals(new BigDecimal("100.00"), cards.template(template).getPrice());
        req.getOffers().getFirst().setStartsAt(null);
        req.getOffers().getFirst().setEndsAt(null);
        salesSettings.saveOnline(101L, req);
        var reloaded =
                (Map<?, ?>)
                        ((List<?>) salesSettings.get(101L, "online-sales-settings").get("offers"))
                                .getFirst();
        assertNull(reloaded.get("startsAt"));
        assertNull(reloaded.get("endsAt"));
        req.setOffers(List.of());
        assertEquals(List.of(), salesSettings.saveOnline(101L, req).get("offers"));
        assertEquals(3L, salesSettings.get(101L, "online-sales-settings").get("revision"));
    }

    @Test
    void salesSettingsRejectDuplicateInvalidWindowAndApprovalDependencyAtomically() {
        stores.create(store(101L, "A"));
        Long template = Long.valueOf(cards.create(cardTemplate("period")).getId());
        var req = onlineSettings(template);
        salesSettings.saveOnline(101L, req);
        req.setOffers(List.of(req.getOffers().getFirst(), req.getOffers().getFirst()));
        assertServiceException(() -> salesSettings.saveOnline(101L, req), SALES_SETTINGS_INVALID);
        req.setOffers(List.of(req.getOffers().getFirst()));
        var offer = req.getOffers().getFirst();
        offer.setApprovalRequired(true);
        offer.setContractRequired(false);
        assertServiceException(() -> salesSettings.saveOnline(101L, req), SALES_SETTINGS_INVALID);
        offer.setContractRequired(true);
        offer.setStartsAt(java.time.Instant.parse("2026-11-01T00:00:00Z"));
        assertServiceException(() -> salesSettings.saveOnline(101L, req), SALES_SETTINGS_INVALID);
        offer.setEndsAt(offer.getStartsAt());
        assertServiceException(() -> salesSettings.saveOnline(101L, req), SALES_SETTINGS_INVALID);
        offer.setStartsAt(null);
        offer.setEndsAt(null);
        offer.setSalePrice(new BigDecimal("0.001"));
        assertThrows(ConstraintViolationException.class, () -> salesSettings.saveOnline(101L, req));
        assertEquals(1L, salesSettings.get(101L, "online-sales-settings").get("revision"));
    }

    @Test
    void subscriptionSettingsEnforcePublishedMonthlyStoreTemplateAndAllowDisablingRetiredPlan() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long monthly = Long.valueOf(cards.create(cardTemplate("period")).getId()),
                count = Long.valueOf(cards.create(cardTemplate("count")).getId());
        var selected = cardTemplate("period");
        selected.setAccessScope("selected");
        selected.setStoreIds(List.of(102L));
        Long other = Long.valueOf(cards.create(selected).getId());
        assertServiceException(
                () -> salesSettings.saveSubscription(101L, subscriptionSettings(count)),
                SALES_SETTINGS_CARD_INVALID);
        assertServiceException(
                () -> salesSettings.saveOnline(101L, onlineSettings(other)),
                SALES_SETTINGS_CARD_INVALID);
        var req = subscriptionSettings(monthly);
        salesSettings.saveSubscription(101L, req);
        req.getItems().getFirst().setBillingDay(29);
        assertThrows(
                ConstraintViolationException.class,
                () -> salesSettings.saveSubscription(101L, req));
        req.getItems().getFirst().setBillingDay(28);
        req.getItems().getFirst().setRenewalNoticeDays(31);
        assertThrows(
                ConstraintViolationException.class,
                () -> salesSettings.saveSubscription(101L, req));
        req.getItems().getFirst().setRenewalNoticeDays(0);
        var inactive =
                new cn.iocoder.yudao.module.gym.controller.admin.card.vo.GymCardTemplateSaveReqVO();
        inactive.setStatus("inactive");
        cards.patch(monthly, inactive);
        assertServiceException(
                () -> salesSettings.saveSubscription(101L, req), SALES_SETTINGS_CARD_INVALID);
        req.getItems().getFirst().setEnabled(false);
        salesSettings.saveSubscription(101L, req);
        assertEquals(
                false,
                ((Map<?, ?>)
                                ((List<?>)
                                                salesSettings
                                                        .get(101L, "subscription-settings")
                                                        .get("items"))
                                        .getFirst())
                        .get("enabled"));
    }

    @Test
    void verificationSettingsValidateChannelsTimesAndWholeReplacement() {
        stores.create(store(101L, "A"));
        var req = verificationSettings();
        req.setAllowedStart("09:00");
        req.setAllowedEnd("21:00");
        salesSettings.saveVerification(101L, req);
        req.setAllowedStart("24:00");
        assertThrows(
                ConstraintViolationException.class,
                () -> salesSettings.saveVerification(101L, req));
        req.setAllowedStart("21:00");
        assertServiceException(
                () -> salesSettings.saveVerification(101L, req), SALES_SETTINGS_INVALID);
        req.setAllowedStart(null);
        assertServiceException(
                () -> salesSettings.saveVerification(101L, req), SALES_SETTINGS_INVALID);
        req.setAllowedEnd(null);
        req.setSupportedChannels(List.of());
        assertServiceException(
                () -> salesSettings.saveVerification(101L, req), SALES_SETTINGS_INVALID);
        req.setSupportedChannels(List.of("meituan", "meituan"));
        assertServiceException(
                () -> salesSettings.saveVerification(101L, req), SALES_SETTINGS_INVALID);
        req.setSupportedChannels(List.of("unknown"));
        assertThrows(
                ConstraintViolationException.class,
                () -> salesSettings.saveVerification(101L, req));
        req.setSupportedChannels(List.of());
        req.setEnabled(false);
        var disabled = salesSettings.saveVerification(101L, req);
        assertNull(disabled.get("allowedStart"));
        assertEquals(false, disabled.get("enabled"));
    }

    @Test
    void salesSettingsKeepPermissionStoreAndTenantBoundaries() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        salesSettings.saveVerification(102L, verificationSettings());
        scoped(101L);
        assertServiceException(
                () -> salesSettings.get(102L, "verification-settings"), STORE_NO_PERMISSION);
        assertServiceException(
                () -> salesSettings.saveVerification(102L, verificationSettings()),
                STORE_NO_PERMISSION);
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> salesSettingsController.verification(101L));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> salesSettingsController.saveVerification(101L, verificationSettings()));
        headquarters();
        TenantContextHolder.setTenantId(2L);
        assertServiceException(
                () -> salesSettings.get(102L, "verification-settings"), STORE_NOT_EXISTS);
    }

    @Test
    void concurrentSalesSettingsReplaceAtomicallyWithoutDuplicateRows() throws Exception {
        stores.create(store(101L, "A"));
        var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        var futures = new ArrayList<java.util.concurrent.Future<Map<String, Object>>>();
        try {
            for (int i = 0; i < 6; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    setupThreadContext();
                                    try {
                                        return salesSettings.saveVerification(
                                                101L, verificationSettings());
                                    } finally {
                                        TenantContextHolder.clear();
                                        org.springframework.security.core.context
                                                .SecurityContextHolder.clearContext();
                                    }
                                }));
            for (var f : futures)
                assertEquals(1L, f.get(10, java.util.concurrent.TimeUnit.SECONDS).get("revision"));
            assertEquals(1L, salesSettings.get(101L, "verification-settings").get("revision"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Resource cn.iocoder.yudao.module.gym.service.settlement.GymSettlementService settlementService;

    @Resource
    cn.iocoder.yudao.module.gym.controller.admin.settlement.GymSettlementController
            settlementController;

    cn.iocoder.yudao.module.gym.controller.admin.settlement.vo.GymPaymentAccountCreateReqVO
            paymentAccount(Long store, String suffix) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.settlement.vo
                        .GymPaymentAccountCreateReqVO();
        req.setStoreId(store);
        req.setAccountName("联调收款" + suffix);
        req.setProvider("wechat");
        req.setMerchantId("M-" + suffix);
        var details =
                new cn.iocoder.yudao.module.gym.controller.admin.settlement.vo
                        .GymPaymentAccountCreateReqVO.Details();
        details.setLastFour("1234");
        details.setCredentialRef("vault://test/" + suffix);
        req.setAccountDetails(details);
        return req;
    }

    Long createPaymentAccount(Long store, String suffix) {
        return Long.valueOf(settlementService.createAccount(paymentAccount(store, suffix)).getId());
    }

    void verifiedBindingFixture(Long id) {
        var mapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.settlement.GymPaymentAccountMapper
                                .class);
        var row = mapper.selectById(id);
        row.setBindingStatus("bound");
        mapper.updateById(row);
    }

    cn.iocoder.yudao.module.gym.controller.admin.settlement.vo.GymSettlementRuleSaveReqVO
            settlementRule(Long account) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.settlement.vo
                        .GymSettlementRuleSaveReqVO();
        req.setCycleUnit("day");
        req.setCycleValue(1);
        req.setEffectiveFrom(LocalDate.of(2026, 7, 1));
        var item =
                new cn.iocoder.yudao.module.gym.controller.admin.settlement.vo
                        .GymSettlementRuleSaveReqVO.Item();
        item.setPaymentAccountId(account);
        item.setRatio(BigDecimal.ONE);
        req.setItems(List.of(item));
        return req;
    }

    cn.iocoder.yudao.module.gym.controller.admin.settlement.vo.GymSettlementRuleSaveReqVO
            activeRule() {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.settlement.vo
                        .GymSettlementRuleSaveReqVO();
        req.setStatus("active");
        return req;
    }

    @Test
    void paymentAccountsOnlyPersistMaskedMetadataAndCannotMutateIdentityOrBindingResult() {
        stores.create(store(101L, "A"));
        var created = settlementService.createAccount(paymentAccount(101L, "masked"));
        Long id = Long.valueOf(created.getId());
        assertEquals("**** 1234", created.getAccountMasked());
        assertEquals("101", created.getStoreId());
        assertEquals("unconfigured", created.getBindingStatus());
        assertServiceException(
                () -> settlementService.createAccount(paymentAccount(101L, "masked")),
                PAYMENT_ACCOUNT_DUPLICATE);
        var invalid = paymentAccount(101L, "full-number");
        invalid.getAccountDetails().setLastFour("1234567890");
        assertThrows(
                ConstraintViolationException.class, () -> settlementService.createAccount(invalid));
        invalid.getAccountDetails().setLastFour("1234");
        invalid.getAccountDetails().setCredentialRef("sk-live-raw-secret");
        assertThrows(
                ConstraintViolationException.class, () -> settlementService.createAccount(invalid));
        invalid.getAccountDetails().setCredentialRef("-----BEGIN PRIVATE KEY-----\nsecret");
        assertThrows(
                ConstraintViolationException.class, () -> settlementService.createAccount(invalid));
        var patch =
                new cn.iocoder.yudao.module.gym.controller.admin.settlement.vo
                        .GymPaymentAccountPatchReqVO();
        patch.setBindingStatus("bound");
        assertServiceException(
                () -> settlementService.patchAccount(id, patch), PAYMENT_ACCOUNT_IMMUTABLE);
        patch.setBindingStatus(null);
        patch.setStoreId(null);
        assertServiceException(
                () -> settlementService.patchAccount(id, patch), PAYMENT_ACCOUNT_IMMUTABLE);
        assertEquals("unconfigured", settlementService.account(id).getBindingStatus());
    }

    @Test
    void paymentBindingNeverFakesProviderSuccessAndReferenceChangesInvalidateBinding() {
        stores.create(store(101L, "A"));
        Long id = createPaymentAccount(101L, "binding");
        var result = settlementService.bind(id);
        assertEquals("unconfigured", result.getBindingStatus());
        assertFalse(result.getBindingFailureReason().contains("TODO"));
        assertNotNull(result.getBindingAttemptedAt());
        assertEquals(
                result.getBindingAttemptedAt(),
                settlementService.account(id).getBindingAttemptedAt());
        var patch =
                new cn.iocoder.yudao.module.gym.controller.admin.settlement.vo
                        .GymPaymentAccountPatchReqVO();
        patch.setAccountName("新账户名");
        settlementService.patchAccount(id, patch);
        assertNotNull(settlementService.account(id).getBindingAttemptedAt());
        verifiedBindingFixture(id);
        patch.setCredentialRef("vault://test/replacement");
        var changed = settlementService.patchAccount(id, patch);
        assertEquals("unconfigured", changed.getBindingStatus());
        assertNull(changed.getBindingAttemptedAt());
        assertNull(changed.getBindingFailureReason());
        patch.setCredentialRef(null);
        assertNull(settlementService.patchAccount(id, patch).getCredentialRef());
    }

    @Test
    void settlementDraftRequiresExactTotalUniqueAccountsAndCompatibleChannels() {
        stores.create(store(101L, "A"));
        Long a = createPaymentAccount(101L, "ratio-a"), b = createPaymentAccount(101L, "ratio-b");
        var req = settlementRule(a);
        req.getItems().getFirst().setRatio(new BigDecimal("0.999999"));
        assertServiceException(
                () -> settlementService.createRule(101L, req), SETTLEMENT_RULE_INVALID);
        req.getItems().getFirst().setRatio(new BigDecimal("0.5"));
        req.setItems(List.of(req.getItems().getFirst(), req.getItems().getFirst()));
        assertServiceException(
                () -> settlementService.createRule(101L, req), SETTLEMENT_RULE_INVALID);
        var second =
                new cn.iocoder.yudao.module.gym.controller.admin.settlement.vo
                        .GymSettlementRuleSaveReqVO.Item();
        second.setPaymentAccountId(b);
        second.setRatio(new BigDecimal("0.5"));
        req.setItems(List.of(req.getItems().getFirst(), second));
        var draft = settlementService.createRule(101L, req);
        assertEquals("draft", draft.getStatus());
        assertEquals(2, draft.getItems().size());
        assertEquals(a.toString(), draft.getItems().getFirst().getPaymentAccountId());
        assertServiceException(
                () -> settlementService.patchRule(Long.valueOf(draft.getId()), activeRule()),
                SETTLEMENT_ACCOUNT_UNBOUND);
        assertEquals("draft", settlementService.rule(Long.valueOf(draft.getId())).getStatus());
        var bank = paymentAccount(101L, "bank");
        bank.setProvider("bank");
        second.setPaymentAccountId(Long.valueOf(settlementService.createAccount(bank).getId()));
        assertServiceException(
                () -> settlementService.createRule(101L, req), SETTLEMENT_ACCOUNT_CHANNEL_INVALID);
    }

    @Test
    void settlementActivationFreezesSnapshotAndProtectsActiveCredentialReferences() {
        stores.create(store(101L, "A"));
        when(businessClock.now()).thenReturn(java.time.Instant.parse("2026-06-01T00:00:00Z"));
        Long account = createPaymentAccount(101L, "frozen");
        verifiedBindingFixture(account);
        Long rule =
                Long.valueOf(settlementService.createRule(101L, settlementRule(account)).getId());
        var active = settlementService.patchRule(rule, activeRule());
        assertEquals("active", active.getStatus());
        assertEquals("active", settlementService.patchRule(rule, activeRule()).getStatus());
        var content = activeRule();
        content.setCycleValue(2);
        assertServiceException(
                () -> settlementService.patchRule(rule, content), SETTLEMENT_RULE_IMMUTABLE);
        var rename =
                new cn.iocoder.yudao.module.gym.controller.admin.settlement.vo
                        .GymPaymentAccountPatchReqVO();
        rename.setAccountName("变更展示名称");
        settlementService.patchAccount(account, rename);
        assertEquals(
                "联调收款frozen", settlementService.rule(rule).getItems().getFirst().getAccountName());
        rename.setCredentialRef("vault://test/unsafe-replace");
        assertServiceException(
                () -> settlementService.patchAccount(account, rename), PAYMENT_ACCOUNT_IN_USE);
        assertEquals("bound", settlementService.account(account).getBindingStatus());
    }

    @Test
    void settlementVersionActivationClosesOldIntervalAndRejectsRetroactiveOverlap() {
        stores.create(store(101L, "A"));
        when(businessClock.now()).thenReturn(java.time.Instant.parse("2026-08-01T00:00:00Z"));
        Long account = createPaymentAccount(101L, "versions");
        verifiedBindingFixture(account);
        Long old =
                Long.valueOf(settlementService.createRule(101L, settlementRule(account)).getId());
        settlementService.patchRule(old, activeRule());
        var future = settlementRule(account);
        future.setEffectiveFrom(LocalDate.of(2026, 9, 1));
        Long next = Long.valueOf(settlementService.createRule(101L, future).getId());
        settlementService.patchRule(next, activeRule());
        assertEquals(LocalDate.of(2026, 9, 1), settlementService.rule(old).getEffectiveTo());
        var retro = settlementRule(account);
        retro.setEffectiveFrom(LocalDate.of(2026, 7, 15));
        Long invalid = Long.valueOf(settlementService.createRule(101L, retro).getId());
        assertServiceException(
                () -> settlementService.patchRule(invalid, activeRule()), SETTLEMENT_RULE_OVERLAP);
        assertEquals("draft", settlementService.rule(invalid).getStatus());
        assertEquals(LocalDate.of(2026, 9, 1), settlementService.rule(old).getEffectiveTo());
        var bounded = settlementRule(account);
        bounded.setEffectiveTo(LocalDate.of(2027, 1, 1));
        Long draft = Long.valueOf(settlementService.createRule(101L, bounded).getId());
        var clear =
                new cn.iocoder.yudao.module.gym.controller.admin.settlement.vo
                        .GymSettlementRuleSaveReqVO();
        clear.setEffectiveTo(null);
        assertNull(settlementService.patchRule(draft, clear).getEffectiveTo());
    }

    @Test
    void concurrentSettlementVersionsHaveUniqueNumbersAndOnlyOneOverlappingActivation()
            throws Exception {
        stores.create(store(101L, "A"));
        when(businessClock.now()).thenReturn(java.time.Instant.parse("2026-06-01T00:00:00Z"));
        Long account = createPaymentAccount(101L, "concurrent");
        verifiedBindingFixture(account);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        var futures =
                new ArrayList<
                        java.util.concurrent.Future<
                                cn.iocoder.yudao.module.gym.controller.admin.settlement.vo
                                        .GymSettlementRuleRespVO>>();
        try {
            for (int i = 0; i < 6; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    setupThreadContext();
                                    try {
                                        return settlementService.createRule(
                                                101L, settlementRule(account));
                                    } finally {
                                        TenantContextHolder.clear();
                                        org.springframework.security.core.context
                                                .SecurityContextHolder.clearContext();
                                    }
                                }));
            var rows =
                    new ArrayList<
                            cn.iocoder.yudao.module.gym.controller.admin.settlement.vo
                                    .GymSettlementRuleRespVO>();
            for (var f : futures) rows.add(f.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(
                    Set.of(1, 2, 3, 4, 5, 6),
                    rows.stream()
                            .map(r -> r.getVersion())
                            .collect(java.util.stream.Collectors.toSet()));
            var starts = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (var row : rows.subList(0, 2))
                starts.add(
                        pool.submit(
                                () -> {
                                    setupThreadContext();
                                    try {
                                        settlementService.patchRule(
                                                Long.valueOf(row.getId()), activeRule());
                                        return true;
                                    } catch (
                                            cn.iocoder.yudao.framework.common.exception
                                                            .ServiceException
                                                    e) {
                                        assertEquals(
                                                SETTLEMENT_RULE_OVERLAP.getCode(), e.getCode());
                                        return false;
                                    } finally {
                                        TenantContextHolder.clear();
                                        org.springframework.security.core.context
                                                .SecurityContextHolder.clearContext();
                                    }
                                }));
            int activated = 0;
            for (var f : starts) if (f.get(10, java.util.concurrent.TimeUnit.SECONDS)) activated++;
            assertEquals(1, activated);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void settlementAccountsAndRecordsEnforceScopesTenantAndReadVersusManage() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long hq = createPaymentAccount(null, "hq"),
                a = createPaymentAccount(101L, "scope-a"),
                b = createPaymentAccount(102L, "scope-b");
        var mapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.settlement.GymSettlementMapper.class);
        var row = new cn.iocoder.yudao.module.gym.dal.dataobject.settlement.GymSettlementDO();
        row.setStoreId(101L);
        row.setStoreName("A");
        row.setFinancialEventId(999L);
        row.setRuleId(999L);
        row.setRuleSnapshot("{}");
        row.setOrderNo("TEST-ONLY-RECEIPT");
        row.setBaseAmount(new BigDecimal("100.00"));
        row.setDueAt(java.time.Instant.parse("2026-06-01T00:00:00Z"));
        row.setStatus("failed");
        row.setItems(
                "[{\"id\":\"1\",\"accountName\":\"测试快照\",\"accountMasked\":\"**** 1234\",\"ratio\":1,\"amount\":100,\"status\":\"failed\",\"failureReason\":\"测试渠道失败\"}]");
        mapper.insert(row);
        scoped(101L);
        assertEquals(
                1L,
                settlementService
                        .accounts(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        assertServiceException(() -> settlementService.account(hq), STORE_NO_PERMISSION);
        assertServiceException(() -> settlementService.account(b), STORE_NO_PERMISSION);
        assertServiceException(
                () -> settlementService.createAccount(paymentAccount(null, "not-allowed")),
                STORE_NO_PERMISSION);
        assertEquals(1, settlementService.record(row.getId()).getItems().size());
        assertEquals(
                "failed", settlementService.record(row.getId()).getItems().getFirst().getStatus());
        assertNull(settlementService.record(row.getId()).getItems().getFirst().getSettledAt());
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () ->
                        settlementController.accounts(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO()));
        when(permissions.hasAnyPermissions(eq(1L), eq("gym:settlement:read"))).thenReturn(true);
        assertEquals(
                1L,
                settlementController
                        .records(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getData()
                        .getTotal());
        scoped(102L);
        assertServiceException(() -> settlementService.record(row.getId()), STORE_NO_PERMISSION);
        assertEquals(
                0L,
                settlementService
                        .records(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        headquarters();
        TenantContextHolder.setTenantId(2L);
        assertServiceException(() -> settlementService.account(a), PAYMENT_ACCOUNT_NOT_EXISTS);
        assertServiceException(() -> settlementService.record(row.getId()), SETTLEMENT_NOT_EXISTS);
    }

    @Resource cn.iocoder.yudao.module.gym.service.marketing.GymMarketingService marketing;

    @Resource
    cn.iocoder.yudao.module.gym.controller.admin.marketing.GymMarketingController
            marketingController;

    @Resource
    cn.iocoder.yudao.module.gym.controller.app.marketing.AppGymFranchiseController publicFranchise;

    final java.time.Instant activityNow = java.time.Instant.parse("2026-06-10T02:00:00Z");

    cn.iocoder.yudao.module.gym.controller.admin.marketing.vo.GymActivitySaveReqVO activityRequest(
            Long... storeIds) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.marketing.vo
                        .GymActivitySaveReqVO();
        req.setName("真实联调活动");
        req.setDescription("活动介绍");
        req.setStoreIds(List.of(storeIds));
        req.setRegistrationStartsAt(activityNow.minusSeconds(60));
        req.setRegistrationEndsAt(activityNow.plusSeconds(3600));
        req.setStartsAt(activityNow.plusSeconds(7200));
        req.setEndsAt(activityNow.plusSeconds(10800));
        req.setCapacity(2);
        return req;
    }

    cn.iocoder.yudao.module.gym.controller.admin.marketing.vo.GymActivitySaveReqVO activityStatus(
            String status) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.marketing.vo
                        .GymActivitySaveReqVO();
        req.setStatus(status);
        return req;
    }

    cn.iocoder.yudao.module.gym.controller.admin.marketing.vo.GymActivityRegistrationReqVO
            activityRegistration(Long member, Long store) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.marketing.vo
                        .GymActivityRegistrationReqVO();
        req.setMemberId(member);
        req.setStoreId(store);
        return req;
    }

    Long publishedActivity(Long... ids) {
        when(businessClock.now()).thenReturn(activityNow);
        Long id = Long.valueOf(marketing.createActivity(activityRequest(ids)).getId());
        marketing.patchActivity(id, activityStatus("published"));
        return id;
    }

    cn.iocoder.yudao.module.gym.controller.admin.marketing.vo.GymFranchiseSubmitReqVO
            franchiseRequest() {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.marketing.vo
                        .GymFranchiseSubmitReqVO();
        req.setContactName("测试咨询人");
        req.setPhone("13800000000");
        req.setPreferredCity("上海");
        req.setMessage("联调加盟意向");
        return req;
    }

    cn.iocoder.yudao.module.gym.controller.admin.marketing.vo.GymFranchisePatchReqVO
            franchiseStatus(String status) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.marketing.vo
                        .GymFranchisePatchReqVO();
        req.setStatus(status);
        return req;
    }

    @Test
    void activityStatesTimesAndOptionalCapacityAndCoverAreValidated() {
        stores.create(store(101L, "A"));
        when(businessClock.now()).thenReturn(activityNow);
        var invalid = activityRequest(101L);
        invalid.setRegistrationEndsAt(invalid.getStartsAt().plusSeconds(1));
        assertServiceException(() -> marketing.createActivity(invalid), ACTIVITY_INVALID);
        var req = activityRequest(101L);
        req.setStatus("published");
        assertServiceException(() -> marketing.createActivity(req), ACTIVITY_STATE_INVALID);
        req.setStatus(null);
        var draft = marketing.createActivity(req);
        Long id = Long.valueOf(draft.getId());
        assertEquals("draft", draft.getStatus());
        var capacity =
                new cn.iocoder.yudao.module.gym.controller.admin.marketing.vo
                        .GymActivitySaveReqVO();
        capacity.setCapacity(null);
        assertNull(marketing.patchActivity(id, capacity).getCapacity());
        marketing.patchActivity(id, activityStatus("published"));
        assertServiceException(
                () -> marketing.patchActivity(id, activityStatus("draft")), ACTIVITY_STATE_INVALID);
        marketing.patchActivity(id, activityStatus("closed"));
        assertEquals("closed", marketing.patchActivity(id, activityStatus("closed")).getStatus());
        assertServiceException(
                () -> marketing.patchActivity(id, activityStatus("published")),
                ACTIVITY_STATE_INVALID);
    }

    @Test
    void
            activityRegistrationIsIdempotentCancellationReleasesCapacityAndEventCancelPersistsHistory() {
        stores.create(store(101L, "A"));
        Long first = Long.valueOf(members.create(member(101L, "13800001001")).getId()),
                second = Long.valueOf(members.create(member(101L, "13800001002")).getId());
        Long id = publishedActivity(101L);
        var a = marketing.register(id, activityRegistration(first, 101L));
        assertEquals(a.getId(), marketing.register(id, activityRegistration(first, 101L)).getId());
        marketing.register(id, activityRegistration(second, 101L));
        assertEquals(2L, marketing.activity(id).getRegistrationCount());
        var capacity =
                new cn.iocoder.yudao.module.gym.controller.admin.marketing.vo
                        .GymActivitySaveReqVO();
        capacity.setCapacity(1);
        assertServiceException(
                () -> marketing.patchActivity(id, capacity), ACTIVITY_CAPACITY_INVALID);
        var times =
                new cn.iocoder.yudao.module.gym.controller.admin.marketing.vo
                        .GymActivitySaveReqVO();
        times.setStartsAt(activityNow.plusSeconds(8000));
        assertServiceException(
                () -> marketing.patchActivity(id, times), ACTIVITY_REGISTERED_IMMUTABLE);
        marketing.cancelRegistration(id, Long.valueOf(a.getId()));
        assertEquals(1L, marketing.activity(id).getRegistrationCount());
        assertEquals(a.getId(), marketing.register(id, activityRegistration(first, 101L)).getId());
        marketing.patchActivity(id, activityStatus("cancelled"));
        assertEquals(0L, marketing.activity(id).getRegistrationCount());
        var history =
                marketing.registrations(
                        id,
                        new cn.iocoder.yudao.module.gym.controller.admin.common
                                .GymBusinessPageReqVO());
        assertEquals(2L, history.getTotal());
        assertTrue(
                history.getList().stream()
                        .allMatch(
                                r ->
                                        "cancelled".equals(r.getStatus())
                                                && r.getCancelledAt() != null));
        assertServiceException(
                () -> marketing.register(id, activityRegistration(first, 101L)),
                ACTIVITY_REGISTRATION_INVALID);
    }

    @Test
    void activityStoreScopeRedactsOtherStoreRegistrationsAndRequiresAllStoresForSharedEdits() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long a = Long.valueOf(members.create(member(101L, "13800001003")).getId()),
                b = Long.valueOf(members.create(member(102L, "13800001004")).getId());
        Long id = publishedActivity(101L, 102L);
        marketing.register(id, activityRegistration(a, 101L));
        marketing.register(id, activityRegistration(b, 102L));
        scoped(101L);
        var visible = marketing.activity(id);
        assertEquals(List.of("101"), visible.getStoreIds());
        assertEquals(1L, visible.getRegistrationCount());
        assertEquals(
                1L,
                marketing
                        .registrations(
                                id,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        assertServiceException(
                () -> marketing.patchActivity(id, activityStatus("cancelled")),
                STORE_NO_PERMISSION);
        assertServiceException(
                () -> marketing.register(id, activityRegistration(b, 102L)), STORE_NO_PERMISSION);
        scoped(999L);
        assertEquals(
                0L,
                marketing
                        .activities(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        assertServiceException(() -> marketing.activity(id), STORE_NO_PERMISSION);
        headquarters();
        TenantContextHolder.setTenantId(2L);
        assertServiceException(() -> marketing.activity(id), ACTIVITY_NOT_EXISTS);
    }

    @Test
    void concurrentActivityRegistrationsCannotOversubscribeSharedCapacity() throws Exception {
        stores.create(store(101L, "A"));
        when(businessClock.now()).thenReturn(activityNow);
        var req = activityRequest(101L);
        req.setCapacity(1);
        Long id = Long.valueOf(marketing.createActivity(req).getId());
        marketing.patchActivity(id, activityStatus("published"));
        var memberIds = new ArrayList<Long>();
        for (int i = 0; i < 6; i++)
            memberIds.add(Long.valueOf(members.create(member(101L, "1380000110" + i)).getId()));
        var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        var futures = new ArrayList<java.util.concurrent.Future<Boolean>>();
        try {
            for (Long member : memberIds)
                futures.add(
                        pool.submit(
                                () -> {
                                    setupThreadContext();
                                    try {
                                        marketing.register(id, activityRegistration(member, 101L));
                                        return true;
                                    } catch (
                                            cn.iocoder.yudao.framework.common.exception
                                                            .ServiceException
                                                    e) {
                                        assertEquals(
                                                ACTIVITY_CAPACITY_INVALID.getCode(), e.getCode());
                                        return false;
                                    } finally {
                                        TenantContextHolder.clear();
                                        org.springframework.security.core.context
                                                .SecurityContextHolder.clearContext();
                                    }
                                }));
            int success = 0;
            for (var f : futures) if (f.get(10, java.util.concurrent.TimeUnit.SECONDS)) success++;
            assertEquals(1, success);
            assertEquals(1L, marketing.activity(id).getRegistrationCount());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void activityCoverMustBeOwnedImageAndRegistrationTimeBoundaryIsExclusive() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        when(businessClock.now()).thenReturn(activityNow);
        var file =
                GymFileDO.builder()
                        .storeId(101L)
                        .category("image")
                        .name("cover.png")
                        .mimeType("image/png")
                        .size(10L)
                        .url("test://activity-cover")
                        .uploaderId(1L)
                        .build();
        files.insert(file);
        var req = activityRequest(102L);
        req.setCoverFileId(file.getId());
        assertServiceException(() -> marketing.createActivity(req), ACTIVITY_COVER_INVALID);
        req.setStoreIds(List.of(101L));
        Long id = Long.valueOf(marketing.createActivity(req).getId());
        var clear =
                new cn.iocoder.yudao.module.gym.controller.admin.marketing.vo
                        .GymActivitySaveReqVO();
        clear.setCoverFileId(null);
        assertNull(marketing.patchActivity(id, clear).getCoverFileId());
        marketing.patchActivity(id, activityStatus("published"));
        Long member = Long.valueOf(members.create(member(101L, "13800001005")).getId());
        when(businessClock.now()).thenReturn(activityNow.plusSeconds(3600));
        assertServiceException(
                () -> marketing.register(id, activityRegistration(member, 101L)),
                ACTIVITY_REGISTRATION_INVALID);
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> marketingController.activity(id));
    }

    @Test
    void publicFranchiseSubmissionIsIdempotentOpaqueAndHeadquartersOnlyForContacts() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        var req = franchiseRequest();
        var result = publicFranchise.submit(req, "public-request-1").getData();
        assertEquals(Set.of("id", "status"), result.keySet());
        assertEquals("received", result.get("status"));
        assertEquals(result, publicFranchise.submit(req, "public-request-1").getData());
        req.setMessage("变更内容");
        assertServiceException(
                () -> publicFranchise.submit(req, "public-request-1"), FRANCHISE_REQUEST_CONFLICT);
        loginAs(1L);
        Long id = Long.valueOf(result.get("id"));
        var full = marketing.inquiry(id);
        assertEquals("13800000000", full.getPhone());
        assertEquals("new", full.getStatus());
        marketing.patchInquiry(id, franchiseStatus("contacted"));
        var contacted = marketing.inquiry(id);
        assertNotNull(contacted.getContactedAt());
        assertEquals("1", contacted.getHandledBy());
        marketing.patchInquiry(id, franchiseStatus("closed"));
        var closed = marketing.inquiry(id);
        assertNotNull(closed.getClosedAt());
        assertEquals(
                closed.getClosedAt(),
                marketing.patchInquiry(id, franchiseStatus("closed")).getClosedAt());
        assertServiceException(
                () -> marketing.patchInquiry(id, franchiseStatus("new")), FRANCHISE_STATE_INVALID);
        req.setMessage("联调加盟意向");
        assertEquals(
                "received",
                publicFranchise.submit(req, "public-request-1").getData().get("status"));
        scoped(101L);
        assertServiceException(() -> marketing.inquiry(id), STORE_NO_PERMISSION);
        assertServiceException(
                () ->
                        marketing.inquiries(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO()),
                STORE_NO_PERMISSION);
        headquarters();
        TenantContextHolder.setTenantId(2L);
        assertServiceException(() -> marketing.inquiry(id), FRANCHISE_NOT_EXISTS);
    }

    @Test
    void concurrentFranchiseSubmissionCreatesOneInquiryAndDoesNotGrantAdminPermission()
            throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        var futures = new ArrayList<java.util.concurrent.Future<Long>>();
        try {
            for (int i = 0; i < 6; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    TenantContextHolder.setTenantId(1L);
                                    org.springframework.security.core.context.SecurityContextHolder
                                            .clearContext();
                                    try {
                                        return marketing.submitInquiry(
                                                franchiseRequest(), "concurrent-inquiry", "client");
                                    } finally {
                                        TenantContextHolder.clear();
                                    }
                                }));
            var ids = new HashSet<Long>();
            for (var f : futures) ids.add(f.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, ids.size());
            assertEquals(
                    1L,
                    marketing
                            .inquiries(
                                    new cn.iocoder.yudao.module.gym.controller.admin.common
                                            .GymBusinessPageReqVO())
                            .getTotal());
        } finally {
            pool.shutdownNow();
        }
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () ->
                        marketingController.inquiries(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO()));
        var invalid = franchiseRequest();
        invalid.setPhone("123");
        assertThrows(
                ConstraintViolationException.class,
                () -> marketing.submitInquiry(invalid, "bad-phone-key", "client"));
    }

    @Resource cn.iocoder.yudao.module.gym.service.device.GymDeviceService deviceService;

    @Resource
    cn.iocoder.yudao.module.gym.controller.admin.device.GymDeviceController deviceController;

    cn.iocoder.yudao.module.gym.controller.admin.device.vo.GymDeviceSaveReqVO deviceRequest(
            Long store, String type, String uid) {
        var req = new cn.iocoder.yudao.module.gym.controller.admin.device.vo.GymDeviceSaveReqVO();
        req.setStoreId(store);
        req.setName("设备联调" + uid);
        req.setType(type);
        req.setProvider("unconfigured-vendor");
        req.setDeviceUid(uid);
        req.setLocation("前台");
        return req;
    }

    cn.iocoder.yudao.module.gym.controller.admin.device.vo.GymCompartmentsReqVO compartments(
            String... numbers) {
        var req = new cn.iocoder.yudao.module.gym.controller.admin.device.vo.GymCompartmentsReqVO();
        var items =
                new ArrayList<
                        cn.iocoder.yudao.module.gym.controller.admin.device.vo.GymCompartmentsReqVO
                                .Item>();
        for (String number : numbers) {
            var item =
                    new cn.iocoder.yudao.module.gym.controller.admin.device.vo.GymCompartmentsReqVO
                            .Item();
            item.setNumber(number);
            item.setStatus("available");
            items.add(item);
        }
        req.setCompartments(items);
        return req;
    }

    cn.iocoder.yudao.module.gym.controller.admin.device.vo.GymDeviceCommandReqVO deviceCommand(
            String command, Long slot) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.device.vo.GymDeviceCommandReqVO();
        req.setCommand(command);
        req.setCompartmentId(slot);
        req.setReason("测试指令，不控制真实设备");
        return req;
    }

    cn.iocoder.yudao.module.gym.controller.admin.device.vo.GymEquipmentSaveReqVO equipmentRequest(
            Long store, String code) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.device.vo.GymEquipmentSaveReqVO();
        req.setStoreId(store);
        req.setCode(code);
        req.setName("器械联调" + code);
        req.setModel("TEST");
        return req;
    }

    cn.iocoder.yudao.module.gym.controller.admin.device.vo.GymTutorialSaveReqVO tutorialRequest(
            Long file) {
        var req = new cn.iocoder.yudao.module.gym.controller.admin.device.vo.GymTutorialSaveReqVO();
        req.setTitle("安全使用教学");
        req.setFileId(file);
        req.setInstructions("测试说明");
        req.setSortOrder(0);
        return req;
    }

    @Test
    void deviceIdentityIsUniqueAndOnlineStateCannotBeForged() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        var created = deviceService.createDevice(deviceRequest(101L, "door", "DOOR-1"));
        Long id = Long.valueOf(created.getId());
        assertFalse(created.getOnline());
        assertNull(created.getLastSeenAt());
        assertServiceException(
                () -> deviceService.createDevice(deviceRequest(102L, "door", "DOOR-1")),
                DEVICE_UID_DUPLICATE);
        var patch = new cn.iocoder.yudao.module.gym.controller.admin.device.vo.GymDeviceSaveReqVO();
        patch.setOnline(true);
        assertServiceException(
                () -> deviceService.patchDevice(id, patch), DEVICE_IDENTITY_IMMUTABLE);
        patch.setOnline(null);
        patch.setStoreId(102L);
        assertServiceException(
                () -> deviceService.patchDevice(id, patch), DEVICE_IDENTITY_IMMUTABLE);
        patch.setStoreId(null);
        patch.setName("更新设备");
        patch.setStatus("inactive");
        assertEquals("inactive", deviceService.patchDevice(id, patch).getStatus());
        assertFalse(deviceService.device(id).getOnline());
        var req = deviceRequest(101L, "camera", "CAMERA");
        req.setLastSeenAt(java.time.Instant.now());
        assertServiceException(() -> deviceService.createDevice(req), DEVICE_IDENTITY_IMMUTABLE);
    }

    @Test
    void compartmentsKeepIdentifiersAcrossRemovalAndRestoreAndRejectBusyChanges() {
        stores.create(store(101L, "A"));
        Long id =
                Long.valueOf(
                        deviceService
                                .createDevice(deviceRequest(101L, "locker", "LOCKER-1"))
                                .getId());
        var first = deviceService.saveCompartments(id, compartments("a1", "A2"));
        String a = first.getCompartments().getFirst().getId();
        assertEquals("A1", first.getCompartments().getFirst().getNumber());
        assertEquals("unknown", first.getCompartments().getFirst().getOccupancyState());
        deviceService.saveCompartments(id, compartments("A2"));
        assertEquals(1, deviceService.device(id).getCompartments().size());
        var restored = deviceService.saveCompartments(id, compartments("A1", "A2"));
        assertEquals(a, restored.getCompartments().getFirst().getId());
        assertServiceException(
                () -> deviceService.saveCompartments(id, compartments("a1", "A1")),
                DEVICE_COMPARTMENT_INVALID);
        var mapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.device.GymDeviceCompartmentMapper
                                .class);
        var row = mapper.selectById(Long.valueOf(a));
        row.setOccupancyState("occupied");
        mapper.updateById(row);
        assertServiceException(
                () -> deviceService.saveCompartments(id, compartments("A2")),
                DEVICE_COMPARTMENT_BUSY);
        assertEquals(2, deviceService.device(id).getCompartments().size());
        assertEquals("occupied", mapper.selectById(Long.valueOf(a)).getOccupancyState());
        Long door =
                Long.valueOf(
                        deviceService
                                .createDevice(deviceRequest(101L, "door", "NOT-LOCKER"))
                                .getId());
        assertServiceException(
                () -> deviceService.saveCompartments(door, compartments("A1")),
                DEVICE_COMPARTMENT_INVALID);
    }

    @Test
    void deviceCommandsPersistFailedResultsIdempotentlyWithoutChangingTelemetry() {
        stores.create(store(101L, "A"));
        Long id =
                Long.valueOf(
                        deviceService
                                .createDevice(deviceRequest(101L, "door", "COMMAND-1"))
                                .getId());
        var req = deviceCommand("open_door", null);
        var result = deviceService.command(id, req, "command-request-1");
        assertEquals("failed", result.getStatus());
        assertFalse(result.getSimulated());
        assertTrue(result.getFailureReason().contains("未发送"));
        assertEquals(result.getId(), deviceService.command(id, req, "command-request-1").getId());
        assertEquals(
                result.getRequestedAt(),
                deviceService
                        .commands(
                                id,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getList()
                        .getFirst()
                        .getRequestedAt());
        req.setReason("另一个原因");
        assertServiceException(
                () -> deviceService.command(id, req, "command-request-1"), DEVICE_COMMAND_CONFLICT);
        assertFalse(deviceService.device(id).getOnline());
        assertNull(deviceService.device(id).getLastSeenAt());
        var mock = deviceRequest(101L, "door", "MOCK-1");
        mock.setProvider("mock");
        Long mockId = Long.valueOf(deviceService.createDevice(mock).getId());
        var simulated =
                deviceService.command(mockId, deviceCommand("open_door", null), "mock-request-1");
        assertTrue(simulated.getSimulated());
        assertEquals("failed", simulated.getStatus());
        Long camera =
                Long.valueOf(
                        deviceService
                                .createDevice(deviceRequest(101L, "camera", "CAM-CMD"))
                                .getId());
        assertServiceException(
                () ->
                        deviceService.command(
                                camera, deviceCommand("open_door", null), "camera-request-1"),
                DEVICE_COMMAND_INVALID);
    }

    @Test
    void concurrentDeviceCommandReplayProducesOneAuditRecord() throws Exception {
        stores.create(store(101L, "A"));
        Long id =
                Long.valueOf(
                        deviceService
                                .createDevice(deviceRequest(101L, "door", "COMMAND-CONCURRENT"))
                                .getId());
        var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        var futures = new ArrayList<java.util.concurrent.Future<String>>();
        try {
            for (int i = 0; i < 6; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    setupThreadContext();
                                    try {
                                        return deviceService
                                                .command(
                                                        id,
                                                        deviceCommand("open_door", null),
                                                        "same-command-key")
                                                .getId();
                                    } finally {
                                        TenantContextHolder.clear();
                                        org.springframework.security.core.context
                                                .SecurityContextHolder.clearContext();
                                    }
                                }));
            var ids = new HashSet<String>();
            for (var f : futures) ids.add(f.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, ids.size());
            assertEquals(
                    1L,
                    deviceService
                            .commands(
                                    id,
                                    new cn.iocoder.yudao.module.gym.controller.admin.common
                                            .GymBusinessPageReqVO())
                            .getTotal());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void lockerCommandsCannotTargetAnotherDeviceAndDisabledAttemptsRemainAuditable() {
        stores.create(store(101L, "A"));
        Long
                a =
                        Long.valueOf(
                                deviceService
                                        .createDevice(deviceRequest(101L, "locker", "LOCK-A"))
                                        .getId()),
                b =
                        Long.valueOf(
                                deviceService
                                        .createDevice(deviceRequest(101L, "locker", "LOCK-B"))
                                        .getId());
        Long slot =
                Long.valueOf(
                        deviceService
                                .saveCompartments(b, compartments("01"))
                                .getCompartments()
                                .getFirst()
                                .getId());
        assertServiceException(
                () ->
                        deviceService.command(
                                a, deviceCommand("open_locker", slot), "wrong-target-key"),
                DEVICE_COMPARTMENT_INVALID);
        var patch = new cn.iocoder.yudao.module.gym.controller.admin.device.vo.GymDeviceSaveReqVO();
        patch.setStatus("inactive");
        deviceService.patchDevice(b, patch);
        assertTrue(
                deviceService
                        .command(b, deviceCommand("open_locker", slot), "disabled-device-key")
                        .getFailureReason()
                        .contains("设备已停用"));
        patch.setStatus("active");
        deviceService.patchDevice(b, patch);
        deviceService.saveCompartments(b, compartments());
        assertTrue(
                deviceService
                        .command(b, deviceCommand("open_locker", slot), "disabled-slot-key")
                        .getFailureReason()
                        .contains("柜格已停用"));
        assertEquals(
                2L,
                deviceService
                        .commands(
                                b,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
    }

    @Test
    void equipmentTutorialsRequireOwnedMediaAndStatusChangesStayOnTheirParent() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long
                a =
                        Long.valueOf(
                                deviceService
                                        .createEquipment(equipmentRequest(101L, "EQ-1"))
                                        .getId()),
                b =
                        Long.valueOf(
                                deviceService
                                        .createEquipment(equipmentRequest(102L, "EQ-1"))
                                        .getId());
        assertServiceException(
                () -> deviceService.createEquipment(equipmentRequest(101L, "EQ-1")),
                EQUIPMENT_CODE_DUPLICATE);
        var file =
                GymFileDO.builder()
                        .storeId(101L)
                        .category("image")
                        .mimeType("image/png")
                        .name("tutorial.png")
                        .url("test://tutorial")
                        .size(10L)
                        .uploaderId(1L)
                        .build();
        files.insert(file);
        var tutorial = deviceService.createTutorial(a, tutorialRequest(file.getId()));
        assertEquals(file.getId().toString(), tutorial.getFileId());
        assertServiceException(
                () -> deviceService.createTutorial(b, tutorialRequest(file.getId())),
                EQUIPMENT_TUTORIAL_FILE_INVALID);
        var status =
                new cn.iocoder.yudao.module.gym.controller.admin.device.vo.GymTutorialStatusReqVO();
        status.setStatus("inactive");
        deviceService.patchTutorial(a, Long.valueOf(tutorial.getId()), status);
        assertServiceException(
                () -> deviceService.patchTutorial(b, Long.valueOf(tutorial.getId()), status),
                EQUIPMENT_TUTORIAL_NOT_EXISTS);
        status.setStatus("active");
        assertEquals(
                "active",
                deviceService.patchTutorial(a, Long.valueOf(tutorial.getId()), status).getStatus());
        var patch =
                new cn.iocoder.yudao.module.gym.controller.admin.device.vo.GymEquipmentSaveReqVO();
        patch.setCode("OTHER");
        assertServiceException(
                () -> deviceService.patchEquipment(a, patch), EQUIPMENT_IDENTITY_IMMUTABLE);
        patch.setCode(null);
        patch.setName("更新器械");
        assertEquals("更新器械", deviceService.patchEquipment(a, patch).getName());
    }

    @Test
    void deviceAndEquipmentTenantScopesAndReadWriteCommandPermissionsAreSeparate() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long
                device =
                        Long.valueOf(
                                deviceService
                                        .createDevice(deviceRequest(101L, "door", "SCOPE-DEVICE"))
                                        .getId()),
                equipment =
                        Long.valueOf(
                                deviceService
                                        .createEquipment(equipmentRequest(101L, "SCOPE-EQ"))
                                        .getId());
        when(permissions.hasAnyPermissions(eq(1L), eq("gym:devices:read"))).thenReturn(true);
        assertEquals(device.toString(), deviceController.device(device).getData().getId());
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () ->
                        deviceController.command(
                                device, deviceCommand("open_door", null), "no-command-permission"));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> deviceController.createEquipment(equipmentRequest(101L, "NO-WRITE")));
        scoped(102L);
        assertServiceException(() -> deviceService.device(device), STORE_NO_PERMISSION);
        assertServiceException(
                () ->
                        deviceService.commands(
                                device,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO()),
                STORE_NO_PERMISSION);
        assertServiceException(() -> deviceService.equipment(equipment), STORE_NO_PERMISSION);
        assertEquals(
                0L,
                deviceService
                        .devices(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        headquarters();
        TenantContextHolder.setTenantId(2L);
        assertServiceException(() -> deviceService.device(device), DEVICE_NOT_EXISTS);
        assertServiceException(() -> deviceService.equipment(equipment), EQUIPMENT_NOT_EXISTS);
    }

    @Resource cn.iocoder.yudao.module.gym.service.content.GymContentService contentService;

    @Resource
    cn.iocoder.yudao.module.gym.controller.admin.content.GymContentController contentController;

    cn.iocoder.yudao.module.gym.controller.admin.content.vo.GymClientSettingsReqVO clientSettings(
            Long store, String platform) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.content.vo
                        .GymClientSettingsReqVO();
        req.setStoreId(store);
        req.setPlatform(platform);
        req.setSystemName("测试展示");
        req.setEnabled(true);
        req.setContentHtml("");
        req.setBlocks(List.of());
        return req;
    }

    cn.iocoder.yudao.module.gym.controller.admin.content.vo.GymQrCodeReqVO qrCode(
            Long store, String platform, String type, Long scene) {
        var req = new cn.iocoder.yudao.module.gym.controller.admin.content.vo.GymQrCodeReqVO();
        req.setStoreId(store);
        req.setPlatform(platform);
        req.setSceneType(type);
        req.setSceneId(scene);
        return req;
    }

    @Test
    void clientSettingsFullReplacementIsScopedAndRevisionIsIdempotent() {
        stores.create(store(101L, "A"));
        assertEquals(0L, contentService.settings("miniapp", null).get("revision"));
        var req = clientSettings(null, "miniapp");
        req.setWelcomeText("欢迎");
        assertEquals(1L, contentService.save(req).get("revision"));
        assertEquals(1L, contentService.save(req).get("revision"));
        assertEquals(0L, contentService.settings("app", null).get("revision"));
        assertEquals(0L, contentService.settings("miniapp", 101L).get("revision"));
        req.setWelcomeText("");
        assertEquals(2L, contentService.save(req).get("revision"));
        assertEquals("", contentService.settings("miniapp", null).get("welcomeText"));
        TenantContextHolder.setTenantId(2L);
        assertEquals(0L, contentService.settings("miniapp", null).get("revision"));
    }

    @Test
    void clientHtmlSanitizesScriptsAndStylesButRetainsAuthorizedPrivateMedia() {
        stores.create(store(101L, "A"));
        var image = file(101L, "image");
        var req = clientSettings(101L, "miniapp");
        req.setCoverFileId(image.getId());
        req.setContentHtml(
                "<script>alert(1)</script><p style='color:#fff;background-image:url(https://bad.test);text-align:center' onclick='bad()'>正文</p><img src='gym-file:"
                        + image.getId()
                        + "' onerror='bad()'><a href='javascript:alert(1)'>坏链接</a><a href='/gym/file-preview/"
                        + image.getId()
                        + "'>私有图片</a>");
        var result = contentService.save(req);
        String html = result.get("contentHtml").toString();
        assertFalse(html.contains("script"));
        assertFalse(html.contains("onerror"));
        assertFalse(html.contains("onclick"));
        assertFalse(html.contains("background-image"));
        assertTrue(html.contains("text-align:center"));
        assertTrue(html.contains("gym-file:" + image.getId()));
        assertTrue(html.contains("/gym/file-preview/" + image.getId()));
        assertEquals(image.getId().toString(), result.get("coverFileId"));
        assertEquals(html, contentService.settings("miniapp", 101L).get("contentHtml"));
    }

    @Test
    void clientSettingsRejectCrossStoreHiddenImagesAndInvalidDownloadsAtomically() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        var foreign = file(102L, "image");
        var req = clientSettings(101L, "app");
        contentService.save(req);
        req.setContentHtml("<img src='gym-file:" + foreign.getId() + "'>");
        assertServiceException(() -> contentService.save(req), CLIENT_CONTENT_FILE_INVALID);
        req.setContentHtml("<img src='https://external.test/image.png'>");
        assertServiceException(() -> contentService.save(req), CLIENT_CONTENT_FILE_INVALID);
        req.setContentHtml("");
        req.setAppDownloadUrl("https://user:password@example.test/app");
        assertServiceException(() -> contentService.save(req), CLIENT_CONTENT_INVALID);
        req.setAppDownloadUrl("javascript:alert(1)");
        assertServiceException(() -> contentService.save(req), CLIENT_CONTENT_INVALID);
        assertEquals(1L, contentService.settings("app", 101L).get("revision"));
        req.setAppDownloadUrl("https://example.test/app");
        assertEquals(2L, contentService.save(req).get("revision"));
        req.setPlatform("miniapp");
        assertServiceException(() -> contentService.save(req), CLIENT_CONTENT_INVALID);
    }

    @Test
    void clientBlocksValidateCategoryAndSerializeIdentifiersAsStrings() {
        stores.create(store(101L, "A"));
        var image = file(101L, "image");
        var req = clientSettings(101L, "miniapp");
        var block =
                new cn.iocoder.yudao.module.gym.controller.admin.content.vo.GymClientSettingsReqVO
                        .Block();
        block.setBlockType("banner");
        block.setEnabled(true);
        block.setSortOrder(0);
        block.setFileId(image.getId());
        req.setBlocks(List.of(block));
        var saved = contentService.save(req);
        var blocks = (List<?>) saved.get("blocks");
        assertEquals(image.getId().toString(), ((Map<?, ?>) blocks.getFirst()).get("fileId"));
        block.setBlockType("video");
        assertServiceException(() -> contentService.save(req), CLIENT_CONTENT_FILE_INVALID);
        block.setBlockType("banner");
        req.setBlocks(List.of(block, block));
        assertServiceException(() -> contentService.save(req), CLIENT_CONTENT_INVALID);
    }

    @Test
    void contentSettingsRequireHeadquartersOrExplicitAccessibleStoreAndControllerPermission() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        scoped(101L);
        contentService.save(clientSettings(101L, "app"));
        assertThrows(
                cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> contentService.settings("app", null));
        assertThrows(
                cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> contentService.save(clientSettings(102L, "app")));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> contentController.save(clientSettings(101L, "app")));
    }

    @Test
    void concurrentIdenticalClientSettingsHaveSingleRevision() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        var futures = new ArrayList<java.util.concurrent.Future<Object>>();
        try {
            for (int i = 0; i < 6; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    setupThreadContext();
                                    try {
                                        return contentService
                                                .save(clientSettings(null, "miniapp"))
                                                .get("revision");
                                    } finally {
                                        TenantContextHolder.clear();
                                        org.springframework.security.core.context
                                                .SecurityContextHolder.clearContext();
                                    }
                                }));
            for (var future : futures)
                assertEquals(1L, future.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1L, contentService.settings("miniapp", null).get("revision"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void qrCodesPersistTruthfulFailureAndDeduplicateEachScene() {
        stores.create(store(101L, "A"));
        var req = qrCode(101L, "miniapp", "store", null);
        var first = contentService.createCode(req);
        assertEquals("failed", first.getStatus());
        assertNull(first.getProviderCodeFileId());
        assertTrue(first.getFailureReason().contains("未配置"));
        assertEquals(first.getId(), contentService.createCode(req).getId());
        assertEquals(
                first.getCreatedAt(),
                contentService.code(Long.valueOf(first.getId())).getCreatedAt());
        assertNotEquals(
                first.getId(),
                contentService.createCode(qrCode(101L, "app", "store", null)).getId());
        assertServiceException(
                () -> contentService.createCode(qrCode(101L, "app", "store", 1L)),
                QR_CODE_SCENE_INVALID);
        assertServiceException(
                () -> contentService.createCode(qrCode(101L, "app", "course", null)),
                QR_CODE_SCENE_INVALID);
        assertServiceException(
                () -> contentService.createCode(qrCode(101L, "app", "course", 999L)),
                QR_CODE_SCENE_INVALID);
    }

    @Test
    void qrCourseMustExistInItsStoreAndUnavailableScenesRemainFailed() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        var mapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.course.GymCourseSessionMapper.class);
        var session = new cn.iocoder.yudao.module.gym.dal.dataobject.course.GymCourseSessionDO();
        session.setStoreId(101L);
        session.setCourseProductId(1L);
        session.setCoachId(1L);
        session.setStartsAt(java.time.Instant.now().minusSeconds(7200));
        session.setEndsAt(java.time.Instant.now().minusSeconds(3600));
        session.setStatus("scheduled");
        session.setCapacity(1);
        session.setReservedCount(0);
        mapper.insert(session);
        var code = contentService.createCode(qrCode(101L, "miniapp", "course", session.getId()));
        assertEquals("failed", code.getStatus());
        assertTrue(code.getFailureReason().contains("结束"));
        assertServiceException(
                () -> contentService.createCode(qrCode(102L, "miniapp", "course", session.getId())),
                QR_CODE_SCENE_INVALID);
    }

    @Test
    void qrScopeAndControllerWritePermissionAreEnforced() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long foreign =
                Long.valueOf(
                        contentService.createCode(qrCode(102L, "miniapp", "store", null)).getId());
        scoped(101L);
        assertEquals(
                0L,
                contentService
                        .codes(
                                new cn.iocoder.yudao.module.gym.controller.admin.content.vo
                                        .GymQrCodePageReqVO())
                        .getTotal());
        assertThrows(
                cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> contentService.code(foreign));
        assertThrows(
                cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> contentService.createCode(qrCode(102L, "miniapp", "store", null)));
        when(permissions.hasAnyPermissions(eq(1L), eq("gym:stores:read"))).thenReturn(true);
        assertEquals(
                0L,
                contentController
                        .codes(
                                new cn.iocoder.yudao.module.gym.controller.admin.content.vo
                                        .GymQrCodePageReqVO())
                        .getData()
                        .getTotal());
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> contentController.createCode(qrCode(101L, "miniapp", "store", null)));
        TenantContextHolder.setTenantId(2L);
        headquarters();
        assertServiceException(() -> contentService.code(foreign), QR_CODE_NOT_EXISTS);
    }

    @Test
    void concurrentQrRequestsCreateSingleFailedRecord() throws Exception {
        stores.create(store(101L, "A"));
        var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        var futures = new ArrayList<java.util.concurrent.Future<String>>();
        try {
            for (int i = 0; i < 6; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    setupThreadContext();
                                    try {
                                        return contentService
                                                .createCode(qrCode(101L, "miniapp", "store", null))
                                                .getId();
                                    } finally {
                                        TenantContextHolder.clear();
                                        org.springframework.security.core.context
                                                .SecurityContextHolder.clearContext();
                                    }
                                }));
            var ids = new HashSet<String>();
            for (var future : futures)
                ids.add(future.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, ids.size());
            assertEquals(
                    1L,
                    contentService
                            .codes(
                                    new cn.iocoder.yudao.module.gym.controller.admin.content.vo
                                            .GymQrCodePageReqVO())
                            .getTotal());
        } finally {
            pool.shutdownNow();
        }
    }

    @Resource cn.iocoder.yudao.module.gym.service.sales.GymSalesRecordsService salesRecords;

    @Resource
    cn.iocoder.yudao.module.gym.controller.admin.sales.GymSalesRecordsController
            salesRecordsController;

    cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymBusinessOrderDO salesOrder(
            Long store, String channel, String number) {
        var row = new cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymBusinessOrderDO();
        row.setStoreId(store);
        row.setMemberId(900L);
        row.setMemberName("订单测试会员");
        row.setOrderNo(number);
        row.setRequestKey(number);
        row.setBusinessType("card_sale");
        row.setChannel(channel);
        row.setStatus("paid");
        row.setPayableAmount(new BigDecimal("99.99"));
        row.setPaidAmount(new BigDecimal("99.99"));
        row.setRefundedAmount(BigDecimal.ZERO);
        row.setOrderedAt(java.time.Instant.parse("2026-06-01T16:00:00Z"));
        applicationContext
                .getBean(cn.iocoder.yudao.module.gym.dal.mysql.sales.GymBusinessOrderMapper.class)
                .insert(row);
        return row;
    }

    cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymSubscriptionDO salesSubscription(
            Long store, String agreement) {
        var row = new cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymSubscriptionDO();
        row.setStoreId(store);
        row.setMemberId(900L);
        row.setMemberName("协议测试会员");
        row.setCardTemplateId(500L);
        row.setChannel("miniapp");
        row.setStatus("unconfigured");
        row.setAmount(new BigDecimal("99.99"));
        row.setBillingDay(1);
        row.setProvider("test-provider");
        row.setProviderAgreementId(agreement);
        row.setRequestedAt(java.time.Instant.parse("2026-06-01T16:00:00Z"));
        row.setFailureReason("测试未授权");
        applicationContext
                .getBean(cn.iocoder.yudao.module.gym.dal.mysql.sales.GymSubscriptionMapper.class)
                .insert(row);
        return row;
    }

    @Test
    void selfServiceOrdersExcludeOtherSourcesAndProductTypes() {
        stores.create(store(101L, "A"));
        salesOrder(101L, "miniapp", "MINI");
        salesOrder(101L, "app", "APP");
        var offline = salesOrder(101L, "offline", "OFFLINE");
        var course = salesOrder(101L, "app", "COURSE");
        course.setBusinessType("course_sale");
        applicationContext
                .getBean(cn.iocoder.yudao.module.gym.dal.mysql.sales.GymBusinessOrderMapper.class)
                .updateById(course);
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.sales.vo.GymSalesRecordPageReqVO();
        assertEquals(2L, salesRecords.orders(req).getTotal());
        assertServiceException(() -> salesRecords.order(offline.getId()), SALES_ORDER_NOT_EXISTS);
        assertServiceException(() -> salesRecords.order(course.getId()), SALES_ORDER_NOT_EXISTS);
        req.setChannel("app");
        assertEquals(1L, salesRecords.orders(req).getTotal());
        req.setKeyword("APP");
        assertEquals("APP", salesRecords.orders(req).getList().getFirst().getOrderNo());
        req.setMemberId(901L);
        assertEquals(0L, salesRecords.orders(req).getTotal());
    }

    @Test
    void orderDetailsReturnMoneySnapshotsAndChildRecordsWithoutProviderSecrets() {
        stores.create(store(101L, "A"));
        var order = salesOrder(101L, "miniapp", "SNAPSHOT");
        Long id = order.getId();
        var item = new cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymOrderItemDO();
        item.setOrderId(id);
        item.setProductType("card");
        item.setProductId(600L);
        item.setProductNameSnapshot("历史卡种名");
        item.setProductVersion(3);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("99.99"));
        item.setPayableAmount(new BigDecimal("99.99"));
        applicationContext
                .getBean(cn.iocoder.yudao.module.gym.dal.mysql.sales.GymOrderItemMapper.class)
                .insert(item);
        var payment = new cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymOrderPaymentDO();
        payment.setOrderId(id);
        payment.setProvider("test-provider");
        payment.setProviderPaymentId("SECRET-PAY-REF");
        payment.setAmount(new BigDecimal("99.99"));
        payment.setStatus("paid");
        payment.setPaidAt(order.getOrderedAt());
        applicationContext
                .getBean(cn.iocoder.yudao.module.gym.dal.mysql.sales.GymOrderPaymentMapper.class)
                .insert(payment);
        var refund = new cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymOrderRefundDO();
        refund.setOrderId(id);
        refund.setPaymentId(payment.getId());
        refund.setProvider("test-provider");
        refund.setProviderRefundId("SECRET-REFUND-REF");
        refund.setAmount(new BigDecimal("20.00"));
        refund.setStatus("failed");
        refund.setFailureReason("模拟失败夹具");
        applicationContext
                .getBean(cn.iocoder.yudao.module.gym.dal.mysql.sales.GymOrderRefundMapper.class)
                .insert(refund);
        var result = salesRecords.order(id);
        assertEquals(id.toString(), result.getId());
        assertEquals("历史卡种名", result.getItems().getFirst().getProductNameSnapshot());
        assertEquals("600", result.getItems().getFirst().getProductId());
        assertEquals(new BigDecimal("99.99"), result.getPaidAmount());
        assertEquals(BigDecimal.ZERO.setScale(2), result.getRefundedAmount());
        assertEquals("failed", result.getRefunds().getFirst().getStatus());
        String json = cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(result);
        assertFalse(json.contains("SECRET-"));
        assertFalse(json.contains("requestKey"));
        var foreignTenantItem =
                new cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymOrderItemDO();
        org.springframework.beans.BeanUtils.copyProperties(item, foreignTenantItem);
        foreignTenantItem.setId(null);
        TenantContextHolder.setTenantId(2L);
        foreignTenantItem.setTenantId(2L);
        applicationContext
                .getBean(cn.iocoder.yudao.module.gym.dal.mysql.sales.GymOrderItemMapper.class)
                .insert(foreignTenantItem);
        TenantContextHolder.setTenantId(1L);
        assertEquals(1, salesRecords.order(id).getItems().size());
    }

    @Test
    void commerceScopeProtectsListsDetailsAndSubscriptionChildren() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        var own = salesOrder(101L, "miniapp", "OWN");
        var foreign = salesOrder(102L, "miniapp", "FOREIGN");
        var sub = salesSubscription(102L, "AGREEMENT-FOREIGN");
        scoped(101L);
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.sales.vo.GymSalesRecordPageReqVO();
        assertEquals(1L, salesRecords.orders(req).getTotal());
        assertEquals(own.getId().toString(), salesRecords.order(own.getId()).getId());
        assertThrows(
                cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> salesRecords.order(foreign.getId()));
        assertThrows(
                cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> salesRecords.subscription(sub.getId()));
        assertThrows(
                cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> salesRecords.cycles(sub.getId(), req));
        req.setStoreId(102L);
        assertThrows(
                cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> salesRecords.orders(req));
        scoped();
        req.setStoreId(null);
        assertEquals(0L, salesRecords.orders(req).getTotal());
        assertEquals(0L, salesRecords.subscriptions(req).getTotal());
        assertEquals(0L, salesRecords.verifications(req).getTotal());
    }

    @Test
    void salesRecordDatesUseBeijingHalfOpenBoundaries() {
        stores.create(store(101L, "A"));
        salesOrder(101L, "miniapp", "DATE");
        salesSubscription(101L, "DATE-AGREEMENT");
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.sales.vo.GymSalesRecordPageReqVO();
        req.setDate(LocalDate.of(2026, 6, 1));
        assertEquals(0L, salesRecords.orders(req).getTotal());
        assertEquals(0L, salesRecords.subscriptions(req).getTotal());
        req.setDate(LocalDate.of(2026, 6, 2));
        assertEquals(1L, salesRecords.orders(req).getTotal());
        assertEquals(1L, salesRecords.subscriptions(req).getTotal());
        req.setMonth("2026-06");
        assertServiceException(() -> salesRecords.orders(req), BUSINESS_DATE_INVALID);
    }

    @Test
    void subscriptionCyclesRetainFailedAndPendingResultsAndFilterPeriodStarts() {
        stores.create(store(101L, "A"));
        var sub = salesSubscription(101L, "CYCLE-AGREEMENT");
        Long id = sub.getId();
        var cycle = new cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymSubscriptionCycleDO();
        cycle.setSubscriptionId(id);
        cycle.setPeriodStart(LocalDate.of(2026, 6, 1));
        cycle.setPeriodEnd(LocalDate.of(2026, 7, 1));
        cycle.setAmount(new BigDecimal("99.99"));
        cycle.setPaidAmount(BigDecimal.ZERO);
        cycle.setStatus("failed");
        cycle.setFailureReason("测试渠道未配置");
        applicationContext
                .getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.sales.GymSubscriptionCycleMapper
                                .class)
                .insert(cycle);
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.sales.vo.GymSalesRecordPageReqVO();
        var result = salesRecords.cycles(id, req);
        assertEquals(1L, result.getTotal());
        assertNull(result.getList().getFirst().getChargedAt());
        assertNull(result.getList().getFirst().getOrderId());
        assertEquals("failed", result.getList().getFirst().getStatus());
        assertNull(salesRecords.subscription(id).getSignedAt());
        assertFalse(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(
                                salesRecords.subscription(id))
                        .contains("CYCLE-AGREEMENT"));
        req.setFrom("2026-05-31T16:00:01Z");
        assertEquals(0L, salesRecords.cycles(id, req).getTotal());
        req.setFrom(null);
        req.setTo("2026-05-31T16:00:01Z");
        assertEquals(1L, salesRecords.cycles(id, req).getTotal());
        req.setTo("2026-05-31T16:00:00Z");
        assertEquals(0L, salesRecords.cycles(id, req).getTotal());
    }

    @Test
    void verificationQueriesFilterStoreTypeChannelStatusAndHideProviderReferences() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        var mapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.sales.GymVerificationMapper.class);
        for (long store : List.of(101L, 102L)) {
            var row = new cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymVerificationDO();
            row.setStoreId(store);
            row.setMemberId(900L);
            row.setMemberName("核销测试会员");
            row.setType("card");
            row.setChannel("miniapp");
            row.setStatus("failed");
            row.setProviderReference("SECRET-COUPON-" + store);
            row.setRequestKey("KEY-" + store);
            row.setVerifiedAt(java.time.Instant.parse("2026-06-01T16:00:00Z"));
            row.setFailureReason("测试未配置");
            mapper.insert(row);
        }
        scoped(101L);
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.sales.vo.GymSalesRecordPageReqVO();
        req.setType("card");
        req.setChannel("miniapp");
        req.setStatus("failed");
        req.setDate(LocalDate.of(2026, 6, 2));
        var result = salesRecords.verifications(req);
        assertEquals(1L, result.getTotal());
        assertEquals("101", result.getList().getFirst().getStoreId());
        assertFalse(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(result)
                        .contains("SECRET-COUPON"));
        req.setType("course");
        assertEquals(0L, salesRecords.verifications(req).getTotal());
    }

    @Test
    void salesRecordsRequireReadPermissionAndAreTenantIsolated() {
        stores.create(store(101L, "A"));
        var order = salesOrder(101L, "miniapp", "TENANT");
        var sub = salesSubscription(101L, "TENANT-AGREEMENT");
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.sales.vo.GymSalesRecordPageReqVO();
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> salesRecordsController.order(order.getId()));
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> salesRecordsController.cycles(sub.getId(), req));
        when(permissions.hasAnyPermissions(eq(1L), eq("gym:sales:read"))).thenReturn(true);
        assertEquals(1L, salesRecordsController.orders(req).getData().getTotal());
        TenantContextHolder.setTenantId(2L);
        assertEquals(0L, salesRecords.orders(req).getTotal());
        assertEquals(0L, salesRecords.subscriptions(req).getTotal());
        assertServiceException(() -> salesRecords.order(order.getId()), SALES_ORDER_NOT_EXISTS);
        assertServiceException(
                () -> salesRecords.subscription(sub.getId()), SUBSCRIPTION_NOT_EXISTS);
    }

    @Resource cn.iocoder.yudao.module.gym.service.sales.GymCommerceService commerce;

    @MockitoBean
    cn.iocoder.yudao.module.gym.service.sales.payment.GymPaymentReceiptVerifier receiptVerifier;

    cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymBusinessOrderDO commerceOrder() {
        stores.create(store(101L, "A"));
        Long member = Long.valueOf(members.create(member(101L, "13800000888")).getId());
        Long template = Long.valueOf(cards.create(cardTemplate("count")).getId());
        salesSettings.saveOnline(101L, onlineSettings(template));
        return commerce.prepareCardOrder(101L, member, template, 1, "miniapp", "purchase-test-key");
    }

    cn.iocoder.yudao.module.gym.service.sales.payment.GymPaymentReceiptVerifier.Receipt receipt(
            cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymBusinessOrderDO order,
            String id,
            String paymentId,
            String amount) {
        return new cn.iocoder.yudao.module.gym.service.sales.payment.GymPaymentReceiptVerifier
                .Receipt(
                1L,
                "test-provider",
                id,
                order.getOrderNo(),
                paymentId,
                "CNY",
                new BigDecimal(amount),
                order.getOrderedAt().plusSeconds(1));
    }

    @Test
    void cardPurchaseSnapshotsServerPriceAndReplaysOriginalTermsAfterSettingsChange() {
        var order = commerceOrder();
        var detail = salesRecords.order(order.getId());
        var item = detail.getItems().getFirst();
        assertEquals(new BigDecimal("99.99"), order.getPayableAmount());
        assertEquals("pending_payment", order.getStatus());
        assertEquals(new BigDecimal("0.00"), order.getPaidAmount());
        Long template = Long.valueOf(item.getProductId());
        var settings = onlineSettings(template);
        settings.getOffers().getFirst().setSalePrice(new BigDecimal("80.00"));
        salesSettings.saveOnline(101L, settings);
        var replay =
                commerce.prepareCardOrder(
                        101L, order.getMemberId(), template, 1, "miniapp", "purchase-test-key");
        assertEquals(order.getId(), replay.getId());
        assertEquals(new BigDecimal("99.99"), replay.getPayableAmount());
        assertServiceException(
                () ->
                        commerce.prepareCardOrder(
                                101L,
                                order.getMemberId(),
                                template,
                                2,
                                "miniapp",
                                "purchase-test-key"),
                COMMERCE_IDEMPOTENCY_CONFLICT);
        var policy =
                applicationContext
                        .getBean(
                                cn.iocoder.yudao.module.gym.dal.mysql.sales.GymOrderPolicyMapper
                                        .class)
                        .selectList()
                        .getFirst();
        assertTrue(policy.getContractRequired());
        assertTrue(policy.getTermsSnapshot().contains("99.99"));
        assertNull(policy.getFulfilledAt());
        assertEquals(0L, financialEvents.selectCount());
    }

    @Test
    void disabledOffersAndWrongStoreCannotPreparePurchaseWhileFreeOffersNeverCreatePayments() {
        var order = commerceOrder();
        Long template =
                Long.valueOf(
                        salesRecords.order(order.getId()).getItems().getFirst().getProductId());
        var settings = onlineSettings(template);
        settings.getOffers().getFirst().setEnabled(false);
        salesSettings.saveOnline(101L, settings);
        assertServiceException(
                () ->
                        commerce.prepareCardOrder(
                                101L,
                                order.getMemberId(),
                                template,
                                1,
                                "app",
                                "disabled-offer-key"),
                COMMERCE_REQUEST_INVALID);
        settings.getOffers().getFirst().setEnabled(true);
        settings.getOffers().getFirst().setSalePrice(BigDecimal.ZERO);
        salesSettings.saveOnline(101L, settings);
        var free =
                commerce.prepareCardOrder(
                        101L, order.getMemberId(), template, 1, "app", "free-offer-test-key");
        assertEquals("pending_fulfillment", free.getStatus());
        assertEquals(new BigDecimal("0.00"), free.getPaidAmount());
        assertTrue(salesRecords.order(free.getId()).getPayments().isEmpty());
        assertEquals(0L, financialEvents.selectCount());
        scoped();
        assertServiceException(
                () ->
                        commerce.prepareCardOrder(
                                101L, order.getMemberId(), template, 1, "app", "scope-test-key"),
                STORE_NO_PERMISSION);
    }

    @Test
    void verifiedPartialPaymentsAndRefundsAppendExactFinancialEventsWithoutIssuingRights() {
        var order = commerceOrder();
        Long id = order.getId();
        when(receiptVerifier.payment("p1")).thenReturn(receipt(order, "PAY-1", null, "40.00"));
        when(receiptVerifier.payment("p2")).thenReturn(receipt(order, "PAY-2", null, "59.99"));
        assertEquals("partially_paid", commerce.acceptPayment(id, "p1").getStatus());
        assertEquals("paid", commerce.acceptPayment(id, "p2").getStatus());
        assertEquals("paid", commerce.acceptPayment(id, "p2").getStatus());
        assertEquals(2L, financialEvents.selectCount());
        assertEquals(
                0,
                cards.allCards(
                                order.getMemberId(),
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .size());
        when(receiptVerifier.refund("r1")).thenReturn(receipt(order, "REFUND-1", "PAY-1", "20.00"));
        var refunded = commerce.acceptRefund(id, "r1");
        assertEquals("partially_refunded", refunded.getStatus());
        assertEquals(new BigDecimal("20.00"), refunded.getRefundedAmount());
        assertEquals(
                refunded.getRefundedAmount(), commerce.acceptRefund(id, "r1").getRefundedAmount());
        assertEquals(3L, financialEvents.selectCount());
        var report =
                reports.performance(
                        new cn.iocoder.yudao.module.gym.controller.admin.report.vo
                                .GymReportReqVO());
        assertEquals(new BigDecimal("79.99"), report.summary().netAmount());
        assertEquals(1, report.summary().orderCount());
        when(receiptVerifier.refund("r2")).thenReturn(receipt(order, "REFUND-2", "PAY-1", "20.00"));
        when(receiptVerifier.refund("r3")).thenReturn(receipt(order, "REFUND-3", "PAY-2", "59.99"));
        commerce.acceptRefund(id, "r2");
        assertEquals("refunded", commerce.acceptRefund(id, "r3").getStatus());
        assertEquals(
                new BigDecimal("0.00"),
                reports.performance(
                                new cn.iocoder.yudao.module.gym.controller.admin.report.vo
                                        .GymReportReqVO())
                        .summary()
                        .netAmount());
    }

    @Test
    void receiptsRejectWrongTenantOrderCurrencyPrecisionOverpaymentAndReplayChanges() {
        var order = commerceOrder();
        Long id = order.getId();
        var valid = receipt(order, "PAY-SAME", null, "99.99");
        when(receiptVerifier.payment("valid")).thenReturn(valid);
        commerce.acceptPayment(id, "valid");
        when(receiptVerifier.payment("changed"))
                .thenReturn(receipt(order, "PAY-SAME", null, "99.98"));
        assertServiceException(
                () -> commerce.acceptPayment(id, "changed"), COMMERCE_IDEMPOTENCY_CONFLICT);
        for (var bad :
                List.of(
                        new cn.iocoder.yudao.module.gym.service.sales.payment
                                .GymPaymentReceiptVerifier.Receipt(
                                2L,
                                valid.provider(),
                                "X",
                                valid.orderNo(),
                                null,
                                "CNY",
                                valid.amount(),
                                valid.occurredAt()),
                        new cn.iocoder.yudao.module.gym.service.sales.payment
                                .GymPaymentReceiptVerifier.Receipt(
                                1L,
                                valid.provider(),
                                "X",
                                "WRONG",
                                null,
                                "CNY",
                                valid.amount(),
                                valid.occurredAt()),
                        new cn.iocoder.yudao.module.gym.service.sales.payment
                                .GymPaymentReceiptVerifier.Receipt(
                                1L,
                                valid.provider(),
                                "X",
                                valid.orderNo(),
                                null,
                                "USD",
                                valid.amount(),
                                valid.occurredAt()),
                        receipt(order, "TOO-MUCH", null, "100"),
                        receipt(order, "TOO-PRECISE", null, "0.001"))) {
            when(receiptVerifier.payment("bad")).thenReturn(bad);
            assertServiceException(
                    () -> commerce.acceptPayment(id, "bad"), COMMERCE_RECEIPT_INVALID);
        }
        assertEquals(1L, financialEvents.selectCount());
        assertEquals(1, salesRecords.order(id).getPayments().size());
    }

    @Test
    void refundMustBelongToPaymentAndCannotExceedEachPaymentBalance() {
        var order = commerceOrder();
        Long id = order.getId();
        when(receiptVerifier.payment("paid")).thenReturn(receipt(order, "PAY", null, "99.99"));
        commerce.acceptPayment(id, "paid");
        when(receiptVerifier.refund("bad")).thenReturn(receipt(order, "R1", "UNKNOWN", "10"));
        assertServiceException(() -> commerce.acceptRefund(id, "bad"), COMMERCE_RECEIPT_INVALID);
        when(receiptVerifier.refund("good")).thenReturn(receipt(order, "R1", "PAY", "90"));
        commerce.acceptRefund(id, "good");
        when(receiptVerifier.refund("bad")).thenReturn(receipt(order, "R2", "PAY", "10"));
        assertServiceException(() -> commerce.acceptRefund(id, "bad"), COMMERCE_RECEIPT_INVALID);
        assertEquals(new BigDecimal("90.00"), salesRecords.order(id).getRefundedAmount());
        assertEquals(2L, financialEvents.selectCount());
    }

    @Test
    void failedLedgerWriteRollsBackPaymentAndOrderTotals() {
        var order = commerceOrder();
        Long id = order.getId();
        when(receiptVerifier.payment("p"))
                .thenReturn(receipt(order, "ROLLBACK-PAY", null, "99.99"));
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.execute(
                "ALTER TABLE gym_financial_event ADD CONSTRAINT reject_payment_event CHECK (event_type <> 'payment')");
        try {
            assertThrows(
                    org.springframework.dao.DataIntegrityViolationException.class,
                    () -> commerce.acceptPayment(id, "p"));
            assertEquals(new BigDecimal("0.00"), salesRecords.order(id).getPaidAmount());
            assertTrue(salesRecords.order(id).getPayments().isEmpty());
            assertEquals(0L, financialEvents.selectCount());
        } finally {
            jdbc.execute("ALTER TABLE gym_financial_event DROP CONSTRAINT reject_payment_event");
        }
    }

    @Test
    void concurrentVerifiedPaymentReplayCreatesOnePaymentAndOneLedgerEvent() throws Exception {
        var order = commerceOrder();
        Long id = order.getId();
        when(receiptVerifier.payment("p"))
                .thenReturn(receipt(order, "CONCURRENT-PAY", null, "99.99"));
        var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        var futures = new ArrayList<java.util.concurrent.Future<String>>();
        try {
            for (int i = 0; i < 6; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    setupThreadContext();
                                    try {
                                        return commerce.acceptPayment(id, "p").getStatus();
                                    } finally {
                                        TenantContextHolder.clear();
                                        org.springframework.security.core.context
                                                .SecurityContextHolder.clearContext();
                                    }
                                }));
            for (var f : futures)
                assertEquals("paid", f.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, salesRecords.order(id).getPayments().size());
            assertEquals(1L, financialEvents.selectCount());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void unconfiguredVerifierAndScopeFailBeforeAnyFinancialMutation() {
        var order = commerceOrder();
        Long id = order.getId();
        var unavailable =
                new cn.iocoder.yudao.module.gym.service.sales.payment
                        .UnconfiguredGymPaymentReceiptVerifier();
        assertServiceException(() -> unavailable.payment("p"), COMMERCE_PROVIDER_UNCONFIGURED);
        when(receiptVerifier.payment("p"))
                .thenThrow(
                        cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil
                                .exception(COMMERCE_PROVIDER_UNCONFIGURED));
        assertServiceException(
                () -> commerce.acceptPayment(id, "p"), COMMERCE_PROVIDER_UNCONFIGURED);
        assertEquals(0L, financialEvents.selectCount());
        reset(receiptVerifier);
        scoped();
        assertServiceException(() -> commerce.acceptPayment(id, "p"), STORE_NO_PERMISSION);
        verifyNoInteractions(receiptVerifier);
        TenantContextHolder.setTenantId(2L);
        headquarters();
        assertServiceException(() -> commerce.acceptPayment(id, "p"), SALES_ORDER_NOT_EXISTS);
        verifyNoInteractions(receiptVerifier);
    }

    @Test
    void partialOverpaymentAndProviderIdentityReuseAcrossOrdersFailWithoutMutation() {
        var order = commerceOrder();
        Long id = order.getId();
        when(receiptVerifier.payment("p1")).thenReturn(receipt(order, "PARTIAL-ID", null, "90.00"));
        commerce.acceptPayment(id, "p1");
        when(receiptVerifier.payment("p2")).thenReturn(receipt(order, "OVER-ID", null, "10.00"));
        assertServiceException(() -> commerce.acceptPayment(id, "p2"), COMMERCE_RECEIPT_INVALID);
        assertEquals(new BigDecimal("90.00"), salesRecords.order(id).getPaidAmount());
        assertEquals(1L, financialEvents.selectCount());
        Long template = Long.valueOf(salesRecords.order(id).getItems().getFirst().getProductId());
        var second =
                commerce.prepareCardOrder(
                        101L, order.getMemberId(), template, 1, "miniapp", "other-purchase-key");
        when(receiptVerifier.payment("cross"))
                .thenReturn(receipt(second, "PARTIAL-ID", null, "90.00"));
        assertServiceException(
                () -> commerce.acceptPayment(second.getId(), "cross"),
                COMMERCE_IDEMPOTENCY_CONFLICT);
        assertEquals(new BigDecimal("0.00"), salesRecords.order(second.getId()).getPaidAmount());
    }

    @Test
    void failedRefundLedgerWriteRollsBackRefundRecordAndTotals() {
        var order = commerceOrder();
        Long id = order.getId();
        when(receiptVerifier.payment("p"))
                .thenReturn(receipt(order, "REFUND-ROLLBACK-PAY", null, "99.99"));
        commerce.acceptPayment(id, "p");
        when(receiptVerifier.refund("r"))
                .thenReturn(receipt(order, "REFUND-ROLLBACK", "REFUND-ROLLBACK-PAY", "20"));
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.execute(
                "ALTER TABLE gym_financial_event ADD CONSTRAINT reject_refund_event CHECK (event_type <> 'refund')");
        try {
            assertThrows(
                    org.springframework.dao.DataIntegrityViolationException.class,
                    () -> commerce.acceptRefund(id, "r"));
            assertEquals(new BigDecimal("0.00"), salesRecords.order(id).getRefundedAmount());
            assertTrue(salesRecords.order(id).getRefunds().isEmpty());
            assertEquals(1L, financialEvents.selectCount());
        } finally {
            jdbc.execute("ALTER TABLE gym_financial_event DROP CONSTRAINT reject_refund_event");
        }
    }

    cn.iocoder.yudao.module.gym.controller.admin.approval.vo.GymApprovalCreateReqVO orderOpening(
            Long template, Long order) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.approval.vo
                        .GymApprovalCreateReqVO();
        req.setStoreId(101L);
        req.setBusinessType("card_opening");
        req.setTemplateId(template);
        req.setSourceType("orders");
        req.setSourceId(order);
        req.setTitle("订单开卡审批");
        return req;
    }

    void payCommerceOrder(
            cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymBusinessOrderDO order) {
        when(receiptVerifier.payment("paid-order" + order.getId()))
                .thenReturn(
                        receipt(
                                order,
                                "FULFILL-PAY-" + order.getId(),
                                null,
                                order.getPayableAmount().toString()));
        commerce.acceptPayment(order.getId(), "paid-order" + order.getId());
    }

    @Test
    void orderOpeningResolvesMemberAndProductOnServerAndFinalDecisionFulfillsOnce() {
        var order = commerceOrder();
        Long approver = approvalEmployee("ORDER-APPROVER", 91L),
                template = publishedApproval("card_opening", approver);
        var req = orderOpening(template, order.getId());
        assertServiceException(
                () -> approvals.create(req, "unpaid-order-opening"), COMMERCE_FULFILLMENT_INVALID);
        publishedContractPdf();
        payCommerceOrder(order);
        var created = approvals.create(req, "paid-order-opening");
        Long id = Long.valueOf(created.getId());
        assertEquals(order.getMemberId().toString(), created.getRequestSnapshot().get("memberId"));
        loginAs(91L);
        var approved = approvals.decide(id, decision("approve"));
        assertEquals("approved", approved.getStatus());
        assertEquals("fulfilled", salesRecords.order(order.getId()).getStatus());
        assertNotNull(salesRecords.order(order.getId()).getFulfilledAt());
        assertTrue(salesRecords.order(order.getId()).getContractRequired());
        assertEquals(1, salesRecords.order(order.getId()).getMemberCardIds().size());
        var issued =
                cards.allCards(
                        order.getMemberId(),
                        new cn.iocoder.yudao.module.gym.controller.admin.common
                                .GymBusinessPageReqVO());
        assertEquals(1, issued.size());
        assertEquals(
                "order-item",
                applicationContext
                        .getBean(
                                cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberCardMapper
                                        .class)
                        .selectById(Long.valueOf(issued.getFirst().getId()))
                        .getSourceType());
        assertEquals(
                approved.getBusinessResult(),
                approvals.decide(id, decision("approve")).getBusinessResult());
        assertEquals(
                1,
                cards.allCards(
                                order.getMemberId(),
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .size());
        assertEquals(
                1L,
                contracts
                        .page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        assertServiceException(
                () -> commerce.fulfillApprovedOrder(order.getId(), id + 1),
                COMMERCE_FULFILLMENT_INVALID);
    }

    @Test
    void orderOpeningRejectsClientSubstitutedIdentityAndWrongStore() {
        var order = commerceOrder();
        payCommerceOrder(order);
        Long approver = approvalEmployee("SUBSTITUTION-APPROVER", 91L),
                template = publishedApproval("card_opening", approver);
        var req = orderOpening(template, order.getId());
        req.setMemberId(order.getMemberId());
        assertServiceException(
                () -> approvals.create(req, "substitute-member-order"), APPROVAL_REQUEST_INVALID);
        req.setMemberId(null);
        req.setCardTemplateId(99L);
        assertServiceException(
                () -> approvals.create(req, "substitute-template-order"), APPROVAL_REQUEST_INVALID);
        req.setCardTemplateId(null);
        req.setRequestedPrice(BigDecimal.ZERO);
        assertServiceException(
                () -> approvals.create(req, "substitute-price-order"), APPROVAL_REQUEST_INVALID);
        stores.create(store(102L, "B"));
        assertServiceException(
                () -> commerce.openingSnapshot(order.getId(), 102L), COMMERCE_FULFILLMENT_INVALID);
        scoped(102L);
        assertServiceException(
                () -> commerce.openingSnapshot(order.getId(), 101L), STORE_NO_PERMISSION);
    }

    @Test
    void requiredContractFailureRollsBackRightsAndApprovalButKeepsVerifiedPayment() {
        var order = commerceOrder();
        payCommerceOrder(order);
        Long approver = approvalEmployee("CONTRACT-ORDER-APPROVER", 91L),
                template = publishedApproval("card_opening", approver);
        Long id =
                Long.valueOf(
                        approvals
                                .create(
                                        orderOpening(template, order.getId()),
                                        "contract-required-order")
                                .getId());
        loginAs(91L);
        assertServiceException(
                () -> approvals.decide(id, decision("approve")), CONTRACT_TEMPLATE_NOT_EXISTS);
        assertEquals("pending", approvals.get(id).getStatus());
        assertTrue(
                cards.allCards(
                                order.getMemberId(),
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .isEmpty());
        assertEquals("paid", salesRecords.order(order.getId()).getStatus());
        assertEquals(1L, financialEvents.selectCount());
        loginAs(1L);
        publishedContractPdf();
        loginAs(91L);
        assertEquals("approved", approvals.decide(id, decision("approve")).getStatus());
    }

    @Test
    void paidPurchaseUsesImmutableCardSnapshotEvenAfterTemplateIsRetired() {
        var order = commerceOrder();
        payCommerceOrder(order);
        publishedContractPdf();
        Long template =
                Long.valueOf(
                        salesRecords.order(order.getId()).getItems().getFirst().getProductId());
        var patch =
                new cn.iocoder.yudao.module.gym.controller.admin.card.vo.GymCardTemplateSaveReqVO();
        patch.setStatus("inactive");
        cards.patch(template, patch);
        var first = commerce.fulfillAutomaticOrder(order.getId());
        assertEquals(first, commerce.fulfillAutomaticOrder(order.getId()));
        var issued =
                cards.allCards(
                                order.getMemberId(),
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getFirst();
        assertEquals(10, issued.getRemainingUses());
        assertEquals("fulfilled", salesRecords.order(order.getId()).getStatus());
        assertEquals(1L, financialEvents.selectCount());
    }

    @Test
    void frozenApprovalRequiredPolicyCannotBeBypassedByAutomaticFulfillment() {
        var order = commerceOrder();
        Long template =
                Long.valueOf(
                        salesRecords.order(order.getId()).getItems().getFirst().getProductId());
        var settings = onlineSettings(template);
        settings.getOffers().getFirst().setApprovalRequired(true);
        salesSettings.saveOnline(101L, settings);
        var gated =
                commerce.prepareCardOrder(
                        101L, order.getMemberId(), template, 1, "miniapp", "gated-purchase-order");
        payCommerceOrder(gated);
        settings.getOffers().getFirst().setApprovalRequired(false);
        salesSettings.saveOnline(101L, settings);
        assertServiceException(
                () -> commerce.fulfillAutomaticOrder(gated.getId()), COMMERCE_FULFILLMENT_INVALID);
        assertTrue(
                cards.allCards(
                                order.getMemberId(),
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .isEmpty());
    }

    @Test
    void refundBetweenOpeningRequestAndDecisionPreventsIssuance() {
        var order = commerceOrder();
        payCommerceOrder(order);
        Long approver = approvalEmployee("REFUND-ORDER-APPROVER", 91L),
                template = publishedApproval("card_opening", approver);
        Long id =
                Long.valueOf(
                        approvals
                                .create(
                                        orderOpening(template, order.getId()),
                                        "order-refunded-before-decision")
                                .getId());
        when(receiptVerifier.refund("pre-decision-refund"))
                .thenReturn(
                        receipt(
                                order,
                                "PRE-DECISION-REFUND",
                                "FULFILL-PAY-" + order.getId(),
                                "10.00"));
        commerce.acceptRefund(order.getId(), "pre-decision-refund");
        loginAs(91L);
        assertServiceException(
                () -> approvals.decide(id, decision("approve")), COMMERCE_FULFILLMENT_INVALID);
        assertTrue(
                cards.allCards(
                                order.getMemberId(),
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .isEmpty());
        assertEquals("pending", approvals.get(id).getStatus());
        assertEquals(
                new BigDecimal("10.00"), salesRecords.order(order.getId()).getRefundedAmount());
    }

    @Test
    void multiQuantityFulfillmentAndConcurrentReplayIssueExactlyPurchasedUnits() throws Exception {
        var order = commerceOrder();
        Long template =
                Long.valueOf(
                        salesRecords.order(order.getId()).getItems().getFirst().getProductId());
        var settings = onlineSettings(template);
        settings.getOffers().getFirst().setContractRequired(false);
        salesSettings.saveOnline(101L, settings);
        var bundle =
                commerce.prepareCardOrder(
                        101L, order.getMemberId(), template, 3, "miniapp", "three-cards-order-key");
        payCommerceOrder(bundle);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        var futures = new ArrayList<java.util.concurrent.Future<Object>>();
        try {
            for (int i = 0; i < 6; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    setupThreadContext();
                                    try {
                                        return commerce.fulfillAutomaticOrder(bundle.getId())
                                                .get("memberCardIds");
                                    } finally {
                                        TenantContextHolder.clear();
                                        org.springframework.security.core.context
                                                .SecurityContextHolder.clearContext();
                                    }
                                }));
            var ids = new HashSet<Object>();
            for (var f : futures) ids.add(f.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, ids.size());
            assertEquals(
                    3,
                    cards.allCards(
                                    order.getMemberId(),
                                    new cn.iocoder.yudao.module.gym.controller.admin.common
                                            .GymBusinessPageReqVO())
                            .size());
            assertEquals(1L, financialEvents.selectCount());
        } finally {
            pool.shutdownNow();
        }
    }

    @Resource cn.iocoder.yudao.module.gym.service.sales.GymRefundReviewService refundReviewService;

    cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymBusinessOrderDO refundableBundle() {
        var initial = commerceOrder();
        Long template =
                Long.valueOf(
                        salesRecords.order(initial.getId()).getItems().getFirst().getProductId());
        var settings = onlineSettings(template);
        settings.getOffers().getFirst().setContractRequired(false);
        salesSettings.saveOnline(101L, settings);
        var order =
                commerce.prepareCardOrder(
                        101L,
                        initial.getMemberId(),
                        template,
                        2,
                        "miniapp",
                        "refundable-bundle-key");
        payCommerceOrder(order);
        commerce.fulfillAutomaticOrder(order.getId());
        return order;
    }

    cn.iocoder.yudao.module.gym.controller.admin.sales.vo.GymRefundReviewDecisionReqVO
            refundDecision(String action, Long... ids) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.sales.vo
                        .GymRefundReviewDecisionReqVO();
        req.setDecision(action);
        req.setMemberCardIds(Arrays.asList(ids));
        req.setNote("经人工核对退款后权益处理");
        return req;
    }

    @Test
    void fullRefundStopsOnlyItsCardsAndClosesPendingPartialReviewsWithoutChangingUsage() {
        var order = refundableBundle();
        Long id = order.getId();
        var owned = salesRecords.order(id).getMemberCardIds();
        Long template = Long.valueOf(salesRecords.order(id).getItems().getFirst().getProductId());
        var manual =
                cards.issue(
                        order.getMemberId(),
                        101L,
                        template,
                        java.time.Instant.now(),
                        "approval",
                        "unrelated-manual-card");
        when(receiptVerifier.refund("partial"))
                .thenReturn(receipt(order, "RULE-PARTIAL", "FULFILL-PAY-" + id, "50.00"));
        commerce.acceptRefund(id, "partial");
        assertEquals(1, refundReviewService.list(id).size());
        assertEquals("pending", refundReviewService.list(id).getFirst().getStatus());
        var mapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberCardMapper.class);
        for (String card : owned)
            assertEquals("active", mapper.selectById(Long.valueOf(card)).getStatus());
        when(receiptVerifier.refund("rest"))
                .thenReturn(receipt(order, "RULE-REST", "FULFILL-PAY-" + id, "149.98"));
        commerce.acceptRefund(id, "rest");
        for (String card : owned) {
            var row = mapper.selectById(Long.valueOf(card));
            assertEquals("inactive", row.getStatus());
            assertEquals(10, row.getRemainingUses());
            assertServiceException(
                    () ->
                            cards.requireCard(
                                    row.getId(),
                                    order.getMemberId(),
                                    101L,
                                    java.time.Instant.now(),
                                    java.time.Instant.now().plusSeconds(60),
                                    false),
                    CARD_RIGHT_INVALID);
        }
        assertEquals("active", mapper.selectById(manual.getId()).getStatus());
        assertEquals("closed_full_refund", refundReviewService.list(id).getFirst().getStatus());
        assertEquals(new BigDecimal("199.98"), salesRecords.order(id).getRefundedAmount());
        assertEquals(3L, financialEvents.selectCount());
    }

    @Test
    void partialRefundReviewCanRetainOrStopSelectedPurchasedCardsWithImmutableDecision() {
        var order = refundableBundle();
        Long id = order.getId();
        var cards = salesRecords.order(id).getMemberCardIds();
        when(receiptVerifier.refund("p1"))
                .thenReturn(receipt(order, "MANUAL-PARTIAL-1", "FULFILL-PAY-" + id, "20"));
        commerce.acceptRefund(id, "p1");
        Long review = Long.valueOf(refundReviewService.list(id).getFirst().getId());
        var request = refundDecision("stop_selected", Long.valueOf(cards.getFirst()));
        var decided = refundReviewService.decide(id, review, request);
        assertEquals("resolved", decided.getStatus());
        assertEquals(List.of(cards.getFirst()), decided.getMemberCardIds());
        assertEquals(
                decided.getReviewedAt(),
                refundReviewService.decide(id, review, request).getReviewedAt());
        assertServiceException(
                () -> refundReviewService.decide(id, review, refundDecision("retain")),
                REFUND_REVIEW_INVALID);
        var mapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberCardMapper.class);
        assertEquals("inactive", mapper.selectById(Long.valueOf(cards.getFirst())).getStatus());
        assertEquals("active", mapper.selectById(Long.valueOf(cards.getLast())).getStatus());
        assertEquals(new BigDecimal("20.00"), salesRecords.order(id).getRefundedAmount());
        assertEquals(2L, financialEvents.selectCount());
        when(receiptVerifier.refund("p2"))
                .thenReturn(receipt(order, "MANUAL-PARTIAL-2", "FULFILL-PAY-" + id, "10"));
        commerce.acceptRefund(id, "p2");
        Long next = Long.valueOf(refundReviewService.list(id).getFirst().getId());
        assertNotEquals(review, next);
        assertEquals(
                "retain",
                refundReviewService.decide(id, next, refundDecision("retain")).getDecision());
        assertEquals("active", mapper.selectById(Long.valueOf(cards.getLast())).getStatus());
    }

    @Test
    void manualRefundReviewRejectsForeignCardsWrongOrderAndScopeAndRequiresBothPermissions() {
        var order = refundableBundle();
        Long id = order.getId();
        when(receiptVerifier.refund("p"))
                .thenReturn(receipt(order, "MANUAL-SCOPE", "FULFILL-PAY-" + id, "10"));
        commerce.acceptRefund(id, "p");
        Long review = Long.valueOf(refundReviewService.list(id).getFirst().getId());
        assertServiceException(
                () ->
                        refundReviewService.decide(
                                id, review, refundDecision("stop_selected", 999999L)),
                REFUND_REVIEW_INVALID);
        assertServiceException(
                () -> refundReviewService.decide(id, review, refundDecision("stop_selected")),
                REFUND_REVIEW_INVALID);
        assertServiceException(
                () -> refundReviewService.decide(id, review, refundDecision("retain", 1L)),
                REFUND_REVIEW_INVALID);
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> salesRecordsController.decideRefund(id, review, refundDecision("retain")));
        when(permissions.hasAnyPermissions(eq(1L), eq("gym:sales:read"))).thenReturn(true);
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> salesRecordsController.decideRefund(id, review, refundDecision("retain")));
        when(permissions.hasAnyPermissions(eq(1L), eq("gym:approvals:decide"))).thenReturn(true);
        assertEquals(
                "resolved",
                salesRecordsController
                        .decideRefund(id, review, refundDecision("retain"))
                        .getData()
                        .getStatus());
        scoped();
        assertServiceException(() -> refundReviewService.list(id), STORE_NO_PERMISSION);
        assertServiceException(
                () -> refundReviewService.decide(id, review, refundDecision("retain")),
                STORE_NO_PERMISSION);
        TenantContextHolder.setTenantId(2L);
        headquarters();
        assertServiceException(() -> refundReviewService.list(id), SALES_ORDER_NOT_EXISTS);
    }

    @Test
    void stoppingCardsAndRecordingFullRefundAreOneAtomicTransaction() {
        var order = refundableBundle();
        Long id = order.getId();
        when(receiptVerifier.refund("full"))
                .thenReturn(receipt(order, "ATOMIC-FULL", "FULFILL-PAY-" + id, "199.98"));
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.execute(
                "ALTER TABLE gym_member_card ADD CONSTRAINT prevent_stop_for_refund CHECK (status <> 'inactive')");
        try {
            assertThrows(
                    org.springframework.dao.DataIntegrityViolationException.class,
                    () -> commerce.acceptRefund(id, "full"));
            assertEquals(new BigDecimal("0.00"), salesRecords.order(id).getRefundedAmount());
            assertTrue(salesRecords.order(id).getRefunds().isEmpty());
            assertEquals(1L, financialEvents.selectCount());
        } finally {
            jdbc.execute("ALTER TABLE gym_member_card DROP CONSTRAINT prevent_stop_for_refund");
        }
        assertEquals("refunded", commerce.acceptRefund(id, "full").getStatus());
        assertEquals(2L, financialEvents.selectCount());
    }

    @Test
    void concurrentFullRefundReplayStopsAllCardsOnceAndCannotReopenPartialReview()
            throws Exception {
        var order = refundableBundle();
        Long id = order.getId();
        when(receiptVerifier.refund("partial"))
                .thenReturn(receipt(order, "CONCURRENT-PARTIAL", "FULFILL-PAY-" + id, "50.00"));
        commerce.acceptRefund(id, "partial");
        Long review = Long.valueOf(refundReviewService.list(id).getFirst().getId());
        when(receiptVerifier.refund("full"))
                .thenReturn(receipt(order, "CONCURRENT-FULL", "FULFILL-PAY-" + id, "149.98"));
        var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        var futures = new ArrayList<java.util.concurrent.Future<String>>();
        try {
            for (int i = 0; i < 6; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    setupThreadContext();
                                    try {
                                        return commerce.acceptRefund(id, "full").getStatus();
                                    } finally {
                                        TenantContextHolder.clear();
                                        org.springframework.security.core.context
                                                .SecurityContextHolder.clearContext();
                                    }
                                }));
            for (var future : futures)
                assertEquals("refunded", future.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(3L, financialEvents.selectCount());
            assertEquals(2, salesRecords.order(id).getRefunds().size());
            assertEquals("closed_full_refund", refundReviewService.list(id).getFirst().getStatus());
            assertServiceException(
                    () -> refundReviewService.decide(id, review, refundDecision("retain")),
                    REFUND_REVIEW_INVALID);
        } finally {
            pool.shutdownNow();
        }
    }

    @Resource cn.iocoder.yudao.module.gym.service.sales.GymFulfillmentQueueService fulfillmentQueue;
    @Resource cn.iocoder.yudao.module.gym.service.sales.GymFulfillmentWorker fulfillmentWorker;

    @Resource
    cn.iocoder.yudao.module.gym.service.sales.GymFulfillmentScheduler fulfillmentScheduler;

    void validFulfillmentPrincipal() {
        var user = new AdminUserDO();
        user.setId(1L);
        user.setTenantId(1L);
        user.setStatus(0);
        when(users.getUser(1L)).thenReturn(user);
        when(permissions.hasAnyPermissions(eq(1L), eq("gym:sales:read"))).thenReturn(true);
    }

    @Test
    void durableFulfillmentWaitsForFullPaymentThenRunsAsLivePrincipalAndRestoresCaller() {
        var order = commerceOrder();
        Long id = order.getId();
        assertEquals("waiting_payment", fulfillmentQueue.get(id).getStatus());
        assertTrue(fulfillmentQueue.due().isEmpty());
        when(receiptVerifier.payment("partial-auto"))
                .thenReturn(receipt(order, "AUTO-PARTIAL", null, "40.00"));
        commerce.acceptPayment(id, "partial-auto");
        assertEquals("waiting_payment", fulfillmentQueue.get(id).getStatus());
        when(receiptVerifier.payment("rest-auto"))
                .thenReturn(receipt(order, "AUTO-REST", null, "59.99"));
        commerce.acceptPayment(id, "rest-auto");
        assertEquals("pending", fulfillmentQueue.get(id).getStatus());
        publishedContractPdf();
        validFulfillmentPrincipal();
        loginAs(91L);
        Long job = Long.valueOf(fulfillmentQueue.get(id).getId());
        assertTrue(fulfillmentWorker.process(job));
        assertEquals(
                91L,
                cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils
                        .getLoginUserId());
        assertEquals("succeeded", fulfillmentQueue.get(id).getStatus());
        assertEquals(1, fulfillmentQueue.get(id).getAttemptCount());
        assertEquals("fulfilled", salesRecords.order(id).getStatus());
        assertEquals(1, salesRecords.order(id).getMemberCardIds().size());
        assertFalse(fulfillmentWorker.process(job));
    }

    @Test
    void automaticFulfillmentFailurePersistsAndSchedulerRetriesAfterConfigurationIsFixed() {
        var order = commerceOrder();
        Long id = order.getId();
        payCommerceOrder(order);
        validFulfillmentPrincipal();
        Long job = Long.valueOf(fulfillmentQueue.get(id).getId());
        assertTrue(fulfillmentWorker.process(job));
        var failed = fulfillmentQueue.get(id);
        assertEquals("failed", failed.getStatus());
        assertTrue(
                failed.getFailureReason()
                        .contains(CONTRACT_TEMPLATE_NOT_EXISTS.getCode().toString()));
        assertEquals("paid", salesRecords.order(id).getStatus());
        assertTrue(salesRecords.order(id).getMemberCardIds().isEmpty());
        assertEquals(1L, financialEvents.selectCount());
        assertFalse(fulfillmentWorker.process(job));
        publishedContractPdf();
        when(businessClock.now()).thenReturn(failed.getNextAttemptAt().plusSeconds(1));
        when(tenantFrameworkService.getTenantIds()).thenReturn(List.of(1L));
        fulfillmentScheduler.tick();
        assertEquals("succeeded", fulfillmentQueue.get(id).getStatus());
        assertEquals(2, fulfillmentQueue.get(id).getAttemptCount());
        assertNull(fulfillmentQueue.get(id).getFailureReason());
        assertEquals(1, salesRecords.order(id).getMemberCardIds().size());
        assertEquals(1L, financialEvents.selectCount());
    }

    @Test
    void jobExecutionChecksAccountStatusTenantAndLivePermissionWithoutElevatingStoreScope() {
        var order = commerceOrder();
        Long id = order.getId();
        payCommerceOrder(order);
        validFulfillmentPrincipal();
        var actor = users.getUser(1L);
        actor.setStatus(1);
        Long job = Long.valueOf(fulfillmentQueue.get(id).getId());
        fulfillmentWorker.process(job);
        assertEquals("failed", fulfillmentQueue.get(id).getStatus());
        assertTrue(salesRecords.order(id).getMemberCardIds().isEmpty());
        actor.setStatus(0);
        fulfillmentQueue.retry(id);
        when(permissions.hasAnyPermissions(eq(1L), eq("gym:sales:read"))).thenReturn(false);
        fulfillmentWorker.process(job);
        assertTrue(fulfillmentQueue.get(id).getFailureReason().contains("读取权限"));
        when(permissions.hasAnyPermissions(eq(1L), eq("gym:sales:read"))).thenReturn(true);
        fulfillmentQueue.retry(id);
        scoped();
        fulfillmentWorker.process(job);
        headquarters();
        assertEquals("failed", fulfillmentQueue.get(id).getStatus());
        assertTrue(
                fulfillmentQueue
                        .get(id)
                        .getFailureReason()
                        .contains(STORE_NO_PERMISSION.getCode().toString()));
        assertEquals("paid", salesRecords.order(id).getStatus());
        assertTrue(salesRecords.order(id).getMemberCardIds().isEmpty());
        fulfillmentQueue.retry(id);
        actor.setTenantId(2L);
        fulfillmentWorker.process(job);
        actor.setTenantId(1L);
        assertTrue(fulfillmentQueue.get(id).getFailureReason().contains("当前租户"));
    }

    @Test
    void expiredLeaseIsRecoverableAndOldWorkerCannotOverwriteNewClaim() {
        var order = commerceOrder();
        Long id = order.getId();
        payCommerceOrder(order);
        Long job = Long.valueOf(fulfillmentQueue.get(id).getId());
        var first = fulfillmentQueue.claim(job);
        assertNotNull(first);
        assertNull(fulfillmentQueue.claim(job));
        when(businessClock.now()).thenReturn(first.getLeaseUntil().plusSeconds(1));
        var second = fulfillmentQueue.claim(job);
        assertNotEquals(first.getClaimToken(), second.getClaimToken());
        assertEquals(2, second.getAttemptCount());
        fulfillmentQueue.finish(job, first.getClaimToken(), null);
        assertEquals("processing", fulfillmentQueue.get(id).getStatus());
        fulfillmentQueue.finish(job, second.getClaimToken(), "第二次执行失败测试");
        assertEquals("failed", fulfillmentQueue.get(id).getStatus());
    }

    @Test
    void refundCancelsPendingOrClaimedJobAndPreventsStaleCompletionFromIssuingRights() {
        var order = commerceOrder();
        Long id = order.getId();
        payCommerceOrder(order);
        var claimed = fulfillmentQueue.claim(Long.valueOf(fulfillmentQueue.get(id).getId()));
        when(receiptVerifier.refund("queued-refund"))
                .thenReturn(receipt(order, "QUEUED-REFUND", "FULFILL-PAY-" + id, "10.00"));
        commerce.acceptRefund(id, "queued-refund");
        assertEquals("cancelled", fulfillmentQueue.get(id).getStatus());
        fulfillmentQueue.finish(claimed.getId(), claimed.getClaimToken(), null);
        assertEquals("cancelled", fulfillmentQueue.get(id).getStatus());
        assertFalse(fulfillmentWorker.process(claimed.getId()));
        assertServiceException(() -> fulfillmentQueue.retry(id), COMMERCE_FULFILLMENT_INVALID);
        assertTrue(salesRecords.order(id).getMemberCardIds().isEmpty());
    }

    @Test
    void retryExhaustionRequiresAuthorizedManualActionAndKeepsCumulativeAttemptCount() {
        var order = commerceOrder();
        Long id = order.getId();
        payCommerceOrder(order);
        validFulfillmentPrincipal();
        Long job = Long.valueOf(fulfillmentQueue.get(id).getId());
        for (int i = 0; i < 8; i++) {
            assertTrue(fulfillmentWorker.process(job));
            var task = fulfillmentQueue.get(id);
            if (task.getNextAttemptAt() != null)
                when(businessClock.now()).thenReturn(task.getNextAttemptAt().plusSeconds(1));
        }
        var exhausted = fulfillmentQueue.get(id);
        assertEquals("failed", exhausted.getStatus());
        assertEquals(8, exhausted.getAttemptCount());
        assertNull(exhausted.getNextAttemptAt());
        assertTrue(fulfillmentQueue.due().isEmpty());
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> salesRecordsController.retryFulfillment(id));
        when(permissions.hasAnyPermissions(eq(1L), eq("gym:approvals:decide"))).thenReturn(true);
        var retried = salesRecordsController.retryFulfillment(id).getData();
        assertEquals(8, retried.getAttemptCount());
        assertEquals(16, retried.getAttemptLimit());
        assertEquals("pending", retried.getStatus());
        publishedContractPdf();
        assertTrue(fulfillmentWorker.process(job));
        assertEquals(9, fulfillmentQueue.get(id).getAttemptCount());
        assertEquals("succeeded", fulfillmentQueue.get(id).getStatus());
    }

    @Test
    void zeroPriceOrdersEnqueueWithoutPaymentAndApprovalRequiredOrdersNeverAutoEnqueue() {
        var initial = commerceOrder();
        Long template =
                Long.valueOf(
                        salesRecords.order(initial.getId()).getItems().getFirst().getProductId());
        var settings = onlineSettings(template);
        settings.getOffers().getFirst().setSalePrice(BigDecimal.ZERO);
        settings.getOffers().getFirst().setContractRequired(false);
        salesSettings.saveOnline(101L, settings);
        var free =
                commerce.prepareCardOrder(
                        101L, initial.getMemberId(), template, 1, "app", "automatic-zero-order");
        assertEquals("pending", fulfillmentQueue.get(free.getId()).getStatus());
        validFulfillmentPrincipal();
        fulfillmentWorker.process(Long.valueOf(fulfillmentQueue.get(free.getId()).getId()));
        assertEquals("fulfilled", salesRecords.order(free.getId()).getStatus());
        assertTrue(salesRecords.order(free.getId()).getPayments().isEmpty());
        assertEquals(0L, financialEvents.selectCount());
        settings.getOffers().getFirst().setContractRequired(true);
        settings.getOffers().getFirst().setApprovalRequired(true);
        salesSettings.saveOnline(101L, settings);
        var gated =
                commerce.prepareCardOrder(
                        101L, initial.getMemberId(), template, 1, "app", "approval-zero-order");
        assertNull(fulfillmentQueue.get(gated.getId()));
        assertServiceException(
                () -> fulfillmentQueue.retry(gated.getId()), COMMERCE_FULFILLMENT_INVALID);
    }

    @Test
    void paymentAndDurableTaskActivationCommitOrRollBackTogether() {
        var order = commerceOrder();
        Long id = order.getId();
        when(receiptVerifier.payment("queue-write-failure"))
                .thenReturn(receipt(order, "QUEUE-ATOMIC-PAY", null, "99.99"));
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.execute(
                "ALTER TABLE gym_fulfillment_task ADD CONSTRAINT reject_pending_task CHECK (status <> 'pending')");
        try {
            assertThrows(
                    org.springframework.dao.DataIntegrityViolationException.class,
                    () -> commerce.acceptPayment(id, "queue-write-failure"));
            assertEquals(new BigDecimal("0.00"), salesRecords.order(id).getPaidAmount());
            assertTrue(salesRecords.order(id).getPayments().isEmpty());
            assertEquals("waiting_payment", fulfillmentQueue.get(id).getStatus());
            assertEquals(0L, financialEvents.selectCount());
        } finally {
            jdbc.execute("ALTER TABLE gym_fulfillment_task DROP CONSTRAINT reject_pending_task");
        }
        commerce.acceptPayment(id, "queue-write-failure");
        assertEquals("pending", fulfillmentQueue.get(id).getStatus());
        assertEquals(1L, financialEvents.selectCount());
    }

    @Test
    void scheduledBatchSeparatesTenantsAndRestoresBothTenantAndSecurityContexts() {
        var initial = commerceOrder();
        Long template =
                Long.valueOf(
                        salesRecords.order(initial.getId()).getItems().getFirst().getProductId());
        var settings = onlineSettings(template);
        settings.getOffers().getFirst().setContractRequired(false);
        salesSettings.saveOnline(101L, settings);
        var first =
                commerce.prepareCardOrder(
                        101L,
                        initial.getMemberId(),
                        template,
                        1,
                        "miniapp",
                        "tenant-one-auto-order");
        payCommerceOrder(first);
        validFulfillmentPrincipal();
        TenantContextHolder.setTenantId(2L);
        var login = new cn.iocoder.yudao.framework.security.core.LoginUser();
        login.setId(2L);
        login.setUserType(2);
        login.setTenantId(2L);
        cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.setLoginUser(
                login, new org.springframework.mock.web.MockHttpServletRequest());
        stores.create(store(201L, "TENANT2"));
        Long member = Long.valueOf(members.create(member(201L, "13800000991")).getId());
        Long secondTemplate = Long.valueOf(cards.create(cardTemplate("count")).getId());
        var secondSettings = onlineSettings(secondTemplate);
        secondSettings.getOffers().getFirst().setContractRequired(false);
        salesSettings.saveOnline(201L, secondSettings);
        var second =
                commerce.prepareCardOrder(
                        201L, member, secondTemplate, 1, "miniapp", "tenant-two-auto-order");
        var provider =
                new cn.iocoder.yudao.module.gym.service.sales.payment.GymPaymentReceiptVerifier
                        .Receipt(
                        2L,
                        "test-provider",
                        "TENANT2-AUTO-PAY",
                        second.getOrderNo(),
                        null,
                        "CNY",
                        new BigDecimal("99.99"),
                        second.getOrderedAt().plusSeconds(1));
        when(receiptVerifier.payment("tenant-two-payment")).thenReturn(provider);
        commerce.acceptPayment(second.getId(), "tenant-two-payment");
        var secondUser = new AdminUserDO();
        secondUser.setId(2L);
        secondUser.setTenantId(2L);
        secondUser.setStatus(0);
        when(users.getUser(2L)).thenReturn(secondUser);
        when(permissions.hasAnyPermissions(eq(2L), eq("gym:sales:read"))).thenReturn(true);
        TenantContextHolder.setTenantId(1L);
        loginAs(1L);
        when(tenantFrameworkService.getTenantIds()).thenReturn(List.of(1L, 2L));
        fulfillmentScheduler.tick();
        assertEquals(1L, TenantContextHolder.getRequiredTenantId());
        assertEquals(
                1L,
                cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils
                        .getLoginUserId());
        assertEquals("fulfilled", salesRecords.order(first.getId()).getStatus());
        assertServiceException(() -> salesRecords.order(second.getId()), SALES_ORDER_NOT_EXISTS);
        TenantContextHolder.setTenantId(2L);
        assertEquals("fulfilled", salesRecords.order(second.getId()).getStatus());
        assertServiceException(() -> salesRecords.order(first.getId()), SALES_ORDER_NOT_EXISTS);
    }

    @Test
    void concurrentWorkersClaimOneLeaseAndIssueOneEntitlement() throws Exception {
        var order = commerceOrder();
        Long id = order.getId();
        payCommerceOrder(order);
        publishedContractPdf();
        validFulfillmentPrincipal();
        Long job = Long.valueOf(fulfillmentQueue.get(id).getId());
        var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        var futures = new ArrayList<java.util.concurrent.Future<Boolean>>();
        try {
            for (int i = 0; i < 6; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    setupThreadContext();
                                    try {
                                        return fulfillmentWorker.process(job);
                                    } finally {
                                        TenantContextHolder.clear();
                                        org.springframework.security.core.context
                                                .SecurityContextHolder.clearContext();
                                    }
                                }));
            int processed = 0;
            for (var future : futures)
                if (future.get(10, java.util.concurrent.TimeUnit.SECONDS)) processed++;
            assertEquals(1, processed);
            assertEquals(1, fulfillmentQueue.get(id).getAttemptCount());
            assertEquals("succeeded", fulfillmentQueue.get(id).getStatus());
            assertEquals(1, salesRecords.order(id).getMemberCardIds().size());
            assertEquals(1L, financialEvents.selectCount());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void unavailableTenantsAreSkippedAndDoNotPreventActiveTenantFulfillment() {
        var order = commerceOrder();
        payCommerceOrder(order);
        publishedContractPdf();
        validFulfillmentPrincipal();
        when(tenantFrameworkService.getTenantIds()).thenReturn(List.of(121L, 1L, 122L));
        doThrow(
                        cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil
                                .exception(
                                        cn.iocoder.yudao.module.system.enums.ErrorCodeConstants
                                                .TENANT_EXPIRE,
                                        "过期测试租户"))
                .when(tenantFrameworkService)
                .validTenant(121L);
        doThrow(
                        cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil
                                .exception(
                                        cn.iocoder.yudao.module.system.enums.ErrorCodeConstants
                                                .TENANT_DISABLE,
                                        "停用测试租户"))
                .when(tenantFrameworkService)
                .validTenant(122L);
        fulfillmentScheduler.tick();
        assertEquals("succeeded", fulfillmentQueue.get(order.getId()).getStatus());
        assertEquals(1L, TenantContextHolder.getRequiredTenantId());
        assertEquals(
                1L,
                cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils
                        .getLoginUserId());
    }

    @Test
    void cancelledOrderCannotBeManuallyRequeuedEvenWhenItsPaidAmountMatches() {
        var order = commerceOrder();
        payCommerceOrder(order);
        validFulfillmentPrincipal();
        fulfillmentWorker.process(Long.valueOf(fulfillmentQueue.get(order.getId()).getId()));
        var mapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.sales.GymBusinessOrderMapper.class);
        var current = mapper.selectById(order.getId());
        current.setStatus("cancelled");
        mapper.updateById(current);
        assertServiceException(
                () -> fulfillmentQueue.retry(order.getId()), COMMERCE_FULFILLMENT_INVALID);
        assertEquals("failed", fulfillmentQueue.get(order.getId()).getStatus());
    }

    @Test
    void manualOpeningPausesAutomaticJobAndInvalidatesPreviouslyClaimedWorker() {
        var order = commerceOrder();
        Long id = order.getId();
        payCommerceOrder(order);
        publishedContractPdf();
        var claim = fulfillmentQueue.claim(Long.valueOf(fulfillmentQueue.get(id).getId()));
        Long approver = approvalEmployee("AUTO-HOLD-APPROVER", 91L),
                template = publishedApproval("card_opening", approver);
        Long approval =
                Long.valueOf(
                        approvals
                                .create(orderOpening(template, id), "manual-auto-hold-order")
                                .getId());
        assertEquals("waiting_approval", fulfillmentQueue.get(id).getStatus());
        assertServiceException(
                () -> commerce.fulfillAutomaticOrder(id), COMMERCE_FULFILLMENT_INVALID);
        fulfillmentQueue.finish(claim.getId(), claim.getClaimToken(), "旧执行器失败");
        assertEquals("waiting_approval", fulfillmentQueue.get(id).getStatus());
        assertTrue(salesRecords.order(id).getMemberCardIds().isEmpty());
        loginAs(91L);
        approvals.decide(approval, decision("approve"));
        assertEquals("succeeded", fulfillmentQueue.get(id).getStatus());
        assertEquals(1, salesRecords.order(id).getMemberCardIds().size());
    }

    @Test
    void failedOpeningRequestRollsBackAutomaticJobHold() {
        var order = commerceOrder();
        Long id = order.getId();
        payCommerceOrder(order);
        publishedContractPdf();
        Long approver = approvalEmployee("HOLD-ROLLBACK-APPROVER", 91L),
                template = publishedApproval("card_opening", approver);
        users.getUser(91L).setStatus(1);
        assertServiceException(
                () -> approvals.create(orderOpening(template, id), "failed-hold-order"),
                APPROVAL_APPROVER_INVALID);
        assertEquals("pending", fulfillmentQueue.get(id).getStatus());
        validFulfillmentPrincipal();
        assertTrue(fulfillmentWorker.process(Long.valueOf(fulfillmentQueue.get(id).getId())));
        assertEquals("succeeded", fulfillmentQueue.get(id).getStatus());
    }

    cn.iocoder.yudao.module.gym.controller.admin.report.vo.GymReportRespVO.MemberCards reportedCard(
            String template,
            cn.iocoder.yudao.module.gym.controller.admin.report.vo.GymReportReqVO req) {
        return reports.memberCards(req).list().stream()
                .filter(r -> template.equals(r.getId()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void cardReportUsesActualCashHoldersAndConfirmedUsageWhileRefundsKeepHistory() {
        var start = java.time.Instant.parse("2026-06-01T16:00:00Z");
        when(businessClock.now()).thenReturn(start);
        var order = refundableBundle();
        Long id = order.getId();
        String template = salesRecords.order(id).getItems().getFirst().getProductId();
        Long card = Long.valueOf(salesRecords.order(id).getMemberCardIds().getFirst());
        Long coach = Long.valueOf(employees.create(employee("CARD-REPORT-COACH", 101L)).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        var from = start.plusSeconds(7200);
        Long session =
                Long.valueOf(courses.createSession(courseSession(product, coach, from)).getId());
        var booking = bookingRequest(order.getMemberId(), session, null);
        booking.setMemberCardId(card);
        Long bid = Long.valueOf(courses.createBooking(booking).getId());
        when(businessClock.now()).thenReturn(from.plusSeconds(60));
        courses.checkIn(bid);
        courses.confirmLesson(bid);
        var req = new cn.iocoder.yudao.module.gym.controller.admin.report.vo.GymReportReqVO();
        var row = reportedCard(template, req);
        assertEquals(2, row.getSoldCount());
        assertEquals(new BigDecimal("199.98"), row.getNetAmount());
        assertEquals(1, row.getHolderCount());
        assertEquals(1, row.getUsageCount());
        assertEquals(new BigDecimal("199.98"), reports.memberCards(req).summary().netAmount());
        when(businessClock.now()).thenReturn(start.plusSeconds(86400));
        when(receiptVerifier.refund("report-partial"))
                .thenReturn(
                        new cn.iocoder.yudao.module.gym.service.sales.payment
                                .GymPaymentReceiptVerifier.Receipt(
                                1L,
                                "test-provider",
                                "REPORT-PARTIAL",
                                "" + order.getOrderNo(),
                                "FULFILL-PAY-" + id,
                                "CNY",
                                new BigDecimal("50.00"),
                                start.plusSeconds(86400)));
        commerce.acceptRefund(id, "report-partial");
        req.setDate(LocalDate.of(2026, 6, 3));
        row = reportedCard(template, req);
        assertEquals(0, row.getSoldCount());
        assertEquals(new BigDecimal("-50.00"), row.getNetAmount());
        assertEquals(1, row.getHolderCount());
        assertEquals(0, row.getUsageCount());
        when(businessClock.now()).thenReturn(start.plusSeconds(172800));
        when(receiptVerifier.refund("report-full"))
                .thenReturn(
                        new cn.iocoder.yudao.module.gym.service.sales.payment
                                .GymPaymentReceiptVerifier.Receipt(
                                1L,
                                "test-provider",
                                "REPORT-FULL",
                                order.getOrderNo(),
                                "FULFILL-PAY-" + id,
                                "CNY",
                                new BigDecimal("149.98"),
                                start.plusSeconds(172800)));
        commerce.acceptRefund(id, "report-full");
        req.setDate(null);
        row = reportedCard(template, req);
        assertEquals(2, row.getSoldCount());
        assertEquals(new BigDecimal("0.00"), row.getNetAmount());
        assertEquals(0, row.getHolderCount());
        assertEquals(1, row.getUsageCount());
    }

    @Test
    void cardReportCountsOnlyCompletedPaidOrdersAndFulfilledZeroPricePurchases() {
        var order = commerceOrder();
        Long id = order.getId();
        String template = salesRecords.order(id).getItems().getFirst().getProductId();
        var req = new cn.iocoder.yudao.module.gym.controller.admin.report.vo.GymReportReqVO();
        when(receiptVerifier.payment("report-partial-pay"))
                .thenReturn(receipt(order, "REPORT-PARTIAL-PAY", null, "40.00"));
        commerce.acceptPayment(id, "report-partial-pay");
        assertEquals(0, reportedCard(template, req).getSoldCount());
        assertEquals(new BigDecimal("40.00"), reportedCard(template, req).getNetAmount());
        when(receiptVerifier.payment("report-final-pay"))
                .thenReturn(receipt(order, "REPORT-FINAL-PAY", null, "59.99"));
        commerce.acceptPayment(id, "report-final-pay");
        assertEquals(1, reportedCard(template, req).getSoldCount());
        var settings = onlineSettings(Long.valueOf(template));
        settings.getOffers().getFirst().setSalePrice(BigDecimal.ZERO);
        settings.getOffers().getFirst().setContractRequired(false);
        salesSettings.saveOnline(101L, settings);
        var free =
                commerce.prepareCardOrder(
                        101L,
                        order.getMemberId(),
                        Long.valueOf(template),
                        1,
                        "app",
                        "card-report-free-order");
        assertEquals(1, reportedCard(template, req).getSoldCount());
        commerce.fulfillAutomaticOrder(free.getId());
        assertEquals(2, reportedCard(template, req).getSoldCount());
        assertEquals(new BigDecimal("99.99"), reportedCard(template, req).getNetAmount());
        assertTrue(salesRecords.order(free.getId()).getPayments().isEmpty());
    }

    @Test
    void cardReportRestrictsFactsAndExclusiveTemplatesToAuthorizedStoreAndTenant() {
        var order = commerceOrder();
        payCommerceOrder(order);
        String own = salesRecords.order(order.getId()).getItems().getFirst().getProductId();
        stores.create(store(102L, "B"));
        var templateRequest = cardTemplate("count");
        templateRequest.setName("B店专属卡");
        templateRequest.setAccessScope("selected");
        templateRequest.setStoreIds(List.of(102L));
        Long template = Long.valueOf(cards.create(templateRequest).getId());
        Long member = Long.valueOf(members.create(member(102L, "13800000993")).getId());
        salesSettings.saveOnline(102L, onlineSettings(template));
        var foreign =
                commerce.prepareCardOrder(
                        102L, member, template, 1, "app", "report-foreign-card-order");
        payCommerceOrder(foreign);
        scoped(101L);
        var req = new cn.iocoder.yudao.module.gym.controller.admin.report.vo.GymReportReqVO();
        var report = reports.memberCards(req);
        assertEquals(new BigDecimal("99.99"), report.summary().netAmount());
        assertTrue(report.list().stream().noneMatch(r -> template.toString().equals(r.getId())));
        assertEquals(1, reportedCard(own, req).getSoldCount());
        req.setStoreId(102L);
        assertServiceException(() -> reports.memberCards(req), STORE_NO_PERMISSION);
        req.setStoreId(null);
        TenantContextHolder.setTenantId(2L);
        headquarters();
        assertEquals(0, reports.memberCards(req).total());
        assertEquals(new BigDecimal("0.00"), reports.memberCards(req).summary().netAmount());
    }

    @Test
    void historicalCashWithoutItemAllocationIsExplicitlyUnattributedInsteadOfBeingLost() {
        stores.create(store(101L, "A"));
        financialEvent(
                null,
                101L,
                "card",
                "payment",
                "25.00",
                "historical-card-unattributed",
                java.time.Instant.parse("2026-06-02T00:00:00Z"));
        var req = new cn.iocoder.yudao.module.gym.controller.admin.report.vo.GymReportReqVO();
        var report = reports.memberCards(req);
        assertEquals(1, report.total());
        assertEquals("unattributed", report.list().getFirst().getId());
        assertEquals(new BigDecimal("25.00"), report.list().getFirst().getNetAmount());
        assertEquals(new BigDecimal("25.00"), report.summary().netAmount());
    }

    @Test
    void perCardCashRemainsBalancedAfterSplitCentPaymentsAndRefunds() {
        var order = commerceOrder();
        Long id = order.getId();
        var orderMapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.sales.GymBusinessOrderMapper.class);
        var itemMapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.sales.GymOrderItemMapper.class);
        var items = itemMapper.selectList();
        var first = items.getFirst();
        first.setUnitPrice(new BigDecimal("0.01"));
        first.setPayableAmount(new BigDecimal("0.01"));
        itemMapper.updateById(first);
        var templateIds = new ArrayList<String>();
        templateIds.add(first.getProductId().toString());
        for (int i = 0; i < 2; i++) {
            var template = cardTemplate("count");
            template.setName("分钱卡种" + i);
            Long tid = Long.valueOf(cards.create(template).getId());
            var item = new cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymOrderItemDO();
            item.setOrderId(id);
            item.setProductId(tid);
            item.setProductType("card");
            item.setProductNameSnapshot(template.getName());
            item.setProductVersion(1);
            item.setQuantity(1);
            item.setUnitPrice(new BigDecimal("0.01"));
            item.setPayableAmount(new BigDecimal("0.01"));
            itemMapper.insert(item);
            templateIds.add(tid.toString());
        }
        order.setPayableAmount(new BigDecimal("0.03"));
        orderMapper.updateById(order);
        for (int i = 0; i < 3; i++) {
            String key = "cent-payment-" + i;
            when(receiptVerifier.payment(key)).thenReturn(receipt(order, key, null, "0.01"));
            commerce.acceptPayment(id, key);
        }
        for (int i = 0; i < 3; i++) {
            String key = "cent-refund-" + i;
            when(receiptVerifier.refund(key))
                    .thenReturn(receipt(order, key, "cent-payment-" + i, "0.01"));
            commerce.acceptRefund(id, key);
        }
        var req = new cn.iocoder.yudao.module.gym.controller.admin.report.vo.GymReportReqVO();
        for (String tid : templateIds) {
            var row = reportedCard(tid, req);
            assertEquals(new BigDecimal("0.00"), row.getNetAmount());
            assertEquals(1, row.getSoldCount());
        }
        assertEquals(new BigDecimal("0.00"), reports.memberCards(req).summary().netAmount());
        assertTrue(
                reports.memberCards(req).list().stream()
                        .noneMatch(r -> "unattributed".equals(r.getId())));
    }

    @Test
    void allocationWriteFailureRollsBackVerifiedCashRowsAndCanBeRetried() {
        var order = commerceOrder();
        Long id = order.getId();
        when(receiptVerifier.payment("allocation-failure"))
                .thenReturn(receipt(order, "ALLOCATION-PAY", null, "99.99"));
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.execute(
                "ALTER TABLE gym_order_item_financial_event ADD CONSTRAINT reject_allocation CHECK (amount=0)");
        try {
            assertThrows(
                    org.springframework.dao.DataIntegrityViolationException.class,
                    () -> commerce.acceptPayment(id, "allocation-failure"));
            assertEquals(0L, financialEvents.selectCount());
            assertTrue(salesRecords.order(id).getPayments().isEmpty());
            assertEquals("waiting_payment", fulfillmentQueue.get(id).getStatus());
        } finally {
            jdbc.execute(
                    "ALTER TABLE gym_order_item_financial_event DROP CONSTRAINT reject_allocation");
        }
        commerce.acceptPayment(id, "allocation-failure");
        Long event = financialEvents.selectList().getFirst().getId();
        jdbc.execute(
                "ALTER TABLE gym_order_item_financial_event ADD CONSTRAINT reject_refund_allocation CHECK (financial_event_id<="
                        + event
                        + ")");
        when(receiptVerifier.refund("allocation-refund"))
                .thenReturn(receipt(order, "ALLOCATION-REFUND", "ALLOCATION-PAY", "10.00"));
        try {
            assertThrows(
                    org.springframework.dao.DataIntegrityViolationException.class,
                    () -> commerce.acceptRefund(id, "allocation-refund"));
            assertEquals(new BigDecimal("0.00"), salesRecords.order(id).getRefundedAmount());
            assertEquals(1L, financialEvents.selectCount());
            assertTrue(salesRecords.order(id).getRefunds().isEmpty());
        } finally {
            jdbc.execute(
                    "ALTER TABLE gym_order_item_financial_event DROP CONSTRAINT reject_refund_allocation");
        }
    }

    @Test
    void archivedTemplateRetainsHistoricalReportMetadataAndCurrentHolderCounts() {
        var order = refundableBundle();
        String template = salesRecords.order(order.getId()).getItems().getFirst().getProductId();
        applicationContext
                .getBean(cn.iocoder.yudao.module.gym.dal.mysql.card.GymCardTemplateMapper.class)
                .deleteById(Long.valueOf(template));
        var row =
                reportedCard(
                        template,
                        new cn.iocoder.yudao.module.gym.controller.admin.report.vo
                                .GymReportReqVO());
        assertEquals("联调卡模板", row.getName());
        assertEquals(1, row.getVersion());
        assertEquals(2, row.getSoldCount());
        assertEquals(1, row.getHolderCount());
        assertEquals(new BigDecimal("199.98"), row.getNetAmount());
        var historical =
                new cn.iocoder.yudao.module.gym.controller.admin.report.vo.GymReportReqVO();
        historical.setDate(LocalDate.of(2000, 1, 1));
        var holderOnly = reportedCard(template, historical);
        assertEquals(1, holderOnly.getVersion());
        assertEquals(1, holderOnly.getHolderCount());
        assertEquals(0, holderOnly.getSoldCount());
    }

    @Test
    void memberCardReportPermissionDateValidationAndPagingDoNotChangeSummary() {
        var order = commerceOrder();
        payCommerceOrder(order);
        cards.create(cardTemplate("period"));
        var req = new cn.iocoder.yudao.module.gym.controller.admin.report.vo.GymReportReqVO();
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> reportController.memberCards(req));
        when(permissions.hasAnyPermissions(eq(1L), eq("gym:reports:read"))).thenReturn(true);
        var all = reportController.memberCards(req).getData();
        req.setPageSize(1);
        req.setPageNo(2);
        var page = reports.memberCards(req);
        assertEquals(1, page.list().size());
        assertEquals(all.summary(), page.summary());
        assertEquals(all.total(), page.total());
        req.setDate(LocalDate.of(2026, 6, 2));
        req.setMonth("2026-06");
        assertServiceException(() -> reports.memberCards(req), BUSINESS_DATE_INVALID);
    }

    cn.iocoder.yudao.module.gym.dal.dataobject.sales.GymSubscriptionDO signingSubscription() {
        stores.create(store(101L, "A"));
        var row = salesSubscription(101L, "private-subscription-provider-id");
        row.setMemberId(Long.valueOf(members.create(member(101L, "13800000987")).getId()));
        row.setCardTemplateId(Long.valueOf(cards.create(cardTemplate("period")).getId()));
        applicationContext
                .getBean(cn.iocoder.yudao.module.gym.dal.mysql.sales.GymSubscriptionMapper.class)
                .updateById(row);
        return row;
    }

    cn.iocoder.yudao.module.gym.controller.admin.approval.vo.GymApprovalCreateReqVO
            subscriptionSigningRequest(Long subscriptionId) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.approval.vo
                        .GymApprovalCreateReqVO();
        req.setStoreId(101L);
        req.setTemplateId(publishedApproval("contract", approvalEmployee("SIGNER", 91L)));
        req.setBusinessType("contract");
        req.setSourceType("subscriptions");
        req.setSourceId(subscriptionId);
        req.setTitle("包月签约审批");
        return req;
    }

    @Test
    void subscriptionSigningFreezesPrivateContractAndDoesNotAuthorizeDebitOrIssueRights() {
        var sub = signingSubscription();
        publishedContractPdf();
        var req = subscriptionSigningRequest(sub.getId());
        var created = approvals.create(req, "subscription-signing");
        var snapshot = created.getRequestSnapshot();
        assertEquals("subscriptions", snapshot.get("sourceType"));
        assertEquals(sub.getId().toString(), snapshot.get("subscriptionId"));
        assertEquals(1, created.getAttachments().size());
        assertEquals(created.getId(), approvals.create(req, "subscription-signing").getId());
        var contract = contracts.get(Long.valueOf(snapshot.get("sourceId").toString()));
        assertEquals("subscriptions", contract.getSourceType());
        assertEquals(sub.getId().toString(), contract.getSourceId());
        assertFalse(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(contract)
                        .contains("private-subscription-provider-id"));
        loginAs(91L);
        var done = approvals.decide(Long.valueOf(created.getId()), decision("approve"));
        assertEquals("approved", done.getStatus());
        assertEquals("unconfigured", done.getBusinessResult().get("signingStatus"));
        var signed = contracts.get(Long.valueOf(contract.getId()));
        assertEquals("unsigned", signed.getStatus());
        assertNull(signed.getSignedAt());
        assertNull(signed.getSignedFileId());
        assertEquals("approved", signed.getApprovalStatus());
        assertEquals(
                done.getId(),
                approvals.decide(Long.valueOf(created.getId()), decision("approve")).getId());
        assertEquals("unconfigured", salesRecords.subscription(sub.getId()).getStatus());
        assertNull(salesRecords.subscription(sub.getId()).getSignedAt());
        assertEquals(
                0L,
                cards.cards(
                                sub.getMemberId(),
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        assertEquals(
                0L,
                salesRecords
                        .cycles(
                                sub.getId(),
                                new cn.iocoder.yudao.module.gym.controller.admin.sales.vo
                                        .GymSalesRecordPageReqVO())
                        .getTotal());
    }

    @Test
    void subscriptionCancellationOrTermsChangePreventsApprovalIncludingGenericContractPath() {
        var sub = signingSubscription();
        publishedContractPdf();
        var req = subscriptionSigningRequest(sub.getId());
        var created = approvals.create(req, "subscription-terms");
        var mapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.sales.GymSubscriptionMapper.class);
        sub.setAmount(new BigDecimal("199.99"));
        mapper.updateById(sub);
        loginAs(91L);
        assertServiceException(
                () -> approvals.decide(Long.valueOf(created.getId()), decision("approve")),
                CONTRACT_SOURCE_CONFLICT);
        assertEquals(
                "pending",
                approvals.get(Long.valueOf(created.getId())).getTasks().getFirst().getStatus());
        sub.setAmount(new BigDecimal("99.99"));
        sub.setStatus("cancelled");
        mapper.updateById(sub);
        assertServiceException(
                () -> approvals.decide(Long.valueOf(created.getId()), decision("approve")),
                CONTRACT_STATE_INVALID);
        var contract =
                contracts.get(
                        Long.valueOf(created.getRequestSnapshot().get("sourceId").toString()));
        assertServiceException(
                () ->
                        contracts.approveSigning(
                                Long.valueOf(contract.getId()),
                                999L,
                                Long.valueOf(contract.getUnsignedFileId())),
                CONTRACT_STATE_INVALID);
        assertNull(contracts.get(Long.valueOf(contract.getId())).getApprovedApprovalId());
    }

    @Test
    void subscriptionSigningRejectsIdentityReplacementStoreMismatchAndAlreadyAuthorizedSources() {
        var sub = signingSubscription();
        publishedContractPdf();
        var req = subscriptionSigningRequest(sub.getId());
        req.setMemberId(sub.getMemberId());
        assertServiceException(
                () -> approvals.create(req, "sub-replace"), APPROVAL_REQUEST_INVALID);
        req.setMemberId(null);
        stores.create(store(102L, "B"));
        req.setStoreId(102L);
        assertServiceException(() -> approvals.create(req, "sub-store"), APPROVAL_REQUEST_INVALID);
        req.setStoreId(101L);
        var mapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.sales.GymSubscriptionMapper.class);
        sub.setStatus("active");
        mapper.updateById(sub);
        assertServiceException(() -> approvals.create(req, "sub-active"), CONTRACT_STATE_INVALID);
        sub.setStatus("unconfigured");
        sub.setSignedAt(java.time.Instant.now());
        mapper.updateById(sub);
        assertServiceException(() -> approvals.create(req, "sub-signed"), CONTRACT_STATE_INVALID);
        sub.setSignedAt(null);
        sub.setCancelledAt(java.time.Instant.now());
        mapper.updateById(sub);
        assertServiceException(
                () -> approvals.create(req, "sub-cancelled"), CONTRACT_STATE_INVALID);
        assertEquals(
                0L,
                contracts
                        .page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
    }

    @Test
    void subscriptionSigningMissingTemplateOrApproverRollsBackContractAndAttachments() {
        var sub = signingSubscription();
        var req = subscriptionSigningRequest(sub.getId());
        assertServiceException(
                () -> approvals.create(req, "sub-no-template"), CONTRACT_TEMPLATE_NOT_EXISTS);
        publishedContractPdf();
        when(users.getUser(91L)).thenReturn(null);
        long fileCount = files.selectCount();
        assertServiceException(
                () -> approvals.create(req, "sub-no-approver"), APPROVAL_APPROVER_INVALID);
        assertEquals(fileCount, files.selectCount());
        assertEquals(
                0L,
                contracts
                        .page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        assertEquals(
                0L,
                approvals
                        .page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
    }

    @Test
    void subscriptionSigningHonorsStoreAndTenantScope() {
        var sub = signingSubscription();
        publishedContractPdf();
        var req = subscriptionSigningRequest(sub.getId());
        scoped(102L);
        assertServiceException(() -> approvals.create(req, "sub-scope"), STORE_NO_PERMISSION);
        headquarters();
        TenantContextHolder.setTenantId(2L);
        assertServiceException(
                () -> contracts.subscriptionSigningSource(sub.getId(), 101L),
                SUBSCRIPTION_NOT_EXISTS);
    }

    @Test
    void concurrentSubscriptionSigningRequestsCreateOneContractAndApproval() throws Exception {
        var sub = signingSubscription();
        publishedContractPdf();
        var req = subscriptionSigningRequest(sub.getId());
        var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        var futures = new ArrayList<java.util.concurrent.Future<String>>();
        try {
            for (int i = 0; i < 6; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    setupThreadContext();
                                    try {
                                        return approvals.create(req, "sub-concurrent").getId();
                                    } finally {
                                        TenantContextHolder.clear();
                                        org.springframework.security.core.context
                                                .SecurityContextHolder.clearContext();
                                    }
                                }));
            var ids = new HashSet<String>();
            for (var future : futures)
                ids.add(future.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, ids.size());
            assertEquals(
                    1L,
                    contracts
                            .page(
                                    new cn.iocoder.yudao.module.gym.controller.admin.common
                                            .GymBusinessPageReqVO())
                            .getTotal());
            assertEquals(
                    1L,
                    approvals
                            .page(
                                    new cn.iocoder.yudao.module.gym.controller.admin.common
                                            .GymBusinessPageReqVO())
                            .getTotal());
        } finally {
            pool.shutdownNow();
        }
    }

    @Resource
    cn.iocoder.yudao.module.gym.service.contract.GymContractReminderWorker automaticReminders;

    @Resource
    cn.iocoder.yudao.module.gym.service.contract.GymContractReminderScheduler
            automaticReminderScheduler;

    Long automaticReminderContract() {
        stores.create(store(101L, "A"));
        publishedContractPdf();
        Long member = Long.valueOf(members.create(member(101L, "13800000988")).getId());
        cards.issue(
                member,
                101L,
                Long.valueOf(cards.create(cardTemplate("count")).getId()),
                java.time.Instant.now(),
                "approval",
                "auto-reminder-fixture");
        return Long.valueOf(
                contracts
                        .page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getList()
                        .getFirst()
                        .getId());
    }

    @Test
    void automaticRemindersUseFrozenAdvanceDaysAndBeijingDayIdempotency() {
        Long id = automaticReminderContract();
        var row = contractMapper.selectById(id);
        row.setDueAt(java.time.Instant.parse("2026-06-20T04:00:00Z"));
        contractMapper.updateById(row);
        var changed = contractMapper.selectById(id);
        var template = contractTemplateMapper.selectById(changed.getTemplateId());
        template.setReminderDays(0);
        contractTemplateMapper.updateById(template);
        when(businessClock.now()).thenReturn(java.time.Instant.parse("2026-06-16T15:59:59Z"));
        assertFalse(automaticReminders.process(id, "app"));
        when(businessClock.now()).thenReturn(java.time.Instant.parse("2026-06-16T16:00:00Z"));
        assertTrue(automaticReminders.process(id, "app"));
        assertFalse(automaticReminders.process(id, "app"));
        var history =
                contracts.reminders(
                        id,
                        new cn.iocoder.yudao.module.gym.controller.admin.common
                                .GymBusinessPageReqVO());
        assertEquals(1L, history.getTotal());
        var reminder = history.getList().getFirst();
        assertEquals("failed", reminder.getStatus());
        assertTrue(reminder.getFailureReason().contains("未发送"));
        assertEquals(
                0L,
                applicationContext
                        .getBean(
                                cn.iocoder.yudao.module.gym.dal.mysql.contract
                                        .GymContractReminderMapper.class)
                        .selectById(Long.valueOf(reminder.getId()))
                        .getRequestedBy());
        assertTrue(
                contracts.get(id).getEvents().stream()
                        .anyMatch(e -> "automatic_reminder_failed".equals(e.getType())));
        assertNull(contractMapper.selectById(id).getSignedAt());
        when(businessClock.now()).thenReturn(java.time.Instant.parse("2026-06-17T16:00:00Z"));
        assertTrue(automaticReminders.process(id, "app"));
        assertEquals(
                2L,
                contracts
                        .reminders(
                                id,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
    }

    @Test
    void automaticRemindersStopForSignedCancelledOrInactiveMemberAndStore() {
        Long id = automaticReminderContract();
        var row = contractMapper.selectById(id);
        when(businessClock.now()).thenReturn(row.getDueAt().plusSeconds(86400));
        row.setStatus("signed");
        contractMapper.updateById(row);
        assertFalse(automaticReminders.process(id, "app"));
        row.setStatus("cancelled");
        contractMapper.updateById(row);
        assertFalse(automaticReminders.process(id, "app"));
        row.setStatus("unsigned");
        row.setSignedAt(businessClock.now());
        contractMapper.updateById(row);
        assertFalse(automaticReminders.process(id, "app"));
        row.setSignedAt(null);
        row.setCancelledAt(businessClock.now());
        contractMapper.updateById(row);
        assertFalse(automaticReminders.process(id, "app"));
        row.setCancelledAt(null);
        contractMapper.updateById(row);
        var memberMapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberMapper.class);
        var member = memberMapper.selectById(row.getMemberId());
        member.setStatus("inactive");
        memberMapper.updateById(member);
        assertFalse(automaticReminders.process(id, "app"));
        member.setStatus("active");
        memberMapper.updateById(member);
        var storeMapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper.class);
        var store = storeMapper.selectById(101L);
        store.setStatus("inactive");
        storeMapper.updateById(store);
        assertFalse(automaticReminders.process(id, "app"));
        assertEquals(
                0L,
                contracts
                        .reminders(
                                id,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
    }

    @Test
    void concurrentAutomaticReminderWorkersPersistOnlyOneDailyAttempt() throws Exception {
        Long id = automaticReminderContract();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        var futures = new ArrayList<java.util.concurrent.Future<Boolean>>();
        try {
            for (int i = 0; i < 6; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    setupThreadContext();
                                    try {
                                        return automaticReminders.process(id, "app");
                                    } finally {
                                        TenantContextHolder.clear();
                                        org.springframework.security.core.context
                                                .SecurityContextHolder.clearContext();
                                    }
                                }));
            int attempts = 0;
            for (var f : futures) if (f.get(10, java.util.concurrent.TimeUnit.SECONDS)) attempts++;
            assertEquals(1, attempts);
            assertEquals(
                    1L,
                    contracts
                            .reminders(
                                    id,
                                    new cn.iocoder.yudao.module.gym.controller.admin.common
                                            .GymBusinessPageReqVO())
                            .getTotal());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void automaticReminderAuditFailureRollsBackAndCanRetry() {
        Long id = automaticReminderContract();
        var jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(
                        applicationContext.getBean(javax.sql.DataSource.class));
        jdbc.execute(
                "ALTER TABLE gym_contract_event ADD CONSTRAINT auto_reminder_test_reject CHECK (actor_user_id <> 0)");
        try {
            assertThrows(
                    org.springframework.dao.DataIntegrityViolationException.class,
                    () -> automaticReminders.process(id, "app"));
            assertEquals(
                    0L,
                    contracts
                            .reminders(
                                    id,
                                    new cn.iocoder.yudao.module.gym.controller.admin.common
                                            .GymBusinessPageReqVO())
                            .getTotal());
        } finally {
            jdbc.execute(
                    "ALTER TABLE gym_contract_event DROP CONSTRAINT auto_reminder_test_reject");
        }
        assertTrue(automaticReminders.process(id, "app"));
    }

    @Test
    void automaticReminderSchedulerScansPastFirstPageAndRestoresTenantContext() {
        Long id = automaticReminderContract();
        var original = contractMapper.selectById(id);
        var future = businessClock.now().plusSeconds(30 * 86400);
        original.setDueAt(future);
        var frozen =
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                        original.getTemplateSnapshot(),
                        cn.iocoder.yudao.module.gym.dal.dataobject.contract.GymContractTemplateDO
                                .class);
        frozen.setReminderDays(0);
        original.setTemplateSnapshot(
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(frozen));
        contractMapper.updateById(original);
        for (int i = 1; i <= 101; i++) {
            var copy =
                    cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(
                            original,
                            cn.iocoder.yudao.module.gym.dal.dataobject.contract.GymContractDO
                                    .class);
            copy.setId(null);
            copy.setContractNo("SCAN-" + i);
            copy.setSourceId(10000L + i);
            if (i == 101) {
                frozen.setReminderDays(365);
                copy.setTemplateSnapshot(
                        cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(frozen));
            }
            contractMapper.insert(copy);
        }
        when(tenantFrameworkService.getTenantIds()).thenReturn(List.of(1L, 2L, 121L));
        doThrow(
                        cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil
                                .exception(
                                        cn.iocoder.yudao.module.system.enums.ErrorCodeConstants
                                                .TENANT_EXPIRE,
                                        "过期测试"))
                .when(tenantFrameworkService)
                .validTenant(121L);
        automaticReminderScheduler.tick();
        assertEquals(1L, TenantContextHolder.getRequiredTenantId());
        assertEquals(
                1L,
                cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils
                        .getLoginUserId());
        var reminderMapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.contract.GymContractReminderMapper
                                .class);
        assertEquals(1L, reminderMapper.selectCount());
        automaticReminderScheduler.tick();
        assertEquals(1L, reminderMapper.selectCount());
    }

    @Test
    void automaticReminderTenantIsolationAndReservedManualKeys() {
        Long id = automaticReminderContract();
        TenantContextHolder.setTenantId(2L);
        assertFalse(automaticReminders.process(id, "app"));
        TenantContextHolder.setTenantId(1L);
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.contract.vo
                        .GymContractReminderReqVO();
        req.setChannel("app");
        assertServiceException(
                () -> contracts.remind(id, req, "auto:2026-06-17:app"), CONTRACT_REMINDER_INVALID);
        assertThrows(
                IllegalArgumentException.class, () -> automaticReminders.process(id, "invalid"));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        assertTrue(automaticReminders.process(id, "app"));
        var automatic =
                applicationContext
                        .getBean(
                                cn.iocoder.yudao.module.gym.dal.mysql.contract
                                        .GymContractReminderMapper.class)
                        .selectList()
                        .getFirst();
        assertEquals("0", automatic.getCreator());
        assertEquals("0", automatic.getUpdater());
        var event =
                applicationContext
                        .getBean(
                                cn.iocoder.yudao.module.gym.dal.mysql.contract
                                        .GymContractEventMapper.class)
                        .selectList()
                        .stream()
                        .filter(e -> "automatic_reminder_failed".equals(e.getType()))
                        .findFirst()
                        .orElseThrow();
        assertEquals("0", event.getCreator());
        assertEquals("0", event.getUpdater());
    }

    @Resource cn.iocoder.yudao.module.gym.service.member.GymMemberCoachService memberCoaches;

    Long responsibleEmployee(Long user) {
        Long id = approvalEmployee("RESP-" + user, user);
        var account = users.getUser(user);
        account.setTenantId(1L);
        return id;
    }

    cn.iocoder.yudao.module.gym.controller.admin.member.vo.GymMemberCoachesReqVO responsibility(
            Long... employees) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.member.vo.GymMemberCoachesReqVO();
        req.setEmployeeIds(Arrays.asList(employees));
        return req;
    }

    void personalScope(Long user) {
        loginAs(user);
        var data = new DeptDataPermissionRespDTO();
        data.setSelf(true);
        when(permissions.getDeptDataPermission(any())).thenReturn(data);
    }

    @Test
    void responsibleMemberListDetailsAndRightsNeverGrantWholeStore() {
        stores.create(store(101L, "A"));
        Long employee = responsibleEmployee(91L);
        Long own = Long.valueOf(members.create(member(101L, "13800000801")).getId());
        Long other = Long.valueOf(members.create(member(101L, "13800000802")).getId());
        Long template = Long.valueOf(cards.create(cardTemplate("count")).getId());
        cards.issue(own, 101L, template, java.time.Instant.now(), "approval", "responsible-card");
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, employee)).getId());
        courses.issuePack(
                own, product, java.time.Instant.now(), null, "approval", "responsible-pack");
        memberCoaches.replace(own, 101L, responsibility(employee));
        personalScope(91L);
        var req = new cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO();
        assertEquals(1L, members.page(req).getTotal());
        assertEquals(own.toString(), members.page(req).getList().getFirst().getId());
        assertEquals(List.of("101"), members.get(own).getStoreIds());
        assertEquals(1, cards.allCards(own, req).size());
        assertEquals(1, courses.allPacks(own, req).size());
        assertEquals("门店A", courses.allPacks(own, req).getFirst().getStoreName());
        assertServiceException(() -> members.get(other), STORE_NO_PERMISSION);
        assertServiceException(() -> cards.allCards(other, req), STORE_NO_PERMISSION);
        assertServiceException(
                () -> applicationContext.getBean(GymDataScope.class).check(101L),
                STORE_NO_PERMISSION);
        req.setStoreId(101L);
        assertEquals(1L, members.page(req).getTotal());
        req.setStoreId(102L);
        assertServiceException(() -> members.page(req), STORE_NO_PERMISSION);
    }

    @Test
    void responsibilityIsStoreSpecificAcrossSharedMemberMeasurementsAndContracts() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long employee = responsibleEmployee(91L);
        Long member = Long.valueOf(members.create(member(101L, "13800000803")).getId());
        var membership = new cn.iocoder.yudao.module.gym.dal.dataobject.card.GymMemberStoreDO();
        membership.setMemberId(member);
        membership.setStoreId(102L);
        membership.setJoinedAt(java.time.Instant.now());
        applicationContext
                .getBean(cn.iocoder.yudao.module.gym.dal.mysql.card.GymMemberStoreMapper.class)
                .insert(membership);
        publishedContractPdf();
        Long template = Long.valueOf(cards.create(cardTemplate("count")).getId());
        cards.issue(
                member,
                101L,
                template,
                java.time.Instant.now(),
                "approval",
                "responsible-own-contract");
        cards.issue(
                member,
                102L,
                template,
                java.time.Instant.now(),
                "approval",
                "responsible-foreign-contract");
        var own = records.createMeasurement(measurement(member, 101L));
        var foreign = records.createMeasurement(measurement(member, 102L));
        var all =
                contracts
                        .page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getList();
        Long foreignContract =
                Long.valueOf(
                        all.stream()
                                .filter(c -> "102".equals(c.getStoreId()))
                                .findFirst()
                                .orElseThrow()
                                .getId());
        memberCoaches.replace(member, 101L, responsibility(employee));
        personalScope(91L);
        var req = new cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO();
        assertEquals(List.of("101"), members.get(member).getStoreIds());
        assertEquals(1L, records.measurements(req).getTotal());
        assertEquals(own.getId(), records.measurement(Long.valueOf(own.getId())).getId());
        assertServiceException(
                () -> records.measurement(Long.valueOf(foreign.getId())), STORE_NO_PERMISSION);
        assertEquals(1L, contracts.page(req).getTotal());
        assertEquals(
                "101",
                contracts
                        .get(Long.valueOf(contracts.page(req).getList().getFirst().getId()))
                        .getStoreId());
        assertServiceException(() -> contracts.get(foreignContract), STORE_NO_PERMISSION);
        assertEquals(
                2L,
                records.measurements(
                                        new cn.iocoder.yudao.module.gym.controller.admin.common
                                                .GymBusinessPageReqVO())
                                .getTotal()
                        + contracts.page(req).getTotal());
    }

    @Test
    void responsibleStaffCanRecordOwnMemberMeasurementsButCannotAttachOtherUploadersReport() {
        stores.create(store(101L, "A"));
        Long employee = responsibleEmployee(91L);
        Long member = Long.valueOf(members.create(member(101L, "13800000804")).getId());
        var report = file(101L, "body_report");
        memberCoaches.replace(member, 101L, responsibility(employee));
        personalScope(91L);
        var req = measurement(member, 101L);
        req.setReportFileId(report.getId());
        assertServiceException(() -> records.createMeasurement(req), MEASUREMENT_FILE_INVALID);
        req.setReportFileId(null);
        var created = records.createMeasurement(req);
        assertEquals(member.toString(), created.getMemberId());
        var patch =
                new cn.iocoder.yudao.module.gym.controller.admin.member.vo
                        .GymMeasurementSaveReqVO();
        patch.setNote("负责员工记录");
        assertEquals(
                "负责员工记录", records.patchMeasurement(Long.valueOf(created.getId()), patch).getNote());
    }

    @Test
    void responsibilityRechecksEmploymentAccountAndEmployeeStatusOnEveryRead() {
        stores.create(store(101L, "A"));
        Long employee = responsibleEmployee(91L);
        Long member = Long.valueOf(members.create(member(101L, "13800000805")).getId());
        memberCoaches.replace(member, 101L, responsibility(employee));
        personalScope(91L);
        assertEquals(member.toString(), members.get(member).getId());
        var account = users.getUser(91L);
        account.setStatus(1);
        assertServiceException(() -> members.get(member), STORE_NO_PERMISSION);
        account.setStatus(0);
        account.setTenantId(2L);
        assertServiceException(() -> members.get(member), STORE_NO_PERMISSION);
        account.setTenantId(1L);
        cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUser()
                .setUserType(1);
        assertServiceException(() -> members.get(member), STORE_NO_PERMISSION);
        loginAs(91L);
        var employeeMapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.employee.GymEmployeeMapper.class);
        var row = employeeMapper.selectById(employee);
        row.setStatus("inactive");
        employeeMapper.updateById(row);
        assertServiceException(() -> members.get(member), STORE_NO_PERMISSION);
        row.setStatus("active");
        employeeMapper.updateById(row);
        var employmentMapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.employee.GymEmployeeStoreMapper
                                .class);
        employmentMapper.deleteRelations(employee);
        assertServiceException(() -> members.get(member), STORE_NO_PERMISSION);
    }

    @Test
    void revocationIsAuditedAndAnAssignmentAloneDoesNotGrantWithoutSelfScope() {
        stores.create(store(101L, "A"));
        Long employee = responsibleEmployee(91L);
        Long member = Long.valueOf(members.create(member(101L, "13800000806")).getId());
        memberCoaches.replace(member, 101L, responsibility(employee));
        scoped();
        loginAs(91L);
        assertEquals(
                0L,
                members.page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        assertServiceException(() -> members.get(member), STORE_NO_PERMISSION);
        personalScope(91L);
        assertEquals(member.toString(), members.get(member).getId());
        loginAs(1L);
        headquarters();
        memberCoaches.replace(member, 101L, responsibility());
        var mapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberCoachMapper.class);
        assertEquals(1L, mapper.selectCount());
        assertFalse(mapper.selectList().getFirst().getActive());
        personalScope(91L);
        assertServiceException(() -> members.get(member), STORE_NO_PERMISSION);
    }

    @Test
    void responsibleAssignmentsValidateSameStoreActiveAccountAndDistinctEmployees() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long employee = responsibleEmployee(91L);
        Long member = Long.valueOf(members.create(member(101L, "13800000807")).getId());
        assertServiceException(
                () -> memberCoaches.replace(member, 101L, responsibility(employee, employee)),
                MEMBER_COACH_INVALID);
        Long unbound = Long.valueOf(employees.create(employee("UNBOUND", 101L)).getId());
        assertServiceException(
                () -> memberCoaches.replace(member, 101L, responsibility(unbound)),
                MEMBER_COACH_INVALID);
        Long other = Long.valueOf(employees.create(employee("FOREIGN", 102L)).getId());
        assertServiceException(
                () -> memberCoaches.replace(member, 101L, responsibility(other)),
                MEMBER_COACH_INVALID);
        users.getUser(91L).setStatus(1);
        assertServiceException(
                () -> memberCoaches.replace(member, 101L, responsibility(employee)),
                MEMBER_COACH_INVALID);
        users.getUser(91L).setStatus(0);
        assertServiceException(
                () -> memberCoaches.replace(member, 102L, responsibility(employee)),
                STORE_NO_PERMISSION);
        assertServiceException(
                () -> memberCoaches.replace(99999L, 101L, responsibility(employee)),
                MEMBER_NOT_EXISTS);
    }

    @Test
    void responsibleAssignmentApiAndTenantDoNotAllowSelfPromotion() {
        stores.create(store(101L, "A"));
        Long employee = responsibleEmployee(91L);
        Long member = Long.valueOf(members.create(member(101L, "13800000808")).getId());
        var controller =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.controller.admin.member.GymMemberController
                                .class);
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> controller.replaceCoaches(member, 101L, responsibility(employee)));
        when(permissions.hasAnyPermissions(eq(1L), eq("gym:members:write"))).thenReturn(true);
        assertEquals(
                1,
                controller.replaceCoaches(member, 101L, responsibility(employee)).getData().size());
        personalScope(91L);
        when(permissions.hasAnyPermissions(eq(91L), eq("gym:members:write"))).thenReturn(true);
        assertServiceException(
                () -> controller.replaceCoaches(member, 101L, responsibility()),
                STORE_NO_PERMISSION);
        TenantContextHolder.setTenantId(2L);
        headquarters();
        assertServiceException(() -> memberCoaches.list(member, 101L), MEMBER_NOT_EXISTS);
        assertEquals(0, memberCoaches.selfResponsibilities().size());
    }

    @Test
    void concurrentResponsibleAssignmentsRemainOneAuditableRelation() throws Exception {
        stores.create(store(101L, "A"));
        Long employee = responsibleEmployee(91L);
        Long member = Long.valueOf(members.create(member(101L, "13800000809")).getId());
        var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
        try {
            for (int i = 0; i < 6; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    setupThreadContext();
                                    try {
                                        return memberCoaches
                                                .replace(member, 101L, responsibility(employee))
                                                .size();
                                    } finally {
                                        TenantContextHolder.clear();
                                        org.springframework.security.core.context
                                                .SecurityContextHolder.clearContext();
                                    }
                                }));
            for (var f : futures) assertEquals(1, f.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(
                    1L,
                    applicationContext
                            .getBean(
                                    cn.iocoder.yudao.module.gym.dal.mysql.member
                                            .GymMemberCoachMapper.class)
                            .selectCount());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void responsibleCoachCanBookCancelCheckInAndConfirmOnlyResponsibleMembers() {
        stores.create(store(101L, "A"));
        Long employee = responsibleEmployee(91L);
        Long own = Long.valueOf(members.create(member(101L, "13800000810")).getId());
        Long other = Long.valueOf(members.create(member(101L, "13800000811")).getId());
        Long template = Long.valueOf(cards.create(cardTemplate("count")).getId());
        var start = java.time.Instant.now().plusSeconds(7200);
        var card =
                cards.issue(
                        own,
                        101L,
                        template,
                        java.time.Instant.now().minusSeconds(60),
                        "approval",
                        "responsible-booking-own");
        var otherCard =
                cards.issue(
                        other,
                        101L,
                        template,
                        java.time.Instant.now().minusSeconds(60),
                        "approval",
                        "responsible-booking-other");
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, employee)).getId());
        Long session =
                Long.valueOf(
                        courses.createSession(courseSession(product, employee, start)).getId());
        Long otherSession =
                Long.valueOf(
                        courses.createSession(
                                        courseSession(product, employee, start.plusSeconds(7200)))
                                .getId());
        var otherRequest = bookingRequest(other, otherSession, null);
        otherRequest.setMemberCardId(otherCard.getId());
        Long foreign = Long.valueOf(courses.createBooking(otherRequest).getId());
        memberCoaches.replace(own, 101L, responsibility(employee));
        personalScope(91L);
        var req = bookingRequest(own, session, null);
        req.setMemberCardId(card.getId());
        Long id = Long.valueOf(courses.createBooking(req).getId());
        var cancel =
                new cn.iocoder.yudao.module.gym.controller.admin.course.vo.GymBookingSaveReqVO();
        cancel.setStatus("cancelled");
        assertEquals("cancelled", courses.patchBooking(id, cancel).getStatus());
        assertEquals(id.toString(), courses.createBooking(req).getId());
        var page = new cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO();
        assertEquals(1L, courses.bookings(page).getTotal());
        assertServiceException(() -> courses.booking(foreign), STORE_NO_PERMISSION);
        assertServiceException(() -> courses.patchBooking(foreign, cancel), STORE_NO_PERMISSION);
        when(businessClock.now()).thenReturn(start.minusSeconds(300));
        courses.checkIn(id);
        when(businessClock.now()).thenReturn(start.plusSeconds(60));
        courses.confirmLesson(id);
        assertEquals(1L, courses.checkins(page).getTotal());
        assertEquals(1L, courses.lessons(page).getTotal());
        page.setDate(
                start.atZone(cn.iocoder.yudao.module.gym.service.common.GymDateRange.ZONE)
                        .toLocalDate());
        assertEquals(1L, courses.checkinSummary(page).get("entries"));
        assertServiceException(() -> courses.checkIn(foreign), STORE_NO_PERMISSION);
        assertServiceException(() -> courses.confirmLesson(foreign), STORE_NO_PERMISSION);
        loginAs(1L);
        headquarters();
        memberCoaches.replace(own, 101L, responsibility());
        personalScope(91L);
        assertServiceException(() -> courses.booking(id), STORE_NO_PERMISSION);
        assertEquals(
                0L,
                courses.lessons(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
    }

    @Test
    void responsiblePrivatePackBookingUsesMemberScopeWithoutOpeningStoreAdministration() {
        stores.create(store(101L, "A"));
        Long employee = responsibleEmployee(91L);
        Long own = Long.valueOf(members.create(member(101L, "13800000812")).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, employee)).getId());
        var start = java.time.Instant.now().plusSeconds(7200);
        Long session =
                Long.valueOf(
                        courses.createSession(courseSession(product, employee, start)).getId());
        var pack =
                courses.issuePack(
                        own,
                        product,
                        java.time.Instant.now().minusSeconds(60),
                        null,
                        "approval",
                        "responsible-pack-booking");
        memberCoaches.replace(own, 101L, responsibility(employee));
        personalScope(91L);
        Long id =
                Long.valueOf(
                        courses.createBooking(bookingRequest(own, session, pack.getId())).getId());
        assertEquals(
                1,
                courses.allPacks(
                                own,
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getFirst()
                        .getReservedSessions());
        when(businessClock.now()).thenReturn(start.minusSeconds(300));
        courses.checkIn(id);
        when(businessClock.now()).thenReturn(start.plusSeconds(60));
        courses.confirmLesson(id);
        assertEquals(
                1L,
                courses.lessons(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        assertEquals(session.toString(), courses.session(session).getId());
    }

    @Resource cn.iocoder.yudao.module.gym.service.file.GymMemberFileAccess memberFileAccess;

    @Test
    void responsibleContractFilesRequireBusinessReadAndCurrentResponsibility() {
        stores.create(store(101L, "A"));
        Long employee = responsibleEmployee(91L);
        publishedContractPdf();
        Long own = Long.valueOf(members.create(member(101L, "13800000813")).getId());
        Long other = Long.valueOf(members.create(member(101L, "13800000814")).getId());
        Long template = Long.valueOf(cards.create(cardTemplate("count")).getId());
        cards.issue(own, 101L, template, java.time.Instant.now(), "approval", "file-own");
        cards.issue(other, 101L, template, java.time.Instant.now(), "approval", "file-other");
        var rows =
                contracts
                        .page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getList();
        var ownContract =
                rows.stream()
                        .filter(c -> own.toString().equals(c.getMemberId()))
                        .findFirst()
                        .orElseThrow();
        var foreign =
                rows.stream()
                        .filter(c -> other.toString().equals(c.getMemberId()))
                        .findFirst()
                        .orElseThrow();
        var original = files.selectById(Long.valueOf(ownContract.getUnsignedFileId()));
        memberCoaches.replace(own, 101L, responsibility(employee));
        personalScope(91L);
        assertFalse(memberFileAccess.canRead(original));
        when(permissions.hasAnyPermissions(eq(91L), eq("gym:contracts:read"))).thenReturn(true);
        assertTrue(memberFileAccess.canRead(original));
        assertFalse(
                memberFileAccess.canRead(
                        files.selectById(Long.valueOf(foreign.getUnsignedFileId()))));
        var unbound = file(101L, "contract");
        unbound.setUploaderId(91L);
        files.updateById(unbound);
        assertFalse(memberFileAccess.canRead(unbound));
        loginAs(1L);
        headquarters();
        memberCoaches.replace(own, 101L, responsibility());
        personalScope(91L);
        assertFalse(memberFileAccess.canRead(original));
    }

    @Test
    void responsibleReportReadRequiresBoundMeasurementAndCannotRebindAnotherMembersUpload() {
        stores.create(store(101L, "A"));
        Long employee = responsibleEmployee(91L);
        Long own = Long.valueOf(members.create(member(101L, "13800000815")).getId());
        Long other = Long.valueOf(members.create(member(101L, "13800000816")).getId());
        var ownFile = file(101L, "body_report");
        var otherFile = file(101L, "body_report");
        var unbound = file(101L, "body_report");
        for (var f : List.of(otherFile, unbound)) {
            f.setUploaderId(91L);
            files.updateById(f);
        }
        var ownMeasure = measurement(own, 101L);
        ownMeasure.setReportFileId(ownFile.getId());
        var savedOwn = records.createMeasurement(ownMeasure);
        var otherMeasure = measurement(other, 101L);
        otherMeasure.setReportFileId(otherFile.getId());
        records.createMeasurement(otherMeasure);
        memberCoaches.replace(own, 101L, responsibility(employee));
        personalScope(91L);
        when(permissions.hasAnyPermissions(eq(91L), eq("gym:measurements:read"))).thenReturn(true);
        assertTrue(memberFileAccess.canRead(ownFile));
        assertFalse(memberFileAccess.canRead(otherFile));
        assertFalse(memberFileAccess.canRead(unbound));
        var update =
                new cn.iocoder.yudao.module.gym.controller.admin.member.vo
                        .GymMeasurementSaveReqVO();
        update.setNote("保留管理员关联原件");
        assertEquals(
                ownFile.getId().toString(),
                records.patchMeasurement(Long.valueOf(savedOwn.getId()), update).getReportFileId());
        var rebound = measurement(own, 101L);
        rebound.setReportFileId(otherFile.getId());
        assertServiceException(() -> records.createMeasurement(rebound), MEASUREMENT_FILE_INVALID);
        when(permissions.hasAnyPermissions(eq(91L), eq("gym:measurements:read"))).thenReturn(false);
        assertFalse(memberFileAccess.canRead(ownFile));
        TenantContextHolder.setTenantId(2L);
        assertFalse(memberFileAccess.canRead(ownFile));
    }

    @Test
    void sharedPrivateReportWithAnUnauthorizedMemberFailsClosedForSelf() {
        stores.create(store(101L, "A"));
        Long employee = responsibleEmployee(91L);
        Long own = Long.valueOf(members.create(member(101L, "13800000817")).getId());
        Long other = Long.valueOf(members.create(member(101L, "13800000818")).getId());
        var shared = file(101L, "body_report");
        var a = measurement(own, 101L);
        a.setReportFileId(shared.getId());
        records.createMeasurement(a);
        var b = measurement(other, 101L);
        b.setReportFileId(shared.getId());
        records.createMeasurement(b);
        memberCoaches.replace(own, 101L, responsibility(employee));
        personalScope(91L);
        when(permissions.hasAnyPermissions(eq(91L), eq("gym:measurements:read"))).thenReturn(true);
        assertFalse(memberFileAccess.canRead(shared));
    }

    @Test
    void responsibleBlacklistListMatchesDetailsWithoutOpeningOtherMembers() {
        stores.create(store(101L, "A"));
        Long employee = responsibleEmployee(91L);
        Long own = Long.valueOf(members.create(member(101L, "13800000819")).getId());
        Long other = Long.valueOf(members.create(member(101L, "13800000820")).getId());
        var a = records.createBlacklist(blacklist(own));
        var b = records.createBlacklist(blacklist(other));
        memberCoaches.replace(own, 101L, responsibility(employee));
        personalScope(91L);
        var req = new cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO();
        assertEquals(1L, records.blacklists(req).getTotal());
        assertEquals(a.getId(), records.blacklists(req).getList().getFirst().getId());
        assertEquals(a.getId(), records.blacklist(Long.valueOf(a.getId())).getId());
        assertServiceException(
                () -> records.blacklist(Long.valueOf(b.getId())), STORE_NO_PERMISSION);
        req.setStoreId(101L);
        assertEquals(1L, records.blacklists(req).getTotal());
        req.setStoreId(102L);
        assertServiceException(() -> records.blacklists(req), STORE_NO_PERMISSION);
    }

    @Test
    void
            responsibleReportUploadRequiresWriteAndActiveResponsibilityWithoutOpeningOtherCategories() {
        stores.create(store(101L, "A"));
        Long employee = responsibleEmployee(91L);
        Long own = Long.valueOf(members.create(member(101L, "13800000821")).getId());
        memberCoaches.replace(own, 101L, responsibility(employee));
        personalScope(91L);
        assertFalse(memberFileAccess.canUploadReport(101L, "body_report"));
        when(permissions.hasAnyPermissions(eq(91L), eq("gym:measurements:write"))).thenReturn(true);
        assertTrue(memberFileAccess.canUploadReport(101L, "body_report"));
        assertFalse(memberFileAccess.canUploadReport(102L, "body_report"));
        for (String category : List.of("contract", "approval", "image", "video"))
            assertFalse(memberFileAccess.canUploadReport(101L, category));
        assertFalse(memberFileAccess.canUploadReport(null, "body_report"));
        loginAs(1L);
        headquarters();
        memberCoaches.replace(own, 101L, responsibility());
        personalScope(91L);
        assertFalse(memberFileAccess.canUploadReport(101L, "body_report"));
    }

    @Resource cn.iocoder.yudao.module.gym.service.member.GymTrainingPlanService trainingPlans;

    @Resource
    cn.iocoder.yudao.module.gym.controller.admin.member.GymTrainingPlanController
            trainingController;

    private cn.iocoder.yudao.module.gym.controller.admin.member.vo.GymTrainingPlanSaveReqVO
            training(Long member, Long store) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.member.vo
                        .GymTrainingPlanSaveReqVO();
        req.setMemberId(member);
        req.setStoreId(store);
        req.setTitle("训练计划");
        req.setContent("周一力量训练，周三恢复训练");
        return req;
    }

    @Test
    void trainingArchiveCrudKeepsIdentityAndSupportsExplicitAttachmentRemovalAndFilters() {
        stores.create(store(101L, "A"));
        Long member = Long.valueOf(members.create(member(101L, "13800000822")).getId());
        var file = file(101L, "training_plan");
        var req = training(member, 101L);
        req.setFileId(file.getId());
        var saved = trainingPlans.create(req);
        assertEquals("active", saved.getStatus());
        assertEquals(file.getId().toString(), saved.getFileId());
        var patch =
                new cn.iocoder.yudao.module.gym.controller.admin.member.vo
                        .GymTrainingPlanSaveReqVO();
        patch.setTitle("更新计划");
        patch.setStatus("archived");
        patch.setFileId(null);
        var updated = trainingPlans.patch(Long.valueOf(saved.getId()), patch);
        assertNull(updated.getFileId());
        assertEquals("archived", updated.getStatus());
        assertEquals(saved.getMemberId(), updated.getMemberId());
        var page = new cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO();
        page.setStatus("active");
        assertEquals(0L, trainingPlans.page(page).getTotal());
        page.setStatus("archived");
        page.setKeyword("更新计划");
        assertEquals(1L, trainingPlans.page(page).getTotal());
        page.setKeyword("不存在");
        assertEquals(0L, trainingPlans.page(page).getTotal());
        patch.setMemberId(member + 1);
        assertServiceException(
                () -> trainingPlans.patch(Long.valueOf(saved.getId()), patch),
                TRAINING_PLAN_INVALID);
    }

    @Test
    void trainingArchiveRejectsEmptyOrForeignAttachmentsAndCrossTenantReads() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long member = Long.valueOf(members.create(member(101L, "13800000823")).getId());
        var req = training(member, 101L);
        req.setContent("  ");
        assertServiceException(() -> trainingPlans.create(req), TRAINING_PLAN_INVALID);
        req.setFileId(file(102L, "training_plan").getId());
        assertServiceException(() -> trainingPlans.create(req), TRAINING_PLAN_FILE_INVALID);
        req.setFileId(file(101L, "body_report").getId());
        assertServiceException(() -> trainingPlans.create(req), TRAINING_PLAN_FILE_INVALID);
        req.setFileId(file(101L, "training_plan").getId());
        Long id = Long.valueOf(trainingPlans.create(req).getId());
        TenantContextHolder.setTenantId(2L);
        assertServiceException(() -> trainingPlans.get(id), TRAINING_PLAN_NOT_EXISTS);
        assertEquals(
                0L,
                trainingPlans
                        .page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
    }

    @Test
    void responsibleTrainingArchiveAndAttachmentRequireLiveMemberScope() {
        stores.create(store(101L, "A"));
        Long employee = responsibleEmployee(91L);
        Long own = Long.valueOf(members.create(member(101L, "13800000824")).getId());
        Long other = Long.valueOf(members.create(member(101L, "13800000825")).getId());
        var original = file(101L, "training_plan");
        var foreign = file(101L, "training_plan");
        foreign.setUploaderId(91L);
        files.updateById(foreign);
        var ownReq = training(own, 101L);
        ownReq.setFileId(original.getId());
        var ownPlan = trainingPlans.create(ownReq);
        var otherReq = training(other, 101L);
        otherReq.setFileId(foreign.getId());
        var otherPlan = trainingPlans.create(otherReq);
        memberCoaches.replace(own, 101L, responsibility(employee));
        personalScope(91L);
        when(permissions.hasAnyPermissions(eq(91L), eq("gym:measurements:read"))).thenReturn(true);
        when(permissions.hasAnyPermissions(eq(91L), eq("gym:measurements:write"))).thenReturn(true);
        var page = new cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO();
        assertEquals(1L, trainingPlans.page(page).getTotal());
        assertTrue(memberFileAccess.canRead(original));
        assertFalse(memberFileAccess.canRead(foreign));
        assertTrue(memberFileAccess.canUploadReport(101L, "training_plan"));
        var patch =
                new cn.iocoder.yudao.module.gym.controller.admin.member.vo
                        .GymTrainingPlanSaveReqVO();
        patch.setContent("保留管理员已关联的附件");
        assertEquals(
                original.getId().toString(),
                trainingPlans.patch(Long.valueOf(ownPlan.getId()), patch).getFileId());
        var rebind = training(own, 101L);
        rebind.setFileId(foreign.getId());
        assertServiceException(() -> trainingPlans.create(rebind), TRAINING_PLAN_FILE_INVALID);
        assertServiceException(
                () -> trainingPlans.get(Long.valueOf(otherPlan.getId())), STORE_NO_PERMISSION);
        var ownUpload = file(101L, "training_plan");
        ownUpload.setUploaderId(91L);
        files.updateById(ownUpload);
        assertFalse(memberFileAccess.canRead(ownUpload));
        var newPlan = training(own, 101L);
        newPlan.setFileId(ownUpload.getId());
        trainingPlans.create(newPlan);
        assertTrue(memberFileAccess.canRead(ownUpload));
        loginAs(1L);
        headquarters();
        memberCoaches.replace(own, 101L, responsibility());
        personalScope(91L);
        assertFalse(memberFileAccess.canRead(original));
        assertServiceException(
                () -> trainingPlans.get(Long.valueOf(ownPlan.getId())), STORE_NO_PERMISSION);
        assertEquals(0L, trainingPlans.page(page).getTotal());
    }

    @Test
    void trainingControllerRequiresArchivePermissionsAndSharedFileFailsClosed() {
        stores.create(store(101L, "A"));
        Long employee = responsibleEmployee(91L);
        Long own = Long.valueOf(members.create(member(101L, "13800000826")).getId());
        Long other = Long.valueOf(members.create(member(101L, "13800000827")).getId());
        var shared = file(101L, "training_plan");
        var a = training(own, 101L);
        a.setFileId(shared.getId());
        trainingPlans.create(a);
        var b = training(other, 101L);
        b.setFileId(shared.getId());
        trainingPlans.create(b);
        memberCoaches.replace(own, 101L, responsibility(employee));
        personalScope(91L);
        when(permissions.hasAnyPermissions(eq(91L), eq("gym:measurements:read"))).thenReturn(true);
        assertFalse(memberFileAccess.canRead(shared));
        var page = new cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO();
        when(permissions.hasAnyPermissions(eq(91L), eq("gym:measurements:read"))).thenReturn(false);
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> trainingController.page(page));
        when(permissions.hasAnyPermissions(eq(91L), eq("gym:measurements:read"))).thenReturn(true);
        assertEquals(1L, trainingController.page(page).getData().getTotal());
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> trainingController.create(training(own, 101L)));
        when(permissions.hasAnyPermissions(eq(91L), eq("gym:measurements:write"))).thenReturn(true);
        assertEquals(
                own.toString(),
                trainingController.create(training(own, 101L)).getData().getMemberId());
    }

    @Test
    void futureHireOrStoreJoiningCannotReadResponsibleMembersBeforeTheBeijingStartDate() {
        stores.create(store(101L, "A"));
        Long employee = responsibleEmployee(91L);
        Long own = Long.valueOf(members.create(member(101L, "13800000828")).getId());
        memberCoaches.replace(own, 101L, responsibility(employee));
        personalScope(91L);
        var today = java.time.LocalDate.of(2026, 10, 15);
        when(businessClock.now())
                .thenReturn(
                        today.atStartOfDay(
                                        cn.iocoder.yudao.module.gym.service.common.GymDateRange
                                                .ZONE)
                                .toInstant());
        var em =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.employee.GymEmployeeMapper.class);
        var e = em.selectById(employee);
        e.setHiredOn(today.plusDays(1));
        em.updateById(e);
        assertTrue(memberCoaches.selfResponsibilities().isEmpty());
        e.setHiredOn(today);
        em.updateById(e);
        assertFalse(memberCoaches.selfResponsibilities().isEmpty());
        var esm =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.employee.GymEmployeeStoreMapper
                                .class);
        var es =
                esm.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query
                                                        .LambdaQueryWrapper<
                                                cn.iocoder.yudao.module.gym.dal.dataobject.employee
                                                        .GymEmployeeStoreDO>()
                                        .eq(
                                                cn.iocoder.yudao.module.gym.dal.dataobject.employee
                                                                .GymEmployeeStoreDO
                                                        ::getEmployeeId,
                                                employee))
                        .getFirst();
        es.setJoinedOn(today.plusDays(1));
        esm.updateById(es);
        assertTrue(memberCoaches.selfResponsibilities().isEmpty());
        when(businessClock.now())
                .thenReturn(
                        today.plusDays(1)
                                .atStartOfDay(
                                        cn.iocoder.yudao.module.gym.service.common.GymDateRange
                                                .ZONE)
                                .toInstant());
        assertFalse(memberCoaches.selfResponsibilities().isEmpty());
    }

    @Test
    void responsibleCourseSelectionOnlyShowsOwnTeachingAndKeepsStoreWritesDenied() {
        stores.create(store(101L, "A"));
        stores.create(store(102L, "B"));
        Long employee = responsibleEmployee(91L);
        Long otherCoach = approvalEmployee("OTHER-TEACHER", 92L);
        Long own = Long.valueOf(members.create(member(101L, "13800000829")).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, employee)).getId());
        Long otherProduct =
                Long.valueOf(courses.createProduct(courseProduct(101L, otherCoach)).getId());
        var start = java.time.Instant.now().plusSeconds(7200);
        Long session =
                Long.valueOf(
                        courses.createSession(courseSession(product, employee, start)).getId());
        Long otherSession =
                Long.valueOf(
                        courses.createSession(courseSession(otherProduct, otherCoach, start))
                                .getId());
        Long template = Long.valueOf(cards.create(cardTemplate("count")).getId());
        var card =
                cards.issue(
                        own,
                        101L,
                        template,
                        java.time.Instant.now().minusSeconds(60),
                        "approval",
                        "self-selection");
        memberCoaches.replace(own, 101L, responsibility(employee));
        personalScope(91L);
        var req = new cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO();
        assertEquals(1L, courses.products(req).getTotal());
        assertEquals(1L, courses.sessions(req).getTotal());
        assertEquals(1L, courses.coaches(req).getTotal());
        assertEquals(product.toString(), courses.product(product).getId());
        assertEquals(session.toString(), courses.session(session).getId());
        req.setStoreId(101L);
        assertEquals(1L, courses.sessions(req).getTotal());
        req.setCoachId(otherCoach);
        assertEquals(0L, courses.sessions(req).getTotal());
        req.setCoachId(null);
        req.setStoreId(102L);
        assertServiceException(() -> courses.sessions(req), STORE_NO_PERMISSION);
        assertServiceException(() -> courses.session(otherSession), STORE_NO_PERMISSION);
        assertServiceException(() -> courses.product(otherProduct), STORE_NO_PERMISSION);
        assertServiceException(
                () -> courses.createProduct(courseProduct(101L, employee)), STORE_NO_PERMISSION);
        assertServiceException(
                () ->
                        courses.patchSession(
                                session,
                                new cn.iocoder.yudao.module.gym.controller.admin.course.vo
                                        .GymCourseSessionSaveReqVO()),
                STORE_NO_PERMISSION);
        var booking = bookingRequest(own, otherSession, null);
        booking.setMemberCardId(card.getId());
        assertServiceException(() -> courses.createBooking(booking), STORE_NO_PERMISSION);
        booking.setSessionId(session);
        assertEquals(own.toString(), courses.createBooking(booking).getMemberId());
        loginAs(1L);
        headquarters();
        memberCoaches.replace(own, 101L, responsibility());
        personalScope(91L);
        assertServiceException(() -> courses.session(session), STORE_NO_PERMISSION);
        assertEquals(
                0L,
                courses.sessions(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
    }

    private cn.iocoder.yudao.module.gym.controller.admin.approval.vo.GymApprovalCreateReqVO
            priceRequest(Long template, Long product) {
        var req =
                new cn.iocoder.yudao.module.gym.controller.admin.approval.vo
                        .GymApprovalCreateReqVO();
        req.setStoreId(101L);
        req.setTemplateId(template);
        req.setBusinessType("price_change");
        req.setTitle("本人改价申请");
        req.setSourceType("course-products");
        req.setSourceId(product);
        req.setRequestedPrice(new BigDecimal("150.00"));
        return req;
    }

    private void selfPriceScope(Long user, Long approver) {
        personalScope(user);
        var broad = new DeptDataPermissionRespDTO();
        broad.setAll(true);
        when(permissions.getDeptDataPermission(approver)).thenReturn(broad);
    }

    @Test
    void responsibleSelfCanRequestOwnProductPriceButOnlyManagerApprovalChangesThePrice() {
        stores.create(store(101L, "A"));
        Long coach = responsibleEmployee(91L), manager = approvalEmployee("PRICE-MANAGER", 92L);
        Long template = publishedApproval("price_change", manager);
        Long own = Long.valueOf(members.create(member(101L, "13800000830")).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        memberCoaches.replace(own, 101L, responsibility(coach));
        selfPriceScope(91L, 92L);
        assertEquals(1, approvalTemplates.options().size());
        var req = priceRequest(template, product);
        var request = approvals.create(req, "self-price-request");
        Long id = Long.valueOf(request.getId());
        assertEquals(id.toString(), approvals.create(req, "self-price-request").getId());
        assertEquals(new BigDecimal("200.00"), courses.product(product).getPrice());
        assertFalse(request.getCanDecide());
        var page = new cn.iocoder.yudao.module.gym.controller.admin.common.GymBusinessPageReqVO();
        assertEquals(1L, approvals.page(page).getTotal());
        assertEquals(id.toString(), approvals.get(id).getId());
        assertServiceException(
                () -> approvals.decide(id, decision("approve")), STORE_NO_PERMISSION);
        var direct =
                new cn.iocoder.yudao.module.gym.controller.admin.course.vo
                        .GymCourseProductSaveReqVO();
        direct.setPrice(new BigDecimal("150.00"));
        assertServiceException(() -> courses.patchProduct(product, direct), STORE_NO_PERMISSION);
        loginAs(92L);
        assertEquals("approved", approvals.decide(id, decision("approve")).getStatus());
        selfPriceScope(91L, 92L);
        assertEquals("approved", approvals.get(id).getStatus());
        assertEquals(new BigDecimal("150.00"), courses.product(product).getPrice());
    }

    @Test
    void selfPriceApproverValidityIgnoresApplicantDepartmentFilterButNeverTenant() {
        stores.create(store(101L, "A"));
        Long coach = responsibleEmployee(91L), manager = approvalEmployee("PRICE-MANAGER", 92L);
        Long template = publishedApproval("price_change", manager);
        Long own = Long.valueOf(members.create(member(101L, "13800000834")).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        memberCoaches.replace(own, 101L, responsibility(coach));
        selfPriceScope(91L, 92L);
        var account = new AdminUserDO();
        account.setId(92L);
        account.setTenantId(1L);
        account.setStatus(0);
        when(users.getUser(92L))
                .thenAnswer(
                        call -> {
                            var context =
                                    cn.iocoder.yudao.framework.datapermission.core.aop
                                            .DataPermissionContextHolder.get();
                            return context != null && !context.enable() ? account : null;
                        });
        assertEquals(
                "pending",
                approvals
                        .create(priceRequest(template, product), "internal-approver-validity")
                        .getStatus());
        assertNull(
                cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionContextHolder
                        .get());
        account.setTenantId(2L);
        assertServiceException(
                () -> approvals.create(priceRequest(template, product), "foreign-tenant-approver"),
                APPROVAL_APPROVER_INVALID);
        assertNull(
                cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionContextHolder
                        .get());
    }

    @Test
    void selfPriceRequestsDoNotExposeOtherApplicantsOrOtherBusinessTypesInTheSameStore() {
        stores.create(store(101L, "A"));
        Long coach = responsibleEmployee(91L), manager = approvalEmployee("PRICE-MANAGER", 92L);
        Long template = publishedApproval("price_change", manager),
                manualTemplate = publishedApproval("other_configured", manager);
        Long own = Long.valueOf(members.create(member(101L, "13800000831")).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        Long foreign = Long.valueOf(courses.createProduct(courseProduct(101L, manager)).getId());
        var otherRequest = approvals.create(priceRequest(template, product), "other-applicant");
        memberCoaches.replace(own, 101L, responsibility(coach));
        selfPriceScope(91L, 92L);
        var ownRequest = approvals.create(priceRequest(template, product), "own-applicant");
        assertEquals(
                List.of(ownRequest.getId()),
                approvals
                        .page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getList()
                        .stream()
                        .map(r -> r.getId())
                        .toList());
        assertServiceException(
                () -> approvals.get(Long.valueOf(otherRequest.getId())), STORE_NO_PERMISSION);
        assertServiceException(
                () -> approvals.create(priceRequest(template, foreign), "foreign-product"),
                STORE_NO_PERMISSION);
        assertEquals(1, approvalTemplates.options().size());
        var manual = priceRequest(manualTemplate, product);
        manual.setBusinessType("other_configured");
        manual.setSourceId(null);
        manual.setSourceType(null);
        manual.setRequestedPrice(null);
        assertServiceException(() -> approvals.create(manual, "manual-self"), STORE_NO_PERMISSION);
        var attachment = file(101L, "contract");
        var req = priceRequest(template, product);
        req.setAttachmentIds(List.of(attachment.getId()));
        assertServiceException(
                () -> approvals.create(req, "self-borrowed-file"), APPROVAL_ATTACHMENT_INVALID);
    }

    @Test
    void selfPriceVisibilityRevokesWithResponsibilityAndCannotCrossTenantOrChangeOwnerIdentity() {
        stores.create(store(101L, "A"));
        Long coach = responsibleEmployee(91L), manager = approvalEmployee("PRICE-MANAGER", 92L);
        Long template = publishedApproval("price_change", manager);
        Long own = Long.valueOf(members.create(member(101L, "13800000832")).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        memberCoaches.replace(own, 101L, responsibility(coach));
        selfPriceScope(91L, 92L);
        Long id =
                Long.valueOf(
                        approvals
                                .create(priceRequest(template, product), "revoke-price-request")
                                .getId());
        var mapper =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.dal.mysql.approval.GymApprovalMapper.class);
        assertEquals(coach, mapper.selectById(id).getSourceCoachId());
        TenantContextHolder.setTenantId(2L);
        assertServiceException(() -> approvals.get(id), APPROVAL_NOT_EXISTS);
        TenantContextHolder.setTenantId(1L);
        loginAs(1L);
        headquarters();
        memberCoaches.replace(own, 101L, responsibility());
        selfPriceScope(91L, 92L);
        assertEquals(
                0L,
                approvals
                        .page(
                                new cn.iocoder.yudao.module.gym.controller.admin.common
                                        .GymBusinessPageReqVO())
                        .getTotal());
        assertTrue(approvalTemplates.options().isEmpty());
        assertServiceException(() -> approvals.get(id), STORE_NO_PERMISSION);
        assertServiceException(
                () -> approvals.create(priceRequest(template, product), "revoke-price-request"),
                STORE_NO_PERMISSION);
    }

    @Test
    void selfPriceControllerStillRequiresExplicitCreateAndReadPermissions() {
        stores.create(store(101L, "A"));
        Long coach = responsibleEmployee(91L), manager = approvalEmployee("PRICE-MANAGER", 92L);
        Long template = publishedApproval("price_change", manager);
        Long own = Long.valueOf(members.create(member(101L, "13800000833")).getId());
        Long product = Long.valueOf(courses.createProduct(courseProduct(101L, coach)).getId());
        memberCoaches.replace(own, 101L, responsibility(coach));
        selfPriceScope(91L, 92L);
        var controller =
                applicationContext.getBean(
                        cn.iocoder.yudao.module.gym.controller.admin.approval.GymApprovalController
                                .class);
        var req = priceRequest(template, product);
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> controller.create(req, "role-price-request"));
        when(permissions.hasAnyPermissions(eq(91L), eq("gym:approvals:create"))).thenReturn(true);
        var saved = controller.create(req, "role-price-request").getData();
        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> controller.get(Long.valueOf(saved.getId())));
        when(permissions.hasAnyPermissions(eq(91L), eq("gym:approvals:read"))).thenReturn(true);
        assertEquals(saved.getId(), controller.get(Long.valueOf(saved.getId())).getData().getId());
    }
}
