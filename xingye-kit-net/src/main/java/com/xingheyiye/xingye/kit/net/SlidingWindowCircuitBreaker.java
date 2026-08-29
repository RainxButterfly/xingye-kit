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

import java.util.Arrays;
import java.util.Objects;

/**
 * 基于滑动窗口失败率的熔断器：失败率过高时快速失败，冷却后放行少量试探请求探测下游恢复情况。
 *
 * <p>本类是 {@link CircuitBreaker} 接口的内置实现（{@link CircuitBreaker} 的可替换选择之一）；
 * 需要其它熔断策略（如并发信号量、基于延迟的熔断）时可自行实现 {@link CircuitBreaker} 接口。</p>
 *
 * <p>状态机示意：</p>
 * <pre>
 *   +--------+   失败率 &gt;= 阈值（窗口已满）    +------+
 *   | CLOSED | -----------------------------&gt; | OPEN |
 *   +--------+                                 +------+
 *        ^                                        |
 *        | HALF_OPEN 试探全部成功                  | 冷却 openStateMillis 到期
 *        |                                        v
 *   +----------+    任一试探失败               +------+
 *   |HALF_OPEN | ----------------------------&gt; | OPEN |
 *   +----------+                                +------+
 * </pre>
 *
 * <p>适用场景：包裹不稳定的外部调用（HTTP、RPC、数据库），在下游故障期间直接走降级逻辑，
 * 避免雪崩式的资源耗尽；与 {@link RateLimiter}（限“量”）互补，本类负责限“故障扩散”。</p>
 *
 * <p>线程安全性：全部状态由 synchronized 保护，可被多线程并发调用；
 * 典型用法是"先 {@link #allowRequest()} 判定，再按调用结果 {@link #recordSuccess()}/{@link #recordFailure()}"。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * CircuitBreaker breaker = new SlidingWindowCircuitBreaker("inventory-service");
 * if (breaker.allowRequest()) {
 *     try {
 *         callRemoteService();
 *         breaker.recordSuccess();
 *     } catch (IOException e) {
 *         breaker.recordFailure();
 *     }
 * } else {
 *     // 熔断打开：走降级/缓存逻辑
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-25
 */
public class SlidingWindowCircuitBreaker implements CircuitBreaker {

    /** 缺省滑动窗口大小：最近 10 次调用。 */
    private static final int DEFAULT_SLIDING_WINDOW_SIZE = 10;
    /** 缺省失败率阈值：50%。 */
    private static final double DEFAULT_FAILURE_RATE_THRESHOLD = 0.5d;
    /** 缺省熔断冷却时长：30000 毫秒。 */
    private static final long DEFAULT_OPEN_STATE_MILLIS = 30000L;
    /** 缺省半开态试探请求数：1 个。 */
    private static final long DEFAULT_HALF_OPEN_MAX_TRIALS = 1L;

    /** 熔断器名称，仅用于日志/监控标识。 */
    private final String name;
    /** 滑动窗口大小：参与失败率统计的最近调用次数。 */
    private final int slidingWindowSize;
    /** 失败率阈值（0, 1]：窗口满且失败率不低于该值即熔断。 */
    private final double failureRateThreshold;
    /** OPEN 态冷却时长（毫秒），到期后转入 HALF_OPEN。 */
    private final long openStateMillis;
    /** HALF_OPEN 态最多放行的试探请求数。 */
    private final long halfOpenMaxTrials;

