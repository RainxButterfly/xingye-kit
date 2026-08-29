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
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 零依赖 JWT（JWS）签发与校验：支持 HS256/HS384/HS512（HMAC）与 RS256（RSA）。
 *
 * <p>一句话职责：以纯 JDK 能力实现 JWT compact serialization 的生成、签名与验签。</p>
 *
 * <p>适用场景：单体/微服务的无状态登录凭证、服务间调用身份传递。
 * 令牌结构为标准三段 base64url（无填充）：{@code base64url(header).base64url(payload).base64url(signature)}，
 * header 固定为 {@code {"alg":...,"typ":"JWT"}}，payload 经 {@link JwtJson} 序列化。</p>
 *
 * <p>线程安全性：全静态方法且无共享可变状态（Mac/Signature 每次调用新建），线程安全。</p>
 *
 * <p>生成与校验完整示例：</p>
 * <pre>{@code
 * // 生成（HS256）
 * String token = JwtWrapper.create()
 *         .issuer("order-service")
 *         .subject("user-1001")
 *         .issuedAtMillis(System.currentTimeMillis())
 *         .expiresInSeconds(1800)
 *         .claim("role", "admin")
 *         .signHmacSha256("0123456789abcdef");
 *
 * // 校验（按 header 中的 alg 自动分发 HS256/HS384/HS512）
 * JwtClaims claims = JwtWrapper.verify(token, "0123456789abcdef");
 * String subject = claims.getStringClaim("sub");
 *
 * // 生成（RS256）
 * KeyPair pair = RSAUtils.generateKeyPair();
 * String rsaToken = JwtWrapper.create().subject("user-1001")
 *         .signRsa(pair.getPrivate());
 * JwtWrapper.verify(rsaToken, pair.getPublic());
 * }</pre>
 *
 * <p>安全注意：</p>
 * <ul>
 *     <li>HMAC secret 请使用至少 16 字节（128 位）的高熵随机值，存放于配置中心/KMS，
 *         严禁硬编码在代码或写入日志；</li>
 *     <li>本实现拒绝 alg=none 与算法不匹配的令牌，防止"算法混淆"攻击；
 *         校验时务必使用与签发方约定的密钥类型（对称密钥验 HS*、公钥验 RS256）；</li>
 *     <li>签名比对使用常量时间算法，避免时序侧信道泄露。</li>
 * </ul>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-29
 */
public final class JwtWrapper {

    /** header 中的算法声明名 */
    private static final String HEADER_ALG = "alg";

    /** header 中的令牌类型声明名 */
    private static final String HEADER_TYP = "typ";

    /** 固定令牌类型值 */
    private static final String TOKEN_TYPE_JWT = "JWT";

    /** 标准 iss（签发者）声明名 */
    private static final String CLAIM_ISSUER = "iss";

    /** 标准 sub（主体）声明名 */
    private static final String CLAIM_SUBJECT = "sub";

    /** 标准 aud（受众）声明名 */
    private static final String CLAIM_AUDIENCE = "aud";

    /** 标准 iat（签发时间，秒级）声明名 */
    private static final String CLAIM_ISSUED_AT = "iat";

    /** 标准 nbf（生效时间，秒级）声明名 */
    private static final String CLAIM_NOT_BEFORE = "nbf";

    /** 标准 exp（过期时间，秒级）声明名 */
    private static final String CLAIM_EXPIRATION = "exp";

    /** 秒与毫秒的换算系数 */
    private static final long MILLIS_PER_SECOND = 1000L;

    /** HMAC secret 最短长度（字节）：128 位，防止弱口令直接充当签名密钥 */
    private static final int MIN_HMAC_SECRET_BYTES = 16;

    /** 算法标识：HMAC-SHA-256 */
    private static final String ALG_HS256 = "HS256";

    /** 算法标识：HMAC-SHA-384 */
    private static final String ALG_HS384 = "HS384";

    /** 算法标识：HMAC-SHA-512 */
    private static final String ALG_HS512 = "HS512";

    /** 算法标识：RSASSA-PKCS1-v1_5 with SHA-256 */
    private static final String ALG_RS256 = "RS256";

    /** 无签名算法标识，必须拒绝 */
    private static final String ALG_NONE = "none";

