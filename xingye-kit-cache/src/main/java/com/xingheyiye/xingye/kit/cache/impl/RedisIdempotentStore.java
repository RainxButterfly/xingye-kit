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
package com.xingheyiye.xingye.kit.cache.impl;

import com.xingheyiye.xingye.kit.cache.IdempotentStore;
import com.xingheyiye.xingye.kit.cache.RedisClient;

/**
 * 基于 {@link RedisClient} 的分布式幂等标记存储：以 SET NX PX 的原子性保证跨实例幂等语义。
 *
 * <p>一句话职责：借助真实 Redis 的 SET NX PX 原子命令，在多实例部署下实现
 * {@link IdempotentStore}（这是与单机 {@link MemoryIdempotentStore} 互补的分布式选择）。</p>
 *
 * <p>本类是 {@link IdempotentStore} 的内置实现之一（{@link IdempotentStore} 的可替换选择）；
 * 与单机版相比：标记存在于共享 Redis，多实例可见且过期语义由 Redis 保证，进程重启不丢
 * （TTL 到期前仍被识别为重复请求）。</p>
 *
 * <p>线程安全性：线程安全取决于传入的 {@link RedisClient} 实现（推荐连接池或多路复用客户端
 * 如 Lettuce；单连接 Jedis 需自行保证线程安全）。putIfAbsent 底层依赖
 * {@link RedisClient#setNx(String, String, long)} 的原子性，这是幂等语义成立的前提。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 生产：传入真实 Redis 适配（Jedis/Lettuce/Redisson 实现 RedisClient）
 * IdempotentStore store = new RedisIdempotentStore(redisClient);
 * Idempotent idempotent = new Idempotent(store);
 *
 * // 本地联调：不部署 Redis，用内置内存版代替
 * IdempotentStore devStore = new RedisIdempotentStore(new InMemoryRedisClient());
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public class RedisIdempotentStore implements IdempotentStore {

    /** 幂等标记在 Redis 中的固定 value（仅表示"已存在"，不承载业务数据） */
    private static final String MARK = "1";

    /** 底层 Redis 命令抽象 */
    private final RedisClient client;

    /**
     * 构造基于 Redis 的幂等标记存储。
     *
     * @param client Redis 命令抽象实现，不能为 null
     * @throws IllegalArgumentException client 为 null 时抛出
     */
    public RedisIdempotentStore(RedisClient client) {
        if (client == null) {
            throw new IllegalArgumentException("client 不能为 null");
        }
        this.client = client;
    }

    /**
     * 仅当键不存在（或已过期）时写入标记。
     *
     * <p>直接委托 {@link RedisClient#setNx(String, String, long)}：SET key 1 NX PX ttl
     * 由 Redis 原子执行，多实例并发下只有一个成功，从而保证幂等语义。</p>
     *
     * @param key       幂等键，不能为 null 或空串
     * @param ttlMillis 标记存活时长（毫秒），必须大于 0；到期后标记自动失效
     * @return true 表示写入成功（此前不存在）；false 表示键已存在（重复请求）
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
        return client.setNx(key, MARK, ttlMillis);
    }

    /**
     * 移除幂等标记。
     *
     * @param key 幂等键；键不存在时静默成功
     * @throws IllegalArgumentException key 为 null 时抛出
     */
    @Override
    public void remove(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key 不能为 null");
        }
        client.del(key);
    }
}
