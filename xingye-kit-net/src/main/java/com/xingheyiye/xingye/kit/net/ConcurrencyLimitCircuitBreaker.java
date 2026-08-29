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

import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * 并发信号量熔断器：以“在途并发请求数”为门禁，超限即快速失败，保护下游免遭并发压垮。
 *
 * <p>本类是 {@link CircuitBreaker} 接口的内置实现（{@link CircuitBreaker} 的可替换选择之一）；
 * 需要其它熔断策略（滑动窗口失败率、基于延迟的熔断）时可自行实现 {@link CircuitBreaker} 接口。</p>
 *
 * <p>与 {@link SlidingWindowCircuitBreaker} 的取舍：滑动窗口按“最近失败率”熔断，适合下游已
 * 出现故障时避免雪崩；信号量熔断按“并发上限”门禁，不统计成败，适合下游本身健康但并发能力有限、
 * 需要硬性控制瞬时并发量的场景（线程池隔离、慢接口限流）。两者可叠加使用。</p>
 *
 * <p>使用契约（必须严格遵守，否则会泄漏许可）：</p>
 * <ul>
 *     <li>{@link #allowRequest()} 返回 true 表示已占用 1 个并发许可；</li>
 *     <li>占用许可后，无论调用成功或失败，都必须调用一次
 *         {@link #recordSuccess()} 或 {@link #recordFailure()} 归还许可；</li>
 *     <li>{@link #allowRequest()} 返回 false 表示并发已满，调用方应直接走降级逻辑，不得再记录。</li>
 * </ul>
 *
 * <p>状态语义：并发许可充足时为 {@code CLOSED}（放行），许可耗尽时为 {@code OPEN}（拒绝）；
 * 本实现不产生 {@code HALF_OPEN} 状态（试探由并发余量自然承担）。</p>
 *
 * <p>线程安全性：底层 {@link Semaphore} 线程安全，可被多线程并发调用。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * CircuitBreaker breaker = new ConcurrencyLimitCircuitBreaker("pay-service", 50);
 * if (breaker.allowRequest()) {
 *     try {
 *         callRemoteService();
 *         breaker.recordSuccess();
 *     } catch (IOException e) {
 *         breaker.recordFailure();
 *     }
 * } else {
 *     // 并发已满：走降级/排队逻辑
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public class ConcurrencyLimitCircuitBreaker implements CircuitBreaker {

    /** 熔断器名称，仅用于日志/监控标识。 */
    private final String name;
    /** 允许的最大并发（在途）请求数。 */
    private final int maxConcurrentRequests;
    /** 并发许可：每次放行占用 1 个，记录成功/失败时归还 1 个。 */
    private final Semaphore semaphore;

    /**
     * 创建并发信号量熔断器。
     *
     * @param name 熔断器名称，用于日志与监控，不可为 null
     * @param maxConcurrentRequests 允许的最大并发请求数，必须不小于 1
     * @throws NullPointerException name 为 null
     * @throws IllegalArgumentException maxConcurrentRequests 越界
     */
    public ConcurrencyLimitCircuitBreaker(String name, int maxConcurrentRequests) {
        this.name = Objects.requireNonNull(name, "name 不能为 null");
        if (maxConcurrentRequests < 1) {
            throw new IllegalArgumentException("maxConcurrentRequests 必须不小于 1: " + maxConcurrentRequests);
        }
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.semaphore = new Semaphore(maxConcurrentRequests, true);
    }

    /**
     * 尝试占用 1 个并发许可。
     *
     * <p>占用成功返回 true（状态为 CLOSED）；许可耗尽返回 false（状态为 OPEN）。
     * 占用成功后必须调用 {@link #recordSuccess()} 或 {@link #recordFailure()} 归还许可。</p>
     *
     * @return 允许发起请求返回 true；并发已满返回 false（应快速失败/降级）
     */
    @Override
    public boolean allowRequest() {
        return semaphore.tryAcquire();
    }

    /**
     * 记录一次调用成功：归还 {@link #allowRequest()} 占用的并发许可。
     */
    @Override
    public void recordSuccess() {
        semaphore.release();
    }

    /**
     * 记录一次调用失败：归还 {@link #allowRequest()} 占用的并发许可。
     */
    @Override
    public void recordFailure() {
        semaphore.release();
    }

    /**
     * @return 并发许可耗尽返回 {@code OPEN}，否则返回 {@code CLOSED}，永不为 null
     */
    @Override
    public State getState() {
        return semaphore.availablePermits() == 0 ? State.OPEN : State.CLOSED;
    }

    /**
     * 人工复位：将并发许可恢复为满额。
     *
     * <p>注意：应在无在途请求时调用（即所有已占用许可都已通过 record 归还），
     * 否则会把当前在途请求也算入满额，导致瞬时超额放行。</p>
     */
    @Override
    public void reset() {
        semaphore.drainPermits();
        semaphore.release(maxConcurrentRequests);
    }

    /**
     * @return 熔断器名称，永不为 null
     */
    public String getName() {
        return name;
    }

    /**
     * @return 允许的最大并发请求数
     */
    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    /**
     * @return 当前剩余的并发许可数（0 表示并发已满）
     */
    public int availablePermits() {
        return semaphore.availablePermits();
    }
}