    /** JCA 的 HMAC-SHA-256 算法名 */
    private static final String MAC_SHA256 = "HmacSHA256";

    /** JCA 的 HMAC-SHA-384 算法名 */
    private static final String MAC_SHA384 = "HmacSHA384";

    /** JCA 的 HMAC-SHA-512 算法名 */
    private static final String MAC_SHA512 = "HmacSHA512";

    /** RSA 签名算法（SHA-256 摘要 + RSASSA-PKCS1-v1_5），对应 RS256 */
    private static final String SIGN_ALGORITHM = "SHA256withRSA";

    /** compact serialization 三段分隔符 */
    private static final char SEGMENT_SEPARATOR = '.';

    /** 令牌段数：header、payload、签名 */
    private static final int SEGMENT_COUNT = 3;

    private JwtWrapper() {
    }

    /**
     * 创建 JWT 构建器。
     *
     * @return 新的 Builder 实例，不会为 null
     */
    public static Builder create() {
        return new Builder();
    }

    /**
     * 校验 HMAC 签名的令牌：按 header 中的 alg 自动分发 HS256/HS384/HS512，
     * 通过后校验 exp/nbf 时间窗口。
     *
     * @param token  compact serialization 令牌字符串，不能为 null 或空串
     * @param secret HMAC 密钥，不能为 null；必须与签发方一致（签发时同样要求不少于 16 字节）
     * @return 校验通过的声明集，不会为 null
     * @throws IllegalArgumentException secret 为 null 时抛出
     * @throws JwtException             令牌格式非法（段数/base64url/header JSON 不合法）、
     *                                  alg 不支持或为 none、签名不匹配（"signature mismatch"）、
     *                                  已过期（"token expired"）、未到生效时间（"token not yet valid"）
     *                                  或 payload JSON 非法时抛出
     */
    public static JwtClaims verify(String token, String secret) {
        if (secret == null) {
            throw new IllegalArgumentException("secret 不能为 null");
        }
        String[] segments = splitToken(token);
        String alg = readHeaderAlg(segments[0]);
        String macAlgorithm;
        if (ALG_HS256.equals(alg)) {
            macAlgorithm = MAC_SHA256;
        } else if (ALG_HS384.equals(alg)) {
            macAlgorithm = MAC_SHA384;
        } else if (ALG_HS512.equals(alg)) {
            macAlgorithm = MAC_SHA512;
        } else {
            throw new JwtException("unsupported alg: " + alg + "，HMAC 校验仅支持 HS256/HS384/HS512");
        }
        byte[] expected = computeHmac(macAlgorithm, secret.getBytes(StandardCharsets.UTF_8),
                segments[0] + SEGMENT_SEPARATOR + segments[1]);
        byte[] actual = decodeBase64Url(segments[2]);
        // HMAC 比较使用常量时间算法，防止按字节比较的时序侧信道泄露签名前缀
        if (!HashUtils.constantTimeEquals(expected, actual)) {
            throw new JwtException("signature mismatch");
        }
        return parseAndValidateClaims(segments[1]);
    }

