package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.GymEmployeeDO;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.GymEmployeeMapper;
import cn.iocoder.yudao.module.gym.service.common.GymBusinessClock;
import cn.iocoder.yudao.module.gym.service.common.GymDateRange;
import cn.iocoder.yudao.module.gym.service.member.GymMemberService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class GymMobileWorkAccess {
    @Resource private AdminUserApi users;
    @Resource private GymMemberService members;
    @Resource private GymEmployeeMapper employees;
    @Resource private GymBusinessClock clock;

    public Long requireAdmin() {
        var login = SecurityFrameworkUtils.getLoginUser();
        if (login == null
                || !Integer.valueOf(2).equals(login.getUserType())
                || !Objects.equals(login.getTenantId(), TenantContextHolder.getRequiredTenantId()))
            throw exception(MOBILE_IDENTITY_INVALID);
        var user = users.getUser(login.getId());
        if (user == null || !Integer.valueOf(0).equals(user.getStatus()))
            throw exception(MOBILE_IDENTITY_INVALID);
        return login.getId();
    }

    /** Called before idempotency lookup too: old responses do not bypass current responsibility. */
    public void member(Long memberId, Long storeId) {
        requireAdmin();
        members.requireMember(memberId, false);
        members.checkMemberStore(memberId, storeId);
    }

    public Long employee() {
        Long userId = requireAdmin();
        var rows =
                employees.selectList(
                        new LambdaQueryWrapper<GymEmployeeDO>()
                                .eq(GymEmployeeDO::getUserId, userId)
                                .eq(GymEmployeeDO::getStatus, "active")
                                .le(
                                        GymEmployeeDO::getHiredOn,
                                        clock.now().atZone(GymDateRange.ZONE).toLocalDate()));
        if (rows.size() != 1) throw exception(MOBILE_EMPLOYEE_REQUIRED);
        return rows.getFirst().getId();
    }
}
