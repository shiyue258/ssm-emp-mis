/**
 * 拦截器：登录态校验与放行规则。
 *
 * <p>基于 HandlerInterceptor，在 preHandle 阶段拦截未登录请求并返回 401。
 */
package com.lab.emp.interceptor;
