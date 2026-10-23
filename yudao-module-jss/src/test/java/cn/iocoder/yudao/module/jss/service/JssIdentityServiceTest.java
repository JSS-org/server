package cn.iocoder.yudao.module.jss.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.jss.dal.dataobject.JssIdentityAuditDO;
import cn.iocoder.yudao.module.jss.dal.dataobject.JssUserIdentityDO;
import cn.iocoder.yudao.module.jss.dal.mysql.JssIdentityAuditMapper;
import cn.iocoder.yudao.module.jss.dal.mysql.JssUserIdentityMapper;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JssIdentityServiceTest {
    @InjectMocks private JssIdentityService service;
    @Mock private JssUserIdentityMapper identities;
    @Mock private JssIdentityAuditMapper audits;
    @Mock private MemberUserApi members;
    @Mock private AdminUserApi admins;

    @BeforeEach
    void setup() {
        var member = new MemberUserRespDTO();
        member.setId(10L);
        member.setStatus(0);
        var admin = new AdminUserRespDTO();
        admin.setId(20L);
        admin.setStatus(0);
        when(members.getUser(10L)).thenReturn(member);
        when(admins.getUser(20L)).thenReturn(admin);
        doAnswer(
                        call -> {
                            ((JssUserIdentityDO) call.getArgument(0)).setId(100L);
                            return 1;
                        })
                .when(identities)
                .insert(any(JssUserIdentityDO.class));
    }

    private JssUserIdentityDO existing(int status) {
        var row = new JssUserIdentityDO();
        row.setId(100L);
        row.setMemberUserId(10L);
        row.setAdminUserId(19L);
        row.setStatus(status);
        row.setBindingVersion(2L);
        when(identities.findByMember(10L)).thenReturn(row);
        when(identities.selectForUpdate(100L)).thenReturn(row);
        return row;
    }

    @Test
    void reviewedBindingCreatesAuditAndVersion() {
        assertEquals(100L, service.bind(10L, 20L, "双方证件核验"));
        verify(audits)
                .insert(
                        argThat(
                                (JssIdentityAuditDO audit) ->
                                        "BIND".equals(audit.getAction())
                                                && audit.getAdminUserId().equals(20L)
                                                && audit.getBindingVersion() == 1L));
    }

    @Test
    void missingOrCrossTenantAccountNeverCreatesBinding() {
        when(admins.getUser(20L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.bind(10L, 20L, "review"));
        verify(identities, never()).insert(any(JssUserIdentityDO.class));
    }

    @Test
    void databaseUniqueConstraintHandlesConcurrentReview() {
        doThrow(new DuplicateKeyException("duplicate"))
                .when(identities)
                .insert(any(JssUserIdentityDO.class));
        assertEquals(
                400,
                assertThrows(ServiceException.class, () -> service.bind(10L, 20L, "review"))
                        .getCode());
        verifyNoInteractions(audits);
    }

    @Test
    void activeBindingCannotBeSilentlyOverwritten() {
        existing(0);
        assertThrows(ServiceException.class, () -> service.bind(10L, 20L, "review"));
        verify(identities, never()).updateById(any(JssUserIdentityDO.class));
    }

    @Test
    void revokedBindingCanBeExplicitlyReassignedWithVersionFence() {
        var row = existing(1);
        service.bind(10L, 20L, "重新核验双方身份");
        assertEquals(20L, row.getAdminUserId());
        assertEquals(3L, row.getBindingVersion());
        verify(audits)
                .insert(
                        argThat(
                                (JssIdentityAuditDO audit) ->
                                        audit.getBindingVersion() == 3L
                                                && audit.getAdminUserId().equals(20L)));
    }

    @Test
    void revocationIsVersionedAndIdempotent() {
        var row = existing(0);
        service.revoke(100L, "离职撤权");
        verify(identities).revoke(eq(100L), anyString());
        verify(audits)
                .insert(
                        argThat(
                                (JssIdentityAuditDO audit) ->
                                        "REVOKE".equals(audit.getAction())
                                                && audit.getBindingVersion() == 3L));
        row.setStatus(1);
        service.revoke(100L, "重复请求");
        verify(identities, times(1)).revoke(eq(100L), anyString());
    }
}
