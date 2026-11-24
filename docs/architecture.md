# 架构说明

## 一、整体结构

单 war 包、单 Maven 模块、Spring 父子双容器。**没有独立前端工程**：管理界面是 war 内的一个静态 `index.html`，用原生 `fetch` 调同一批 JSON 接口。

```text
                         ┌────────────────────────────────────┐
   浏览器（管理界面） ───► │  Tomcat 9（Servlet 容器）            │
   ApiFox / curl    ───► │   端口 8080，上下文路径 /            │
                         └───────────────┬────────────────────┘
                                         │
                    ┌────────────────────┴────────────────────┐
                    │                                          │
             静态资源（/、/index.html）                 全部 /xxx 接口
             DefaultServlet 直接返回                   DispatcherServlet（"/"）
                                                              │
                    ┌───────────────────┬────────────────────┼───────────────────┐
                    │                   │                    │                   │
              CharacterEncodingFilter  LoginInterceptor   WebLogAspect     AuditAspect
              （UTF-8，forceEncoding） （未登录 → 401）    （日志/耗时）    （写操作落库）
                    │                   │                    │                   │
                    └───────────────────▼────────────────────┘                   │
                                        │                                          │
                              ┌─────────▼─────────┐                                │
                              │   Controller 层    │  只做接收与封装                 │
                              └─────────┬─────────┘                                │
                              ┌─────────▼─────────┐                                │
                              │    Service 层      │  事务边界                      │
                              └─────────┬─────────┘                                │
                              ┌─────────▼─────────┐                                │
                              │    Mapper 层       │  BaseMapper + 条件构造器        │
                              └─────────┬─────────┘                                │
                              ┌─────────▼─────────┐        ┌──────────────────────▼─┐
                              │  MySQL 8.0.46      │        │  op_log（独立事务写入） │
                              │  ssm_emp 库 7 张表  │        └────────────────────────┘
                              └───────────────────┘
```

表清单：`emp` / `dept` / `sys_user` / `attendance` / `salary_record` / `salary_standard` / `op_log`。

## 二、父子容器边界

全注解整合最容易出错的地方就在这里，所以单独画出来。

