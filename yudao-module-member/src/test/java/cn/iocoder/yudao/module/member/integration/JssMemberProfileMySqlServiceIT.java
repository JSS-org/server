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
import cn.iocoder.yudao.module.member.controller.admin.user.vo.MemberUserUpdateReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.group.MemberGroupDO;
import cn.iocoder.yudao.module.member.dal.dataobject.tag.MemberTagDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.group.MemberGroupMapper;
import cn.iocoder.yudao.module.member.dal.mysql.tag.MemberTagMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

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

import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

/** Production profile service and mapper/tenant SQL. No Redis or HTTP acceptance. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_MYSQL_URL",
        matches = "jdbc:mysql://127[.]0[.]0[.]1:13316/jss_member_profile_verify_[0-9a-f]{12}.*")
class JssMemberProfileMySqlServiceIT {
    private static AnnotationConfigApplicationContext context;
    private static MemberUserService service;
    private static MemberUserMapper users;
    private static MemberTagMapper tags;
    private static MemberGroupMapper groups;
    private static org.springframework.jdbc.core.JdbcTemplate jdbc;
    private static final ObjectMapper json =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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
        tags = context.getBean(MemberTagMapper.class);
        groups = context.getBean(MemberGroupMapper.class);
        advice.setTransactionManager(context.getBean(DataSourceTransactionManager.class));
        ReflectionTestUtils.setField(target, "memberUserMapper", users);
        ReflectionTestUtils.setField(target, "memberTagMapper", tags);
        ReflectionTestUtils.setField(target, "memberGroupMapper", groups);
        service = context.getBean(MemberUserService.class);
        jdbc = new org.springframework.jdbc.core.JdbcTemplate(context.getBean(DataSource.class));
        try (var c = context.getBean(DataSource.class).getConnection()) {
            assertTrue(c.getCatalog().matches("jss_member_profile_verify_[0-9a-f]{12}"));
        }
    }

    @BeforeEach
    void clean() {
        TenantContextHolder.setTenantId(1L);
        for (String table : List.of("member_user", "member_tag", "member_group", "member_level"))
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
            configuration.addMapper(MemberTagMapper.class);
            configuration.addMapper(MemberGroupMapper.class);
            configuration.addMapper(MemberUserMapper.class);
            return new MybatisSqlSessionFactoryBuilder().build(configuration);
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }

        @Bean
        MemberTagMapper tagMapper(SqlSessionTemplate sql) {
            return sql.getMapper(MemberTagMapper.class);
        }

        @Bean
        MemberGroupMapper groupMapper(SqlSessionTemplate sql) {
            return sql.getMapper(MemberGroupMapper.class);
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

    private MemberUserDO member() {
        var user =
                new MemberUserDO()
                        .setStatus(0)
                        .setNickname("Original")
                        .setPassword("fixture-only")
                        .setRegisterIp("127.0.0.1")
                        .setLevelId(10L)
                        .setExperience(77)
                        .setPoint(88);
        users.insert(user);
        return user;
    }

    private MemberUserUpdateReqVO form(MemberUserDO user) {
        var r = new MemberUserUpdateReqVO();
        r.setId(user.getId());
        r.setMobile("");
        r.setNickname("Updated");
        r.setStatus((byte) 0);
        return r;
    }

    private Long tag() {
        var t = new MemberTagDO().setName("Tag" + UUID.randomUUID().toString().substring(0, 12));
        tags.insert(t);
        return t.getId();
    }

    private Long group(int status) {
        var g = new MemberGroupDO().setName("Group").setStatus(status).setRemark("");
        groups.insert(g);
        return g.getId();
    }

    @Test
    void profileCannotBypassTheDedicatedLevelChangePermissionOrAlterOtherBalances() {
        var user = member();
        var r = form(user);
        r.setLevelId(9007199254740993L);
        service.updateUser(r);
        var saved = users.selectById(user.getId());
        assertEquals(10L, saved.getLevelId());
        assertEquals(77, saved.getExperience());
        assertEquals(88, saved.getPoint());
        assertEquals("Updated", saved.getNickname());
    }

    @Test
    void newlyAssignedTagsAndGroupsMustBeVisibleInTheSameTenant() {
        var user = member();
        TenantContextHolder.setTenantId(2L);
        Long foreignTag = tag();
        Long foreignGroup = group(0);
        TenantContextHolder.setTenantId(1L);
        var first = form(user);
        first.setTagIds(List.of(foreignTag));
        assertThrows(ServiceException.class, () -> service.updateUser(first));
        var request = form(user);
        request.setGroupId(foreignGroup);
        assertThrows(ServiceException.class, () -> service.updateUser(request));
        assertEquals("Original", users.selectById(user.getId()).getNickname());
    }

    @Test
    void disabledOriginalGroupCanBeRetainedButCannotBeNewlyAssigned() {
        Long disabled = group(1);
        var user = member();
        var r = form(user);
        r.setGroupId(disabled);
        assertThrows(ServiceException.class, () -> service.updateUser(r));
        users.updateById(new MemberUserDO().setId(user.getId()).setGroupId(disabled));
        service.updateUser(r);
        assertEquals(disabled, users.selectById(user.getId()).getGroupId());
        r.setGroupId(0L);
        service.updateUser(r);
        assertNull(users.selectById(user.getId()).getGroupId());
    }

    @Test
    void nullableProfileFieldsDistinguishOmittedValuesFromExplicitClears() throws Exception {
        var user = member();
        var birth = java.time.LocalDateTime.of(2000, 1, 2, 0, 0);
        Long g = group(0);
        users.updateById(
                new MemberUserDO()
                        .setId(user.getId())
                        .setBirthday(birth)
                        .setAreaId(110000)
                        .setGroupId(g));
        service.updateUser(form(user));
        assertEquals(birth, users.selectById(user.getId()).getBirthday());
        assertEquals(g, users.selectById(user.getId()).getGroupId());
        var forged =
                json.readValue(
                        "{\"id\":"
                                + user.getId()
                                + ",\"mobile\":\"\",\"nickname\":\"Still\",\"status\":0,\"birthdayPresent\":true,\"areaIdPresent\":true,\"groupIdPresent\":true}",
                        MemberUserUpdateReqVO.class);
        service.updateUser(forged);
        assertEquals(birth, users.selectById(user.getId()).getBirthday());
        assertEquals(g, users.selectById(user.getId()).getGroupId());
        assertFalse(json.writeValueAsString(forged).contains("Present"));
        var r =
                json.readValue(
                        "{\"id\":"
                                + user.getId()
                                + ",\"mobile\":\"\",\"nickname\":\"Cleared\",\"status\":0,\"birthday\":null,\"areaId\":null,\"groupId\":null}",
                        MemberUserUpdateReqVO.class);
        service.updateUser(r);
        var saved = users.selectById(user.getId());
        assertNull(saved.getBirthday());
        assertNull(saved.getAreaId());
        assertNull(saved.getGroupId());
    }

    @Test
    void tagAssignmentDeduplicatesLargeIdsAndExplicitNullClears() throws Exception {
        var user = member();
        Long id = 9007199254740993L;
        tags.insert(new MemberTagDO().setId(id).setName("Long"));
        var r = form(user);
        r.setTagIds(List.of(id, id));
        service.updateUser(r);
        assertEquals(List.of(id), users.selectById(user.getId()).getTagIds());
        tags.deleteById(id);
        service.updateUser(r);
        var other = member();
        var fresh = form(other);
        fresh.setTagIds(List.of(id));
        assertThrows(ServiceException.class, () -> service.updateUser(fresh));
        r.setTagIds(null);
        service.updateUser(r);
        assertTrue(users.selectById(user.getId()).getTagIds().isEmpty());
        assertEquals(0, service.getUserCountByTagId(id));
    }

    @Test
    void targetTenantAndDuplicateLoginIdentifiersAreEnforced() {
        var user = member();
        users.updateById(
                new MemberUserDO()
                        .setId(user.getId())
                        .setMobile("13800000101")
                        .setEmail("taken@example.test"));
        var second = member();
        var duplicate = form(second);
        duplicate.setMobile("13800000101");
        assertThrows(ServiceException.class, () -> service.updateUser(duplicate));
        duplicate.setMobile("");
        duplicate.setEmail("taken@example.test");
        assertThrows(ServiceException.class, () -> service.updateUser(duplicate));
        TenantContextHolder.setTenantId(2L);
        assertThrows(ServiceException.class, () -> service.updateUser(form(user)));
        var other = member();
        var r = form(other);
        r.setMobile("13800000101");
        r.setEmail("taken@example.test");
        service.updateUser(r);
        assertEquals("taken@example.test", users.selectById(other.getId()).getEmail());
    }

    @Test
    void profileChangesParticipateInTheActualServiceAndOuterTransactionRollback() {
        var user = member();
        var tx = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
        tx.execute(
                status -> {
                    service.updateUser(form(user));
                    assertEquals("Updated", users.selectById(user.getId()).getNickname());
                    status.setRollbackOnly();
                    return null;
                });
        assertEquals("Original", users.selectById(user.getId()).getNickname());
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(service));
    }

    @Test
    void invalidTagElementsAndOversizedCommaFieldsFailBeforeWriting() {
        var user = member();
        var r = form(user);
        r.setTagIds(java.util.Arrays.asList(1L, null));
        assertThrows(ServiceException.class, () -> service.updateUser(r));
        r.setTagIds(
                java.util.stream.LongStream.range(9007199254740993L, 9007199254741010L)
                        .boxed()
                        .toList());
        assertThrows(ServiceException.class, () -> service.updateUser(r));
        r.setTagIds(null);
        r.setAreaId(2147483648L);
        assertEquals(
                USER_AREA_INVALID.getCode(),
                assertThrows(ServiceException.class, () -> service.updateUser(r)).getCode());
        assertEquals("Original", users.selectById(user.getId()).getNickname());
    }
}
