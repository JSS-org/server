package cn.iocoder.yudao.module.member.integration;

import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.member.enums.point.MemberPointBizTypeEnum.*;

import static org.junit.jupiter.api.Assertions.*;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.mybatis.core.injector.YudaoSqlInjector;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.member.dal.dataobject.point.MemberPointRecordDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.point.MemberPointRecordMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.service.point.MemberPointRecordService;
import cn.iocoder.yudao.module.member.service.point.MemberPointRecordServiceImpl;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.member.service.user.MemberUserServiceImpl;

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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import javax.sql.DataSource;

/** Production ADMIN point changes and ledger on owned MySQL. No Redis or HTTP acceptance. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_MYSQL_URL",
        matches = "jdbc:mysql://127[.]0[.]0[.]1:13316/jss_member_point_verify_[0-9a-f]{12}.*")
class JssMemberPointMySqlServiceIT {
    @Test
    void adminHistoryFiltersDatesAlongWithMemberTitleTypeAndTenant() {
        var user = member(10);
        users.updateById(new MemberUserDO().setId(user.getId()).setNickname("HistoryTarget"));
        for (int index = 0; index < 4; index++) change(user.getId(), 1);
        var rows = records(user.getId());
        for (var row : rows)
            jdbc.update(
                    "UPDATE member_point_record SET title='dated', create_time='2026-10-01 00:00:00' WHERE id=?",
                    row.getId());
        jdbc.update(
                "UPDATE member_point_record SET create_time='2026-10-02 23:59:59' WHERE id=?",
                rows.get(1).getId());
        jdbc.update(
                "UPDATE member_point_record SET create_time='2026-09-30 23:59:59' WHERE id=?",
                rows.get(2).getId());
        jdbc.update(
                "UPDATE member_point_record SET title='different' WHERE id=?", rows.get(3).getId());
        var other = member(10);
        change(other.getId(), 1);
        jdbc.update(
                "UPDATE member_point_record SET title='dated', create_time='2026-10-01 00:00:00' WHERE user_id=?",
                other.getId());
        TenantContextHolder.setTenantId(2L);
        var foreign =
                new MemberPointRecordDO()
                        .setUserId(user.getId())
                        .setBizId("foreign")
                        .setBizType(ADMIN.getType())
                        .setTitle("dated")
                        .setPoint(1)
                        .setTotalPoint(1);
        foreign.setCreateTime(java.time.LocalDateTime.of(2026, 10, 1, 0, 0));
        points.insert(foreign);
        TenantContextHolder.setTenantId(1L);
        var query =
                new cn.iocoder.yudao.module.member.controller.admin.point.vo.recrod
                        .MemberPointRecordPageReqVO();
        query.setUserId(user.getId())
                .setNickname("HistoryTarget")
                .setTitle("dated")
                .setBizType(ADMIN.getType())
                .setCreateTime(
                        new java.time.LocalDateTime[] {
                            java.time.LocalDateTime.of(2026, 10, 1, 0, 0),
                            java.time.LocalDateTime.of(2026, 10, 2, 23, 59, 59)
                        });
        query.setPageSize(1);
        var page = service.getPointRecordPage(query);
        assertEquals(2L, page.getTotal());
        assertEquals(rows.get(1).getId(), page.getList().getFirst().getId());
        query.setPageNo(2);
        assertEquals(
                rows.get(0).getId(),
                service.getPointRecordPage(query).getList().getFirst().getId());
        query.setNickname("not-a-real-member");
        assertEquals(0L, service.getPointRecordPage(query).getTotal());
    }

    private static AnnotationConfigApplicationContext context;
    private static MemberPointRecordService service;
    private static MemberUserMapper users;
    private static MemberPointRecordMapper points;
    private static org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeAll
    static void setup() throws Exception {
        context = new AnnotationConfigApplicationContext();
        var target = new MemberUserServiceImpl();
        var advice = new TransactionInterceptor();
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(advice);
        context.getBeanFactory().registerSingleton("memberUserService", proxy.getProxy());
        context.register(Config.class);
        context.refresh();
        users = context.getBean(MemberUserMapper.class);
        points = context.getBean(MemberPointRecordMapper.class);
        advice.setTransactionManager(context.getBean(DataSourceTransactionManager.class));
        ReflectionTestUtils.setField(target, "memberUserMapper", users);
        var pointTarget = new MemberPointRecordServiceImpl();
        ReflectionTestUtils.setField(
                pointTarget, "memberUserService", context.getBean(MemberUserService.class));
        ReflectionTestUtils.setField(pointTarget, "memberPointRecordMapper", points);
        var pointProxy = new ProxyFactory(pointTarget);
        pointProxy.setProxyTargetClass(true);
        pointProxy.addAdvice(advice);
        service = (MemberPointRecordService) pointProxy.getProxy();
        jdbc = new org.springframework.jdbc.core.JdbcTemplate(context.getBean(DataSource.class));
        try (var c = context.getBean(DataSource.class).getConnection()) {
            assertTrue(c.getCatalog().matches("jss_member_point_verify_[0-9a-f]{12}"));
        }
    }

    @BeforeEach
    void clean() {
        TenantContextHolder.setTenantId(1L);
        for (String table : List.of("member_point_record", "member_user"))
            jdbc.execute("DELETE FROM " + table);
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
    @Import(SpringUtil.class)
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
                            "jss-member-points", new SpringManagedTransactionFactory(), source));
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
            configuration.addMapper(MemberPointRecordMapper.class);
            configuration.addMapper(MemberUserMapper.class);
            return new MybatisSqlSessionFactoryBuilder().build(configuration);
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }

        @Bean
        MemberPointRecordMapper pointMapper(SqlSessionTemplate sql) {
            return sql.getMapper(MemberPointRecordMapper.class);
        }

        @Bean
        MemberUserMapper userMapper(SqlSessionTemplate sql) {
            return sql.getMapper(MemberUserMapper.class);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }

    private MemberUserDO member(int balance) {
        var user =
                new MemberUserDO()
                        .setStatus(0)
                        .setNickname("Points")
                        .setPassword("fixture-only")
                        .setRegisterIp("127.0.0.1")
                        .setPoint(balance)
                        .setExperience(77)
                        .setLevelId(10L);
        users.insert(user);
        return user;
    }

    private List<MemberPointRecordDO> records(Long id) {
        return points.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<
                                MemberPointRecordDO>()
                        .eq(MemberPointRecordDO::getUserId, id)
                        .orderByAsc(MemberPointRecordDO::getId));
    }

    private void change(Long id, Integer amount) {
        service.createPointRecord(id, amount, ADMIN, "9007199254740993");
    }

    @Test
    void insufficientAdminDeductionAndMinIntegerAreBusinessErrorsWithoutWrites() {
        var user = member(20);
        for (int amount : new int[] {-21, Integer.MIN_VALUE}) {
            var error = assertThrows(ServiceException.class, () -> change(user.getId(), amount));
            assertEquals(USER_POINT_NOT_ENOUGH.getCode(), error.getCode());
        }
        assertEquals(20, users.selectById(user.getId()).getPoint());
        assertTrue(records(user.getId()).isEmpty());
    }

    @Test
    void positiveOverflowIsABusinessErrorForAdminAndAutomaticAwards() {
        var user = member(Integer.MAX_VALUE);
        assertEquals(
                USER_POINT_OUT_OF_RANGE.getCode(),
                assertThrows(ServiceException.class, () -> change(user.getId(), 1)).getCode());
        assertEquals(
                USER_POINT_OUT_OF_RANGE.getCode(),
                assertThrows(
                                ServiceException.class,
                                () ->
                                        service.createPointRecord(
                                                user.getId(), 1, SIGN, "sign-overflow"))
                        .getCode());
        assertEquals(Integer.MAX_VALUE, users.selectById(user.getId()).getPoint());
        assertTrue(records(user.getId()).isEmpty());
    }

    @Test
    void missingAndForeignMembersAndNullAmountsFailAsBusinessErrors() {
        var user = member(20);
        assertEquals(
                USER_POINT_INVALID.getCode(),
                assertThrows(ServiceException.class, () -> change(user.getId(), null)).getCode());
        assertThrows(ServiceException.class, () -> change(null, 1));
        assertThrows(ServiceException.class, () -> change(Long.MAX_VALUE, 1));
        TenantContextHolder.setTenantId(2L);
        var error = assertThrows(ServiceException.class, () -> change(user.getId(), 1));
        assertEquals(USER_NOT_EXISTS.getCode(), error.getCode());
        TenantContextHolder.setTenantId(1L);
        assertEquals(20, users.selectById(user.getId()).getPoint());
        assertTrue(records(user.getId()).isEmpty());
    }

    @Test
    void separateManualChangesByTheSameAdminRemainIndependentAndAudited() {
        var user = member(90);
        change(user.getId(), 10);
        change(user.getId(), 10);
        var saved = users.selectById(user.getId());
        assertEquals(110, saved.getPoint());
        assertEquals(77, saved.getExperience());
        assertEquals(10L, saved.getLevelId());
        assertEquals(
                List.of(100, 110),
                records(user.getId()).stream().map(MemberPointRecordDO::getTotalPoint).toList());
        assertTrue(
                records(user.getId()).stream()
                        .allMatch(
                                r ->
                                        r.getBizId().equals("9007199254740993")
                                                && r.getBizType().equals(ADMIN.getType())
                                                && r.getPoint() == 10));
    }

    private List<Throwable> parallelFromSameSnapshot(Long id, int amount) throws Exception {
        var ready = new CountDownLatch(6);
        var executor = Executors.newFixedThreadPool(6);
        var futures = new ArrayList<Future<Throwable>>();
        try {
            for (int index = 0; index < 6; index++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    TenantContextHolder.setTenantId(1L);
                                    try {
                                        new TransactionTemplate(
                                                        context.getBean(
                                                                DataSourceTransactionManager.class))
                                                .executeWithoutResult(
                                                        tx -> {
                                                            assertNotNull(
                                                                    users.selectById(
                                                                            id)); // establish an
                                                            // old RR
                                                            // snapshot/cache
                                                            // before
                                                            // competing
                                                            ready.countDown();
                                                            try {
                                                                assertTrue(
                                                                        ready.await(
                                                                                20,
                                                                                TimeUnit.SECONDS));
                                                            } catch (InterruptedException ex) {
                                                                throw new RuntimeException(ex);
                                                            }
                                                            change(id, amount);
                                                        });
                                        return null;
                                    } catch (Throwable ex) {
                                        return ex;
                                    } finally {
                                        TenantContextHolder.clear();
                                    }
                                }));
            }
            var errors = new ArrayList<Throwable>();
            for (var future : futures) {
                var failure = future.get(60, TimeUnit.SECONDS);
                if (failure != null) errors.add(failure);
            }
            return errors;
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void sixConcurrentCreditsHaveExactBalancesAndSequentialLedgerTotals() throws Exception {
        var user = member(90);
        assertTrue(parallelFromSameSnapshot(user.getId(), 10).isEmpty());
        assertEquals(150, users.selectById(user.getId()).getPoint());
        assertEquals(
                List.of(100, 110, 120, 130, 140, 150),
                records(user.getId()).stream().map(MemberPointRecordDO::getTotalPoint).toList());
    }

    @Test
    void sixConcurrentDeductionsOnlyAllowThreeAndRefuseTheRest() throws Exception {
        var user = member(90);
        var errors = parallelFromSameSnapshot(user.getId(), -30);
        assertEquals(3, errors.size());
        for (var ex : errors)
            assertEquals(
                    USER_POINT_NOT_ENOUGH.getCode(),
                    assertInstanceOf(ServiceException.class, ex).getCode());
        assertEquals(0, users.selectById(user.getId()).getPoint());
        assertEquals(
                List.of(60, 30, 0),
                records(user.getId()).stream().map(MemberPointRecordDO::getTotalPoint).toList());
    }

    @Test
    void concurrentCreditsNearIntegerLimitRejectOnlyTheOverflowingRequests() throws Exception {
        var user = member(Integer.MAX_VALUE - 30);
        var errors = parallelFromSameSnapshot(user.getId(), 10);
        assertEquals(3, errors.size());
        for (var ex : errors)
            assertEquals(
                    USER_POINT_OUT_OF_RANGE.getCode(),
                    assertInstanceOf(ServiceException.class, ex).getCode());
        assertEquals(Integer.MAX_VALUE, users.selectById(user.getId()).getPoint());
        assertEquals(3, records(user.getId()).size());
    }

    @Test
    void lateLedgerFailureAndOuterTransactionRollbackRestoreBalances() {
        var user = member(90);
        assertThrows(
                RuntimeException.class,
                () -> service.createPointRecord(user.getId(), 10, ADMIN, null));
        assertEquals(90, users.selectById(user.getId()).getPoint());
        assertTrue(records(user.getId()).isEmpty());
        new TransactionTemplate(context.getBean(DataSourceTransactionManager.class))
                .executeWithoutResult(
                        tx -> {
                            change(user.getId(), 10);
                            assertEquals(100, users.selectById(user.getId()).getPoint());
                            assertEquals(1, records(user.getId()).size());
                            tx.setRollbackOnly();
                        });
        assertEquals(90, users.selectById(user.getId()).getPoint());
        assertTrue(records(user.getId()).isEmpty());
        change(user.getId(), 10);
        assertEquals(100, users.selectById(user.getId()).getPoint());
        assertEquals(1, records(user.getId()).size());
    }

    @Test
    void automaticInsufficientDeductionsKeepTheirExistingNoOpPolicy() {
        var user = member(20);
        service.createPointRecord(user.getId(), -30, SIGN, "legacy-negative-sign");
        service.createPointRecord(user.getId(), -30, ORDER_GIVE_CANCEL, "refund");
        assertEquals(20, users.selectById(user.getId()).getPoint());
        assertTrue(records(user.getId()).isEmpty());
    }

    @Test
    void zeroRemainsNoOpAndLedgerReadsRespectTheTenant() {
        var user = member(20);
        change(user.getId(), 0);
        assertTrue(records(user.getId()).isEmpty());
        change(user.getId(), -20);
        assertEquals(0, users.selectById(user.getId()).getPoint());
        TenantContextHolder.setTenantId(2L);
        assertTrue(records(user.getId()).isEmpty());
        TenantContextHolder.setTenantId(1L);
        assertEquals(1, records(user.getId()).size());
    }
}
