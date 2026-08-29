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
package com.xingheyiye.xingye.kit.core.impl;

import com.xingheyiye.xingye.kit.core.RetryBackoff;

/**
 * 固定间隔重试退避：每次重试前都等待相同毫秒数。
 *
 * <p>一句话职责：{@link RetryBackoff} 的内置实现之一，适用于"间隔恒定"的简单重试场景。</p>
 *
 * <p>线程安全性：仅持有不可变毫秒数，线程安全，可跨线程共享单例。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * RetryBackoff fixed = new FixedBackoff(200L); // 每次重试前等 200ms
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public final class FixedBackoff implements RetryBackoff {

    /** 每次重试前的固定等待毫秒数（恒非负）。 */
    private final long millis;

    /**
     * 创建固定间隔退避策略。
     *
     * @param millis 每次重试前等待的毫秒数，必须 >= 0
     * @throws IllegalArgumentException millis 为负数时抛出
     */
    public FixedBackoff(long millis) {
        if (millis < 0L) {
            throw new IllegalArgumentException("millis 不能为负数，当前: " + millis);
        }
        this.millis = millis;
    }

    @Override
    public long nextDelayMillis(int attempt) {
        return millis;
    }
}
