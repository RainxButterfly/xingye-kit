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

/**
 * 密码哈希抽象：统一"明文密码 -> 存储串"与"明文密码 + 存储串 -> 是否匹配"两个动作。
 *
 * <p>一句话职责：隔离密码哈希算法，使业务代码不感知具体实现（PBKDF2/BCrypt/Argon2）。</p>
 *
 * <p>适用场景：用户注册/登录、API 凭证校验。本库内置 PBKDF2 实现
 * {@code com.xingheyiye.xingye.kit.security.impl.Pbkdf2PasswordHasher}；
 * 如需 BCrypt/Argon2，可引入 spring-security-crypto 或 Bouncy Castle 后实现本接口进行包装：</p>
 * <pre>{@code
 * // 包装 spring-security-crypto 的 BCrypt 示例（引入对应依赖后）
 * public final class BcryptPasswordHasher implements PasswordHasher {
 *
 *     public String hash(String rawPassword) {
 *         return BCrypt.hashpw(rawPassword, BCrypt.gensalt(12));
 *     }
 *
 *     public boolean verify(String rawPassword, String storedHash) {
 *         return BCrypt.checkpw(rawPassword, storedHash);
 *     }
 * }
 * }</pre>
 *
 * <p>线程安全性：接口本身无状态；实现方应保证实现的线程安全性（无共享可变状态即可）。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * PasswordHasher hasher = new Pbkdf2PasswordHasher();
 * String stored = hasher.hash("raw-password");          // 存库
 * boolean ok = hasher.verify("raw-password", stored);   // 登录校验
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-29
 */
public interface PasswordHasher {

    /**
     * 将明文密码哈希为可存储字符串。
     *
     * <p>规范要求：实现必须使用随机盐，同一明文每次哈希的结果应不同；
     * 返回串需自描述算法与参数（如迭代次数），以便 {@link #verify(String, String)} 独立解析。</p>
     *
     * @param rawPassword 明文密码，由实现方约束非 null
     * @return 可持久化存储的哈希串，不会为 null
     */
    String hash(String rawPassword);

    /**
     * 校验明文密码与存储哈希是否匹配。
     *
     * <p>规范要求：比较必须使用常量时间算法，且必须由本方法完成（不要自行比对 hash 输出）。</p>
     *
     * @param rawPassword 用户输入的明文密码，由实现方约束非 null
     * @param storedHash  {@link #hash(String)} 产生的存储串，由实现方约束非 null
     * @return true 表示密码匹配；false 表示不匹配（密码错误或哈希串格式非法时不得抛异常中断流程，
     *         格式非法的具体约束由实现方在 Javadoc 中声明）
     */
    boolean verify(String rawPassword, String storedHash);
}
