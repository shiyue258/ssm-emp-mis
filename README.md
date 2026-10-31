# ssm-emp-mis · EMP 员工管理 MIS 系统（SSM 全注解整合）

<p>
  <b>《JAVA框架技术（一）》实验二</b> · 10 学时（含双师课堂 2 学时）· 综合性 + 设计性<br>
  Spring + SpringMVC + MyBatis-Plus 全注解整合，war 包部署于外置 Tomcat 9，<br>
  对外提供 EMP 员工管理的 RESTful 接口，并覆盖参数校验、全局异常、统一返回、请求日志、登录拦截与声明式事务。
</p>

| 项 | 内容 |
| :--- | :--- |
| 构建 | Maven 3.9.12 |
| 语言 / JDK | **Java 17**（`maven.compiler.release = 17`） |
| 框架 | Spring 5.3.31 + SpringMVC 5.3.31 + MyBatis-Plus 3.5.3.1 |
| 数据库 | MySQL 8.0，库 `ssm_emp`，主表 `emp` |
| 容器 | Tomcat 9.0.x（`javax.servlet` 4.0 命名空间） |
| 打包 | `war`，全注解整合、**无 `web.xml`** |
| 状态 | 🚧 **骨架阶段**：`pom.xml` 与本文档已完成，业务代码尚未开始 |

> 进度口径说明：本文档中标注「已实现」的内容均指**当前仓库里真实存在且跑通过**的东西；
> 其余一律标「待实现」。不做「文档先行、代码后补」的虚假陈述。

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
| Jackson | 2.15.3 | JSON 序列化 |
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
- JDK 17 的强封装会让反射滥用从「运行时玄学」变成「编译/启动期显式报错」，对 Druid、MyBatis-Plus 这类反射密集的库需要显式 `--add-opens`（已写入 surefire 配置）。

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
| 1 | POST | `/emps` | 新增员工 | 待实现 |
| 2 | DELETE | `/emps/{id}` | 按 ID 删除 | 待实现 |
| 3 | DELETE | `/emps` | 批量删除（body 传 `List<Integer>`） | 待实现 |
| 4 | PUT | `/emps` | 修改员工信息 | 待实现 |
| 5 | GET | `/emps/{id}` | 单条查询 | 待实现 |
| 6 | GET | `/emps` | 列表 + 多条件模糊 + 分页（`name` / `gender` / `dept` / `page` / `size`） | 待实现 |
| 7 | POST | `/login` | 登录，写入 session（拦截器放行名单内） | 待实现 |
| 8 | POST | `/logout` | 退出，清 session | 待实现 |

> 第 7、8 条是指导书「登录权限拦截」的配套实现——没有登录入口就无法演示 401，故单列。

---

## 四、工程结构

目标形态（`✅` = 已存在，`⬜` = 待实现）：

```text
ssm-emp-mis/
├── pom.xml                                      ✅ 依赖与构建，版本全部锁定
├── README.md                                    ✅
├── .gitignore                                   ✅
├── sql/init.sql                                 ⬜ 建库建表 + 约束索引 + 种子数据
└── src/
    ├── main/java/com/lab/emp/
    │   ├── config/
    │   │   ├── ServletContainersInitConfig      ⬜ 取代 web.xml，注册 DispatcherServlet + 编码过滤器
    │   │   ├── SpringConfig                     ⬜ 根容器：Druid、MyBatis-Plus、Mapper 扫描、事务
    │   │   └── SpringMvcConfig                  ⬜ 子容器：@EnableWebMvc、跨域、拦截器、静态资源
    │   ├── controller/EmpController             ⬜ RESTful 六接口
    │   ├── service/EmpService + impl/EmpServiceImpl  ⬜ 分页多条件 + 事务方法
    │   ├── mapper/EmpMapper                     ⬜ BaseMapper
    │   ├── entity/Emp                           ⬜ JSR-303 注解 + MP 注解
    │   ├── dto/EmpQueryDTO                      ⬜ 查询条件对象
    │   ├── common/
    │   │   ├── Result                           ⬜ 统一返回
    │   │   ├── BusinessException                ⬜ 业务异常
    │   │   └── GlobalExceptionHandler           ⬜ @RestControllerAdvice
    │   ├── aspect/WebLogAspect                  ⬜ AOP 请求日志
    │   └── interceptor/LoginInterceptor         ⬜ 未登录返回 401
    ├── main/resources/
    │   ├── jdbc.properties                      ⬜ 连接串（密码可被环境变量覆盖）
    │   ├── log4j.properties                     ⬜ Console，Mapper 包 DEBUG
    │   └── mapper/EmpMapper.xml                 ⬜ 若采用 XML 映射则放此处
    └── test/java/com/lab/emp/                   ⬜ 事务失效三场景集成测试
```

