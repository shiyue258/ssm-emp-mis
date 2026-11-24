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

    /**
     * 按当前考勤重算某月**待发放**记录的扣款与实发。
     *
     * <p>存在的理由：{@link #generate} 是幂等的（已存在即跳过），所以「先算薪、后补录考勤」
     * 时，薪资不会自动跟随。重算提供一条显式的修正通道——只动待发放记录，
     * 已发放的一律跳过，避免已出的账被悄悄改动。
     *
     * <p>重算会覆盖手工调整过的扣款。这是有意的：重算的语义就是「以考勤为准」，
     * 若要保留人工调整，就不要点重算。
     */
    SalaryGenerateResult recalculate(String month);

    /** 调整奖金/扣款，实发金额由服务端重算。已发放的记录不允许修改。 */
    void update(SalaryRecord record);

    /** 标记为已发放。 */
    void pay(Integer id);

    /** 删除待发放的记录；已发放的不允许删除。 */
    void remove(Integer id);
}
