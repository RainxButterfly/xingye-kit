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

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 先进先出（FIFO）淘汰策略：只按写入顺序淘汰，读取不改变淘汰顺序，实现最轻量、行为最可预期。
 *
 * <p>本类是 {@link EvictionPolicy} 接口的内置实现（{@link EvictionPolicy} 的可替换选择之一）。
 * 适合“先写入的先淘汰”即可满足需求的场景，如短期缓存、限流计数桶；对“热数据保活”有要求的
 * 场景请改用 {@link LruEvictionPolicy} 或自定义策略。</p>
 *
 * <p>写入同一键视为覆盖，会移动到队尾（先入先出按最后一次写入计算淘汰顺序）；读取命中不改变顺序。</p>
 *
 * <p>线程安全性：内部 {@link ConcurrentLinkedDeque} 线程安全，可被多线程并发调用。</p>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public class FifoEvictionPolicy<K> implements EvictionPolicy<K> {

    /** 写入顺序队列：队头为最早写入，队尾为最近写入 */
    private final ConcurrentLinkedDeque<K> order = new ConcurrentLinkedDeque<K>();

    /**
     * 读取命中：不改变 FIFO 淘汰顺序（无操作）。
     */
    @Override
    public void onAccess(K key) {
        // FIFO 语义下读取不改变淘汰顺序
    }

    /**
     * 写入（含覆盖）：移动到队尾（按最后一次写入计算淘汰顺序）。
     */
    @Override
    public void onWrite(K key) {
        order.remove(key);
        order.addLast(key);
    }

    /**
     * @return 队头（最早写入）的键并出队；队列为空返回 null
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
}
