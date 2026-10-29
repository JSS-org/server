# 异步数据权限栈隔离修复

2026-10-29，真实 IM 服务定向运行 `jss_http_verify_246469546130` 再现接收端断连。新增根因日志明确记录 `NoSuchElementException → LinkedList.removeLast → DataPermissionContextHolder.remove → DataPermissionAnnotationInterceptor → AdminUserApiImpl.getUser → JssWorkspaceService.allowedIdentity`。日志不含令牌、异常消息或正文。临时库残留 0、所属 Redis 关闭，失败记录保留。

原 `TransmittableThreadLocal.withInitial(LinkedList::new)` 在 TTL 捕获与子线程继承时共享同一可变 LinkedList。请求线程和异步通知线程分别调用带数据权限注解的用户 API，入栈/出栈会互相修改；既可能丢失权限作用域，也可能重复出栈。WebSocket 管理器将鉴权异常按原规则关闭为 1008，因此此前偶发缺帧不能靠延长等待解决。

修复在共享框架边界进行：add/remove 先复制栈再发布当前线程的新值，不再修改已发布栈；getAll 返回副本，避免外部调用改动被捕获的上下文。保留嵌套顺序、空栈清理、原注解与鉴权规则；没有吞掉异常、缓存有效身份或允许鉴权失败后发送。影响所有使用该上下文的异步模块，不局限于 IM。

独立 JVM 的确定性捕获复现：子任务捕获权限后等待，父线程先出栈，原代码导致子任务权限消失（`.runtime/data-permission-capture-before.log/.exit`，退出 1）；修复后同一程序通过（`data-permission-capture-after.*`，退出 0）。正式定向 `DataPermissionContextHolderTest` 共 6 项通过，失败/错误/跳过 0：既有三项及新增父线程退出后捕获保留、两个捕获之间及父/工作线程入出栈隔离/工作线程恢复、返回列表不能修改上下文。证据 `.runtime/data-permission-context-tests.log/.exit/.xml`。

上述是真实线程/TTL 行为检查，数据库无参与；不能代替修复后真实 IM 流程。完整服务在确定根因的失败运行中加载的是修复前 class；源码编辑与独立 `.runtime` javac 不改变该运行的产物。仅在其终止和清理后才运行相关 Maven 定向检查。修复后的同一 HTTP/WS 流程结果另行记录，不以此前通过轮次代替。
