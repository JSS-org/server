package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileFileUploadReqVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.contract.GymContractDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.file.GymFileDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.mobile.GymMobileFileOwnerDO;
import cn.iocoder.yudao.module.gym.dal.mysql.contract.GymContractMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.member.*;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.GymMobileFileOwnerMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.enums.file.GymFileMimeTypeEnum;
import cn.iocoder.yudao.module.gym.framework.file.core.utils.GymFileTypeUtils;
import cn.iocoder.yudao.module.gym.service.file.dto.GymFileContentDTO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Service
public class GymMobileFilesService {
    @Resource private GymMobileIdentityService identities;
    @Resource private GymFileMapper files;
    @Resource private GymMobileFileOwnerMapper owners;
    @Resource private GymStoreMapper stores;
    @Resource private GymMeasurementMapper measurements;
    @Resource private GymTrainingPlanMapper plans;
    @Resource private GymContractMapper contracts;
    @Resource private FileApi storage;
    @Resource private Validator validator;

    @Value("${gym.file.storage-config-id:#{null}}")
    private Long storageConfigId;

    public record UploadedFile(String fileId, String name, String mimeType, long byteSize) {}

    @Transactional(rollbackFor = Exception.class)
    public UploadedFile upload(GymMobileFileUploadReqVO req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        var identity = identities.requireIdentity();
        Long storeId = req.getStoreId() == null ? identity.getDefaultStoreId() : req.getStoreId();
        var store = stores.selectById(storeId);
        if (store == null || !"active".equals(store.getStatus()))
            throw exception(MEMBER_STORE_INACTIVE);
        long limit = ("feedback".equals(req.getPurpose()) ? 10L : 5L) * 1024 * 1024;
        var file = req.getFile();
        if (file.isEmpty()) throw exception(FILE_CONTENT_EMPTY);
        if (file.getSize() > limit)
            throw exception(FILE_SIZE_EXCEED_LIMIT, req.getPurpose(), limit, file.getSize());
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw exception(FILE_UPLOAD_FAILED, "无法读取上传内容");
        }
        if (content.length == 0) throw exception(FILE_CONTENT_EMPTY);
        if (content.length > limit)
            throw exception(FILE_SIZE_EXCEED_LIMIT, req.getPurpose(), limit, content.length);
        var detected = GymFileTypeUtils.detect(content);
        if (detected == null
                || !Set.of(GymFileMimeTypeEnum.JPEG, GymFileMimeTypeEnum.PNG).contains(detected))
            throw exception(FILE_TYPE_NOT_ALLOWED, "仅支持JPEG、PNG图片");
        var nameType = GymFileTypeUtils.detectByFileName(file.getOriginalFilename());
        var declaredType = GymFileMimeTypeEnum.findByMimeType(file.getContentType());
        if (nameType != null && nameType != detected
                || declaredType != null && declaredType != detected)
            throw exception(FILE_TYPE_NOT_ALLOWED, "文件声明类型与内容不一致");
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) name = "upload." + detected.getExtension();
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.length() > 200) name = name.substring(name.length() - 200);
        name = name.replaceAll("[\\r\\n\\x00]", "");
        if (name.isBlank()) name = "upload." + detected.getExtension();
        String category = "member_" + req.getPurpose();
        String storageName = UUID.randomUUID() + "." + detected.getExtension();
        // The configured gym/ private namespace rejects public object storage adapters.
        String url =
                storage.createFile(
                        content,
                        storageName,
                        "gym/" + category,
                        detected.getMimeType(),
                        storageConfigId);
        var row =
                GymFileDO.builder()
                        .storeId(storeId)
                        .category(category)
                        .name(name)
                        .url(url)
                        .mimeType(detected.getMimeType())
                        .size((long) content.length)
                        .build();
        // uploaderId is deliberately not a member ID: the existing column refers to system users.
        files.insert(row);
        var owner = new GymMobileFileOwnerDO();
        owner.setFileId(row.getId());
        owner.setMemberId(identity.getMemberId());
        owner.setPurpose(req.getPurpose());
        owners.insert(owner);
        return new UploadedFile(
                row.getId().toString(), name, detected.getMimeType(), content.length);
    }

    public GymFileContentDTO content(Long id) {
        Long member = identities.requireMemberId();
        var file = files.selectById(id);
        if (file == null) throw exception(FILE_NOT_EXISTS);
        var owner = owners.selectOne(GymMobileFileOwnerDO::getFileId, id);
        // Face originals are not exposed through a general download endpoint, even to the uploader.
        if ("member_face".equals(file.getCategory())
                || owner != null && "face".equals(owner.getPurpose()))
            throw exception(MOBILE_FILE_NOT_OWNED);
        boolean allowed = owner != null && member.equals(owner.getMemberId());
        if (!allowed && "body_report".equals(file.getCategory()))
            allowed =
                    measurements.selectCount(
                                    new LambdaQueryWrapper<GymMeasurementDO>()
                                            .eq(GymMeasurementDO::getMemberId, member)
                                            .eq(GymMeasurementDO::getReportFileId, id))
                            > 0;
        if (!allowed && "training_plan".equals(file.getCategory()))
            allowed =
                    plans.selectCount(
                                    new LambdaQueryWrapper<GymTrainingPlanDO>()
                                            .eq(GymTrainingPlanDO::getMemberId, member)
                                            .eq(GymTrainingPlanDO::getFileId, id))
                            > 0;
        if (!allowed && "contract".equals(file.getCategory()))
            allowed =
                    contracts.selectCount(
                                    new LambdaQueryWrapper<GymContractDO>()
                                            .eq(GymContractDO::getMemberId, member)
                                            .and(
                                                    q ->
                                                            q.eq(
                                                                            GymContractDO
                                                                                    ::getUnsignedFileId,
                                                                            id)
                                                                    .or()
                                                                    .eq(
                                                                            GymContractDO
                                                                                    ::getSignedFileId,
                                                                            id)))
                            > 0;
        if (!allowed) throw exception(MOBILE_FILE_NOT_OWNED);
        byte[] content = storage.getFileContentByUrl(file.getUrl());
        if (content == null) throw exception(FILE_CONTENT_NOT_EXISTS);
        return new GymFileContentDTO(file.getName(), file.getMimeType(), content);
    }
}
