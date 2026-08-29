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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

/**
 * 进程内线程安全本地缓存：支持写入后过期、容量上限近似 LRU 淘汰与命中统计。
 *
 * <p>适用场景：单实例服务的热点数据缓存、字典/配置缓存、通过 loader 回源防击穿；
 * 不适合跨实例共享或需要持久化的场景（请改用 Redis 等外部缓存）。</p>
 *
 * <p>线程安全性：所有公开方法线程安全，可被多线程并发调用。
 * 注意 {@code get(key, loader)} 不对相同 key 做加载去重，并发下同一 key 可能被多个线程同时回源。</p>
 *
 * <p>近似 LRU 语义：内部使用 {@link ConcurrentLinkedDeque} 维护访问顺序，命中时执行 remove + addLast；
 * 该操作在大容量下为 O(n)，且并发下顺序可能短暂漂移，因此仅作为容量淘汰的启发式依据，
 * 不保证严格 LRU。缓存条目数极大或读写极热的场景请自行评估该开销。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * LocalCache<String, String> cache = LocalCache.<String, String>newBuilder()
 *         .maximumSize(2048)                    // 最大 2048 条
 *         .expireAfterWriteMillis(60000)        // 写入 60 秒后过期
 *         .cleanupIntervalSeconds(30)           // 后台每 30 秒清理一次
 *         .build();
 *
 * cache.put("greeting", "hello");
 * String hit = cache.getIfPresent("greeting");                     // "hello"
 * String loaded = cache.get("missing", key -> "value-of-" + key);  // 未命中则回源加载
 * LocalCache.CacheStats stats = cache.stats();                     // 命中/过期/淘汰统计
 *
 * cache.shutdown();                                                // 应用停机时关闭后台清理线程
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-28
 */
public final class LocalCache<K, V> {

    /** 默认最大缓存条目数（条） */
    public static final long DEFAULT_MAXIMUM_SIZE = 1024L;

    /** 默认写入后过期时长（毫秒），3600000 毫秒 = 1 小时 */
    public static final long DEFAULT_EXPIRE_AFTER_WRITE_MILLIS = 3600000L;

    /** 默认后台清理周期（秒） */
    public static final long DEFAULT_CLEANUP_INTERVAL_SECONDS = 60L;

    /** 核心存储：键 -> 缓存条目（含值与过期时刻） */
    private final ConcurrentHashMap<K, Entry<V>> store;

    /** 近似 LRU 访问顺序队列：队头为最久未访问，队尾为最近访问 */
    private final ConcurrentLinkedDeque<K> lruOrder;

    /** 最大缓存条目数（条），超过后按 LRU 顺序从队头淘汰 */
    private final long maximumSize;

    /** 写入后过期时长（纳秒），由毫秒配置换算而来 */
    private final long expireAfterWriteNanos;

    /** 后台清理调度器：单守护线程，周期清理过期与超量条目 */
    private final ScheduledExecutorService cleaner;

    /** 命中/未命中/淘汰/过期计数器（线程安全，实时累加） */
    private final CacheStats stats;

    /** shutdown 幂等控制标记 */
    private final AtomicBoolean shutdown;

