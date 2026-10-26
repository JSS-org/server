package cn.iocoder.yudao.module.system.integration;

import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.mybatis.core.injector.YudaoSqlInjector;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.NoticePageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.NoticeSaveReqVO;
import cn.iocoder.yudao.module.system.dal.mysql.notice.NoticeMapper;
import cn.iocoder.yudao.module.system.service.notice.NoticeService;
import cn.iocoder.yudao.module.system.service.notice.NoticeServiceImpl;

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
        matches = "jdbc:mysql://127[.]0[.]0[.]1:13316/jss_notice_service_verify_[0-9a-f]{12}.*")
class JssNoticeMySqlServiceIT {
    private static AnnotationConfigApplicationContext context;
    private static NoticeService service;

    @BeforeAll
    static void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(NoticeService.class);
        try (var connection = context.getBean(DataSource.class).getConnection()) {
            assertTrue(connection.getCatalog().matches("jss_notice_service_verify_[0-9a-f]{12}"));
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
    @Import(NoticeServiceImpl.class)
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
                            "jss-notice-services", new SpringManagedTransactionFactory(), source));
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
            configuration.addMapper(NoticeMapper.class);
            return new MybatisSqlSessionFactoryBuilder().build(configuration);
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }

        @Bean
        NoticeMapper noticeMapper(SqlSessionTemplate sql) {
            return sql.getMapper(NoticeMapper.class);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }

    private String unique() {
        return "JSSNotice" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private NoticeSaveReqVO form(String title) {
        return new NoticeSaveReqVO()
                .setTitle(title)
                .setContent("<p>Original <strong>content</strong></p>")
                .setType(1)
                .setStatus(0)
                .setRemark("original remark");
    }

    private long create(String title) {
        return service.createNotice(form(title));
    }

    private void error(org.junit.jupiter.api.function.Executable action) {
        assertEquals(
                NOTICE_NOT_FOUND.getCode(), assertThrows(ServiceException.class, action).getCode());
    }

    @Test
    void fullCrudRoundTripsRemarkRichContentTypeAndStatus() {
        String title = unique();
        long id = create(title);
        assertEquals("original remark", service.getNotice(id).getRemark());
        service.updateNotice(form(title).setId(id).setRemark(null));
        assertEquals("original remark", service.getNotice(id).getRemark());
        service.updateNotice(
                form(title + "Edited")
                        .setId(id)
                        .setType(2)
                        .setStatus(1)
                        .setContent("<h2>Updated</h2><p>0009007199254740993</p>")
                        .setRemark(""));
        var saved = service.getNotice(id);
        assertEquals("", saved.getRemark());
        assertEquals(2, saved.getType());
        assertEquals(1, saved.getStatus());
        assertEquals("<h2>Updated</h2><p>0009007199254740993</p>", saved.getContent());
        service.deleteNotice(id);
        assertNull(service.getNotice(id));
        error(() -> service.updateNotice(form(unique()).setId(id)));
        error(() -> service.deleteNotice(id));
    }

    @Test
    void titleAndEnabledFiltersCombineWithDescendingPagination() {
        String title = unique();
        long a = create(title + "A");
        long b = create(title + "B");
        long c = create(title + "C");
        service.updateNotice(form(title + "B").setId(b).setStatus(1));
        var query = new NoticePageReqVO().setTitle(title).setStatus(0);
        query.setPageNo(1);
        query.setPageSize(1);
        assertEquals(2, service.getNoticePage(query).getTotal());
        assertEquals(c, service.getNoticePage(query).getList().getFirst().getId());
        query.setPageNo(2);
        assertEquals(a, service.getNoticePage(query).getList().getFirst().getId());
        query.setPageNo(1);
        query.setStatus(1);
        assertEquals(b, service.getNoticePage(query).getList().getFirst().getId());
    }

    @Test
    void foreignTenantReadUpdateSingleAndMixedBatchDeleteAreRejectedWithoutPartialDeletion() {
        String title = unique();
        long own = create(title);
        TenantContextHolder.setTenantId(2L);
        long foreign = create(title);
        assertNull(service.getNotice(own));
        TenantContextHolder.setTenantId(1L);
        assertNull(service.getNotice(foreign));
        error(() -> service.updateNotice(form(title).setId(foreign)));
        error(() -> service.deleteNotice(foreign));
        assertAll(
                () -> error(() -> service.deleteNoticeList(List.of(own, foreign))),
                () ->
                        assertNotNull(
                                service.getNotice(own),
                                "Mixed foreign batch deleted owned notice"));
        var query = new NoticePageReqVO().setTitle(title);
        query.setPageNo(1);
        query.setPageSize(20);
        assertEquals(
                List.of(own),
                service.getNoticePage(query).getList().stream()
                        .map(cn.iocoder.yudao.module.system.dal.dataobject.notice.NoticeDO::getId)
                        .toList());
    }

    @Test
    void missingBatchTargetRejectsExistingRowsAsAWhole() {
        long id = create(unique());
        assertAll(
                () -> error(() -> service.deleteNoticeList(List.of(id, Long.MAX_VALUE))),
                () ->
                        assertNotNull(
                                service.getNotice(id), "Mixed missing batch deleted owned notice"));
    }

    @Test
    void duplicateBatchIdsJoinTheCallersRollback() {
        long a = create(unique());
        long b = create(unique());
        var transaction =
                new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
        assertThrows(
                IllegalStateException.class,
                () ->
                        transaction.executeWithoutResult(
                                status -> {
                                    service.deleteNoticeList(List.of(a, b, a));
                                    assertNull(service.getNotice(a));
                                    throw new IllegalStateException("owned rollback");
                                }));
        assertNotNull(service.getNotice(a));
        assertNotNull(service.getNotice(b));
        service.deleteNoticeList(List.of(a, b, a));
        assertNull(service.getNotice(a));
        assertNull(service.getNotice(b));
    }
}
