# ssm-emp-mis · EMP 员工管理 MIS 系统（SSM 全注解整合）

<p>
  <b>《JAVA框架技术（一）》实验二</b> · 10 学时（含双师课堂 2 学时）· 综合性 + 设计性<br>
  Spring + SpringMVC + MyBatis-Plus 全注解整合，war 包部署于外置 Tomcat 9，<br>
  对外提供 EMP 员工管理的 RESTful 接口，并覆盖参数校验、全局异常、统一返回、请求日志、登录拦截与声明式事务。
</p>

[![CI](https://github.com/shiyue258/ssm-emp-mis/actions/workflows/ci.yml/badge.svg)](https://github.com/shiyue258/ssm-emp-mis/actions/workflows/ci.yml)

| 项 | 内容 |
| :--- | :--- |
| 构建 | Maven 3.9.12 |
| 语言 / JDK | **Java 17**（`maven.compiler.release = 17`） |
| 框架 | Spring 5.3.31 + SpringMVC 5.3.31 + MyBatis-Plus 3.5.3.1 |
| 数据库 | MySQL 8.0，库 `ssm_emp`，主表 `emp` |
| 容器 | Tomcat 9.0.x（`javax.servlet` 4.0 命名空间） |
| 打包 | `war`，全注解整合、**无 `web.xml`** |
| 版本 | `v1.3.0`：相对 `v1.2.0` 新增薪资标准表（按生效日期）、薪资重算与界面优化 |
| 表数量 | 10 张：`emp` / `dept` / `sys_user` / `attendance` / `salary_record` / `salary_standard` / `role` / `permission` / `user_role` / `role_permission`（另有审计表 `op_log`，共 11 张） |
| 状态 | ✅ **已完成**：47 个接口（健康 2 / 认证 2 + 当前用户 1 / 员工 6 / 部门 6 / 考勤 6 / 薪资 7 / 账号 8 / 角色与权限 8 / 审计 1）、91 条接口用例、4 个 JUnit 用例、单页管理界面，已在 Tomcat 9 + JDK 17 上端到端验证 |

> 标注口径：`✅` 表示仓库里真实存在且已跑通，`⬜` 表示尚未开始。

## 目录

- [一、这个仓库是什么](#一这个仓库是什么)
- [二、技术栈与版本锁定](#二技术栈与版本锁定)
  - [2.2 为什么把 JDK 提到 17](#22-为什么把-jdk-提到-17)
  - [2.3 为什么停在 Spring 5.3 + Tomcat 9](#23-为什么停在-spring-53--tomcat-9-而不是升到-spring-6)
  - [2.5 版本兼容矩阵](#25-版本兼容矩阵)
- [三、接口清单](#三接口清单)
  - [3.1 三个容易误解的行为](#31-三个容易误解的行为)
- [四、工程结构](#四工程结构)
  - [4.1 为什么是单个 Maven 模块](#41-为什么是单个-maven-模块--内部分层不是多模块)
  - [4.2 数据库设计](#42-数据库设计ssm_empemp)
  - [4.3 登录用户表](#43-登录用户表ssm_empsys_user)
  - [4.4 部门表](#44-部门表ssm_empdept)
  - [4.5 考勤如何影响薪资](#45-考勤如何影响薪资)
  - [4.6 薪资标准：为什么不能直接用 emp.salary](#46-薪资标准为什么不能直接用-empsalary)
  - [4.7 权限模型：从「登录了」到「能不能做这件事」](#47-权限模型从登录了到能不能做这件事)
- [五、构建与运行](#五构建与运行)
  - [5.2 初始化数据库与构建](#52-初始化数据库与构建)
  - [5.3 部署到 Tomcat 9 与接口基址](#53-部署到-tomcat-9-与接口基址)
  - [5.4 打开管理界面](#54-打开管理界面)
  - [5.5 目前验证到哪一步](#55-目前验证到哪一步)
  - [5.6 事务失效三场景实验](#56-事务失效三场景实验)
  - [5.7 故障排查手册](#57-故障排查手册)
- [六、评分点与工程产出的对照](#六评分点与工程产出的对照)
- [七、路线图](#七路线图任务-27)
- [八、与实验一的关系](#八与实验一的关系)
- [九、曾经的设计决策与结论](#九曾经的设计决策与结论)
- [十、变更记录](#十变更记录)
- [附：交付文档](#附交付文档)

---


## 一、这个仓库是什么

一个最小但完整的**员工管理 MIS（Management Information System）**后端，用来承载实验二的六个考核点：

1. **全注解整合**——用 `AbstractAnnotationConfigDispatcherServletInitializer` 取代 `web.xml`，父子容器分离（根容器管 Service/Mapper/事务，子容器管 Controller）；
2. **RESTful 接口**——`/emps` 资源的增删改查 + 多条件模糊 + 分页；
3. **系统健壮性**——JSR-303 校验、`@RestControllerAdvice` 全局异常、`Result{code,message,data}` 统一返回、AOP 请求日志、登录拦截器；
4. **数据一致性**——基于 Druid + `DataSourceTransactionManager` 的声明式事务，并**主动制造三类事务失效场景**留证；
5. **AI 辅助可追溯**——保留提示词、生成结果、人工修正的完整链条；
6. **可测试**——ApiFox 用例集覆盖正常路径与异常路径。

### 它不是什么

- **不是 Spring Boot 项目**：没有 `spring-boot-starter-*`、没有自动配置、没有内嵌容器。所有 Bean 都由 `@Configuration` 类显式装配——这正是实验二的考核主体。
- **不是前后端分离的前端工程**：本仓库只产出 JSON，不产出页面。
- **不用 Spring 6 / Tomcat 10**：见 [2.3](#23-为什么停在-spring-53--tomcat-9-而不是升到-spring-6)。

---

## 二、技术栈与版本锁定

### 2.1 版本清单

所有版本写在 `pom.xml` 的 `<properties>` 里，集中管理；不使用 `LATEST` / `RELEASE`，也不继承 parent。

| 组件 | 版本 | 说明 |
| :--- | :--- | :--- |
| JDK | **17**（Temurin 17.0.21） | `release=17`，见 [2.2](#22-为什么把-jdk-提到-17) |
| Maven | 3.9.12 | 本机默认 `JAVA_HOME` 已指向 JDK 17 |
| Spring / SpringMVC | 5.3.31 | 基于 `javax.*` 的最后一条主线 |
| MyBatis-Plus | 3.5.3.1 | 传递引入 mybatis 3.5.10、mybatis-spring 2.0.7、jsqlparser 4.4 |
| Druid | 1.2.20 | 连接池，`initialSize=5` / `maxActive=20` |
| MySQL 驱动 | `com.mysql:mysql-connector-j:8.0.33` | 已排除 `protobuf-java`（仅 X DevAPI 需要） |
| Jackson | 2.15.3（含 `jackson-datatype-jsr310`） | JSON 序列化，含 `LocalDate` / `LocalDateTime` |
| Hibernate Validator | 6.2.5.Final | JSR-303 实现，`javax.validation` 命名空间 |
| Servlet API | 4.0.1（`provided`） | 由 Tomcat 9 提供 |
| AspectJ | aspectjweaver 1.9.21 | `@Aspect` 切点解析 |
| 日志 | slf4j 1.7.36 + slf4j-reload4j + reload4j 1.2.19 | 单一日志实现，见 [2.4](#24-两个容易被忽略的依赖细节) |
| Lombok | 1.18.30（`provided`） | JDK 17 兼容 |
| 测试 | JUnit 4.13.2 + spring-test 5.3.31 | Service 层与集成测试 |

### 2.2 为什么把 JDK 提到 17

指导书「二、实验环境」写的是 **JDK 8/11**，本工程主动升到 **17**。理由与代价如下，实验报告「实验环境」一节将原样引用这段说明。

**选择理由**

1. **环境已就绪，零成本**：本机 SDKMAN 已安装 `17.0.21-tem`，且 `mvn -v` 默认输出的就是 `Java version: 17.0.21`。升级不需要改 `JAVA_HOME`，也不需要 `sudo` 装包。
2. **仍在 Spring 5.3 的支持范围内**：Spring Framework 5.3.x 官方基线为 JDK 8，5.3 分支已覆盖 JDK 17；`javax.servlet` 命名空间不变，指导书与教材里的全部注解、配置类示例可以逐字照抄。
3. **17 是当前的教学与工程共同 LTS**：8 已停止免费商用更新，11 处于维护尾期；17 的文本块（写多行 SQL / JSON 断言更可读）、`record`（DTO 免样板）、`instanceof` 模式匹配、`switch` 表达式，直接对应评分表里的「编码规范」「代码可读性」两项。

**代价与应对**

- 与指导书声明的版本不符，必须在报告中显式说明「为什么改」和「改完是否仍兼容」。为此本仓库保留 `jdk8-lint` profile：以 `release 8` 再编译一遍，用来量化「哪些代码是 17 独有写法」。
- JDK 17 的强封装会让反射滥用从「运行时玄学」变成「编译/启动期显式报错」。**不过本工程实测不需要 `--add-opens`**：Spring 5.3 起已适配 17 的封装限制，Druid、MyBatis-Plus 也未触达受限 API（详见 5.3 末尾）。

### 2.3 为什么停在 Spring 5.3 + Tomcat 9，而不是升到 Spring 6

这是本项目**唯一一条不可协商的技术约束**：

| 组合 | 命名空间 | 容器 | 与教材/指导书的关系 |
| :--- | :--- | :--- | :--- |
| **Spring 5.3 + Servlet 4.0 + Tomcat 9**（本工程） | `javax.*` | Tomcat 9 | ✅ 注解、配置类、代码示例可逐字对照 |
| Spring 6 + Servlet 5.0+ + Tomcat 10 | `jakarta.*` | Tomcat 10 | ❌ 全工程 `import` 需整体替换，教材示例全部失效 |

升 Java 版本（语言层）与升 Spring 大版本（API 层）是两件独立的事。**只做前者，不动后者**——这是本工程在「用新 JDK」和「贴合课程」之间的取舍点。

### 2.4 两个容易被忽略的依赖细节

**其一：日志为什么不是「log4j 1.2.17 + slf4j-log4j12」。**
`slf4j-log4j12:1.7.36` 的 pom 已 `<relocation>` 到 `slf4j-reload4j`，会额外拉进 reload4j 1.2.19，导致 classpath 里同时存在两份同名的 `org/apache/log4j/*` 类，谁先加载取决于类加载顺序。本工程统一到 **reload4j 单实现**（log4j 1.2 的维护分支，包名与 API 完全兼容），`log4j.properties` 一个字都不用改。可用 `mvn dependency:tree` 复核：只有一个日志实现。

**其二：Hibernate Validator 需要 EL 实现。**
缺 `jakarta.el` 时启动不报错、一校验就抛 `HV000183: Unable to initialize 'javax.el.ExpressionFactory'`。Tomcat 9 的 `lib/` 自带 EL 3.0，故本工程把 `org.glassfish:jakarta.el:3.0.4` 标为 `provided`；若改用嵌入式容器，要去掉该 scope。

### 2.5 版本兼容矩阵

| 维度 | 约束 | 本工程取值 | 结论 |
| :--- | :--- | :--- | :--- |
| JDK ↔ Spring | Spring 5.3.x 支持 JDK 8–17 | 17 | ✅ |
| JDK ↔ Lombok | Lombok ≥ 1.18.22 才支持 17 | 1.18.30 | ✅ |
| Spring ↔ Servlet | Spring 5.3 对应 Servlet 3.1–4.0 | 4.0.1 | ✅ |
| Servlet ↔ Tomcat | Servlet 4.0 对应 Tomcat 9 | 9.0.x | ✅ |
| MP ↔ MyBatis | MP 3.5.3.1 绑定 MyBatis 3.5.10 | 3.5.10（传递） | ✅ |
| MP ↔ mybatis-spring | MP 3.5.3.1 绑定 mybatis-spring 2.0.7 | 2.0.7（传递） | ✅ |
| Validator ↔ Spring | Spring 5 使用 `javax.validation` | HV 6.2.5 | ✅（HV 7 起改 `jakarta`，不可用） |

---

## 三、接口清单

统一前缀 `/emps`，统一返回 `Result{code, message, data}`。

| # | 方法 | 路径 | 说明 | 状态 |
| :-: | :--- | :--- | :--- | :--- |
| 0 | GET | `/health` | 框架存活检查（不碰数据库） | ✅ 已实现 |
| 0 | GET | `/health/db` | 持久层检查，返回 `emp` 表行数 | ✅ 已实现 |
| 1 | POST | `/emps` | 新增员工 | ✅ 已实现 |
| 2 | DELETE | `/emps/{id}` | 按 ID 删除（逻辑删除） | ✅ 已实现 |
| 3 | DELETE | `/emps` | 批量删除（body 传 `List<Integer>`） | ✅ 已实现 |
| 4 | PUT | `/emps` | 修改员工信息 | ✅ 已实现 |
| 5 | GET | `/emps/{id}` | 单条查询 | ✅ 已实现 |
| 6 | GET | `/emps` | 列表 + 多条件模糊 + 分页（`name` / `gender` / `dept` / `page` / `size` / `sortBy` / `sortDir`） | ✅ 已实现 |
| 7 | POST | `/login` | 登录，写入 session（拦截器放行名单内） | ✅ 已实现 |
| 8 | POST | `/logout` | 退出，销毁 session | ✅ 已实现 |
| 9 | GET | `/depts` | 部门列表 + 名称模糊（`name`） | ✅ 已实现 |
| 10 | GET | `/depts/{id}` | 部门单条查询 | ✅ 已实现 |
| 11 | POST | `/depts` | 新增部门（名称唯一） | ✅ 已实现 |
| 12 | PUT | `/depts` | 修改部门（改名时级联刷新员工所属部门） | ✅ 已实现 |
| 13 | DELETE | `/depts/{id}` | 删除部门（部门下有员工时拒绝） | ✅ 已实现 |
| 14 | GET | `/depts/{id}/employees` | 该部门下的员工列表 | ✅ 已实现 |
| 15 | GET | `/attendance` | 考勤分页（`empId` / `month`） | ✅ 已实现 |
| 16 | GET | `/attendance/{id}` | 考勤单条 | ✅ 已实现 |
| 17 | POST | `/attendance` | 登记考勤（状态由打卡时间推导） | ✅ 已实现 |
| 18 | PUT | `/attendance` | 修改考勤（时间改动后状态重算） | ✅ 已实现 |
| 19 | DELETE | `/attendance/{id}` | 删除考勤 | ✅ 已实现 |
| 20 | GET | `/attendance/summary` | 月度汇总（按人出勤率） | ✅ 已实现 |
| 21 | GET | `/salaries` | 薪资分页（`empId` / `month`） | ✅ 已实现 |
| 22 | GET | `/salaries/{id}` | 薪资单条 | ✅ 已实现 |
| 23 | POST | `/salaries/generate` | 按月批量生成（幂等） | ✅ 已实现 |
| 24 | POST | `/salaries/recalculate` | 按当前考勤重算待发放记录 | ✅ 已实现 |
| 25 | PUT | `/salaries` | 调整奖金/扣款，实发重算 | ✅ 已实现 |
| 26 | PUT | `/salaries/{id}/pay` | 标记发放（单向） | ✅ 已实现 |
| 27 | DELETE | `/salaries/{id}` | 删除待发放记录 | ✅ 已实现 |
| 28 | GET | `/users` | 账号分页（不返回摘要） | ✅ 已实现 |
| 29 | GET | `/users/{id}` | 账号单条 | ✅ 已实现 |
| 30 | POST | `/users` | 新增账号 | ✅ 已实现 |
| 31 | PUT | `/users` | 改显示名与启停 | ✅ 已实现 |
| 32 | PUT | `/users/{id}/password` | 重置口令 | ✅ 已实现 |
| 33 | DELETE | `/users/{id}` | 删除账号（两道防护） | ✅ 已实现 |
| 34 | GET | `/op-logs` | 操作审计查询（只读） | ✅ 已实现 |
| 35 | GET | `/me` | 当前登录人与权限集合 | ✅ 已实现 |
| 36 | GET | `/roles` | 角色列表 | ✅ 已实现 |
| 37 | GET | `/roles/{id}` | 角色单条 | ✅ 已实现 |
| 38 | POST | `/roles` | 新增角色 | ✅ 已实现 |
| 39 | PUT | `/roles` | 修改角色 | ✅ 已实现 |
| 40 | DELETE | `/roles/{id}` | 删除角色（内置 ADMIN 与已绑定角色拒删） | ✅ 已实现 |
| 41 | GET | `/roles/permissions` | 全部权限点（供勾选） | ✅ 已实现 |
| 42 | GET | `/roles/{id}/permissions` | 角色已分配权限 | ✅ 已实现 |
| 43 | PUT | `/roles/{id}/permissions` | 覆盖式分配权限 | ✅ 已实现 |
| 44 | GET | `/users/{id}/roles` | 某账号的角色 ID 列表 | ✅ 已实现 |
| 45 | PUT | `/users/{id}/roles` | 覆盖式分配角色（最后管理员受保护） | ✅ 已实现 |

> 第 7、8 条是指导书「登录权限拦截」的配套实现——没有登录入口就无法演示 401，故单列。
>
> **除 `/login` 与 `/health` 外，所有接口都要求已登录。** 用 curl 或 ApiFox 调试前先登录拿 Cookie：
>
> ```bash
> curl -c cookie.txt -X POST http://127.0.0.1:8080/login \
>      -H 'Content-Type: application/json' \
>      -d '{"username":"admin","password":"123456"}'
> curl -b cookie.txt http://127.0.0.1:8080/emps
> ```

### 3.1 三个容易误解的行为

**其一：`DELETE` 是逻辑删除。** `Emp.deleted` 标了 `@TableLogic`，所以 `DELETE /emps/13` 实际执行的是 `UPDATE emp SET deleted = 1 WHERE emp_id = 13 AND deleted = 0`。表现为：

- 接口层面「删掉了」——`GET /emps/13` 返回「员工不存在」；
- 数据库层面「还在」——直连查库仍能看到这一行，`deleted = 1`；
- 重复删除同一个 ID 返回影响 0 行，服务层翻译成「员工不存在或已删除」。

**其二：业务失败用 HTTP 200，认证失败用 HTTP 401。** 统一返回结构把业务结果放在 `code` 字段里，因此「员工不存在」这类失败仍是 HTTP 200，要看响应体的 `code` 判断成败。唯一的例外是未登录：拦截器返回 **HTTP 401**，因为 401 是传输层语义，ApiFox 之类的工具会据此直接标记用例失败，比埋在 body 里更容易发现。

**其三：口令不以明文存储。** `sys_user` 表存的是 `SHA-256(明文)` 的十六进制摘要，比对时把输入做同样运算再比摘要。日志切面里 `password` 字段固定输出 `***`，明文口令不会出现在任何日志中。

---

## 四、工程结构

实际形态（`✅` = 已落地，`⬜` = 未采用或待补）：

```text
ssm-emp-mis/
├── pom.xml                                      ✅ 依赖与构建，版本全部锁定
├── README.md                                    ✅
├── .gitignore                                   ✅
├── sql/init.sql                                 ✅ 建库建表 + 约束索引 + 种子数据（emp 12 条 / sys_user 1 条）
└── src/
    ├── main/java/com/lab/emp/
    │   ├── config/                              ✅ 全注解整合三件套 + 自动填充
    │   │   ├── ServletContainersInitConfig      ✅ 取代 web.xml，注册 DispatcherServlet + 编码过滤器
    │   │   ├── SpringConfig                     ✅ 根容器：Druid、MyBatis-Plus、Mapper 扫描、事务
    │   │   ├── SpringMvcConfig                  ✅ 子容器：WebMvc、跨域、静态资源、拦截器、Jackson
    │   │   └── MyMetaObjectHandler              ✅ 时间字段自动填充（挂到 GlobalConfig）
    │   ├── controller/
    │   │   ├── HealthController                 ✅ GET /health、GET /health/db（整合验收）
    │   │   ├── AuthController                   ✅ POST /login、POST /logout
    │   │   ├── EmpController                    ✅ 员工 RESTful 六接口
    │   │   ├── DeptController                   ✅ 部门六接口（含该部门员工查询）
    │   │   ├── AttendanceController             ✅ 考勤六接口（含月度汇总）
    │   │   ├── SalaryController                 ✅ 薪资七接口（生成 / 重算 / 发放）
    │   │   ├── UserController                   ✅ 账号六接口
    │   │   └── OpLogController                  ✅ 审计查询（只读）
    │   ├── service/                             ✅ Emp / Dept / Attendance / Salary / User / Auth / OpLog
    │   │   └── impl/                            ✅ 对应七个实现（事务边界所在层）
    │   ├── mapper/                              ✅ 六个 BaseMapper（Emp / Dept / SysUser / Attendance / SalaryRecord / SalaryStandard / OpLog）
    │   ├── entity/                              ✅ 七个实体，均带 JSR-303；时间字段自动填充
    │   ├── dto/                                 ✅ EmpQueryDTO、LoginDTO、AttendanceSummaryVO、SalaryGenerateResult、三个用户 DTO
    │   ├── common/                              ✅ Result：统一返回（code / message / data）
    │   │   ├── LoginUser                        ✅ 登录态载体（只放必要字段）
    │   │   ├── PasswordEncoder                  ✅ SHA-256 摘要（登录与账号管理共用）
    │   │   ├── SalaryRule                       ✅ 日薪与扣款规则（21.75 天、迟到早退 20 元）
    │   │   ├── BusinessException                ✅ 业务异常（可预期失败）
    │   │   └── GlobalExceptionHandler           ✅ @RestControllerAdvice，九类异常出口
    │   ├── aspect/                              ✅ WebLogAspect：请求日志（口令打码）
    │   │   └── AuditAspect                      ✅ 写操作落库到 op_log（REQUIRES_NEW）
    │   └── interceptor/                         ✅ LoginInterceptor：未登录返回 401
    ├── main/webapp/
    │   └── index.html                           ✅ 单页管理界面（原生 HTML/CSS/fetch，零外部依赖）
    ├── main/resources/
    │   ├── jdbc.properties                      ✅ 连接串 / 连接池参数
    │   ├── log4j.properties                     ✅ Console 输出，Mapper 包 DEBUG
    │   └── mapper/EmpMapper.xml                 未采用：查询全部走 BaseMapper 与条件构造器
    └── test/java/com/lab/emp/
        └── service/
            ├── TransactionFailureTest          ✅ 4 用例：回滚与三种静默失效对照
            └── support/TransactionFailureDemo  ✅ 故意写错的被测代码（仅测试期存在）
```

每个包目录里放了一个 `package-info.java`，用 Javadoc 声明该包的职责与依赖方向。这样做的目的是：
Java 的包本身不能被 Git 跟踪（空目录不入库），而 `package-info.java` 是 JDK 认可的正式语法，
既能占住目录，又能让 IDE 的包视图中直接看到每层的边界约定。

### 4.1 为什么是「单个 Maven 模块 + 内部分层」，不是多模块

这里要区分两个容易混淆的「模块」：

| 概念 | 本工程的做法 | 说明 |
| :--- | :--- | :--- |
| **Maven 模块**（工程结构） | **1 个**：整个仓库就是一个 `war` 工程 | 一次 `mvn package` 产出一个 `ssm-emp-mis.war`，部署一次即可运行 |
| **代码分层**（包结构） | **9 个包**：`config / controller / service / mapper / entity / dto / common / aspect / interceptor` | 逻辑分层，编译后都在同一个 war 里 |

多模块 Maven（`<packaging>pom</packaging>` 的父工程 + 若干 `jar` 子模块）解决的是**构件复用**问题：当 `common` 需要以 jar 形式被别的项目依赖时才值得拆。本工程是单一可部署产物，拆开只会带来三个坏处——老师克隆后根目录没有可运行工程、全注解整合的装配链被拆散到多个 pom、`mvn package` 需要先 `install` 父工程。**所以保持单模块。**

> 换句话说：「项目的全部」= 这个仓库里的所有内容（源码、SQL、配置、文档、Git 历史）。
> 它编译出的**唯一**产物是 `ssm-emp-mis.war`，没有第二个 jar 需要单独部署。

依赖方向严格单向：

```text
entity ← mapper ← service ← controller
                ↑
        config（数据源 / 事务 / 拦截器）
```

### 4.2 数据库设计：`ssm_emp.emp`

```sql
CREATE TABLE emp (
    emp_id      INT UNSIGNED    NOT NULL AUTO_INCREMENT  COMMENT '员工ID',
    emp_name    VARCHAR(50)     NOT NULL                 COMMENT '员工姓名',
    gender      CHAR(1)         NOT NULL DEFAULT '男'     COMMENT '性别：男/女',
    dept        VARCHAR(50)     NOT NULL                 COMMENT '所属部门',
    post        VARCHAR(50)         NULL                 COMMENT '职位',
    salary      DECIMAL(10, 2)  NOT NULL DEFAULT 0.00    COMMENT '月薪（元）',
    hire_date   DATE            NOT NULL                 COMMENT '入职日期',
    status      TINYINT         NOT NULL DEFAULT 1       COMMENT '状态：1 在职 / 0 离职',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT         NOT NULL DEFAULT 0       COMMENT '逻辑删除：0 正常 / 1 已删除',
    PRIMARY KEY (emp_id),
    KEY idx_emp_name (emp_name),
    KEY idx_emp_dept_status (dept, status),
    CONSTRAINT chk_emp_gender CHECK (gender IN ('男', '女')),
    CONSTRAINT chk_emp_salary CHECK (salary >= 0),
    CONSTRAINT chk_emp_status CHECK (status IN (0, 1)),
    CONSTRAINT chk_emp_deleted CHECK (deleted IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
```

三个设计决定值得说明：

1. **`status` 与 `deleted` 是两个维度。** `status` 是业务状态（在职 / 离职），员工本人可见，影响业务查询；`deleted` 是逻辑删除标记，只影响本系统看到的数据，用于保留历史。合并成一个字段会导致「离职」和「被删除」无法区分。
2. **金额用 `DECIMAL(10,2)`，不用 `FLOAT`/`DOUBLE`。** 浮点在 0.1 + 0.2 这类运算上会产生 `0.30000000000000004`，薪资字段一旦出现这种误差，对账就永远对不平。Java 侧对应 `BigDecimal`。
3. **约束下沉到数据库。** 应用层的校验可以被绕过（直接连库、脚本导入、AI 生成的代码漏掉校验），CHECK 约束是最后一道闸门。实测三条都会被拒：
   - 负薪资 → `ERROR 3819 ... chk_emp_salary is violated`
   - `status = 9` → `ERROR 3819 ... chk_emp_status is violated`
   - `gender = 'X'` → `ERROR 3819 ... chk_emp_gender is violated`

索引验证（`EXPLAIN`，`like '张%'`）：

```text
type=range  key=idx_emp_name  key_len=202  rows=2  Extra=Using index condition
```

前导模糊走索引，`like '%张%'` 不走——这是 B+ 树的结构决定的，不是配置问题。分页多条件查询里如果要对姓名做前后模糊，需要接受全表扫描的代价，或者改用前缀匹配。

**种子数据 12 条**，覆盖 5 个部门、2 名离职员工；「张 / 王 / 刘 / 陈」各 2 人，用来验证姓名模糊查询不会只命中一条；12 条配 `size=5` 正好演示 3 页。

### 4.3 登录用户表：`ssm_emp.sys_user`

```sql
CREATE TABLE sys_user (
    user_id       INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username      VARCHAR(50)  NOT NULL                COMMENT '登录名',
    password_hash CHAR(64)     NOT NULL                COMMENT 'SHA-256(明文) 的小写十六进制',
    display_name  VARCHAR(50)  NOT NULL                COMMENT '显示名',
    status        TINYINT      NOT NULL DEFAULT 1      COMMENT '1 启用 / 0 停用',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_sys_user_username (username),
    CONSTRAINT chk_sys_user_status CHECK (status IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

演示账号：`admin` / `123456`（库中存的是 `8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92`）。

三点说明：

1. **为什么不复用 `emp` 表做登录**：`emp` 是业务数据（员工档案），`sys_user` 是系统身份（能否进系统）。两者生命周期和权限边界都不同——员工离职不一定注销系统账号，管理员也不一定是员工。混在一张表里，后面做权限时必然要拆。
2. **为什么存摘要而不是明文**：数据库导出、备份、慢查询日志都可能带出表内容。存摘要后，即使这些数据外泄也拿不到原始口令。
3. **为什么选 SHA-256 而不是 BCrypt**：本项目只依赖 JDK 标准库，不加额外依赖；SHA-256 是快速散列，**抗彩虹表能力弱于 BCrypt/Argon2**，真实系统不应这样用。这一点在 `AuthServiceImpl` 的类注释里也写明了——知道边界比假装没有边界更重要。

### 4.4 部门表：`ssm_emp.dept`

```sql
CREATE TABLE dept (
    dept_id     INT UNSIGNED NOT NULL AUTO_INCREMENT,
    dept_name   VARCHAR(50)  NOT NULL,          -- 与 emp.dept 对应
    location    VARCHAR(50)  NOT NULL DEFAULT '',
    status      TINYINT      NOT NULL DEFAULT 1,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (dept_id),
    UNIQUE KEY uk_dept_name (dept_name),
    CONSTRAINT chk_dept_status CHECK (status IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

6 条种子数据，前 5 条与 `emp` 中实际出现的部门一致，「测试部」刻意留空，用于演示「无员工可删、有员工拒删」的对照。

**关联方式：靠部门名称，不用外键。**

| 方案 | 代价 |
| :--- | :--- |
| 加 `emp.dept_id` 外键 | 更规范，但要改 `emp` 表、六个员工接口、77 条用例与 `openapi.yaml` |
| **按名称关联（当前）** | 零改动；代价是部门改名必须级联刷新 `emp.dept`，否则关联断裂 |

选了后者，并用两处代码把这个代价兜住：

1. `DeptServiceImpl#remove`：部门下还有员工时拒绝删除，避免员工指向不存在的部门；
2. `DeptServiceImpl#update`：改名时在同一事务内 `UPDATE emp SET dept = 新名 WHERE dept = 旧名`。

第 2 条是整个模块唯一必须放进事务的操作——改部门名与刷员工部门要么都成功，要么都不做。这也让部门与员工两个模块真正产生了耦合，而不是两张各管各的表。

`status` 不是装饰字段：**停用部门不能再分配员工**，写入时会返回「部门已停用，不能分配员工」。否则「停用」只影响显示，列表里照样能挂人，按部门做的统计也会把已撤销的部门算进去。

### 4.5 考勤如何影响薪资

这是五个业务域里唯一一条「模块间业务规则」，也是本项目与「五张表各配一套 CRUD」的分界线。

**考勤状态由打卡时间推导，不由人填**（`AttendanceServiceImpl#deriveStatus`）：

| 打卡情况 | 推导结果 |
| :--- | :--- |
| 上下班都不填 | 缺勤（3） |
| 上班晚于 09:00 | 迟到（1） |
| 下班早于 18:00 | 早退（2） |
| 其余 | 正常（0） |

同时满足迟到与早退时按**迟到**记。客户端传来的 `status` 一律忽略——状态是对事实的判定结论，允许手选就会出现「09:35 打卡却标正常」的自相矛盾，而出勤率与扣款都建立在这个字段上。

**薪资生成时按当月考勤算扣款**（`SalaryRule`）：

```
日薪   = 月基本工资 ÷ 21.75      （法定月计薪天数）
扣款   = 缺勤天数 × 日薪 + 迟到次数 × 20 + 早退次数 × 20
实发   = 基本工资 + 奖金 − 扣款     （扣款上限为基本工资，不出现负数）
```

实测：员工 base=7800、当月缺勤 1 天 + 迟到 2 次 + 早退 1 次 → 日薪 358.62，扣款 358.62+40+20=**418.62**，实发 7381.38；全勤员工扣款为 0。

规则集中在 `common/SalaryRule` 一处，因为散落的魔法数字（21.75、20）过两个月没人说得清依据。**这不等于真实薪酬方案**——真实场景还有社保公积金、个税累进、加班费折算，本系统一概没有；实现的是「考勤影响薪资」这条链路本身。

### 4.6 薪资标准：为什么不能直接用 `emp.salary`

`emp.salary` 只保存**当前**薪资。若拿它补生成历史月份，调过薪的人历史账目会用新数额——例如 11 月把张伟从 18500 调到 20000，再补生成 9 月，就会按 20000 算，多出 1500。薪资恰恰是最不能事后改的东西。

因此单列 `salary_standard`：

```sql
CREATE TABLE salary_standard (
    std_id         INT UNSIGNED   NOT NULL AUTO_INCREMENT,
    emp_id         INT UNSIGNED   NOT NULL,
    base           DECIMAL(10, 2) NOT NULL,
    effective_from DATE           NOT NULL,   -- 生效日期（含）
    create_time    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (std_id),
    UNIQUE KEY uk_std_emp_effective (emp_id, effective_from)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

**写入时机**（由 `EmpServiceImpl` 自动处理，不开放独立接口）：

| 场景 | 生效日 |
| :--- | :--- |
| 新增员工 | 入职日期 |
| 修改薪资 | **当月 1 日**（同月重复调薪覆盖，不留两条） |

**读取规则**（`SalaryServiceImpl#loadBaseByMonth`）：取 `effective_from <= 该月最后一天` 的最后一条；查不到时回退 `emp.salary`。

实测对照：

```
张伟 18500（标准 2021-03-15 生效）
11-24 调薪到 20000  →  新增标准 2026-11-01 = 20000
补生成 2026-09      →  base=18500（用当时的标准）
生成 2026-12        →  base=20000
```

为什么生效日取「当月 1 日」而不是「今天」：薪资按月核算，月中改薪需要按日折算，那套规则本工程未实现；取 1 日意味着「本月起按新标准」，语义简单且不会在月内出现两套基准。

### 4.7 权限模型：从「登录了」到「能不能做这件事」

登录只解决身份，权限解决授权。两者分开实现：

| 层 | 组件 | 回答的问题 | 失败响应 |
| :--- | :--- | :--- | :--- |
| 拦截器 | `LoginInterceptor` | 有没有登录 | 401 |
| 切面 | `PermissionAspect` | 这个登录的人能不能调这个接口 | 403 |

**四张表**：`role`（角色）、`permission`（权限点）、`user_role`（账号-角色）、`role_permission`（角色-权限）。权限点编码形如 `emp:write`，与注解取值一一对应：

```java
@RequirePermission("emp:write")
@PostMapping
public Result<Void> add(@RequestBody @Valid Emp emp) { ... }
```

**为什么用切面而不是拦截器**：拦截器只能按 URL 前缀猜意图（`/emps` 的 POST 要写权限），规则与路由耦合，新增接口时漏改就是越权。注解跟着方法走，看得见、改得动。

**为什么权限每次查库、不放进 session**：撤权必须立即生效。实测流程是：管理员清空 HR 角色的权限 → 该账号下一次请求立刻 403；恢复后立刻 200。缓存在会话里会出现「已撤权但还能用」，这在权限系统里是最危险的错误方向。

**三条防呆**：内置 `ADMIN` 角色不可删；仍有账号绑定的角色不可删；不允许把最后一个管理员改成别的角色（一旦没有管理员，系统内没有任何后门可以改回来）。

**界面的角色**：`GET /me` 返回权限集合，前端据此隐藏导航与写操作按钮。这只是体验收敛——**隐藏按钮不等于限制调用**，越权请求仍会被切面拒绝并返回 403。

种子角色：`ADMIN`（12 个权限点全量）、`HR`（员工/部门/考勤/薪资 8 个）、`VIEWER`（只读 5 个）。示例账号 `hr01`、`viewer01`，口令均 `123456`。

---

## 五、构建与运行

### 5.1 环境要求

| 依赖 | 要求 | 检查命令 |
| :--- | :--- | :--- |
| JDK | 17（Temurin 17.0.21 已验证） | `java -version` |
| Maven | 3.9+ | `mvn -v`（输出的 Java version 应为 17） |
| MySQL | 8.0，端口 3306 | `mysql -uroot -p -e "select version()"` |
| Tomcat | 9.0.x（**9.0.123 已验证**） | `bin/version.sh` |

### 5.2 初始化数据库与构建

```bash
# 1) 建库建表 + 约束索引 + 种子数据
#    必须显式指定 utf8mb4，否则中文会按 latin1 二次编码成乱码
docker exec -i dev-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 < sql/init.sql

# 2) 只解析依赖（最快的一道健康检查）
mvn -B dependency:resolve

# 3) 打包成 war，产物 target/ssm-emp-mis.war
mvn -B clean package
```

`init.sql` 可重复执行：先按依赖顺序 `DROP TABLE` 再重建，重复导入不会报错，行数恒为 12。

### 5.3 部署到 Tomcat 9 与接口基址

把 `target/ssm-emp-mis.war` 丢进 Tomcat 9 的 `webapps/`，或在 IDEA 里配置 Tomcat Server 并添加该 war 的 deployment。

**关键一步：把 Application context 设为 `/`。** 否则默认上下文是 `/ssm-emp-mis`，所有接口前面会多一段前缀：

| Application context | 新增员工的完整 URL | 与指导书的匹配度 |
| :--- | :--- | :--- |
| `/`（推荐） | `http://localhost:8080/emps` | ✅ 与 `POST /emps` 逐字一致 |
| `/ssm-emp-mis`（默认） | `http://localhost:8080/ssm-emp-mis/emps` | ⚠️ 需在报告里额外说明前缀来源 |

> 这也是「war 名与仓库名一致」的代价——名字长，但换来仓库名、构件名、产物名三者统一。上下文路径在 Tomcat 里是一行配置，可以随时压平。

### 5.4 打开管理界面

启动后浏览器访问 `http://localhost:8080/`，即为单页管理界面：

| 项 | 说明 |
| :--- | :--- |
| 源码 | `src/main/webapp/index.html`，单文件、零外部依赖（无 CDN、无前端构建） |
| 技术 | 原生 HTML + CSS + `fetch`，直接调本工程 REST 接口 |
| 覆盖 | 员工 / 部门 / 考勤 / 薪资 / 账号 / 审计六个页面，含分页、筛选、弹窗表单 |
| 登录 | 默认 `admin / 123456`；会话仍由 `LoginInterceptor` 校验，未登录访问接口返回 401 |

界面只是接口的可视化外壳，业务规则全部在服务端：例如「部门下仍有员工不可删」「已发放薪资不可改」在页面上也会被后端拒绝并弹提示。

本地验证采用的方式（与上表等价，可复现）：

```bash
# 解压版 Tomcat，无需安装、无需 sudo
curl -LO https://dlcdn.apache.org/tomcat/tomcat-9/v9.0.123/bin/apache-tomcat-9.0.123.tar.gz
tar -xzf apache-tomcat-9.0.123.tar.gz -C ~/opt

# 把 war 以 ROOT 名字部署，上下文路径即为 "/"
mv ~/opt/apache-tomcat-9.0.123/webapps/ROOT ~/opt/apache-tomcat-9.0.123/webapps/ROOT.default
cp target/ssm-emp-mis.war ~/opt/apache-tomcat-9.0.123/webapps/ROOT.war

~/opt/apache-tomcat-9.0.123/bin/catalina.sh run   # 前台运行，日志直接打到终端
```

**关于 `--add-opens`：实测不需要。** JDK 17 的强封装理论上会让反射密集的库报 `InaccessibleObjectException`，本工程实测（清空 `JDK_JAVA_OPTIONS` 与 `CATALINA_OPTS` 后启动）Spring 5.3.31 + MyBatis-Plus 3.5.3.1 + Druid 1.2.20 + Hibernate Validator 6.2.5 组合启动与查库均正常。原本 `pom.xml` 的 surefire 配置里预置了两条 `--add-opens` 作兜底，移除后 `mvn test` 4 个用例同样全绿，因此已删除——无效配置留着只会让后来者以为「必须加」。`argLine` 仅保留 `-Dfile.encoding=UTF-8`，它的作用是在默认编码非 UTF-8 的环境（如 Windows 的 GBK）下保证中文断言不乱码。

### 5.5 目前验证到哪一步

分四组。全部为实际执行结果，未通过的项留在表内并标注 ⬜。

**一、构建与数据库**

| 检查项 | 结果 |
| :--- | :--- |
| `mvn -B dependency:resolve`（JDK 17.0.21 / Maven 3.9.12） | ✅ BUILD SUCCESS，38 个构件全部解析 |
| `mvn -B clean package` | ✅ BUILD SUCCESS，产出 `target/ssm-emp-mis.war`（21.4 MB） |
| `sql/init.sql` 首次导入 | ✅ 12 行数据落库，中文无乱码 |
| `sql/init.sql` 重复导入 | ✅ 幂等，行数仍为 12（先 DROP 再 CREATE） |
| CHECK 约束（负薪资 / 非法 status / 非法 gender） | ✅ 三条均被拒，`ERROR 3819` |
| `EXPLAIN ... like '张%'` | ✅ `key=idx_emp_name`，`type=range` |
| 十个 `package-info.java` 编译 | ✅ 对应 `.class` 全部生成 |
| `Result` / `Emp` 序列化 | ✅ 金额保两位小数，`hireDate` 输出 `"2021-03-15"`，`deleted` 被 `@JsonIgnore` 屏蔽 |
| JSR-303 校验 | ✅ 空实体 6 条违规；负薪资、`gender=X` 均被拒 |

**二、接口行为**

| 检查项 | 结果 |
| :--- | :--- |
| `GET /health` | ✅ `{"code":200,"message":"success","data":"UP"}` |
| `GET /health/db` | ✅ `data=12`，与 `select count(*)` 一致 |
| 未知路径 / 根路径 / `POST /health` | ✅ 分别返回 404、404、405 |
| `GET /emps/abc`（路径变量非数字） | ✅ HTTP 400，「参数格式不正确：id」 |
| `GET /emps?page=1&size=5` | ✅ `total=12`、`pages=3`、`records=5`、ID 倒序 |
| `GET /emps?page=3&size=5` | ✅ 末页 2 条（ID 2、1） |
| `GET /emps?name=张&dept=研发部` | ✅ 命中 2 条（张敏、张伟） |
| `GET /emps`（`name="   "`） | ✅ 空白按未传处理，返回 12 条 |
| `GET /emps/1` | ✅ 字段完整，`hireDate` 为字符串，无 `deleted` |
| `GET /emps/9999` | ✅ `code=500`，「员工不存在：9999」 |
| `POST /emps` | ✅ 插入成功，自增 ID = 13 |
| `PUT /emps` | ✅ 职位与薪资更新生效（12888.00） |
| `DELETE /emps/13` | ✅ 接口查不到；数据库物理行仍在且 `deleted=1` |
| 重复 `DELETE /emps/13` | ✅ 「员工不存在或已删除」 |
| `DELETE /emps`（批量 2 条） | ✅ `affected=2` |
| `POST /emps`（空姓名 + 负薪资） | ✅ 「员工姓名不能为空；薪资不能为负数」 |
| `POST /emps`（非法 JSON） | ✅ 「请求体格式错误，请检查 JSON 结构与字段类型」 |
| `DELETE /emps`（空数组） | ✅ 「批量删除的 ID 列表不能为空」 |

**三、安全与会话**

| 检查项 | 结果 |
| :--- | :--- |
| 未登录 `GET /emps` | ✅ **HTTP 401**，body `{"code":401,...}` |
| 未登录 `GET /health` | ✅ HTTP 200（放行名单生效） |
| `POST /login` 错误口令 | ✅ 「用户名或密码错误」 |
| `POST /login` 空用户名 | ✅ 「用户名不能为空」（校验链路） |
| `POST /login` 正确口令 | ✅ `code=200`，返回 `displayName=系统管理员`，下发 `JSESSIONID` |
| 带 Cookie `GET /emps` | ✅ HTTP 200，`total=12` |
| 带 Cookie `POST /emps` | ✅ HTTP 200，插入成功 |
| `POST /logout` 后再访问 | ✅ 回到 HTTP 401 |

**四、运行环境与测试**

| 检查项 | 结果 |
| :--- | :--- |
| Tomcat 9.0.123 部署启动 | ✅ 8080 监听，无 `SEVERE`、无 `BeanCreationException` |
| 无 `--add-opens` 启动 | ✅ 清空相关环境变量后仍正常启动并查库 |
| AOP 请求日志 | ✅ `[REQ] AuthController#login 入参=[LoginDTO{username=admin, password=***}]` + `[RESP] 耗时` |
| 日志中明文口令计数 | ✅ `grep -c 123456` = 0 |
| `mvn -B test` | ✅ Tests run: 4, Failures: 0, Errors: 0 |
| 事务失效三场景 | ✅ 见 [5.6](#56-事务失效三场景实验)，三个「不回滚」与一个「回滚」同时成立 |
| GitHub Actions CI | ✅ run `43783591630`，62 秒通过：建库导入 SQL、4 用例、war 打包全部成功 |
| 字段自动填充 | ✅ INSERT 语句含 `create_time, update_time`；UPDATE 的 SET 子句含 `update_time`（改前由数据库 `ON UPDATE` 兜底，不出现在 SQL 里） |
| 列表排序参数 | ✅ `sortBy` 白名单（empId / empName / salary / hireDate）+ `sortDir`；注入尝试 `sortBy=empName;drop table emp--` 回落默认且表完好；默认排序未变 |
| 部门模块 | ✅ 13 个场景通过：列表 6 条、名称过滤、单条、不存在、该部门 4 名员工、新增、重名拒绝、空名拒绝、改名级联（4 条员工同步、旧名 0 残留）、有员工拒删、空部门可删、改回复原 |
| 考勤 → 薪资联动 | ✅ 考勤状态由打卡时间推导（9:00/18:00 为界）；薪资生成扣款与考勤一致（缺勤 1 天 + 迟到 2 + 早退 1 → 扣 418.62，全勤为 0）；`recalculate` 可把补录的考勤补算进待发放记录 |
| 停用部门 | ✅ 分配员工返回「部门已停用，不能分配员工」 |
| 薪资标准（生效日期） | ✅ 张伟 18500→20000 后：补生成 2026-09 用 `base=18500`，生成 2026-12 用 `base=20000`；同月再调薪标准仍只 1 条；新员工写入职日标准 |
| 薪资重算 | ✅ 补录缺勤后 `recalculate` 使张伟扣款变为 850.57（18500÷21.75）；对已发放的 2026-10 返回 `generated=0, skipped=6`，未改动 |
| 考勤日期校验 | ✅ 未来日期 2027-06-01 被拒（`考勤日期不能晚于今天`），当天正常通过 |
| 单页界面 | ✅ 浏览器实操：登录、员工增删改、部门「查看员工」、考勤月度汇总、薪资生成与重算、审计列表均正常；XSS 载荷 `<img onerror>` 原样显示为文本 |
| 权限模型 | ✅ 三角色 17 权限点：viewer 写员工 403、hr 访问角色 403、admin 全通；清空 HR 权限后立即 403，恢复后立即 200；最后管理员降级被拒 |
| 响应耗时 | ✅ 查询 11–68 ms（首次含预编译）、写操作 60–96 ms；连续 60 次查询 0 失败，并发 10 请求全 200 |

### 5.6 事务失效三场景实验

指导书要求验证「异常被吞掉 / 自调用 / private 方法」三种事务失效。三个场景的共同特征是**静默失效**：不报错、不抛业务异常、日志正常，只有数据不对。因此断言不能看「方法有没有抛异常」，只能看「库里那一行还在不在」。

四个用例放在 `TransactionFailureTest`，被测代码 `TransactionFailureDemo` 位于 `src/test/java`——**故意写错的代码不进生产产物**，只在测试期由包扫描装配。

| 用例 | 破坏的前提 | 现象 | 断言 |
| :--- | :--- | :--- | :--- |
| A 对照 | 无（public 方法 + 外部经代理调用 + 异常未捕获） | 正常回滚 | 查库 **0 行** |
| B 吞异常 | 事务管理器收不到异常信号 | 插入被提交 | 查库 **1 行** |
| C 自调用 | `this.inner()` 未经过代理 | 内层注解不生效 | 查库 **1 行** |
| D private 方法 | 代理无法覆写 private，注解被静默忽略 | 注解不生效 | 查库 **1 行** |

**为什么 A 必须存在**：只写 B/C/D 无法排除「整个事务机制压根没配好」这种可能——那时四个用例都会「恰好」留下数据。A 证明了配置是对的，B/C/D 才成立。反过来，B/C/D 证明了插入是真的写进库了，A 的 0 行才不是「插入失败」的假象。两组互相校验。

对应的根因归到一句：

- **B** — `@Transactional` 的回滚触发条件是「方法向外抛出异常」，异常被吃掉等于告诉事务管理器一切正常；
- **C** — `this` 指向真实对象而非代理，调用链里根本没有事务拦截器；
- **D** — `AnnotationTransactionAttributeSource` 默认只处理 public 方法，非 public 上的注解直接返回 null（同理 `final`、`static` 方法也不生效）。

> **这个实验顺带暴露了一个生产缺陷。** 首次跑测试时报 `IllegalStateException: No ServletContext set`：`SpringConfig` 的组件扫描把同包的 `SpringMvcConfig`（它本身也是 `@Configuration`）一起扫进了根容器，于是根容器照着 `@EnableWebMvc` 又建了一整套 `HandlerMapping` / `HandlerAdapter`。Web 环境下因为有 ServletContext 而不报错，只是白白多建一套组件——**问题一直存在，直到纯容器测试才暴露**。修复方式是在根容器扫描时按类型排除 `SpringMvcConfig`。修复后回归部署，`/health/db`、登录与列表接口均正常。

### 5.7 故障排查手册

开发与部署期间实际踩过的问题（Tomcat 启动即退出、502 来自代理、日期被序列化成数组、`No ServletContext set`、事务静默不回滚、405 被兜底处理器吞掉等），按「现象 → 根因 → 处理」逐条记录在 **[docs/troubleshooting.md](docs/troubleshooting.md)**。只收录实测复现过的条目。

> 这两条验证只证明「依赖选型与构建链在 JDK 17 上成立」，不代表功能可用。功能进度见 [七、路线图](#七路线图任务-27)。

---

## 六、评分点与工程产出的对照

实验二报告满分 100 分，四个维度：环境与功能 40 + 问题排查 20 + AI 规范 20 + 报告质量 20。

| 评分要点 | 本工程的落点 | 可提供的证据 |
| :--- | :--- | :--- |
| 全注解整合正确 | 三个 `@Configuration` 类 + `ServletContainersInitConfig` | 启动日志、父子容器 Bean 数量、`web.xml` 缺失截图 |
| 三层架构职责清晰 | `controller / service / mapper` 单向依赖，DTO 隔离入参 | 包结构截图、接口清单 |
| 增删改查 + 模糊 + 分页 | `EmpController` 六个端点 | ApiFox 用例集、响应截图 |
| 批量删除 | `DELETE /emps` + `empMapper.deleteBatchIds` | 用例：正常、空数组、含不存在 ID |
| JSR-303 校验 | 实体注解 + `@Valid`；失败由 `GlobalExceptionHandler` 捕获 `BindException` 统一返回，方法签名不接 `BindingResult` | 用例：空姓名、负薪资、超长字段 |
| 全局异常处理 | `@RestControllerAdvice`，6 个处理器（业务 / 校验 / 报文不可读 / 405 / 400 / 兜底） | 用例：不存在的 ID、未登录、方法用错 |
| 统一返回格式 | `Result<T>` | 所有响应的 `code/message/data` 截图 |
| 请求日志记录 | `WebLogAspect` 环绕通知 | 控制台 `[REQ]/[RESP]/[ERR]` 日志 |
| 登录权限拦截 | `LoginInterceptor` + `/login` | 用例：未登录 401、登录后 200 |
| 声明式事务 | `@Transactional(rollbackFor)` | 三个失效场景的现象对比表 |
| 过程可追溯 | 14 条提交信息 + `docs/troubleshooting.md` | 每次变更的动机、踩过的坑与修复方式 |

**一票否决红线**（来自评分标准第七节，本工程全程规避）：

- 直接复制 AI 生成的完整项目 → 0 分；
- 未保留 AI 交互调试记录与修改痕迹 → 维度三 0 分；
- 硬编码真实账号口令 → 维度三 0 分。故 `jdbc.properties` 中密码支持环境变量覆盖，见 [9.3](#九已知待解决的设计问题)。

---

## 七、路线图（任务 2–7）

- [x] **任务 2** 工程初始化：`pom.xml` 版本锁定、`.gitignore`、目录骨架、JDK 17 构建链验证、首次提交
- [x] **任务 3** 全注解整合三件套 + 整合验证（`/health` 与 `/health/db` 已在 Tomcat 9.0.123 上跑通）
- [x] **任务 4** `sql/init.sql`：`emp` 表 + 索引 + CHECK 约束 + 12 条种子数据，已导入验证
- [x] **任务 4** 数据契约层：`Emp` / `EmpQueryDTO` / `EmpMapper` / `Result`，序列化与校验已冒烟验证
- [x] **任务 4（剩余）** `EmpService` + `EmpServiceImpl`（分页多条件）+ `BusinessException` + `GlobalExceptionHandler`
- [x] **任务 5** 分页多条件查询 + RESTful 六接口，已用 curl 逐条验证
- [x] **任务 6（部分）** 登录拦截（`sys_user` + `LoginInterceptor` + `/login`、`/logout`）与 AOP 请求日志，401/口令打码已验证
- [x] **任务 6（剩余）** 声明式事务验证 + **事务失效三场景实验**（吞异常 / 自调用 / private 方法），4 个 JUnit 用例全绿
- [x] **任务 7-1** 接口用例集：`docs/test-cases.md` 91 条，实际结果均为实测
- [x] **任务 7-2** 企业开发规范清单：`docs/engineering-checklist.md` 31 条，另列 7 项未做到
- [x] **任务 7-3** Git tag：`v1.0.0` → `v1.0.1` → `v1.1.0`，均为附注 tag 并已推送
- [ ] **任务 7-4（外部动作）** ApiFox 录入与导出：用例文档已就绪，需在客户端逐条录入后导出测试记录

---

## 八、与实验一的关系

| 维度 | 实验一（`mybatis-lab`） | 实验二（本仓库） |
| :--- | :--- | :--- |
| 形态 | 纯 Java SE，无容器 | war + Tomcat 9，Spring 父子容器 |
| 装配 | `MyBatisUtil` 手工 `new SqlSessionFactory` | Spring `@Configuration` + Bean 生命周期 |
| 事务 | `SqlSession.commit/rollback` 显式控制 | `@Transactional` 声明式 + AOP 代理 |
| 并发 | 条件 UPDATE 原子扣减 + 行锁 | 同左（`emp` 场景弱化，重点转向事务失效） |
| 接口 | 无（JUnit 即界面） | RESTful + JSON |
| JDK | 11 | **17** |
| 日志 | reload4j 单实现 | 同左（延续同一决策） |

**必须继承的经验**（实验一踩过的坑，实验二不重复踩）：

1. MyBatis-Plus 的装配必须用 MP 自己的构建入口。实验一是 `MybatisXMLConfigBuilder` 而非 `XMLConfigBuilder`；实验二对应的是 `MybatisSqlSessionFactoryBean` 而非原生 `SqlSessionFactoryBean`——用错则 `BaseMapper` 的通用 CRUD 全部失效。
2. 任何含中文字面量的 `mysql` 命令都要加 `--default-character-set=utf8mb4`，否则中文按 latin1 二次编码成乱码。
3. 金额一律 `BigDecimal` ↔ `DECIMAL`，断言用 `setScale(2).toPlainString()` 比较，不用 `double`。
4. 时间断言留 1 秒容差：`DATETIME` 秒级 + 驱动毫秒四舍五入，写死相等必然偶发失败。

---

## 九、曾经的设计决策与结论

开发初期悬而未决、后来已经定下来的四件事。保留当时的纠结过程，是因为它比单纯的结论更能说明取舍依据——报告里的「问题与解决方案」正好需要这种材料。

### 9.1 数据库要从零建，不能「沿用实验一」

指导书写「数据库沿用实验一的 `ssm_emp` 库与 `emp` 表」，但实验一实际建的是 `ssm_mall` 库 + `tb_user / tb_product / tb_order`（数码商城三表），**不存在 `ssm_emp` 与 `emp`**。因此 `sql/init.sql` 需完整自建，字段按指导书附录 A 的提示取：

```text
emp_id(PK) / emp_name / gender / dept / post / salary / hire_date / status
```

**结论**：已自建完整脚本（建库、建表、4 条 CHECK、2 个索引、12 条种子数据），幂等性实测——连续导入两次行数恒为 12。

### 9.2 Mapper 用注解还是 XML

两种都符合要求。当时的倾向是「主用 `BaseMapper` + 条件构造器，仅对动态 SQL 保留一份 XML」，理由是实验一已充分展示 XML 动态 SQL，实验二的增量在 Spring 整合。

**结论**：**全部使用 `BaseMapper` + `LambdaQueryWrapper`，仓库中没有 XML 映射文件**。多条件分页、模糊查询、白名单排序都能用构造器表达，写 XML 属重复劳动。

### 9.3 密码不能硬编码

`jdbc.properties` 中的密码支持环境变量 `EMP_DB_PASSWORD` 覆盖，默认值仅用于本地开发。评分标准把「硬编码真实账号口令」列为维度三计 0 分的违规项。

**结论**：已按此实现（见 `SpringConfig` 的数据源 Bean）；`.gitignore` 另排除 `jdbc-local.properties` 与 `.env`。

### 9.4 事务失效三场景如何留证

指导书要求验证「异常被 try-catch 吞掉 / `this` 自调用 / 注解加在 private 方法上」。三者都是**静默失效**——不报错、不抛异常，只能靠数据比对发现。

**结论**：已实现为 `TransactionFailureTest` 的 4 个用例（A 对照组 + B/C/D 三种失效），断言只看「库里那一行还在不在」，不看方法有没有抛异常。详见 [5.6](#56-事务失效三场景实验)。

---

## 十、变更记录

| 日期 | 内容 |
| :--- | :--- |
| 2026-10-31 | 初始化仓库：`pom.xml`（JDK 17 + Spring 5.3.31 + MP 3.5.3.1，版本全部锁定）、`.gitignore`、本 README；在 JDK 17.0.21 上完成 `dependency:resolve` 与空 war 打包验证 |
| 2026-10-31 | 项目定名 `ssm-emp-mis`（技术栈 + 领域 + 系统类型三段式），仓库名 / 构件名 / 产物名三者统一；确认采用「单 Maven 模块 + 内部分层包」结构 |
| 2026-10-31 | 建立 `src` 十层包骨架（每包一份 `package-info.java` 声明职责）、`jdbc.properties`、`log4j.properties`；`sql/init.sql` 完成 `emp` 表、2 个索引、4 个 CHECK 约束与 12 条种子数据，导入与幂等性已验证 |
| 2026-11-01 | 补充 `.gitattributes`（行尾归一与文本/二进制类型声明）与 `.editorconfig`（缩进、编码、末尾换行）；README 增加目录导航 |
| 2026-11-06 | 补齐数据契约层：`Result` 统一返回、`Emp` 实体（表映射 + JSR-303）、`EmpQueryDTO`（分页边界收敛）、`EmpMapper`（BaseMapper）；pom 增加 `jackson-datatype-jsr310`，序列化与校验行为已冒烟验证 |
| 2026-11-07 | 完成全注解整合：`ServletContainersInitConfig` / `SpringConfig` / `SpringMvcConfig` + `HealthController`；在 Tomcat 9.0.123 + JDK 17 上部署验证，`/health` 与 `/health/db` 均返回 200，确认 `BaseMapper` 注入与数据源连通 |
| 2026-11-08 | 补齐业务层与接口：`EmpService` / `EmpServiceImpl`（分页多条件、声明式事务）、`EmpController` 六接口、`BusinessException` 与 `GlobalExceptionHandler`；修正 `LocalDate` 被序列化成数组的问题，八项接口场景验证通过 |
| 2026-11-09 | 加入登录权限拦截与请求日志：`sys_user` 表（口令存 SHA-256 摘要）、`AuthController`、`LoginInterceptor`（未登录 401）、`WebLogAspect`（口令打码）；切面须与 Controller 同处子容器，已在扫描边界上做排除 |
| 2026-11-10 | 补首批 JUnit 用例：`TransactionFailureTest` 四例验证事务回滚与三种静默失效；顺带修复根容器误扫 `SpringMvcConfig` 导致的重复 WebMvc 装配，回归部署通过 |
| 2026-11-11 | 新增 `docs/troubleshooting.md`（八条实测故障的排查记录）；`.gitignore` 补齐 JVM 崩溃产物（`hs_err_pid*`、`*.hprof`）与打包残留 |
| 2026-11-12 | 文档修订：目录补两条缺失锚点、`5.4` 验证表按四组重排、排查手册加分类索引、修正一处「声称存在 `docs/ai-log/` 但实际没有」的不实表述；补齐 `docs/` 下三份交付文档，打 tag `v1.0.0` |
| 2026-11-13 | 新增 `docs/test-cases.md`（31 条用例，实际结果全部来自实测）；用例回归时发现并修复兜底异常处理器吞掉 405/400 语义的缺陷 |
| 2026-11-14 | 新增 `docs/openapi.yaml`（OpenAPI 3.0.3，6 条路径 10 个操作，`$ref` 全部可解析）；顺带修正文档中「8 个端点」的计数错误 |
| 2026-11-15 | 排查手册补第 9 条（兜底异常处理器吞掉 405/400 的根因与判据）；README 头部补版本行、修正「状态」为已完成，发布 `v1.0.1` |
| 2026-11-16 | 接入 GitHub Actions：MySQL 8 service + 导入 `init.sql` + `mvn clean verify`，失败上传 surefire 报告；README 加构建徽章，规范清单中「无 CI」一项改为已做到 |
| 2026-11-17 | 文档与实现对齐：修正包数量（8→9）、批量删除方法名（`removeByIds`→`deleteBatchIds`）、校验与异常处理的实现描述、提交数；`5.4` 补 GitHub Actions 实测记录 |
| 2026-11-18 | 清理编译告警：MyBatis `Log4jImpl` 自 3.5.9 起废弃，改用 `Slf4jImpl`；`-Xlint:all` 复查零告警，SQL 日志格式不变 |
| 2026-11-19 | 删除 surefire 中两条无效的 `--add-opens`（移除后 `mvn test` 仍 4 用例全绿）；README 两处相关表述同步更新 |
| 2026-11-20 | 补字段自动填充：新增 `MyMetaObjectHandler` 并挂到 `GlobalConfig`（纯 SSM 下不会自动装配）；`Emp` 的 `createTime`/`updateTime` 加 `@TableField(fill)`；INSERT 与 UPDATE 语句已显式携带时间列 |
| 2026-11-21 | 列表支持排序：`sortBy` 走白名单映射（排序列不能用占位符，直接拼接即有注入风险），越界回落 `empId`；`sortDir` 仅 `asc` 生效，默认仍为 `emp_id` 倒序；用例补至 47 条 |
| 2026-11-22 | 发布 `v1.1.0`：相对 `v1.0.1` 累计 6 条提交，含两个向后兼容的新功能与四项修复/清理；README 版本行同步 |
| 2026-11-22 | 更新收尾状态：路线图任务 7 拆成四项打勾，ApiFox 如实标为待录入；第九节标题由「已知待解决」改为「曾经的设计决策与结论」，四条各补最终做法与证据位置 |
| 2026-11-23 | 新增部门管理模块：`dept` 表（名称唯一、CHECK 约束、6 条种子）+ 实体/Mapper/Service/Controller，六个接口；删除时校验部门下无员工，改名时同事务级联刷新 `emp.dept`；用例补至 47 条 |
| 2026-11-23 | 新增账号管理与操作审计：`op_log` 表 + `AuditAspect`（写操作落库，`REQUIRES_NEW` 独立事务）；六个用户接口，含「不能停用/删除自己」与「不能删最后一个启用账号」；口令摘要工具抽到 `PasswordEncoder`，`SysUser.passwordHash` 加 `@JsonIgnore`；日志切面的口令打码改为按字段名正则，覆盖新增的 DTO |
| 2026-11-24 | 新增考勤与薪资模块：`attendance`（`emp_id + work_date` 唯一、CHECK 校验打卡时间与状态）、`salary_record`（`emp_id + month` 唯一、实发由服务端计算）；月度汇总与批量生成均用「一次 IN 查询」避开 N+1；薪资生成幂等，已发放记录不可改删 |
| 2026-11-24 | 新增单页管理界面 `src/main/webapp/index.html`（原生 HTML/CSS/fetch，零外部依赖）：顶栏横向导航、六页面、分页筛选与弹窗表单；`SpringMvcConfig` 增加 `/` 转发与静态资源放行，接口仍受登录拦截保护 |
| 2026-11-24 | 修复三处衔接与协议问题：员工写入校验部门是否存在于 `dept` 表；考勤/薪资列表补 `empName`（批量回填）；缺参数与不支持的 Content-Type 分别返回 400/415，并发唯一键冲突提示「请勿重复提交」 |
| 2026-11-24 | 打通「考勤 → 薪资」：考勤状态改由打卡时间推导（客户端传值忽略），薪资生成时按缺勤/迟到/早退计算扣款，规则集中在 `SalaryRule`；停用部门不再允许分配员工；种子数据与推导规则对齐 |
| 2026-11-24 | 新增 `POST /salaries/recalculate`：补录考勤后可重算待发放记录，已发放跳过；文档总校——`api.md` 补 4 章 19 个接口，`openapi.yaml` 扩到 21 路径 36 操作，`test-cases.md` 扩到 77 条（9 组），README 计数与交付文档描述同步 |
| 2026-11-24 | 新增权限模型：`role` / `permission` / `user_role` / `role_permission` 四表，`@RequirePermission` + `PermissionAspect` 实现接口级鉴权（403）；内置 ADMIN/HR/VIEWER 三角色，权限实时生效；界面按 `/me` 收敛导航与写按钮；接口 48、用例 90 |
| 2026-11-24 | 界面补批量删除：员工表加勾选列与全选，按钮显示选中数量；后端 `DELETE /emps` 早已支持数组，界面此前只有单条删除，「批量处理」只能靠接口调用 |
| 2026-11-24 | 端点去重与授权入口：删除与 `GET /users/{id}/roles` 重复的 `GET /roles/users/{id}`（48→47）；账号页新增「角色」按钮，可勾选分配角色（复用既有接口，不新增端点） |
| 2026-11-24 | 修复删除账号撞外键：`user_role` 对 `sys_user` 有外键，直接删账号会抛 `DataIntegrityViolationException`（表现为「系统繁忙」）；改为同事务内先清角色关联；并补该异常的全局处理，提示「数据被其他记录引用或违反约束」 |
| 2026-11-24 | 新增 `salary_standard` 薪资标准表（按生效日期留存调薪历史）：生成薪资改取当月适用标准，修复「补生成历史月份用当前薪资」；员工表单部门改下拉、登记考勤日期默认今天、考勤变动后提示重算；用例补至 80 条 |

---

## 附：交付文档

| 文档 | 内容 |
| :--- | :--- |
| [docs/architecture.md](docs/architecture.md) | 整体结构图、父子容器边界、一次请求的完整链路、组件清单、分层原则 |
| [docs/api.md](docs/api.md) | 47 个接口的请求/响应示例、状态码与错误码约定、调用顺序示例 |
| [docs/openapi.yaml](docs/openapi.yaml) | OpenAPI 3.0.3 规范，21 条路径 36 个操作，可导入 ApiFox / Postman 生成接口树 |
| [docs/test-cases.md](docs/test-cases.md) | 91 条接口用例（10 个分组），含预期与实际结果对照，附本轮发现并修复的缺陷分析 |
| [docs/engineering-checklist.md](docs/engineering-checklist.md) | 31 条开发规范逐条对照，附「未做到的部分」7 项 |
| [docs/troubleshooting.md](docs/troubleshooting.md) | 9 条实测故障的「现象 → 根因 → 处理」记录 |

---

<p align="center">
  <sub>实验二 · EMP 员工管理 MIS 系统 —— 先把依赖和边界钉死，再写第一行业务代码。</sub>
</p>
