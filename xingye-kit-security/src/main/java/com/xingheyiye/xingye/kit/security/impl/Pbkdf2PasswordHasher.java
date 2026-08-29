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
package com.xingheyiye.xingye.kit.security.impl;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.xingheyiye.xingye.kit.security.HashUtils;
import com.xingheyiye.xingye.kit.security.PasswordHasher;

/**
 * PBKDF2 密码哈希实现（默认 PBKDF2WithHmacSHA256，21 万次迭代，256 位输出）。
 *
 * <p>一句话职责：以"随机盐 + PBKDF2"产出可自描述参数的密码存储串，并以常量时间比较完成校验。</p>
 *
 * <p>存储格式（四段，以 "$" 分隔）：</p>
 * <pre>
 * pbkdf2-sha256$&lt;iterations&gt;$&lt;Base64(salt)&gt;$&lt;Base64(hash)&gt;
 * 例如: pbkdf2-sha256$210000$Qxx...==$mVz...==
 * </pre>
 *
 * <p>重要说明：因每次哈希使用随机盐，同一密码两次 {@link #hash(String)} 的结果不同，
 * 校验必须且只能通过 {@link #verify(String, String)} 完成（解析存储串中的参数后重算并常量时间比较），
 * 切勿直接比对两次 hash 输出。</p>
 *
 * <p>适用场景：JDK 环境下的用户密码存储；需要 BCrypt/Argon2 时请实现 {@link PasswordHasher} 接入第三方库。</p>
 *
 * <p>线程安全性：无可变共享状态（SecureRandom 线程安全），实例可多线程共享。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher();       // 默认参数
 * String stored = hasher.hash("p@ssw0rd");                        // 入库存储
 * boolean ok = hasher.verify("p@ssw0rd", stored);                 // true
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-29
 */
public class Pbkdf2PasswordHasher implements PasswordHasher {

    /** 默认迭代次数（次）。OWASP 建议 PBKDF2-HMAC-SHA256 不少于 60 万次，此处取 21 万次为折中值 */
    public static final int DEFAULT_ITERATION_COUNT = 210000;

    /** 默认输出哈希长度（位） */
    public static final int DEFAULT_KEY_LENGTH_BITS = 256;

    /** 默认 KDF 算法（JDK 内置） */
    public static final String DEFAULT_ALGORITHM = "PBKDF2WithHmacSHA256";

    /** 随机盐长度（字节） */
    private static final int SALT_LENGTH_BYTES = 16;

    /** 存储串首段固定标识（格式历史标识，保持不变以兼容既有存量数据） */
    private static final String FORMAT_PREFIX = "pbkdf2-sha256";

    /** 存储串分段分隔符 */
    private static final String SEPARATOR = "$";

    /** 正则中的分隔符转义（"$" 为正则元字符） */
    private static final String SEPARATOR_REGEX = "\\$";

    /** 存储串固定段数：前缀、迭代次数、盐、哈希 */
    private static final int FORMAT_PARTS = 4;

    /** 格式非法统一提示 */
    private static final String UNRECOGNIZED = "unrecognized hash format";

    /** 共享安全随机源（线程安全） */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 迭代次数（次） */
    private final int iterationCount;

    /** 输出哈希长度（位） */
    private final int keyLengthBits;

    /** KDF 算法名（如 PBKDF2WithHmacSHA256/WithHmacSHA512） */
    private final String algorithm;

    /**
     * 以默认参数构造：PBKDF2WithHmacSHA256、210000 次迭代、256 位输出。
     */
    public Pbkdf2PasswordHasher() {
        this(DEFAULT_ITERATION_COUNT, DEFAULT_KEY_LENGTH_BITS, DEFAULT_ALGORITHM);
    }

    /**
     * 指定迭代次数构造，其余参数取默认值。
     *
     * @param iterationCount 迭代次数（次），必须大于 0
     * @throws IllegalArgumentException iterationCount 小于等于 0 时抛出
     */
    public Pbkdf2PasswordHasher(int iterationCount) {
        this(iterationCount, DEFAULT_KEY_LENGTH_BITS, DEFAULT_ALGORITHM);
    }

    /**
     * 指定迭代次数与输出长度构造，算法取默认值。
     *
     * @param iterationCount 迭代次数（次），必须大于 0
     * @param keyLengthBits  输出哈希长度（位），必须大于 0 且为 8 的整数倍
     * @throws IllegalArgumentException 参数非法时抛出
     */
    public Pbkdf2PasswordHasher(int iterationCount, int keyLengthBits) {
        this(iterationCount, keyLengthBits, DEFAULT_ALGORITHM);
    }

