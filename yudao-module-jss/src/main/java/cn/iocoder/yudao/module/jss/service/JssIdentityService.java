package cn.iocoder.yudao.module.jss.service;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.jss.dal.dataobject.JssIdentityAuditDO;
import cn.iocoder.yudao.module.jss.dal.dataobject.JssUserIdentityDO;
import cn.iocoder.yudao.module.jss.dal.mysql.JssIdentityAuditMapper;
import cn.iocoder.yudao.module.jss.dal.mysql.JssUserIdentityMapper;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;

import jakarta.annotation.Resource;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class JssIdentityService {
    @Resource private JssUserIdentityMapper identities;
    @Resource private JssIdentityAuditMapper audits;
    @Resource private MemberUserApi members;
    @Resource private AdminUserApi admins;

    @Transactional(rollbackFor = Exception.class)
    public Long bind(Long memberId, Long adminId, String reason) {
        // APIs run inside the authenticated tenant; no phone/ID coincidence is an identity proof.
        var member = members.getUser(memberId);
        var admin = admins.getUser(adminId);
        if (member == null
                || admin == null
                || !CommonStatusEnum.isEnable(member.getStatus())
                || !CommonStatusEnum.isEnable(admin.getStatus())) {
            throw exception0(BAD_REQUEST.getCode(), "会员和员工必须是当前租户的有效账号");
        }
        JssUserIdentityDO identity = identities.findByMember(memberId);
        if (identity == null) {
            identity = new JssUserIdentityDO();
            identity.setMemberUserId(memberId);
            identity.setAdminUserId(adminId);
            identity.setStatus(0);
            identity.setBindingVersion(1L);
            identity.setBoundBy(SecurityFrameworkUtils.getLoginUserId());
            identity.setBoundAt(LocalDateTime.now());
            try {
                identities.insert(identity);
            } catch (DuplicateKeyException ex) {
                throw exception0(BAD_REQUEST.getCode(), "会员或员工已绑定，请先审核解除原绑定");
            }
        } else {
            identity = identities.selectForUpdate(identity.getId());
            if (CommonStatusEnum.isEnable(identity.getStatus())) {
                throw exception0(BAD_REQUEST.getCode(), "该会员已有生效绑定，请先审核撤销");
            }
            // Reassignment is explicit and audited, and never overwrites an active binding.
            identity.setAdminUserId(adminId);
            identity.setStatus(0);
            identity.setBindingVersion(identity.getBindingVersion() + 1);
            identity.setBoundBy(SecurityFrameworkUtils.getLoginUserId());
            identity.setBoundAt(LocalDateTime.now());
            try {
                identities.updateById(identity);
            } catch (DuplicateKeyException ex) {
                throw exception0(BAD_REQUEST.getCode(), "该员工已有生效绑定，请先审核解除原绑定");
            }
        }
        audit(identity, "BIND", reason);
        return identity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id, String reason) {
        JssUserIdentityDO identity = identities.selectForUpdate(id);
        if (identity == null) {
            throw exception0(NOT_FOUND.getCode(), "绑定不存在");
        }
        if (!CommonStatusEnum.isEnable(identity.getStatus())) {
            return;
        }
        identities.revoke(id, String.valueOf(SecurityFrameworkUtils.getLoginUserId()));
        identity.setBindingVersion(identity.getBindingVersion() + 1);
        audit(identity, "REVOKE", reason);
    }

    private void audit(JssUserIdentityDO identity, String action, String reason) {
        JssIdentityAuditDO audit = new JssIdentityAuditDO();
        audit.setIdentityId(identity.getId());
        audit.setMemberUserId(identity.getMemberUserId());
        audit.setAdminUserId(identity.getAdminUserId());
        audit.setBindingVersion(identity.getBindingVersion());
        audit.setAction(action);
        audit.setOperatorId(SecurityFrameworkUtils.getLoginUserId());
        audit.setReason(reason);
        audits.insert(audit);
    }
}
