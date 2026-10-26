package cn.iocoder.yudao.module.gym.service.points;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.module.gym.dal.dataobject.points.*;
import cn.iocoder.yudao.module.gym.dal.mysql.member.GymMemberMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.points.*;
import cn.iocoder.yudao.module.gym.service.common.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;
import jakarta.validation.constraints.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Internal transactional ledger only. No HTTP credit/debit endpoint or automatic reward producer. */
@Service
public class GymPointLedgerService {
    public static final long MAX_POINTS = 9_007_199_254_740_991L;

    public record Posting(
            @NotNull @Positive Long memberId,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9:_.-]{0,127}") String businessKey,
            @NotNull @Pattern(regexp = "earn|spend|refund") String type,
            @Min(-9_007_199_254_740_991L) @Max(9_007_199_254_740_991L) long delta,
            @NotBlank @Size(max = 200) String reason,
            @Positive Long originalRecordId) {}

    public record Posted(Long id, long balanceAfter) {}

    @Resource private GymPointAccountMapper accounts;
    @Resource private GymPointRecordMapper records;
    @Resource private GymMemberMapper members;
    @Resource private GymBusinessMutex mutex;
    @Resource private GymBusinessClock clock;
    @Resource private Validator validator;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Posted append(Posting req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        if (req.delta() == 0
                || "earn".equals(req.type()) && req.delta() < 0
                || "spend".equals(req.type()) && req.delta() > 0
                || "refund".equals(req.type()) != (req.originalRecordId() != null))
            throw exception(POINT_ENTRY_INVALID);
        String reason = req.reason().trim();
        if (reason.isBlank()) throw exception(POINT_ENTRY_INVALID);
        mutex.lockStoreEntitlements();
        var member = members.selectForUpdate(req.memberId());
        if (member == null || "spend".equals(req.type()) && !"active".equals(member.getStatus()))
            throw exception(POINT_ENTRY_INVALID);
        var old =
                records.selectOne(
                        new LambdaQueryWrapper<GymPointRecordDO>()
                                .eq(GymPointRecordDO::getBusinessKey, req.businessKey())
                                .last("FOR UPDATE"));
        if (old != null) {
            if (!req.memberId().equals(old.getMemberId())
                    || !req.type().equals(old.getType())
                    || req.delta() != old.getDelta()
                    || !reason.equals(old.getReason())
                    || !Objects.equals(req.originalRecordId(), old.getOriginalRecordId()))
                throw exception(POINT_EVENT_CONFLICT);
            return new Posted(old.getId(), old.getBalanceAfter());
        }
        var account =
                accounts.selectOne(
                        new LambdaQueryWrapper<GymPointAccountDO>()
                                .eq(GymPointAccountDO::getMemberId, req.memberId())
                                .last("FOR UPDATE"));
        if (account == null) {
            account = new GymPointAccountDO();
            account.setMemberId(req.memberId());
            account.setBalance(0L);
            account.setTotalEarned(0L);
            account.setTotalSpent(0L);
            account.setRefundReserve(0L);
            accounts.insert(account);
        }
        if ("refund".equals(req.type())) {
            var original =
                    records.selectOne(
                            new LambdaQueryWrapper<GymPointRecordDO>()
                                    .eq(GymPointRecordDO::getId, req.originalRecordId())
                                    .last("FOR UPDATE"));
            if (original == null
                    || !req.memberId().equals(original.getMemberId())
                    || !account.getId().equals(original.getAccountId())
                    || "refund".equals(original.getType())
                    || Long.signum(req.delta()) == Long.signum(original.getDelta()))
                throw exception(POINT_ENTRY_INVALID);
            long reversed = 0;
            for (var refund :
                    records.selectList(
                            new LambdaQueryWrapper<GymPointRecordDO>()
                                    .eq(GymPointRecordDO::getOriginalRecordId, original.getId())
                                    .last("FOR UPDATE")))
                reversed = Math.addExact(reversed, Math.abs(refund.getDelta()));
            if (Math.addExact(reversed, Math.abs(req.delta())) > Math.abs(original.getDelta()))
                throw exception(POINT_ENTRY_INVALID);
        }
        long balance = Math.addExact(account.getBalance(), req.delta());
        if ("spend".equals(req.type()) && balance < 0) throw exception(POINT_BALANCE_INSUFFICIENT);
        long earned = account.getTotalEarned(), spent = account.getTotalSpent();
        if (req.delta() > 0) earned = Math.addExact(earned, req.delta());
        else spent = Math.addExact(spent, -req.delta());
        if (earned + account.getRefundReserve() > MAX_POINTS || spent > MAX_POINTS)
            throw exception(POINT_ENTRY_INVALID);
        var row = new GymPointRecordDO();
        row.setAccountId(account.getId());
        row.setMemberId(req.memberId());
        row.setBusinessKey(req.businessKey());
        row.setType(req.type());
        row.setDelta(req.delta());
        row.setBalanceAfter(balance);
        row.setReason(reason);
        row.setOriginalRecordId(req.originalRecordId());
        row.setPostedAt(clock.now().truncatedTo(ChronoUnit.MICROS));
        records.insert(row);
        account.setBalance(balance);
        account.setTotalEarned(earned);
        account.setTotalSpent(spent);
        accounts.updateById(account);
        return new Posted(row.getId(), balance);
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void reserveRefund(Long member, long amount) {
        if (amount <= 0) throw exception(POINT_ENTRY_INVALID);
        adjustRefundReserve(member, amount);
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void releaseRefund(Long member, long amount) {
        if (amount <= 0) throw exception(POINT_ENTRY_INVALID);
        adjustRefundReserve(member, -amount);
    }

    private void adjustRefundReserve(Long member, long change) {
        if (change == 0 || change > MAX_POINTS || change < -MAX_POINTS)
            throw exception(POINT_ENTRY_INVALID);
        mutex.lockStoreEntitlements();
        var account =
                accounts.selectOne(
                        new LambdaQueryWrapper<GymPointAccountDO>()
                                .eq(GymPointAccountDO::getMemberId, member)
                                .last("FOR UPDATE"));
        if (account == null) throw exception(POINT_ENTRY_INVALID);
        long reserved = account.getRefundReserve() + change;
        if (reserved < 0 || account.getTotalEarned() + reserved > MAX_POINTS)
            throw exception(POINT_ENTRY_INVALID);
        account.setRefundReserve(reserved);
        accounts.updateById(account);
    }
}
