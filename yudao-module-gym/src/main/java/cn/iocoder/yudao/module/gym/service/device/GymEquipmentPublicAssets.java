package cn.iocoder.yudao.module.gym.service.device;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.dal.dataobject.device.*;
import cn.iocoder.yudao.module.gym.dal.dataobject.file.GymFileDO;
import cn.iocoder.yudao.module.gym.dal.mysql.device.GymEquipmentTutorialMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.service.file.GymWorkFileBindings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GymEquipmentPublicAssets {
    @Resource private GymEquipmentTutorialMapper tutorials;
    @Resource private GymFileMapper files;
    @Resource private GymWorkFileBindings bindings;

    private boolean eligible(GymFileDO f) {
        return f != null
                && (("image".equals(f.getCategory())
                                && Set.of("image/jpeg", "image/png", "image/gif", "image/webp")
                                        .contains(Objects.toString(f.getMimeType(), "")))
                        || ("video".equals(f.getCategory()) && "video/mp4".equals(f.getMimeType())))
                && bindings.find(f.getId()) == null;
    }

    private LambdaQueryWrapper<GymEquipmentTutorialDO> query(GymFileDO f) {
        return new LambdaQueryWrapper<GymEquipmentTutorialDO>()
                .eq(GymEquipmentTutorialDO::getFileId, f.getId())
                .eq(GymEquipmentTutorialDO::getStatus, "active")
                .exists(
                        "SELECT 1 FROM gym_equipment e JOIN gym_store s ON s.id=e.store_id AND s.tenant_id={0} AND s.deleted=0 AND s.status='active' WHERE e.id=gym_equipment_tutorial.equipment_id AND e.tenant_id={0} AND e.deleted=0 AND e.status='active' AND e.public_scene IS NOT NULL AND e.store_id={1}",
                        TenantContextHolder.getRequiredTenantId(), f.getStoreId());
    }

    public GymFileDO file(GymEquipmentTutorialDO row) {
        var f = files.selectById(row.getFileId());
        return eligible(f)
                        && tutorials.selectCount(
                                        query(f).eq(GymEquipmentTutorialDO::getId, row.getId()))
                                > 0
                ? f
                : null;
    }

    public boolean references(GymFileDO f) {
        return eligible(f) && tutorials.selectCount(query(f)) > 0;
    }
}
