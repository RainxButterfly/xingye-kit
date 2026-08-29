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

import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;

/**
 * RSA 加解密与签名工具：OAEP(SHA-256) 加密、SHA256withRSA 签名，密钥支持 Base64 序列化。
 *
 * <p>一句话职责：封装 RSA 公钥加密/私钥解密与私钥签名/公钥验签四类操作。</p>
 *
 * <p>适用场景：短小敏感数据（如 AES 会话密钥）的加密传递、报文签名防篡改。
 * RSA 单块明文容量受限，大数据请使用"RSA 加密随机 AES 密钥 + AES 加密数据"的混合加密。</p>
 *
 * <p>线程安全性：全静态方法且无共享可变状态（Cipher/Signature 每次调用新建），
 * 可在多线程环境并发调用。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * KeyPair pair = RSAUtils.generateKeyPair();            // 默认 2048 位
 * String pubB64 = RSAUtils.toBase64(pair.getPublic());
 * PublicKey pub = RSAUtils.parsePublicKey(pubB64);
 *
 * byte[] cipher = RSAUtils.encrypt(pub, sessionKey);    // 加密短数据
 * byte[] plain = RSAUtils.decrypt(pair.getPrivate(), cipher);
 *
 * String sign = RSAUtils.signBase64(pair.getPrivate(), data);
 * boolean ok = RSAUtils.verify(pub, data, sign);
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-28
 */
public final class RSAUtils {

    /** RSA 算法名 */
    private static final String RSA = "RSA";

    /** OAEP 填充的 RSA 加解密 transformation（摘要与 MGF1 均为 SHA-256） */
    private static final String OAEP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    /** 签名算法（SHA-256 摘要 + RSASSA-PKCS1-v1_5） */
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    /** 允许的最小密钥长度（位）：1024 位 RSA 已被证明不安全 */
    private static final int MIN_KEY_SIZE_BITS = 2048;

    /**
     * OAEP(SHA-256) 填充开销（字节）= 2 × 32（SHA-256 摘要长度）+ 2，
     * 即单块最大明文长度 = 模长字节 - 66
     */
    private static final int OAEP_OVERHEAD_BYTES = 66;

    /** 共享安全随机源（线程安全） */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private RSAUtils() {
    }

    /**
     * 生成 RSA 密钥对（默认 2048 位）。
     *
     * @return 新生成的密钥对，不会为 null
     * @throws GeneralSecurityException 底层密钥生成失败时抛出
     */
    public static KeyPair generateKeyPair() throws GeneralSecurityException {
        return generateKeyPair(MIN_KEY_SIZE_BITS);
    }

    /**
     * 生成指定长度的 RSA 密钥对。
     *
     * @param keySize 密钥长度（位），必须不小于 2048（1024 位已不安全）；常用 2048/3072/4096
     * @return 新生成的密钥对，不会为 null
     * @throws IllegalArgumentException keySize 小于 2048 时抛出
     * @throws GeneralSecurityException 底层密钥生成失败时抛出
     */
    public static KeyPair generateKeyPair(int keySize) throws GeneralSecurityException {
        if (keySize < MIN_KEY_SIZE_BITS) {
            throw new IllegalArgumentException("密钥长度 " + keySize + " 位不安全：RSA 密钥必须不小于 "
                    + MIN_KEY_SIZE_BITS + " 位");
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA);
        generator.initialize(keySize, SECURE_RANDOM);
        return generator.generateKeyPair();
    }