    /**
     * 全参数构造。
     *
     * @param iterationCount 迭代次数（次），必须大于 0；OWASP 建议 PBKDF2-HMAC-SHA256 至少数十万次
     * @param keyLengthBits  输出哈希长度（位），必须大于 0 且为 8 的整数倍
     * @param algorithm      KDF 算法名，不能为 null 或空串；如 PBKDF2WithHmacSHA256
     * @throws IllegalArgumentException 任一参数非法时抛出
     */
    public Pbkdf2PasswordHasher(int iterationCount, int keyLengthBits, String algorithm) {
        if (iterationCount <= 0) {
            throw new IllegalArgumentException("iterationCount 必须大于 0，当前: " + iterationCount);
        }
        if (keyLengthBits <= 0 || keyLengthBits % 8 != 0) {
            throw new IllegalArgumentException("keyLengthBits 必须为正的 8 的整数倍，当前: " + keyLengthBits);
        }
        if (algorithm == null || algorithm.length() == 0) {
            throw new IllegalArgumentException("algorithm 不能为 null 或空串");
        }
        this.iterationCount = iterationCount;
        this.keyLengthBits = keyLengthBits;
        this.algorithm = algorithm;
    }

    /**
     * 哈希明文密码。
     *
     * <p>每次调用生成 16 字节随机盐，输出格式为
     * {@code pbkdf2-sha256$<iterations>$<Base64(salt)>$<Base64(hash)>}；
     * 同一密码多次哈希结果不同，属正常现象。</p>
     *
     * @param rawPassword 明文密码，不能为 null（允许空串密码，但强烈不建议业务上接受）
     * @return 可入库存储的哈希串，不会为 null
     * @throws IllegalArgumentException  rawPassword 为 null 时抛出
     * @throws IllegalStateException     底层 KDF 计算失败时抛出（JDK 内置算法下理论上不可达）
     */
    @Override
    public String hash(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("rawPassword 不能为 null");
        }
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] digest = pbkdf2(rawPassword, salt, iterationCount, keyLengthBits);
        return FORMAT_PREFIX + SEPARATOR + iterationCount
                + SEPARATOR + Base64.getEncoder().encodeToString(salt)
                + SEPARATOR + Base64.getEncoder().encodeToString(digest);
    }

    /**
     * 校验明文密码与存储哈希是否匹配。
     *
     * <p>解析存储串四段（前缀、迭代次数、盐、哈希），按串内参数重算 PBKDF2 后
     * 以 {@link HashUtils#constantTimeEquals(byte[], byte[])} 常量时间比较，
     * 因此校验结果与本实例构造参数无关，可正确校验历史参数生成的存量数据。</p>
     *
     * @param rawPassword 用户输入的明文密码，不能为 null
     * @param storedHash  {@link #hash(String)} 产生的存储串，不能为 null
     * @return true 表示密码匹配；false 表示不匹配
     * @throws IllegalArgumentException rawPassword 为 null，或 storedHash 为 null/段数不是 4/前缀不符/
     *                                  迭代数或 Base64 非法（即格式不符）时抛出，消息为
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
        int storedIterations;
        byte[] salt;
        byte[] expected;
        try {
            storedIterations = Integer.parseInt(parts[1]);
            salt = Base64.getDecoder().decode(parts[2]);
            expected = Base64.getDecoder().decode(parts[3]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(UNRECOGNIZED, e);
        } catch (IllegalArgumentException e) {
            // Base64 解码失败同样视为格式不符
            throw new IllegalArgumentException(UNRECOGNIZED, e);
        }
        byte[] actual = pbkdf2(rawPassword, salt, storedIterations, expected.length * 8);
        // 必须常量时间比较，防止逐字节比对泄露匹配前缀长度（时序攻击）
        return HashUtils.constantTimeEquals(actual, expected);
    }

    /**
     * 执行 PBKDF2 计算。
     *
     * @param password   明文密码
     * @param salt       盐
     * @param iterations 迭代次数（次）
     * @param keyBits    输出长度（位）
     * @return 派生哈希字节数组，不会为 null
     */
    private byte[] pbkdf2(String password, byte[] salt, int iterations, int keyBits) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyBits);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(algorithm);
            return factory.generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            // JDK 内置 PBKDF2WithHmacSHA256，该异常理论上不可达（自定义算法名错误时才可能发生）
            throw new IllegalStateException("PBKDF2 计算失败: " + algorithm, e);
        } finally {
            spec.clearPassword();
        }
    }
}
