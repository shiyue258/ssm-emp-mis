package com.lab.emp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.emp.common.LoginUser;
import com.lab.emp.common.Result;
import com.lab.emp.dto.PasswordResetDTO;
import com.lab.emp.dto.UserCreateDTO;
import com.lab.emp.dto.UserUpdateDTO;
import com.lab.emp.entity.SysUser;
import com.lab.emp.service.UserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;

/**
 * 账号管理接口。
 *
 * <p>分页返回 {@code SysUser}，但实体上的 {@code passwordHash} 已标 {@code @JsonIgnore}，
 * 不会出现在响应里——这是「不让摘要外泄」的最后一道保险，比在 Controller 里手工剔除可靠。
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Result<Page<SysUser>> page(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Integer status,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        return Result.ok(userService.page(keyword, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<SysUser> get(@PathVariable("id") Integer id) {
        return Result.ok(userService.getById(id));
    }

    @PostMapping
    public Result<Void> create(@RequestBody @Valid UserCreateDTO dto) {
        userService.create(dto);
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@RequestBody @Valid UserUpdateDTO dto, HttpSession session) {
        userService.update(dto, currentUserId(session));
        return Result.ok();
    }

    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable("id") Integer id,
                                      @RequestBody @Valid PasswordResetDTO dto) {
        userService.resetPassword(id, dto.getNewPassword());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable("id") Integer id, HttpSession session) {
        userService.remove(id, currentUserId(session));
        return Result.ok();
    }

    /** 拦截器已保证受保护接口一定带登录态；取不到时返回 null，业务层不会误判为「就是自己」。 */
    private Integer currentUserId(HttpSession session) {
        Object value = (session == null) ? null : session.getAttribute(LoginUser.SESSION_KEY);
        return (value instanceof LoginUser) ? ((LoginUser) value).getUserId() : null;
    }
}
