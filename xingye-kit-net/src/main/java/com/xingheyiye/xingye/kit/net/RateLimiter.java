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
 * @since 2026-08-25
 */

package com.xingheyiye.xingye.kit.net;

import java.util.concurrent.TimeUnit;

/**
 * 令牌桶限流器：以固定速率向桶内补充令牌，业务请求每次消费若干令牌，桶空时获取失败或阻塞等待。
 *
 * <p>原理：桶容量为 maxBurstPermits（允许的最大瞬时突发量），补充速率为 permitsPerSecond（长期平均速率）。
 * 令牌按纳秒精度惰性补充，无需任何后台线程——每次获取前先按"距上次补充的时间差 × 补充速率"
 * 把桶补到不超容量的水平，再判断当前请求能否满足。因此长期平均速率被 permitsPerSecond 约束，
 * 同时允许短暂突发（最高一次放行桶内已有的全部令牌对应的请求数）。</p>
 *
 * <p>适用场景：保护下游接口、限制爬虫/批量任务速率、平滑定时任务的突发触发。</p>
 *
 * <p>线程安全性：所有状态由 synchronized(this) 保护，可被多线程并发调用；
 * 阻塞方法在被打断时会恢复线程中断标记并抛出 RuntimeException。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * RateLimiter limiter = new RateLimiter(10.0d, 20L); // 平均 10 QPS，最多突发 20 个
 * if (limiter.tryAcquire()) {
 *     // 拿到令牌，执行调用
 * } else {
 *     // 被限流，可降级或丢弃
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-25
 */
public final class RateLimiter {

    /** 每秒的纳秒数，用于把补充速率换算为按纳秒流逝的令牌增量。 */
    private static final double NANOS_PER_SECOND = 1000000000d;
    /** 每毫秒的纳秒数，用于 maxWaitMillis 换算截止时间。 */
    private static final long NANOS_PER_MILLIS = 1000000L;
    /** 单次等待的时间片上限：60 秒。分片等待可避免超长计算溢出，并保证状态变化能被及时感知。 */
    private static final long MAX_WAIT_SLICE_NANOS = 60000000000L;

    /** 补充速率：每秒令牌数（恒为正）。 */
    private final double permitsPerSecond;
    /** 桶容量：最大突发令牌数（恒不小于 1）。 */
    private final long maxBurstPermits;
    /** 当前可用令牌数（含小数，按时间累积），由 this 监视器保护。 */
    private double availablePermits;
    /** 上次补充令牌的时刻（纳秒），由 this 监视器保护。 */
    private long lastRefillNanos;

