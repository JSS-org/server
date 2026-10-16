package cn.iocoder.yudao.module.infra.api.file;

import static cn.hutool.core.util.StrUtil.isEmpty;

import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.service.file.FileService;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * 文件 API 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
@Slf4j
public class FileApiImpl implements FileApi {

    @Resource private FileService fileService;

    @Override
    public String createFile(byte[] content, String name, String directory, String type) {
        return fileService.createFile(content, name, directory, type);
    }

    @Override
    public String createFile(
            byte[] content, String name, String directory, String type, Long configId) {
        return fileService.createFile(content, name, directory, type, configId);
    }

    @Override
    public String presignGetUrl(String url, Integer expirationSeconds) {
        return fileService.presignGetUrl(url, expirationSeconds);
    }

    @Override
    public byte[] getFileContentByUrl(String url) {
        if (isEmpty(url)) {
            return null;
        }
        // 1. 反查文件记录，拿到存储配置 + 路径
        FileDO file = fileService.getFileByUrl(url);
        if (file == null) {
            log.warn("[getFileContentByUrl][url({}) 文件记录不存在]", url);
            return null;
        }
        // 2. 读取内容
        try {
            return fileService.getFileContent(file.getConfigId(), file.getPath());
        } catch (Exception ex) {
            log.error("[getFileContentByUrl][url({}) 文件内容读取失败]", url, ex);
            return null;
        }
    }
}
