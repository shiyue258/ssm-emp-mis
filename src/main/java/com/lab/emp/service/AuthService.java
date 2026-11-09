package com.lab.emp.service;

import com.lab.emp.common.LoginUser;

/**
 * 登录业务接口。
 *
 * <p>返回 {@link LoginUser} 而不是实体，是为了让会话层拿到的东西最小化——
 * 口令摘要与账号状态不应该离开本层。
 */
public interface AuthService {

    /**
     * 校验账号口令。
     *
     * @param username 登录名
     * @param password 明文口令，本方法内部做摘要比对，不会持久化
     * @return 登录成功后的会话用户信息
     * @throws com.lab.emp.common.BusinessException 账号不存在、已停用或口令错误
     */
    LoginUser login(String username, String password);
}
