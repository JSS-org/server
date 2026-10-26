package cn.iocoder.yudao.module.gym.service.groupbuy;

import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.dal.mysql.groupbuy.GymCouponVerificationMapper;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.*;

@Service
@Slf4j
public class GymCouponVerificationWorker {
    @Resource private GymCouponVerificationMapper rows;
    @Resource private GymCouponVerificationTransactions transactions;
    @Resource private GymMemberCouponProvider provider;
    @Resource private GymMemberCouponConsumption consumption;

    @Transactional(propagation = Propagation.NEVER)
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
            if ("prepared".equals(row.getStage())) dispatch(id);
            else if ("confirmed".equals(row.getStage())) fulfill(id);
            else observe(id);
        } finally {
            SecurityContextHolder.setContext(saved);
        }
    }

    private int code(Exception e) {
        return e instanceof ServiceException business ? business.getCode() : 500;
    }

    private void dispatch(Long id) {
        GymCouponVerificationTransactions.Ticket ticket;
        try {
            ticket = transactions.inspect(id);
            if (ticket == null) return;
            var prepared =
                    provider.prepare(
                            ticket.tenant(),
                            ticket.store(),
                            ticket.code(),
                            ticket.context().mobile());
            if (!transactions.start(ticket, prepared)) return;
        } catch (Exception e) {
            transactions.reject(id, code(e), false);
            return;
        }
        GymMemberCouponConsumption.Confirmation proof;
        try {
            proof =
                    consumption.consume(
                            ticket.tenant(),
                            ticket.store(),
                            ticket.code(),
                            ticket.deal(),
                            ticket.group(),
                            ticket.requestId(),
                            ticket.scope());
        } catch (Exception e) {
            int code = code(e);
            if (Set.of(
                            GROUP_BUY_PROVIDER_UNCONFIGURED.getCode(),
                            GROUP_BUY_PROVIDER_SCENE_MISMATCH.getCode(),
                            GROUP_BUY_PROVIDER_MUTATIONS_DISABLED.getCode(),
                            GROUP_BUY_PROVIDER_REJECTED.getCode(),
                            COUPON_NOT_CONFIGURED.getCode(),
                            COUPON_PREVIEW_INVALID.getCode())
                    .contains(code)) {
                // Adapter's coupon errors occur before dispatch. Record them using the explicit
                // no-send code.
                transactions.reject(
                        id,
                        Set.of(COUPON_NOT_CONFIGURED.getCode(), COUPON_PREVIEW_INVALID.getCode())
                                        .contains(code)
                                ? GROUP_BUY_PROVIDER_UNCONFIGURED.getCode()
                                : code,
                        true);
            } else transactions.defer(id, code);
            return;
        }
        // Retry only persistence of an already obtained proof, never the remote mutation.
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                transactions.confirm(id, proof);
                fulfill(id);
                return;
            } catch (Exception e) {
                log.warn("核销确认保存待恢复，verificationId={}, code={}", id, code(e));
            }
        }
        transactions.defer(id, 500);
    }

    private void fulfill(Long id) {
        try {
            transactions.fulfill(id);
        } catch (Exception e) {
            log.warn("已核销待发卡，verificationId={}, code={}", id, code(e));
            transactions.defer(id, code(e));
        }
    }

    private void observe(Long id) {
        try {
            if (!transactions.claimObservation(id)) return;
            var ticket = transactions.observationTicket(id);
            if (ticket == null) return;
            var result =
                    consumption.observe(
                            ticket.tenant(),
                            ticket.store(),
                            ticket.code(),
                            ticket.deal(),
                            ticket.group(),
                            ticket.scope());
            transactions.observed(id, result);
        } catch (Exception e) {
            transactions.defer(id, code(e));
        }
    }
}
