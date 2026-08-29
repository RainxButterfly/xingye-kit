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
 * @since 2026-08-30
 */
package com.xingheyiye.xingye.kit.security.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import com.xingheyiye.xingye.kit.security.HashUtils;
import com.xingheyiye.xingye.kit.security.PasswordHasher;

/**
 * SHA-256 加盐密码哈希实现（轻量选择，非高强度密码存储推荐方案）。
 *
 * <p>一句话职责：以"随机盐 + SHA-256"产出可自描述参数的密码存储串，并以常量时间比较完成校验。</p>
 *
 * <p>存储格式（三段，以 "$" 分隔）：</p>
 * <pre>
 * sha256$&lt;Base64(salt)&gt;$&lt;Base64(hash)&gt;
 * 例如: sha256$Qxx...==$mVz...==
 * </pre>
 *
 * <p><b>安全定位（重要）</b>：SHA-256 为快速哈希，抗暴力破解弱于 PBKDF2/BCrypt/Argon2。
 * 本实现仅作为 {@link PasswordHasher} 的轻量内置选择，适用于对安全性要求不高的内部场景
 * （如工具内鉴权、低价值凭证、测试环境）；用户密码等敏感凭证请务必使用
 * {@link Pbkdf2PasswordHasher} 或实现 {@link PasswordHasher} 接入 BCrypt/Argon2。</p>
 *
 * <p>线程安全性：无可变共享状态（SecureRandom 线程安全），实例可多线程共享。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * PasswordHasher hasher = new Sha256PasswordHasher();
 * String stored = hasher.hash("p@ssw0rd");                        // 入库存储
 * boolean ok = hasher.verify("p@ssw0rd", stored);                 // true
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public class Sha256PasswordHasher implements PasswordHasher {

    /** 随机盐长度（字节） */
    private static final int SALT_LENGTH_BYTES = 16;

    /** 存储串首段固定标识 */
    private static final String FORMAT_PREFIX = "sha256";

    /** 存储串分段分隔符 */
    private static final String SEPARATOR = "$";

    /** 正则中的分隔符转义（"$" 为正则元字符） */
    private static final String SEPARATOR_REGEX = "\\$";

    /** 存储串固定段数：前缀、盐、哈希 */
    private static final int FORMAT_PARTS = 3;

    /** 格式非法统一提示 */
    private static final String UNRECOGNIZED = "unrecognized hash format";

    /** 共享安全随机源（线程安全） */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 哈希明文密码。
     *
     * <p>每次调用生成 16 字节随机盐，输出格式为 {@code sha256$<Base64(salt)>$<Base64(hash)>}；
     * 同一密码多次哈希结果不同，属正常现象。</p>
     *
     * @param rawPassword 明文密码，不能为 null（允许空串密码，但强烈不建议业务上接受）
     * @return 可入库存储的哈希串，不会为 null
     * @throws IllegalArgumentException rawPassword 为 null 时抛出
     * @throws IllegalStateException     SHA-256 算法不可用（JDK 内置，理论上不可达）
     */
    @Override
    public String hash(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("rawPassword 不能为 null");
        }
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] digest = sha256(rawPassword, salt);
        return FORMAT_PREFIX + SEPARATOR
                + Base64.getEncoder().encodeToString(salt)
                + SEPARATOR + Base64.getEncoder().encodeToString(digest);
    }

    /**
     * 校验明文密码与存储哈希是否匹配。
     *
     * <p>解析存储串三段（前缀、盐、哈希），按串内盐重算 SHA-256 后以
     * {@link HashUtils#constantTimeEquals(byte[], byte[])} 常量时间比较。</p>
     *
     * @param rawPassword 用户输入的明文密码，不能为 null
     * @param storedHash  {@link #hash(String)} 产生的存储串，不能为 null
     * @return true 表示密码匹配；false 表示不匹配
     * @throws IllegalArgumentException rawPassword 为 null，或 storedHash 为 null/段数不是 3/
     *                                  前缀不符/Base64 非法（即格式不符）时抛出，消息为
     *                                  "unrecognized hash format"
     */
    @Override
    public boolean verify(String rawPassword, String storedHash) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("rawPassword 不能为 null");
        }
        if (storedHash == null) {
            throw new IllegalArgumentException(UNRECOGNIZED);
        }
        String[] parts = storedHash.split(SEPARATOR_REGEX);
        if (parts.length != FORMAT_PARTS || !FORMAT_PREFIX.equals(parts[0])) {
            throw new IllegalArgumentException(UNRECOGNIZED);
        }
        byte[] salt;
        byte[] expected;
        try {
            salt = Base64.getDecoder().decode(parts[1]);
            expected = Base64.getDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(UNRECOGNIZED, e);
        }
        byte[] actual = sha256(rawPassword, salt);
        // 必须常量时间比较，防止逐字节比对泄露匹配前缀长度（时序攻击）
        return HashUtils.constantTimeEquals(actual, expected);
    }

    /**
     * 执行 SHA-256 计算。
     *
     * @param password 明文密码
     * @param salt     盐
     * @return 哈希字节数组（32 字节），不会为 null
     */
    private byte[] sha256(String password, byte[] salt) {
        byte[] salted = new byte[salt.length + password.getBytes(StandardCharsets.UTF_8).length];
        System.arraycopy(salt, 0, salted, 0, salt.length);
        System.arraycopy(password.getBytes(StandardCharsets.UTF_8), 0, salted, salt.length,
                password.getBytes(StandardCharsets.UTF_8).length);
        try {
            return MessageDigest.getInstance("SHA-256").digest(salted);
        } catch (NoSuchAlgorithmException e) {
            // JDK 内置 SHA-256，该异常理论上不可达
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
