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

/**
 * 熔断器契约：在下游故障期间快速失败、冷却后试探性放行，避免雪崩式的资源耗尽。
 *
 * <p>一句话职责：把"熔断策略"抽象为可替换的端口，业务代码只依赖本接口做
 * "先 {@link #allowRequest()} 门禁，再按调用结果 {@link #recordSuccess()}/{@link #recordFailure()} 反馈"。</p>
 *
 * <p>内置选择：</p>
 * <ul>
 *     <li>{@link SlidingWindowCircuitBreaker}：滑动窗口失败率，CLOSED/OPEN/HALF_OPEN 状态机；</li>
 *     <li>{@link ConcurrencyLimitCircuitBreaker}：并发信号量，按在途并发上限硬性门禁。</li>
 * </ul>
 * <p>需要其它熔断策略（基于延迟的熔断）时，
 * 可自行实现本接口包装 Resilience4j、Hystrix 等，业务代码面向接口不变。</p>
 *
 * <p>线程安全性：接口本身不约束线程安全性，由实现方声明；
 * {@link SlidingWindowCircuitBreaker} 全部状态由 synchronized 保护，
 * {@link ConcurrencyLimitCircuitBreaker} 基于 {@link java.util.concurrent.Semaphore}，
 * 均可被多线程并发调用。</p>
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
public interface CircuitBreaker {

    /**
     * 判断当前是否允许发起请求（不产生任何记录，仅做门禁判定）。
     *
     * @return 允许发起请求返回 true；应快速失败（走降级）返回 false
     */
    boolean allowRequest();

    /**
     * 记录一次调用成功。
     */
    void recordSuccess();

    /**
     * 记录一次调用失败。
     */
    void recordFailure();

    /**
     * @return 当前状态，永不为 null
     */
    State getState();

    /**
     * 人工复位：回到初始放行状态（用于运维干预或下游发布完成后手动恢复）。
     */
    void reset();

    /**
     * 熔断器状态。
     *
     * <p>线程安全性：枚举常量不可变，可被任意线程读取。</p>
     *
     * @author 星河一叶 (RainxButterfly)
     * @since 2026-08-25
     */
    enum State {

        /** 关闭（正常放行）：所有请求通过，持续统计失败率。 */
        CLOSED,

        /** 打开（熔断中）：所有请求被拒绝，直到冷却期结束。 */
        OPEN,

        /** 半开（试探中）：放行有限个试探请求，全部成功则回 CLOSED，任一失败则回 OPEN。 */
        HALF_OPEN
    }
}
