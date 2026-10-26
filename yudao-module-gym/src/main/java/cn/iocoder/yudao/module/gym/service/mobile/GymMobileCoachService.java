package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileCoachVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.employee.GymEmployeeDO;
import cn.iocoder.yudao.module.gym.dal.mysql.employee.GymEmployeeMapper;
import cn.iocoder.yudao.module.gym.service.employee.GymCoachPublicProfiles;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.*;

@Service
public class GymMobileCoachService {
    @Resource private GymEmployeeMapper employees;
    @Resource private GymCoachPublicProfiles profiles;
    @Resource private GymMobilePublicService publicContent;
    @Resource private Validator validator;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<Coach> page(CoachPage req) {
        var violations = validator.validate(req);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        publicContent.requirePublicStore(req.getStoreId());
        var page =
                employees.selectPage(req, profiles.publicQuery(req.getStoreId()).orderByAsc("id"));
        return new PageResult<>(
                page.getList().stream().map(this::response).toList(), page.getTotal());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Coach detail(Long id, Long storeId) {
        if (id == null || id <= 0) throw exception(MOBILE_REQUEST_INVALID);
        publicContent.requirePublicStore(storeId);
        var employee = employees.selectOne(profiles.publicQuery(storeId).eq("id", id));
        if (employee == null) throw exception(MOBILE_COACH_NOT_AVAILABLE);
        return response(employee);
    }

    private Coach response(GymEmployeeDO row) {
        var profile = profiles.read(row);
        if (profile == null || !Boolean.TRUE.equals(profile.getPublished()))
            return new Coach(row.getId().toString(), row.getName(), null, "", List.of(), List.of());
        return new Coach(
                row.getId().toString(),
                row.getName(),
                publicContent.coachMedia(row, profile.getAvatarFileId()),
                profile.getIntroduction(),
                profile.getSpecialties(),
                profile.getQualificationFileIds().stream()
                        .map(id -> publicContent.coachMedia(row, id))
                        .filter(Objects::nonNull)
                        .toList());
    }
}
