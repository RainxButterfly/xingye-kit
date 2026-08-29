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
package com.xingheyiye.xingye.kit.id.impl;

import com.xingheyiye.xingye.kit.core.IdGenerator;
import com.xingheyiye.xingye.kit.id.ShortCode;

/**
 * 基于 Base62 随机短码的 ID 生成器实现，返回指定长度的安全随机短码。
 *
 * <p>适用场景：邀请码、短链后缀、验证码等需要短小且 URL 安全标识的场景；
 * 注意随机短码不保证时间有序，长位数下才有极低碰撞概率（8 位约 2.18e14 种组合）。
 *
 * <p>线程安全性：短码长度为 {@code final} 字段，底层共享的 {@code SecureRandom} 线程安全，可并发调用。
 *
 * <p>使用示例：
 * <pre>{@code
 * IdGenerator generator = new ShortCodeIdGenerator(8);
 * String code = generator.nextId(); // 例如 "aZ3kQ9xW"
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-21
 */
public class ShortCodeIdGenerator implements IdGenerator {

    /** 默认短码长度：8 位 Base62 约含 47.6 bit 熵，兼顾长度与碰撞概率 */
    private static final int DEFAULT_LENGTH = 8;

    /** 短码长度下限 */
    private static final int MIN_LENGTH = 1;

    /** 短码长度上限 */
    private static final int MAX_LENGTH = 32;

    /** 生成的短码长度，构造后不可变 */
    private final int length;

    /**
     * 以默认长度 8 构造短码生成器。
     */
    public ShortCodeIdGenerator() {
        this(DEFAULT_LENGTH);
    }

    /**
     * 以指定长度构造短码生成器。
     *
     * @param length 短码长度，范围 [1, 32]
     * @throws IllegalArgumentException 当 {@code length} 不在 [1, 32] 范围内时抛出
     */
    public ShortCodeIdGenerator(int length) {
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new IllegalArgumentException("length 必须在 [" + MIN_LENGTH + ", "
                    + MAX_LENGTH + "] 范围内，当前: " + length);
        }
        this.length = length;
    }

    /**
     * 生成指定长度的 Base62 随机短码。
     *
     * @return 随机短码，恒不为 null
     */
    @Override
    public String nextId() {
        return ShortCode.random(length);
    }
}
