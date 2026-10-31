/**
 * 切面：请求日志等横切关注点。
 *
 * <p>基于 Spring AOP 的 @Aspect，围绕 controller 包的方法织入，记录入参、耗时与异常，不修改业务返回值。
 */
package com.lab.emp.aspect;
