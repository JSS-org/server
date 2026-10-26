package cn.iocoder.yudao.module.gym.service.mobile;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointsVO.*;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePointsVO.Record;
import cn.iocoder.yudao.module.gym.dal.dataobject.points.*;
import cn.iocoder.yudao.module.gym.dal.mysql.points.*;
import cn.iocoder.yudao.module.gym.service.common.GymDateRange;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class GymMobilePointsService {
    @Resource private GymMobileIdentityService identities;
    @Resource private GymPointAccountMapper accounts;
    @Resource private GymPointRecordMapper records;
    @Resource private Validator validator;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Balance balance() {
        Long member = identities.requireMemberId();
        var row = accounts.selectOne(GymPointAccountDO::getMemberId, member);
        return row == null
                ? new Balance(0, 0, 0)
                : new Balance(row.getBalance(), row.getTotalEarned(), row.getTotalSpent());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Record> records(Query req) {
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        Long member = identities.requireMemberId();
        var page =
                records.selectPage(
                        req,
                        new LambdaQueryWrapper<GymPointRecordDO>()
                                .eq(GymPointRecordDO::getMemberId, member)
                                .eq(req.getType() != null, GymPointRecordDO::getType, req.getType())
                                .orderByDesc(GymPointRecordDO::getId));
        return new PageResult<>(
                page.getList().stream()
                        .map(
                                row ->
                                        new Record(
                                                row.getId().toString(),
                                                row.getDelta(),
                                                row.getBalanceAfter(),
                                                row.getReason(),
                                                row.getPostedAt()
                                                        .atZone(GymDateRange.ZONE)
                                                        .toOffsetDateTime()
                                                        .toString()))
                        .toList(),
                page.getTotal());
    }
}
