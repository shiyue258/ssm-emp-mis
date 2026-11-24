package com.lab.emp.service;

import java.util.Set;

/**
 * 权限查询。
 *
 * <p>只提供「查」：权限点由种子数据定义、角色分配由 {@link RoleService} 维护，
 * 这里负责把「某个账号当前拥有哪些权限」算出来。
 */
public interface PermissionService {

    /** 某账号拥有的权限编码集合；无角色或角色无权限时返回空集。 */
    Set<String> codesOf(Integer userId);

    /** 是否拥有指定权限之一。 */
    boolean hasAny(Integer userId, String... codes);
}