    /**
     * 创建令牌桶限流器（初始为满桶）。
     *
     * @param permitsPerSecond 补充速率：每秒补充的令牌数（也是长期平均允许的请求数），必须大于 0 且非 NaN
     * @param maxBurstPermits 桶容量：允许的最大瞬时突发令牌数，必须不小于 1（同时也是单次 acquire 的上限）
     * @throws IllegalArgumentException 任一参数越界
     */
    public RateLimiter(double permitsPerSecond, long maxBurstPermits) {
        if (Double.isNaN(permitsPerSecond) || permitsPerSecond <= 0d) {
            throw new IllegalArgumentException("permitsPerSecond 必须大于 0: " + permitsPerSecond);
        }
        if (maxBurstPermits < 1L) {
            throw new IllegalArgumentException("maxBurstPermits 必须不小于 1: " + maxBurstPermits);
        }
        this.permitsPerSecond = permitsPerSecond;
        this.maxBurstPermits = maxBurstPermits;
        this.availablePermits = (double) maxBurstPermits; // 初始为满桶，允许开闸即突发
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * 尝试立即获取 1 个令牌（不等待）。
     *
     * @return 获取成功返回 true；令牌不足返回 false
     */
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * 尝试立即获取指定数量的令牌（不等待）。
     *
     * @param permits 需要的令牌数，必须满足 1 &lt;= permits &lt;= maxBurstPermits
     * @return 获取成功返回 true；令牌不足立即返回 false，不阻塞
     * @throws IllegalArgumentException permits 越界
     */
    public boolean tryAcquire(int permits) {
        checkPermits(permits);
        synchronized (this) {
            return tryTakeNowLocked(permits);
        }
    }

    /**
     * 尝试在限定时间内获取指定数量的令牌：先立即尝试，不足则等待令牌补充，直到超时。
     *
     * @param permits 需要的令牌数，必须满足 1 &lt;= permits &lt;= maxBurstPermits
     * @param maxWaitMillis 最长等待时间（毫秒），必须不小于 0；0 表示只做一次立即尝试
     * @return 在期限内获取成功返回 true；超时仍未凑足令牌返回 false
     * @throws IllegalArgumentException 任一参数越界
     * @throws RuntimeException 等待期间线程被中断（此时会先恢复中断标记再抛出）
     */
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
                // 只等到“令牌足够”或“超时”二者中较早的时刻，避免无谓唤醒
                long waitNanos = Math.min(remainingNanos, nanosUntilEnoughPermitsLocked(permits));
                try {
                    TimeUnit.NANOSECONDS.timedWait(this, waitNanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // 恢复中断标记，交由上层感知
                    throw new RuntimeException("等待令牌时被中断", e);
                }
            }
        }
    }

    /**
     * 阻塞式获取 1 个令牌，直到成功为止（无限等待）。
     *
     * @throws RuntimeException 等待期间线程被中断（此时会先恢复中断标记再抛出）
     */
    public void acquire() {
        synchronized (this) {
            while (true) {
                if (tryTakeNowLocked(1)) {
                    return;
                }
                long waitNanos = nanosUntilEnoughPermitsLocked(1);
                try {
                    TimeUnit.NANOSECONDS.timedWait(this, waitNanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // 恢复中断标记，交由上层感知
                    throw new RuntimeException("等待令牌时被中断", e);
                }
            }
        }
    }

    /**
     * 校验单次申请的令牌数。
     */
    private void checkPermits(int permits) {
        if (permits < 1) {
            throw new IllegalArgumentException("permits 必须不小于 1: " + permits);
        }
        if (permits > maxBurstPermits) {
            throw new IllegalArgumentException("permits 不能超过桶容量 " + maxBurstPermits + ": " + permits);
        }
    }

    /**
     * 在持有监视器的前提下先惰性补充令牌，再尝试一次性扣除。
     * 令牌只随时间自然补充（无归还接口），等待方按时间片自行醒来重试，因此无需 notify。
     */
    private boolean tryTakeNowLocked(int permits) {
        refillLocked();
        if (availablePermits >= permits) {
            availablePermits -= permits;
            return true;
        }
        return false;
    }

    /**
     * 在持有监视器的前提下按流逝时间惰性补充令牌，补充量封顶桶容量。
     */
    private void refillLocked() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillNanos;
        if (elapsedNanos <= 0L) {
            return; // 尚未流逝（或 nanoTime 回绕/被调表），跳过本次补充
        }
        double newPermits = elapsedNanos * permitsPerSecond / NANOS_PER_SECOND;
        availablePermits = Math.min((double) maxBurstPermits, availablePermits + newPermits);
        lastRefillNanos = now;
    }

    /**
     * 在持有监视器的前提下估算“再等多久令牌就够 permits 个”，结果按 60 秒分片封顶。
     */
    private long nanosUntilEnoughPermitsLocked(int permits) {
        double deficitPermits = permits - availablePermits;
        if (deficitPermits <= 0d) {
            return 0L;
        }
        // 向上取整，避免提前醒来后空转；再按时间片封顶防止极端速率下的数值溢出
        double nanos = Math.ceil(deficitPermits / permitsPerSecond * NANOS_PER_SECOND);
        return (long) Math.min(nanos, MAX_WAIT_SLICE_NANOS);
    }
}
