package com.lab.emp.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 口令摘要工具。
 *
 * <p>从 {@code AuthServiceImpl} 抽出来是因为现在有两个地方要算摘要：登录比对与
 * 账号管理。两处各写一份实现，迟早出现「新增账号用的算法与登录校验不一致」这种
 * 最难查的问题——表现是口令明明输对了却登不进去。
 *
 * <p>算法是 SHA-256 单次散列，不带盐。这是本课程的取舍：不引入 BCrypt 等额外依赖。
 * 代价是抗彩虹表能力弱于带盐慢哈希，已在 {@code engineering-checklist} 的
 * 「未做到」一节点明。
 */
public final class PasswordEncoder {

    private PasswordEncoder() {
    }

    /** 计算明文口令的 SHA-256 摘要，输出小写十六进制。 */
    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 必然支持 SHA-256，走到这里说明运行环境异常
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }

    /** 校验明文口令与摘要是否匹配。 */
    public static boolean matches(String raw, String hash) {
        return raw != null && hash != null && sha256Hex(raw).equalsIgnoreCase(hash);
    }
}
