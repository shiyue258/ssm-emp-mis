package com.lab.emp.service;

import com.lab.emp.entity.Permission;
import com.lab.emp.entity.Role;

import java.util.List;

/** 角色与授权的维护接口。 */
public interface RoleService {

    List<Role> list();

    Role getById(Integer roleId);

    void create(Role role);

    void update(Role role);

    /** 删除角色：内置 ADMIN 不可删；仍有账号绑定时拒绝。 */
    void remove(Integer roleId);

    /** 全部权限点（按模块、编码排序），供分配界面勾选。 */
    List<Permission> listPermissions();

    /** 某角色已勾选的权限 ID。 */
    List<Integer> permissionIdsOf(Integer roleId);

    /** 覆盖式分配：先清空该角色的授权，再按传入集合重建。 */
    void assignPermissions(Integer roleId, List<Integer> permIds);

    /** 某账号的角色 ID 列表。 */
    List<Integer> roleIdsOf(Integer userId);

    /** 覆盖式分配账号角色；不允许把最后一个管理员降级。 */
    void assignRoles(Integer userId, List<Integer> roleIds);
}
