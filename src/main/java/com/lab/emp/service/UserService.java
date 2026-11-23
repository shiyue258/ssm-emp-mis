package com.lab.emp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.emp.dto.UserCreateDTO;
import com.lab.emp.dto.UserUpdateDTO;
import com.lab.emp.entity.SysUser;

/** 账号管理业务接口。 */
public interface UserService {

    /** 分页查询，keyword 匹配用户名或显示名。 */
    Page<SysUser> page(String keyword, Integer status, int page, int size);

    SysUser getById(Integer userId);

    /** 新增账号，用户名重复时拒绝。 */
    void create(UserCreateDTO dto);

    /**
     * 修改显示名与状态。
     *
     * @param currentUserId 当前登录用户，用于阻止「把自己停用」
     */
    void update(UserUpdateDTO dto, Integer currentUserId);

    /** 重置口令。 */
    void resetPassword(Integer userId, String newPassword);

    /**
     * 删除账号。
     *
     * @param currentUserId 当前登录用户，用于阻止「删自己」与「删掉最后一个启用账号」
     */
    void remove(Integer userId, Integer currentUserId);
}
