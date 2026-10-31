/**
 * 持久层：继承 MyBatis-Plus 的 BaseMapper，按需补充自定义 SQL。
 *
 * <p>简单条件用 LambdaQueryWrapper 表达，复杂动态 SQL 走同包 XML。依赖方向：mapper → entity。
 */
package com.lab.emp.mapper;
