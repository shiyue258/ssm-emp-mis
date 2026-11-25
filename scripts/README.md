# scripts 说明

这些脚本原本散落在 `~/`，现收进仓库，便于换机器或交接后直接复现。
全部假设：**项目位于 `$HOME/projects/ssm-emp-mis`**，Tomcat 路径写在 `$HOME/opt/tomcat9.path`。

| 脚本 | 作用 | 依赖 |
| :--- | :--- | :--- |
| `check_env.sh` | 打印 JDK / Maven / Tomcat / MySQL 版本 | — |
| `deploy.sh` | 把 `target/ssm-emp-mis.war` 拷成 `webapps/ROOT.war`（上下文路径 `/`） | 先 `mvn clean package` |
| `run_tomcat.sh` | 前台运行 `catalina.sh run`，日志写 `logs/run.out` | **必须以后台任务方式启动** |
| `stop_tomcat.sh` | 停服并释放 | — |
| `verify_all.sh` | 主回归：员工、部门、账号、考勤、薪资 | 服务已启动、库为种子状态 |
| `verify_perm.sh` | 三角色权限、403、撤权即时生效、最后管理员保护 | 同上 |
| `verify_perm2.sh` | 路径冲突、删除带角色账号、角色 CRUD | 同上 |
| `verify_perm3.sh` | 分配不存在角色、角色删除后的关联清理 | 同上 |
| `verify_std.sh` | 薪资标准生效日期（补生成历史月份） | 同上 |
| `verify_perf.sh` | 响应耗时、连续 60 次、10 并发 | 同上 |

## 使用约定

```bash
# 一次完整的「改代码 → 上线 → 验证」
mvn -B clean package
bash scripts/deploy.sh
bash scripts/run_tomcat.sh        # 后台任务方式
bash scripts/verify_all.sh
```

## 已知差异

- `verify_all.sh` 的 U01 断言「账号总数 1」，而种子现有 3 个账号（admin/hr01/viewer01）。
  跑出来 `total=3` 属**脚本期望过期**，不是缺陷；修改断言即可。
- 所有脚本以 `no_proxy='*'` 访问 `127.0.0.1`，避免 WSL 里残留的代理变量导致 curl 走代理。
