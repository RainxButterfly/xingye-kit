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
package com.xingheyiye.xingye.kit.cache;

/**
 * 缓存淘汰策略契约：决定在缓存超过容量上限时按什么顺序淘汰键。
 *
 * <p>一句话职责：把 {@link LocalCache} 的“超容量淘汰顺序”抽象为可替换端口，
 * 与 TTL 过期（由 LocalCache 统一管理）解耦。</p>
 *
 * <p>内置选择：</p>
 * <ul>
 *     <li>{@code com.xingheyiye.xingye.kit.cache.impl.LruEvictionPolicy}：
 *         最近最少使用，读写都会提升键的活跃度，热数据不易被淘汰；</li>
 *     <li>{@code com.xingheyiye.xingye.kit.cache.impl.FifoEvictionPolicy}：
 *         先进先出，只按写入顺序淘汰，实现最轻量、行为最可预期。</li>
 * </ul>
 * <p>需要其它淘汰策略（如基于权重/优先级的淘汰）时，自行实现本接口并通过
 * {@link LocalCache.Builder#evictionPolicy(EvictionPolicy)} 注入即可。</p>
 *
 * <p>调用约定（实现方需保证线程安全，LocalCache 会在多个线程并发调用）：</p>
 * <ul>
 *     <li>{@link #onAccess}：键被读取命中时回调（不得抛出异常）；</li>
 *     <li>{@link #onWrite}：键被写入（含覆盖）时回调；</li>
 *     <li>{@link #evictionCandidate}：返回当前最应被淘汰的键，并从策略内部移除该键，无可淘汰键返回 null；</li>
 *     <li>{@link #onRemove}：键因过期/主动失效/淘汰被移出缓存时回调，用于清理策略内部维护的引用；</li>
 *     <li>{@link #clear}：缓存清空时回调。</li>
 * </ul>
 *
 * <p>线程安全性：接口不约束线程安全性，由实现方声明；内置两个实现内部
 * 使用 {@link java.util.concurrent.ConcurrentLinkedDeque}，线程安全，可被多线程并发调用。</p>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public interface EvictionPolicy<K> {

    /**
     * 键被读取命中时回调（LRU 会提升活跃度；FIFO 忽略）。
     *
     * @param key 被读取的键
     */
    void onAccess(K key);

    /**
     * 键被写入（含覆盖）时回调。
     *
     * @param key 被写入的键
     */
    void onWrite(K key);

    /**
     * 返回当前最应被淘汰的键。
     *
     * <p>实现方应在此方法内将返回的键从内部顺序结构中移除（等价于“已出队”）；
     * 没有可淘汰的键时返回 null，调用方据此结束淘汰循环。</p>
     *
     * @return 应被淘汰的键；无可用键时返回 null
     */
    K evictionCandidate();

    /**
     * 键被移出缓存时回调，用于清理策略内部维护的引用。
     *
     * @param key 被移出缓存的键
     */
    void onRemove(K key);

    /**
     * 缓存被清空时回调，清空策略内部维护的全部顺序信息。
     */
    void clear();
}
