package cn.iocoder.yudao.module.gym.service.file;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.gym.controller.admin.file.vo.GymFileUploadReqVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.file.GymFileDO;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.enums.file.GymFileCategoryEnum;
import cn.iocoder.yudao.module.gym.enums.file.GymFileMimeTypeEnum;
import cn.iocoder.yudao.module.gym.framework.file.core.utils.GymFileTypeUtils;
import cn.iocoder.yudao.module.gym.service.file.dto.GymFileContentDTO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * gym 文件 Service 实现类
 *
 * 越权校验思路（对齐旧 Mock 的 fileAccess + 08-gym-api-contract.md §1 数据范围约定）：
 * - 门店 = {@code system_dept}，权限范围走 yudao 的数据权限：总部角色 all=true，门店角色限本部门及以下；
 * - 上传：未传 storeId 时默认上传人所属部门；显式传入时必须落在自己的数据范围内；
 * - 读取（元数据 / 内容）：文件所属门店必须落在自己的数据范围内，否则 403（{@code 1_070_000_007}）。
 *
 * @author JSS
 */
@Service
@Validated
@Slf4j
public class GymFileServiceImpl implements GymFileService {

    /** infra 存储目录前缀，便于运维在文件存储里按业务归类 */
    private static final String DIRECTORY_PREFIX = "gym/";

    /** 存储用文件名的长度上限（infra_file.name 列为 varchar(255)，留出安全余量） */
    private static final int MAX_STORAGE_NAME_LENGTH = 180;

    @Resource private GymFileMapper gymFileMapper;

    @Resource
    private cn.iocoder.yudao.module.gym.dal.mysql.contract.GymContractTemplateMapper
            gymContractTemplateMapper;

    @Resource private FileApi fileApi;

    @org.springframework.beans.factory.annotation.Value("${gym.file.storage-config-id:#{null}}")
    private Long storageConfigId;

    @Resource private AdminUserApi adminUserApi;

    @Resource private DeptApi deptApi;

    @Resource private PermissionApi permissionApi;

    @Resource private GymMemberFileAccess memberFileAccess;

    @Override
    public GymFileDO uploadFile(GymFileUploadReqVO uploadReqVO) {
        // 1. 校验类别
        GymFileCategoryEnum category = GymFileCategoryEnum.find(uploadReqVO.getCategory());
        if (category == null) {
            throw exception(FILE_CATEGORY_INVALID, GymFileCategoryEnum.allCategories());
        }
        MultipartFile file = uploadReqVO.getFile();
        if (file == null || file.isEmpty()) {
            throw exception(FILE_CONTENT_EMPTY);
        }

        // 2. 先按 multipart 元信息判断大小，避免把超大文件整体读进内存
        if (file.getSize() > category.getMaxSize()) {
            throw exception(
                    FILE_SIZE_EXCEED_LIMIT,
                    category.getName(),
                    category.getMaxSize(),
                    file.getSize());
        }

        // 3. 读取内容，并以「魔数」判定真实类型（不信任 Content-Type / 后缀）
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            log.error(
                    "[uploadFile][category({}) name({}) 读取上传内容失败]",
                    category.getCategory(),
                    file.getOriginalFilename(),
                    ex);
            throw exception(FILE_UPLOAD_FAILED, ex.getMessage());
        }
        if (content.length == 0) {
            throw exception(FILE_CONTENT_EMPTY);
        }
        if (content.length > category.getMaxSize()) { // 双保险：元信息与实际内容不一致时以内容为准
            throw exception(
                    FILE_SIZE_EXCEED_LIMIT,
                    category.getName(),
                    category.getMaxSize(),
                    content.length);
        }
        GymFileMimeTypeEnum detected = GymFileTypeUtils.detect(content);
        if (detected == null) {
            throw exception(
                    FILE_TYPE_NOT_ALLOWED,
                    StrUtil.format("无法识别文件内容类型，仅支持 {}", GymFileMimeTypeEnum.allMimeTypes()));
        }

