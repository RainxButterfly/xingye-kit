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

package com.xingheyiye.xingye.kit.net;

import java.util.concurrent.TimeUnit;

/**
 * 漏桶限流器：请求先进入容量有限的桶中排队，再以固定速率匀速流出，桶满时新请求被拒绝或阻塞等待。
 *
 * <p>本类是 {@link RateLimiter} 接口的内置实现（{@link RateLimiter} 的可替换选择之一）；
 * 需要其它限流算法（令牌桶、滑动窗口、GCRA）时可自行实现 {@link RateLimiter} 接口。</p>
 *
 * <p>与 {@link TokenBucketRateLimiter} 的取舍：令牌桶允许“瞬间突发”（桶内积攒的令牌可被
 * 一次大量消费，适合允许短时冲刺的流量）；漏桶把吞吐严格压在匀速上——桶内积压（在途）的
 * 请求数不超过 capacity，且始终按 permitsPerSecond 恒定速率释放，输出更平滑，适合
 * 下游对“瞬时并发量”敏感、必须平滑打流的场景（如消息推送、慢速第三方接口）。</p>
 *
 * <p>原理：以“当前桶内积压量 + 上次排出时刻”建模，每次获取前先按流逝时间把桶惰性排空
 * （无需后台线程），若再加入后不超出 capacity 则放行入桶，否则拒绝；阻塞方法等待桶腾出容量。</p>
 *
 * <p>线程安全性：所有状态由 synchronized(this) 保护，可被多线程并发调用；
 * 阻塞方法在被打断时会恢复线程中断标记并抛出 RuntimeException。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * RateLimiter limiter = new LeakyBucketRateLimiter(10.0d, 20L); // 恒速 10 QPS，桶容量 20
 * if (limiter.tryAcquire()) {
 *     // 放行入桶，执行调用
 * } else {
 *     // 桶满被限流，可降级或丢弃
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public class LeakyBucketRateLimiter implements RateLimiter {

    /** 每秒的纳秒数，用于把排出速率换算为按纳秒流逝的流出量。 */
    private static final double NANOS_PER_SECOND = 1000000000d;
    /** 每毫秒的纳秒数，用于 maxWaitMillis 换算截止时间。 */
    private static final long NANOS_PER_MILLIS = 1000000L;
    /** 单次等待的时间片上限：60 秒。分片等待可避免超长计算溢出，并保证状态变化能被及时感知。 */
    private static final long MAX_WAIT_SLICE_NANOS = 60000000000L;

    /** 排出速率：每秒放行的请求数（恒为正）。 */
    private final double permitsPerSecond;
    /** 桶容量：允许同时在桶内积压（在途）的最大请求数（恒不小于 1）。 */
    private final long capacity;
    /** 当前桶内积压的请求数（含小数，按时间排出），由 this 监视器保护。 */
    private double bucketLevel;
    /** 上次排出积压的时刻（纳秒），由 this 监视器保护。 */
    private long lastDrainNanos;

    /**
     * 创建漏桶限流器（初始为空桶）。
     *
     * @param permitsPerSecond 排出速率：每秒放行（处理）的请求数，必须大于 0 且非 NaN
     * @param capacity 桶容量：允许同时在途的最大请求数，必须不小于 1（同时也是单次获取的上限）
     * @throws IllegalArgumentException 任一参数越界
     */
    public LeakyBucketRateLimiter(double permitsPerSecond, long capacity) {
        if (Double.isNaN(permitsPerSecond) || permitsPerSecond <= 0d) {
            throw new IllegalArgumentException("permitsPerSecond 必须大于 0: " + permitsPerSecond);
        }
        if (capacity < 1L) {
            throw new IllegalArgumentException("capacity 必须不小于 1: " + capacity);
        }
        this.permitsPerSecond = permitsPerSecond;
        this.capacity = capacity;
        this.bucketLevel = 0d;
        this.lastDrainNanos = System.nanoTime();
    }

    /**
     * 尝试立即获取 1 个桶位（不等待）。
     *
     * @return 获取成功返回 true；桶满返回 false
     */
    @Override
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * 尝试立即获取指定数量的桶位（不等待）。
     *
     * @param permits 需要的桶位数，必须满足 1 &lt;= permits &lt;= capacity
     * @return 获取成功返回 true；桶满立即返回 false，不阻塞
     * @throws IllegalArgumentException permits 越界
     */
    @Override
    public boolean tryAcquire(int permits) {
        checkPermits(permits);
        synchronized (this) {
            return tryTakeNowLocked(permits);
        }
    }

    /**
     * 尝试在限定时间内获取指定数量的桶位：先立即尝试，不足则等待桶腾出容量，直到超时。
     *
     * @param permits 需要的桶位数，必须满足 1 &lt;= permits &lt;= capacity
     * @param maxWaitMillis 最长等待时间（毫秒），必须不小于 0；0 表示只做一次立即尝试
     * @return 在期限内获取成功返回 true；超时仍未腾出容量返回 false
     * @throws IllegalArgumentException 任一参数越界
     * @throws RuntimeException 等待期间线程被中断（此时会先恢复中断标记再抛出）
     */
    @Override
    public boolean tryAcquire(int permits, long maxWaitMillis) {
        checkPermits(permits);
        if (maxWaitMillis < 0L) {
            throw new IllegalArgumentException("maxWaitMillis 不能为负: " + maxWaitMillis);
        }
        long deadlineNanos = System.nanoTime() + maxWaitMillis * NANOS_PER_MILLIS;
        synchronized (this) {
            while (true) {
                if (tryTakeNowLocked(permits)) {
                    return true;
                }
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0L) {
                    return false;
                }
                // 只等到“桶腾出容量”或“超时”二者中较早的时刻，避免无谓唤醒
                long waitNanos = Math.min(remainingNanos, nanosUntilFreeLocked(permits));
                try {
                    TimeUnit.NANOSECONDS.timedWait(this, waitNanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // 恢复中断标记，交由上层感知
                    throw new RuntimeException("等待漏桶容量时被中断", e);
                }
            }
        }
    }

    /**
     * 阻塞式获取 1 个桶位，直到成功为止（无限等待）。
     *
     * @throws RuntimeException 等待期间线程被中断（此时会先恢复中断标记再抛出）
     */
    @Override
    public void acquire() {
        synchronized (this) {
            while (true) {
                if (tryTakeNowLocked(1)) {
                    return;
                }
                long waitNanos = nanosUntilFreeLocked(1);
                try {
                    TimeUnit.NANOSECONDS.timedWait(this, waitNanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // 恢复中断标记，交由上层感知
                    throw new RuntimeException("等待漏桶容量时被中断", e);
                }
            }
        }
    }

    /**
     * 校验单次申请的桶位数。
     */
    private void checkPermits(int permits) {
        if (permits < 1) {
            throw new IllegalArgumentException("permits 必须不小于 1: " + permits);
        }
        if (permits > capacity) {
            throw new IllegalArgumentException("permits 不能超过桶容量 " + capacity + ": " + permits);
        }
    }

    /**
     * 在持有监视器的前提下先惰性排空积压，再判断是否能将新请求放入桶中。
     * 积压只随时间自然排出（无提前取出接口），等待方按时间片自行醒来重试，因此无需 notify。
     */
    private boolean tryTakeNowLocked(int permits) {
        drainLocked();
        if (bucketLevel + permits <= capacity) {
            bucketLevel += permits;
            return true;
        }
        return false;
    }

    /**
     * 在持有监视器的前提下按流逝时间惰性排出积压，排空量封底为 0。
     */
    private void drainLocked() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastDrainNanos;
        if (elapsedNanos <= 0L) {
            return; // 尚未流逝（或 nanoTime 回绕/被调表），跳过本次排出
        }
        double drained = elapsedNanos * permitsPerSecond / NANOS_PER_SECOND;
        bucketLevel = Math.max(0d, bucketLevel - drained);
        lastDrainNanos = now;
    }

    /**
     * 在持有监视器的前提下估算“再等多久桶就能腾出 permits 个桶位”，结果按 60 秒分片封顶。
     */
    private long nanosUntilFreeLocked(int permits) {
        double freeCapacity = capacity - bucketLevel;
        double deficit = permits - freeCapacity;
        if (deficit <= 0d) {
            return 0L;
        }
        // 向上取整，避免提前醒来后空转；再按时间片封顶防止极端速率下的数值溢出
        double nanos = Math.ceil(deficit / permitsPerSecond * NANOS_PER_SECOND);
        return (long) Math.min(nanos, MAX_WAIT_SLICE_NANOS);
    }
}