### 4.1 为什么是「单个 Maven 模块 + 内部分层」，不是多模块

这里要区分两个容易混淆的「模块」：

| 概念 | 本工程的做法 | 说明 |
| :--- | :--- | :--- |
| **Maven 模块**（工程结构） | **1 个**：整个仓库就是一个 `war` 工程 | 一次 `mvn package` 产出一个 `ssm-emp-mis.war`，部署一次即可运行 |
| **代码分层**（包结构） | **8 个包**：`config / controller / service / mapper / entity / dto / common / aspect / interceptor` | 逻辑分层，编译后都在同一个 war 里 |

多模块 Maven（`<packaging>pom</packaging>` 的父工程 + 若干 `jar` 子模块）解决的是**构件复用**问题：当 `common` 需要以 jar 形式被别的项目依赖时才值得拆。本工程是单一可部署产物，拆开只会带来三个坏处——老师克隆后根目录没有可运行工程、全注解整合的装配链被拆散到多个 pom、`mvn package` 需要先 `install` 父工程。**所以保持单模块。**

> 换句话说：「项目的全部」= 这个仓库里的所有内容（源码、SQL、配置、文档、Git 历史）。
> 它编译出的**唯一**产物是 `ssm-emp-mis.war`，没有第二个 jar 需要单独部署。

依赖方向严格单向：


```text
entity ← mapper ← service ← controller
                ↑
        config（数据源 / 事务 / 拦截器）
```

---

## 五、构建与运行

### 5.1 环境要求

| 依赖 | 要求 | 检查命令 |
| :--- | :--- | :--- |
| JDK | 17（Temurin 17.0.21 已验证） | `java -version` |
| Maven | 3.9+ | `mvn -v`（输出的 Java version 应为 17） |
| MySQL | 8.0，端口 3306 | `mysql -uroot -p -e "select version()"` |
| Tomcat | 9.0.x | IDEA → Run Configuration → Tomcat Server |

### 5.2 构建

```bash
# 只解析依赖（最快的一道健康检查）
mvn -B dependency:resolve

# 打包成 war，产物 target/ssm-emp-mis.war
mvn -B clean package
```

### 5.3 部署到 Tomcat 9 与接口基址

把 `target/ssm-emp-mis.war` 丢进 Tomcat 9 的 `webapps/`，或在 IDEA 里配置 Tomcat Server 并添加该 war 的 deployment。

**关键一步：把 Application context 设为 `/`。** 否则默认上下文是 `/ssm-emp-mis`，所有接口前面会多一段前缀：

| Application context | 新增员工的完整 URL | 与指导书的匹配度 |
| :--- | :--- | :--- |
| `/`（推荐） | `http://localhost:8080/emps` | ✅ 与 `POST /emps` 逐字一致 |
| `/ssm-emp-mis`（默认） | `http://localhost:8080/ssm-emp-mis/emps` | ⚠️ 需在报告里额外说明前缀来源 |

> 这也是「war 名与仓库名一致」的代价——名字长，但换来仓库名、构件名、产物名三者统一。上下文路径在 Tomcat 里是一行配置，可以随时压平。

### 5.4 目前验证到哪一步

| 检查项 | 结果 |
| :--- | :--- |
| `mvn -B dependency:resolve`（JDK 17.0.21 / Maven 3.9.12） | ✅ BUILD SUCCESS，38 个构件全部解析 |
| `mvn -B clean package`（空工程） | ✅ BUILD SUCCESS，产出 `target/ssm-emp-mis.war`（21.4 MB） |
| `mvn -B test` | ⬜ 尚无测试用例 |
| Tomcat 9 部署启动 | ⬜ 尚无 `ServletContainersInitConfig`，启动会 404 |

