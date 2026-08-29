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
 * 限流器契约：限制单位时间内的请求（令牌）吞吐量，供业务在调用前做门禁判定或阻塞等待。
 *
 * <p>一句话职责：把"限流算法"抽象为可替换的端口，业务代码只依赖本接口，
 * 具体算法（令牌桶、漏桶、滑动窗口等）由实现方决定。</p>
 *
 * <p>内置选择：</p>
 * <ul>
 *     <li>{@link TokenBucketRateLimiter}：令牌桶，允许瞬间突发，无需后台线程；</li>
 *     <li>{@link LeakyBucketRateLimiter}：漏桶，吞吐严格匀速、输出平滑，限制在途并发。</li>
 * </ul>
 * <p>需要其它算法（滑动窗口、GCRA）或与网关/代理联动的限流能力时，
 * 可自行实现本接口包装 Guava RateLimiter、Redis 计数等，业务代码面向接口不变。</p>
 *
 * <p>线程安全性：接口本身不约束线程安全性，由实现方声明；
 * {@link TokenBucketRateLimiter} 与 {@link LeakyBucketRateLimiter} 全部状态由 synchronized 保护，
 * 可被多线程并发调用。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * RateLimiter limiter = new TokenBucketRateLimiter(10.0d, 20L); // 平均 10 QPS，最多突发 20 个
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
public interface RateLimiter {

    /**
     * 尝试立即获取 1 个令牌（不等待）。
     *
     * @return 获取成功返回 true；令牌不足返回 false
     */
    boolean tryAcquire();

    /**
     * 尝试立即获取指定数量的令牌（不等待）。
     *
     * @param permits 需要的令牌数，必须满足 1 &lt;= permits &lt;= 实现允许的上限
     * @return 获取成功返回 true；令牌不足立即返回 false，不阻塞
     * @throws IllegalArgumentException permits 越界
     */
    boolean tryAcquire(int permits);

    /**
     * 尝试在限定时间内获取指定数量的令牌：先立即尝试，不足则等待令牌补充，直到超时。
     *
     * @param permits 需要的令牌数，必须满足 1 &lt;= permits &lt;= 实现允许的上限
     * @param maxWaitMillis 最长等待时间（毫秒），必须不小于 0；0 表示只做一次立即尝试
     * @return 在期限内获取成功返回 true；超时仍未凑足令牌返回 false
     * @throws IllegalArgumentException 任一参数越界
     * @throws RuntimeException 等待期间线程被中断（实现需先恢复中断标记再抛出）
     */
    boolean tryAcquire(int permits, long maxWaitMillis);

    /**
     * 阻塞式获取 1 个令牌，直到成功为止（无限等待）。
     *
     * @throws RuntimeException 等待期间线程被中断（实现需先恢复中断标记再抛出）
     */
    void acquire();
}
