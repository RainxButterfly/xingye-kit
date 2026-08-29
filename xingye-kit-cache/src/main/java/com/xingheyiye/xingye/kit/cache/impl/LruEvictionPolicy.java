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

import com.xingheyiye.xingye.kit.cache.EvictionPolicy;
import com.xingheyiye.xingye.kit.cache.LocalCache;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 最近最少使用（LRU）淘汰策略：读写都会把键提升到“最近使用”位置，超过容量时从最久未使用的一端淘汰。
 *
 * <p>本类是 {@link EvictionPolicy} 接口的内置实现（{@link EvictionPolicy} 的可替换选择之一），
 * 也是 {@link LocalCache} 的默认淘汰策略。热数据会因频繁读写保持存活，冷数据优先被淘汰。</p>
 *
 * <p>近似语义：内部使用 {@link ConcurrentLinkedDeque} 维护访问顺序，命中时执行 remove + addLast；
 * 该操作在大容量下为 O(n)，且并发下顺序可能短暂漂移，因此仅作为容量淘汰的启发式依据，
 * 不保证严格 LRU。缓存条目数极大或读写极热的场景请自行评估该开销。</p>
 *
 * <p>线程安全性：内部 {@link ConcurrentLinkedDeque} 线程安全，可被多线程并发调用。</p>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public class LruEvictionPolicy<K> implements EvictionPolicy<K> {

    /** 访问顺序队列：队头为最久未使用，队尾为最近使用 */
    private final ConcurrentLinkedDeque<K> order = new ConcurrentLinkedDeque<K>();

    /**
     * 读取命中：提升到队尾（最近使用）。
     */
    @Override
    public void onAccess(K key) {
        touch(key);
    }

    /**
     * 写入（含覆盖）：提升到队尾（最近使用）。
     */
    @Override
    public void onWrite(K key) {
        touch(key);
    }

    /**
     * @return 队头（最久未使用）的键并出队；队列为空返回 null
     */
    @Override
    public K evictionCandidate() {
        return order.pollFirst();
    }

    /**
     * 键被移出缓存：从顺序队列中移除其引用。
     */
    @Override
    public void onRemove(K key) {
        order.remove(key);
    }

    /**
     * 清空顺序队列。
     */
    @Override
    public void clear() {
        order.clear();
    }

    /**
     * 把键移动到队尾（最近使用），先移除旧引用避免重复。
     *
     * @param key 缓存键
     */
    private void touch(K key) {
        order.remove(key);
        order.addLast(key);
    }
}
