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

import java.util.Locale;

/**
 * 基于 {@link System#nanoTime()} 的简易毫秒计时器，支持多次 start/stop 分段累计耗时。
 *
 * <p>适用场景：方法耗时观测、代码片段性能对比、日志中的耗时统计等轻量计时场景。
 *
 * <p>线程安全性：<b>非线程安全</b>，单个实例仅应在同一线程内使用；
 * 跨线程计时请为每个线程创建独立实例。
 *
 * <p>使用示例：
 * <pre>{@code
 * StopWatch watch = new StopWatch();
 * watch.start();
 * doSomething();
 * watch.stop();
 * System.out.println(watch.pretty()); // 例如 "123.4 ms"
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-19
 */
public final class StopWatch {

    /** 纳秒与毫秒的换算系数：1 毫秒 = 1,000,000 纳秒 */
    private static final long NANOS_PER_MILLI = 1000000L;

    /** pretty() 输出切换为秒单位的阈值：不足 1000 毫秒以毫秒输出，否则以秒输出 */
    private static final double MILLIS_THRESHOLD_FOR_SECONDS = 1000.0d;

    /** 当前计时片段的起始纳秒值；0 表示当前不在计时中 */
    private long startNanos;

    /** 已停止片段累计的纳秒总数 */
    private long totalNanos;

    /** 是否处于计时中 */
    private boolean running;

    /**
     * 开始计时。
     *
     * @return 当前实例，便于链式调用，恒不为 null
     * @throws IllegalStateException 当实例已处于计时中（上次 start 后未 stop）时抛出
     */
    public StopWatch start() {
        if (running) {
            throw new IllegalStateException("StopWatch 已处于计时中，请先调用 stop()");
        }
        running = true;
        startNanos = System.nanoTime();
        return this;
    }

    /**
     * 停止计时，并将本片段耗时累加到总耗时。
     *
     * @return 当前实例，便于链式调用，恒不为 null
     * @throws IllegalStateException 当实例尚未 start（或已 stop）时抛出
     */
    public StopWatch stop() {
        if (!running) {
            throw new IllegalStateException("StopWatch 尚未 start，无法 stop");
        }
        totalNanos += System.nanoTime() - startNanos;
        running = false;
        startNanos = 0L;
        return this;
    }

    /**
     * 获取累计耗时（毫秒，含小数）。
     *
     * <p>若当前仍处于计时中，返回值包含进行中的片段耗时；支持多次 start/stop 后的累计结果。
     *
     * @return 累计耗时毫秒数（非负数），恒不为 {@code null}（原始包装类型）
     */
    public double elapsedMillis() {
        long current = totalNanos + (running ? System.nanoTime() - startNanos : 0L);
        return current / (double) NANOS_PER_MILLI;
    }

    /**
     * 获取人类可读的耗时描述：不足 1000 毫秒输出如 {@code "123.4 ms"}，
     * 否则输出如 {@code "1.234 s"}。
     *
     * @return 可读的耗时字符串，恒不为 null
     */
    public String pretty() {
        double millis = elapsedMillis();
        if (millis < MILLIS_THRESHOLD_FOR_SECONDS) {
            return String.format(Locale.ROOT, "%.1f ms", millis);
        }
        return String.format(Locale.ROOT, "%.3f s", millis / MILLIS_THRESHOLD_FOR_SECONDS);
    }
}
