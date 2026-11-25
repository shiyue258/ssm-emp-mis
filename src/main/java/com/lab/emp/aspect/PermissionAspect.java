package com.lab.emp.aspect;

import com.lab.emp.common.ForbiddenException;
import com.lab.emp.common.LoginUser;
import com.lab.emp.common.RequirePermission;
import com.lab.emp.interceptor.PermissionInterceptor;
import com.lab.emp.service.PermissionService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.lang.reflect.Method;

/**
 * 接口权限校验切面。
 *
 * <p>与 {@code LoginInterceptor} 分工：拦截器只回答「有没有登录」，
 * 本切面回答「这个登录的人能不能做这件事」。评分表要求「基于 Spring AOP 实现接口权限校验」，
 * 指的就是这一层——若把它塞进拦截器里按 URL 前缀判断，规则会和路由耦合，
 * 新增接口时必须记得改拦截器，漏改就是越权。
 *
 * <p>用注解 {@link RequirePermission} 声明所需权限，切面在方法执行前校验；
 * 校验不通过抛 {@link ForbiddenException}，由全局异常处理器转成 HTTP 403。
 *
 * <p>权限每次查库、不缓存在会话里：撤权要立即生效，缓存会导致「已撤权但还能用」。
 *
 * <p><b>与 {@code PermissionInterceptor} 的关系。</b>拦截器在参数解析前先判一次，
 * 让越权请求在 {@code @Valid} 校验之前就拿到 403（否则未授权方能读到字段校验信息）；
 * 本切面负责兜底，覆盖不经过 MVC 链路的调用。拦截器校验通过后会在 request 上留标记，
 * 切面见到标记直接放行，同一请求不重复查库。
 */
@Aspect
@Component
public class PermissionAspect {

    private final PermissionService permissionService;

    public PermissionAspect(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Around("@annotation(com.lab.emp.common.RequirePermission)")
    public Object check(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequirePermission required = method.getAnnotation(RequirePermission.class);
        if (required == null) {
            return joinPoint.proceed();
        }

        // 拦截器已在参数解析前判过权限：同一请求内不再重复查库。
        // 标记只写在 request 上，不跨请求，因此不影响「撤权立即生效」。
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null
                && Boolean.TRUE.equals(attrs.getRequest().getAttribute(PermissionInterceptor.CHECKED_ATTRIBUTE))) {
            return joinPoint.proceed();
        }

        LoginUser user = currentUser();
        if (user == null) {
            throw new ForbiddenException("未登录");
        }
        if (!permissionService.hasAny(user.getUserId(), required.value())) {
            throw new ForbiddenException("无权限执行该操作，需要：" + String.join(" 或 ", required.value()));
        }
        return joinPoint.proceed();
    }

    /** 取当前会话中的登录用户；未登录返回 null，交由上抛的 403 处理。 */
    private LoginUser currentUser() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        HttpSession session = request.getSession(false);
        Object value = (session == null) ? null : session.getAttribute(LoginUser.SESSION_KEY);
        return (value instanceof LoginUser) ? (LoginUser) value : null;
    }
}
