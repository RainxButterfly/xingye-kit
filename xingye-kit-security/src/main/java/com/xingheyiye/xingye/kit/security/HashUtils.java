/*
 * Copyright (c) 2026 星河一叶 (RainxButterfly)
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-29
 */
package com.xingheyiye.xingye.kit.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 哈希与安全比较工具：SHA-256/SHA-512 摘要、HMAC-SHA256、随机盐与常量时间比较。
 *
 * <p>一句话职责：提供消息摘要、带密钥摘要与抗时序攻击的字节比较三类基础安全原语。</p>
 *
 * <p>适用场景：数据完整性校验、API 签名、盐值生成、密码/签名比对。</p>
 *
 * <p>线程安全性：全静态方法且无共享可变状态（MessageDigest/Mac 每次调用新建，
 * SecureRandom 自身线程安全），可在多线程环境并发调用。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * String digest = HashUtils.sha256Hex("hello");
 * String mac = HashUtils.hmacSha256Hex("secret-key", "hello");
 * String salt = HashUtils.randomSaltHex(16);
 * boolean same = HashUtils.constantTimeEquals(a.getBytes(StandardCharsets.UTF_8),
 *                                             b.getBytes(StandardCharsets.UTF_8));
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-29
 */
public final class HashUtils {

    /** SHA-256 算法名 */
    private static final String SHA_256 = "SHA-256";

    /** SHA-512 算法名 */
    private static final String SHA_512 = "SHA-512";

    /** HMAC-SHA256 算法名 */
    private static final String HMAC_SHA_256 = "HmacSHA256";

    /** 小写十六进制字符表 */
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /** 共享安全随机源（线程安全） */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private HashUtils() {
    }

    /**
     * 计算 SHA-256 摘要（小写十六进制）。
     *
     * @param text 原文，不能为 null；按 UTF-8 编码
     * @return 64 个字符的小写十六进制摘要，不会为 null
     * @throws IllegalArgumentException text 为 null 时抛出
     */
    public static String sha256Hex(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text 不能为 null");
        }
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算 SHA-256 摘要（小写十六进制）。
     *
     * @param data 原始字节，不能为 null
     * @return 64 个字符的小写十六进制摘要，不会为 null
     * @throws IllegalArgumentException data 为 null 时抛出
     */
    public static String sha256Hex(byte[] data) {
        return digestHex(SHA_256, data);
    }

    /**
     * 计算 SHA-512 摘要（小写十六进制）。
     *
     * @param text 原文，不能为 null；按 UTF-8 编码
     * @return 128 个字符的小写十六进制摘要，不会为 null
     * @throws IllegalArgumentException text 为 null 时抛出
     */
    public static String sha512Hex(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text 不能为 null");
        }
        return digestHex(SHA_512, text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算 HMAC-SHA256（小写十六进制）。
     *
     * @param secret HMAC 密钥，不能为 null；按 UTF-8 编码
     * @param data   待认证的数据，不能为 null；按 UTF-8 编码
     * @return 64 个字符的小写十六进制 MAC 值，不会为 null
     * @throws IllegalArgumentException secret 或 data 为 null 时抛出
     */
    public static String hmacSha256Hex(String secret, String data) {
        if (secret == null || data == null) {
            throw new IllegalArgumentException("secret 与 data 均不能为 null");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return toHex(result);
        } catch (GeneralSecurityException e) {
            // JDK 内置 HmacSHA256，该异常理论上不可达
            throw new IllegalStateException("HmacSHA256 计算失败", e);
        }
    }

    /**
     * 生成随机盐（小写十六进制）。
     *
     * @param bytes 随机字节数，必须大于 0；常用 16 字节（输出 32 个十六进制字符）
     * @return 2×bytes 长度的小写十六进制字符串，不会为 null
     * @throws IllegalArgumentException bytes 小于等于 0 时抛出
     */
    public static String randomSaltHex(int bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("盐字节数必须大于 0，当前: " + bytes);
        }
        byte[] salt = new byte[bytes];
        SECURE_RANDOM.nextBytes(salt);
        return toHex(salt);
    }

    /**
     * 常量时间比较两个字节数组是否相同。
     *
     * <p>为什么必须常量时间：普通的逐字节比较在遇到第一个不同字节时立即返回，
     * 总耗时与"匹配的前缀长度"相关，攻击者可据此逐字节爆破出正确值（时序攻击）；
     * {@link MessageDigest#isEqual(byte[], byte[])} 无论差异出现在何处都消耗相同时间，
     * 是比较密钥、密码哈希、签名等敏感数据的正确姿势。</p>
     *
     * @param a 待比较数组，可为 null（null 仅与 null 视为相等）
     * @param b 待比较数组，可为 null（null 仅与 null 视为相等）
     * @return true 表示内容与长度完全一致
     */
    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        // MessageDigest.isEqual 内部为常量时间实现，见方法 Javadoc 的攻击原理说明
        return MessageDigest.isEqual(a, b);
    }

    /**
     * 计算指定算法的摘要并转十六进制。
     *
     * @param algorithm 摘要算法名（JCA 标准名）
     * @param data      原始字节，不能为 null
     * @return 小写十六进制摘要，不会为 null
     */
    private static String digestHex(String algorithm, byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data 不能为 null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return toHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            // JDK 必备 SHA-256/SHA-512，该异常理论上不可达
            throw new IllegalStateException("摘要算法不可用: " + algorithm, e);
        }
    }

    /**
     * 字节数组转小写十六进制字符串。
     *
     * @param bytes 字节数组
     * @return 小写十六进制字符串，不会为 null
     */
    private static String toHex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            result[i * 2] = HEX_CHARS[value >>> 4];
            result[i * 2 + 1] = HEX_CHARS[value & 0x0F];
        }
        return new String(result);
    }
}
