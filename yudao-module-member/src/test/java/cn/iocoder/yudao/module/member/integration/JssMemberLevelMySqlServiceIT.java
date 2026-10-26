package cn.iocoder.yudao.module.member.integration;

import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;

import static org.junit.jupiter.api.Assertions.*;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.mybatis.core.injector.YudaoSqlInjector;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.member.controller.admin.level.vo.level.*;
import cn.iocoder.yudao.module.member.controller.admin.user.vo.MemberUserUpdateLevelReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.level.MemberLevelDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.level.MemberExperienceRecordMapper;
import cn.iocoder.yudao.module.member.dal.mysql.level.MemberLevelMapper;
import cn.iocoder.yudao.module.member.dal.mysql.level.MemberLevelRecordMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.enums.MemberExperienceBizTypeEnum;
import cn.iocoder.yudao.module.member.service.level.*;
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
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import javax.sql.DataSource;

/** Actual level configuration, assignment, experience and history services; no HTTP or Redis. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_MYSQL_URL",
        matches = "jdbc:mysql://127[.]0[.]0[.]1:13316/jss_member_level_verify_[0-9a-f]{12}.*")
class JssMemberLevelMySqlServiceIT {
    private static AnnotationConfigApplicationContext context;
    private static MemberLevelService service;
    private static MemberUserMapper users;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void setup() throws Exception {
        context = new AnnotationConfigApplicationContext();
        var userService = new MemberUserServiceImpl();
        context.getBeanFactory().registerSingleton("memberUserService", userService);
        context.register(Config.class);
        context.refresh();
        users = context.getBean(MemberUserMapper.class);
        ReflectionTestUtils.setField(userService, "memberUserMapper", users);
        service = context.getBean(MemberLevelService.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        try (var connection = context.getBean(DataSource.class).getConnection()) {
            assertTrue(connection.getCatalog().matches("jss_member_level_verify_[0-9a-f]{12}"));
        }
    }

    @BeforeEach
    void clean() {
        TenantContextHolder.setTenantId(1L);
        for (String table :
                List.of(
                        "member_level_record",
                        "member_experience_record",
                        "member_user",
                        "member_level")) jdbc.execute("DELETE FROM " + table);
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
    @Import({
        MemberLevelServiceImpl.class,
        MemberLevelRecordServiceImpl.class,
        MemberExperienceRecordServiceImpl.class,
        SpringUtil.class
    })
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
                            "jss-member-segments", new SpringManagedTransactionFactory(), source));
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
            configuration.addMapper(MemberLevelMapper.class);
            configuration.addMapper(MemberLevelRecordMapper.class);
            configuration.addMapper(MemberUserMapper.class);
            configuration.addMapper(MemberExperienceRecordMapper.class);
            return new MybatisSqlSessionFactoryBuilder().build(configuration);
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }

        @Bean
        MemberLevelMapper levelMapper(SqlSessionTemplate sql) {
            return sql.getMapper(MemberLevelMapper.class);
        }

        @Bean
        MemberLevelRecordMapper recordMapper(SqlSessionTemplate sql) {
            return sql.getMapper(MemberLevelRecordMapper.class);
        }

        @Bean
        MemberUserMapper userMapper(SqlSessionTemplate sql) {
            return sql.getMapper(MemberUserMapper.class);
        }

        @Bean
        MemberExperienceRecordMapper experienceMapper(SqlSessionTemplate sql) {
            return sql.getMapper(MemberExperienceRecordMapper.class);
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }

    private MemberLevelCreateReqVO form(String name, int level, int experience) {
        var request = new MemberLevelCreateReqVO();
        request.setName(name);
        request.setLevel(level);
        request.setExperience(experience);
        request.setDiscountPercent(0);
        request.setIcon("");
        request.setBackgroundUrl("");
        request.setStatus(0);
        return request;
    }

    private Long create(int value) {
        return service.createLevel(form("Level" + value, value, value * 100));
    }

    private MemberLevelUpdateReqVO change(Long id, String name, int value, int experience) {
        var request = new MemberLevelUpdateReqVO();
        request.setId(id);
        request.setName(name);
        request.setLevel(value);
        request.setExperience(experience);
        request.setDiscountPercent(100);
        request.setIcon("");
        request.setBackgroundUrl("");
        request.setStatus(0);
        return request;
    }

    private MemberUserDO member() {
        var user =
                new MemberUserDO()
                        .setStatus(0)
                        .setPassword("fixture-only")
                        .setRegisterIp("127.0.0.1")
                        .setNickname("LevelUser")
                        .setExperience(0)
                        .setLevelId(0L);
        users.insert(user);
        return user;
    }

    private MemberUserUpdateLevelReqVO assign(Long user, Long level) {
        var request = new MemberUserUpdateLevelReqVO();
        request.setId(user);
        request.setLevelId(level);
        request.setReason("Isolated acceptance");
        return request;
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void error(int code, Runnable action) {
        assertEquals(code, assertThrows(ServiceException.class, action::run).getCode());
    }

    private void parallelFromSameSnapshot(Long userId, java.util.function.IntConsumer action)
            throws Exception {
        var executor = Executors.newFixedThreadPool(6);
        var ready = new CyclicBarrier(6);
        var futures = new ArrayList<Future<?>>();
        try {
            for (int index = 0; index < 6; index++) {
                int participant = index;
                futures.add(
                        executor.submit(
                                () -> {
                                    TenantContextHolder.setTenantId(1L);
                                    try {
                                        var tx =
                                                new TransactionTemplate(
                                                        context.getBean(
                                                                DataSourceTransactionManager
                                                                        .class));
                                        tx.execute(
                                                status -> {
                                                    // Establish both a repeatable-read snapshot and
                                                    // the MyBatis first-level cache.
                                                    assertNotNull(users.selectById(userId));
                                                    try {
                                                        ready.await(10, TimeUnit.SECONDS);
                                                    } catch (Exception e) {
                                                        throw new RuntimeException(e);
                                                    }
                                                    action.accept(participant);
                                                    return null;
                                                });
                                    } finally {
                                        TenantContextHolder.clear();
                                    }
                                }));
            }
            for (var future : futures) future.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentExperienceEventsUseCurrentRowsAfterAnEarlierSnapshot() throws Exception {
        Long level = create(1);
        var user = member();
        users.updateById(new MemberUserDO().setId(user.getId()).setExperience(90));
        parallelFromSameSnapshot(
                user.getId(),
                index ->
                        service.addExperience(
                                user.getId(),
                                10,
                                MemberExperienceBizTypeEnum.SIGN_IN,
                                "concurrent-" + index));
        assertEquals(150, users.selectById(user.getId()).getExperience());
        assertEquals(level, users.selectById(user.getId()).getLevelId());
        assertEquals(
                List.of(100, 110, 120, 130, 140, 150),
                jdbc.queryForList(
                        "SELECT total_experience FROM member_experience_record ORDER BY id",
                        Integer.class));
        assertEquals(1, count("member_level_record"));
        assertEquals(6, count("member_experience_record"));
        assertEquals(
                10,
                jdbc.queryForObject("SELECT experience FROM member_level_record", Integer.class));
    }

    @Test
    void concurrentSameLevelAssignmentsOnlyWriteOneActualChange() throws Exception {
        Long level = create(1);
        var user = member();
        parallelFromSameSnapshot(
                user.getId(), index -> service.updateUserLevel(assign(user.getId(), level)));
        assertEquals(100, users.selectById(user.getId()).getExperience());
        assertEquals(1, count("member_level_record"));
        assertEquals(1, count("member_experience_record"));
    }

    @Test
    void experienceOverflowRejectsWithoutChangingAnyTable() {
        var user = member();
        users.updateById(new MemberUserDO().setId(user.getId()).setExperience(Integer.MAX_VALUE));
        assertThrows(
                ServiceException.class,
                () ->
                        service.addExperience(
                                user.getId(), 1, MemberExperienceBizTypeEnum.SIGN_IN, "overflow"));
        assertEquals(Integer.MAX_VALUE, users.selectById(user.getId()).getExperience());
        assertEquals(0, count("member_experience_record"));
        assertEquals(0, count("member_level_record"));
    }

    @Test
    void clampedDeductionsRecordOnlyTheExperienceActuallyRemoved() {
        var user = member();
        users.updateById(new MemberUserDO().setId(user.getId()).setExperience(5));
        service.addExperience(
                user.getId(),
                Integer.MIN_VALUE,
                MemberExperienceBizTypeEnum.ORDER_GIVE_CANCEL,
                "large-deduction");
        assertEquals(0, users.selectById(user.getId()).getExperience());
        assertEquals(
                -5,
                jdbc.queryForObject(
                        "SELECT experience FROM member_experience_record", Integer.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT total_experience FROM member_experience_record", Integer.class));
    }

    @Test
    void cancellingAnAlreadyCancelledLevelDoesNotCreateMoreRecords() {
        Long level = create(1);
        var user = member();
        service.updateUserLevel(assign(user.getId(), level));
        service.updateUserLevel(assign(user.getId(), null));
        service.updateUserLevel(assign(user.getId(), null));
        service.updateUserLevel(assign(user.getId(), 0L));
        assertEquals(2, count("member_experience_record"));
        assertEquals(2, count("member_level_record"));
    }

    @Test
    void missingMemberExperienceUsesTheBusinessErrorAndNoWrites() {
        error(
                USER_NOT_EXISTS.getCode(),
                () ->
                        service.addExperience(
                                9223372036854775807L,
                                1,
                                MemberExperienceBizTypeEnum.SIGN_IN,
                                "missing"));
        assertEquals(0, count("member_experience_record"));
    }

    @Test
    void nullExperienceIsRejectedAsABusinessErrorWithoutWrites() {
        var user = member();
        assertThrows(
                ServiceException.class,
                () ->
                        service.addExperience(
                                user.getId(), null, MemberExperienceBizTypeEnum.SIGN_IN, "null"));
        assertEquals(0, count("member_experience_record"));
    }

    @Test
    void deductionBelowEveryEnabledThresholdRemovesTheOldLevel() {
        Long level = create(1);
        var user = member();
        service.updateUserLevel(assign(user.getId(), level));
        service.addExperience(
                user.getId(), 100, MemberExperienceBizTypeEnum.ORDER_GIVE_CANCEL, "cancel-all");
        assertEquals(0L, users.selectById(user.getId()).getLevelId());
        assertEquals(0, users.selectById(user.getId()).getExperience());
        assertEquals(
                0L,
                jdbc.queryForObject(
                        "SELECT level_id FROM member_level_record ORDER BY id DESC LIMIT 1",
                        Long.class));
        assertEquals(
                -100,
                jdbc.queryForObject(
                        "SELECT experience FROM member_level_record ORDER BY id DESC LIMIT 1",
                        Integer.class));
    }

    @Test
    void configurationCrudOrderedFiltersAndEnabledZero() {
        Long high = create(2);
        Long low = create(1);
        var request = new MemberLevelListReqVO();
        assertEquals(
                List.of(low, high),
                service.getLevelList(request).stream().map(MemberLevelDO::getId).toList());
        assertEquals(0, service.getLevel(low).getDiscountPercent());
        var edit = change(low, "Changed", 1, 100);
        edit.setStatus(1);
        edit.setIcon("https://files.example/icon.png");
        service.updateLevel(edit);
        request.setStatus(0);
        assertEquals(
                List.of(high),
                service.getLevelList(request).stream().map(MemberLevelDO::getId).toList());
        assertFalse(
                service.getEnableLevelList().stream().anyMatch(item -> item.getId().equals(low)));
        request.setStatus(null);
        request.setName("Changed");
        assertEquals(
                List.of(low),
                service.getLevelList(request).stream().map(MemberLevelDO::getId).toList());
        assertEquals(edit.getIcon(), service.getLevel(low).getIcon());
        edit.setIcon("");
        service.updateLevel(edit);
        assertEquals("", service.getLevel(low).getIcon());
        service.deleteLevel(low);
        assertNull(service.getLevel(low));
    }

    @Test
    void namesValuesAndExperienceNeighborsRejectWithoutWriting() {
        Long low = create(1);
        Long high = create(3);
        error(LEVEL_NAME_EXISTS.getCode(), () -> service.createLevel(form("Level1", 2, 200)));
        error(LEVEL_VALUE_EXISTS.getCode(), () -> service.createLevel(form("Different", 1, 50)));
        error(LEVEL_EXPERIENCE_MIN.getCode(), () -> service.createLevel(form("Middle", 2, 100)));
        error(LEVEL_EXPERIENCE_MAX.getCode(), () -> service.createLevel(form("Middle", 2, 300)));
        error(
                LEVEL_EXPERIENCE_MAX.getCode(),
                () -> service.updateLevel(change(low, "Changed", 1, 300)));
        assertEquals(2, count("member_level"));
        assertEquals(100, service.getLevel(low).getExperience());
        assertEquals(300, service.getLevel(high).getExperience());
    }

    @Test
    void foreignTenantTargetsAndConfigurationValidationStayIsolated() {
        Long id = create(1);
        var own = member();
        TenantContextHolder.setTenantId(2L);
        assertNull(service.getLevel(id));
        assertTrue(service.getLevelList(new MemberLevelListReqVO()).isEmpty());
        error(LEVEL_NOT_EXISTS.getCode(), () -> service.updateLevel(change(id, "Foreign", 1, 100)));
        error(LEVEL_NOT_EXISTS.getCode(), () -> service.deleteLevel(id));
        Long local = create(1);
        assertNotEquals(id, local);
        error(USER_NOT_EXISTS.getCode(), () -> service.updateUserLevel(assign(own.getId(), local)));
        var other = member();
        error(LEVEL_NOT_EXISTS.getCode(), () -> service.updateUserLevel(assign(other.getId(), id)));
        assertEquals(0, count("member_level_record"));
        assertEquals(0, count("member_experience_record"));
        TenantContextHolder.setTenantId(1L);
        assertEquals("Level1", service.getLevel(id).getName());
    }

    @Test
    void largeLevelIdAndRealMembershipProtectDeletion() {
        Long id = 9007199254740993L;
        context.getBean(MemberLevelMapper.class)
                .insert(
                        new MemberLevelDO()
                                .setId(id)
                                .setName("Large")
                                .setLevel(1)
                                .setExperience(100)
                                .setDiscountPercent(100)
                                .setStatus(0)
                                .setIcon("")
                                .setBackgroundUrl(""));
        var user = member();
        service.updateUserLevel(assign(user.getId(), id));
        assertEquals(id, users.selectById(user.getId()).getLevelId());
        error(LEVEL_HAS_USER.getCode(), () -> service.deleteLevel(id));
        service.updateUserLevel(assign(user.getId(), null));
        service.deleteLevel(id);
        assertNull(service.getLevel(id));
        assertEquals(0L, users.selectById(user.getId()).getLevelId());
    }

    @Test
    void manualAssignmentAndCancellationPersistBothHistoriesAndSameLevelIsNoop() {
        Long id = create(1);
        var user = member();
        service.updateUserLevel(assign(user.getId(), id));
        assertEquals(100, users.selectById(user.getId()).getExperience());
        assertEquals(1, count("member_level_record"));
        assertEquals(1, count("member_experience_record"));
        service.updateUserLevel(assign(user.getId(), id));
        assertEquals(1, count("member_level_record"));
        service.updateUserLevel(assign(user.getId(), null));
        assertEquals(0, users.selectById(user.getId()).getExperience());
        assertEquals(0L, users.selectById(user.getId()).getLevelId());
        assertEquals(2, count("member_level_record"));
        assertEquals(2, count("member_experience_record"));
        assertEquals(
                -100,
                jdbc.queryForObject(
                        "SELECT experience FROM member_level_record ORDER BY id DESC LIMIT 1",
                        Integer.class));
    }

    @Test
    void earnedExperienceSelectsHighestEnabledThresholdAndCanDowngrade() {
        Long low = create(1);
        Long middle = create(2);
        Long high = create(3);
        var disabled = change(high, "Level3", 3, 300);
        disabled.setStatus(1);
        service.updateLevel(disabled);
        var user = member();
        service.addExperience(user.getId(), 350, MemberExperienceBizTypeEnum.SIGN_IN, "first");
        assertEquals(middle, users.selectById(user.getId()).getLevelId());
        service.addExperience(
                user.getId(), 200, MemberExperienceBizTypeEnum.ORDER_GIVE_CANCEL, "cancel");
        assertEquals(150, users.selectById(user.getId()).getExperience());
        assertEquals(low, users.selectById(user.getId()).getLevelId());
        assertEquals(2, count("member_level_record"));
        assertEquals(2, count("member_experience_record"));
        service.addExperience(user.getId(), 0, MemberExperienceBizTypeEnum.SIGN_IN, "zero");
        assertEquals(2, count("member_experience_record"));
    }

    @Test
    void lateSqlFailureAndOuterRollbackUndoUserAndBothHistories() {
        Long id = create(1);
        var user = member();
        jdbc.execute(
                "CREATE TRIGGER jss_level_fail BEFORE UPDATE ON member_user FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated rollback probe'");
        try {
            assertThrows(
                    RuntimeException.class,
                    () -> service.updateUserLevel(assign(user.getId(), id)));
        } finally {
            jdbc.execute("DROP TRIGGER jss_level_fail");
        }
        assertEquals(0, count("member_level_record"));
        assertEquals(0, count("member_experience_record"));
        assertEquals(0L, users.selectById(user.getId()).getLevelId());
        var tx = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
        tx.execute(
                status -> {
                    service.updateUserLevel(assign(user.getId(), id));
                    status.setRollbackOnly();
                    return null;
                });
        assertEquals(0, count("member_level_record"));
        assertEquals(0, count("member_experience_record"));
        assertEquals(0, users.selectById(user.getId()).getExperience());
    }
}
