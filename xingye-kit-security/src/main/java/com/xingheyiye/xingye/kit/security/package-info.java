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

/**
 * 星河工具库 —— 安全组件模块。
 *
 * <p>一句话职责：提供零第三方依赖的加密、哈希、JWT、密码哈希与敏感字段脱敏能力。</p>
 *
 * <p>模块能力：</p>
 * <ul>
 *     <li>JWT（{@link com.xingheyiye.xingye.kit.security.JwtWrapper}）：零依赖自实现，
 *         支持 HS256/HS384/HS512 与 RS256 签发与校验，配套内置极简 JSON 编解码；</li>
 *     <li>密码哈希：内置 PBKDF2 实现
 *         {@code com.xingheyiye.xingye.kit.security.impl.Pbkdf2PasswordHasher}；
 *         BCrypt/Argon2 可通过实现
 *         {@link com.xingheyiye.xingye.kit.security.PasswordHasher} 接口包装
 *         spring-security-crypto 或 Bouncy Castle 等第三方库接入，业务代码无需感知具体算法；</li>
 *     <li>对称/非对称加密：{@link com.xingheyiye.xingye.kit.security.AESUtils}（AES-GCM，含口令派生密钥）、
 *         {@link com.xingheyiye.xingye.kit.security.RSAUtils}（OAEP 加密与 SHA256withRSA 签名）；</li>
 *     <li>摘要与安全比较：{@link com.xingheyiye.xingye.kit.security.HashUtils}；</li>
 *     <li>敏感数据脱敏：{@link com.xingheyiye.xingye.kit.security.SensitiveMask} 配合
 *         {@link com.xingheyiye.xingye.kit.security.Sensitive} 注解按
 *         {@link com.xingheyiye.xingye.kit.security.SensitiveType} 规则对实体字段脱敏。</li>
 * </ul>
 *
 * <p>线程安全性：本模块的工具类均为全静态实现且无共享可变状态（Cipher/Signature 等按调用新建），
 * 可在多线程环境直接使用；JwtClaims 不可变，天然线程安全。</p>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-28
 */
package com.xingheyiye.xingye.kit.security;
