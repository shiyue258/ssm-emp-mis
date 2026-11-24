package com.lab.emp.controller;

import com.lab.emp.common.RequirePermission;
import com.lab.emp.common.Result;
import com.lab.emp.entity.Permission;
import com.lab.emp.entity.Role;
import com.lab.emp.service.RoleService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 角色与授权接口。
 *
 * <p>全部要求 {@code role:write}：授权本身就是最敏感的写操作，只读角色也没有存在的意义，
 * 因此不单独设 {@code role:read}。
 */
@RestController
@RequestMapping("/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @RequirePermission("role:write")
    public Result<List<Role>> list() {
        return Result.ok(roleService.list());
    }

    @GetMapping("/{id}")
    @RequirePermission("role:write")
    public Result<Role> get(@PathVariable("id") Integer id) {
        return Result.ok(roleService.getById(id));
    }

    @PostMapping
    @RequirePermission("role:write")
    public Result<Void> create(@RequestBody @Valid Role role) {
        roleService.create(role);
        return Result.ok();
    }

    @PutMapping
    @RequirePermission("role:write")
    public Result<Void> update(@RequestBody @Valid Role role) {
        if (role.getRoleId() == null) {
            return Result.fail("角色 ID 不能为空");
        }
        roleService.update(role);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("role:write")
    public Result<Void> remove(@PathVariable("id") Integer id) {
        roleService.remove(id);
        return Result.ok();
    }

    /** 全部权限点，供分配界面渲染勾选框。 */
    @GetMapping("/permissions")
    @RequirePermission("role:write")
    public Result<List<Permission>> permissions() {
        return Result.ok(roleService.listPermissions());
    }

    @GetMapping("/{id}/permissions")
    @RequirePermission("role:write")
    public Result<List<Integer>> permissionsOf(@PathVariable("id") Integer id) {
        return Result.ok(roleService.permissionIdsOf(id));
    }

    /** 覆盖式分配：请求体为权限 ID 列表，传空数组表示清空。 */
    @PutMapping("/{id}/permissions")
    @RequirePermission("role:write")
    public Result<Void> assignPermissions(@PathVariable("id") Integer id,
                                          @RequestBody List<Integer> permIds) {
        roleService.assignPermissions(id, permIds);
        return Result.ok();
    }
}
