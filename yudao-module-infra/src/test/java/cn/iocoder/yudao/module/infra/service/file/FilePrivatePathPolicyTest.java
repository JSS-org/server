package cn.iocoder.yudao.module.infra.service.file;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.db.DBFileClient;

import org.junit.jupiter.api.Test;

class FilePrivatePathPolicyTest {
    @Test
    void privatePrefixesUseDirectoryBoundariesAndCanBeDisabledForOtherDeployments() {
        var policy = new FilePrivatePathPolicy(" gym/, archives/private/ ");
        assertTrue(policy.isPrivatePath("gym/contract/file.pdf"));
        assertTrue(policy.isPrivatePath("archives/private/report.pdf"));
        assertFalse(policy.isPrivatePath("gym-public/avatar.png"));
        assertFalse(new FilePrivatePathPolicy("").isPrivatePath("gym/contract/file.pdf"));
        assertFalse(policy.isPrivatePath(null));
    }

    @Test
    void privateStorageRequiresDatabaseButPublicStorageRemainsCompatible() {
        var policy = new FilePrivatePathPolicy("gym/");
        var external = mock(FileClient.class);
        assertThrows(
                ServiceException.class,
                () -> policy.validateUpload("gym/contract/file.pdf", external));
        assertDoesNotThrow(
                () -> policy.validateUpload("gym/contract/file.pdf", mock(DBFileClient.class)));
        assertDoesNotThrow(() -> policy.validateUpload("avatar/file.png", external));
        assertThrows(IllegalArgumentException.class, () -> new FilePrivatePathPolicy("gym"));
        assertThrows(ServiceException.class, () -> new FilePrivatePathPolicy("other/../gym/"));
    }
}
