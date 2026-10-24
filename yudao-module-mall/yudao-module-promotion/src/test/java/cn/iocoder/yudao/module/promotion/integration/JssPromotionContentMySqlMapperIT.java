package cn.iocoder.yudao.module.promotion.integration;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.promotion.dal.dataobject.article.ArticleDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.diy.DiyPageDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.diy.DiyTemplateDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.kefu.KeFuConversationDO;
import cn.iocoder.yudao.module.promotion.dal.dataobject.kefu.KeFuMessageDO;
import cn.iocoder.yudao.module.promotion.dal.mysql.article.ArticleMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.diy.DiyPageMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.diy.DiyTemplateMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.kefu.KeFuConversationMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.kefu.KeFuMessageMapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

/** Runs only through the owned-MySQL wrapper; no Spring, H2 or mapper doubles. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_MYSQL_URL",
        matches = "jdbc:mysql://127[.]0[.]0[.]1:[0-9]+/jss_content_mapper_verify_[0-9a-f]+.*")
class JssPromotionContentMySqlMapperIT {
    private static SqlSessionFactory sessions;

    @BeforeAll
    static void configureActualMappers() {
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(
                new Environment(
                        "jss-isolated-mysql",
                        new JdbcTransactionFactory(),
                        new UnpooledDataSource(
                                "com.mysql.cj.jdbc.Driver",
                                System.getenv("JSS_TEST_MYSQL_URL"),
                                System.getenv("JSS_TEST_MYSQL_USER"),
                                System.getenv("JSS_TEST_MYSQL_PASSWORD"))));
        configuration.setMapUnderscoreToCamelCase(true);
        GlobalConfigUtils.setGlobalConfig(
                configuration,
                new GlobalConfig()
                        .setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO))
                        .setMetaObjectHandler(new DefaultDBFieldHandler())
                        .setSqlInjector(new DefaultSqlInjector()));
        var plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(
                new TenantLineInnerInterceptor(
                        new TenantDatabaseInterceptor(new TenantProperties())));
        configuration.addInterceptor(plugins);
        configuration.addMapper(DiyTemplateMapper.class);
        configuration.addMapper(DiyPageMapper.class);
        configuration.addMapper(ArticleMapper.class);
        configuration.addMapper(KeFuConversationMapper.class);
        configuration.addMapper(KeFuMessageMapper.class);
        sessions = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void decorationPreviewImagesAndPropertyRoundTripThroughActualHandlers() {
        TenantContextHolder.setTenantId(1L);
        try (var session = sessions.openSession(true)) {
            var templates = session.getMapper(DiyTemplateMapper.class);
            var template =
                    new DiyTemplateDO()
                            .setName("isolated-template")
                            .setPreviewPicUrls(
                                    List.of(
                                            "https://example.invalid/1.png",
                                            "https://example.invalid/2.png"))
                            .setProperty("{\"tabBar\":{}}");
            templates.insert(template);
            var loaded = templates.selectByName(template.getName());
            assertEquals(template.getPreviewPicUrls(), loaded.getPreviewPicUrls());
            assertEquals(template.getProperty(), loaded.getProperty());
            assertFalse(loaded.getUsed());
            assertNull(loaded.getUsedTime());
            var pages = session.getMapper(DiyPageMapper.class);
            var page =
                    new DiyPageDO()
                            .setTemplateId(template.getId())
                            .setName("首页")
                            .setPreviewPicUrls(template.getPreviewPicUrls())
                            .setProperty("{\"components\":[]}");
            pages.insert(page);
            assertEquals(
                    page.getProperty(),
                    pages.selectListByTemplateId(template.getId()).getFirst().getProperty());
            assertEquals(
                    page.getPreviewPicUrls(), pages.selectById(page.getId()).getPreviewPicUrls());
            assertNull(pages.selectByNameAndTemplateIdIsNull("首页"));
            TenantContextHolder.setTenantId(2L);
            assertNull(templates.selectByName(template.getName()));
            assertTrue(pages.selectListByTemplateId(template.getId()).isEmpty());
        }
    }

    @Test
    void usedTemplateAndStandalonePageQueriesRespectTenantAndDeletion() {
        TenantContextHolder.setTenantId(1L);
        try (var session = sessions.openSession(true)) {
            var templates = session.getMapper(DiyTemplateMapper.class);
            var template = new DiyTemplateDO().setName("isolated-used").setUsed(true);
            templates.insert(template);
            assertEquals(template.getId(), templates.selectByUsed(true).getId());
            TenantContextHolder.setTenantId(2L);
            assertNull(templates.selectByUsed(true));
            TenantContextHolder.setTenantId(1L);
            templates.deleteById(template.getId());
            assertNull(templates.selectByUsed(true));
            var pages = session.getMapper(DiyPageMapper.class);
            var page = new DiyPageDO().setName("isolated-standalone").setProperty("{}");
            pages.insert(page);
            assertEquals(
                    page.getId(), pages.selectByNameAndTemplateIdIsNull(page.getName()).getId());
            TenantContextHolder.setTenantId(2L);
            assertNull(pages.selectByNameAndTemplateIdIsNull(page.getName()));
            TenantContextHolder.setTenantId(1L);
            pages.deleteById(page.getId());
            assertNull(pages.selectByNameAndTemplateIdIsNull(page.getName()));
            var replacement = new DiyPageDO().setName(page.getName());
            assertEquals(1, pages.insert(replacement));
        }
    }

    @Test
    void articleTextAndBrowseUpdateStayWithinTenant() {
        TenantContextHolder.setTenantId(1L);
        try (var session = sessions.openSession(true)) {
            var mapper = session.getMapper(ArticleMapper.class);
            var article =
                    new ArticleDO()
                            .setCategoryId(1L)
                            .setTitle("isolated-article")
                            .setContent("<p>正文 😀</p>")
                            .setRecommendHot(true);
            mapper.insert(article);
            assertEquals(
                    article.getContent(),
                    mapper.selectListByTitle(article.getTitle()).getFirst().getContent());
            mapper.updateBrowseCount(article.getId());
            assertEquals(1, mapper.selectById(article.getId()).getBrowseCount());
            TenantContextHolder.setTenantId(2L);
            mapper.updateBrowseCount(article.getId());
            assertTrue(mapper.selectListByTitle(article.getTitle()).isEmpty());
            TenantContextHolder.setTenantId(1L);
            assertEquals(1, mapper.selectById(article.getId()).getBrowseCount());
            mapper.deleteById(article.getId());
            assertTrue(mapper.selectListByTitle(article.getTitle()).isEmpty());
        }
    }

    @Test
    void customerMessagesAndUnreadUpdatesRemainTenantScoped() {
        TenantContextHolder.setTenantId(1L);
        try (var session = sessions.openSession(true)) {
            var conversations = session.getMapper(KeFuConversationMapper.class);
            var conversation = new KeFuConversationDO().setUserId(10L);
            conversations.insert(conversation);
            conversations.updateAdminUnreadMessageCountIncrement(conversation.getId());
            assertEquals(1, conversations.selectByUserId(10L).getAdminUnreadMessageCount());
            var messages = session.getMapper(KeFuMessageMapper.class);
            var message =
                    new KeFuMessageDO()
                            .setConversationId(conversation.getId())
                            .setSenderId(10L)
                            .setSenderType(1)
                            .setReceiverId(20L)
                            .setReceiverType(2)
                            .setContentType(1)
                            .setContent("isolated 😀");
            messages.insert(message);
            assertEquals(
                    message.getId(),
                    messages.selectListByConversationIdAndUserTypeAndReadStatus(
                                    conversation.getId(), 2, false)
                            .getFirst()
                            .getId());
            TenantContextHolder.setTenantId(2L);
            assertNull(conversations.selectByUserId(10L));
            assertTrue(
                    messages.selectListByConversationIdAndUserTypeAndReadStatus(
                                    conversation.getId(), 2, false)
                            .isEmpty());
            conversations.updateAdminUnreadMessageCountIncrement(conversation.getId());
            messages.updateReadStatusBatchByIds(
                    List.of(message.getId()), new KeFuMessageDO().setReadStatus(true));
            TenantContextHolder.setTenantId(1L);
            assertEquals(1, conversations.selectByUserId(10L).getAdminUnreadMessageCount());
            assertFalse(messages.selectById(message.getId()).getReadStatus());
            messages.updateReadStatusBatchByIds(
                    List.of(message.getId()), new KeFuMessageDO().setReadStatus(true));
            assertTrue(
                    messages.selectListByConversationIdAndUserTypeAndReadStatus(
                                    conversation.getId(), 2, false)
                            .isEmpty());
        }
    }
}