    /**
     * 校验 RS256 签名的令牌：以 RSA 公钥验签，通过后校验 exp/nbf 时间窗口。
     *
     * @param token     compact serialization 令牌字符串，不能为 null 或空串
     * @param publicKey RSA 公钥，不能为 null；必须与签发私钥配对
     * @return 校验通过的声明集，不会为 null
     * @throws IllegalArgumentException publicKey 为 null 时抛出
     * @throws JwtException             令牌格式非法、alg 不是 RS256（含 none）、签名不匹配
     *                                  （"signature mismatch"）、已过期（"token expired"）、
     *                                  未到生效时间（"token not yet valid"）或 payload JSON 非法时抛出
     */
    public static JwtClaims verify(String token, PublicKey publicKey) {
        if (publicKey == null) {
            throw new IllegalArgumentException("publicKey 不能为 null");
        }
        String[] segments = splitToken(token);
        String alg = readHeaderAlg(segments[0]);
        if (!ALG_RS256.equals(alg)) {
            throw new JwtException("unsupported alg: " + alg + "，公钥校验仅支持 RS256");
        }
        byte[] signature = decodeBase64Url(segments[2]);
        boolean valid;
        try {
            Signature verifier = Signature.getInstance(SIGN_ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update((segments[0] + SEGMENT_SEPARATOR + segments[1]).getBytes(StandardCharsets.UTF_8));
            valid = verifier.verify(signature);
        } catch (GeneralSecurityException e) {
            throw new JwtException("signature verify error: " + e.getMessage(), e);
        }
        if (!valid) {
            throw new JwtException("signature mismatch");
        }
        return parseAndValidateClaims(segments[1]);
    }

    /**
     * 以 HMAC 完成签名并拼装三段令牌。
     *
     * @param claims       声明集
     * @param secret       HMAC 密钥
     * @param alg          header 中的算法标识（HS256/HS384/HS512）
     * @param macAlgorithm JCA 的 MAC 算法名
     * @return compact serialization 令牌
     */
    private static String signHmac(Map<String, Object> claims, String secret, String alg, String macAlgorithm) {
        byte[] secretBytes = secret == null ? null : secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes == null || secretBytes.length < MIN_HMAC_SECRET_BYTES) {
            throw new IllegalArgumentException("HMAC secret 不能为空且长度不得小于 "
                    + MIN_HMAC_SECRET_BYTES + " 字节（128 位）");
        }
        String signingInput = base64Url(JwtJson.write(header(alg))) + SEGMENT_SEPARATOR
                + base64Url(JwtJson.write(claims));
        byte[] signature = computeHmac(macAlgorithm, secretBytes, signingInput);
        return signingInput + SEGMENT_SEPARATOR + base64Url(signature);
    }

