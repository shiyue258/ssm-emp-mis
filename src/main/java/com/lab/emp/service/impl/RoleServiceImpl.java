package com.lab.emp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.emp.common.BusinessException;
import com.lab.emp.entity.Permission;
import com.lab.emp.entity.Role;
import com.lab.emp.entity.RolePermission;
import com.lab.emp.entity.UserRole;
import com.lab.emp.mapper.PermissionMapper;
import com.lab.emp.mapper.RoleMapper;
import com.lab.emp.mapper.RolePermissionMapper;
import com.lab.emp.mapper.UserRoleMapper;
import com.lab.emp.service.RoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色与授权维护。
 *
 * <p>三条防呆，都针对「把自己锁在门外」：
 * <ul>
 *   <li>内置 {@code ADMIN} 角色不可删除；</li>
 *   <li>角色仍被账号绑定时不可删除；</li>
 *   <li>不允许把最后一个持有 ADMIN 的账号改成别的角色。</li>
 * </ul>
 * 第三条的逻辑是：权限系统一旦没有管理员，就再也改不回来（没有后门）。
 */
@Service
public class RoleServiceImpl implements RoleService {

    private static final String ADMIN = "ADMIN";

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;

    public RoleServiceImpl(RoleMapper roleMapper, PermissionMapper permissionMapper,
                           RolePermissionMapper rolePermissionMapper, UserRoleMapper userRoleMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public List<Role> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<Role>().orderByAsc(Role::getRoleId));
    }

    @Override
    public Role getById(Integer roleId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在：" + roleId);
        }
        return role;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(Role role) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, role.getRoleCode()));
        if (count != null && count > 0) {
            throw new BusinessException("角色编码已存在：" + role.getRoleCode());
        }
        Role entity = new Role();
        entity.setRoleCode(role.getRoleCode());
        entity.setRoleName(role.getRoleName());
        entity.setRemark(role.getRemark());
        roleMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Role role) {
        Role existing = getById(role.getRoleId());
        if (ADMIN.equals(existing.getRoleCode()) && !ADMIN.equals(role.getRoleCode())) {
            throw new BusinessException("内置管理员角色的编码不可修改");
        }
        Role update = new Role();
        update.setRoleId(existing.getRoleId());
        update.setRoleCode(role.getRoleCode());
        update.setRoleName(role.getRoleName());
        update.setRemark(role.getRemark());
        roleMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Integer roleId) {
        Role role = getById(roleId);
        if (ADMIN.equals(role.getRoleCode())) {
            throw new BusinessException("内置管理员角色不能删除");
        }
        Long bound = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getRoleId, roleId));
        if (bound != null && bound > 0) {
            throw new BusinessException("该角色仍被 " + bound + " 个账号使用，不能删除");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId));
        roleMapper.deleteById(roleId);
    }

    @Override
    public List<Permission> listPermissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<Permission>()
                .orderByAsc(Permission::getModule).orderByAsc(Permission::getPermCode));
    }

    @Override
    public List<Integer> permissionIdsOf(Integer roleId) {
        getById(roleId);
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, roleId))
                .stream().map(RolePermission::getPermId).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Integer roleId, List<Integer> permIds) {
        getById(roleId);
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId));
        if (permIds == null || permIds.isEmpty()) {
            return;
        }
        for (Integer permId : permIds.stream().distinct().collect(Collectors.toList())) {
            RolePermission link = new RolePermission();
            link.setRoleId(roleId);
            link.setPermId(permId);
            rolePermissionMapper.insert(link);
        }
    }

    @Override
    public List<Integer> roleIdsOf(Integer userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId))
                .stream().map(UserRole::getRoleId).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Integer userId, List<Integer> roleIds) {
        List<Integer> targets = (roleIds == null) ? java.util.Collections.emptyList()
                : roleIds.stream().distinct().collect(Collectors.toList());

        // 若该账号当前是管理员，而新角色里没有 ADMIN，需要确认「还有别的管理员」
        List<Integer> current = roleIdsOf(userId);
        boolean wasAdmin = !current.isEmpty() && isAdminRoleIds(current);
        boolean willBeAdmin = !targets.isEmpty() && isAdminRoleIds(targets);
        if (wasAdmin && !willBeAdmin) {
            long admins = countOtherAdmins(userId);
            if (admins == 0) {
                throw new BusinessException("这是最后一个管理员账号，不能取消其管理员角色");
            }
        }

        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        for (Integer roleId : targets) {
            getById(roleId);
            UserRole link = new UserRole();
            link.setUserId(userId);
            link.setRoleId(roleId);
            userRoleMapper.insert(link);
        }
    }

    private boolean isAdminRoleIds(List<Integer> roleIds) {
        return roleMapper.selectBatchIds(roleIds).stream()
                .anyMatch(r -> ADMIN.equals(r.getRoleCode()));
    }

    /** 统计除指定账号外，还有多少个账号持有 ADMIN。 */
    private long countOtherAdmins(Integer userId) {
        List<Integer> adminRoleIds = roleMapper.selectList(new LambdaQueryWrapper<Role>()
                        .eq(Role::getRoleCode, ADMIN))
                .stream().map(Role::getRoleId).collect(Collectors.toList());
        if (adminRoleIds.isEmpty()) {
            return 0;
        }
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .in(UserRole::getRoleId, adminRoleIds)
                        .ne(UserRole::getUserId, userId))
                .stream().map(UserRole::getUserId).distinct().count();
    }
}
