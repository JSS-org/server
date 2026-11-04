package cn.iocoder.yudao.module.iot.framework.storage.config;

import cn.iocoder.yudao.module.iot.dal.storage.IotDeviceMessageDAO;
import cn.iocoder.yudao.module.iot.dal.storage.IotDevicePropertyDAO;
import cn.iocoder.yudao.module.iot.framework.storage.core.enums.IotStorageTypeEnum;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * IoT 时序存储的初始化 Runner：校验数据源、前提条件，并创建消息表
 *
 * 只初始化 yudao.iot.storage.type 选中的存储后端。显式 disabled 时不提供时序读写；其他模式失败则中止启动。
 *
 * @author 芋道源码
 */
@Component
@Slf4j
public class IotStorageInitRunner implements ApplicationRunner {

    @Resource private IotStorageProperties storageProperties;
    @Resource private DataSource dataSource;

    @Resource private IotDevicePropertyDAO devicePropertyDAO;
    @Resource private IotDeviceMessageDAO deviceMessageDAO;

    @Override
    public void run(ApplicationArguments args) {
        // Let Spring own startup failure and orderly cleanup; never terminate the host JVM here.
        initialize();
    }

    /**
     * 初始化选中的存储后端
     */
    public void initialize() {
        if (storageProperties.getType() == IotStorageTypeEnum.DISABLED) {
            log.warn("[initialize][IoT 时序存储已显式禁用，属性历史和消息日志读写不可用]");
            return;
        }
        // 1. 校验数据源已配置：dynamic-datasource 找不到数据源时，会回退到 master，必须提前拦截
        validateDataSourceExists();
        // 2. 初始化 DAO
        devicePropertyDAO.initialize();
        deviceMessageDAO.initialize();
        log.info("[initialize][IoT 时序存储({}) 初始化完成]", storageProperties.getType());
    }

    private void validateDataSourceExists() {
        String dataSourceName = storageProperties.getType().getDataSourceName();
        if (!(dataSource instanceof DynamicRoutingDataSource)) {
            throw new IllegalStateException(
                    String.format(
                            "[IoT 时序存储] 需要使用 dynamic-datasource 多数据源，当前为 %s",
                            dataSource.getClass().getName()));
        }
        if (!((DynamicRoutingDataSource) dataSource).getDataSources().containsKey(dataSourceName)) {
            throw new IllegalStateException(
                    String.format(
                            "[IoT 时序存储] 未配置数据源 spring.datasource.dynamic.datasource.%s，"
                                    + "请检查 yudao.iot.storage.type=%s 对应的数据源配置",
                            dataSourceName, storageProperties.getType().getType()));
        }
    }
}
