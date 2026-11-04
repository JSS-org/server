package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileWorkFileUploadReqVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.contract.GymContractDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.file.GymFileDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.member.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.mobile.GymMobileWorkFileDO;
import cn.iocoder.yudao.module.gym.dal.mysql.contract.GymContractMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.member.*;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.GymMobileWorkFileMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.enums.file.*;
import cn.iocoder.yudao.module.gym.framework.file.core.utils.GymFileTypeUtils;
import cn.iocoder.yudao.module.gym.service.common.GymDataScope;
import cn.iocoder.yudao.module.gym.service.file.*;
import cn.iocoder.yudao.module.gym.service.file.dto.GymFileContentDTO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.io.IOException;
import java.util.*;

@Service
public class GymMobileWorkFilesService {
    @Resource private GymMobileWorkAccess access;
    @Resource private GymMobileCoachProfileService coachProfiles;
    @Resource private GymFileMapper files;
    @Resource private GymMobileWorkFileMapper provenance;
    @Resource private GymWorkFileBindings bindings;
    @Resource private GymMeasurementMapper measurements;
    @Resource private GymTrainingPlanMapper plans;
    @Resource private GymContractMapper contracts;
    @Resource private GymStoreMapper stores;
    @Resource private GymFileService legacyFiles;
    @Resource private GymDataScope scope;
    @Resource private SecurityFrameworkService security;
    @Resource private FileApi storage;
    @Resource private Validator validator;

    @Value("${gym.file.storage-config-id:#{null}}")
    private Long storageConfigId;

    public record UploadedFile(String fileId, String name, String mimeType, long byteSize) {}

    private void permission(String name) {
        if (!security.hasPermission(name)) throw exception(FORBIDDEN);
    }

    private static String category(String purpose) {
        return "coach_profile".equals(purpose) ? "image" : purpose;
    }

