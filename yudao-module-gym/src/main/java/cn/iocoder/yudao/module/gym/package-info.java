/**
 * gym 模块，健身门店业务。
 *
 * 1. Controller 层：{@link cn.iocoder.yudao.module.gym.controller}
 * 2. Service 层：{@link cn.iocoder.yudao.module.gym.service}
 * 3. DAL 层：{@link cn.iocoder.yudao.module.gym.dal}
 * 4. 枚举 / 错误码：{@link cn.iocoder.yudao.module.gym.enums}
 * 5. 框架扩展：{@link cn.iocoder.yudao.module.gym.framework}
 *
 * 约定：
 * - 管理端接口前缀 /admin-api/gym，权限码 gym:&lt;实体&gt;:&lt;动作&gt;
 * - 门店 = system_dept（总部角色全量，门店角色限本部门），越权由 Service 层校验
 * - 响应统一 CommonResult&lt;T&gt;（HTTP 200 + body code），二进制流接口除外
 */
package cn.iocoder.yudao.module.gym;
