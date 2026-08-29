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

import com.xingheyiye.xingye.kit.core.impl.ExponentialBackoff;
import com.xingheyiye.xingye.kit.core.impl.FixedBackoff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 可配置的重试模板，对可能失败的操作按“最大尝试次数 + 命中异常才重试 + 退避间隔”的策略执行。
 *
 * <p>适用场景：调用不稳定的下游（HTTP 接口、RPC、消息发送）时进行有限次自动重试，
 * 避免在业务代码中手写重试循环。
 *
 * <p>线程安全性：本类构建后不可变（所有字段均为 {@code final}，配置方法返回新实例而非修改自身），
 * 可安全地在多线程间共享同一个实例并发调用 {@code execute}。
 *
 * <p>重试与异常语义：
 * <ul>
 *     <li>仅当抛出的异常命中 {@link #retryOn(Class[])} 配置的类型（含子类）时才重试，其余异常立即抛出；</li>
 *     <li>重试间隔由 {@link #backoff(long)}、{@link #exponentialBackoff(long, double, long)}
 *         或自定义的 {@link #backoff(RetryBackoff)} 决定，未配置则立即重试；</li>
 *     <li>等待期间线程被中断时，会恢复中断标记并抛出 RuntimeException；</li>
 *     <li>重试次数耗尽后重抛最后一次异常：RuntimeException 原样抛出，其余（含 Error）包装为 RuntimeException 抛出。</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * String body = RetryTemplate.create()
 *         .maxAttempts(3)
 *         .retryOn(IOException.class)
 *         .exponentialBackoff(200L, 2.0d, 5000L)
 *         .execute(() -> httpClient.get(url));
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-19
 */
public final class RetryTemplate {

    /** 默认最大尝试次数（含首次执行） */
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    /** 默认重试的异常类型：Exception */
    private static final List<Class<? extends Throwable>> DEFAULT_RETRY_ON =
            Collections.<Class<? extends Throwable>>singletonList(Exception.class);

    /** 最大尝试次数（含首次执行），恒 >= 1 */
    private final int maxAttempts;

    /** 触发重试的异常类型列表（命中任一即重试），不可变列表 */
    private final List<Class<? extends Throwable>> retryOnTypes;

    /** 退避策略（决定每次失败后等待多久再重试），恒非 null */
    private final RetryBackoff backoff;

    /**
     * 私有全参构造器，仅通过 {@link #create()} 与配置方法创建实例。
     *
     * @param maxAttempts 最大尝试次数，恒 >= 1
     * @param retryOnTypes 触发重试的异常类型列表，不可为 null
     * @param backoff 退避策略，不可为 null
     */
    private RetryTemplate(int maxAttempts, List<Class<? extends Throwable>> retryOnTypes,
                          RetryBackoff backoff) {
        this.maxAttempts = maxAttempts;
        this.retryOnTypes = retryOnTypes;
        this.backoff = backoff;
    }

    /**
     * 创建默认配置的重试模板：最多尝试 3 次、命中 Exception 重试、无等待间隔。
     *
     * @return 全新重试模板实例，恒不为 null
     */
    public static RetryTemplate create() {
        return new RetryTemplate(DEFAULT_MAX_ATTEMPTS, DEFAULT_RETRY_ON, new FixedBackoff(0L));
    }

    /**
     * 设置最大尝试次数（含首次执行），返回新的模板实例，原实例不变。
     *
     * @param maxAttempts 最大尝试次数，必须 >= 1
     * @return 应用新配置的模板实例，恒不为 null
     * @throws IllegalArgumentException 当 {@code maxAttempts < 1} 时抛出
     */
    public RetryTemplate maxAttempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts 必须 >= 1，当前: " + maxAttempts);
        }
        return new RetryTemplate(maxAttempts, retryOnTypes, backoff);
    }

    /**
     * 设置触发重试的异常类型（命中任一类型或其子类即重试），返回新的模板实例，原实例不变。
     *
     * @param types 触发重试的异常类型列表，不能为 null、不能为空数组、不能含 null 元素
     * @return 应用新配置的模板实例，恒不为 null
     * @throws IllegalArgumentException 当 {@code types} 为 {@code null}、长度为 0 或包含 {@code null} 元素时抛出
     */
    @SafeVarargs
    public final RetryTemplate retryOn(Class<? extends Throwable>... types) {
        if (types == null || types.length == 0) {
            throw new IllegalArgumentException("types 不能为空");
        }
        List<Class<? extends Throwable>> copied = new ArrayList<Class<? extends Throwable>>(types.length);
        for (Class<? extends Throwable> type : types) {
            if (type == null) {
                throw new IllegalArgumentException("types 中不能包含 null 元素");
            }
            copied.add(type);
        }
        return new RetryTemplate(maxAttempts, Collections.unmodifiableList(copied),
                backoff);
    }

    /**
     * 设置固定重试间隔，返回新的模板实例，原实例不变。
     *
     * <p>等价于 {@code backoff(new FixedBackoff(millis))}，即内置的固定间隔退避实现。</p>
     *
     * @param millis 每次重试前固定等待的毫秒数，必须 >= 0
     * @return 应用新配置的模板实例，恒不为 null
     * @throws IllegalArgumentException 当 {@code millis < 0} 时抛出
     */
    public RetryTemplate backoff(long millis) {
        if (millis < 0L) {
            throw new IllegalArgumentException("millis 不能为负数，当前: " + millis);
        }
        return new RetryTemplate(maxAttempts, retryOnTypes, new FixedBackoff(millis));
    }

    /**
     * 设置自定义退避策略，返回新的模板实例，原实例不变。
     *
     * <p>内置选择：{@code FixedBackoff}（固定间隔）与 {@code ExponentialBackoff}（指数递增）
     * 位于 {@code com.xingheyiye.xingye.kit.core.impl} 包；需要抖动、随机或按异常区分间隔时，
     * 自行实现 {@link RetryBackoff} 接口传入即可。</p>
     *
     * @param backoff 退避策略，不能为 null
     * @return 应用新配置的模板实例，恒不为 null
     * @throws IllegalArgumentException 当 {@code backoff} 为 {@code null} 时抛出
     */
    public RetryTemplate backoff(RetryBackoff backoff) {
        if (backoff == null) {
            throw new IllegalArgumentException("backoff 不能为 null");
        }
        return new RetryTemplate(maxAttempts, retryOnTypes, backoff);
    }

    /**
     * 设置指数退避重试间隔，返回新的模板实例，原实例不变。
     *
     * <p>第 n 次重试前等待 {@code initialMillis * multiplier^(n-1)} 毫秒，并以 {@code maxMillis} 封顶。
     * 等价于 {@code backoff(new ExponentialBackoff(initialMillis, multiplier, maxMillis))}。</p>
     *
     * @param initialMillis 首次重试前等待的毫秒数，必须 >= 0
     * @param multiplier 每次重试的间隔倍率，必须 &gt; 0
     * @param maxMillis 单次等待的毫秒数上限，必须 &gt;= initialMillis
     * @return 应用新配置的模板实例，恒不为 null
     * @throws IllegalArgumentException 当任一参数不满足上述约束时抛出
     */
    public RetryTemplate exponentialBackoff(long initialMillis, double multiplier, long maxMillis) {
        if (initialMillis < 0L) {
            throw new IllegalArgumentException("initialMillis 不能为负数，当前: " + initialMillis);
        }
        if (multiplier <= 0.0d) {
            throw new IllegalArgumentException("multiplier 必须 > 0，当前: " + multiplier);
        }
        if (maxMillis < initialMillis) {
            throw new IllegalArgumentException("maxMillis 不能小于 initialMillis，当前: maxMillis="
                    + maxMillis + ", initialMillis=" + initialMillis);
        }
        return new RetryTemplate(maxAttempts, retryOnTypes,
                new ExponentialBackoff(initialMillis, multiplier, maxMillis));
    }

    /**
     * 按当前配置执行有返回值的操作，失败且命中重试类型时自动重试。
     *
     * @param action 待执行的操作，不能为 null
     * @param <T> 操作返回值类型
     * @return 操作成功时的返回值，可能为 null（取决于操作本身）
     * @throws IllegalArgumentException 当 {@code action} 为 {@code null} 时抛出
     * @throws RuntimeException 重试耗尽后：原异常为 RuntimeException 时原样抛出，否则包装后抛出；
     *                          等待重试被中断时抛出（已恢复中断标记）
     */
    public <T> T execute(Supplier<T> action) {
        if (action == null) {
            throw new IllegalArgumentException("action 不能为 null");
        }
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (Throwable ex) {
                if (!shouldRetry(ex) || attempt == maxAttempts) {
                    throw toRuntimeException(ex);
                }
                sleep(backoff.nextDelayMillis(attempt));
            }
        }
        throw new IllegalStateException("重试循环异常退出，理论上不可到达");
    }

    /**
     * 按当前配置执行无返回值的操作，失败且命中重试类型时自动重试。
     *
     * @param action 待执行的操作，不能为 null
     * @throws IllegalArgumentException 当 {@code action} 为 {@code null} 时抛出
     * @throws RuntimeException 重试耗尽后：原异常为 RuntimeException 时原样抛出，否则包装后抛出；
     *                          等待重试被中断时抛出（已恢复中断标记）
     */
    public void execute(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("action 不能为 null");
        }
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                action.run();
                return;
            } catch (Throwable ex) {
                if (!shouldRetry(ex) || attempt == maxAttempts) {
                    throw toRuntimeException(ex);
                }
                sleep(backoff.nextDelayMillis(attempt));
            }
        }
        throw new IllegalStateException("重试循环异常退出，理论上不可到达");
    }

    /**
     * 判断异常是否命中可重试类型。
     *
     * @param ex 待判断异常，不能为 null
     * @return 命中任一配置类型（含子类）时返回 {@code true}，否则返回 {@code false}
     */
    private boolean shouldRetry(Throwable ex) {
        for (Class<? extends Throwable> type : retryOnTypes) {
            if (type.isInstance(ex)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将耗尽后的最后一次异常转为待抛出的 RuntimeException。
     *
     * @param ex 最后一次异常，不能为 null
     * @return 原异常本身（当其为 RuntimeException 时）或以其为 cause 的新 RuntimeException
     */
    private RuntimeException toRuntimeException(Throwable ex) {
        if (ex instanceof RuntimeException) {
            return (RuntimeException) ex;
        }
        return new RuntimeException(ex.getMessage(), ex);
    }

    /**
     * 重试前等待指定毫秒数。
     *
     * @param millis 等待毫秒数，非正数时直接返回
     * @throws RuntimeException 当等待线程被中断时抛出，抛出前已恢复中断标记
     */
    private void sleep(long millis) {
        if (millis <= 0L) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            // 恢复中断标记，让上层调用方能够感知并处理中断
            Thread.currentThread().interrupt();
            throw new RuntimeException("重试等待被中断", ex);
        }
    }
}
