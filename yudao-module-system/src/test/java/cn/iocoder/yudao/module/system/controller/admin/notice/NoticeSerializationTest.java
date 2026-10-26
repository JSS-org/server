package cn.iocoder.yudao.module.system.controller.admin.notice;

import static org.junit.jupiter.api.Assertions.*;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.NoticeRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.NoticeDO;

import org.junit.jupiter.api.Test;

class NoticeSerializationTest {
    @Test
    void administrativeRemarkIsReturnedByManagementVoButExcludedFromEntityPushPayload() {
        var notice =
                new NoticeDO()
                        .setId(12L)
                        .setTitle("Title")
                        .setContent("<p>Public content</p>")
                        .setType(1)
                        .setStatus(0)
                        .setRemark("Internal remark");
        String payload = JsonUtils.toJsonString(notice);
        assertFalse(payload.contains("remark"));
        assertTrue(payload.contains("Public content"));
        var management = BeanUtils.toBean(notice, NoticeRespVO.class);
        assertEquals("Internal remark", management.getRemark());
        assertTrue(JsonUtils.toJsonString(management).contains("Internal remark"));
    }
}