    /** 当前状态，由 this 监视器保护。 */
    private CircuitBreaker.State state = CircuitBreaker.State.CLOSED;
    /** 滑动窗口环形数组：元素为该次调用是否成功，由 this 监视器保护。 */
    private final boolean[] results;
    /** 环形数组的下一个写入位置，由 this 监视器保护。 */
    private int writeIndex;
    /** 窗口内已填充的样本数（不足窗口大小时小于 slidingWindowSize），由 this 监视器保护。 */
    private int filled;
    /** 窗口内的失败样本计数（避免每次全数组扫描），由 this 监视器保护。 */
    private int failureCount;
    /** 进入 OPEN 态的时刻（毫秒时间戳），由 this 监视器保护。 */
    private long openedAtMillis;
    /** HALF_OPEN 态已放行的试探请求数，由 this 监视器保护。 */
    private long halfOpenTrials;
    /** HALF_OPEN 态已成功的试探请求数，由 this 监视器保护。 */
    private long halfOpenSuccesses;

    /**
     * 以缺省参数创建熔断器（窗口 10 次、失败率阈值 0.5、冷却 30 秒、半开试探 1 个）。
     *
     * @param name 熔断器名称，用于日志与监控，不可为 null
     * @throws NullPointerException name 为 null
     */
    public SlidingWindowCircuitBreaker(String name) {
        this(name, DEFAULT_SLIDING_WINDOW_SIZE, DEFAULT_FAILURE_RATE_THRESHOLD,
                DEFAULT_OPEN_STATE_MILLIS, DEFAULT_HALF_OPEN_MAX_TRIALS);
    }

    /**
     * 以完整参数创建熔断器。
     *
     * @param name 熔断器名称，仅用于标识，不可为 null
     * @param slidingWindowSize 滑动窗口大小（统计最近多少次调用），必须不小于 1
     * @param failureRateThreshold 失败率阈值，必须满足 0 &lt; 阈值 &lt;= 1（0.5 表示 50%）
     * @param openStateMillis OPEN 态冷却时长（毫秒），必须大于 0
     * @param halfOpenMaxTrials HALF_OPEN 态最多放行的试探请求数，必须不小于 1
     * @throws NullPointerException name 为 null
     * @throws IllegalArgumentException 任一数值参数越界
     */
    public SlidingWindowCircuitBreaker(String name, int slidingWindowSize, double failureRateThreshold,
            long openStateMillis, long halfOpenMaxTrials) {
        this.name = Objects.requireNonNull(name, "name 不能为 null");
        if (slidingWindowSize < 1) {
            throw new IllegalArgumentException("slidingWindowSize 必须不小于 1: " + slidingWindowSize);
        }
        if (Double.isNaN(failureRateThreshold) || failureRateThreshold <= 0d || failureRateThreshold > 1d) {
            throw new IllegalArgumentException("failureRateThreshold 必须在 (0, 1] 区间: " + failureRateThreshold);
        }
        if (openStateMillis <= 0L) {
            throw new IllegalArgumentException("openStateMillis 必须大于 0: " + openStateMillis);
        }
        if (halfOpenMaxTrials < 1L) {
            throw new IllegalArgumentException("halfOpenMaxTrials 必须不小于 1: " + halfOpenMaxTrials);
        }
        this.slidingWindowSize = slidingWindowSize;
        this.failureRateThreshold = failureRateThreshold;
        this.openStateMillis = openStateMillis;
        this.halfOpenMaxTrials = halfOpenMaxTrials;
        this.results = new boolean[slidingWindowSize];
    }

    /**
     * 判断当前是否允许发起请求（不产生任何记录，仅做门禁判定）。
     *
     * <p>CLOSED：恒放行；OPEN：冷却期内拒绝，冷却结束则转入 HALF_OPEN 并放行首个试探；
     * HALF_OPEN：在 halfOpenMaxTrials 配额内放行试探，超额拒绝。</p>
     *
     * @return 允许发起请求返回 true；应快速失败（走降级）返回 false
     */
    @Override
    public synchronized boolean allowRequest() {
        if (state == CircuitBreaker.State.OPEN) {
            long elapsedMillis = System.currentTimeMillis() - openedAtMillis;
            if (elapsedMillis < openStateMillis) {
                return false; // 冷却未结束：继续熔断
            }
            // 冷却结束：转入半开态，并放行第一个试探请求
            state = CircuitBreaker.State.HALF_OPEN;
            halfOpenTrials = 1L;
            return true;
        }
        if (state == CircuitBreaker.State.HALF_OPEN) {
            if (halfOpenTrials >= halfOpenMaxTrials) {
                return false; // 试探配额已用尽：等待已放行试探的结果
            }
            halfOpenTrials++;
            return true;
        }
        return true; // CLOSED
    }