> 这两条验证只证明「依赖选型与构建链在 JDK 17 上成立」，不代表功能可用。功能进度见 [七、路线图](#七路线图任务-27)。

---

## 六、评分点与工程产出的对照

实验二报告满分 100 分，四个维度：环境与功能 40 + 问题排查 20 + AI 规范 20 + 报告质量 20。

| 评分要点 | 本工程的落点 | 可提供的证据 |
| :--- | :--- | :--- |
| 全注解整合正确 | 三个 `@Configuration` 类 + `ServletContainersInitConfig` | 启动日志、父子容器 Bean 数量、`web.xml` 缺失截图 |
| 三层架构职责清晰 | `controller / service / mapper` 单向依赖，DTO 隔离入参 | 包结构截图、接口清单 |
| 增删改查 + 模糊 + 分页 | `EmpController` 六个端点 | ApiFox 用例集、响应截图 |
| 批量删除 | `DELETE /emps` + `removeByIds` | 用例：正常、空数组、含不存在 ID |
| JSR-303 校验 | 实体注解 + `@Valid` + `BindingResult` | 用例：空姓名、负薪资、超长字段 |
| 全局异常处理 | `@RestControllerAdvice` 双方法 | 用例：不存在的 ID、未登录、故意抛异常 |
| 统一返回格式 | `Result<T>` | 所有响应的 `code/message/data` 截图 |
| 请求日志记录 | `WebLogAspect` 环绕通知 | 控制台 `[REQ]/[RESP]/[ERR]` 日志 |
| 登录权限拦截 | `LoginInterceptor` + `/login` | 用例：未登录 401、登录后 200 |
| 声明式事务 | `@Transactional(rollbackFor)` | 三个失效场景的现象对比表 |
| AI 交互可追溯 | git 提交历史 + `docs/ai-log/`（规划中） | 提示词、生成结果、人工修正 diff |

**一票否决红线**（来自评分标准第七节，本工程全程规避）：

- 直接复制 AI 生成的完整项目 → 0 分；
- 未保留 AI 交互调试记录与修改痕迹 → 维度三 0 分；
- 硬编码真实账号口令 → 维度三 0 分。故 `jdbc.properties` 中密码支持环境变量覆盖，见 [9.3](#九已知待解决的设计问题)。

---

## 七、路线图（任务 2–7）

- [x] **任务 2（部分）** 工程初始化：`pom.xml` 版本锁定、`.gitignore`、JDK 17 构建链验证
- [ ] **任务 2（剩余）** 目录骨架、`git` 首次提交、依赖清单人工核对记录
- [ ] **任务 3** 全注解整合三件套 + 整合验证（临时 Controller / 临时 Service 试跑后删除）
- [ ] **任务 4** `sql/init.sql`（`emp` 表 + 索引 + 约束）、分层代码生成与逐层校验、`Result` 封装
- [ ] **任务 5** 分页多条件查询 + RESTful 六接口 + ApiFox 调试
- [ ] **任务 6** JSR-303 / 全局异常 / 登录拦截 / AOP 日志 / 声明式事务 + **事务失效三场景实验**
- [ ] **任务 7** ApiFox 全量用例集、AI 辅助调优记录、"企业开发规范清单"≥10 条、Git tag `v1.0.0`

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

## 九、已知待解决的设计问题

### 9.1 数据库要从零建，不能「沿用实验一」

指导书写「数据库沿用实验一的 `ssm_emp` 库与 `emp` 表」，但实验一实际建的是 `ssm_mall` 库 + `tb_user / tb_product / tb_order`（数码商城三表），**不存在 `ssm_emp` 与 `emp`**。因此 `sql/init.sql` 需完整自建，字段按指导书附录 A 的提示取：

```text
emp_id(PK) / emp_name / gender / dept / post / salary / hire_date / status
```

### 9.2 Mapper 用注解还是 XML

两种都符合要求。当前倾向：**主用 `BaseMapper` + 条件构造器**（分页多条件查询用 `LambdaQueryWrapper` 一行搞定），仅对需要显式展示 `if/where/foreach` 的动态 SQL 保留一份 XML。理由：实验一已充分展示 XML 动态 SQL，实验二的增量在「Spring 整合」，不必重复。

### 9.3 密码不能硬编码

`jdbc.properties` 中的密码支持环境变量 `EMP_DB_PASSWORD` 覆盖，默认值仅用于本地开发。评分标准把「硬编码真实账号口令」列为维度三计 0 分的违规项。

### 9.4 事务失效三场景如何留证

指导书要求验证「异常被 try-catch 吞掉 / `this` 自调用 / 注解加在 private 方法上」。三者都是**静默失效**——不报错、不抛异常，只能靠数据比对发现。计划写三个 JUnit 用例，各自断言「插入的行仍在库里」，并在报告中给出「现象 → 原因（AOP 代理未生效）→ 验证方法」三段式记录。

---

## 十、变更记录

| 日期 | 内容 |
| :--- | :--- |
| 2026-10-31 | 初始化仓库：`pom.xml`（JDK 17 + Spring 5.3.31 + MP 3.5.3.1，版本全部锁定）、`.gitignore`、本 README；在 JDK 17.0.21 上完成 `dependency:resolve` 与空 war 打包验证 |
| 2026-10-31 | 项目定名 `ssm-emp-mis`（技术栈 + 领域 + 系统类型三段式），仓库名 / 构件名 / 产物名三者统一；确认采用「单 Maven 模块 + 内部分层包」结构 |

---

<p align="center">
  <sub>实验二 · EMP 员工管理 MIS 系统 —— 先把依赖和边界钉死，再写第一行业务代码。</sub>
</p>