    private void business(Long user, String purpose, Long store, Long member, boolean write) {
        if ("coach_profile".equals(purpose)) {
            if (member != null) throw exception(MOBILE_REQUEST_INVALID);
            coachProfiles.requireFileEmployee(store);
            return;
        }
        if ("body_report".equals(purpose) || "training_plan".equals(purpose)) {
            if (member == null) throw exception(MOBILE_REQUEST_INVALID);
            permission("gym:measurements:" + (write ? "write" : "read"));
            access.member(member, store);
            return;
        }
        if ("contract".equals(purpose)) {
            if (member == null) {
                permission("gym:contracts:templates");
                scope.check(store);
            } else {
                permission("gym:contracts:" + (write ? "write" : "read"));
                access.member(member, store);
            }
            return;
        }
        throw exception(MOBILE_REQUEST_INVALID);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public UploadedFile upload(GymMobileWorkFileUploadReqVO req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        Long user = access.requireAdmin();
        permission("gym:files:upload");
        if (stores.selectById(req.getStoreId()) == null) throw exception(STORE_NOT_EXISTS);
        business(user, req.getPurpose(), req.getStoreId(), req.getMemberId(), true);
        Long employee =
                "coach_profile".equals(req.getPurpose())
                        ? coachProfiles.requireFileEmployee(req.getStoreId())
                        : null;
        var category = GymFileCategoryEnum.find(category(req.getPurpose()));
        long limit =
                "coach_profile".equals(req.getPurpose()) ? 5L * 1024 * 1024 : category.getMaxSize();
        var file = req.getFile();
        if (file.isEmpty()) throw exception(FILE_CONTENT_EMPTY);
        if (file.getSize() > limit)
            throw exception(FILE_SIZE_EXCEED_LIMIT, req.getPurpose(), limit, file.getSize());
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw exception(FILE_UPLOAD_FAILED, "无法读取上传内容");
        }
        if (bytes.length == 0) throw exception(FILE_CONTENT_EMPTY);
        if (bytes.length > limit)
            throw exception(FILE_SIZE_EXCEED_LIMIT, req.getPurpose(), limit, bytes.length);
        var detected = GymFileTypeUtils.detect(bytes);
        if (detected == null
                || !category.isMimeTypeAllowed(detected.getMimeType())
                || "coach_profile".equals(req.getPurpose())
                        && !Set.of(GymFileMimeTypeEnum.PNG, GymFileMimeTypeEnum.JPEG)
                                .contains(detected))
            throw exception(FILE_TYPE_NOT_ALLOWED, "文件内容不符合用途要求");
        var byName = GymFileTypeUtils.detectByFileName(file.getOriginalFilename());
        var byType = GymFileMimeTypeEnum.findByMimeType(file.getContentType());
        if (byName != null && byName != detected || byType != null && byType != detected)
            throw exception(FILE_TYPE_NOT_ALLOWED, "文件声明类型与内容不一致");
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) name = "upload." + detected.getExtension();
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\x00]", "");
        if (name.length() > 200) name = name.substring(name.length() - 200);
        if (name.isBlank()) name = "upload." + detected.getExtension();
        String url =
                storage.createFile(
                        bytes,
                        UUID.randomUUID() + "." + detected.getExtension(),
                        "gym/" + category.getCategory(),
                        detected.getMimeType(),
                        storageConfigId);
        var row =
                GymFileDO.builder()
                        .storeId(req.getStoreId())
                        .category(category.getCategory())
                        .name(name)
                        .url(url)
                        .mimeType(detected.getMimeType())
                        .size((long) bytes.length)
                        .uploaderId(user)
                        .build();
        files.insert(row);
        var link = new GymMobileWorkFileDO();
        link.setFileId(row.getId());
        link.setStoreId(req.getStoreId());
        link.setPurpose(req.getPurpose());
        link.setMemberId(req.getMemberId());
        link.setEmployeeId(employee);
        provenance.insert(link);
        return new UploadedFile(row.getId().toString(), name, row.getMimeType(), row.getSize());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GymFileContentDTO content(Long id) {
        if (id == null || id <= 0) throw exception(MOBILE_REQUEST_INVALID);
        Long user = access.requireAdmin();
        permission("gym:files:read");
        var file = files.selectById(id);
        if (file == null) throw exception(FILE_NOT_EXISTS);
        var link = bindings.find(id);
        if (link == null) {
            if ("image".equals(file.getCategory())) return coachProfiles.content(id);
            if (Set.of("body_report", "training_plan").contains(file.getCategory()))
                permission("gym:measurements:read");
            else if ("contract".equals(file.getCategory())) permission("gym:contracts:read");
            else throw exception(FILE_NOT_EXISTS);
            legacyFiles.getFile(id);
        } else {
            if (!Objects.equals(file.getStoreId(), link.getStoreId())
                    || !category(link.getPurpose()).equals(file.getCategory()))
                throw exception(FILE_NOT_EXISTS);
            business(user, link.getPurpose(), file.getStoreId(), link.getMemberId(), false);
            if ("coach_profile".equals(link.getPurpose())) {
                if (!Objects.equals(
                        coachProfiles.requireFileEmployee(file.getStoreId()), link.getEmployeeId()))
                    throw exception(MOBILE_FILE_NOT_OWNED);
            } else if (link.getMemberId() != null) {
                if (bound(file, link.getMemberId())) legacyFiles.getFile(id);
                else if (!Objects.equals(file.getUploaderId(), user))
                    throw exception(MOBILE_FILE_NOT_OWNED);
            }
        }
        byte[] bytes = storage.getFileContentByUrl(file.getUrl());
        if (bytes == null || bytes.length == 0) throw exception(FILE_CONTENT_NOT_EXISTS);
        var type = GymFileTypeUtils.detect(bytes);
        if (type == null || !Objects.equals(type.getMimeType(), file.getMimeType()))
            throw exception(FILE_NOT_EXISTS);
        return new GymFileContentDTO(file.getName(), file.getMimeType(), bytes);
    }

    private boolean bound(GymFileDO file, Long member) {
        return switch (file.getCategory()) {
            case "body_report" ->
                    measurements.selectCount(
                                    new LambdaQueryWrapper<GymMeasurementDO>()
                                            .eq(GymMeasurementDO::getReportFileId, file.getId())
                                            .eq(GymMeasurementDO::getMemberId, member)
                                            .eq(GymMeasurementDO::getStoreId, file.getStoreId()))
                            > 0;
            case "training_plan" ->
                    plans.selectCount(
                                    new LambdaQueryWrapper<GymTrainingPlanDO>()
                                            .eq(GymTrainingPlanDO::getFileId, file.getId())
                                            .eq(GymTrainingPlanDO::getMemberId, member)
                                            .eq(GymTrainingPlanDO::getStoreId, file.getStoreId()))
                            > 0;
            case "contract" ->
                    contracts.selectCount(
                                    new LambdaQueryWrapper<GymContractDO>()
                                            .eq(GymContractDO::getMemberId, member)
                                            .eq(GymContractDO::getStoreId, file.getStoreId())
                                            .and(
                                                    q ->
                                                            q.eq(
                                                                            GymContractDO
                                                                                    ::getUnsignedFileId,
                                                                            file.getId())
                                                                    .or()
                                                                    .eq(
                                                                            GymContractDO
                                                                                    ::getSignedFileId,
                                                                            file.getId())))
                            > 0;
            default -> false;
        };
    }
}
