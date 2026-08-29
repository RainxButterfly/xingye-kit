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
 * 指数递增重试退避：第 {@code n} 次重试前等待 {@code initialMillis * multiplier^(n-1)} 毫秒，并以 {@code maxMillis} 封顶。
 *
 * <p>一句话职责：{@link RetryBackoff} 的内置实现之一，适用于"失败越频繁、间隔越长"的渐进式重试场景。</p>
 *
 * <p>线程安全性：仅持有不可变参数，线程安全，可跨线程共享单例。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * RetryBackoff exponential = new ExponentialBackoff(200L, 2.0d, 2000L);
 * // 重试前等待：200ms、400ms、800ms、1600ms、2000ms（封顶）...
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public final class ExponentialBackoff implements RetryBackoff {

    /** 首次重试前的等待毫秒数（恒非负）。 */
    private final long initialMillis;

    /** 每次重试的间隔倍率（恒 > 0）。 */
    private final double multiplier;

    /** 单次等待的毫秒数上限（恒 >= initialMillis）。 */
    private final long maxMillis;

    /**
     * 创建指数递增退避策略。
     *
     * @param initialMillis 首次重试前等待的毫秒数，必须 >= 0
     * @param multiplier 每次重试的间隔倍率，必须 > 0
     * @param maxMillis 单次等待的毫秒数上限，必须 >= initialMillis
     * @throws IllegalArgumentException 任一参数不满足上述约束时抛出
     */
    public ExponentialBackoff(long initialMillis, double multiplier, long maxMillis) {
        if (initialMillis < 0L) {
            throw new IllegalArgumentException("initialMillis 不能为负数，当前: " + initialMillis);
        }
        if (Double.isNaN(multiplier) || multiplier <= 0.0d) {
            throw new IllegalArgumentException("multiplier 必须 > 0，当前: " + multiplier);
        }
        if (maxMillis < initialMillis) {
            throw new IllegalArgumentException("maxMillis 不能小于 initialMillis，当前: maxMillis="
                    + maxMillis + ", initialMillis=" + initialMillis);
        }
        this.initialMillis = initialMillis;
        this.multiplier = multiplier;
        this.maxMillis = maxMillis;
    }

    @Override
    public long nextDelayMillis(int attempt) {
        // 指数退避：initialMillis * multiplier^(attempt-1)，以 double 计算避免 long 溢出，再用 maxMillis 封顶
        double raw = initialMillis * Math.pow(multiplier, attempt - 1);
        long computed = (long) Math.min(raw, (double) Long.MAX_VALUE);
        return Math.min(computed, maxMillis);
    }
}
