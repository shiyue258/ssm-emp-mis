# 故障排查手册

本文件记录本项目开发与部署过程中**真实遇到过**的问题，每条包含现象、根因与处理方式。
没有实测复现过的问题不写进来——宁可少一条，也不放一条「听说会这样」。

环境基线：Ubuntu 26.04（WSL2）+ JDK 17.0.21 + Maven 3.9.12 + Tomcat 9.0.123 + MySQL 8.0.46（Docker）。

---

## 一、Tomcat 起得来，几秒后自己退出

**现象**

`bin/startup.sh` 返回 `Tomcat started.`，端口短暂可访问，几秒后连接被拒。日志尾部是完整的关闭流程：

```
INFO [Thread-1] org.apache.coyote.AbstractProtocol.stop Stopping ProtocolHandler ["http-nio-8080"]
INFO [Thread-1] org.apache.coyote.AbstractProtocol.destroy Destroying ProtocolHandler ["http-nio-8080"]
```

**根因**

`startup.sh` 会把 JVM 放到后台，但它的父进程是当前 shell 会话。命令会话结束时，整个进程组收到 `SIGTERM`，Tomcat 触发关闭钩子正常退出。不是崩溃，是被杀。

**处理**

在需要长期驻留的会话中前台运行 `catalina.sh run`，由终端工具托管；或改用 systemd 单元。仅用于验证时，把启动与请求放在同一个会话里也能工作。

---

## 二、访问 `127.0.0.1:8080` 返回 502

**现象**

服务确实在监听，`ss -ltn | grep 8080` 有输出，但 `curl http://127.0.0.1:8080/health` 返回 `HTTP 502`，响应体为空。

**根因**

环境里预设了 `http_proxy=http://127.0.0.1:7897`（继承自宿主机的代理客户端）。`no_proxy` 中写了 `127.*`，但 curl 的绕过规则是**域名后缀匹配**，`127.*` 不是合法后缀，等于没写。于是本机请求被转发给代理，代理连不上再返回 502。

**处理**

```bash
export no_proxy='*'
export NO_PROXY='*'
```

或在单次请求上显式绕过：`curl --noproxy '*' ...`。

**判据**：`--noproxy '*'` 能通、默认不能通，即可确诊为代理问题，与服务无关。

---

## 三、启动报 `Failed to initialize component [Connector["http-nio-8080"]]`

**现象**

`catalina.out` 中该行后面跟着 `java.net.BindException: Address already in use`，随后整个实例关闭。

**根因**

8080 已被另一个进程占用——常见于上一次的 Tomcat 没停干净，或同一台机器上并行着别的 Web 应用。

**处理**

```bash
ss -ltnp | grep ':8080'          # 找出占用者
kill <pid>                        # 确认是本项目的进程后再结束
```

启动脚本里应加一段「先探测端口、不通才启动」的前置检查，避免带着残留进程反复试。

---

## 四、测试报 `IllegalStateException: No ServletContext set`

**现象**

`mvn test` 加载 Spring 上下文失败，堆栈指向：

```
resourceHandlerMapping
  ← DelegatingWebMvcConfiguration
  ← java.lang.IllegalStateException: No ServletContext set
```

**根因**

根容器的组件扫描范围是 `com.lab.emp`，而 `SpringMvcConfig` 本身也是 `@Configuration`，会被一并扫入。根容器于是按 `@EnableWebMvc` 去创建 `HandlerMapping` / `HandlerAdapter`，这些组件需要 ServletContext，纯容器环境里没有。

Web 环境下 `ContextLoaderListener` 会设置 ServletContext，所以**不报错**，只是白白多建一整套组件——缺陷一直存在，直到脱离容器的测试才暴露。

**处理**

在根容器的 `@ComponentScan.excludeFilters` 中按类型排除：

```java
@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SpringMvcConfig.class)
```

**教训**：只在 Web 环境验证，会漏掉「配置本身不干净」这一类问题。单元测试的价值不只是测业务。

---

## 五、接口返回的日期是 `[2021,3,15]` 而不是 `"2021-03-15"`

**现象**

`GET /emps/1` 的响应体中 `hireDate` 是整数数组。

**根因**

Jackson 默认 `WRITE_DATES_AS_TIMESTAMPS = true`。关闭它的是 Spring Boot 的 `JacksonAutoConfiguration`，**纯 SSM 下 `@EnableWebMvc` 装配的 `Jackson2ObjectMapperBuilder` 并不会关**。所以从 Boot 迁到纯 SSM 时，日期格式会静默变化。

**处理**

覆写 `WebMvcConfigurer#extendMessageConverters`，用定制 `ObjectMapper` 原位替换默认的 `MappingJackson2HttpMessageConverter`：

```java
ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .serializerByType(LocalDate.class, new LocalDateSerializer(ofPattern("yyyy-MM-dd")))
        .build();
```

用 `extendMessageConverters` 而非 `configureMessageConverters`：后者会清空默认转换器列表，`String`、`Resource` 的处理一起丢失。

---

## 六、`Invalid bound statement (not found)`

**现象**

容器启动正常、`/health` 正常，但任何调用 `BaseMapper` 通用方法的请求都抛该异常。

**根因**

用了 mybatis-spring 的 `SqlSessionFactoryBean` 而非 MyBatis-Plus 的 `MybatisSqlSessionFactoryBean`。前者产出原生 `Configuration`，`BaseMapper` 上的通用 CRUD 不会被注入。

与实验一「纯 Java SE 装配必须用 `MybatisXMLConfigBuilder`」是同一类坑：**选错构建入口，功能整体失效且不报配置错误**。

**处理**

`SpringConfig` 中改用 `com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean`，并传入 `MybatisConfiguration`。

---

## 七、导入 SQL 后中文变成 `æµ‹è¯•` 之类的乱码

**根因**

`mysql` 客户端默认按 latin1 解释输入流，含中文的 SQL 被二次编码。

**处理**

任何含中文字面量的导入命令都加参数：

```bash
docker exec -i dev-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 < sql/init.sql
```

判据：导入后 `select emp_name from emp limit 1` 应输出正常汉字。

---

## 八、事务「看起来加了注解却没回滚」

**现象**

方法上标了 `@Transactional`，执行中抛了异常，但数据仍然落库。

**根因**（三种，均已用测试固化，见 `TransactionFailureTest`）

| 场景 | 根因 |
| :--- | :--- |
| 异常被 try-catch 吞掉 | 事务管理器只能通过「方法是否向外抛异常」判断回滚，异常被吃掉等于报告一切正常 |
| `this` 自调用 | 内层调用不经过代理对象，事务拦截器根本没参与 |
| 注解在 private 方法上 | `AnnotationTransactionAttributeSource` 默认只处理 public 方法，非 public 上的注解被静默忽略（`final`、`static` 同理） |

**排查方法**

不要看方法有没有抛异常——三个场景里有两个照样抛异常。**看数据库里那一行在不在**，这是唯一可靠的判据。
