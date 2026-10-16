package cn.iocoder.yudao.module.system.service.auth;

import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthPermissionInfoRespVO.MenuVO;

import java.util.List;

/** 登录菜单的展示适配扩展；输入已经过角色授权和禁用菜单过滤，不得增加业务页面权限。 */
public interface AuthMenuTreeCustomizer {

    List<MenuVO> customize(List<MenuVO> menus);
}
