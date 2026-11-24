package com.lab.emp.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 批量生成薪资的结果。
 *
 * <p>把「生成了几条」与「跳过几条」分开返回，而不是只回一个总数：
 * 跳过意味着该员工当月已有记录（幂等），与「没有在职员工」是完全不同的情况，
 * 只回一个数字会让调用方分不清。
 */
@Data
public class SalaryGenerateResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 薪资月份。 */
    private String month;

    /** 本次新生成的条数。 */
    private int generated;

    /** 因已存在而跳过的条数。 */
    private int skipped;

    /**
     * 本次生成记录的考勤扣款合计。
     *
     * <p>单独回传这个数，是为了让调用方一眼看出「这批薪资里有多少是被考勤扣掉的」，
     * 而不是逐条去读 deduction 相加——生成结果本身就是给人看的汇总。
     */
    private BigDecimal deductedTotal;
}