    /**
     * 公钥序列化为 Base64（X.509 编码）。
     *
     * @param key 公钥，不能为 null
     * @return Base64 字符串，不会为 null；可用 {@link #parsePublicKey(String)} 还原
     * @throws IllegalArgumentException key 为 null 时抛出
     */
    public static String toBase64(PublicKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key 不能为 null");
        }
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * 私钥序列化为 Base64（PKCS#8 编码）。
     *
     * @param key 私钥，不能为 null
     * @return Base64 字符串，不会为 null；可用 {@link #parsePrivateKey(String)} 还原
     * @throws IllegalArgumentException key 为 null 时抛出
     */
    public static String toBase64(PrivateKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key 不能为 null");
        }
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * 从 Base64（X.509 编码）还原公钥。
     *
     * @param base64 公钥 Base64 字符串，不能为 null
     * @return 还原的公钥，不会为 null
     * @throws IllegalArgumentException base64 为 null、Base64 非法或内容不是合法 RSA 公钥时抛出
     */
    public static PublicKey parsePublicKey(String base64) {
        if (base64 == null) {
            throw new IllegalArgumentException("base64 不能为 null");
        }
        try {
            byte[] encoded = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance(RSA).generatePublic(new X509EncodedKeySpec(encoded));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("非法的 RSA 公钥内容", e);
        }
    }

    /**
     * 从 Base64（PKCS#8 编码）还原私钥。
     *
     * @param base64 私钥 Base64 字符串，不能为 null
     * @return 还原的私钥，不会为 null
     * @throws IllegalArgumentException base64 为 null、Base64 非法或内容不是合法 RSA 私钥时抛出
     */
    public static PrivateKey parsePrivateKey(String base64) {
        if (base64 == null) {
            throw new IllegalArgumentException("base64 不能为 null");
        }
        try {
            byte[] encoded = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance(RSA).generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("非法的 RSA 私钥内容", e);
        }
    }

    /**
     * 公钥加密（OAEP with SHA-256）。
     *
     * <p>明文长度限制：单块最多 模长字节 - 66 字节（66 = 2×32 SHA-256 摘要 + 2 填充开销），
     * 例如 2048 位密钥最多加密 190 字节。超出请改用"RSA 加密随机 AES 密钥"的混合加密方案。</p>
     *
     * @param publicKey RSA 公钥，不能为 null
     * @param plain     明文，不能为 null（可为空数组）
     * @return 密文字节数组，长度等于模长字节，不会为 null
     * @throws IllegalArgumentException publicKey/plain 为 null、密钥不是 RSA 密钥
     *                                  或明文超过单块上限时抛出（超限时会建议混合加密）
     * @throws GeneralSecurityException 底层加密失败时抛出
     */
    public static byte[] encrypt(PublicKey publicKey, byte[] plain) throws GeneralSecurityException {
        if (publicKey == null || plain == null) {
            throw new IllegalArgumentException("publicKey 与 plain 均不能为 null");
        }
        int blockSize = modulusBlockSize(publicKey);
        int maxPlainLength = blockSize - OAEP_OVERHEAD_BYTES;
        if (plain.length > maxPlainLength) {
            throw new IllegalArgumentException("明文 " + plain.length + " 字节超过 RSA-OAEP 单块上限 "
                    + maxPlainLength + " 字节，请改用 RSA 加密随机 AES 密钥的混合加密方案");
        }
        Cipher cipher = Cipher.getInstance(OAEP_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(plain);
    }

    /**
     * 私钥解密（OAEP with SHA-256），支持对端按块加密后拼接的多块密文。
     *
     * <p>密文总长度必须为模长字节的整数倍，本方法按块逐段解密后拼接明文。</p>
     *
     * @param privateKey RSA 私钥，不能为 null
     * @param cipherText 密文，不能为 null 且不能为空数组
     * @return 解密后的明文字节数组，不会为 null
     * @throws IllegalArgumentException privateKey/cipherText 为 null、密钥不是 RSA 密钥
     *                                  或密文长度不是模长字节整数倍时抛出
     * @throws javax.crypto.BadPaddingException 密钥不匹配或密文损坏时抛出
     * @throws GeneralSecurityException 其它底层解密失败时抛出
     */
    public static byte[] decrypt(PrivateKey privateKey, byte[] cipherText) throws GeneralSecurityException {
        if (privateKey == null || cipherText == null) {
            throw new IllegalArgumentException("privateKey 与 cipherText 均不能为 null");
        }
        int blockSize = modulusBlockSize(privateKey);
        if (cipherText.length == 0 || cipherText.length % blockSize != 0) {
            throw new IllegalArgumentException("密文长度 " + cipherText.length
                    + " 必须是密钥块大小 " + blockSize + " 字节的整数倍");
        }
        Cipher cipher = Cipher.getInstance(OAEP_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        // 分块解密：兼容对端将长明文切块加密后拼接的密文
        for (int offset = 0; offset < cipherText.length; offset += blockSize) {
            byte[] block = new byte[blockSize];
            System.arraycopy(cipherText, offset, block, 0, blockSize);
            byte[] plainBlock = cipher.doFinal(block);
            buffer.write(plainBlock, 0, plainBlock.length);
        }
        return buffer.toByteArray();
    }

    /**
     * 私钥签名（SHA256withRSA），输出 Base64。
     *
     * @param privateKey RSA 私钥，不能为 null
     * @param data       待签名的原始数据，不能为 null
     * @return 签名的 Base64 字符串，不会为 null
     * @throws IllegalArgumentException privateKey/data 为 null 时抛出
     * @throws GeneralSecurityException 底层签名失败时抛出
     */
    public static String signBase64(PrivateKey privateKey, byte[] data) throws GeneralSecurityException {
        if (privateKey == null || data == null) {
            throw new IllegalArgumentException("privateKey 与 data 均不能为 null");
        }
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(privateKey);
        signature.update(data);
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    /**
     * 公钥验签（SHA256withRSA）。
     *
     * <p>异常说明：底层 {@link Signature} 抛出的 {@link SignatureException} 会原样透传（不吞不包装）；
     * 而签名值与数据不匹配（验签失败）仅返回 false，不抛任何异常。</p>
     *
     * @param publicKey  RSA 公钥，不能为 null；必须与签名私钥配对
     * @param data       参与验签的原始数据，不能为 null
     * @param signBase64 签名的 Base64 字符串，不能为 null
     * @return true 表示签名有效；false 表示签名不匹配（数据或签名被篡改、密钥不配对）
     * @throws IllegalArgumentException     publicKey/data/signBase64 为 null 或 signBase64 不是合法 Base64 时抛出
     * @throws SignatureException           底层签名引擎异常时透传抛出
     * @throws GeneralSecurityException     公钥非法等其它底层异常时抛出
     */
    public static boolean verify(PublicKey publicKey, byte[] data, String signBase64)
            throws GeneralSecurityException {
        if (publicKey == null || data == null || signBase64 == null) {
            throw new IllegalArgumentException("publicKey、data 与 signBase64 均不能为 null");
        }
        byte[] signBytes = Base64.getDecoder().decode(signBase64);
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initVerify(publicKey);
        signature.update(data);
        // 验签失败（内容或签名不匹配）时 verify 返回 false，本方法同样返回 false 而不抛异常
        return signature.verify(signBytes);
    }

    /**
     * 计算 RSA 密钥的模长块大小（字节）= 模长位数 / 8 向上取整。
     *
     * @param key RSA 公钥或私钥
     * @return 块大小（字节）
     * @throws IllegalArgumentException key 为 null 或不是 RSA 密钥时抛出
     */
    private static int modulusBlockSize(java.security.Key key) {
        if (!(key instanceof RSAKey)) {
            throw new IllegalArgumentException("仅支持 RSA 密钥，实际类型: "
                    + (key == null ? "null" : key.getClass().getName()));
        }
        return (((RSAKey) key).getModulus().bitLength() + 7) / 8;
    }
}