```text
┌─────────────────────────────── 根容器（ContextLoaderListener）───────────────────────────────┐
│  SpringConfig                                                                                │
│    ├─ DruidDataSource            jdbc.properties + 环境变量覆盖（initialSize 5 / maxActive 20）│
│    ├─ MybatisSqlSessionFactoryBean   MybatisConfiguration + 分页插件 + GlobalConfig 自动填充    │
│    ├─ MapperScannerConfigurer    com.lab.emp.mapper（7 个 Mapper）                            │
│    ├─ DataSourceTransactionManager                                                           │
│    ├─ 七个 @Service：Emp / Dept / Attendance / Salary / User / Auth / OpLog                   │
│    ├─ MyMetaObjectHandler        时间字段填充（纯 SSM 下必须挂到 GlobalConfig，不会自动装配）    │
│    └─ 全局异常处理器 GlobalExceptionHandler（@RestControllerAdvice，属控制器增强，随子容器工作）│
│                                                                                              │
│  扫描范围：com.lab.emp                                                                        │
│  排除：@Controller、com.lab.emp.aspect.*、SpringMvcConfig                                     │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
                                             │ 子容器可访问父容器 Bean，反之不可
┌─────────────────────────────── 子容器（DispatcherServlet）──────────────────────────────────┐
│  SpringMvcConfig（@EnableWebMvc + @EnableAspectJAutoProxy）                                  │
│    ├─ 8 个 Controller：Health / Auth / Emp / Dept / Attendance / Salary / User / OpLog         │
│    ├─ WebLogAspect + AuditAspect（切面必须与它代理的 Controller 同容器）                        │
│    ├─ LoginInterceptor（在 addInterceptors 中注册，放行 /、/index.html 等静态路径）            │
│    ├─ 定制 Jackson 转换器（关闭 WRITE_DATES_AS_TIMESTAMPS，指定日期格式）                      │
│    └─ CORS、静态资源放行、`/` → forward:/index.html                                           │
│                                                                                              │
│  扫描范围：com.lab.emp.controller、com.lab.emp.aspect                                          │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

三条边界规则，每条都对应一个实际踩过的坑：

| 规则 | 违反后的现象 |
| :--- | :--- |
| 根容器必须排除 `@Controller` | Controller 被两个容器各建一份，Service 注入为 null、拦截器不生效 |
| 切面必须与 Controller 同容器 | 切面注册成功但一条日志都不打，也不报错 |
| 根容器必须排除 `SpringMvcConfig` | Web 环境下多建一套 WebMvc 组件；纯容器测试直接抛 `No ServletContext` |

## 三、一次请求的完整链路

以 `GET /emps?page=1&size=5`（已登录）为例：

1. **Tomcat** 收到请求，按上下文路径 `/` 交给 `DispatcherServlet`；
2. **CharacterEncodingFilter** 设置请求/响应编码为 UTF-8（`forceEncoding = true`，请求体也强制按 UTF-8 解析）；
3. **DispatcherServlet** 查 `HandlerMapping`，定位到 `EmpController#page`；
4. **LoginInterceptor#preHandle** 检查 session 中的 `loginUser`，存在则放行，不存在直接写 401 并中断；
5. **WebLogAspect#logAround** 打印 `[REQ]` 与入参；
6. **参数绑定**：`page` / `size` / `name` / `gender` / `dept` 绑定到 `EmpQueryDTO`；
7. **Controller** 调 `EmpService#pageQuery`；
8. **Service** 用 `LambdaQueryWrapper` 拼条件，`Page` 承载分页参数；
9. **Mapper** 由 MyBatis-Plus 注入，自动追加 `deleted = 0`（`@TableLogic`），分页插件改写 SQL 为 `LIMIT`；
10. **Druid** 从连接池取连接执行；
11. 结果逐层返回，`MappingJackson2HttpMessageConverter` 序列化为 JSON；
12. **WebLogAspect** 打印 `[RESP]` 与耗时。

写操作（`POST` / `PUT` / `DELETE`）在第 7 步进入 `@Transactional` 方法，事务边界覆盖到第 10 步结束。

写操作还多一层 **AuditAspect**（与 `WebLogAspect` 同为切面，职责不同）：

| 切面 | 输出 | 事务 | 记录内容 |
| :--- | :--- | :--- | :--- |
| `WebLogAspect` | 控制台日志 | 无 | 入参、耗时、异常（口令打码） |
| `AuditAspect` | `op_log` 表 | `REQUIRES_NEW` 独立事务 | 操作人、方法、路径、结果；失败也落库 |

独立事务是必须的：若共用业务事务，业务回滚会把「这次操作失败了」的记录一起滚掉，而失败恰恰最需要留痕。

## 四、组件清单

