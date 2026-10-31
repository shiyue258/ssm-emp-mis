/**
 * 配置层：Spring 根容器、SpringMVC 子容器与 Web 容器初始化。
 *
 * <p>只放 @Configuration 类与 Servlet 初始化器，不写业务逻辑。依赖方向：config 依赖其余各层做装配，其余各层不得反向依赖 config。
 */
package com.lab.emp.config;
