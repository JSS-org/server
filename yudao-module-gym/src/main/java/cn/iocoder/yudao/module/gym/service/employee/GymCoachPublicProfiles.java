package cn.iocoder.yudao.module.gym.service.employee;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.admin.employee.vo.GymPublicCoachProfileVO;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.file.GymFileDO;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.*;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.service.common.*;

import com.baomidou.mybatisplus.core.conditions.query.*;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;

import java.util.*;

/** Publication is explicit. An internal employee record alone never makes a public coach. */
@Service
public class GymCoachPublicProfiles {
    @Resource private GymEmployeeMapper employees;
    @Resource private cn.iocoder.yudao.module.gym.service.file.GymWorkFileBindings workFiles;
    @Resource private GymEmployeeStoreMapper employments;
    @Resource private GymFileMapper files;
    @Resource private GymBusinessClock clock;
    @Resource private Validator validator;

    public String validateAndSerialize(
            GymPublicCoachProfileVO profile, Collection<Long> storeIds, Long employeeId) {
        var violations = validator.validate(profile);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        if (profile.getSpecialties().stream().map(String::strip).distinct().count()
                        != profile.getSpecialties().size()
                || new HashSet<>(profile.getQualificationFileIds()).size()
                        != profile.getQualificationFileIds().size())
            throw exception(MOBILE_REQUEST_INVALID);
        // Unpublishing must remain possible after a previously referenced file was deleted.
        if (Boolean.TRUE.equals(profile.getPublished())) {
            for (Long id : fileIds(profile)) {
                var file = files.selectById(id);
                if (!image(file)
                        || !storeIds.contains(file.getStoreId())
                        || !workFiles.coach(id, employeeId)) throw exception(MOBILE_FILE_NOT_OWNED);
            }
        }
        return JsonUtils.toJsonString(profile);
    }

    public GymPublicCoachProfileVO read(GymEmployeeDO employee) {
        if (employee.getCoachProfile() == null) return null;
        try {
            var profile =
                    JsonUtils.parseObject(
                            employee.getCoachProfile(), GymPublicCoachProfileVO.class);
            return profile != null && validator.validate(profile).isEmpty() ? profile : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public QueryWrapper<GymEmployeeDO> publicQuery(Long storeId) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        var today = clock.now().atZone(GymDateRange.ZONE).toLocalDate();
        String relation =
                "SELECT 1 FROM gym_employee_store es JOIN gym_store s ON s.id=es.store_id AND s.tenant_id={0} AND s.deleted=0 AND s.status='active' WHERE es.employee_id=gym_employee.id AND es.tenant_id={0} AND es.deleted=0 AND es.joined_on<={1}";
        var query =
                new QueryWrapper<GymEmployeeDO>()
                        .eq("coach_published", true)
                        .eq("status", "active")
                        .le("hired_on", today);
        if (storeId == null) query.exists(relation, tenant, today);
        else query.exists(relation + " AND es.store_id={2}", tenant, today, storeId);
        return query;
    }

    public GymFileDO publicImage(GymEmployeeDO employee, Long fileId) {
        if (fileId == null) return null;
        var file = files.selectById(fileId);
        return currentImage(employee, file) && workFiles.coach(fileId, employee.getId())
                ? file
                : null;
    }

    private boolean currentImage(GymEmployeeDO employee, GymFileDO file) {
        if (!image(file)) return false;
        return employments.selectCount(
                        new LambdaQueryWrapper<GymEmployeeStoreDO>()
                                .eq(GymEmployeeStoreDO::getEmployeeId, employee.getId())
                                .eq(GymEmployeeStoreDO::getStoreId, file.getStoreId())
                                .le(
                                        GymEmployeeStoreDO::getJoinedOn,
                                        clock.now().atZone(GymDateRange.ZONE).toLocalDate())
                                .exists(
                                        "SELECT 1 FROM gym_store s WHERE s.id=gym_employee_store.store_id AND s.tenant_id={0} AND s.deleted=0 AND s.status='active'",
                                        TenantContextHolder.getRequiredTenantId()))
                > 0;
    }

    public boolean references(GymFileDO file) {
        if (!image(file)) return false;
        // LIKE narrows candidates only; parsed numeric IDs and current employment decide access.
        for (var employee :
                employees.selectList(
                        publicQuery(null).like("coach_profile", file.getId().toString()))) {
            var profile = read(employee);
            if (profile != null
                    && Boolean.TRUE.equals(profile.getPublished())
                    && fileIds(profile).contains(file.getId())
                    && currentImage(employee, file)
                    && workFiles.coach(file.getId(), employee.getId())) return true;
        }
        return false;
    }

    private static Set<Long> fileIds(GymPublicCoachProfileVO profile) {
        var ids = new HashSet<>(profile.getQualificationFileIds());
        if (profile.getAvatarFileId() != null) ids.add(profile.getAvatarFileId());
        return ids;
    }

    private static boolean image(GymFileDO file) {
        return file != null
                && "image".equals(file.getCategory())
                && Set.of("image/png", "image/jpeg", "image/gif", "image/webp")
                        .contains(Objects.toString(file.getMimeType(), ""));
    }
}
