package cn.iocoder.yudao.module.mes.service.wm.miscissue;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.mes.enums.ErrorCodeConstants.WM_MISC_ISSUE_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.mes.dal.dataobject.wm.miscissue.*;
import cn.iocoder.yudao.module.mes.dal.mysql.wm.miscissue.MesWmMiscIssueMapper;
import cn.iocoder.yudao.module.mes.service.wm.transaction.MesWmTransactionService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class MesWmMiscIssueQuantityTest extends BaseMockitoUnitTest {
    private static final Long ISSUE_ID = 9007199254740993L;
    private static final Long LINE_ID = 9007199254740995L;
    @InjectMocks private MesWmMiscIssueServiceImpl service;
    @Mock private MesWmMiscIssueMapper mapper;
    @Mock private MesWmMiscIssueLineService lines;
    @Mock private MesWmMiscIssueDetailService details;
    @Mock private MesWmTransactionService transactions;

    private MesWmMiscIssueLineDO line(Long id, String quantity) {
        return new MesWmMiscIssueLineDO().setId(id).setIssueId(ISSUE_ID)
                .setQuantity(quantity == null ? null : new BigDecimal(quantity));
    }

    private MesWmMiscIssueDetailDO detail(Long lineId, String quantity) {
        return new MesWmMiscIssueDetailDO().setIssueId(ISSUE_ID).setLineId(lineId)
                .setQuantity(quantity == null ? null : new BigDecimal(quantity));
    }

    private void existingIssue() {
        when(mapper.selectById(ISSUE_ID)).thenReturn(new MesWmMiscIssueDO().setId(ISSUE_ID));
    }

    @Test void sumsSplitAllocationsExactlyWithoutChangingStock() {
        existingIssue();
        when(lines.getMiscIssueLineListByIssueId(ISSUE_ID)).thenReturn(List.of(line(LINE_ID, "0.3000")));
        when(details.getMiscIssueDetailListByIssueId(ISSUE_ID))
                .thenReturn(List.of(detail(LINE_ID, "0.1"), detail(LINE_ID, "0.20")));
        assertTrue(service.checkMiscIssueQuantity(ISSUE_ID));
        verifyNoInteractions(transactions);
        verify(mapper, never()).updateById(any(MesWmMiscIssueDO.class));
    }

    @Test void equalDocumentTotalsCannotHideDifferentLineAllocations() {
        existingIssue();
        when(lines.getMiscIssueLineListByIssueId(ISSUE_ID))
                .thenReturn(List.of(line(LINE_ID, "1"), line(LINE_ID + 2, "2")));
        when(details.getMiscIssueDetailListByIssueId(ISSUE_ID))
                .thenReturn(List.of(detail(LINE_ID, "2"), detail(LINE_ID + 2, "1")));
        assertFalse(service.checkMiscIssueQuantity(ISSUE_ID));
    }

    @Test void missingOrOrphanedAllocationsDoNotPass() {
        existingIssue();
        when(lines.getMiscIssueLineListByIssueId(ISSUE_ID)).thenReturn(List.of(line(LINE_ID, "1")));
        when(details.getMiscIssueDetailListByIssueId(ISSUE_ID)).thenReturn(List.of());
        assertFalse(service.checkMiscIssueQuantity(ISSUE_ID));
        when(details.getMiscIssueDetailListByIssueId(ISSUE_ID))
                .thenReturn(List.of(detail(LINE_ID, "1"), detail(LINE_ID + 2, "1")));
        assertFalse(service.checkMiscIssueQuantity(ISSUE_ID));
    }

    @Test void invalidQuantitiesDoNotCancelEachOtherOut() {
        existingIssue();
        when(lines.getMiscIssueLineListByIssueId(ISSUE_ID)).thenReturn(List.of(line(LINE_ID, "1")));
        when(details.getMiscIssueDetailListByIssueId(ISSUE_ID))
                .thenReturn(List.of(detail(LINE_ID, "2"), detail(LINE_ID, "-1")));
        assertFalse(service.checkMiscIssueQuantity(ISSUE_ID));
        when(details.getMiscIssueDetailListByIssueId(ISSUE_ID)).thenReturn(List.of(detail(LINE_ID, null)));
        assertFalse(service.checkMiscIssueQuantity(ISSUE_ID));
        when(lines.getMiscIssueLineListByIssueId(ISSUE_ID)).thenReturn(List.of(line(LINE_ID, null)));
        assertFalse(service.checkMiscIssueQuantity(ISSUE_ID));
    }

    @Test void emptyOrMissingDocumentCannotBeReportedAsValid() {
        assertServiceException(() -> service.checkMiscIssueQuantity(ISSUE_ID), WM_MISC_ISSUE_NOT_EXISTS);
        verifyNoInteractions(lines, details, transactions);
        existingIssue();
        when(lines.getMiscIssueLineListByIssueId(ISSUE_ID)).thenReturn(List.of());
        assertFalse(service.checkMiscIssueQuantity(ISSUE_ID));
    }
}
