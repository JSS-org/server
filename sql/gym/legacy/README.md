# Frozen gym SQL history

01～47保留原始内容，sha256.json固定来源校验和；不再作为发布入口，不在Flyway扫描路径内。旧development导入仅供追溯及隔离验证。新增变更放到 ../migration/mysql 的新版本，开发数据放 ../seed/development。

当前操作入口见 ../../../script/gym/README.md。禁止在运行库全量重放历史账号/角色授权；历史验证工具只创建并清理随机隔离库。
