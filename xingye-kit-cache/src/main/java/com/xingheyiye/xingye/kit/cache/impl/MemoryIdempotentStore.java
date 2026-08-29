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
 * @since 2026-08-28
 */
package com.xingheyiye.xingye.kit.cache.impl;

import java.util.concurrent.ConcurrentHashMap;

import com.xingheyiye.xingye.kit.cache.IdempotentStore;

/**
 * 单机内存版幂等标记存储：基于 ConcurrentHashMap 的带 TTL putIfAbsent 实现。
 *
 * <p>一句话职责：在单个 JVM 内以"过期即视为不存在"的语义实现 {@link IdempotentStore}。</p>
 *
 * <p>适用场景：单实例部署或开发自测。多实例部署时各进程内存不共享，幂等语义失效，
 * 请改用基于 Redis 的实现；进程重启后标记全部丢失。</p>
 *
 * <p>线程安全性：putIfAbsent 通过 compute 原子清理过期旧值后再写入，
 * ConcurrentHashMap 保证并发下的原子性与可见性，可多线程共享实例。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * Idempotent idempotent = new Idempotent(new MemoryIdempotentStore());
 * if (idempotent.tryBegin(requestNo, 30000)) {
 *     // 首次处理
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-28
 */
public class MemoryIdempotentStore implements IdempotentStore {

    /** 存储结构：幂等键 -> 带过期时刻的标记值 */
    private final ConcurrentHashMap<String, ExpiredValue> store = new ConcurrentHashMap<String, ExpiredValue>();

    /**
     * 仅当键不存在（或已过期）时写入标记。
     *
     * <p>读取采用惰性过期：写入前先以 compute 原子移除该键上已过期的旧值，
     * 随后执行 putIfAbsent，两者配合保证"过期标记等价于不存在"的 putIfAbsent 语义。</p>
     *
     * @param key       幂等键，不能为 null 或空串
     * @param ttlMillis 标记存活时长（毫秒），必须大于 0
     * @return true 表示写入成功；false 表示存在未过期的标记（重复请求）
     * @throws IllegalArgumentException key 为 null 或空串、ttlMillis 小于等于 0 时抛出
     */
    @Override
    public boolean putIfAbsent(String key, long ttlMillis) {
        if (key == null || key.length() == 0) {
            throw new IllegalArgumentException("key 不能为 null 或空串");
        }
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("ttlMillis 必须大于 0，单位毫秒");
        }
        long expireAt = System.currentTimeMillis() + ttlMillis;
        // 先原子清理该键上已过期的旧标记：若不清理，putIfAbsent 会对过期标记返回 false，
        // 破坏"过期即视为不存在"的语义；compute 对单个键的 remapping 是原子的
        store.compute(key, (k, oldValue) -> oldValue != null && oldValue.isExpired() ? null : oldValue);
        ExpiredValue previous = store.putIfAbsent(key, new ExpiredValue(expireAt));
        // previous 为 null 表示写入成功；此处兜底再判断过期，防御并发下的极端竞态
        return previous == null || previous.isExpired();
    }

    /**
     * 移除幂等标记。
     *
     * @param key 幂等键；为 null 时静默忽略；键不存在时不产生任何效果
     */
    @Override
    public void remove(String key) {
        if (key == null) {
            return;
        }
        store.remove(key);
    }

    /**
     * 带过期时刻的标记值。
     */
    private static final class ExpiredValue {

        /** 过期时刻（System.currentTimeMillis() 基准，单位：毫秒） */
        private final long expireAtMillis;

        /**
         * 构造标记值。
         *
         * @param expireAtMillis 过期时刻（毫秒时间戳）
         */
        ExpiredValue(long expireAtMillis) {
            this.expireAtMillis = expireAtMillis;
        }

        /**
         * 判断标记是否已过期。
         *
         * @return true 表示当前时间已到达或超过过期时刻
         */
        boolean isExpired() {
            return System.currentTimeMillis() >= expireAtMillis;
        }
    }
}
