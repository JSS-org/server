package cn.iocoder.yudao.module.system.integration;

import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.mybatis.core.injector.YudaoSqlInjector;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.controller.admin.permission.vo.menu.MenuListReqVO;
import cn.iocoder.yudao.module.system.controller.admin.permission.vo.menu.MenuSaveVO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleMenuDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.MenuMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMenuMapper;
import cn.iocoder.yudao.module.system.service.permission.MenuService;
import cn.iocoder.yudao.module.system.service.permission.MenuServiceImpl;
import cn.iocoder.yudao.module.system.service.permission.PermissionServiceImpl;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

import javax.sql.DataSource;

/** Production menu/permission targets, MySQL mappers and real transaction advice; no HTTP, tenant-package selection or Redis cache advice. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_MYSQL_URL",
        matches = "jdbc:mysql://127[.]0[.]0[.]1:13316/jss_menu_service_verify_[0-9a-f]{12}.*")
class JssMenuMySqlServiceIT {
    private static AnnotationConfigApplicationContext context;
    private static MenuService service;

    @BeforeAll
    static void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(MenuService.class);
        try (var connection = context.getBean(DataSource.class).getConnection()) {
            assertTrue(connection.getCatalog().matches("jss_menu_service_verify_[0-9a-f]{12}"));
        }
    }

    @BeforeEach
    void tenant() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @AfterAll
    static void close() {
        if (context != null) context.close();
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean
        DataSource dataSource() {
            return new UnpooledDataSource(
                    "com.mysql.cj.jdbc.Driver",
                    System.getenv("JSS_TEST_MYSQL_URL"),
                    System.getenv("JSS_TEST_MYSQL_USER"),
                    System.getenv("JSS_TEST_MYSQL_PASSWORD"));
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource source) {
            var configuration = new MybatisConfiguration();
            configuration.setEnvironment(
                    new Environment(
                            "jss-menu-services", new SpringManagedTransactionFactory(), source));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
            GlobalConfigUtils.setGlobalConfig(
                    configuration,
                    new GlobalConfig()
                            .setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO))
                            .setMetaObjectHandler(new DefaultDBFieldHandler())
                            .setSqlInjector(new YudaoSqlInjector()));
            var plugins = new MybatisPlusInterceptor();
            plugins.addInnerInterceptor(
                    new TenantLineInnerInterceptor(
                            new TenantDatabaseInterceptor(new TenantProperties())));
            plugins.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
            configuration.addInterceptor(plugins);
            configuration.addMapper(MenuMapper.class);
            configuration.addMapper(RoleMenuMapper.class);
            return new MybatisSqlSessionFactoryBuilder().build(configuration);
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }

        @Bean
        MenuMapper menuMapper(SqlSessionTemplate sql) {
            return sql.getMapper(MenuMapper.class);
        }

        @Bean
        RoleMenuMapper roleMenuMapper(SqlSessionTemplate sql) {
            return sql.getMapper(RoleMenuMapper.class);
        }

        @Bean
        MenuService menuService(
                MenuMapper mapper, RoleMenuMapper roleMenus, DataSourceTransactionManager tx) {
            // Wire production targets explicitly: only processMenuDeleted is used on
            // PermissionService.
            var permissions = new PermissionServiceImpl();
            ReflectionTestUtils.setField(permissions, "roleMenuMapper", roleMenus);
            var target = new MenuServiceImpl();
            ReflectionTestUtils.setField(target, "menuMapper", mapper);
            ReflectionTestUtils.setField(target, "permissionService", permissions);
            var proxy = new ProxyFactory(target);
            proxy.addAdvice(
                    new TransactionInterceptor(tx, new AnnotationTransactionAttributeSource()));
            return (MenuService) proxy.getProxy();
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }

    private String unique() {
        return "JSSMenu" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private MenuSaveVO form(String name, long parent, int type) {
        return new MenuSaveVO()
                .setName(name)
                .setParentId(parent)
                .setType(type)
                .setSort(0)
                .setStatus(0)
                .setPermission("jss:test:" + name)
                .setPath("/" + name)
                .setIcon("icon")
                .setComponent("component/" + name)
                .setComponentName(name)
                .setVisible(true)
                .setKeepAlive(true)
                .setAlwaysShow(true);
    }

    private long create(long parent, int type) {
        return service.createMenu(form(unique(), parent, type));
    }

    private void error(
            cn.iocoder.yudao.framework.common.exception.ErrorCode code,
            org.junit.jupiter.api.function.Executable action) {
        assertEquals(code.getCode(), assertThrows(ServiceException.class, action).getCode());
    }

    private RoleMenuMapper grants() {
        return context.getBean(RoleMenuMapper.class);
    }

    private void grant(long menu, long tenant) {
        TenantUtils.execute(
                tenant,
                () -> {
                    var row = new RoleMenuDO().setMenuId(menu).setRoleId(tenant * 100);
                    row.setTenantId(tenant);
                    grants().insert(row);
                });
    }

    private long grantCount(long menu, long tenant) {
        return TenantUtils.execute(tenant, () -> grants().selectCount(RoleMenuDO::getMenuId, menu));
    }

    @Test
    void fullCrudIsSharedAndKeepsFalseAndExplicitClears() {
        String name = unique();
        long id = service.createMenu(form(name, 0, 2));
        TenantContextHolder.setTenantId(2L);
        assertEquals(name, service.getMenu(id).getName());
        assertTrue(
                service.getMenuList(new MenuListReqVO().setName(name).setStatus(0)).stream()
                        .anyMatch(menu -> menu.getId() == id));
        service.updateMenu(
                form(name, 0, 2)
                        .setId(id)
                        .setPermission("")
                        .setIcon("")
                        .setComponent("")
                        .setComponentName("")
                        .setVisible(false)
                        .setKeepAlive(false)
                        .setAlwaysShow(false));
        MenuDO saved = service.getMenu(id);
        assertEquals("", saved.getPermission());
        assertEquals("", saved.getComponentName());
        assertFalse(saved.getVisible());
        assertFalse(saved.getKeepAlive());
        assertFalse(saved.getAlwaysShow());
        service.updateMenu(form(name, 0, 3).setId(id));
        saved = service.getMenu(id);
        assertEquals("", saved.getPath());
        assertEquals("", saved.getComponent());
        assertEquals("", saved.getIcon());
        assertEquals("", saved.getComponentName());
        service.deleteMenu(id);
        assertNull(service.getMenu(id));
        error(MENU_NOT_EXISTS, () -> service.deleteMenu(id));
    }

    @Test
    void uniquenessAndParentTypeAndChildrenAreEnforced() {
        long root = create(0, 1);
        String name = unique();
        long child = service.createMenu(form(name, root, 2));
        error(
                MENU_NAME_DUPLICATE,
                () -> service.createMenu(form(name, root, 2).setComponentName(unique())));
        error(
                MENU_COMPONENT_NAME_DUPLICATE,
                () -> service.createMenu(form(unique(), 0, 2).setComponentName(name)));
        long button = create(child, 3);
        error(MENU_PARENT_NOT_DIR_OR_MENU, () -> create(button, 2));
        error(MENU_PARENT_NOT_EXISTS, () -> create(Long.MAX_VALUE, 2));
        error(MENU_EXISTS_CHILDREN, () -> service.updateMenu(form(unique(), root, 3).setId(child)));
        error(MENU_EXISTS_CHILDREN, () -> service.deleteMenu(root));
        error(MENU_EXISTS_CHILDREN, () -> service.deleteMenuList(List.of(root, child, button)));
        assertNotNull(service.getMenu(root));
        assertNotNull(service.getMenu(child));
        assertNotNull(service.getMenu(button));
    }

    @Test
    void movingUnderADescendantIsRejectedBeforeWriting() {
        long root = create(0, 1);
        long child = create(root, 1);
        long leaf = create(child, 2);
        error(MENU_PARENT_ERROR, () -> service.updateMenu(form(unique(), leaf, 1).setId(root)));
        assertEquals(0L, service.getMenu(root).getParentId());
        assertEquals(root, service.getMenu(child).getParentId());
    }

    @Test
    void dirtyCyclesAndOrphansFailClosedAndDeepTreesDoNotOverflow() {
        var nodes = new ArrayList<MenuDO>();
        nodes.add(new MenuDO().setId(1L).setParentId(2L).setStatus(0));
        nodes.add(new MenuDO().setId(2L).setParentId(1L).setStatus(0));
        nodes.add(new MenuDO().setId(3L).setParentId(99999L).setStatus(0));
        assertTrue(service.filterDisableMenus(nodes).isEmpty());
        nodes.clear();
        for (long id = 1; id <= 10000; id++)
            nodes.add(new MenuDO().setId(id).setParentId(id - 1).setStatus(0));
        assertEquals(10000, service.filterDisableMenus(nodes).size());
        nodes.get(0).setStatus(1);
        assertTrue(service.filterDisableMenus(nodes).isEmpty());
    }

    @Test
    void permissionLookupExcludesDisabledNodesAndAncestors() {
        long root = create(0, 1);
        String name = unique();
        String permission = "jss:test:" + name;
        long child = service.createMenu(form(name, root, 3));
        assertEquals(List.of(child), service.getMenuIdListByPermissionFromCache(permission));
        MenuDO original = service.getMenu(root);
        service.updateMenu(form(original.getName(), 0, 1).setId(root).setStatus(1));
        assertTrue(service.getMenuIdListByPermissionFromCache(permission).isEmpty());
        service.updateMenu(form(original.getName(), 0, 1).setId(root));
        assertEquals(List.of(child), service.getMenuIdListByPermissionFromCache(permission));
        service.updateMenu(form(name, root, 3).setId(child).setStatus(1));
        assertTrue(service.getMenuIdListByPermissionFromCache(permission).isEmpty());
    }

    @Test
    void mixedMissingBatchIsRejectedAndDuplicatesAreIdempotent() {
        long id = create(0, 2);
        grant(id, 1);
        error(MENU_NOT_EXISTS, () -> service.deleteMenuList(List.of(id, Long.MAX_VALUE)));
        assertNotNull(service.getMenu(id));
        assertEquals(1, grantCount(id, 1));
        service.deleteMenuList(List.of(id, id));
        assertNull(service.getMenu(id));
        assertEquals(0, grantCount(id, 1));
    }

    @Test
    void deletingASharedMenuCleansRoleGrantsInEveryTenant() {
        long id = create(0, 2);
        grant(id, 1);
        grant(id, 2);
        service.deleteMenu(id);
        assertEquals(0, grantCount(id, 1));
        assertEquals(0, grantCount(id, 2));
        assertFalse(TenantContextHolder.isIgnore());
        assertEquals(1L, TenantContextHolder.getTenantId());
    }

    @Test
    void laterSqlFailureAndOuterRollbackKeepMenusAndGrantsTogether() {
        long first = create(0, 2);
        long second = create(0, 2);
        grant(first, 1);
        grant(second, 1);
        var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.execute(
                "CREATE TRIGGER fail_menu_cleanup BEFORE UPDATE ON system_role_menu FOR EACH ROW BEGIN IF OLD.menu_id="
                        + second
                        + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated cleanup failure'; END IF; END");
        try {
            assertThrows(
                    RuntimeException.class, () -> service.deleteMenuList(List.of(first, second)));
            assertNotNull(service.getMenu(first));
            assertNotNull(service.getMenu(second));
            assertEquals(1, grantCount(first, 1));
            assertEquals(1, grantCount(second, 1));
        } finally {
            jdbc.execute("DROP TRIGGER fail_menu_cleanup");
        }
        new TransactionTemplate(context.getBean(DataSourceTransactionManager.class))
                .executeWithoutResult(
                        tx -> {
                            service.deleteMenuList(List.of(first, second));
                            assertNull(service.getMenu(first));
                            tx.setRollbackOnly();
                        });
        assertNotNull(service.getMenu(first));
        assertNotNull(service.getMenu(second));
        assertEquals(1, grantCount(first, 1));
        assertEquals(1, grantCount(second, 1));
    }
}
