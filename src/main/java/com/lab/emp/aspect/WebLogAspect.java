package com.lab.emp.aspect;

import com.lab.emp.dto.LoginDTO;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 请求日志切面：记录每个 Controller 方法的入参、耗时与异常。
 *
 * <p>为什么用 AOP 而不是在每个方法里手写 log：切点是「controller 包下的所有方法」，
 * 新增接口自动获得日志能力，不需要记得加一行；要调整格式也只改一处。
 * 代价是多一层代理，Controller 的调用栈会变深，调试时看到的是代理类名字。
 *
 * <p>入参处理有三条规则，都是为了不把日志写成事故现场：
 * <ul>
 *   <li><b>口令必须打码</b>。日志文件往往权限更宽、保留更久，明文口令写进去
 *       等于绕过了数据库那层保护；</li>
 *   <li><b>跳过 Servlet 对象</b>。request/response/session 的 toString 没有可读信息，
 *       打出来只是噪音；</li>
 *   <li><b>超长截断</b>。批量删除的 ID 列表可能很长，不截断会把一次请求刷成几十行。</li>
 * </ul>
 *
 * <p>注意：本切面只织入 Controller。Service 层不记录入参日志，
 * 因为 Handler 层已经有完整链路，重复记录只会让日志量翻倍。
 */
@Aspect
@Component
public class WebLogAspect {

    private static final Logger log = LoggerFactory.getLogger(WebLogAspect.class);

    /** 入参日志的最大长度，超出部分截断。 */
    private static final int MAX_ARG_LENGTH = 300;

    @Pointcut("execution(* com.lab.emp.controller..*.*(..))")
    public void controllerMethods() {
        // 切点声明，方法体为空
    }

    @Around("controllerMethods()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String target = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "#" + joinPoint.getSignature().getName();
        long start = System.currentTimeMillis();
        log.info("[REQ ] {} 入参={}", target, describeArgs(joinPoint.getArgs()));
        try {
            Object result = joinPoint.proceed();
            log.info("[RESP] {} 耗时={}ms", target, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable t) {
            log.error("[ERR ] {} 耗时={}ms 异常={}: {}",
                    target, System.currentTimeMillis() - start,
                    t.getClass().getSimpleName(), t.getMessage());
            throw t;
        }
    }

    private String describeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(describeArg(args[i]));
        }
        sb.append(']');
        String text = sb.toString();
        return (text.length() <= MAX_ARG_LENGTH)
                ? text
                : text.substring(0, MAX_ARG_LENGTH) + "...(已截断)";
    }

    private String describeArg(Object arg) {
        if (arg == null) {
            return "null";
        }
        if (arg instanceof ServletRequest || arg instanceof ServletResponse || arg instanceof HttpSession) {
            return arg.getClass().getSimpleName() + "(略)";
        }
        if (arg instanceof LoginDTO) {
            // 只保留用户名，口令一律打码
            LoginDTO dto = (LoginDTO) arg;
            return "LoginDTO{username=" + dto.getUsername() + ", password=***}";
        }
        return String.valueOf(arg);
    }
}