    /**
     * 私有构造：仅能通过 Builder 创建，创建时启动后台清理线程。
     */
    private LocalCache(Builder<K, V> builder) {
        this.maximumSize = builder.maximumSize;
        this.expireAfterWriteNanos = TimeUnit.MILLISECONDS.toNanos(builder.expireAfterWriteMillis);
        this.store = new ConcurrentHashMap<K, Entry<V>>();
        this.lruOrder = new ConcurrentLinkedDeque<K>();
        this.stats = new CacheStats();
        this.shutdown = new AtomicBoolean(false);
        this.cleaner = Executors.newSingleThreadScheduledExecutor(new CleanerThreadFactory());
        this.cleaner.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                cleanUp();
            }
        }, builder.cleanupIntervalSeconds, builder.cleanupIntervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * 创建缓存构建器。
     *
     * @param <K> 键类型（键不允许为 null）
     * @param <V> 值类型（值不允许为 null）
     * @return 新的 Builder 实例，不会为 null
     */
    public static <K, V> Builder<K, V> newBuilder() {
        return new Builder<K, V>();
    }

    /**
     * 读取缓存中现存的值（不触发回源）。
     *
     * <p>读取时发现条目已过期会立即惰性移除并计入过期统计。</p>
     *
     * @param key 缓存键，可为 null（null 键直接返回 null 且不计入统计）
     * @return 命中返回缓存值；未命中或已过期返回 null
     */
    public V getIfPresent(K key) {
        if (key == null) {
            return null;
        }
        Entry<V> entry = store.get(key);
        if (entry == null) {
            stats.missCount.increment();
            return null;
        }
        if (isExpired(entry)) {
            // 惰性过期：读取时发现已过期即移除，并计入过期统计
            if (store.remove(key, entry)) {
                stats.expiredCount.increment();
                lruOrder.remove(key);
            }
            stats.missCount.increment();
            return null;
        }
        stats.hitCount.increment();
        touch(key);
        return entry.value;
    }

    /**
     * 读取缓存，未命中时通过 loader 回源并写入缓存。
     *
     * <p>loader 返回 null 表示无数据，此时不写入缓存（避免空值占位）；
     * loader 抛出的任何运行时异常将原样向上传播，本方法不做捕获与包装。</p>
     *
     * @param key    缓存键；为 null 时直接返回 null 且不调用 loader
     * @param loader 回源加载函数；为 null 且未命中时直接返回 null
     * @return 缓存值或 loader 加载的值；无数据时返回 null
     */
    public V get(K key, Function<K, V> loader) {
        if (key == null) {
            return null;
        }
        V cached = getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        if (loader == null) {
            return null;
        }
        V value = loader.apply(key);
        if (value == null) {
            // loader 返回 null 视为无数据，不缓存
            return null;
        }
        put(key, value);
        return value;
    }

    /**
     * 写入缓存并重置该键的过期时间。
     *
     * @param key   缓存键；为 null 时忽略本次写入
     * @param value 缓存值；为 null 时忽略本次写入
     */
    public void put(K key, V value) {
        if (key == null || value == null) {
            return;
        }
        long now = System.nanoTime();
        store.put(key, new Entry<V>(value, now + expireAfterWriteNanos));
        touch(key);
        evictIfNeeded();
    }

    /**
     * 移除指定键。
     *
     * @param key 缓存键；为 null 或不存在时不产生任何效果
     */
    public void invalidate(K key) {
        if (key == null) {
            return;
        }
        store.remove(key);
        lruOrder.remove(key);
    }

    /**
     * 清空全部缓存条目。
     */
    public void invalidateAll() {
        store.clear();
        lruOrder.clear();
    }

    /**
     * 当前缓存条目数。
     *
     * <p>说明：返回的是存储表中的实时条目数，个别已逻辑过期但尚未被惰性/后台清理的条目也会计入。</p>
     *
     * @return 条目数（条），不会为负
     */
    public long size() {
        return store.size();
    }

    /**
     * 获取统计数据（实时视图）。
     *
     * <p>返回的是内部计数器对象本身，后续读写操作会持续反映到该对象中。</p>
     *
     * @return 统计对象，不会为 null
     */
    public CacheStats stats() {
        return stats;
    }

    /**
     * 关闭后台清理调度器。
     *
     * <p>幂等：重复调用无副作用。关闭后缓存仍可正常读写，
     * 过期条目依赖读取时的惰性清理，超量淘汰依赖写入时的即时淘汰。</p>
     */
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            // 仅第一次调用真正关闭调度器，保证幂等
            cleaner.shutdownNow();
        }
    }

    /**
     * 判断条目是否已过期。
     *
     * @param entry 缓存条目
     * @return true 表示已超过写入后过期时长
     */
    private boolean isExpired(Entry<V> entry) {
        return System.nanoTime() - entry.expireAtNanos >= 0;
    }

    /**
     * 将键移动到 LRU 队尾（最近访问）。
     *
     * <p>ConcurrentLinkedDeque 的 remove 为 O(n)，是近似 LRU 的主要代价来源。</p>
     *
     * @param key 缓存键
     */
    private void touch(K key) {
        lruOrder.remove(key);
        lruOrder.addLast(key);
    }

    /**
     * 超过容量上限时按 LRU 顺序从队头淘汰，直到回到上限以内。
     */
    private void evictIfNeeded() {
        while (store.size() > maximumSize) {
            K eldest = lruOrder.pollFirst();
            if (eldest == null) {
                break;
            }
            if (store.remove(eldest) != null) {
                stats.evictionCount.increment();
            }
        }
    }

    /**
     * 后台周期清理：先清理全部已过期条目，再处理超量淘汰。
     */
    private void cleanUp() {
        for (Map.Entry<K, Entry<V>> element : store.entrySet()) {
            if (isExpired(element.getValue())) {
                if (store.remove(element.getKey(), element.getValue())) {
                    stats.expiredCount.increment();
                    lruOrder.remove(element.getKey());
                }
            }
        }
        evictIfNeeded();
    }

    /**
     * 缓存条目：值与过期时刻。
     *
     * @param <V> 值类型
     */
    private static final class Entry<V> {

        /** 缓存值 */
        private final V value;

        /** 过期时刻（基于 System.nanoTime() 的单调时钟，单位：纳秒） */
        private final long expireAtNanos;

        Entry(V value, long expireAtNanos) {
            this.value = value;
            this.expireAtNanos = expireAtNanos;
        }
    }

    /**
     * 后台清理线程工厂：统一命名 "xingye-localcache-cleaner-序号"，守护线程避免阻止 JVM 退出。
     */
    private static final class CleanerThreadFactory implements ThreadFactory {

        /** 线程序号发生器（单调度器下只会取到 1，保留序号便于排查多实例场景） */
        private final AtomicInteger sequence = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "xingye-localcache-cleaner-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

    /**
     * 缓存构建器：以链式调用配置容量、过期时长与清理周期。
     *
     * @param <K> 键类型
     * @param <V> 值类型
     */
    public static final class Builder<K, V> {

        /** 最大缓存条目数（条），默认 1024 */
        private long maximumSize = DEFAULT_MAXIMUM_SIZE;

        /** 写入后过期时长（毫秒），默认 3600000（1 小时） */
        private long expireAfterWriteMillis = DEFAULT_EXPIRE_AFTER_WRITE_MILLIS;

        /** 后台清理周期（秒），默认 60 */
        private long cleanupIntervalSeconds = DEFAULT_CLEANUP_INTERVAL_SECONDS;

        private Builder() {
        }

        /**
         * 设置最大缓存条目数，超过后按近似 LRU 淘汰。
         *
         * @param maximumSize 最大条目数（条），必须大于 0
         * @return 当前 Builder，便于链式调用，不会为 null
         */
        public Builder<K, V> maximumSize(long maximumSize) {
            this.maximumSize = maximumSize;
            return this;
        }

        /**
         * 设置写入后过期时长。
         *
         * @param expireAfterWriteMillis 写入后过期时长（毫秒），必须大于 0
         * @return 当前 Builder，便于链式调用，不会为 null
         */
        public Builder<K, V> expireAfterWriteMillis(long expireAfterWriteMillis) {
            this.expireAfterWriteMillis = expireAfterWriteMillis;
            return this;
        }

        /**
         * 设置后台清理周期。
         *
         * @param cleanupIntervalSeconds 清理周期（秒），必须大于 0
         * @return 当前 Builder，便于链式调用，不会为 null
         */
        public Builder<K, V> cleanupIntervalSeconds(long cleanupIntervalSeconds) {
            this.cleanupIntervalSeconds = cleanupIntervalSeconds;
            return this;
        }

        /**
         * 构建缓存实例并启动后台清理线程。
         *
         * @return 新的缓存实例，不会为 null
         * @throws IllegalArgumentException 任一配置项小于等于 0 时抛出
         */
        public LocalCache<K, V> build() {
            if (maximumSize <= 0) {
                throw new IllegalArgumentException("maximumSize 必须大于 0，当前: " + maximumSize);
            }
            if (expireAfterWriteMillis <= 0) {
                throw new IllegalArgumentException("expireAfterWriteMillis 必须大于 0，当前: " + expireAfterWriteMillis);
            }
            if (cleanupIntervalSeconds <= 0) {
                throw new IllegalArgumentException("cleanupIntervalSeconds 必须大于 0，当前: " + cleanupIntervalSeconds);
            }
            return new LocalCache<K, V>(this);
        }
    }

    /**
     * 缓存统计快照视图：记录命中、未命中、容量淘汰与过期清理次数。
     *
     * <p>线程安全性：内部使用 LongAdder 计数，可被多线程并发累加与读取。</p>
     */
    public static final class CacheStats {

        /** 命中次数（含 get 与 getIfPresent 命中） */
        private final LongAdder hitCount = new LongAdder();

        /** 未命中次数（含 get 与 getIfPresent 未命中） */
        private final LongAdder missCount = new LongAdder();

        /** 容量淘汰次数（超过 maximumSize 被淘汰的条目数） */
        private final LongAdder evictionCount = new LongAdder();

        /** 过期清理次数（惰性或后台清理的过期条目数） */
        private final LongAdder expiredCount = new LongAdder();

        /**
         * 获取命中次数。
         *
         * @return 命中次数（次），不会为负
         */
        public long hitCount() {
            return hitCount.sum();
        }

        /**
         * 获取未命中次数。
         *
         * @return 未命中次数（次），不会为负
         */
        public long missCount() {
            return missCount.sum();
        }

        /**
         * 获取容量淘汰次数。
         *
         * @return 容量淘汰次数（次），不会为负
         */
        public long evictionCount() {
            return evictionCount.sum();
        }

        /**
         * 获取过期清理次数。
         *
         * @return 过期清理次数（次），不会为负
         */
        public long expiredCount() {
            return expiredCount.sum();
        }

        @Override
        public String toString() {
            return "CacheStats{hitCount=" + hitCount.sum()
                    + ", missCount=" + missCount.sum()
                    + ", evictionCount=" + evictionCount.sum()
                    + ", expiredCount=" + expiredCount.sum() + "}";
        }
    }
}
