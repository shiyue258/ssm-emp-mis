package com.lab.emp.aspect;

import com.lab.emp.common.BusinessException;
import com.lab.emp.common.ForbiddenException;
import com.lab.emp.common.LoginUser;
import com.lab.emp.service.OpLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.dao.DuplicateKeyException;
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
 * <p><b>覆盖边界</b>：本切面织入的是 Controller 方法调用，而参数校验
 * （{@code @Valid} 触发的 {@code MethodArgumentNotValidException}）发生在方法体执行
 * <b>之前</b>，切面看不到，因此校验失败不会进审计表。这里是有意接受的：校验失败意味着
 * 数据根本没进业务层，不存在被改动的可能；控制台日志仍有 {@code [REQ]} 记录可查。
 * 若要求「所有被拒绝的写请求都留痕」，就得把审计从 AOP 移到 HandlerInterceptor，
 * 代价是拿不到异常类型，只能记结果码。
 *
 * <p>为什么 {@code /login} 也会被记：登录属于 POST，切点会命中。这其实是好事——
 * 登录失败同样值得留痕。操作人在 {@code proceed()} 之后重取一次：登录成功时
 * 会话中才有登录态，只取一次会把成功的登录记成 anonymous。
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
            opLogService.record(operator, action, target, describe(t), false);
            throw t;
        }
    }

    /**
     * 把异常压成一句人能看懂的话。
     *
     * <p>不直接存 {@code t.getMessage()}：数据库异常的消息里裹着整段 SQL 与表结构，
     * 存进审计表既占空间又把「谁改坏了什么」淹没在技术细节里。审计记「发生了什么」，
     * 技术细节留在控制台日志——两边的受众不同。
     */
    private String describe(Throwable t) {
        if (t instanceof BusinessException) {
            return ((BusinessException) t).getMessage();
        }
        if (t instanceof ForbiddenException) {
            // 记下具体缺哪个权限，比只记类名有用得多
            return ((ForbiddenException) t).getMessage();
        }
        if (t instanceof DuplicateKeyException) {
            return "唯一键冲突：并发重复提交被拒绝";
        }
        return t.getClass().getSimpleName();
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
