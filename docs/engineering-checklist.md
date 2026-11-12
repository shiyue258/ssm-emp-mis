# 企业开发规范清单

用于开发过程自检。每条包含「规范要求 / 本项目做法 / 证据位置」三部分，可直接对照代码核查；只写实际做到的，没做到的单列在最后一节。

## 一、分层与职责

| # | 规范 | 本项目做法 | 证据 |
| :-: | :--- | :--- | :--- |
| 1 | 分层单向依赖，禁止反向调用 | `controller → service → mapper`，`entity` 被上层依赖但不依赖任何人 | `docs/architecture.md` 第五节 |
| 2 | Controller 只做协议适配，不写业务与 SQL | 六个端点方法体均为「调 Service + 包 `Result`」，无一行 SQL、无事务注解 | `EmpController.java` |
| 3 | 事务边界画在 Service 实现类 | `@Transactional` 只出现在 `EmpServiceImpl` 的 public 方法上，接口层不标 | `EmpServiceImpl.java` |
| 4 | 业务层不返回表现层对象 | Service 返回 `Emp` / `Page<Emp>` / `boolean`，不出现 `Result` | `EmpService.java` |

## 二、命名与可读性

| # | 规范 | 本项目做法 | 证据 |
| :-: | :--- | :--- | :--- |
| 5 | 类名体现职责，包名体现分层 | `EmpServiceImpl` / `LoginInterceptor` / `WebLogAspect`；包名 `config/controller/service/mapper/entity/dto/common/aspect/interceptor` | `src/main/java/com/lab/emp/` |
| 6 | 关键决策写「为什么」，不写「做什么」 | 每个类与关键方法均有 Javadoc 说明取舍（如为何不用 `SqlSessionFactoryBean`、为何切面必须在子容器） | 各类文件头注释 |
| 7 | 缩写与术语全局一致 | `emp` / `userId` / `pageQuery` 在全工程保持同一写法，不混用 `employee`、`queryPage` | 全局检索可验证 |

## 三、异常与错误处理

| # | 规范 | 本项目做法 | 证据 |
| :-: | :--- | :--- | :--- |
| 8 | 可预期失败与未知异常分级处理 | `BusinessException` 走 warn 级、原样返回 message；其余异常走 error 级、对外固定文案 | `GlobalExceptionHandler.java` |
| 9 | 不把堆栈、表名、SQL 暴露给调用方 | 兜底分支只返回「系统繁忙，请稍后重试」，完整堆栈仅进日志 | 同上 |
| 10 | 统一返回结构，成功/失败同一外壳 | `Result{code, message, data}`，构造函数私有，只能经 `ok/fail/unauthorized` 产生 | `Result.java` |
| 11 | 参数校验在入口拦截，且有多道防线 | JSR-303 注解在实体上 + `@Valid` 触发；数据库侧另有 CHECK 约束与 NOT NULL | `Emp.java`、`sql/init.sql` |

## 四、日志

| # | 规范 | 本项目做法 | 证据 |
| :-: | :--- | :--- | :--- |
| 12 | 日志集中一处，不散落 `System.out` | 全部经 SLF4J；请求日志由 `WebLogAspect` 统一织入 | `WebLogAspect.java` |
| 13 | 敏感信息不入日志 | `LoginDTO` 的 `password` 固定输出 `***`；实测 `grep -c 123456` 为 0 | 同上、README 5.4 |
| 14 | 日志量可控 | 入参超 300 字符截断；Servlet 对象跳过不打印 | 同上 |

## 五、数据库与持久层

| # | 规范 | 本项目做法 | 证据 |
| :-: | :--- | :--- | :--- |
| 15 | 禁止字符串拼接 SQL | 全部走 `LambdaQueryWrapper` 与方法参数占位，无 `+` 拼接 | `EmpServiceImpl.java` |
| 16 | 金额禁用浮点 | 库中 `DECIMAL(10,2)`，Java 侧 `BigDecimal`，断言用 `setScale(2)` | `Emp.java`、`init.sql` |
| 17 | 逻辑删除与业务状态分离 | `deleted` 由 `@TableLogic` 管理；`status`（在职/离职）是业务字段，二者不合并 | `Emp.java` |
| 18 | 约束下沉到数据库 | 4 条 CHECK（gender / salary / status / deleted）、2 个索引、唯一键；实测 `ERROR 3819` 拦截 | `sql/init.sql` |

## 六、安全

| # | 规范 | 本项目做法 | 证据 |
| :-: | :--- | :--- | :--- |
| 19 | 口令不落明文 | 库中存 `SHA-256(明文)` 摘要，实体不含 `password` 字段 | `sys_user` 表、`AuthServiceImpl.java` |
| 20 | 不通过错误提示泄露账号是否存在 | 「用户不存在」与「口令错误」返回同一句提示 | 同上 |
| 21 | 凭证不写死在代码/仓库 | `jdbc.properties` 仅存本地默认值，支持 `EMP_DB_PASSWORD` 环境变量覆盖 | `SpringConfig.java` |
| 22 | 未登录请求在入口拦截 | `LoginInterceptor` 返回 401，放行名单仅 `/login`、`/health` | `SpringMvcConfig.java` |

## 七、测试

| # | 规范 | 本项目做法 | 证据 |
| :-: | :--- | :--- | :--- |
| 23 | 不只测正常路径 | 4 个用例中 3 个验证「静默失效」，1 个作对照；断言看数据而非异常 | `TransactionFailureTest.java` |
| 24 | 测试数据可清理、可重复执行 | 统一 `事务测试_` 前缀，`@After` 物理删除，跑完 `emp` 表恒为 12 行 | 同上 |

## 八、版本控制与文档

| # | 规范 | 本项目做法 | 证据 |
| :-: | :--- | :--- | :--- |
| 25 | 提交粒度对应一件可验证的事 | 十次提交，每次对应一个可运行/可验证的状态 | `git log` |
| 26 | 提交信息说明动机与影响面 | 标题给结论，正文列改动点与取舍理由 | 同上 |
| 27 | 文档与实现同步 | 每次改动同步更新 README 的验证结果表与变更记录，不留「文档说 A、代码做 B」 | `README.md` 5.4 / 十 |
| 28 | 不提交构建产物与本地配置 | `.gitignore` 排除 `target/`、IDE 配置、JVM 崩溃产物、敏感配置 | `.gitignore` |

## 九、未做到的部分

如实列出，避免把「清单」写成「成绩单」：

| 项 | 现状 | 影响 |
| :--- | :--- | :--- |
| 持续集成 | 无 CI 配置 | 每次改动靠本地 `mvn test` 把关，没有自动化门禁 |
| 接口文档自动化 | `docs/api.md` 手写维护 | 接口变更时需人工同步，存在滞后风险 |
| 会话管理 | Servlet 原生 session，单机内存 | 多实例部署时无法共享会话，需改 Redis 或 JWT |
| 口令哈希强度 | SHA-256 单次散列 | 抗彩虹表弱于 BCrypt/Argon2；仅为不引入额外依赖的折中 |
| 压力测试 | 未做 | 并发能力无量化数据 |
