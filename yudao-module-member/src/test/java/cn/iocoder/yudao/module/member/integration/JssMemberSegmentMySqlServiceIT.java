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
import cn.iocoder.yudao.module.member.controller.admin.group.vo.*;
import cn.iocoder.yudao.module.member.controller.admin.tag.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.group.MemberGroupDO;
import cn.iocoder.yudao.module.member.dal.dataobject.tag.MemberTagDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.group.MemberGroupMapper;
import cn.iocoder.yudao.module.member.dal.mysql.tag.MemberTagMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.service.group.MemberGroupService;
import cn.iocoder.yudao.module.member.service.group.MemberGroupServiceImpl;
import cn.iocoder.yudao.module.member.service.tag.MemberTagService;
import cn.iocoder.yudao.module.member.service.tag.MemberTagServiceImpl;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

/** Production classification services and real user count methods/SQL; no HTTP or Redis. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_MYSQL_URL",
        matches = "jdbc:mysql://127[.]0[.]0[.]1:13316/jss_member_segment_verify_[0-9a-f]{12}.*")
class JssMemberSegmentMySqlServiceIT {
    private static AnnotationConfigApplicationContext context;
    private static MemberTagService tags;
    private static MemberGroupService groups;
    private static MemberUserMapper users;

    @BeforeAll
    static void setup() throws Exception {
        context = new AnnotationConfigApplicationContext();
        // Register the real count service without instantiating unrelated SMS/social/MQ clients.
        // The two count methods only delegate to the production mapper injected below.
        var userService = new MemberUserServiceImpl();
        context.getBeanFactory().registerSingleton("memberUserService", userService);
        context.register(Config.class);
        context.refresh();
        users = context.getBean(MemberUserMapper.class);
        ReflectionTestUtils.setField(userService, "memberUserMapper", users);
        tags = context.getBean(MemberTagService.class);
        groups = context.getBean(MemberGroupService.class);
        try (var connection = context.getBean(DataSource.class).getConnection()) {
            assertTrue(connection.getCatalog().matches("jss_member_segment_verify_[0-9a-f]{12}"));
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
    @Import({MemberTagServiceImpl.class, MemberGroupServiceImpl.class, SpringUtil.class})
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

    private String unique() {
        return "JSS" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private Long tag(String name) {
        var request = new MemberTagCreateReqVO();
        request.setName(name);
        return tags.createTag(request);
    }

    private Long group(String name, int status) {
        var request = new MemberGroupCreateReqVO();
        request.setName(name);
        request.setStatus(status);
        request.setRemark("");
        return groups.createGroup(request);
    }

    private MemberTagUpdateReqVO tagUpdate(Long id, String name) {
        var request = new MemberTagUpdateReqVO();
        request.setId(id);
        request.setName(name);
        return request;
    }

    private MemberGroupUpdateReqVO groupUpdate(Long id, String name, int status) {
        var request = new MemberGroupUpdateReqVO();
        request.setId(id);
        request.setName(name);
        request.setStatus(status);
        request.setRemark("");
        return request;
    }

    private MemberUserDO member(Long group, List<Long> tagIds) {
        var user =
                new MemberUserDO()
                        .setStatus(0)
                        .setPassword("fixture-only")
                        .setRegisterIp("127.0.0.1")
                        .setNickname(unique())
                        .setGroupId(group)
                        .setTagIds(tagIds);
        users.insert(user);
        return user;
    }

    private void error(int code, Runnable action) {
        assertEquals(code, assertThrows(ServiceException.class, action::run).getCode());
    }

    @Test
    void tagCrudDatesDuplicateNamesAndLogicalDeletion() {
        String name = unique();
        Long id = tag(name);
        error(TAG_NAME_EXISTS.getCode(), () -> tag(name));
        tags.updateTag(tagUpdate(id, name));
        var query = new MemberTagPageReqVO();
        query.setName(name);
        query.setCreateTime(
                new LocalDateTime[] {
                    LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(1)
                });
        assertEquals(
                List.of(id),
                tags.getTagPage(query).getList().stream().map(MemberTagDO::getId).toList());
        query.setCreateTime(
                new LocalDateTime[] {
                    LocalDateTime.now().minusYears(2), LocalDateTime.now().minusYears(1)
                });
        assertEquals(0, tags.getTagPage(query).getTotal());
        tags.deleteTag(id);
        assertNull(tags.getTag(id));
        assertFalse(tags.getTagList().stream().anyMatch(item -> item.getId().equals(id)));
        assertNotNull(tag(name));
        error(TAG_NOT_EXISTS.getCode(), () -> tags.updateTag(tagUpdate(id, name)));
    }

    @Test
    void groupStatusZeroPaginationAndEnabledLookup() {
        String name = unique();
        Long enabled = group(name, 0);
        Long disabled = group(name, 1);
        var query = new MemberGroupPageReqVO();
        query.setName(name);
        query.setStatus(0);
        query.setPageSize(1);
        assertEquals(
                List.of(enabled),
                groups.getGroupPage(query).getList().stream().map(MemberGroupDO::getId).toList());
        assertTrue(
                groups.getEnableGroupList().stream()
                        .anyMatch(item -> item.getId().equals(enabled)));
        assertFalse(
                groups.getEnableGroupList().stream()
                        .anyMatch(item -> item.getId().equals(disabled)));
        var change = groupUpdate(enabled, name + "X", 1);
        change.setRemark("r".repeat(255));
        groups.updateGroup(change);
        assertEquals(change.getRemark(), groups.getGroup(enabled).getRemark());
        assertFalse(
                groups.getEnableGroupList().stream()
                        .anyMatch(item -> item.getId().equals(enabled)));
        query.setStatus(null);
        query.setPageNo(2);
        assertEquals(
                List.of(enabled),
                groups.getGroupPage(query).getList().stream().map(MemberGroupDO::getId).toList());
        query.setCreateTime(
                new LocalDateTime[] {
                    LocalDateTime.now().minusYears(2), LocalDateTime.now().minusYears(1)
                });
        assertEquals(0, groups.getGroupPage(query).getTotal());
    }

    @Test
    void tenantReadsUpdatesDeletesAndSimpleListsStayIsolated() {
        String name = unique();
        Long tag = tag(name);
        Long group = group(name, 0);
        TenantContextHolder.setTenantId(2L);
        assertNull(tags.getTag(tag));
        assertNull(groups.getGroup(group));
        error(TAG_NOT_EXISTS.getCode(), () -> tags.updateTag(tagUpdate(tag, name + "X")));
        error(TAG_NOT_EXISTS.getCode(), () -> tags.deleteTag(tag));
        error(
                GROUP_NOT_EXISTS.getCode(),
                () -> groups.updateGroup(groupUpdate(group, name + "X", 1)));
        error(GROUP_NOT_EXISTS.getCode(), () -> groups.deleteGroup(group));
        assertFalse(tags.getTagList().stream().anyMatch(item -> item.getId().equals(tag)));
        assertFalse(
                groups.getEnableGroupList().stream().anyMatch(item -> item.getId().equals(group)));
        Long own = tag(name);
        assertNotEquals(tag, own);
        TenantContextHolder.setTenantId(1L);
        assertEquals(name, tags.getTag(tag).getName());
        assertEquals(0, groups.getGroup(group).getStatus());
    }

    @Test
    void linkedMembersProtectGroupsAndExactCommaSeparatedTags() {
        Long group = group(unique(), 0);
        Long tag = tag(unique());
        Long other = tag(unique());
        var user = member(group, List.of(other, tag));
        assertEquals(1, users.selectCountByTagId(tag));
        error(TAG_HAS_USER.getCode(), () -> tags.deleteTag(tag));
        error(GROUP_HAS_USER.getCode(), () -> groups.deleteGroup(group));
        assertNotNull(tags.getTag(tag));
        assertNotNull(groups.getGroup(group));
        users.updateById(
                new MemberUserDO().setId(user.getId()).setTagIds(List.of(other)).setGroupId(0L));
        tags.deleteTag(tag);
        groups.deleteGroup(group);
        assertNull(tags.getTag(tag));
        assertNull(groups.getGroup(group));
        assertNotNull(tags.getTag(other));
    }

    @Test
    void otherTenantAndDeletedMembersDoNotBlockLocalDeletion() {
        Long tag = tag(unique());
        Long group = group(unique(), 0);
        TenantContextHolder.setTenantId(2L);
        member(group, List.of(tag));
        TenantContextHolder.setTenantId(1L);
        var local = member(group, List.of(tag));
        users.deleteById(local.getId());
        assertEquals(0, users.selectCountByTagId(tag));
        assertEquals(0, users.selectCountByGroupId(group));
        tags.deleteTag(tag);
        groups.deleteGroup(group);
        assertNull(tags.getTag(tag));
        assertNull(groups.getGroup(group));
    }

    @Test
    void outerTransactionRollsBackBothClassificationWrites() {
        String name = unique();
        var tx = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
        var ids =
                tx.execute(
                        status -> {
                            Long tag = tag(name);
                            Long group = group(name, 0);
                            tags.updateTag(tagUpdate(tag, name + "X"));
                            groups.updateGroup(groupUpdate(group, name + "X", 1));
                            status.setRollbackOnly();
                            return List.of(tag, group);
                        });
        assertNull(tags.getTag(ids.get(0)));
        assertNull(groups.getGroup(ids.get(1)));
    }

    @Test
    void largeIdsRemainExactThroughUpdateMembershipCountsAndDelete() {
        Long tag = 9007199254740993L;
        Long group = 9007199254740995L;
        context.getBean(MemberTagMapper.class)
                .insert(new MemberTagDO().setId(tag).setName(unique()));
        context.getBean(MemberGroupMapper.class)
                .insert(
                        new MemberGroupDO()
                                .setId(group)
                                .setName(unique())
                                .setStatus(0)
                                .setRemark(""));
        tags.updateTag(tagUpdate(tag, unique()));
        groups.updateGroup(groupUpdate(group, unique(), 1));
        var user = member(group, List.of(tag));
        assertEquals(1, users.selectCountByTagId(tag));
        assertEquals(1, users.selectCountByGroupId(group));
        error(TAG_HAS_USER.getCode(), () -> tags.deleteTag(tag));
        error(GROUP_HAS_USER.getCode(), () -> groups.deleteGroup(group));
        users.deleteById(user.getId());
        tags.deleteTag(tag);
        groups.deleteGroup(group);
        assertNull(tags.getTag(tag));
        assertNull(groups.getGroup(group));
    }
}
