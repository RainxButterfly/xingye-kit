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
import com.xingheyiye.xingye.kit.id.UuidUtils;

/**
 * 基于 UUID 的 ID 生成器实现，返回 32 位无连字符的 UUID V4 字符串。
 *
 * <p>适用场景：不需要趋势有序、仅需全局唯一标识的场景（traceId、幂等键等）；
 * 若用作 MySQL 主键请优先考虑趋势有序的 {@code Snowflake} 或 {@code UuidUtils.ordered()}。
 *
 * <p>线程安全性：无状态，底层 {@code UUID.randomUUID()} 线程安全，可并发调用。
 *
 * <p>使用示例：
 * <pre>{@code
 * IdGenerator generator = new UuidIdGenerator();
 * String id = generator.nextId(); // "550e8400e29b41d4a716446655440000"
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-21
 */
public class UuidIdGenerator implements IdGenerator {

    /**
     * 生成 32 位无连字符的 UUID V4 字符串。
     *
     * @return UUID 字符串，全局唯一，恒不为 null
     */
    @Override
    public String nextId() {
        return UuidUtils.simple();
    }
}
