package com.lab.emp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.emp.dto.SalaryGenerateResult;
import com.lab.emp.entity.SalaryRecord;

/** 薪资业务接口。 */
public interface SalaryService {

    Page<SalaryRecord> page(Integer empId, String month, int page, int size);

    SalaryRecord getById(Integer id);

    /**
     * 按月批量生成薪资。
     *
     * <p>幂等：已存在记录的员工跳过，不覆盖已有数据——否则调整过奖金的记录
     * 会被重跑一次生成打回原值。
     */
    SalaryGenerateResult generate(String month);

    /** 调整奖金/扣款，实发金额由服务端重算。已发放的记录不允许修改。 */
    void update(SalaryRecord record);

    /** 标记为已发放。 */
    void pay(Integer id);

    /** 删除待发放的记录；已发放的不允许删除。 */
    void remove(Integer id);
}
