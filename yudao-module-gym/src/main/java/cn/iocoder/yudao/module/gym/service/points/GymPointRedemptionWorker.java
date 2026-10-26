package cn.iocoder.yudao.module.gym.service.points;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.dal.mysql.points.GymPointRedemptionMapper;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class GymPointRedemptionWorker {
    @Resource private GymPointRedemptionMapper rows;
    @Resource private GymPointRedemptionTransactions transactions;

    /** Internal only: actor comes from the persisted tenant-scoped request, never a client actor ID. */
    public void process(Long id) {
        var row = rows.selectById(id);
        if (row == null || !"processing".equals(row.getStatus())) return;
        var saved = SecurityContextHolder.getContext();
        try {
            var login =
                    new LoginUser()
                            .setId(row.getUserId())
                            .setUserType(1)
                            .setTenantId(TenantContextHolder.getRequiredTenantId());
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(login, null, List.of()));
            SecurityContextHolder.setContext(context);
            try {
                transactions.fulfill(id);
            } catch (Exception e) {
                Integer code = e instanceof ServiceException business ? business.getCode() : 500;
                log.warn("积分兑换发卡失败，将原子返还积分及库存，redemptionId={}, code={}", id, code);
                transactions.fail(id, code);
            }
        } finally {
            SecurityContextHolder.setContext(saved);
        }
    }
}
