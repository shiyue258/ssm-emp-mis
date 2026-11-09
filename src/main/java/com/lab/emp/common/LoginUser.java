package com.lab.emp.common;

import java.io.Serializable;

/**
 * 登录态载体：只放会话必须知道的三个字段。
 *
 * <p>刻意不放 {@code passwordHash}、{@code status} 等字段——session 在服务端虽然不直接暴露，
 * 但一旦有人写出「把 session 属性整个序列化返回」这种代码，多余字段就会一起泄露。
 * 需要更多信息时按 id 重新查库，而不是把整行实体塞进会话。
 */
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** session 中的属性名，拦截器与控制器都引用这个常量，避免字符串写错。 */
    public static final String SESSION_KEY = "loginUser";

    private Integer userId;

    private String username;

    private String displayName;

    public LoginUser() {
    }

    public LoginUser(Integer userId, String username, String displayName) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return "LoginUser{userId=" + userId + ", username='" + username
                + "', displayName='" + displayName + "'}";
    }
}
