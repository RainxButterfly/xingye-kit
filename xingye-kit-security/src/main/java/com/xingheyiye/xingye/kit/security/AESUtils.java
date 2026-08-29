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
 * @since 2026-08-28
 */
package com.xingheyiye.xingye.kit.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-GCM 加解密工具：支持随机密钥、口令派生密钥（PBKDF2）两种方式，密文以 Base64 输出。
 *
 * <p>一句话职责：以 GCM 模式（128 位认证标签）提供机密性与完整性一并保证的对称加密。</p>
 *
 * <p>适用场景：配置项/数据库敏感字段加密、接口报文字段加密、仅有口令没有预共享密钥的轻量加密。</p>
 *
 * <p>线程安全性：全静态方法且无共享可变状态（Cipher/SecureRandom 每次按需创建或线程安全），
 * 可在多线程环境并发调用。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 方式一：随机密钥
 * SecretKey key = AESUtils.generateKey(256);
 * String cipher = AESUtils.encryptToBase64(key, "机密内容");
 * String plain = AESUtils.decryptFromBase64(key, cipher);
 *
 * // 方式二：口令派生密钥（salt 与 IV 内嵌于密文）
 * String encrypted = AESUtils.encryptWithPassword("登录口令", "机密内容");
 * String decrypted = AESUtils.decryptWithPassword("登录口令", encrypted);
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-28
 */
public final class AESUtils {

    /** GCM 认证标签长度（位）：128 位是 NIST 建议的最短安全长度 */
    private static final int GCM_TAG_BITS = 128;

    /** GCM IV 长度（字节）：96 位为 GCM 标准推荐长度，既安全又不增加认证开销 */
    private static final int GCM_IV_LENGTH_BYTES = 12;

    /** 口令派生密钥使用的随机盐长度（字节） */
    private static final int SALT_LENGTH_BYTES = 16;

    /**
     * PBKDF2 默认迭代次数。
     * OWASP《Password Storage Cheat Sheet》建议 PBKDF2-HMAC-SHA256 不少于 60 万次迭代，
     * 本库在纯 JDK 环境下取 210000 次作为安全与耗时的折中，安全敏感场景请自行调高。
     */
    private static final int DEFAULT_PBKDF2_ITERATIONS = 210000;

    /** 口令派生 AES 密钥的默认长度（位） */
    private static final int DEFAULT_DERIVED_KEY_BITS = 128;

    /** 允许的最小 KDF 迭代次数（次），低于该值视为不安全配置 */
    private static final int MIN_PBKDF2_ITERATIONS = 1000;

    /** 允许的最短盐长度（字节） */
    private static final int MIN_SALT_LENGTH_BYTES = 8;

    /** AES 算法名 */
    private static final String AES = "AES";

    /** GCM 加解密 transformation：NoPadding 下密文长度 = 明文长度 + 16 字节认证标签 */
    private static final String GCM_TRANSFORMATION = "AES/GCM/NoPadding";

    /** 口令派生密钥的 KDF 算法 */
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";

    /** 共享安全随机源（SecureRandom 本身线程安全） */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AESUtils() {
    }

    /**
     * 生成随机 AES 密钥。
     *
     * @param bits 密钥长度（位），仅允许 128/192/256；JDK 默认策略下 192/256 需安装不限强度策略文件
     * @return 新生成的密钥，不会为 null
     * @throws IllegalArgumentException bits 不属于 {128, 192, 256} 时抛出
     * @throws GeneralSecurityException 底层密钥生成失败时抛出
     */
    public static SecretKey generateKey(int bits) throws GeneralSecurityException {
        if (bits != 128 && bits != 192 && bits != 256) {
            throw new IllegalArgumentException("AES 密钥长度仅支持 128/192/256 位，当前: " + bits);
        }
        KeyGenerator generator = KeyGenerator.getInstance(AES);
        generator.init(bits, SECURE_RANDOM);
        return generator.generateKey();
    }

