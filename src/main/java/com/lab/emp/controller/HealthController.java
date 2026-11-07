package com.lab.emp.controller;

import com.lab.emp.common.Result;
import com.lab.emp.mapper.EmpMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查端点，用于验证框架整合是否真的通了。
 *
 * <p>它承担的是「整合验收」而不是业务职责，所以刻意分成两级：
 * <ul>
 *   <li>{@code GET /health} —— 只经过 SpringMVC 的请求分发与 JSON 序列化，
 *       返回 UP 就说明 DispatcherServlet、HandlerMapping、消息转换器三件套正常；</li>
 *   <li>{@code GET /health/db} —— 再往下走一层，调用 {@code EmpMapper.selectCount}，
 *       返回行数就说明数据源、MyBatis-Plus 的 BaseMapper 注入、
 *       PageInterceptor 配置全部到位。</li>
 * </ul>
 *
 * <p>把两者分开的意义在排错：若 {@code /health} 通而 {@code /health/db} 报
 * {@code Invalid bound statement (not found)}，问题必然出在
 * SqlSessionFactory 或 Mapper 扫描，与 SpringMVC 无关——一次请求就能把故障域缩小一半。
 *
 * <p>这两个端点不需要登录，后续接入 LoginInterceptor 时应加入放行名单。
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    private final EmpMapper empMapper;

    /** 构造器注入：单一构造器时 Spring 自动装配，无需 @Autowired。 */
    public HealthController(EmpMapper empMapper) {
        this.empMapper = empMapper;
    }

    /** 框架存活检查：不触碰数据库。 */
    @GetMapping
    public Result<String> health() {
        return Result.ok("UP");
    }

    /** 持久层检查：返回 emp 表实际行数，正常应为种子数据的 12。 */
    @GetMapping("/db")
    public Result<Long> db() {
        try {
            Long total = empMapper.selectCount(null);
            return Result.ok(total);
        } catch (Exception e) {
            // 全局异常处理器尚未接入，这里先自行兜底，避免返回容器默认的 HTML 错误页，
            // 让排查时能直接看到根因（例如 Invalid bound statement 或连接失败）。
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            return Result.fail("数据库检查失败：" + root.getClass().getSimpleName()
                    + " - " + root.getMessage());
        }
    }
}
