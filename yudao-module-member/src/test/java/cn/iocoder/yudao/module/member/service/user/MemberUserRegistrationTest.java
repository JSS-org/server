package cn.iocoder.yudao.module.member.service.user;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.USER_MOBILE_USED;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.mq.producer.user.MemberUserProducer;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MemberUserRegistrationTest extends BaseMockitoUnitTest {
    @InjectMocks private MemberUserServiceImpl service;
    @Mock private MemberUserMapper mapper;
    @Mock private PasswordEncoder passwords;
    @Mock private MemberUserProducer producer;

    @Test
    void insertsOnlyANewMemberWithEncodedPassword() {
        when(passwords.encode("fixture-only")).thenReturn("fixture-encoded");
        doAnswer(
                        i -> {
                            ((MemberUserDO) i.getArgument(0)).setId(9007199254740993L);
                            return 1;
                        })
                .when(mapper)
                .insert(any(MemberUserDO.class));
        TransactionSynchronizationManager.initSynchronization();
        try {
            var user = service.registerUser("15601691300", "fixture-only", "新会员", "127.0.0.1", 10);
            assertEquals("fixture-encoded", user.getPassword());
            assertEquals("新会员", user.getNickname());
            assertEquals("15601691300", user.getMobile());
            verify(mapper).insert(user);
            verify(mapper, never()).updateById(any(MemberUserDO.class));
            verifyNoInteractions(producer);
            for (var callback : TransactionSynchronizationManager.getSynchronizations())
                callback.afterCommit();
            verify(producer).sendUserCreateMessage(user.getId());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void concurrentUniqueKeyRejectionNeverUpdatesAnotherAccount() {
        when(passwords.encode(any())).thenReturn("fixture-encoded");
        doThrow(new org.springframework.dao.DuplicateKeyException("fixture unique mobile"))
                .when(mapper)
                .insert(any(MemberUserDO.class));
        assertServiceException(
                () -> service.registerUser("15601691300", "fixture-only", "新会员", "127.0.0.1", 10),
                USER_MOBILE_USED,
                "15601691300");
        verify(mapper, never()).updateById(any(MemberUserDO.class));
        verifyNoInteractions(producer);
    }
}
