package cn.iocoder.yudao.module.system.integration;

import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.mybatis.core.injector.YudaoSqlInjector;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.post.PostPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.post.PostSaveReqVO;
import cn.iocoder.yudao.module.system.dal.mysql.dept.PostMapper;
import cn.iocoder.yudao.module.system.service.dept.PostService;
import cn.iocoder.yudao.module.system.service.dept.PostServiceImpl;

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
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

import javax.sql.DataSource;

/** Actual MySQL, service, mapper, tenant/pagination SQL and transactions; no HTTP or Redis. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_MYSQL_URL",
        matches = "jdbc:mysql://127[.]0[.]0[.]1:13316/jss_post_service_verify_[0-9a-f]{12}.*")
class JssPostMySqlServiceIT {
    private static AnnotationConfigApplicationContext context;
    private static PostService service;

    @BeforeAll
    static void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(PostService.class);
        try (var connection = context.getBean(DataSource.class).getConnection()) {
            assertTrue(connection.getCatalog().matches("jss_post_service_verify_[0-9a-f]{12}"));
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
    @Import(PostServiceImpl.class)
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
                            "jss-post-services", new SpringManagedTransactionFactory(), source));
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
            configuration.addMapper(PostMapper.class);
            return new MybatisSqlSessionFactoryBuilder().build(configuration);
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }

        @Bean
        PostMapper postMapper(SqlSessionTemplate sql) {
            return sql.getMapper(PostMapper.class);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }

    private String unique() {
        return "JSSPost" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private PostSaveReqVO form(String name, String code) {
        return new PostSaveReqVO()
                .setName(name)
                .setCode(code)
                .setSort(3)
                .setStatus(0)
                .setRemark("initial");
    }

    private long create(String name) {
        return service.createPost(form(name, name));
    }

    private void error(int code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(ServiceException.class, action).getCode());
    }

    @Test
    void actualCrudPaginationFiltersAndDisabledPostValidation() {
        String prefix = unique();
        long id = create(prefix + "A");
        long other = create(prefix + "B");
        service.updatePost(
                form(prefix + "Edited", prefix + "Code")
                        .setId(id)
                        .setSort(8)
                        .setStatus(1)
                        .setRemark(""));
        var saved = service.getPost(id);
        assertEquals(prefix + "Edited", saved.getName());
        assertEquals("", saved.getRemark());
        assertEquals(8, saved.getSort());
        error(POST_NOT_ENABLE.getCode(), () -> service.validatePostList(List.of(id)));
        var query = new PostPageReqVO().setName(prefix).setCode("Code").setStatus(1);
        query.setPageNo(1);
        query.setPageSize(1);
        var page = service.getPostPage(query);
        assertEquals(1, page.getTotal());
        assertEquals(id, page.getList().getFirst().getId());
        assertFalse(
                service.getPostList(null, List.of(0)).stream()
                        .anyMatch(post -> post.getId().equals(id)));
        service.deletePost(id);
        assertNull(service.getPost(id));
        assertNotNull(service.getPost(other));
    }

    @Test
    void uniquenessAndReadUpdateDeleteRespectTenantBoundaries() {
        String name = unique();
        long own = create(name);
        error(POST_NAME_DUPLICATE.getCode(), () -> service.createPost(form(name, unique())));
        error(POST_CODE_DUPLICATE.getCode(), () -> service.createPost(form(unique(), name)));
        TenantContextHolder.setTenantId(2L);
        long foreign = create(name);
        assertNotNull(service.getPost(foreign));
        assertNull(service.getPost(own));
        TenantContextHolder.setTenantId(1L);
        assertNull(service.getPost(foreign));
        error(
                POST_NOT_FOUND.getCode(),
                () -> service.updatePost(form(unique(), unique()).setId(foreign)));
        error(POST_NOT_FOUND.getCode(), () -> service.deletePost(foreign));
        assertEquals(
                List.of(own),
                service.getPostList(List.of(own, foreign)).stream()
                        .map(post -> post.getId())
                        .toList());
    }

    @Test
    void mixedForeignAndMissingBatchRejectsBeforeDeletingAnyOwnedPost() {
        long first = create(unique());
        long second = create(unique());
        TenantContextHolder.setTenantId(2L);
        long foreign = create(unique());
        TenantContextHolder.setTenantId(1L);
        assertAll(
                () ->
                        error(
                                POST_NOT_FOUND.getCode(),
                                () -> service.deletePostList(List.of(first, foreign))),
                () ->
                        assertNotNull(
                                service.getPost(first),
                                "Mixed-tenant batch deleted an owned post"));
        error(
                POST_NOT_FOUND.getCode(),
                () -> service.deletePostList(List.of(second, Long.MAX_VALUE)));
        assertNotNull(service.getPost(second));
        service.deletePostList(List.of(first, second, first));
        assertNull(service.getPost(first));
        assertNull(service.getPost(second));
        TenantContextHolder.setTenantId(2L);
        assertNotNull(service.getPost(foreign));
    }

    @Test
    void successfulBatchStillRollsBackWithItsOuterBusinessTransaction() {
        long first = create(unique());
        long second = create(unique());
        var transaction =
                new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
        transaction.executeWithoutResult(
                status -> {
                    service.deletePostList(List.of(first, second));
                    assertNull(service.getPost(first));
                    assertNull(service.getPost(second));
                    status.setRollbackOnly();
                });
        assertNotNull(service.getPost(first));
        assertNotNull(service.getPost(second));
    }
}