        // 4. 内容与「声明类型」比对：后缀、Content-Type 任一不一致即拒绝（旧 Mock 的 415 语义）
        GymFileMimeTypeEnum byName = GymFileTypeUtils.detectByFileName(file.getOriginalFilename());
        if (byName != null && byName != detected) {
            throw exception(
                    FILE_TYPE_NOT_ALLOWED,
                    StrUtil.format(
                            "文件名后缀声明为 {}，实际内容为 {}", byName.getMimeType(), detected.getMimeType()));
        }
        GymFileMimeTypeEnum byContentType =
                GymFileMimeTypeEnum.findByMimeType(file.getContentType());
        if (byContentType != null && byContentType != detected) {
            throw exception(
                    FILE_TYPE_NOT_ALLOWED,
                    StrUtil.format(
                            "请求声明的类型为 {}，实际内容为 {}",
                            byContentType.getMimeType(),
                            detected.getMimeType()));
        }

        // 5. 该类别是否允许此类型
        if (!category.isMimeTypeAllowed(detected.getMimeType())) {
            throw exception(
                    FILE_TYPE_NOT_ALLOWED,
                    StrUtil.format("类别 {} 不允许 {} 类型", category.getName(), detected.getMimeType()));
        }

        // 6. 门店：解析 + 存在性 + 数据范围校验
        Long storeId = resolveAndValidateStoreId(uploadReqVO.getStoreId(), category.getCategory());

        // 7. 存储文件（复用 infra 文件服务：本地磁盘 / 数据库 / S3 等按 infra 配置生效）
        //    存储名带唯一前缀：infra 默认路径是「日期/原文件名」，同名文件会落同一路径互相覆盖
        String storageName = buildStorageName(file.getOriginalFilename(), detected);
        String url =
                storageConfigId == null
                        ? fileApi.createFile(
                                content,
                                storageName,
                                DIRECTORY_PREFIX + category.getCategory(),
                                detected.getMimeType())
                        : fileApi.createFile(
                                content,
                                storageName,
                                DIRECTORY_PREFIX + category.getCategory(),
                                detected.getMimeType(),
                                storageConfigId);

