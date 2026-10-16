package cn.iocoder.yudao.module.infra.api.file;

import jakarta.validation.constraints.NotEmpty;

/**
 * 文件 API 接口
 *
 * @author 芋道源码
 */
public interface FileApi {

    /**
     * 保存文件，并返回文件的访问路径
     *
     * @param content 文件内容
     * @return 文件路径
     */
    default String createFile(byte[] content) {
        return createFile(content, null, null, null);
    }

    /**
     * 保存文件，并返回文件的访问路径
     *
     * @param content 文件内容
     * @param name 文件名称，允许空
     * @return 文件路径
     */
    default String createFile(byte[] content, String name) {
        return createFile(content, name, null, null);
    }

    /**
     * 保存文件，并返回文件的访问路径
     *
     * @param content 文件内容
     * @param name 文件名称，允许空
     * @param directory 目录，允许空
     * @param type 文件的 MIME 类型，允许空
     * @return 文件路径
     */
    String createFile(
            @NotEmpty(message = "文件内容不能为空") byte[] content,
            String name,
            String directory,
            String type);

    /** Explicit backend-selected storage; null retains the existing master-storage behavior. */
    default String createFile(
            byte[] content, String name, String directory, String type, Long configId) {
        if (configId == null) return createFile(content, name, directory, type);
        throw new UnsupportedOperationException("Explicit file storage is not implemented");
    }

    /**
     * 生成文件预签名地址，用于读取
     *
     * @param url 完整的文件访问地址
     * @param expirationSeconds 访问有效期，单位秒
     * @return 文件预签名地址
     */
    String presignGetUrl(@NotEmpty(message = "URL 不能为空") String url, Integer expirationSeconds);

    /**
     * 读取文件内容
     *
     * 场景：调用方把 {@link #createFile} 返回的 url 落库后，需要在**自己的鉴权接口**里回读内容
     * （例如 gym 模块的私有文件下载：门店/类别越权校验由调用方负责，不能直接暴露 infra 的公开下载地址）。
     *
     * @param url 文件访问地址（{@link #createFile} 的返回值）
     * @return 文件内容；文件不存在时返回 null
     */
    byte[] getFileContentByUrl(String url);
}
