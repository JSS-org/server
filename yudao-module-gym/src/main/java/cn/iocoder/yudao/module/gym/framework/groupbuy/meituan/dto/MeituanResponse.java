package cn.iocoder.yudao.module.gym.framework.groupbuy.meituan.dto;

import lombok.Getter;
import lombok.Setter;

/** 平台公共响应；OP_SUCCESS 仅表示本次接口调用成功，批量结果仍须逐条检查。 */
@Getter
@Setter
public class MeituanResponse<T> {
    private String code;
    private String msg;
    private String traceId;
    private T data;
}
