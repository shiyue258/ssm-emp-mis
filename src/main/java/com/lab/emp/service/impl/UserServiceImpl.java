package com.lab.emp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.emp.common.BusinessException;
import com.lab.emp.common.PasswordEncoder;
import com.lab.emp.dto.UserCreateDTO;
import com.lab.emp.dto.UserUpdateDTO;
import com.lab.emp.entity.SysUser;
import com.lab.emp.mapper.SysUserMapper;
import com.lab.emp.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 账号管理实现。
 *
 * <p>三处防呆是刻意加的，都是「管理员手一滑就把自己锁在门外」的场景：
 * <ul>
 *   <li>不能停用当前登录账号；</li>
 *   <li>不能删除当前登录账号；</li>
 *   <li>不能删掉最后一个启用状态的账号。</li>
 * </ul>
 * 第三条尤其重要：本系统没有「通过邮箱找回口令」的通道，一旦启用账号归零，
 * 就只能进数据库手工改。
 */
@Service
public class UserServiceImpl implements UserService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    private final SysUserMapper sysUserMapper;

    public UserServiceImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public Page<SysUser> page(String keyword, Integer status, int page, int size) {
        int pageNo = page < 1 ? 1 : page;
        int pageSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getDisplayName, keyword));
        }
        wrapper.eq(status != null, SysUser::getStatus, status)
                .orderByAsc(SysUser::getUserId);
        return sysUserMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
    }

    @Override
    public SysUser getById(Integer userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("账号不存在：" + userId);
        }
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(UserCreateDTO dto) {
        ensureUsernameAvailable(dto.getUsername());
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPasswordHash(PasswordEncoder.sha256Hex(dto.getPassword()));
        user.setDisplayName(dto.getDisplayName());
        user.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        sysUserMapper.insert(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UserUpdateDTO dto, Integer currentUserId) {
        SysUser existing = getById(dto.getUserId());
        if (dto.getStatus() != null && dto.getStatus() == 0
                && dto.getUserId().equals(currentUserId)) {
            throw new BusinessException("不能停用当前登录的账号");
        }
        SysUser update = new SysUser();
        update.setUserId(existing.getUserId());
        update.setDisplayName(dto.getDisplayName());
        update.setStatus(dto.getStatus());
        sysUserMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Integer userId, String newPassword) {
        getById(userId);
        SysUser update = new SysUser();
        update.setUserId(userId);
        update.setPasswordHash(PasswordEncoder.sha256Hex(newPassword));
        sysUserMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Integer userId, Integer currentUserId) {
        getById(userId);
        if (userId.equals(currentUserId)) {
            throw new BusinessException("不能删除当前登录的账号");
        }
        // 统计「除自己以外还有几个启用账号」；为 0 则删完就没人能登录了
        Long remaining = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .ne(SysUser::getUserId, userId));
        if (remaining == null || remaining == 0) {
            throw new BusinessException("这是最后一个启用状态的账号，不能删除");
        }
        sysUserMapper.deleteById(userId);
    }

    private void ensureUsernameAvailable(String username) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (count != null && count > 0) {
            throw new BusinessException("用户名已存在：" + username);
        }
    }
}
