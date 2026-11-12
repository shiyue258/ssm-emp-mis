# 架构说明

## 一、整体结构

单 war 包、单 Maven 模块、Spring 父子双容器。没有前端工程，对外只有 JSON 接口。

```text
                         ┌──────────────────────────────┐
   浏览器 / ApiFox  ───►  │  Tomcat 9（Servlet 容器）      │
                         │   端口 8080，上下文路径 /      │
                         └──────────────┬───────────────┘
                                        │ 全部请求
                          ┌─────────────▼──────────────┐
                          │ DispatcherServlet（"/"）     │
                          └─────────────┬──────────────┘
                                        │
                    ┌───────────────────┼───────────────────┐
                    │                   │                   │
              CharacterEncodingFilter  LoginInterceptor   WebLogAspect
              （UTF-8，forceEncoding） （未登录 → 401）    （入参/耗时/异常）
                    │                   │                   │
                    └───────────────────▼───────────────────┘
                                        │
                              ┌─────────▼─────────┐
                              │   Controller 层    │  只做接收与封装
                              └─────────┬─────────┘
                              ┌─────────▼─────────┐
                              │    Service 层      │  事务边界
                              └─────────┬─────────┘
                              ┌─────────▼─────────┐
                              │    Mapper 层       │  BaseMapper + 条件构造器
                              └─────────┬─────────┘
                              ┌─────────▼─────────┐
                              │  MySQL 8.0.46      │  ssm_emp 库
                              └───────────────────┘
```

## 二、父子容器边界

全注解整合最容易出错的地方就在这里，所以单独画出来。

```text
┌─────────────────────────────── 根容器（ContextLoaderListener）───────────────────────────────┐
│  SpringConfig                                                                                │
│    ├─ DruidDataSource            jdbc.properties + 环境变量覆盖                               │
│    ├─ MybatisSqlSessionFactoryBean   MybatisConfiguration + 分页插件                          │
│    ├─ MapperScannerConfigurer    com.lab.emp.mapper                                          │
│    ├─ DataSourceTransactionManager                                                           │
│    ├─ EmpService / EmpServiceImpl / AuthService / AuthServiceImpl   （@Service）              │
│    └─ 全局异常处理器 GlobalExceptionHandler（@RestControllerAdvice，属控制器增强，随子容器工作）│
│                                                                                              │
│  扫描范围：com.lab.emp                                                                        │
│  排除：@Controller、com.lab.emp.aspect.*、SpringMvcConfig                                     │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
                                             │ 子容器可访问父容器 Bean，反之不可
┌─────────────────────────────── 子容器（DispatcherServlet）──────────────────────────────────┐
│  SpringMvcConfig（@EnableWebMvc + @EnableAspectJAutoProxy）                                  │
│    ├─ Controller：HealthController / AuthController / EmpController                          │
│    ├─ WebLogAspect（切面必须与它代理的 Controller 同容器）                                     │
│    ├─ LoginInterceptor（在 addInterceptors 中注册）                                           │
│    ├─ 定制 Jackson 转换器（关闭 WRITE_DATES_AS_TIMESTAMPS，指定日期格式）                      │
│    └─ CORS、静态资源放行                                                                       │
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

## 四、组件清单

| 层次 | 类 | 职责 |
| :--- | :--- | :--- |
| 容器初始化 | `ServletContainersInitConfig` | 取代 `web.xml`，注册 DispatcherServlet 与编码过滤器 |
| 配置 | `SpringConfig` | 数据源、MyBatis-Plus、Mapper 扫描、事务管理器 |
| 配置 | `SpringMvcConfig` | WebMvc、跨域、拦截器、Jackson 定制 |
| 表现层 | `HealthController` | 整合验收：`/health`、`/health/db` |
| 表现层 | `AuthController` | `POST /login`、`POST /logout` |
| 表现层 | `EmpController` | 员工资源六个端点 |
| 业务层 | `EmpService` / `EmpServiceImpl` | 分页多条件、增改删、批量删除 |
| 业务层 | `AuthService` / `AuthServiceImpl` | 口令摘要比对、登录态构造 |
| 持久层 | `EmpMapper` / `SysUserMapper` | `BaseMapper` 通用 CRUD |
| 模型 | `Emp` / `SysUser` / `EmpQueryDTO` / `LoginDTO` / `LoginUser` | 表映射、查询条件、会话载体 |
| 公共 | `Result` / `BusinessException` / `GlobalExceptionHandler` | 统一返回与异常出口 |
| 横切 | `LoginInterceptor` / `WebLogAspect` | 登录校验、请求日志 |

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

## 六、为什么不做多模块

单 war 工程，`mvn package` 产出一个 `ssm-emp-mis.war`，部署一次即可运行。多模块 Maven 解决的是**构件复用**问题（公共 jar 被别的项目依赖），本项目没有这个需求；拆开反而会让「克隆下来根目录不是可运行工程」「装配链散落在多个 pom」。
