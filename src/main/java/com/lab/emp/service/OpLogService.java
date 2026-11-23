package com.lab.emp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.emp.entity.OpLog;

/** 操作审计业务接口。 */
public interface OpLogService {

    /**
     * 记录一条审计。
     *
     * <p>实现必须保证：<b>审计失败不得影响业务</b>。所以它会捕获自身异常，
     * 只往控制台打一条 error，不向上抛。
     *
     * @param success 业务是否成功；失败也要留痕
     */
    void record(String operator, String action, String target, String detail, boolean success);

    /** 分页查询审计记录，可按操作人模糊过滤。 */
    Page<OpLog> page(String operator, int page, int size);
}
