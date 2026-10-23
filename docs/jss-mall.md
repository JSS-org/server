# JSS 商城运行组合

`jss-mall` Maven profile 将 pay、product、promotion、trade、statistics 加入已有 system/infra/gym/member/JSS 容器。它不启用无关 CRM/BPM 等模块，也不执行数据库迁移。

```sh
mvn -Pjss-mall -pl yudao-server -am package -DskipTests
```

本地已成功构建，并核验生成 jar 中包含上述五个模块及 trade-api。普通构建继续使用原有模块组合。

启动完整商城之前，必须完成会员、商品、营销、交易、支付的数据库结构与必要配置。当前只完成模块依赖接入，尚未证明真实商城 API 可用；数据库迁移、配置和隔离联调继续按整合计划推进。不要将已有框架初始化 SQL 全量重放到业务库。