        // 8. 落库业务归属（name 仍存原始文件名，展示/下载不受存储名影响）
        GymFileDO gymFile =
                GymFileDO.builder()
                        .storeId(storeId)
                        .category(category.getCategory())
                        .name(
                                StrUtil.blankToDefault(
                                        file.getOriginalFilename(),
                                        "unnamed." + detected.getExtension()))
                        .url(url)
                        .mimeType(detected.getMimeType())
                        .size((long) content.length)
                        .uploaderId(SecurityFrameworkUtils.getLoginUserId())
                        .build();
        gymFileMapper.insert(gymFile);
        return gymFile;
    }

    /**
     * 构造「存储用」文件名：{@code <uuid>-<原文件名>}
     *
     * 背景（缺陷修复）：infra 默认的上传路径是「日期/原文件名」（{@code FileServiceImpl#generateUploadPath}，
     * 且 {@code PATH_SUFFIX_TIMESTAMP_ENABLE=false}），因此**同名文件当天会落到同一路径互相覆盖**。
     * 这里给每次上传加唯一前缀，保证存储路径唯一；原文件名保留在 {@code gym_file.name}，展示与下载不受影响。
     *
     * 为什么不改 infra 的 {@code PATH_SUFFIX_TIMESTAMP_ENABLE}：那是全局开关，会影响其他所有模块的存储路径，
     * 属于平台级决策；gym 侧自愈成本更低、影响面更小。
     *
     * @param originalName 原始文件名，允许为空
     * @param mimeType     魔数嗅探出的类型（用于补默认后缀）
     * @return 存储用文件名，长度受 {@link #MAX_STORAGE_NAME_LENGTH} 限制（infra_file.name 列为 varchar(255)）
     */
    String buildStorageName(String originalName, GymFileMimeTypeEnum mimeType) {
        String name = StrUtil.blankToDefault(originalName, "unnamed." + mimeType.getExtension());
        String unique = IdUtil.fastSimpleUUID();
        String extension = FileUtil.extName(name);
        String mainName = FileUtil.mainName(name);
        // 截断主文件名，避免「uuid + 原文件名」超过 infra_file.name 的长度上限
        int maxMainNameLength =
                MAX_STORAGE_NAME_LENGTH
                        - unique.length()
                        - 1
                        - (StrUtil.isEmpty(extension) ? 0 : extension.length() + 1);
        if (mainName.length() > maxMainNameLength) {
            mainName = StrUtil.subPre(mainName, maxMainNameLength);
        }
        return unique
                + "-"
                + mainName
                + (StrUtil.isEmpty(extension) ? StrUtil.EMPTY : StrUtil.DOT + extension);
    }

    @Override
    public GymFileDO getFile(Long id) {
        GymFileDO file = gymFileMapper.selectById(id);
        if (file == null) {
            throw exception(FILE_NOT_EXISTS);
        }
        // 门店越权校验：非本门店（且不在数据范围内）的文件，一律按无权处理
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (!publishedTemplateAccess(file, userId) && !memberFileAccess.canRead(file))
            validateStorePermission(userId, file.getStoreId());
        return file;
    }

    @Override
    public GymFileContentDTO getFileContent(Long id) {
        GymFileDO file = getFile(id); // 一次查询 + 一次越权校验
        byte[] content = fileApi.getFileContentByUrl(file.getUrl());
        if (content == null) {
            throw exception(FILE_CONTENT_NOT_EXISTS);
        }
        return GymFileContentDTO.builder()
                .name(file.getName())
                .mimeType(file.getMimeType())
                .content(content)
                .build();
    }

    /**
     * 解析归属门店：未传则取上传人所属部门；随后校验门店存在且在当前登录人的数据范围内
     *
     * @param storeId 请求传入的门店编号，允许为空
     * @return 最终归属门店编号
     */
    private Long resolveAndValidateStoreId(Long storeId, String category) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (storeId == null) {
            AdminUserRespDTO user = adminUserApi.getUser(userId);
            storeId = user != null ? user.getDeptId() : null;
            if (storeId == null) {
                throw exception(FILE_STORE_REQUIRED);
            }
        }
        DeptRespDTO dept = deptApi.getDept(storeId);
        if (dept == null) {
            throw exception(FILE_STORE_NOT_EXISTS, storeId);
        }
        if (!memberFileAccess.canUploadReport(storeId, category))
            validateStorePermission(userId, storeId);
        return storeId;
    }

    /**
     * 校验门店是否在当前登录人的数据范围内
     *
     * 规则来自 yudao 数据权限（角色 data_scope）：
     * 全部（all=true）放行；否则门店编号必须命中 deptIds。
     *
     * @param userId  登录用户编号
     * @param storeId 门店编号，允许为空（为空表示无门店归属的公共文件）
     */
    private void validateStorePermission(Long userId, Long storeId) {
        DeptDataPermissionRespDTO permission = permissionApi.getDeptDataPermission(userId);
        if (storeId == null) {
            // An unbound legacy file is not a responsible member's private attachment.
            if (permission != null
                    && Boolean.TRUE.equals(permission.getSelf())
                    && !Boolean.TRUE.equals(permission.getAll())
                    && (permission.getDeptIds() == null || permission.getDeptIds().isEmpty()))
                throw exception(FILE_STORE_NO_PERMISSION, storeId);
            return;
        }
        if (permission != null && Boolean.TRUE.equals(permission.getAll())) {
            return;
        }
        if (permission != null
                && permission.getDeptIds() != null
                && permission.getDeptIds().contains(storeId)) {
            return;
        }
        throw exception(FILE_STORE_NO_PERMISSION, storeId);
    }

    /** Published blank contract templates are intentionally shared; unrelated PDFs and draft/suspended templates remain store-private. */
    private boolean publishedTemplateAccess(GymFileDO file, Long userId) {
        if (!"contract".equals(file.getCategory()) || !"application/pdf".equals(file.getMimeType()))
            return false;
        var permission = permissionApi.getDeptDataPermission(userId);
        if (permission == null
                || (!Boolean.TRUE.equals(permission.getAll())
                        && (permission.getDeptIds() == null || permission.getDeptIds().isEmpty())))
            return false;
        return permissionApi.hasAnyPermissions(
                        userId, "gym:contracts:read", "gym:contracts:templates")
                && gymContractTemplateMapper.isPublishedFile(file.getId());
    }
}
