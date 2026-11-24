package com.lab.emp.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口所需的权限编码，由 {@code PermissionAspect} 在调用前校验。
 *
 * <p>标在方法上而不是类上：同一个 Controller 里「查询」与「维护」往往分属不同权限，
 * 标在类上只能一刀切。类级注解仍然受支持（作为默认值），但本项目统一标在方法上。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 权限编码，如 {@code emp:write}；多个时按「任一满足」处理。 */
    String[] value();
}
