package com.lab.emp.interceptor;

import com.lab.emp.common.ForbiddenException;
import com.lab.emp.common.LoginUser;
import com.lab.emp.common.RequirePermission;
import com.lab.emp.service.OpLogService;
import com.lab.emp.service.PermissionService;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 权限前置拦截器：在参数绑定与校验<b>之前</b>判定「这个登录的人能不能调这个接口」。
 *
 * <p><b>为什么需要它。</b>权限判定原先只有 {@code PermissionAspect} 一处，切面织入的是
 * Controller 方法，而 {@code @Valid} 的参数校验发生在 HandlerAdapter 解析参数阶段——
 * 早于方法调用。结果是：一个没有 {@code emp:write} 的账号提交不完整的员工数据时，
 * 收到的是「所属部门不能为空」而不是 403，等于让未授权方免费探测字段约束。
 * 数据虽未被写入（实测 emp 行数不变），但错误语义是错的，也泄露了实现细节。
 *
 * <p><b>为什么仍然保留切面。</b>两者分工不同：
 * <ul>
 *   <li>本拦截器是「尽早拒绝」，作用在 MVC 请求链路，让越权请求在解析请求体前就 403；</li>
 *   <li>{@code PermissionAspect} 是「兜底强制」，作用在方法调用层，任何非 MVC 路径
 *       （内部调用、将来暴露的远程接口）仍然受它约束。</li>
 * </ul>
 * 因此这不是「把权限写进拦截器按 URL 前缀判断」——那套做法规则与路由耦合、新增接口
 * 漏改即越权。这里读的是同一个 {@link RequirePermission} 注解，规则仍跟着方法走。
 *
 * <p><b>不重复查库。</b>校验通过后往 request 写一个标记，切面见到标记就直接放行；
 * 同一请求内只查一次权限表。撤权即时性不受影响：标记只存在于单次请求内。
 */
@Component
public class PermissionInterceptor implements HandlerInterceptor {

    /**
     * 请求级标记：本次请求已由拦截器完成鉴权。
     *
     * <p>放在 request 而不是 session：session 级缓存会让「撤权立即生效」失效，
     * 而这正是本项目刻意不做的事。
     */
    public static final String CHECKED_ATTRIBUTE =
            PermissionInterceptor.class.getName() + ".CHECKED";

    private final PermissionService permissionService;
    private final OpLogService opLogService;

    public PermissionInterceptor(PermissionService permissionService, OpLogService opLogService) {
        this.permissionService = permissionService;
        this.opLogService = opLogService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 跨域预检不带会话，放行交给后续的 CORS 处理
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        // 静态资源等非 Controller 处理器没有注解可读，直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        RequirePermission required = ((HandlerMethod) handler).getMethodAnnotation(RequirePermission.class);
        if (required == null) {
            return true;
        }

        LoginUser user = currentUser(request);
        if (user == null) {
            // 正常链路上 LoginInterceptor 已经挡掉未登录，这里是防御性分支
            throw new ForbiddenException("未登录");
        }
        if (!permissionService.hasAny(user.getUserId(), required.value())) {
            String reason = "无权限执行该操作，需要：" + String.join(" 或 ", required.value());
            recordDeniedWrite(request, user, reason);
            throw new ForbiddenException(reason);
        }
        request.setAttribute(CHECKED_ATTRIBUTE, Boolean.TRUE);
        return true;
    }

    /**
     * 越权写操作留痕。
     *
     * <p>审计切面 {@code AuditAspect} 织入的是 Controller 方法，而本拦截器在方法执行
     * <b>之前</b>就拒绝了请求，切面根本没被调用。若这里不补一条，「谁试图改、被拒了」
     * 就会从审计表里消失——恰恰是排查越权时最想看的信息。
     *
     * <p>只记 POST / PUT / DELETE，与 {@code AuditAspect} 的口径一致：
     * 查询不算「操作」，全部记下来只会把审计表淹掉。
     */
    private void recordDeniedWrite(HttpServletRequest request, LoginUser user, String reason) {
        String method = request.getMethod();
        if (!"POST".equals(method) && !"PUT".equals(method) && !"DELETE".equals(method)) {
            return;
        }
        opLogService.record(user.getUsername(), method, request.getRequestURI(), reason, false);
    }

    /** 取当前会话中的登录用户；未登录返回 null。 */
    private LoginUser currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = (session == null) ? null : session.getAttribute(LoginUser.SESSION_KEY);
        return (value instanceof LoginUser) ? (LoginUser) value : null;
    }
}
