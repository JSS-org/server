package cn.iocoder.yudao.module.system.dal.mysql.sms;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.sms.SmsCodeDO;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface SmsCodeMapper extends BaseMapperX<SmsCodeDO> {

    /**
     * 获得手机号的最后一个手机验证码
     *
     * @param mobile 手机号
     * @param scene 发送场景，选填
     * @param code 验证码 选填
     * @return 手机验证码
     */
    default SmsCodeDO selectLastByMobile(String mobile, String code, Integer scene) {
        return selectFirstOne(
                new LambdaQueryWrapperX<SmsCodeDO>()
                        .eq(SmsCodeDO::getMobile, mobile)
                        .eq(scene != null, SmsCodeDO::getScene, scene)
                        .eq(code != null, SmsCodeDO::getCode, code)
                        .orderByDesc(SmsCodeDO::getId));
    }

    /** 只有尚未使用且仍在有效期内的验证码能被本次请求消费。 */
    default int consume(
            Long id, LocalDateTime expiresAfter, LocalDateTime usedTime, String usedIp) {
        return update(
                SmsCodeDO.builder().used(true).usedTime(usedTime).usedIp(usedIp).build(),
                new LambdaUpdateWrapper<SmsCodeDO>()
                        .eq(SmsCodeDO::getId, id)
                        .eq(SmsCodeDO::getUsed, false)
                        .gt(SmsCodeDO::getCreateTime, expiresAfter));
    }
}
