/**
 * 业务层实现：事务边界所在层。
 *
 * <p>@Transactional 只标在这一层的方法上；实现类不直接对外暴露，由接口注入使用，以便 Spring 生成代理。
 */
package com.lab.emp.service.impl;
