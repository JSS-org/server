package cn.iocoder.yudao.module.product.integration;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.product.dal.dataobject.sku.ProductSkuDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.sku.ProductSkuMapper;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;

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
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/** Runs only through the owned-MySQL wrapper; no Spring, H2 or mapper doubles. */
@EnabledIfEnvironmentVariable(
        named = "JSS_TEST_MYSQL_URL",
        matches = "jdbc:mysql://127[.]0[.]0[.]1:[0-9]+/jss_mapper_verify_[0-9a-f]+.*")
class JssProductMySqlMapperIT {
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
        configuration.addMapper(ProductSpuMapper.class);
        configuration.addMapper(ProductSkuMapper.class);
        configuration.addMapper(MemberUserMapper.class);
        sessions = new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private ProductSpuDO newSpu(ProductSpuMapper mapper) {
        var spu = new ProductSpuDO();
        spu.setName("isolated product");
        spu.setDescription("<p>current model</p>");
        spu.setCategoryId(1L);
        spu.setSliderPicUrls(List.of("https://example.invalid/product.png"));
        spu.setDeliveryTypes(List.of(1, 2));
        assertEquals(1, mapper.insert(spu));
        assertNotNull(spu.getId());
        return spu;
    }

    @Test
    void jsonAndCsvFieldsRoundTripThroughCurrentMapper() {
        TenantContextHolder.setTenantId(1L);
        try (var connection = sessions.openSession(true)) {
            var mapper = connection.getMapper(ProductSpuMapper.class);
            var spu = newSpu(mapper);
            var loaded = mapper.selectById(spu.getId());
            assertEquals(spu.getSliderPicUrls(), loaded.getSliderPicUrls());
            assertEquals(List.of(1, 2), loaded.getDeliveryTypes());
            assertEquals(
                    List.of(1, 2), mapper.selectByIdIncludeDeleted(spu.getId()).getDeliveryTypes());
        }
    }

    @Test
    void generatedAndCustomQueriesBothEnforceTenantAndLogicalDelete() {
        TenantContextHolder.setTenantId(1L);
        try (var connection = sessions.openSession(true)) {
            var mapper = connection.getMapper(ProductSpuMapper.class);
            var spu = newSpu(mapper);
            TenantContextHolder.setTenantId(2L);
            assertNull(mapper.selectById(spu.getId()));
            assertNull(mapper.selectByIdIncludeDeleted(spu.getId()));
            TenantContextHolder.setTenantId(1L);
            assertEquals(1, mapper.deleteById(spu.getId()));
            assertNull(mapper.selectById(spu.getId()));
            assertNotNull(mapper.selectByIdIncludeDeleted(spu.getId()));
            TenantContextHolder.setTenantId(2L);
            assertNull(mapper.selectByIdIncludeDeleted(spu.getId()));
        }
    }

    @Test
    void nestedSkuPropertiesAndConcurrentStockUseProductionMapper() throws Exception {
        TenantContextHolder.setTenantId(1L);
        Long id;
        try (var connection = sessions.openSession(true)) {
            var spu = newSpu(connection.getMapper(ProductSpuMapper.class));
            var sku = new ProductSkuDO();
            sku.setSpuId(spu.getId());
            sku.setStock(3);
            sku.setProperties(List.of(new ProductSkuDO.Property(1L, "颜色", 2L, "蓝色")));
            var mapper = connection.getMapper(ProductSkuMapper.class);
            assertEquals(1, mapper.insert(sku));
            id = sku.getId();
            assertEquals("蓝色", mapper.selectById(id).getProperties().getFirst().getValueName());
        }
        Callable<Integer> decrease =
                () -> {
                    TenantContextHolder.setTenantId(1L);
                    try (var connection = sessions.openSession(true)) {
                        return connection.getMapper(ProductSkuMapper.class).updateStockDecr(id, -2);
                    } finally {
                        TenantContextHolder.clear();
                    }
                };
        try (var workers = Executors.newFixedThreadPool(2)) {
            var results = workers.invokeAll(List.of(decrease, decrease));
            assertEquals(1, results.get(0).get() + results.get(1).get());
        }
        TenantContextHolder.setTenantId(1L);
        try (var connection = sessions.openSession(true)) {
            var mapper = connection.getMapper(ProductSkuMapper.class);
            assertEquals(1, mapper.selectById(id).getStock());
            assertEquals(2, mapper.selectById(id).getSalesCount());
            mapper.updateStockIncr(id, 2);
            assertEquals(3, mapper.selectById(id).getStock());
            assertEquals(0, mapper.selectById(id).getSalesCount());
        }
    }

    @Test
    void memberLoginIdentifiersAndTagHandlerAreTenantScoped() {
        TenantContextHolder.setTenantId(1L);
        try (var connection = sessions.openSession(true)) {
            var mapper = connection.getMapper(MemberUserMapper.class);
            var member = new MemberUserDO();
            member.setMobile("13800000003");
            member.setEmail("mapper@example.invalid");
            member.setStatus(0);
            member.setRegisterIp("::1");
            member.setTagIds(List.of(1L, 2L));
            mapper.insert(member);
            assertEquals(List.of(1L, 2L), mapper.selectByMobile(member.getMobile()).getTagIds());
            assertEquals(member.getId(), mapper.selectByEmail(member.getEmail()).getId());
            TenantContextHolder.setTenantId(2L);
            assertNull(mapper.selectByMobile(member.getMobile()));
            assertNull(mapper.selectByEmail(member.getEmail()));
        }
    }
}
