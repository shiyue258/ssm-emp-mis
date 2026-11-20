package com.lab.emp.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * 公共字段自动填充：插入时填 createTime 与 updateTime，更新时只刷新 updateTime。
 *
 * <p><b>为什么不能只写这个类就完事。</b>在 MyBatis-Plus 的 Boot Starter 环境下，
 * 容器里的 {@code MetaObjectHandler} Bean 会被自动装配；但本项目是纯 SSM 全注解整合，
 * 没有 Starter，必须显式挂到 {@code GlobalConfig} 上（见 {@code SpringConfig#sqlSessionFactory}），
 * 否则这个类写完也不会被调用——不报错，时间字段永远是 null。
 *
 * <p>用 {@code strictInsertFill} 而不是 {@code setFieldValByName}：前者会检查该字段是否
 * 真的标了 {@code @TableField(fill = ...)}、且当前值为 null，只有两者都满足才写入。
 * 这样「业务方显式传了时间」时不会被覆盖，语义更安全。
 */
public class MyMetaObjectHandler implements MetaObjectHandler {

    private static final Logger log = LoggerFactory.getLogger(MyMetaObjectHandler.class);

    private static final String CREATE_TIME = "createTime";
    private static final String UPDATE_TIME = "updateTime";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, CREATE_TIME, LocalDateTime.class, now);
        strictInsertFill(metaObject, UPDATE_TIME, LocalDateTime.class, now);
        log.debug("插入填充：{} = {}", CREATE_TIME, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictUpdateFill(metaObject, UPDATE_TIME, LocalDateTime.class, now);
        log.debug("更新填充：{} = {}", UPDATE_TIME, now);
    }
}
