package cn.iocoder.yudao.module.gym.service;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.module.gym.controller.admin.common.GymPageReqVO;
import cn.iocoder.yudao.module.gym.controller.admin.store.vo.*;
import cn.iocoder.yudao.module.gym.controller.admin.employee.vo.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.file.GymFileDO;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.GymDataScope;
import cn.iocoder.yudao.module.gym.service.store.GymStoreService;
import cn.iocoder.yudao.module.gym.service.employee.GymEmployeeService;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import jakarta.annotation.Resource;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 实际 H2 / MyBatis / 事务 / 租户拦截器；只 mock 跨模块 system 服务。 */
@Import({GymStoreService.class, GymEmployeeService.class, GymDataScope.class, cn.iocoder.yudao.module.gym.controller.admin.store.GymStoreController.class, cn.iocoder.yudao.module.gym.controller.admin.employee.GymEmployeeController.class, GymOrganizationServiceTest.Config.class})
public class GymOrganizationServiceTest extends BaseDbUnitTest {
    @Resource GymStoreService stores;
    @Resource GymEmployeeService employees;
    @Resource GymFileMapper files;
    @Resource GymStoreMapper storeMapper;
    @Resource org.springframework.context.ApplicationContext applicationContext;
    @Resource cn.iocoder.yudao.module.gym.controller.admin.store.GymStoreController storeController;
    @Resource cn.iocoder.yudao.module.gym.controller.admin.employee.GymEmployeeController employeeController;
    @MockitoBean DeptService depts;
    @MockitoBean AdminUserService users;
    @MockitoBean PermissionApi permissions;
    @Configuration(proxyBeanMethods = false)
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    @org.mybatis.spring.annotation.MapperScan(basePackageClasses = cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper.class)
    static class Config {
        @Bean(name = "ss") cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService security(PermissionApi api) {
            return new cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkServiceImpl(api);
        }
        @Bean LocalValidatorFactoryBean validator() { return new LocalValidatorFactoryBean(); }
        @Bean static BeanPostProcessor tenantInterceptor() {
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if (bean instanceof MybatisPlusInterceptor interceptor) {
                        var chain = new ArrayList<>(interceptor.getInterceptors());
                        chain.add(0, new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
                        interceptor.setInterceptors(chain);
                    }
                    return bean;
                }
            };
        }
    }
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var login = new cn.iocoder.yudao.framework.security.core.LoginUser(); login.setId(1L); login.setUserType(2); login.setTenantId(1L);
        cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.setLoginUser(login, new org.springframework.mock.web.MockHttpServletRequest());
        headquarters();
        when(depts.getDept(anyLong())).thenAnswer(i -> { DeptDO d = new DeptDO(); d.setId(i.getArgument(0)); d.setStatus(0); return d; });
    }
    @AfterEach void cleanupContext() { TenantContextHolder.clear(); org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    @Test void bothFileMappersCoexistAndStoreMediaUsesGymMapper() {
        assertInstanceOf(cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper.class, applicationContext.getBean("fileMapper"));
        assertInstanceOf(GymFileMapper.class, applicationContext.getBean("gymFileMapper"));
        stores.create(store(101L, "A"));
        var file = file(101L, "image");
        stores.saveMedia(101L, media(file.getId(), true));
        assertEquals(file.getId().toString(), stores.get(101L).getMedia().getFirst().getFileId());
    }
    void headquarters() {
        DeptDataPermissionRespDTO data = new DeptDataPermissionRespDTO(); data.setAll(true);
        when(permissions.getDeptDataPermission(any())).thenReturn(data);
    }
    void scoped(Long... ids) {
        DeptDataPermissionRespDTO data = new DeptDataPermissionRespDTO(); data.setAll(false); data.setDeptIds(new HashSet<>(Arrays.asList(ids)));
        when(permissions.getDeptDataPermission(any())).thenReturn(data);
    }
    GymStoreSaveReqVO store(Long dept, String code) {
        GymStoreSaveReqVO req = new GymStoreSaveReqVO(); req.setDeptId(dept); req.setCode(code); req.setName("门店"+code);
        req.setAreaSqm(new BigDecimal("123.45")); req.setAddress("测试地址"); req.setFeatures(List.of("私教", "力量,训练"));
        GymStoreSaveReqVO.BusinessHours h = new GymStoreSaveReqVO.BusinessHours(); h.setOpens("06:00"); h.setCloses("23:00"); req.setBusinessHours(h);
        return req;
    }
    GymEmployeeSaveReqVO employee(String no, Long... ids) {
        GymEmployeeSaveReqVO req = new GymEmployeeSaveReqVO(); req.setEmployeeNo(no); req.setName("测试员工"); req.setPhone("13800000000"); req.setHiredOn(LocalDate.of(2026,1,1));
        List<GymEmployeeSaveReqVO.StoreAssignment> list = new ArrayList<>();
        for (int i=0;i<ids.length;i++) { var a = new GymEmployeeSaveReqVO.StoreAssignment(); a.setStoreId(ids[i]); a.setIsPrimary(i==0); a.setIsManager(false); a.setJoinedOn(req.getHiredOn()); list.add(a); }
        req.setStoreAssignments(list); return req;
    }
    @Test void storeRoundTripAndPartialUpdate() {
        var created = stores.create(store(101L,"A")); assertEquals("101", created.getId());
        assertEquals(List.of("私教", "力量,训练"), stores.get(101L).getFeatures());
        var patch = new GymStoreSaveReqVO(); patch.setName("新名称"); patch.setFeatures(List.of());
        stores.patch(101L, patch);
        var saved = stores.get(101L); assertEquals("新名称",saved.getName()); assertEquals("A",saved.getCode()); assertEquals("06:00",saved.getBusinessHours().getOpens()); assertTrue(saved.getFeatures().isEmpty());
    }
    @Test void storeCreationRequiresAllScopeAndUsesSystemDept() {
        scoped(101L); assertServiceException(() -> stores.create(store(101L,"A")), STORE_NO_PERMISSION);
        headquarters(); when(depts.createDept(any())).thenReturn(102L);
        var created = stores.create(store(null,"NEW")); assertEquals("102", created.getId()); verify(depts).createDept(any());
    }
    @Test void storeScopeFiltersTotalsAndRejectsDirectAccess() {
        stores.create(store(101L,"A")); stores.create(store(102L,"B")); scoped(101L);
        var page = stores.page(new GymPageReqVO()); assertEquals(1L,page.getTotal()); assertEquals("101",page.getList().getFirst().getId());
        assertServiceException(() -> stores.get(102L), STORE_NO_PERMISSION);
        assertServiceException(() -> stores.patch(102L,new GymStoreSaveReqVO()), STORE_NO_PERMISSION);
        var filter = new GymPageReqVO(); filter.setStoreId(102L); assertServiceException(() -> stores.page(filter), STORE_NO_PERMISSION);
        scoped(); assertEquals(0L,stores.page(new GymPageReqVO()).getTotal());
    }
    @Test void storeValidationAndUniqueness() {
        stores.create(store(101L,"A")); assertServiceException(() -> stores.create(store(102L,"A")), STORE_CODE_DUPLICATE);
        var bad = store(102L,"B"); bad.getBusinessHours().setOpens("25:99"); assertThrows(ConstraintViolationException.class, () -> stores.create(bad));
        var finalBad = store(102L,"B"); finalBad.setAreaSqm(new BigDecimal("1.234")); assertThrows(ConstraintViolationException.class, () -> stores.create(finalBad));
        var invalid = new GymStoreSaveReqVO(); invalid.setStatus("unknown"); assertThrows(ConstraintViolationException.class, () -> stores.patch(101L,invalid));
        assertEquals("active",stores.get(101L).getStatus());
    }
    @Test void storeTenantIsolationIncludingLocking() {
        stores.create(store(101L,"A")); TenantContextHolder.setTenantId(2L);
        assertEquals(0L, stores.page(new GymPageReqVO()).getTotal()); assertServiceException(() -> stores.get(101L),STORE_NOT_EXISTS);
        assertServiceException(() -> stores.patch(101L,new GymStoreSaveReqVO()),STORE_NOT_EXISTS);
        stores.create(store(102L,"A")); assertEquals("102",stores.page(new GymPageReqVO()).getList().getFirst().getId());
    }
    GymFileDO file(Long storeId, String category) {
        GymFileDO f = new GymFileDO(); f.setStoreId(storeId); f.setCategory(category); f.setName("测试.png"); f.setMimeType("image/png"); f.setSize(10L); files.insert(f); return f;
    }
    GymStoreMediaReqVO media(Long fileId, boolean cover) {
        var item = new GymStoreMediaReqVO.Item(); item.setFileId(fileId); item.setIsCover(cover); item.setSortOrder(0); item.setCaption("封面");
        var req = new GymStoreMediaReqVO(); req.setItems(List.of(item)); return req;
    }
    @Test void mediaRoundTripReplaceAndClear() {
        stores.create(store(101L,"A")); var f = file(101L,"image"); stores.saveMedia(101L,media(f.getId(),true));
        var item = stores.get(101L).getMedia().getFirst(); assertEquals(f.getId().toString(),item.getFileId()); assertEquals("测试.png",item.getOriginalName());
        stores.saveMedia(101L,media(f.getId(),true)); assertEquals(1, stores.get(101L).getMedia().size());
        var empty = new GymStoreMediaReqVO(); empty.setItems(List.of()); stores.saveMedia(101L,empty); assertTrue(stores.get(101L).getMedia().isEmpty());
    }
    @Test void invalidMediaDoesNotReplaceExisting() {
        stores.create(store(101L,"A")); var f = file(101L,"image"); stores.saveMedia(101L,media(f.getId(),true));
        var other=file(102L,"image"); assertServiceException(() -> stores.saveMedia(101L,media(other.getId(),false)),STORE_MEDIA_INVALID);
        var video=file(101L,"video"); assertServiceException(() -> stores.saveMedia(101L,media(video.getId(),true)),STORE_MEDIA_INVALID);
        var req = media(f.getId(),true); req.setItems(List.of(req.getItems().getFirst(),req.getItems().getFirst()));
        assertServiceException(() -> stores.saveMedia(101L,req),STORE_MEDIA_INVALID);
        assertEquals(1,stores.get(101L).getMedia().size());
    }
    @Test void employeeRoundTripPatchAndAssignments() {
        stores.create(store(101L,"A")); stores.create(store(102L,"B")); var e = employees.create(employee("E1",101L)); Long id=Long.valueOf(e.getId());
        var patch = new GymEmployeeSaveReqVO(); patch.setJobTitle("教练"); employees.patch(id,patch);
        assertEquals("教练",employees.get(id).getJobTitle()); assertEquals(List.of("101"),employees.get(id).getStoreIds());
        patch = new GymEmployeeSaveReqVO(); patch.setStoreAssignments(employee("E1",102L).getStoreAssignments()); employees.patch(id,patch);
        assertEquals(List.of("102"),employees.get(id).getStoreIds());
        employees.patch(id,patch); assertEquals(1,employees.get(id).getStoreAssignments().size());
    }
    @Test void employeeScopeMasksOtherStoresAndPreventsSharedWrites() {
        stores.create(store(101L,"A")); stores.create(store(102L,"B"));
        var shared=employees.create(employee("E1",101L,102L)); var other=employees.create(employee("E2",102L)); scoped(101L);
        assertEquals(1L,employees.page(new GymPageReqVO()).getTotal());
        assertEquals(List.of("101"),employees.get(Long.valueOf(shared.getId())).getStoreIds());
        assertServiceException(() -> employees.get(Long.valueOf(other.getId())),STORE_NO_PERMISSION);
        var patch = new GymEmployeeSaveReqVO(); patch.setName("越权修改");
        assertServiceException(() -> employees.patch(Long.valueOf(shared.getId()),patch),STORE_NO_PERMISSION);
        assertServiceException(() -> employees.create(employee("E3",102L)),STORE_NO_PERMISSION);
    }
    @Test void employeeImmutableAndUniqueNumberAndSalaryRejection() {
        stores.create(store(101L,"A")); var e=employees.create(employee("E1",101L)); Long id=Long.valueOf(e.getId());
        assertServiceException(() -> employees.create(employee("E1",101L)),EMPLOYEE_NO_DUPLICATE);
        var patch=new GymEmployeeSaveReqVO(); patch.setEmployeeNo("E2"); assertServiceException(() -> employees.patch(id,patch),EMPLOYEE_NO_IMMUTABLE);
        var salary=new GymEmployeeSaveReqVO(); salary.setName("不能落库"); salary.setSalaryAssignment(Map.of("templateId","1"));
        assertServiceException(() -> employees.patch(id,salary),EMPLOYEE_SALARY_UNSUPPORTED); assertEquals("测试员工",employees.get(id).getName());
    }
    @Test void employeeRequiresOnePrimaryAndValidDates() {
        stores.create(store(101L,"A")); var dup=employee("E1",101L,101L); assertServiceException(() -> employees.create(dup),EMPLOYEE_ASSIGNMENT_INVALID);
        var noPrimary=employee("E2",101L); noPrimary.getStoreAssignments().getFirst().setIsPrimary(false); assertServiceException(() -> employees.create(noPrimary),EMPLOYEE_ASSIGNMENT_INVALID);
        var early=employee("E3",101L); early.getStoreAssignments().getFirst().setJoinedOn(LocalDate.of(2025,1,1)); assertServiceException(() -> employees.create(early),EMPLOYEE_ASSIGNMENT_INVALID);
        var empty=employee("E4"); assertThrows(ConstraintViolationException.class, () -> employees.create(empty));
    }
    @Test void employeeUserLinkRequiresPermissionAndPrimaryDept() {
        stores.create(store(101L,"A")); var req=employee("E1",101L); req.setUserId(900L);
        assertServiceException(() -> employees.create(req),EMPLOYEE_USER_NO_PERMISSION);
        when(permissions.hasAnyPermissions(any(),eq("system:user:update"))).thenReturn(true);
        var user = new AdminUserDO(); user.setId(900L); user.setDeptId(102L); user.setStatus(0); user.setUsername("linked-user"); when(users.getUser(900L)).thenReturn(user);
        assertServiceException(() -> employees.create(req),EMPLOYEE_USER_INVALID);
        user.setDeptId(101L); var e=employees.create(req); assertEquals("900",e.getUserId()); assertEquals("linked-user",e.getUsername());
        var second=employee("E2",101L); second.setUserId(900L); assertServiceException(() -> employees.create(second),EMPLOYEE_NO_DUPLICATE);
    }
    @Test void employeeTenantIsolationAndScopedPagination() {
        stores.create(store(101L,"A")); var e=employees.create(employee("E1",101L)); Long id=Long.valueOf(e.getId());
        TenantContextHolder.setTenantId(2L); assertEquals(0L,employees.page(new GymPageReqVO()).getTotal());
        assertServiceException(() -> employees.get(id),EMPLOYEE_NOT_EXISTS); assertServiceException(() -> employees.patch(id,new GymEmployeeSaveReqVO()),EMPLOYEE_NOT_EXISTS);
        TenantContextHolder.setTenantId(1L); scoped(101L); var req=new GymPageReqVO(); req.setPageSize(1); req.setKeyword("E1");
        assertEquals(1L,employees.page(req).getTotal()); req.setKeyword("missing"); assertEquals(0L,employees.page(req).getTotal());
    }

    @Test void readOnlyRoleCannotWriteThroughControllers() {
        stores.create(store(101L,"A"));
        when(permissions.hasAnyPermissions(eq(1L),eq("gym:stores:read"))).thenReturn(true);
        when(permissions.hasAnyPermissions(eq(1L),eq("gym:employees:read"))).thenReturn(true);
        assertEquals(0,storeController.get(101L).getCode());
        assertEquals(0,employeeController.page(new GymPageReqVO()).getCode());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> storeController.create(store(102L,"B")));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> storeController.patch(101L,new GymStoreSaveReqVO()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> employeeController.create(employee("E1",101L)));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> employeeController.patch(1L,new GymEmployeeSaveReqVO()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> storeController.media(101L,new GymStoreMediaReqVO()));
    }
    @Test void noPermissionCannotReadThroughControllers() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> storeController.page(new GymPageReqVO()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> storeController.get(101L));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> employeeController.page(new GymPageReqVO()));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> employeeController.get(1L));
    }
    @Test void employeeRelationReplacementRollsBackOnDatabaseFailure() {
        stores.create(store(101L,"A")); stores.create(store(102L,"B"));
        var e = employees.create(employee("E1",101L)); Long id = Long.valueOf(e.getId());
        // 强制在主档案写入之后、替换关系过程中触发数据库约束失败。
        // 不 mock 数据库：使用 H2 CHECK 约束模拟写入异常。
        org.springframework.jdbc.core.JdbcTemplate jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.execute("ALTER TABLE gym_employee_store ADD CONSTRAINT reject_store_b CHECK (store_id <> 102)");
        try {
            var patch = new GymEmployeeSaveReqVO(); patch.setName("应回滚"); patch.setStoreAssignments(employee("E1",102L).getStoreAssignments());
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> employees.patch(id,patch));
            assertEquals("测试员工",employees.get(id).getName()); assertEquals(List.of("101"),employees.get(id).getStoreIds());
        } finally { jdbc.execute("ALTER TABLE gym_employee_store DROP CONSTRAINT reject_store_b"); }
    }
    @Resource javax.sql.DataSource dataSource;
    @Test void fileResponseMatchesCurrentFrontendContract() {
        var file = file(101L,"image"); file.setUploaderId(1L);
        var vo=cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(file,cn.iocoder.yudao.module.gym.controller.admin.file.vo.GymFileRespVO.class);
        var json=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(vo));
        assertTrue(json.get("id").isString()); assertEquals("101",json.get("storeId").asString());
        assertEquals("测试.png",json.get("originalName").asString()); assertEquals(10L,json.get("byteSize").asLong()); assertNull(json.get("url"));
    }
}
