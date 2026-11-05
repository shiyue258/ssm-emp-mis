package com.lab.emp.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 员工列表查询条件。
 *
 * <p>与实体分离的原因：查询条件里的 {@code name} 是模糊匹配关键字，而实体里的
 * {@code empName} 是完整字段值，两者语义不同，混用会让「传 name=张 就按姓名精确匹配」
 * 这种误解反复出现。
 *
 * <p>分页参数的边界约定写在 {@link #toPageNo()} 与 {@link #toPageSize()} 里：
 * 非法输入不抛异常，而是收敛到安全区间。理由是分页参数来自 URL，属于用户可随手改的输入，
 * 用异常回应会让前端难以处理；收敛后再查询，行为可预测且不会拖垮数据库。
 */
@Data
public class EmpQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 默认页码 */
    public static final int DEFAULT_PAGE = 1;

    /** 默认每页条数，与指导书接口示例一致 */
    public static final int DEFAULT_SIZE = 5;

    /** 每页条数上限，防止 size=999999 把整表拉进内存 */
    public static final int MAX_SIZE = 100;

    /** 姓名关键字，前后模糊 */
    private String name;

    /** 性别：男 / 女，精确匹配 */
    private String gender;

    /** 部门，精确匹配 */
    private String dept;

    /** 页码，从 1 开始 */
    private Integer page;

    /** 每页条数 */
    private Integer size;

    /**
     * 规范化后的页码：null、0、负数一律回落到第 1 页。
     */
    public int toPageNo() {
        return (page == null || page < 1) ? DEFAULT_PAGE : page;
    }

    /**
     * 规范化后的每页条数：null、0、负数回落到默认值，超过上限则截断到 {@link #MAX_SIZE}。
     */
    public int toPageSize() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
