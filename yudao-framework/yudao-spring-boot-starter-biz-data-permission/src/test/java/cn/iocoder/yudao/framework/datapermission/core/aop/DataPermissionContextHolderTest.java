package cn.iocoder.yudao.framework.datapermission.core.aop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;

import com.alibaba.ttl.TtlCallable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * {@link DataPermissionContextHolder} 的单元测试
 *
 * @author 芋道源码
 */
class DataPermissionContextHolderTest {

    @BeforeEach
    public void setUp() {
        DataPermissionContextHolder.clear();
    }

    @Test
    public void testGet() {
        // mock 方法
        DataPermission dataPermission01 = mock(DataPermission.class);
        DataPermissionContextHolder.add(dataPermission01);
        DataPermission dataPermission02 = mock(DataPermission.class);
        DataPermissionContextHolder.add(dataPermission02);

        // 调用
        DataPermission result = DataPermissionContextHolder.get();
        // 断言
        assertSame(result, dataPermission02);
    }

    @Test
    public void testPush() {
        // 调用
        DataPermission dataPermission01 = mock(DataPermission.class);
        DataPermissionContextHolder.add(dataPermission01);
        DataPermission dataPermission02 = mock(DataPermission.class);
        DataPermissionContextHolder.add(dataPermission02);
        // 断言
        DataPermission first = DataPermissionContextHolder.getAll().get(0);
        DataPermission second = DataPermissionContextHolder.getAll().get(1);
        assertSame(dataPermission01, first);
        assertSame(dataPermission02, second);
    }

    @Test
    public void testRemove() {
        // mock 方法
        DataPermission dataPermission01 = mock(DataPermission.class);
        DataPermissionContextHolder.add(dataPermission01);
        DataPermission dataPermission02 = mock(DataPermission.class);
        DataPermissionContextHolder.add(dataPermission02);

        // 调用
        DataPermission result = DataPermissionContextHolder.remove();
        // 断言
        assertSame(result, dataPermission02);
        assertEquals(1, DataPermissionContextHolder.getAll().size());
    }

    @Test
    public void testCapturedContextSurvivesParentPop() throws Exception {
        var permission = mock(DataPermission.class);
        try (var executor = Executors.newSingleThreadExecutor()) {
            // Start the worker before the request scope exists, as with a pooled async executor.
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
            DataPermissionContextHolder.add(permission);
            var captured = new FutureTask<>(TtlCallable.get(DataPermissionContextHolder::get));
            assertSame(permission, DataPermissionContextHolder.remove());
            executor.execute(captured);
            assertSame(permission, captured.get(5, TimeUnit.SECONDS));
            assertNull(executor.submit(DataPermissionContextHolder::get).get(5, TimeUnit.SECONDS));
            assertNull(DataPermissionContextHolder.get());
        }
    }

    @Test
    public void testTaskPushAndPopDoNotChangeParentOrAnotherCapture() throws Exception {
        var permission = mock(DataPermission.class);
        var nested = mock(DataPermission.class);
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
            DataPermissionContextHolder.add(permission);
            var first = new FutureTask<>(TtlCallable.get(() -> {
                DataPermissionContextHolder.add(nested);
                assertSame(nested, DataPermissionContextHolder.remove());
                assertSame(permission, DataPermissionContextHolder.remove());
                return DataPermissionContextHolder.get();
            }));
            var second = new FutureTask<>(TtlCallable.get(DataPermissionContextHolder::get));
            executor.execute(first);
            assertNull(first.get(5, TimeUnit.SECONDS));
            assertSame(permission, DataPermissionContextHolder.get());
            executor.execute(second);
            assertSame(permission, second.get(5, TimeUnit.SECONDS));
            assertSame(permission, DataPermissionContextHolder.remove());
            assertNull(executor.submit(DataPermissionContextHolder::get).get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testReturnedListCannotMutateContext() {
        var permission = mock(DataPermission.class);
        DataPermissionContextHolder.add(permission);
        DataPermissionContextHolder.getAll().clear();
        assertSame(permission, DataPermissionContextHolder.get());
        assertSame(permission, DataPermissionContextHolder.remove());
    }
}
