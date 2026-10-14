# 实际验证记录

验证日期：2026-10-14。当前版本已重新执行后台测试；分页、缓存与压测结果来自同日后台基础设施验证。环境：Ubuntu / WSL2，Linux 6.18.54.1，x86_64，20 个可见逻辑 CPU，JDK 21.0.12.1，Maven 3.9.12，Podman 5.7.0。真实依赖由 Podman 启动：MySQL 8.4.11 与 Redis 7.4 系列。未修改系统代理配置。

## 自动化测试

| 场景 | 结果 |
| --- | --- |
| 默认 H2 模式 `mvn verify` | 11 个测试，0 失败、0 错误，JAR 打包成功 |
| mysql 模式 `mvn -Dspring.profiles.active=mysql test` | 同样 11 个测试，0 失败、0 错误 |
| 8 个同时提交 version=0 的状态更新 | 只有 1 个成功，最终 version=1，审计只有一次更新 |
| 审计插入模拟异常 | 状态及 version 回滚，未执行提交后的缓存删除 |
| Basic 认证 | 实际用户名/密码认证用例通过，匿名请求被拒绝 |

摘要：[H2 测试](evidence/h2-tests.log)、[MySQL 测试](evidence/mysql-tests.log)。

本次沙箱将 Maven 仓库放在 `/tmp/smart-ticket-m2`，所以实际构建命令使用 `-Dmaven.repo.local=/tmp/smart-ticket-m2`；用户本地可使用默认仓库。初次下载期间，为 Maven 单个进程设置了现有代理地址的 JVM 参数，没有更改系统代理或 Podman 配置。

## 运行中的 HTTP 演示

分别在默认 H2 模式与 mysql 模式运行 `scripts/demo.py`，均通过：创建 201 → 详情和列表 → 接单、解决、关闭 → 旧版本 409 → 四条审计事件。工单数据由演示新建，不依赖固定 ID。

## Redis 实测

运行 `scripts/verify-cache.py --outage`，观察到：

- 首次详情回填 key，实际 TTL=40 秒。
- 状态更新事务提交后 key 不存在，再查详情得到 version=1 并回填。
- 临时停止本项目 Redis 后，API 仍从 MySQL 返回 version=1。
- finally 恢复 Redis，客户端日志确认重新连接。

实际输出：[Redis 验证](evidence/redis-verification.log)。这些实测验证正常链路与一次故障回源，不证明并发缓存回填的强一致性。

## MySQL 十万行分页实验

执行 `scripts/explain.sql`，插入 100000 行后 ANALYZE TABLE，比较相同结果区间。实际计划：[原始执行计划](evidence/mysql-explain.log)。

| 查询 | 实际扫描输出行数 | 最上层实际耗时 |
| --- | --- | --- |
| LIMIT 20 OFFSET 90000 | 90020 | 约 97.2 ms |
| id < cursor LIMIT 20 | 20 | 约 0.0417 ms |

OFFSET 查询使用 `idx_ticket_status_id` 的反向查找；游标查询本次选择 PRIMARY 反向范围扫描，说明优化器不保证每次选择联合索引。这份数据几乎全是 OPEN，不能用它推导真实状态分布下的查询性能。执行顺序也使第二个查询可能受益于缓冲池预热。核心可验证结论是本次游标查询避免扫描 90000 个前置结果，不泛化具体性能倍数。

## HTTP 读压测

默认 H2 模式，已由 demo 创建并读取过的单个工单，Python urllib 并发 8、请求 200、Basic/BCrypt 认证，非独立容量测试环境：

- 成功 200，错误 0。
- 总耗时 1.657 秒，约 120.73 requests/s。
- 成功请求 P95 约 75.05 ms。

原始结果：[HTTP 压测](evidence/http-benchmark.json)。测试期间也运行了 MySQL 模式实例和分页实验，因此不视为受控性能基准；不代表生产 QPS、MySQL 吞吐或 Redis 收益。重跑时应使用实际工单 ID，固定环境，并独立比较冷缓存、热缓存与关闭缓存。

## 功能边界

Spring Cloud 集群、多租户隔离、幂等创建和生产部署尚未实现。


验证完成后，已清理临时应用进程、测试容器及测试数据卷；保留已拉取的镜像与项目构建产物，方便用户按 README 用自己的密码重新启动。
