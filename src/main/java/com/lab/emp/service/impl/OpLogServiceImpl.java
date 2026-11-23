package com.lab.emp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.emp.entity.OpLog;
import com.lab.emp.mapper.OpLogMapper;
import com.lab.emp.service.OpLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 操作审计实现。
 *
 * <p>两个关键点：
 * <ol>
 *   <li><b>{@code REQUIRES_NEW}</b>：审计写入必须独立于业务事务。若共用同一个事务，
 *       业务失败回滚时会把「这次操作失败了」的记录一起滚掉——而失败恰恰是最需要
 *       留痕的情形：谁试图改、为什么没改成。</li>
 *   <li><b>吞掉自身异常</b>：审计是旁路能力，不能因为写日志失败就把业务搞挂。
 *       写入异常只记控制台 error，不向上传播。</li>
 * </ol>
 */
@Service
public class OpLogServiceImpl implements OpLogService {

    private static final Logger log = LoggerFactory.getLogger(OpLogServiceImpl.class);

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    private final OpLogMapper opLogMapper;

    public OpLogServiceImpl(OpLogMapper opLogMapper) {
        this.opLogMapper = opLogMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(String operator, String action, String target, String detail, boolean success) {
        try {
            OpLog entry = new OpLog();
            entry.setOperator(StringUtils.hasText(operator) ? operator : "anonymous");
            entry.setAction(action);
            entry.setTarget(target);
            entry.setDetail(truncate(detail));
            entry.setResult(success ? 1 : 0);
            entry.setCreateTime(java.time.LocalDateTime.now());
            opLogMapper.insert(entry);
        } catch (Exception e) {
            // 审计失败不影响业务，只留一条控制台痕迹
            log.error("写入操作审计失败：{} {} {}", action, target, e.getMessage());
        }
    }

    @Override
    public Page<OpLog> page(String operator, int page, int size) {
        int pageNo = page < 1 ? 1 : page;
        int pageSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        LambdaQueryWrapper<OpLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(operator), OpLog::getOperator, operator)
                .orderByDesc(OpLog::getLogId);
        return opLogMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
    }

    /** detail 列是 VARCHAR(500)，超长会导致插入失败。 */
    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 500 ? text : text.substring(0, 497) + "...";
    }
}
