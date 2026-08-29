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
package com.xingheyiye.xingye.kit.cache;

/**
 * 幂等标记存储抽象：以"键是否已存在"表达"请求是否正在或已被处理"。
 *
 * <p>一句话职责：提供带 TTL 的 putIfAbsent 与 remove 两个原子原语，供 {@link Idempotent} 组合使用。</p>
 *
 * <p>适用场景：接口防重复提交的底层存储。单机部署可用
 * {@code com.xingheyiye.xingye.kit.cache.impl.MemoryIdempotentStore}（本库内置单机内存实现）；
 * 多实例部署请基于 Redis SET NX PX 自行实现，保证跨实例的原子性。</p>
 *
 * <p>线程安全性：接口本身无状态；实现的线程安全性由实现方保证
 * （putIfAbsent 必须是原子操作，否则幂等语义失效）。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * class RedisIdempotentStore implements IdempotentStore {
 *     public boolean putIfAbsent(String key, long ttlMillis) {
 *         return redisClient.setNx(key, "1", ttlMillis);
 *     }
 *
 *     public void remove(String key) {
 *         redisClient.del(key);
 *     }
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-28
 */
public interface IdempotentStore {

    /**
     * 仅当键不存在（或已过期）时写入标记。
     *
     * @param key       幂等键，由实现方约束非 null
     * @param ttlMillis 标记存活时长（毫秒），必须大于 0；到期后标记自动失效，视为不存在
     * @return true 表示写入成功（此前不存在，请求可继续处理）；false 表示键已存在（重复请求）
     */
    boolean putIfAbsent(String key, long ttlMillis);

    /**
     * 移除幂等标记（业务完成或取消时调用，允许后续重试）。
     *
     * @param key 幂等键；键不存在时应静默成功，不抛异常
     */
    void remove(String key);
}
