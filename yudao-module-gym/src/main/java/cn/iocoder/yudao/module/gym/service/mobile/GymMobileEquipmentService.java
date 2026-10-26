package cn.iocoder.yudao.module.gym.service.mobile;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.gym.enums.ErrorCodeConstants.*;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.controller.app.mobile.vo.GymMobileEquipmentVO.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.device.*;
import cn.iocoder.yudao.module.gym.dal.mysql.device.*;
import cn.iocoder.yudao.module.gym.dal.mysql.store.GymStoreMapper;
import cn.iocoder.yudao.module.gym.service.common.GymDataScope;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import jakarta.annotation.Resource;
import jakarta.validation.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.*;

@Service
public class GymMobileEquipmentService {
    @Resource private GymEquipmentMapper equipment;
    @Resource private GymEquipmentTutorialMapper tutorials;
    @Resource private GymStoreMapper stores;
    @Resource private GymMobilePublicService content;
    @Resource private GymMobileWorkAccess access;
    @Resource private GymDataScope scope;
    @Resource private Validator validator;

    private void positive(Long id) {
        if (id == null || id <= 0) throw exception(MOBILE_REQUEST_INVALID);
    }

    private boolean available(GymEquipmentDO row) {
        if (row == null || !"active".equals(row.getStatus()) || row.getPublicScene() == null)
            return false;
        var store = stores.selectById(row.getStoreId());
        return store != null && "active".equals(store.getStatus());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Resolution resolve(Resolve req) {
        TenantContextHolder.getRequiredTenantId();
        var errors = validator.validate(req);
        if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
        var row = equipment.selectOne(GymEquipmentDO::getPublicScene, req.getScene());
        if (!available(row)) throw exception(EQUIPMENT_SCENE_INVALID);
        return new Resolution("equipment", row.getId().toString(), row.getStoreId().toString());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Tutorials tutorials(Long id) {
        TenantContextHolder.getRequiredTenantId();
        positive(id);
        var row = equipment.selectById(id);
        if (!available(row)) throw exception(EQUIPMENT_NOT_EXISTS);
        var result = new ArrayList<Tutorial>();
        for (var tutorial :
                tutorials.selectList(
                        new LambdaQueryWrapper<GymEquipmentTutorialDO>()
                                .eq(GymEquipmentTutorialDO::getEquipmentId, id)
                                .eq(GymEquipmentTutorialDO::getStatus, "active")
                                .orderByAsc(GymEquipmentTutorialDO::getSortOrder)
                                .orderByDesc(GymEquipmentTutorialDO::getId))) {
            var media = content.equipmentMedia(tutorial);
            if (media == null) continue;
            result.add(
                    new Tutorial(
                            tutorial.getId().toString(),
                            tutorial.getTitle(),
                            tutorial.getInstructions(),
                            "image".equals(media.type()) ? media : null,
                            "video".equals(media.type()) ? media : null));
        }
        return new Tutorials(id.toString(), row.getName(), List.copyOf(result));
    }

    private GymEquipmentDO workRow(Long id) {
        access.requireAdmin();
        positive(id);
        var row =
                equipment.selectOne(
                        new LambdaQueryWrapper<GymEquipmentDO>()
                                .eq(GymEquipmentDO::getId, id)
                                .last("FOR UPDATE"));
        if (row == null) throw exception(EQUIPMENT_NOT_EXISTS);
        scope.check(row.getStoreId());
        return row;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Scene publish(Long id) {
        var row = workRow(id);
        var store = stores.selectById(row.getStoreId());
        if (!"active".equals(row.getStatus())
                || store == null
                || !"active".equals(store.getStatus())) throw exception(EQUIPMENT_SCENE_INVALID);
        if (row.getPublicScene() == null) {
            row.setPublicScene(UUID.randomUUID().toString().replace("-", ""));
            equipment.updateById(row);
        }
        return new Scene(id.toString(), row.getPublicScene());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public boolean revoke(Long id) {
        var row = workRow(id);
        if (row.getPublicScene() != null)
            equipment.update(
                    null,
                    new LambdaUpdateWrapper<GymEquipmentDO>()
                            .eq(GymEquipmentDO::getId, id)
                            .set(GymEquipmentDO::getPublicScene, null));
        return true;
    }
}
