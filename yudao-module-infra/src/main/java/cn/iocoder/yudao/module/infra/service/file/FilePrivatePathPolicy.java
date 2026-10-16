package cn.iocoder.yudao.module.infra.service.file;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_PRIVATE_STORAGE_REQUIRED;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_PRIVATE_UPLOAD_REQUIRED;

import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.db.DBFileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FilePathUtils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/** Private namespaces are served only through their owning business's authenticated content API. */
@Component
public class FilePrivatePathPolicy {

    private final List<String> prefixes;

    public FilePrivatePathPolicy(
            @Value("${yudao.file.private-path-prefixes:}") String configuredPrefixes) {
        prefixes =
                Arrays.stream(configuredPrefixes.split(","))
                        .map(String::trim)
                        .filter(prefix -> !prefix.isEmpty())
                        .toList();
        for (String prefix : prefixes) {
            if (!prefix.endsWith("/")) {
                throw new IllegalArgumentException("Private file prefixes must end with /");
            }
            FilePathUtils.validatePath(prefix.substring(0, prefix.length() - 1));
        }
    }

    public boolean isPrivatePath(String path) {
        return path != null && prefixes.stream().anyMatch(path::startsWith);
    }

    public void validatePublicUploadDirectory(String directory) {
        FilePathUtils.validateDirectory(directory);
        if (directory != null && !directory.isEmpty() && isPrivatePath(directory + "/upload")) {
            throw exception(FILE_PRIVATE_UPLOAD_REQUIRED);
        }
    }

    public void validateUpload(String path, FileClient client) {
        // DB storage has no external bucket or static-file URL that can bypass our HTTP gate.
        // Other private storage adapters need their own proven access-control contract first.
        if (isPrivatePath(path) && !(client instanceof DBFileClient)) {
            throw exception(FILE_PRIVATE_STORAGE_REQUIRED);
        }
    }
}
