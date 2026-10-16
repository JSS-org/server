package cn.iocoder.yudao.framework.datapermission.core.util;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionContextHolder;

import org.junit.jupiter.api.Test;

public class DataPermissionUtilsTest {

    @Test
    public void testExecuteIgnore() {
        DataPermissionUtils.executeIgnore(
                () -> assertFalse(DataPermissionContextHolder.get().enable()));
    }
}
