package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.gym.controller.admin.employee.vo.GymPublicCoachProfileVO;
import cn.iocoder.yudao.module.gym.controller.admin.mobile.vo.GymMobileCoachProfilePatch;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCoachVO.Coach;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobilePublicVO.Media;
import cn.iocoder.yudao.module.gym.dal.dataobject.course.GymCourseProductDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.*;
import cn.iocoder.yudao.module.gym.dal.mysql.course.GymCourseProductMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.*;
import cn.iocoder.yudao.module.gym.framework.file.core.utils.GymFileTypeUtils;
import cn.iocoder.yudao.module.gym.service.common.*;
import cn.iocoder.yudao.module.gym.service.employee.GymCoachPublicProfiles;
import cn.iocoder.yudao.module.gym.service.file.dto.GymFileContentDTO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.*;

@Service
public class GymMobileCoachProfileService {
    @Resource private GymMobileWorkAccess access;
    @Resource private GymEmployeeMapper employees;
    @Resource private GymEmployeeStoreMapper employments;
    @Resource private GymCourseProductMapper products;
    @Resource private GymCoachPublicProfiles profiles;
    @Resource private GymBusinessClock clock;
    @Resource private WebProperties web;
    @Resource private Validator validator;
    @Resource private FileApi storage;

    private record Owner(GymEmployeeDO employee, List<Long> stores) {}

    private Owner owner(boolean lock) {
        Long employeeId = access.employee();
        var employee =
                lock ? employees.selectForUpdate(employeeId) : employees.selectById(employeeId);
        Long userId = access.requireAdmin();
        var today = clock.now().atZone(GymDateRange.ZONE).toLocalDate();
        if (employee == null
                || !Objects.equals(employee.getUserId(), userId)
                || !"active".equals(employee.getStatus())
                || employee.getHiredOn().isAfter(today)) throw exception(MOBILE_EMPLOYEE_REQUIRED);
        var stores =
                employments
                        .selectList(
                                new LambdaQueryWrapper<GymEmployeeStoreDO>()
                                        .eq(GymEmployeeStoreDO::getEmployeeId, employeeId)
                                        .le(GymEmployeeStoreDO::getJoinedOn, today)
                                        .exists(
                                                "SELECT 1 FROM gym_store s WHERE s.id=gym_employee_store.store_id AND s.tenant_id={0} AND s.deleted=0 AND s.status='active'",
                                                TenantContextHolder.getRequiredTenantId()))
                        .stream()
                        .map(GymEmployeeStoreDO::getStoreId)
                        .distinct()
                        .toList();
        if (stores.isEmpty()) throw exception(MOBILE_COACH_NOT_AVAILABLE);
        // Existing coach profile or an active private product is explicit coach evidence.
        if (employee.getCoachProfile() == null
                && products.selectCount(
                                new LambdaQueryWrapper<GymCourseProductDO>()
                                        .eq(GymCourseProductDO::getCoachId, employeeId)
                                        .eq(GymCourseProductDO::getStatus, "active")
                                        .eq(GymCourseProductDO::getKind, "private")
                                        .in(GymCourseProductDO::getStoreId, stores))
                        == 0) throw exception(MOBILE_COACH_NOT_AVAILABLE);
        return new Owner(employee, stores);
    }

    private GymPublicCoachProfileVO profile(GymEmployeeDO employee) {
        var result = profiles.read(employee);
        if (result == null) {
            result = new GymPublicCoachProfileVO();
            result.setIntroduction("");
            result.setSpecialties(List.of());
            result.setQualificationFileIds(List.of());
        }
        result.setPublished(Boolean.TRUE.equals(employee.getCoachPublished()));
        return result;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Coach get() {
        var owner = owner(false);
        return response(owner.employee(), profile(owner.employee()));
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Coach patch(GymMobileCoachProfilePatch req) {
        if (req == null) throw exception(MOBILE_REQUEST_INVALID);
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        var owner = owner(true);
        var employee = owner.employee();
        var profile = profile(employee);
        if (!req.isAvatarPresent()
                && !req.isIntroductionPresent()
                && !req.isSpecialtiesPresent()
                && !req.isQualificationsPresent()) return response(employee, profile);
        var previousFiles = ids(profile);
        if (req.isAvatarPresent()) profile.setAvatarFileId(req.getAvatarFileId());
        if (req.isIntroductionPresent()) profile.setIntroduction(req.getIntroduction());
        if (req.isSpecialtiesPresent()) profile.setSpecialties(req.getSpecialties());
        if (req.isQualificationsPresent())
            profile.setQualificationFileIds(req.getQualificationFileIds());
        String json = profiles.validateAndSerialize(profile, owner.stores());
        for (Long id : ids(profile)) {
            var file = profiles.publicImage(employee, id);
            if (file == null
                    || !previousFiles.contains(id)
                            && !Objects.equals(
                                    file.getUploaderId(), SecurityFrameworkUtils.getLoginUserId()))
                throw exception(MOBILE_FILE_NOT_OWNED);
        }
        // Merge under the administrator's employee row lock; write only the profile and audit
        // fields.
        var update = new GymEmployeeDO();
        update.setId(employee.getId());
        update.setCoachProfile(json);
        employees.updateById(update);
        return response(employee, profile);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GymFileContentDTO content(Long fileId) {
        if (fileId == null || fileId <= 0) throw exception(MOBILE_REQUEST_INVALID);
        var owner = owner(false);
        if (!ids(profile(owner.employee())).contains(fileId))
            throw exception(MOBILE_FILE_NOT_OWNED);
        var file = profiles.publicImage(owner.employee(), fileId);
        if (file == null) throw exception(FILE_NOT_EXISTS);
        byte[] bytes = storage.getFileContentByUrl(file.getUrl());
        if (bytes == null || bytes.length == 0) throw exception(FILE_CONTENT_NOT_EXISTS);
        var detected = GymFileTypeUtils.detect(bytes);
        if (detected == null || !detected.getMimeType().equals(file.getMimeType()))
            throw exception(FILE_NOT_EXISTS);
        return new GymFileContentDTO(file.getName(), file.getMimeType(), bytes);
    }

    private Coach response(GymEmployeeDO employee, GymPublicCoachProfileVO profile) {
        return new Coach(
                employee.getId().toString(),
                employee.getName(),
                media(employee, profile.getAvatarFileId()),
                profile.getIntroduction(),
                profile.getSpecialties(),
                profile.getQualificationFileIds().stream()
                        .map(id -> media(employee, id))
                        .filter(Objects::nonNull)
                        .toList());
    }

    private Media media(GymEmployeeDO employee, Long id) {
        var file = profiles.publicImage(employee, id);
        return file == null
                ? null
                : new Media(
                        id.toString(),
                        "image",
                        web.getAdminApi().getPrefix()
                                + "/gym/mobile/me/coach-profile/files/"
                                + id
                                + "/content",
                        null,
                        null);
    }

    private static Set<Long> ids(GymPublicCoachProfileVO profile) {
        var result = new HashSet<>(profile.getQualificationFileIds());
        if (profile.getAvatarFileId() != null) result.add(profile.getAvatarFileId());
        return result;
    }
}