    /**
     * 使用密钥加密字符串，输出 Base64。
     *
     * <p>IV 说明：IV（初始向量）参与 GCM 计数器模式的初始化；同一密钥下 IV 绝不可重复，
     * 否则攻击者可利用两次密文的异或关系恢复明文差异（IV 重用是 GCM 最致命的误用方式），
     * 因此本方法每次加密都以 SecureRandom 生成全新随机 IV 并前置于密文。</p>
     *
     * <p>密文布局（整体 Base64 编码）：{@code IV(12字节) | GCM 密文 + 认证标签(变长)}</p>
     *
     * @param key   AES 密钥，不能为 null
     * @param plain 明文字符串，不能为 null（可加密空串）；按 UTF-8 编码
     * @return Base64 密文，不会为 null
     * @throws IllegalArgumentException key 或 plain 为 null 时抛出
     * @throws GeneralSecurityException 底层加密失败时抛出
     */
    public static String encryptToBase64(SecretKey key, String plain) throws GeneralSecurityException {
        if (key == null || plain == null) {
            throw new IllegalArgumentException("key 与 plain 均不能为 null");
        }
        // IV 必须每次随机生成，理由见方法 Javadoc
        byte[] iv = randomBytes(GCM_IV_LENGTH_BYTES);
        Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);
        return Base64.getEncoder().encodeToString(out);
    }

    /**
     * 解密 {@link #encryptToBase64(SecretKey, String)} 产生的 Base64 密文。
     *
     * @param key           AES 密钥，不能为 null；必须与加密时一致
     * @param cipherBase64 Base64 密文，不能为 null
     * @return 明文字符串（UTF-8 解码），不会为 null
     * @throws IllegalArgumentException           key/cipherBase64 为 null、Base64 非法
     *                                            或密文长度不足 IV+标签最小长度时抛出
     * @throws javax.crypto.AEADBadTagException   密钥不匹配或密文被篡改时抛出（GCM 认证失败）
     * @throws GeneralSecurityException           其它底层解密失败时抛出
     */
    public static String decryptFromBase64(SecretKey key, String cipherBase64) throws GeneralSecurityException {
        if (key == null || cipherBase64 == null) {
            throw new IllegalArgumentException("key 与 cipherBase64 均不能为 null");
        }
        byte[] data = Base64.getDecoder().decode(cipherBase64);
        int minLength = GCM_IV_LENGTH_BYTES + GCM_TAG_BITS / 8;
        if (data.length < minLength) {
            throw new IllegalArgumentException("密文长度非法：至少应为 " + minLength + " 字节（IV+认证标签）");
        }
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        System.arraycopy(data, 0, iv, 0, GCM_IV_LENGTH_BYTES);
        Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plain = cipher.doFinal(data, GCM_IV_LENGTH_BYTES, data.length - GCM_IV_LENGTH_BYTES);
        return new String(plain, StandardCharsets.UTF_8);
    }

    /**
     * 生成随机盐。
     *
     * @param bytes 盐长度（字节），必须大于 0；常用 16 字节
     * @return 随机盐字节数组，不会为 null
     * @throws IllegalArgumentException bytes 小于等于 0 时抛出
     */
    public static byte[] randomSalt(int bytes) {
        return randomBytes(bytes);
    }

    /**
     * 以口令派生 AES 密钥（PBKDF2WithHmacSHA256），使用默认迭代次数与密钥长度。
     *
     * <p>默认值：迭代 210000 次、密钥 128 位；OWASP 对 PBKDF2-HMAC-SHA256 的推荐迭代数更高，
     * 安全敏感场景请显式调用四参重载并调高迭代次数。</p>
     *
     * @param password 口令，不能为 null 或空串
     * @param salt     盐（来自 {@link #randomSalt(int)}），不能为 null 且长度至少 8 字节
     * @return 派生出的 AES 密钥，不会为 null
     * @throws IllegalArgumentException 参数非法（口令为空、盐过短、迭代过少、密钥位数非法）时抛出
     * @throws GeneralSecurityException 底层 KDF 计算失败时抛出
     */
    public static SecretKey deriveKey(String password, byte[] salt) throws GeneralSecurityException {
        return deriveKey(password, salt, DEFAULT_PBKDF2_ITERATIONS, DEFAULT_DERIVED_KEY_BITS);
    }

    /**
     * 以口令派生 AES 密钥（PBKDF2WithHmacSHA256）。
     *
     * @param password       口令，不能为 null 或空串
     * @param salt           盐，不能为 null 且长度至少 8 字节；同一口令配不同盐可得不同密钥
     * @param iterationCount 迭代次数（次），必须不小于 1000；越大越抗暴力破解但越慢
     * @param keyBits        派生密钥长度（位），仅允许 128/192/256
     * @return 派生出的 AES 密钥，不会为 null
     * @throws IllegalArgumentException 参数非法（口令为空、盐过短、迭代过少、密钥位数非法）时抛出
     * @throws GeneralSecurityException 底层 KDF 计算失败时抛出
     */
    public static SecretKey deriveKey(String password, byte[] salt, int iterationCount, int keyBits)
            throws GeneralSecurityException {
        if (password == null || password.length() == 0) {
            throw new IllegalArgumentException("password 不能为 null 或空串");
        }
        if (salt == null || salt.length < MIN_SALT_LENGTH_BYTES) {
            throw new IllegalArgumentException("salt 不能为 null 且长度至少 " + MIN_SALT_LENGTH_BYTES + " 字节");
        }
        if (iterationCount < MIN_PBKDF2_ITERATIONS) {
            throw new IllegalArgumentException("iterationCount 不得小于 " + MIN_PBKDF2_ITERATIONS + " 次");
        }
        if (keyBits != 128 && keyBits != 192 && keyBits != 256) {
            throw new IllegalArgumentException("keyBits 仅支持 128/192/256 位，当前: " + keyBits);
        }
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterationCount, keyBits);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            SecretKey derived = factory.generateSecret(spec);
            // PBKDF2 产出的是通用密钥字节，包装为 AES SecretKeySpec 以便直接用于 Cipher
            return new SecretKeySpec(derived.getEncoded(), AES);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * 仅凭口令加密字符串：随机生成盐与 IV，派生 AES 密钥后按 GCM 加密，整体 Base64 输出。
     *
     * <p>密文布局（整体 Base64 编码）：</p>
     * <pre>
     * +-------------+------------+---------------------------+
     * | salt(16字节) | IV(12字节) | GCM 密文 + 认证标签(变长)  |
     * +-------------+------------+---------------------------+
     * 偏移:0        16           28                          N
     * </pre>
     *
     * @param password 口令，不能为 null 或空串
     * @param plain    明文字符串，不能为 null；按 UTF-8 编码
     * @return Base64 密文，不会为 null；可用 {@link #decryptWithPassword(String, String)} 解开
     * @throws IllegalArgumentException password/plain 非法时抛出
     * @throws GeneralSecurityException 底层加密失败时抛出
     */
    public static String encryptWithPassword(String password, String plain) throws GeneralSecurityException {
        if (plain == null) {
            throw new IllegalArgumentException("plain 不能为 null");
        }
        byte[] salt = randomBytes(SALT_LENGTH_BYTES);
        SecretKey key = deriveKey(password, salt, DEFAULT_PBKDF2_ITERATIONS, DEFAULT_DERIVED_KEY_BITS);
        byte[] iv = randomBytes(GCM_IV_LENGTH_BYTES);
        Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] out = new byte[salt.length + iv.length + cipherText.length];
        System.arraycopy(salt, 0, out, 0, salt.length);
        System.arraycopy(iv, 0, out, salt.length, iv.length);
        System.arraycopy(cipherText, 0, out, salt.length + iv.length, cipherText.length);
        return Base64.getEncoder().encodeToString(out);
    }

    /**
     * 解密 {@link #encryptWithPassword(String, String)} 产生的 Base64 密文。
     *
     * @param password      口令，不能为 null 或空串；必须与加密时一致
     * @param cipherBase64  Base64 密文，不能为 null
     * @return 明文字符串（UTF-8 解码），不会为 null
     * @throws IllegalArgumentException          password 为空、cipherBase64 为 null、Base64 非法
     *                                           或长度小于 44 字节（salt16+iv12+tag16）时抛出
     * @throws javax.crypto.AEADBadTagException  口令不匹配或密文被篡改时抛出（GCM 认证失败，
     *                                           这是密钥/口令错误的典型表现）
     * @throws GeneralSecurityException          其它底层解密失败时抛出
     */
    public static String decryptWithPassword(String password, String cipherBase64) throws GeneralSecurityException {
        if (cipherBase64 == null) {
            throw new IllegalArgumentException("cipherBase64 不能为 null");
        }
        byte[] data = Base64.getDecoder().decode(cipherBase64);
        int minLength = SALT_LENGTH_BYTES + GCM_IV_LENGTH_BYTES + GCM_TAG_BITS / 8;
        if (data.length < minLength) {
            throw new IllegalArgumentException("密文格式非法：解包后至少应为 " + minLength + " 字节（salt+IV+标签）");
        }
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        System.arraycopy(data, 0, salt, 0, SALT_LENGTH_BYTES);
        System.arraycopy(data, SALT_LENGTH_BYTES, iv, 0, GCM_IV_LENGTH_BYTES);
        SecretKey key = deriveKey(password, salt, DEFAULT_PBKDF2_ITERATIONS, DEFAULT_DERIVED_KEY_BITS);
        Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        int cipherOffset = SALT_LENGTH_BYTES + GCM_IV_LENGTH_BYTES;
        byte[] plain = cipher.doFinal(data, cipherOffset, data.length - cipherOffset);
        return new String(plain, StandardCharsets.UTF_8);
    }

    /**
     * 生成指定长度的随机字节数组。
     *
     * @param bytes 长度（字节），必须大于 0
     * @return 随机字节数组，不会为 null
     * @throws IllegalArgumentException bytes 小于等于 0 时抛出
     */
    private static byte[] randomBytes(int bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("随机字节长度必须大于 0，当前: " + bytes);
        }
        byte[] buffer = new byte[bytes];
        SECURE_RANDOM.nextBytes(buffer);
        return buffer;
    }
}
