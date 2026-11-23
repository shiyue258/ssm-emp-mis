package com.lab.emp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lab.emp.common.BusinessException;
import com.lab.emp.common.LoginUser;
import com.lab.emp.common.PasswordEncoder;
import com.lab.emp.entity.SysUser;
import com.lab.emp.mapper.SysUserMapper;
import com.lab.emp.service.AuthService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 登录业务实现。
 *
 * <p>三点设计说明：
 * <ol>
 *   <li><b>口令不落明文</b>：库里存的是 SHA-256 摘要，比对时把用户输入的口令做同样运算
 *       再比摘要。这样即使数据库被导出，也拿不到原始口令。</li>
 *   <li><b>不区分「用户不存在」与「口令错误」</b>：两者返回同一句提示。若分别提示，
 *       攻击者可以用它枚举系统里有哪些用户名。</li>
 *   <li><b>停用账号单独提示</b>：这里的取舍是可用性优先——用户需要知道「不是密码输错了，
 *       是账号被停用」，否则会反复重试。真实系统若担心枚举，应改成同样模糊的提示。</li>
 * </ol>
 *
 * <p>关于哈希算法：SHA-256 是快速散列，不加盐时对彩虹表抵抗力弱，生产环境应使用
 * BCrypt / Argon2 这类慢哈希。本项目选 SHA-256 是因为它只依赖 JDK 标准库，
 * 不需额外依赖，同时足以表达「口令不以明文存储」这一设计意图。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;

    public AuthServiceImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public LoginUser login(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new BusinessException("用户名或密码不能为空");
        }

        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));

        // 用户不存在与口令错误合并为同一提示，避免用户名枚举
        if (user == null || !PasswordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("账号已停用，请联系管理员");
        }

        return new LoginUser(user.getUserId(), user.getUsername(), user.getDisplayName());
    }
}
