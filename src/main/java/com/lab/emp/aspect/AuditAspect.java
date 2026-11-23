package com.lab.emp.aspect;

import com.lab.emp.common.LoginUser;
import com.lab.emp.service.OpLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * 操作审计切面：把 Controller 上的写操作落库。
 *
 * <p>切点选「controller 包下的 POST / PUT / DELETE 方法」，而不是手工在每个 Service
 * 里插一行记录：新加接口会自动被覆盖，不需要记得加。这与 {@code WebLogAspect} 的
 * 思路一致，区别在于一个写控制台、一个写数据库。
 *
 * <p>为什么记的是失败也要落库：排查时最想知道的往往是「谁试图改、为什么没改成」。
 * 若只记成功，失败的操作就完全消失了。
 *
 * <p>为什么 {@code /login} 也会被记：登录属于 POST，切点会命中。这其实是好事——
 * 登录失败同样值得留痕。但登录成功时的操作人取自会话，此刻会话刚建立，
 * 取到的是本次登录的用户；登录失败时会话里没有登录态，记 anonymous，符合预期。
 */
@Aspect
@Component
public class AuditAspect {

    private final OpLogService opLogService;

    public AuditAspect(OpLogService opLogService) {
        this.opLogService = opLogService;
    }

    @Around("execution(public * com.lab.emp.controller..*.*(..)) && ("
            + "@annotation(org.springframework.web.bind.annotation.PostMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.PutMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.DeleteMapping))")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = currentRequest();
        String action = (request == null) ? "?" : request.getMethod();
        String target = (request == null)
                ? joinPoint.getSignature().toShortString()
                : request.getRequestURI();
        String operator = currentOperator(request);

        try {
            Object result = joinPoint.proceed();
            // 成功后再取一次操作人：登录接口在 proceed 之前会话里还没有登录态，
            // 若只取一次，成功的登录会被记成 anonymous——而「谁登录了」恰恰是
            // 审计最想回答的问题之一。
            String operatorAfter = currentOperator(request);
            opLogService.record(operatorAfter != null ? operatorAfter : operator,
                    action, target, "success", true);
            return result;
        } catch (Throwable t) {
            opLogService.record(operator, action, target,
                    t.getClass().getSimpleName() + ": " + t.getMessage(), false);
            throw t;
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return (attributes == null) ? null : attributes.getRequest();
    }

    /** 取不到登录态时返回 null，由 OpLogServiceImpl 统一记为 anonymous。 */
    private String currentOperator(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        Object value = (session == null) ? null : session.getAttribute(LoginUser.SESSION_KEY);
        return (value instanceof LoginUser) ? ((LoginUser) value).getUsername() : null;
    }
}
