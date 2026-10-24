package cn.iocoder.yudao.module.gym.service.mobile;

import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileWorkContextVO;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePublicVO.StoreBrief;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.GymEmployeeDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.GymEmployeeStoreDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.store.GymStoreDO;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.GymEmployeeMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.GymEmployeeStoreMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.GymBusinessClock;
import cn.iocoder.yudao.module.gym.service.common.GymDataScope;
import cn.iocoder.yudao.module.gym.service.common.GymDateRange;
import cn.iocoder.yudao.module.gym.service.member.GymMemberCoachService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

/** 门店入口集合不等于整店操作权限，所有业务请求继续各自检查当前权限和数据范围。 */
@Service
public class GymMobileWorkContextService {
    @Resource private GymMobileWorkAccess access;
    @Resource private PermissionApi permissions;
    @Resource private GymEmployeeMapper employees;
    @Resource private GymEmployeeStoreMapper employments;
    @Resource private GymStoreMapper stores;
    @Resource private GymDataScope scope;
    @Resource private GymMemberCoachService memberCoaches;
    @Resource private GymBusinessClock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GymMobileWorkContextVO get() {
        Long userId = access.requireAdmin();
        var authorization = permissions.getUserPermissionInfo(userId);
        var today = clock.now().atZone(GymDateRange.ZONE).toLocalDate();
        var employeeRows =
                employees.selectList(
                        new LambdaQueryWrapper<GymEmployeeDO>()
                                .eq(GymEmployeeDO::getUserId, userId)
                                .eq(GymEmployeeDO::getStatus, "active")
                                .le(GymEmployeeDO::getHiredOn, today));
        Long employeeId = employeeRows.size() == 1 ? employeeRows.getFirst().getId() : null;
        var broadStores = scope.readableStoreIds();
        var candidates = new HashSet<Long>();
        if (broadStores != null) {
            candidates.addAll(broadStores);
            memberCoaches.selfResponsibilities().forEach(row -> candidates.add(row.getStoreId()));
            if (employeeId != null)
                employments
                        .selectList(
                                new LambdaQueryWrapper<GymEmployeeStoreDO>()
                                        .eq(GymEmployeeStoreDO::getEmployeeId, employeeId)
                                        .le(GymEmployeeStoreDO::getJoinedOn, today))
                        .forEach(row -> candidates.add(row.getStoreId()));
        }
        List<StoreBrief> visibleStores =
                broadStores != null && candidates.isEmpty()
                        ? List.of()
                        : stores
                                .selectList(
                                        new LambdaQueryWrapper<GymStoreDO>()
                                                .eq(GymStoreDO::getStatus, "active")
                                                .in(
                                                        broadStores != null,
                                                        GymStoreDO::getId,
                                                        candidates)
                                                .orderByAsc(GymStoreDO::getId))
                                .stream()
                                .map(
                                        row ->
                                                new StoreBrief(
                                                        row.getId().toString(),
                                                        row.getName(),
                                                        null,
                                                        row.getAddress(),
                                                        null))
                                .toList();
        return new GymMobileWorkContextVO(
                employeeId == null ? null : employeeId.toString(),
                authorization.roles(),
                authorization.permissions().stream()
                        .filter(code -> code.startsWith("gym:"))
                        .toList(),
                visibleStores);
    }
}
