# MySQL 查询优化实验

先按 README 启动 Podman 依赖并启动一次 mysql 模式应用，让 Flyway 建表。仅在练习库中执行，脚本会新增 10 万条数据。

```bash
set -a
source .env
set +a
# 通过容器内环境变量传递密码，避免在命令参数中显式写密码
podman exec -i smart-ticket-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u ticket ticket' \
  < scripts/explain.sql
```

脚本对比相同结果范围的 `LIMIT 20 OFFSET 90000` 与 `id < cursor LIMIT 20`。前者通常需要遍历大量前置结果；后者通过范围定位取下一批。记录 `EXPLAIN ANALYZE` 的 actual time、rows 和 loops，结合数据量解释结果，不保证每种分布下同样的倍数。

`(status,id)` 匹配状态等值和 id 范围，InnoDB 可逆序扫描；没有状态过滤时主键可服务 id 分页。状态低基数并不自动意味着联合索引无用，应看完整查询、排序和返回行数。

索引不是覆盖整个列表，因为还选择 title、priority 等字段，需要回表。不要为演示随意把大文本塞入宽索引。列表不读取 description，不计算 total，都是降低查询负担的独立设计选择。

本次实测见 [验证报告](verification.md)：OFFSET 扫描 90020 行，游标扫描 20 行。游标计划实际选择 PRIMARY，并非联合索引；数据几乎全部为 OPEN，优化器选择应以实际计划为准。

HTTP 性能还受 BCrypt Basic 认证影响：本项目每个请求校验口令，压测不能把端点耗时全归因于数据库或 Redis。比较缓存前后应固定鉴权、机器、并发与预热过程；一次压测也不能代表容量规划。