| 层次 | 类 | 职责 |
| :--- | :--- | :--- |
| 容器初始化 | `ServletContainersInitConfig` | 取代 `web.xml`，注册 DispatcherServlet 与编码过滤器 |
| 配置 | `SpringConfig` | 数据源、MyBatis-Plus、Mapper 扫描、事务管理器、自动填充 |
| 配置 | `SpringMvcConfig` | WebMvc、跨域、拦截器、Jackson 定制、静态资源与首页转发 |
| 配置 | `MyMetaObjectHandler` | `createTime` / `updateTime` 自动填充 |
| 表现层 | `HealthController` | 整合验收：`/health`、`/health/db` |
| 表现层 | `AuthController` | `POST /login`、`POST /logout` |
| 表现层 | `EmpController` / `DeptController` | 员工六接口、部门六接口（含部门员工查询） |
| 表现层 | `AttendanceController` | 考勤六接口（含月度汇总） |
| 表现层 | `SalaryController` | 薪资七接口（生成、重算、调整、发放、删除） |
| 表现层 | `UserController` / `OpLogController` | 账号六接口、审计查询（只读） |
| 业务层 | `EmpServiceImpl` | 分页多条件、增改删、批量删除、部门校验、薪资标准写入 |
| 业务层 | `DeptServiceImpl` | 部门 CRUD、删除前校验、改名级联 |
| 业务层 | `AttendanceServiceImpl` | 状态推导、重复校验、月度汇总（两条查询避开 N+1） |
| 业务层 | `SalaryServiceImpl` | 按标准取基数、按考勤扣款、幂等生成、重算、发放状态机 |
| 业务层 | `UserServiceImpl` / `OpLogServiceImpl` | 账号防呆三道；审计写入（独立事务）与查询 |
| 持久层 | 7 个 `BaseMapper` | `BaseMapper` 通用 CRUD，无 XML |
| 模型 | `Emp` / `Dept` / `SysUser` / `Attendance` / `SalaryRecord` / `SalaryStandard` / `OpLog` | 表映射，均带 JSR-303 |
| 公共 | `Result` / `BusinessException` / `GlobalExceptionHandler` | 统一返回与九类异常出口 |
| 公共 | `PasswordEncoder` / `SalaryRule` | 口令摘要；日薪与扣款规则（单一出处） |
| 横切 | `LoginInterceptor` / `WebLogAspect` / `AuditAspect` | 登录校验、请求日志、操作审计 |
| 前端 | `webapp/index.html` | 单页界面，原生 fetch 调接口，零外部依赖 |

## 五、分层原则

依赖方向严格单向，不允许反向或跨层：

```text
entity  ←  mapper  ←  service  ←  controller
                ↑
        config（只做装配，不含业务）
```

三条具体约束：

1. **Controller 不出现 SQL 与事务注解**——它只负责协议适配，换一个出口（定时任务、消息消费者）时业务仍可复用；
2. **Service 不返回 `Result`**——`Result` 是表现层封装，业务层返回实体或 `Page`；
3. **实体不直接暴露内部字段**——`deleted` 加 `@JsonIgnore`，`LoginUser` 不含口令摘要。

## 六、跨模块规则

五个业务域之间只有三类关系，其余保持独立——这是「有耦合」与「耦合过度」的分界。

| 关系 | 落到代码上 | 为什么必须存在 |
| :--- | :--- | :--- |
| 员工 ↔ 部门 | 名称关联：写入校验部门存在且启用；部门改名时同事务刷新 `emp.dept`；删除前统计员工数 | 部门撤销后员工仍挂着，等于制造孤儿数据 |
| 员工 ↔ 考勤 / 薪资 | `emp_id` 关联；列表批量回填姓名；员工逻辑删除后历史记录保留 | 考勤与薪资是历史事实，不能因人事变动消失 |
| 考勤 → 薪资 | 生成/重算时按当月考勤计算扣款（缺勤 × 日薪、迟到/早退 × 20） | 这是唯一一条**计算依赖**；没有它，两个模块只是各存一张表 |

刻意**没有**建立的关系：

- 账号与员工不关联——管理员是系统角色，不必是员工，强行关联会让登录依赖员工表；
- 部门与考勤/薪资不关联——考勤按人记录，部门调动不应改写历史；
- 薪资的标准不开放独立接口——只能随员工改薪由服务端写入，避免出现绕过校验的入口。

## 七、为什么不做多模块

单 war 工程，`mvn package` 产出一个 `ssm-emp-mis.war`，部署一次即可运行。多模块 Maven 解决的是**构件复用**问题（公共 jar 被别的项目依赖），本项目没有这个需求；拆开反而会让「克隆下来根目录不是可运行工程」「装配链散落在多个 pom」。
