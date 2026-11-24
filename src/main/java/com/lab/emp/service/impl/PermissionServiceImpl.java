package com.lab.emp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.emp.entity.Permission;
import com.lab.emp.entity.Role;
import com.lab.emp.entity.RolePermission;
import com.lab.emp.entity.UserRole;
import com.lab.emp.mapper.PermissionMapper;
import com.lab.emp.mapper.RoleMapper;
import com.lab.emp.mapper.RolePermissionMapper;
import com.lab.emp.mapper.UserRoleMapper;
import com.lab.emp.service.PermissionService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限查询实现。
 *
 * <p>每次校验都查库（3 条查询：用户角色 → 角色 → 权限），不做会话缓存。
 * 取舍理由：角色与权限随时可能被管理员改动，缓存在 session 里会导致「已撤权但还能用」，
 * 这在权限系统里是最危险的错误方向。代价是每个受保护请求多 3 次索引查询，
 * 本项目量级下可忽略；真要优化应加带失效的本地缓存，而不是把权限塞进会话。
 *
 * <p>{@code ADMIN} 角色视为全量权限，直接返回所有权限点，避免给管理员逐条勾选。
 */
@Service
public class PermissionServiceImpl implements PermissionService {

    private static final String ADMIN = "ADMIN";

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;

    public PermissionServiceImpl(UserRoleMapper userRoleMapper, RoleMapper roleMapper,
                                 RolePermissionMapper rolePermissionMapper,
                                 PermissionMapper permissionMapper) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
    }

    @Override
    public Set<String> codesOf(Integer userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        List<UserRole> links = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId));
        if (links.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Integer> roleIds = links.stream().map(UserRole::getRoleId).collect(Collectors.toSet());

        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        if (roles.stream().anyMatch(r -> ADMIN.equals(r.getRoleCode()))) {
            return allCodes();
        }

        List<RolePermission> grants = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().in(RolePermission::getRoleId, roleIds));
        if (grants.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Integer> permIds = grants.stream().map(RolePermission::getPermId).collect(Collectors.toSet());
        return permissionMapper.selectBatchIds(permIds).stream()
                .map(Permission::getPermCode)
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public boolean hasAny(Integer userId, String... codes) {
        if (codes == null || codes.length == 0) {
            return true;
        }
        Set<String> owned = codesOf(userId);
        for (String code : codes) {
            if (owned.contains(code)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> allCodes() {
        return permissionMapper.selectList(null).stream()
                .map(Permission::getPermCode)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
