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
 * @since 2026-08-19
 */
package com.xingheyiye.xingye.kit.core;

/**
 * ID 生成器契约接口，抽象“生成下一个全局唯一 ID”的能力，屏蔽具体发号算法差异。
 *
 * <p>适用场景：订单号、流水号、幂等键、分布式主键等需要字符串形式唯一标识的场景；
 * 业务代码面向本接口编程，通过注入不同实现切换发号策略。
 *
 * <p>线程安全性：由各实现自行声明，本接口不做强制约束。
 *
 * <p>内置实现一览：
 * <ul>
 *     <li>{@code com.xingheyiye.xingye.kit.id.Snowflake}：雪花算法，趋势有序的纯数字串；</li>
 *     <li>{@code com.xingheyiye.xingye.kit.id.impl.UuidIdGenerator}：32 位无连字符 UUID，全局唯一但无序；</li>
 *     <li>{@code com.xingheyiye.xingye.kit.id.impl.ShortCodeIdGenerator}：Base62 随机短码，长度可配。</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * IdGenerator generator = new Snowflake(1L, 1L);
 * String id = generator.nextId();
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-19
 */
public interface IdGenerator {

    /**
     * 生成下一个 ID。
     *
     * @return ID 字符串，恒不为 null；唯一性语义由具体实现保证
     */
    String nextId();
}
