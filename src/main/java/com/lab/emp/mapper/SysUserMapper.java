package com.lab.emp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.emp.entity.SysUser;

/**
 * 登录用户持久层，仅提供 BaseMapper 的通用能力。
 *
 * <p>按用户名查询用 {@code LambdaQueryWrapper} 在 Service 层表达即可，
 * 不必为一条简单条件单独写 XML。
 */
public interface SysUserMapper extends BaseMapper<SysUser> {
}
