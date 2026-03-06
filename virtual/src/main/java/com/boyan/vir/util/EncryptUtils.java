package com.boyan.vir.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class EncryptUtils {
    // 加密算法：MD5（简单、不可逆，适合生成唯一标识），也可改为 "SHA-256"
    private static final String ALGORITHM = "MD5";

    /**
     * 将字符串加密为十六进制字符串
     * @param input 待加密字符串
     * @return 加密后的十六进制字符串（32位MD5/64位SHA-256）
     */
    public static String encryptToHex(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("加密字符串不能为空");
        }
        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            // 转换为字节数组并计算摘要
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            // 转十六进制字符串（避免乱码，保证可读性）
            StringBuilder hexStr = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexStr.append('0'); // 补零，保证32位长度
                }
                hexStr.append(hex);
            }
            return hexStr.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("加密算法不存在", e);
        }
    }
}