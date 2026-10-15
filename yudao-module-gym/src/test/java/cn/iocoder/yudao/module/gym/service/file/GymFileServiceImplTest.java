package cn.iocoder.yudao.module.gym.service.file;

import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.gym.controller.admin.file.vo.GymFileUploadReqVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.file.GymFileDO;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.enums.file.GymFileMimeTypeEnum;
import cn.iocoder.yudao.module.gym.service.file.dto.GymFileContentDTO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.HashSet;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link GymFileServiceImpl} 的单元测试
 *
 * 覆盖：类别 / 大小 / 魔数与后缀不一致 / 类别白名单 / 门店数据范围越权 / 文件不存在
 *
 * @author JSS
 */
public class GymFileServiceImplTest extends BaseMockitoUnitTest {

    private static final Long USER_ID = 252L;
    private static final Long STORE_ID = 124L;
    private static final Long OTHER_STORE_ID = 999L;

    @InjectMocks
    private GymFileServiceImpl gymFileService;

    @Mock
    private GymFileMapper gymFileMapper;
    @Mock
    private cn.iocoder.yudao.module.gym.dal.mysql.contract.GymContractTemplateMapper gymContractTemplateMapper;
    @Mock
    private FileApi fileApi;
    @Mock
    private AdminUserApi adminUserApi;
    @Mock
    private DeptApi deptApi;
    @Mock
    private PermissionApi permissionApi;
    @Mock
    private GymMemberFileAccess memberFileAccess;

