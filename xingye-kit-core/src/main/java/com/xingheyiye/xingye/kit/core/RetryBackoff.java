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
package com.xingheyiye.xingye.kit.core;

/**
 * 重试退避策略契约：给定重试序号，返回该次重试前应等待的毫秒数。
 *
 * <p>一句话职责：把"重试间隔怎么算"抽象为可替换的策略，供 {@link RetryTemplate} 在每次失败后
 * 决定等待多久再重试，业务代码通过 {@link RetryTemplate#backoff(RetryBackoff)} 注入自定义策略。</p>
 *
 * <p>内置选择：本库提供两种开箱即用的实现
 * {@code com.xingheyiye.xingye.kit.core.impl.FixedBackoff}（固定间隔）与
 * {@code com.xingheyiye.xingye.kit.core.impl.ExponentialBackoff}（指数递增，带封顶）。
 * 需要抖动（jitter）、随机退避或按异常区分间隔等自定义策略时，自行实现本接口即可。</p>
 *
 * <p>线程安全性：接口方法不应修改任何共享状态；内置实现均为不可变对象，可被多线程共享。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * RetryBackoff custom = new RetryBackoff() {
 *     @Override
 *     public long nextDelayMillis(int attempt) {
 *         return 100L * attempt; // 线性递增：100ms、200ms、300ms...
 *     }
 * };
 * String body = RetryTemplate.create()
 *         .maxAttempts(4)
 *         .backoff(custom)
 *         .execute(() -> httpGet("/api"));
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public interface RetryBackoff {

    /**
     * 计算第 {@code attempt} 次失败后的重试等待毫秒数。
     *
     * @param attempt 刚刚失败的尝试序号，从 1 开始（1 表示首次失败，即第一次重试前的等待）
     * @return 等待毫秒数，必须非负
     */
    long nextDelayMillis(int attempt);
}