    /**
     * 记录一次调用成功。
     *
     * <p>CLOSED 态写入滑动窗口并重新评估失败率；HALF_OPEN 态累计试探成功数，
     * 全部试探成功则合闸回 CLOSED 并清空窗口；OPEN 态的反馈视为早前放行请求的迟到回执，忽略。</p>
     */
    @Override
    public synchronized void recordSuccess() {
        if (state == CircuitBreaker.State.HALF_OPEN) {
            halfOpenSuccesses++;
            if (halfOpenSuccesses >= halfOpenMaxTrials) {
                // 全部试探成功：恢复放行
                resetWindow();
                state = CircuitBreaker.State.CLOSED;
            }
            return;
        }
        if (state == CircuitBreaker.State.CLOSED) {
            recordSample(true);
            evaluateFailureRate(); // 成功样本也可能顶出旧的失败样本，需重新评估
        }
    }

    /**
     * 记录一次调用失败。
     *
     * <p>CLOSED 态写入滑动窗口并评估失败率（窗口满且失败率不低于阈值即熔断为 OPEN）；
     * HALF_OPEN 态任一试探失败立即重新熔断为 OPEN 并重新计时冷却；OPEN 态的反馈被忽略。</p>
     */
    @Override
    public synchronized void recordFailure() {
        if (state == CircuitBreaker.State.HALF_OPEN) {
            tripOpen();
            return;
        }
        if (state == CircuitBreaker.State.CLOSED) {
            recordSample(false);
            evaluateFailureRate();
        }
    }

    /**
     * @return 当前状态，永不为 null
     */
    @Override
    public synchronized CircuitBreaker.State getState() {
        return state;
    }

    /**
     * 人工复位：清空滑动窗口与半开计数，回到 CLOSED（用于运维干预或下游发布完成后手动恢复）。
     */
    @Override
    public synchronized void reset() {
        resetWindow();
        openedAtMillis = 0L;
        state = CircuitBreaker.State.CLOSED;
    }

    /**
     * 写入一个调用样本到环形数组，同步维护失败计数与窗口填充量。
     */
    private void recordSample(boolean success) {
        if (results[writeIndex]) {
            failureCount--; // 被顶出的旧样本是失败，失败计数减一
        }
        results[writeIndex] = success;
        if (!success) {
            failureCount++;
        }
        writeIndex = (writeIndex + 1) % slidingWindowSize;
        if (filled < slidingWindowSize) {
            filled++;
        }
    }

    /**
     * 评估窗口失败率：窗口已填满且失败率不低于阈值时熔断为 OPEN。
     */
    private void evaluateFailureRate() {
        if (filled < slidingWindowSize) {
            return; // 样本不足，不做判定，避免小样本误熔断
        }
        double failureRate = (double) failureCount / (double) slidingWindowSize;
        if (failureRate >= failureRateThreshold) {
            tripOpen();
        }
    }

    /**
     * 熔断为 OPEN 并重新开始冷却计时。
     */
    private void tripOpen() {
        state = CircuitBreaker.State.OPEN;
        openedAtMillis = System.currentTimeMillis();
        halfOpenTrials = 0L;
        halfOpenSuccesses = 0L;
    }

    /**
     * 清空滑动窗口与半开计数。
     */
    private void resetWindow() {
        Arrays.fill(results, false);
        writeIndex = 0;
        filled = 0;
        failureCount = 0;
        halfOpenTrials = 0L;
        halfOpenSuccesses = 0L;
    }
}
