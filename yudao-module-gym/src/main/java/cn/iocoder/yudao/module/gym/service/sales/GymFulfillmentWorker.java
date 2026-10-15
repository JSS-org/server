package cn.iocoder.yudao.module.gym.service.sales;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import jakarta.annotation.Resource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
@Slf4j @Service
public class GymFulfillmentWorker {
 @Resource private GymFulfillmentQueueService queue;
 @Resource private GymCommerceService commerce;
 @Resource private AdminUserService users;
 @Resource private cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions;
 /** Tenant context comes from the trusted scheduler; no request body supplies identity or tenant. */
 public boolean process(Long id){
  var task=queue.claim(id);if(task==null)return false;var saved=SecurityContextHolder.getContext();String failure=null;
  try{
   var user=users.getUser(task.getPrincipalUserId());
   if(user==null||!Integer.valueOf(0).equals(user.getStatus())||!TenantContextHolder.getRequiredTenantId().equals(user.getTenantId()))failure="发起账号已停用、不存在或不属于当前租户";
   else if(!permissions.hasAnyPermissions(user.getId(),"gym:sales:read"))failure="发起账号已无交易读取权限";
   else{
    var login=new LoginUser();login.setId(user.getId());login.setUserType(2);login.setTenantId(user.getTenantId());var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(login,null,List.of()));SecurityContextHolder.setContext(context);
    commerce.fulfillAutomaticOrder(task.getOrderId());
   }
  }catch(ServiceException e){failure="自动履约业务校验失败，错误码 "+e.getCode()+"；请检查合同、卡种和门店权限";}
   catch(Exception e){failure="自动履约发生内部异常，等待重试或后台排查";log.error("自动履约内部异常，taskId={}, orderId={}",task.getId(),task.getOrderId(),e);}
  finally{try{queue.finish(task.getId(),task.getClaimToken(),failure);}finally{SecurityContextHolder.setContext(saved);}}
  return true;
 }
}