    /**
     * 以 RS256（SHA256withRSA）完成签名并拼装三段令牌。
     *
     * @param claims     声明集
     * @param privateKey RSA 私钥
     * @return compact serialization 令牌
     */
    private static String signRsa(Map<String, Object> claims, PrivateKey privateKey) {
        if (privateKey == null) {
            throw new IllegalArgumentException("privateKey 不能为 null");
        }
        String signingInput = base64Url(JwtJson.write(header(ALG_RS256))) + SEGMENT_SEPARATOR
                + base64Url(JwtJson.write(claims));
        try {
            Signature signer = Signature.getInstance(SIGN_ALGORITHM);
            signer.initSign(privateKey);
            signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signingInput + SEGMENT_SEPARATOR + base64Url(signer.sign());
        } catch (GeneralSecurityException e) {
            throw new JwtException("RSA 签名失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构造固定结构 {@code {"alg":...,"typ":"JWT"}} 的 header。
     *
     * @param alg 算法标识
     * @return header 声明 Map
     */
    private static Map<String, Object> header(String alg) {
        Map<String, Object> header = new LinkedHashMap<String, Object>();
        header.put(HEADER_ALG, alg);
        header.put(HEADER_TYP, TOKEN_TYPE_JWT);
        return header;
    }

    /**
     * 计算 HMAC。
     *
     * @param macAlgorithm JCA 的 MAC 算法名
     * @param secretBytes  密钥字节
     * @param data         待认证数据
     * @return MAC 字节
     */
    private static byte[] computeHmac(String macAlgorithm, byte[] secretBytes, String data) {
        try {
            Mac mac = Mac.getInstance(macAlgorithm);
            mac.init(new SecretKeySpec(secretBytes, macAlgorithm));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            // JDK 必带 HmacSHA256/384/512，该异常理论上不可达
            throw new JwtException("HMAC 计算失败: " + e.getMessage(), e);
        }
    }

    /**
     * 拆分并校验三段令牌结构。
     *
     * @param token 令牌字符串
     * @return 三段数组（header/payload/signature 的 base64url 文本）
     */
    private static String[] splitToken(String token) {
        if (token == null || token.length() == 0) {
            throw new JwtException("invalid token format: token 为空");
        }
        int first = token.indexOf(SEGMENT_SEPARATOR);
        int second = token.indexOf(SEGMENT_SEPARATOR, first + 1);
        if (first <= 0 || second <= first || token.indexOf(SEGMENT_SEPARATOR, second + 1) >= 0) {
            throw new JwtException("invalid token format: 应为 header.payload.signature 三段");
        }
        return new String[] {token.substring(0, first), token.substring(first + 1, second),
                token.substring(second + 1)};
    }

    /**
     * 解码并解析 header，读取 alg 声明；拒绝 alg=none。
     *
     * @param headerSegment header 的 base64url 段
     * @return 算法标识
     */
    private static String readHeaderAlg(String headerSegment) {
        byte[] headerBytes = decodeBase64Url(headerSegment);
        Map<String, Object> header;
        try {
            header = JwtJson.readObject(new String(headerBytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new JwtException("invalid token header: " + e.getMessage(), e);
        }
        Object alg = header.get(HEADER_ALG);
        if (!(alg instanceof String)) {
            throw new JwtException("invalid token header: alg 缺失或类型错误");
        }
        String algValue = (String) alg;
        if (ALG_NONE.equals(algValue)) {
            // 安全基线：无签名令牌一律拒绝，杜绝 alg=none 攻击
            throw new JwtException("alg=none 令牌被拒绝");
        }
        return algValue;
    }

    /**
     * 解析 payload 并校验 exp/nbf 时间窗口。
     *
     * @param payloadSegment payload 的 base64url 段
     * @return 声明集
     */
    private static JwtClaims parseAndValidateClaims(String payloadSegment) {
        byte[] payloadBytes = decodeBase64Url(payloadSegment);
        Map<String, Object> claimsMap;
        try {
            claimsMap = JwtJson.readObject(new String(payloadBytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new JwtException("invalid token payload: " + e.getMessage(), e);
        }
        JwtClaims claims = new JwtClaims(claimsMap);
        long now = System.currentTimeMillis();
        if (claims.isExpired(now)) {
            throw new JwtException("token expired");
        }
        Long notBefore = claims.getLongClaim(CLAIM_NOT_BEFORE);
        if (notBefore != null && now < notBefore.longValue() * MILLIS_PER_SECOND) {
            throw new JwtException("token not yet valid");
        }
        return claims;
    }

    /**
     * 无填充 base64url 编码。
     *
     * @param data 原始字节
     * @return base64url 文本（不含 '=' 填充）
     */
    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /**
     * 无填充 base64url 编码（字符串先按 UTF-8 取字节）。
     *
     * @param text 原始文本
     * @return base64url 文本（不含 '=' 填充）
     */
    private static String base64Url(String text) {
        return base64Url(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * base64url 解码，非法输入统一转 JwtException。
     *
     * @param text base64url 文本
     * @return 原始字节
     */
    private static byte[] decodeBase64Url(String text) {
        try {
            return Base64.getUrlDecoder().decode(text);
        } catch (IllegalArgumentException e) {
            throw new JwtException("invalid token format: 非法 base64url 段", e);
        }
    }

    /**
     * JWT 构建器：链式声明 payload 后选择算法完成签名。
     */
    public static final class Builder {

        /** 待签名声明集，LinkedHashMap 保持声明序列化顺序稳定 */
        private final Map<String, Object> claims = new LinkedHashMap<String, Object>();

        private Builder() {
        }

        /**
         * 添加自定义声明（同名覆盖旧值）。
         *
         * @param name  声明名，不能为 null 或空串
         * @param value 声明值，可为 null（序列化为 JSON null）；支持 String/Number/Boolean/List/嵌套 Map
         * @return 当前 Builder，便于链式调用，不会为 null
         * @throws IllegalArgumentException name 为 null 或空串时抛出
         */
        public Builder claim(String name, Object value) {
            if (name == null || name.length() == 0) {
                throw new IllegalArgumentException("claim 名称不能为 null 或空串");
            }
            claims.put(name, value);
            return this;
        }

        /**
         * 批量添加声明（同名覆盖旧值）。
         *
         * @param claims 声明集，不能为 null；值为 null 的键将写入 JSON null
         * @return 当前 Builder，便于链式调用，不会为 null
         * @throws IllegalArgumentException claims 为 null 时抛出
         */
        public Builder claims(Map<String, Object> claims) {
            if (claims == null) {
                throw new IllegalArgumentException("claims 不能为 null");
            }
            this.claims.putAll(claims);
            return this;
        }

        /**
         * 设置签发者（iss）。
         *
         * @param issuer 签发者标识，可为 null（表示不设置该声明）
         * @return 当前 Builder，不会为 null
         */
        public Builder issuer(String issuer) {
            return claim(CLAIM_ISSUER, issuer);
        }

        /**
         * 设置主体（sub）。
         *
         * @param subject 主体标识（如用户 ID），可为 null（表示不设置该声明）
         * @return 当前 Builder，不会为 null
         */
        public Builder subject(String subject) {
            return claim(CLAIM_SUBJECT, subject);
        }

        /**
         * 设置受众（aud）。
         *
         * @param audience 受众标识，可为 null（表示不设置该声明）
         * @return 当前 Builder，不会为 null
         */
        public Builder audience(String audience) {
            return claim(CLAIM_AUDIENCE, audience);
        }

        /**
         * 设置签发时间（iat）。
         *
         * @param millis 签发时刻（毫秒级 Unix 时间戳）；内部按 JWT 标准换算为秒级存储
         * @return 当前 Builder，不会为 null
         */
        public Builder issuedAtMillis(long millis) {
            return claim(CLAIM_ISSUED_AT, Long.valueOf(millis / MILLIS_PER_SECOND));
        }

        /**
         * 设置生效时间（nbf），早于该时间的令牌校验将失败。
         *
         * @param millis 生效时刻（毫秒级 Unix 时间戳）；内部按 JWT 标准换算为秒级存储
         * @return 当前 Builder，不会为 null
         */
        public Builder notBeforeMillis(long millis) {
            return claim(CLAIM_NOT_BEFORE, Long.valueOf(millis / MILLIS_PER_SECOND));
        }

        /**
         * 设置绝对过期时刻（exp）。
         *
         * @param millis 过期时刻（毫秒级 Unix 时间戳）；内部按 JWT 标准换算为秒级存储
         * @return 当前 Builder，不会为 null
         */
        public Builder expirationMillis(long millis) {
            return claim(CLAIM_EXPIRATION, Long.valueOf(millis / MILLIS_PER_SECOND));
        }

        /**
         * 以"当前时刻 + 有效秒数"设置相对过期时刻（exp）。
         *
         * @param seconds 有效时长（秒），必须大于 0
         * @return 当前 Builder，不会为 null
         * @throws IllegalArgumentException seconds 小于等于 0 时抛出
         */
        public Builder expiresInSeconds(long seconds) {
            if (seconds <= 0) {
                throw new IllegalArgumentException("seconds 必须大于 0，当前: " + seconds);
            }
            long nowSeconds = System.currentTimeMillis() / MILLIS_PER_SECOND;
            return claim(CLAIM_EXPIRATION, Long.valueOf(nowSeconds + seconds));
        }

        /**
         * 以 HS256（HMAC-SHA-256）签名生成令牌。
         *
         * @param secret HMAC 密钥，不能为空且 UTF-8 编码长度不得小于 16 字节（128 位）
         * @return compact serialization 令牌，不会为 null
         * @throws IllegalArgumentException secret 为 null 或长度不足 16 字节时抛出
         */
        public String signHmacSha256(String secret) {
            return signHmac(claims, secret, ALG_HS256, MAC_SHA256);
        }

        /**
         * 以 HS384（HMAC-SHA-384）签名生成令牌。
         *
         * @param secret HMAC 密钥，不能为空且 UTF-8 编码长度不得小于 16 字节（128 位）
         * @return compact serialization 令牌，不会为 null
         * @throws IllegalArgumentException secret 为 null 或长度不足 16 字节时抛出
         */
        public String signHmacSha384(String secret) {
            return signHmac(claims, secret, ALG_HS384, MAC_SHA384);
        }

        /**
         * 以 HS512（HMAC-SHA-512）签名生成令牌。
         *
         * @param secret HMAC 密钥，不能为空且 UTF-8 编码长度不得小于 16 字节（128 位）
         * @return compact serialization 令牌，不会为 null
         * @throws IllegalArgumentException secret 为 null 或长度不足 16 字节时抛出
         */
        public String signHmacSha512(String secret) {
            return signHmac(claims, secret, ALG_HS512, MAC_SHA512);
        }

        /**
         * 以 RS256（SHA256withRSA）签名生成令牌。
         *
         * @param privateKey RSA 私钥，不能为 null
         * @return compact serialization 令牌，不会为 null
         * @throws IllegalArgumentException privateKey 为 null 时抛出
         */
        public String signRsa(PrivateKey privateKey) {
            // 显式限定外部类静态方法，避免被 Builder 自身的同名方法遮蔽
            return JwtWrapper.signRsa(claims, privateKey);
        }
    }
}
