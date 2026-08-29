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
package com.xingheyiye.xingye.kit.notify.impl;

import com.xingheyiye.xingye.kit.notify.CodeGenerator;

import java.security.SecureRandom;

/**
 * 纯数字验证码生成器：字符集仅含 0-9，输入友好，适合手机号短信验证码。
 *
 * <p>本类是 {@link CodeGenerator} 接口的内置实现（{@link CodeGenerator} 的可替换选择之一）。
 * 纯数字字符集较小（10 种），同长度下碰撞概率高于字母数字混合，如需更高安全性
 * 请改用 {@link AlphanumericCodeGenerator} 或自定义实现。</p>
 *
 * <p>线程安全性：无状态，底层 {@link SecureRandom} 线程安全，可并发调用。</p>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public class NumericCodeGenerator implements CodeGenerator {

    /** 数字字符集 */
    private static final char[] DIGITS = "0123456789".toCharArray();

    /** 安全随机数发生器（SecureRandom 自身线程安全，作为静态常量共享） */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成指定长度的纯数字验证码。
     *
     * @param length 验证码长度，必须大于 0
     * @return 纯数字验证码串，不会为 null
     * @throws IllegalArgumentException length 小于等于 0
     */
    @Override
    public String generate(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length 必须大于 0: " + length);
        }
        char[] code = new char[length];
        for (int i = 0; i < length; i++) {
            code[i] = DIGITS[RANDOM.nextInt(DIGITS.length)];
        }
        return new String(code);
    }
}
