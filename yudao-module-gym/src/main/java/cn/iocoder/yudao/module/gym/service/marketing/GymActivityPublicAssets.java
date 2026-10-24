package cn.iocoder.yudao.module.gym.service.marketing;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.gym.dal.dataobject.file.GymFileDO;
import cn.iocoder.yudao.module.gym.dal.dataobject.marketing.GymActivityDO;
import cn.iocoder.yudao.module.gym.dal.mysql.file.GymFileMapper;
import cn.iocoder.yudao.module.gym.dal.mysql.marketing.GymActivityMapper;
import cn.iocoder.yudao.module.gym.service.file.GymWorkFileBindings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GymActivityPublicAssets {
    @Resource private GymActivityMapper activities;
    @Resource private GymFileMapper files;
    @Resource private GymWorkFileBindings bindings;

    public GymFileDO file(GymActivityDO row) {
        if (row.getCoverFileId() == null
                || !Set.of("published", "closed").contains(row.getStatus())) return null;
        var f = files.selectById(row.getCoverFileId());
        return eligible(f) && referencesRow(f, row.getId()) ? f : null;
    }

    private boolean eligible(GymFileDO f) {
        return f != null
                && "image".equals(f.getCategory())
                && Set.of("image/jpeg", "image/png", "image/webp", "image/gif")
                        .contains(Objects.toString(f.getMimeType(), ""))
                && bindings.find(f.getId()) == null;
    }

    private LambdaQueryWrapper<GymActivityDO> query(GymFileDO f) {
        return new LambdaQueryWrapper<GymActivityDO>()
                .eq(GymActivityDO::getCoverFileId, f.getId())
                .in(GymActivityDO::getStatus, List.of("published", "closed"))
                .exists(
                        "SELECT 1 FROM gym_activity_store ast JOIN gym_store s ON s.id=ast.store_id AND s.tenant_id={0} AND s.deleted=0 AND s.status='active' WHERE ast.activity_id=gym_activity.id AND ast.tenant_id={0} AND ast.deleted=0 AND ast.store_id={1}",
                        TenantContextHolder.getRequiredTenantId(), f.getStoreId());
    }

    private boolean referencesRow(GymFileDO f, Long id) {
        return activities.selectCount(query(f).eq(GymActivityDO::getId, id)) > 0;
    }

    public boolean references(GymFileDO f) {
        return eligible(f) && activities.selectCount(query(f)) > 0;
    }
}