    private MockMultipartFile pngFile() {
        byte[] content = concat(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A},
                "filler".getBytes(StandardCharsets.US_ASCII));
        return new MockMultipartFile("file", "环境图.png", "image/png", content);
    }

    private GymFileUploadReqVO reqVO(MockMultipartFile file, String category, Long storeId) {
        GymFileUploadReqVO reqVO = new GymFileUploadReqVO();
        reqVO.setFile(file);
        reqVO.setCategory(category);
        reqVO.setStoreId(storeId);
        return reqVO;
    }

    /** mock 登录用户所属门店 */
    private void mockLoginUserStore(Long deptId) {
        when(adminUserApi.getUser(USER_ID)).thenReturn(new AdminUserRespDTO().setId(USER_ID).setDeptId(deptId));
    }

    /** mock 门店存在 + 当前登录人的数据范围 */
    private void mockStoreAndScope(Long storeId, boolean allData, Long... scopeDeptIds) {
        when(deptApi.getDept(storeId)).thenReturn(new DeptRespDTO().setId(storeId).setName("示范门店"));
        DeptDataPermissionRespDTO permission = new DeptDataPermissionRespDTO();
        permission.setAll(allData);
        permission.setDeptIds(new HashSet<>(Arrays.asList(scopeDeptIds)));
        when(permissionApi.getDeptDataPermission(USER_ID)).thenReturn(permission);
    }

    @Test
    public void testUploadFile_success() {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(USER_ID);
            mockLoginUserStore(STORE_ID);
            mockStoreAndScope(STORE_ID, true);
            // 存储名带唯一前缀（见 buildStorageName），后缀与原文件名保持一致
            when(fileApi.createFile(any(), argThat(name -> name.endsWith("-环境图.png")),
                    eq("gym/image"), eq("image/png")))
                    .thenReturn("http://file.local/1/gym/image/环境图.png");

            // 调用：不传 storeId，应落到上传人所属门店
            GymFileDO result = gymFileService.uploadFile(reqVO(pngFile(), "image", null));

            // 断言：字段回填 + 落库
            assertNotNull(result);
            assertEquals(STORE_ID, result.getStoreId());
            assertEquals("image", result.getCategory());
            assertEquals("环境图.png", result.getName()); // 业务侧仍存原始文件名
            assertEquals("image/png", result.getMimeType());
            assertEquals(USER_ID, result.getUploaderId());
            assertEquals(14L, result.getSize()); // 8 字节 PNG 魔数 + "filler"
            verify(gymFileMapper, times(1)).insert(any(GymFileDO.class));
        }
    }

    /**
     * 回归用例：同名文件两次上传，存储名必须不同
     * （否则 infra 的「日期/原文件名」路径会让后一次覆盖前一次）
     */
    @Test
    public void testUploadFile_sameFileNameTwice_storageNamesDiffer() {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(USER_ID);
            mockLoginUserStore(STORE_ID);
            mockStoreAndScope(STORE_ID, true);
            when(fileApi.createFile(any(), anyString(), eq("gym/image"), eq("image/png")))
                    .thenReturn("http://file.local/1/gym/image/a.png")
                    .thenReturn("http://file.local/1/gym/image/b.png");

            gymFileService.uploadFile(reqVO(pngFile(), "image", null));
            gymFileService.uploadFile(reqVO(pngFile(), "image", null));

            ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
            verify(fileApi, times(2)).createFile(any(), nameCaptor.capture(), eq("gym/image"), eq("image/png"));
            List<String> names = nameCaptor.getAllValues();
            assertNotEquals(names.get(0), names.get(1)); // 关键：两次存储名不同，路径不再冲突
            assertTrue(names.get(0).endsWith("-环境图.png") && names.get(1).endsWith("-环境图.png"));
        }
    }

    @Test
    public void testBuildStorageName_truncatesLongName() {
        String storageName = gymFileService.buildStorageName("会".repeat(300) + ".png", GymFileMimeTypeEnum.PNG);
        assertTrue(storageName.length() <= 180, "存储名长度应受限，实际：" + storageName.length());
        assertTrue(storageName.endsWith(".png"), "应保留扩展名");
    }

    @Test
    public void testUploadFile_categoryInvalid() {
        assertServiceException(() -> gymFileService.uploadFile(reqVO(pngFile(), "coupon", null)),
                FILE_CATEGORY_INVALID, "[approval, body_report, training_plan, contract, image, video]");
    }

    @Test
    public void testUploadFile_contentMismatchWithExtension() {
        // 后缀是 .png，但内容是 PDF -> 415 语义；该分支在门店校验之前返回，无需 mock 门店
        MockMultipartFile fakePng = new MockMultipartFile("file", "合同.png", "image/png",
                "%PDF-1.7 filler".getBytes(StandardCharsets.US_ASCII));
        assertServiceException(() -> gymFileService.uploadFile(reqVO(fakePng, "image", null)),
                FILE_TYPE_NOT_ALLOWED, "文件名后缀声明为 image/png，实际内容为 application/pdf");
    }

    @Test
    public void testUploadFile_mimeTypeNotAllowedByCategory() {
        // PDF 内容 + .pdf 后缀（声明一致），但类别为 image -> 该类别不允许 PDF
        MockMultipartFile pdf = new MockMultipartFile("file", "报告.pdf", "application/pdf",
                "%PDF-1.7 filler".getBytes(StandardCharsets.US_ASCII));
        assertServiceException(() -> gymFileService.uploadFile(reqVO(pdf, "image", null)),
                FILE_TYPE_NOT_ALLOWED, "类别 图片素材 不允许 application/pdf 类型");
    }

    @Test
    public void testUploadFile_sizeExceedLimit() {
        byte[] content = new byte[(int) (10 * 1024 * 1024 + 1)];
        System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 0, content, 0, 8);
        MockMultipartFile huge = new MockMultipartFile("file", "大图.png", "image/png", content);
        assertServiceException(() -> gymFileService.uploadFile(reqVO(huge, "image", null)),
                FILE_SIZE_EXCEED_LIMIT, "图片素材", 10L * 1024 * 1024, (long) content.length);
    }

    @Test
    public void testUploadFile_storeOutOfScope() {
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(USER_ID);
            // 显式指定门店，不会查询上传人部门；该门店不在数据范围内 -> 403 语义
            mockStoreAndScope(OTHER_STORE_ID, false, STORE_ID);
            assertServiceException(() -> gymFileService.uploadFile(reqVO(pngFile(), "image", OTHER_STORE_ID)),
                    FILE_STORE_NO_PERMISSION, OTHER_STORE_ID);
        }
    }

    @Test
    public void testGetFile_notExists() {
        when(gymFileMapper.selectById(1L)).thenReturn(null);
        assertServiceException(() -> gymFileService.getFile(1L), FILE_NOT_EXISTS);
    }

    @Test
    public void testGetFile_outOfScope() {
        GymFileDO file = GymFileDO.builder().id(1L).storeId(OTHER_STORE_ID).url("url").build();
        when(gymFileMapper.selectById(1L)).thenReturn(file);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(USER_ID);
            DeptDataPermissionRespDTO permission = new DeptDataPermissionRespDTO();
            permission.setAll(false);
            permission.setDeptIds(Collections.singleton(STORE_ID));
            when(permissionApi.getDeptDataPermission(USER_ID)).thenReturn(permission);
            assertServiceException(() -> gymFileService.getFile(1L), FILE_STORE_NO_PERMISSION, OTHER_STORE_ID);
        }
    }

    @Test
    public void testGetFileContent() {
        byte[] content = "hello-gym".getBytes(StandardCharsets.UTF_8);
        GymFileDO file = GymFileDO.builder().id(1L).storeId(STORE_ID).url("url")
                .name("报告.pdf").mimeType("application/pdf").build();
        when(gymFileMapper.selectById(1L)).thenReturn(file);
        when(fileApi.getFileContentByUrl("url")).thenReturn(content);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(USER_ID);
            DeptDataPermissionRespDTO permission = new DeptDataPermissionRespDTO();
            permission.setAll(true);
            when(permissionApi.getDeptDataPermission(USER_ID)).thenReturn(permission);

            GymFileContentDTO result = gymFileService.getFileContent(1L);
            assertArrayEquals(content, result.getContent());
            assertEquals("报告.pdf", result.getName());
            assertEquals("application/pdf", result.getMimeType());
        }
    }

    @Test
    public void testGetFileContent_contentMissing() {
        GymFileDO file = GymFileDO.builder().id(1L).storeId(STORE_ID).url("url").build();
        when(gymFileMapper.selectById(1L)).thenReturn(file);
        when(fileApi.getFileContentByUrl(anyString())).thenReturn(null);
        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(USER_ID);
            DeptDataPermissionRespDTO permission = new DeptDataPermissionRespDTO();
            permission.setAll(true);
            when(permissionApi.getDeptDataPermission(USER_ID)).thenReturn(permission);

            assertServiceException(() -> gymFileService.getFileContent(1L), FILE_CONTENT_NOT_EXISTS);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }


    @Test void publishedContractPdfAccessRequiresPublicationRoleAndNonemptyScope() {
        var file=GymFileDO.builder().id(81L).storeId(OTHER_STORE_ID).category("contract").mimeType("application/pdf").name("template.pdf").build();
        when(gymFileMapper.selectById(81L)).thenReturn(file);
        try(MockedStatic<SecurityFrameworkUtils> security=mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(USER_ID);
            var permission=new DeptDataPermissionRespDTO();permission.setAll(false);permission.setDeptIds(java.util.Set.of(STORE_ID));
            when(permissionApi.getDeptDataPermission(USER_ID)).thenReturn(permission);
            when(permissionApi.hasAnyPermissions(USER_ID,"gym:contracts:read","gym:contracts:templates")).thenReturn(true,true,false,true);
            when(gymContractTemplateMapper.isPublishedFile(81L)).thenReturn(false);
            assertServiceException(()->gymFileService.getFile(81L),FILE_STORE_NO_PERMISSION,OTHER_STORE_ID);
            when(gymContractTemplateMapper.isPublishedFile(81L)).thenReturn(true);
            assertEquals("template.pdf",gymFileService.getFile(81L).getName());
            assertServiceException(()->gymFileService.getFile(81L),FILE_STORE_NO_PERMISSION,OTHER_STORE_ID);
            permission.setDeptIds(Collections.emptySet());
            assertServiceException(()->gymFileService.getFile(81L),FILE_STORE_NO_PERMISSION,OTHER_STORE_ID);
        }
    }
    @Test
    void explicitGymStorageKeepsPrivateFileValidationAndAvoidsDefaultUpload() {
        org.springframework.test.util.ReflectionTestUtils.setField(gymFileService,"storageConfigId",4L);
        try(MockedStatic<SecurityFrameworkUtils> security=mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(USER_ID);
            mockStoreAndScope(STORE_ID,true);
            when(fileApi.createFile(any(),anyString(),eq("gym/image"),eq("image/png"),eq(4L))).thenReturn("http://db.local/gym/private.png");
            GymFileDO result=gymFileService.uploadFile(reqVO(pngFile(),"image",STORE_ID));
            assertEquals(STORE_ID,result.getStoreId());
            assertEquals("http://db.local/gym/private.png",result.getUrl());
            org.mockito.Mockito.verify(fileApi,org.mockito.Mockito.never()).createFile(any(),anyString(),anyString(),anyString());
            verify(gymFileMapper).insert(any(GymFileDO.class));
        }
    }


    @Test void responsibleAttachmentPolicyProtectsMetadataAndBytesOnEveryRead() {
        var file=GymFileDO.builder().id(91L).storeId(OTHER_STORE_ID).category("body_report").name("private.pdf").mimeType("application/pdf").url("private-url").build();
        when(gymFileMapper.selectById(91L)).thenReturn(file);
        when(fileApi.getFileContentByUrl("private-url")).thenReturn(new byte[]{1,2,3});
        when(memberFileAccess.canRead(file)).thenReturn(true,false);
        try(var security=mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(USER_ID);
            when(permissionApi.getDeptDataPermission(USER_ID)).thenReturn(new DeptDataPermissionRespDTO());
            assertArrayEquals(new byte[]{1,2,3},gymFileService.getFileContent(91L).getContent());
            assertServiceException(()->gymFileService.getFileContent(91L),FILE_STORE_NO_PERMISSION,OTHER_STORE_ID);
            verify(fileApi,times(1)).getFileContentByUrl("private-url");
        }
    }
    @Test void missingStorePermissionCannotDefaultToPrivateFileAccess() {
        var file=GymFileDO.builder().id(92L).storeId(OTHER_STORE_ID).category("body_report").build();when(gymFileMapper.selectById(92L)).thenReturn(file);
        try(var security=mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(USER_ID);
            assertServiceException(()->gymFileService.getFile(92L),FILE_STORE_NO_PERMISSION,OTHER_STORE_ID);
        }
    }

    @Test void selfScopeCannotReadUnboundLegacyFilesWithNoStore() {
        var file=GymFileDO.builder().id(93L).category("body_report").uploaderId(USER_ID).build();when(gymFileMapper.selectById(93L)).thenReturn(file);
        try(var security=mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(USER_ID);var scope=new DeptDataPermissionRespDTO();scope.setSelf(true);when(permissionApi.getDeptDataPermission(USER_ID)).thenReturn(scope);
            assertServiceException(()->gymFileService.getFile(93L),FILE_STORE_NO_PERMISSION,(Object)null);
        }
    }
    @Test void responsibleUploadStoresRealBytesButUnboundFileStillCannotBeRead() {
        try(var security=mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(USER_ID);
            mockStoreAndScope(STORE_ID,false);
            when(memberFileAccess.canUploadReport(STORE_ID,"body_report")).thenReturn(true);
            when(fileApi.createFile(any(),anyString(),eq("gym/body_report"),eq("image/png"))).thenReturn("private-report-url");
            var file=gymFileService.uploadFile(reqVO(pngFile(),"body_report",STORE_ID));
            assertEquals(USER_ID,file.getUploaderId());assertEquals(STORE_ID,file.getStoreId());
            verify(fileApi).createFile(eq(pngFile().getBytes()),anyString(),eq("gym/body_report"),eq("image/png"));
            file.setId(94L);when(gymFileMapper.selectById(94L)).thenReturn(file);
            assertServiceException(()->gymFileService.getFile(94L),FILE_STORE_NO_PERMISSION,STORE_ID);
            when(memberFileAccess.canUploadReport(STORE_ID,"body_report")).thenReturn(false);
            assertServiceException(()->gymFileService.uploadFile(reqVO(pngFile(),"body_report",STORE_ID)),FILE_STORE_NO_PERMISSION,STORE_ID);
            verify(gymFileMapper,times(1)).insert(any(GymFileDO.class));
        } catch(java.io.IOException ex) { throw new AssertionError(ex); }
    }

}
