package cn.iocoder.yudao.module.infra.controller.admin.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.infra.service.file.FilePrivatePathPolicy;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileControllerPrivatePathTest extends BaseMockitoUnitTest {
    @InjectMocks private FileController controller;
    @InjectMocks private cn.iocoder.yudao.module.infra.controller.app.file.AppFileController appController;
    @Mock private FileService files;
    @Spy private FilePrivatePathPolicy policy=new FilePrivatePathPolicy("gym/");

    @Test void privateStorageUrlsNeverReadBytes() throws Exception {
        for(String path:new String[]{"gym/contract/private.pdf","gym%2fbody_report/private.pdf","gym/training_plan/private.pdf"}) {
            var request=new MockHttpServletRequest();request.setRequestURI("/admin-api/infra/file/4/get/"+path);
            var response=new MockHttpServletResponse();controller.getFileContent(request,response,4L);
            assertEquals(403,response.getStatus());assertEquals(0,response.getContentAsByteArray().length);
        }
        verifyNoInteractions(files);
    }
    @Test void decodedDirectoryTraversalCannotAliasAPrivateNamespace() {
        var request=new MockHttpServletRequest();request.setRequestURI("/admin-api/infra/file/4/get/public/%2e%2e/gym/contract/private.pdf");
        assertThrows(ServiceException.class,()->controller.getFileContent(request,new MockHttpServletResponse(),4L));
        verifyNoInteractions(files);
    }
    @Test void publicFilesStillUseTheirExistingDownloadPath() throws Exception {
        byte[] content={1,2,3};when(files.getFileContent(4L,"gym-public/avatar.png")).thenReturn(content);
        var request=new MockHttpServletRequest();request.setRequestURI("/admin-api/infra/file/4/get/gym-public/avatar.png");
        var response=new MockHttpServletResponse();controller.getFileContent(request,response,4L);
        assertEquals(200,response.getStatus());assertArrayEquals(content,response.getContentAsByteArray());
    }
    @Test void genericAdminAndAppUploadsCannotWriteIntoThePrivateNamespace() {
        var adminRequest=new cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadReqVO();adminRequest.setDirectory("gym");
        var appRequest=new cn.iocoder.yudao.module.infra.controller.app.file.vo.AppFileUploadReqVO();appRequest.setDirectory("gym/contract");
        assertThrows(ServiceException.class,()->controller.uploadFile(adminRequest));
        assertThrows(ServiceException.class,()->appController.uploadFile(appRequest));
        verifyNoInteractions(files);
        assertDoesNotThrow(()->policy.validatePublicUploadDirectory("gym-public"));
    }
}
