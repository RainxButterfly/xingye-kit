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
 * @since 2026-08-21
 */
package com.xingheyiye.xingye.kit.id;

import java.security.SecureRandom;

/**
 * Base62 短码工具，提供 long 与 Base62 字符串的互转，以及基于安全随机源的随机短码生成。
 *
 * <p>适用场景：短链编码、自增主键对外展示前的“伪装”、邀请码、验证码等
 * 需要短且仅含 URL 安全字符的场景。
 *
 * <p>线程安全性：全部为无状态静态方法，共享的 {@link SecureRandom} 线程安全，可并发调用。
 *
 * <p>使用示例：
 * <pre>{@code
 * ShortCode.encode(123456789L);  // "8M0kX"
 * ShortCode.decode("8M0kX");     // 123456789L
 * ShortCode.random(8);           // 例如 "aZ3kQ9xW"
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-21
 */
public final class ShortCode {

    /** Base62 字母表：数字 0-9、大写 A-Z、小写 a-z，共 62 字符，均为 URL 安全字符 */
    public static final String ALPHABET_BASE62 =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /** 去歧义字母表：从 Base62 中剔除易混淆的 0、O、1、l、I，共 57 字符，适合人工抄录场景 */
    public static final String ALPHABET_UNAMBIGUOUS =
            "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    /** Base62 进制的基数，由字母表长度决定 */
    private static final int RADIX = ALPHABET_BASE62.length();

    /** 随机短码允许的最小长度 */
    private static final int MIN_RANDOM_LENGTH = 1;

    /** 随机短码允许的最大长度：32 位 Base62 已含约 190 bit 熵，足以覆盖绝大多数短码场景 */
    private static final int MAX_RANDOM_LENGTH = 32;

    /** 加密安全的随机源，SecureRandom 内部实现保证线程安全（必要时内部同步） */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 私有构造器，防止工具类被实例化。
     */
    private ShortCode() {
    }

    /**
     * 将非负 long 编码为 Base62 字符串。
     *
     * @param id 待编码的非负整数，必须 &gt;= 0
     * @return Base62 字符串（0 编码为 "0"，其余不含前导零），恒不为 null
     * @throws IllegalArgumentException 当 {@code id < 0} 时抛出
     */
    public static String encode(long id) {
        if (id < 0L) {
            throw new IllegalArgumentException("id 不能为负数，当前: " + id);
        }
        if (id == 0L) {
            return "0";
        }
        StringBuilder builder = new StringBuilder(11);
        while (id > 0L) {
            builder.append(ALPHABET_BASE62.charAt((int) (id % RADIX)));
            id /= RADIX;
        }
        return builder.reverse().toString();
    }

    /**
     * 将 Base62 字符串解码为 long。
     *
     * @param code 待解码的 Base62 字符串，不能为 null 或空串，
     *             只能包含 {@link #ALPHABET_BASE62} 中的字符
     * @return 解码得到的非负 long
     * @throws IllegalArgumentException 当 {@code code} 为 {@code null}、空串、
     *                                  含非法字符或数值超出 long 范围时抛出
     */
    public static long decode(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("code 不能为空");
        }
        long result = 0L;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            int digit = ALPHABET_BASE62.indexOf(c);
            if (digit < 0) {
                throw new IllegalArgumentException("包含非法字符: '" + c + "'");
            }
            // 乘法溢出防护：结果超出 long 上限时拒绝解码
            if (result > (Long.MAX_VALUE - digit) / RADIX) {
                throw new IllegalArgumentException("数值超出 long 范围: " + code);
            }
            result = result * RADIX + digit;
        }
        return result;
    }

    /**
     * 生成指定长度的 Base62 随机短码（基于 {@link SecureRandom}，可安全用于验证码等场景）。
     *
     * @param length 短码长度，范围 [1, 32]
     * @return 指定长度的随机短码，恒不为 null
     * @throws IllegalArgumentException 当 {@code length} 不在 [1, 32] 范围内时抛出
     */
    public static String random(int length) {
        return random(length, ALPHABET_BASE62);
    }

    /**
     * 生成指定长度的去歧义随机短码（仅使用 {@link #ALPHABET_UNAMBIGUOUS}，
     * 剔除 0/O/1/l/I，适合需要人工抄录或口述的场景）。
     *
     * @param length 短码长度，范围 [1, 32]
     * @return 指定长度的去歧义随机短码，恒不为 null
     * @throws IllegalArgumentException 当 {@code length} 不在 [1, 32] 范围内时抛出
     */
    public static String randomUnambiguous(int length) {
        return random(length, ALPHABET_UNAMBIGUOUS);
    }

    /**
     * 基于指定字母表生成指定长度的安全随机短码。
     *
     * @param length 短码长度，范围 [1, 32]
     * @param alphabet 字母表，不能为 null 且不能为空
     * @return 指定长度的随机短码，恒不为 null
     * @throws IllegalArgumentException 当 {@code length} 不在 [1, 32] 范围内时抛出
     */
    private static String random(int length, String alphabet) {
        if (length < MIN_RANDOM_LENGTH || length > MAX_RANDOM_LENGTH) {
            throw new IllegalArgumentException("length 必须在 [" + MIN_RANDOM_LENGTH + ", "
                    + MAX_RANDOM_LENGTH + "] 范围内，当前: " + length);
        }
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(alphabet.charAt(SECURE_RANDOM.nextInt(alphabet.length())));
        }
        return builder.toString();
    }
}
