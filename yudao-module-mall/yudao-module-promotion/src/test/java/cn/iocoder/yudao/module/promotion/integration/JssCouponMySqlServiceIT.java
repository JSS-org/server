package cn.iocoder.yudao.module.promotion.integration;

import static cn.iocoder.yudao.module.promotion.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.mybatis.core.injector.YudaoSqlInjector;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.product.api.category.ProductCategoryApi;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.CouponDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.CouponTemplateDO;
import cn.iocoder.yudao.module.promotion.dal.mysql.coupon.CouponMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.coupon.CouponTemplateMapper;
import cn.iocoder.yudao.module.promotion.enums.coupon.CouponTakeTypeEnum;
import cn.iocoder.yudao.module.promotion.service.coupon.CouponService;
import cn.iocoder.yudao.module.promotion.service.coupon.CouponServiceImpl;
import cn.iocoder.yudao.module.promotion.service.coupon.CouponTemplateServiceImpl;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import javax.sql.DataSource;

/** Real services, mapper SQL, tenant plugin and Spring transactions; no Redis or HTTP authorization. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_MYSQL_URL",
        matches = "jdbc:mysql://127[.]0[.]0[.]1:13316/jss_coupon_service_verify_[0-9a-f]{12}.*")
class JssCouponMySqlServiceIT {
    private static AnnotationConfigApplicationContext context;
    private static CouponService service;
    private static CouponMapper coupons;
    private static CouponTemplateMapper templates;

    @BeforeAll
    static void configureActualServices() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(CouponService.class);
        coupons = context.getBean(CouponMapper.class);
        templates = context.getBean(CouponTemplateMapper.class);
        try (var connection = context.getBean(DataSource.class).getConnection()) {
            assertTrue(connection.getCatalog().matches("jss_coupon_service_verify_[0-9a-f]{12}"));
        }
    }

    @BeforeEach
    void tenant() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @AfterAll
    static void close() {
        if (context != null) context.close();
    }

    @Configuration
    @EnableTransactionManagement
    @Import({CouponServiceImpl.class, CouponTemplateServiceImpl.class, SpringUtil.class})
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
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) {
            var configuration = new MybatisConfiguration();
            configuration.setEnvironment(
                    new Environment(
                            "jss-coupon-services",
                            new SpringManagedTransactionFactory(),
                            dataSource));
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
            configuration.addInterceptor(plugins);
            configuration.addMapper(CouponMapper.class);
            configuration.addMapper(CouponTemplateMapper.class);
            return new MybatisSqlSessionFactoryBuilder().build(configuration);
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }

        @Bean
        CouponMapper couponMapper(SqlSessionTemplate sql) {
            return sql.getMapper(CouponMapper.class);
        }

        @Bean
        CouponTemplateMapper couponTemplateMapper(SqlSessionTemplate sql) {
            return sql.getMapper(CouponTemplateMapper.class);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        // Unused external APIs are wiring-only doubles; coupon service and both mappers are
        // production implementations.
        @Bean
        MemberUserApi memberUserApi() {
            return org.mockito.Mockito.mock(MemberUserApi.class);
        }

        @Bean
        ProductCategoryApi productCategoryApi() {
            return org.mockito.Mockito.mock(ProductCategoryApi.class);
        }

        @Bean
        ProductSpuApi productSpuApi() {
            return org.mockito.Mockito.mock(ProductSpuApi.class);
        }
    }

    private CouponTemplateDO template(int total, int perUser) {
        var template =
                new CouponTemplateDO()
                        .setName("JSSCoupon" + UUID.randomUUID())
                        .setStatus(0)
                        .setTotalCount(total)
                        .setTakeLimitCount(perUser)
                        .setTakeType(1)
                        .setUsePrice(0)
                        .setProductScope(1)
                        .setValidityType(1)
                        .setValidStartTime(LocalDateTime.now().minusHours(1))
                        .setValidEndTime(LocalDateTime.now().plusDays(1))
                        .setDiscountType(1)
                        .setDiscountPrice(200);
        templates.insert(template);
        return template;
    }

    @Test
    void takeAvailabilityMatchesTemplateStateQuotaTypeAndMemberLimit() {
        var available = template(5, 1);
        var empty = template(1, 1);
        claim(empty.getId(), 82L);
        var disabled = template(5, 1);
        templates.updateById(new CouponTemplateDO().setId(disabled.getId()).setStatus(1));
        var expired = template(5, 1);
        templates.updateById(
                new CouponTemplateDO()
                        .setId(expired.getId())
                        .setValidEndTime(LocalDateTime.now().minusMinutes(1)));
        var adminOnly = template(5, 1);
        templates.updateById(new CouponTemplateDO().setId(adminOnly.getId()).setTakeType(2));
        var snapshots =
                templates.selectByIds(
                        List.of(
                                available.getId(),
                                empty.getId(),
                                disabled.getId(),
                                expired.getId(),
                                adminOnly.getId()));
        var guest = service.getUserCanCanTakeMap(null, snapshots);
        assertEquals(true, guest.get(available.getId()));
        for (var template : List.of(empty, disabled, expired, adminOnly)) {
            assertEquals(false, guest.get(template.getId()), template.getName());
        }
        claim(available.getId(), 80L);
        assertEquals(false, service.getUserCanCanTakeMap(80L, snapshots).get(available.getId()));
        assertEquals(true, service.getUserCanCanTakeMap(81L, snapshots).get(available.getId()));
        assertEquals(false, service.getUserCanCanTakeMap(81L, snapshots).get(disabled.getId()));
    }

    private Map<Long, List<Long>> claim(long template, Long... users) {
        return service.takeCoupon(
                template, new HashSet<>(Arrays.asList(users)), CouponTakeTypeEnum.USER);
    }

    private long issued(long template) {
        return coupons.selectCount(
                new LambdaQueryWrapper<CouponDO>().eq(CouponDO::getTemplateId, template));
    }

    private void businessError(int code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(ServiceException.class, action).getCode());
    }

    @Test
    void batchClaimCannotExceedRemainingQuotaAndRejectionLeavesNoCoupons() {
        var template = template(3, 1);
        claim(template.getId(), 10L, 11L);
        businessError(
                COUPON_TEMPLATE_NOT_ENOUGH.getCode(), () -> claim(template.getId(), 12L, 13L));
        assertEquals(2, issued(template.getId()));
        assertEquals(2, templates.selectById(template.getId()).getTakeCount());
        claim(template.getId(), 12L);
        assertEquals(3, issued(template.getId()));
        businessError(COUPON_TEMPLATE_NOT_ENOUGH.getCode(), () -> claim(template.getId(), 13L));
    }

    @Test
    void concurrentClaimsByOneMemberRespectPerUserLimit() throws Exception {
        var template = template(100, 1);
        var ready = new CountDownLatch(6);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(6);
        List<Future<Integer>> responses = new ArrayList<>();
        try {
            for (int i = 0; i < 6; i++)
                responses.add(
                        pool.submit(
                                () -> {
                                    TenantContextHolder.setTenantId(1L);
                                    try {
                                        var transaction =
                                                new TransactionTemplate(
                                                        context.getBean(
                                                                DataSourceTransactionManager
                                                                        .class));
                                        transaction.setIsolationLevel(
                                                TransactionDefinition.ISOLATION_REPEATABLE_READ);
                                        return transaction.execute(
                                                status -> {
                                                    // Start the outer RR snapshot before waiting
                                                    // for the template lock.
                                                    assertNotNull(
                                                            templates.selectById(template.getId()));
                                                    ready.countDown();
                                                    try {
                                                        assertTrue(
                                                                start.await(10, TimeUnit.SECONDS));
                                                    } catch (InterruptedException error) {
                                                        Thread.currentThread().interrupt();
                                                        throw new IllegalStateException(error);
                                                    }
                                                    claim(template.getId(), 20L);
                                                    return 0;
                                                });
                                    } catch (ServiceException error) {
                                        return error.getCode();
                                    } finally {
                                        TenantContextHolder.clear();
                                    }
                                }));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Integer> codes = new ArrayList<>();
            for (var response : responses) codes.add(response.get(20, TimeUnit.SECONDS));
            assertEquals(1, codes.stream().filter(code -> code == 0).count());
            assertTrue(
                    codes.stream()
                            .allMatch(
                                    code ->
                                            code == 0
                                                    || code
                                                            == COUPON_TEMPLATE_USER_ALREADY_TAKE
                                                                    .getCode()));
            assertEquals(1, issued(template.getId()));
            assertEquals(1, templates.selectById(template.getId()).getTakeCount());
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void disabledAndExpiredTemplatesCannotIssueCoupons() {
        var disabled = template(5, 1);
        templates.updateById(new CouponTemplateDO().setId(disabled.getId()).setStatus(1));
        businessError(1_013_004_006, () -> claim(disabled.getId(), 30L));
        assertEquals(0, issued(disabled.getId()));
        var expired = template(5, 1);
        templates.updateById(
                new CouponTemplateDO()
                        .setId(expired.getId())
                        .setValidEndTime(LocalDateTime.now().minusMinutes(1)));
        businessError(COUPON_TEMPLATE_EXPIRED.getCode(), () -> claim(expired.getId(), 30L));
        assertEquals(0, issued(expired.getId()));
    }

    @Test
    void missingAndForeignTenantTemplatesReturnControlledRejections() {
        businessError(COUPON_TEMPLATE_NOT_EXISTS.getCode(), () -> claim(-1L, 40L));
        var template = template(5, 1);
        TenantContextHolder.setTenantId(2L);
        businessError(COUPON_TEMPLATE_NOT_EXISTS.getCode(), () -> claim(template.getId(), 40L));
        TenantContextHolder.setTenantId(1L);
        assertEquals(0, issued(template.getId()));
    }

    @Test
    void couponOwnershipUseReturnAndExpiryPreserveLifecycle() {
        var template = template(5, 1);
        long coupon = claim(template.getId(), 50L).get(50L).getFirst();
        businessError(COUPON_NOT_EXISTS.getCode(), () -> service.useCoupon(coupon, 51L, 100L));
        TenantContextHolder.setTenantId(2L);
        businessError(COUPON_NOT_EXISTS.getCode(), () -> service.useCoupon(coupon, 50L, 100L));
        TenantContextHolder.setTenantId(1L);
        service.useCoupon(coupon, 50L, 100L);
        assertEquals(2, coupons.selectById(coupon).getStatus());
        businessError(
                COUPON_STATUS_NOT_UNUSED.getCode(), () -> service.useCoupon(coupon, 50L, 101L));
        service.returnUsedCoupon(coupon);
        assertEquals(1, coupons.selectById(coupon).getStatus());
        businessError(COUPON_STATUS_NOT_USED.getCode(), () -> service.returnUsedCoupon(coupon));
        service.useCoupon(coupon, 50L, 101L);
        assertEquals(101L, coupons.selectById(coupon).getUseOrderId());
        coupons.updateById(
                new CouponDO().setId(coupon).setValidEndTime(LocalDateTime.now().minusMinutes(1)));
        service.returnUsedCoupon(coupon);
        assertEquals(3, coupons.selectById(coupon).getStatus());
        businessError(
                COUPON_STATUS_NOT_UNUSED.getCode(), () -> service.useCoupon(coupon, 50L, 102L));
    }

    @Test
    void databaseInsertFailureRollsBackQuotaReservation() throws Exception {
        var template = template(5, 1);
        var source = context.getBean(DataSource.class);
        try (var connection = source.getConnection();
                var sql = connection.createStatement()) {
            sql.execute(
                    "CREATE TRIGGER jss_coupon_reject BEFORE INSERT ON promotion_coupon FOR EACH ROW "
                            + "BEGIN IF NEW.template_id="
                            + template.getId()
                            + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated insert failure'; END IF; END");
            try {
                Throwable error =
                        assertThrows(RuntimeException.class, () -> claim(template.getId(), 60L));
                while (error.getCause() != null) error = error.getCause();
                assertTrue(error.getMessage().contains("isolated insert failure"));
            } finally {
                sql.execute("DROP TRIGGER jss_coupon_reject");
            }
        }
        assertEquals(0, issued(template.getId()));
        assertEquals(0, templates.selectById(template.getId()).getTakeCount());
    }
}
