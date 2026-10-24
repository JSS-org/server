package cn.iocoder.yudao.module.gym.service.file;

import cn.iocoder.yudao.module.gym.dal.dataobject.mobile.GymMobileWorkFileDO;
import cn.iocoder.yudao.module.gym.dal.mysql.mobile.GymMobileWorkFileMapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;

import java.util.Objects;

/** New uploads keep their original business target, including when old admin APIs bind them. */
@Service
public class GymWorkFileBindings {
    @Resource private GymMobileWorkFileMapper files;

    public GymMobileWorkFileDO find(Long fileId) {
        return files.selectOne(GymMobileWorkFileDO::getFileId, fileId);
    }

    public boolean member(Long fileId, String purpose, Long member, Long store) {
        var row = find(fileId);
        return row == null
                || purpose.equals(row.getPurpose())
                        && Objects.equals(member, row.getMemberId())
                        && Objects.equals(store, row.getStoreId());
    }

    public boolean coach(Long fileId, Long employee) {
        var row = find(fileId);
        return row == null
                || employee != null
                        && "coach_profile".equals(row.getPurpose())
                        && employee.equals(row.getEmployeeId());
    }

    public boolean template(Long fileId) {
        var row = find(fileId);
        return row == null || "contract".equals(row.getPurpose()) && row.getMemberId() == null;
    }
}
