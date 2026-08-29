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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.xingheyiye.xingye.kit.cache.RedisClient;

/**
 * {@link RedisClient} 的单机进程内实现：以 ConcurrentHashMap 模拟 Redis 字符串命令语义。
 *
 * <p>一句话职责：在不部署 Redis 的本地联调、单元测试、CI 场景下，为依赖 {@link RedisClient}
 * 的上层能力（{@code RedisHelper}、Redis 版幂等/验证码存储等）提供可用的内存后端。</p>
 *
 * <p>本类是 {@link RedisClient} 的内置实现之一（{@link RedisClient} 的可替换选择）；
 * 生产环境请改用使用方基于 Jedis/Lettuce/Redisson 适配的真实实现。</p>
 *
 * <p>与真实 Redis 的差异（仅限开发环境使用的取舍）：</p>
 * <ul>
 *     <li>数据仅存于本进程内存，重启即全部丢失；</li>
 *     <li>多实例部署时各进程数据不互通，分布式语义（锁、计数）不跨节点；</li>
 *     <li>TTL 采用读取时惰性过期（{@link #get}/{@link #setNx}/{@link #expire} 等读取或写入时清理），
 *         与 {@code RedisHelper} 的锁/计数用法兼容；</li>
 *     <li>不启动后台清理线程，长期不被访问的过期键会驻留内存直到被同键覆盖或删除。</li>
 * </ul>
 *
 * <p>线程安全性：基于 ConcurrentHashMap，所有命令均为原子操作，可多线程共享单例。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * RedisClient client = new InMemoryRedisClient();
 * RedisHelper helper = new RedisHelper(client, "app1");
 * if (helper.tryLock("order:1001", "worker-07", 30000L)) {
 *     // 临界区
 *     helper.unlock("order:1001", "worker-07");
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public class InMemoryRedisClient implements RedisClient {

    /** 键值存储：键 -> 带过期时刻的字符串值 */
    private final Map<String, Entry> store = new ConcurrentHashMap<String, Entry>();

    /**
     * 读取字符串值；已过期条目在读取时惰性清除并返回 null。
     *
     * @param key 键，不能为 null
     * @return 键对应的值；键不存在或已过期返回 null
     * @throws IllegalArgumentException key 为 null 时抛出
     */
    @Override
    public String get(String key) {
        checkKey(key);
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            store.remove(key);
            return null;
        }
        return entry.value;
    }

    /**
     * 无条件写入字符串值（覆盖旧值并清除过期时间）。
     *
     * @param key   键，不能为 null
     * @param value 值，不能为 null
     * @return 恒为 "OK"（模拟 Redis 成功回复）
     * @throws IllegalArgumentException key 或 value 为 null 时抛出
     */
    @Override
    public String set(String key, String value) {
        checkKey(key);
        if (value == null) {
            throw new IllegalArgumentException("value 不能为 null");
        }
        store.put(key, new Entry(value, Long.MAX_VALUE));
        return "OK";
    }

    /**
     * 仅当键不存在（或已过期）时写入，并同时设置过期时间。
     *
     * @param key          键，不能为 null
     * @param value        值，不能为 null
     * @param expireMillis 过期时长（毫秒），必须大于 0
     * @return true 表示写入成功（此前键不存在或已过期）；false 表示键已存在未写入
     * @throws IllegalArgumentException 参数非法时抛出
     */
    @Override
    public boolean setNx(String key, String value, long expireMillis) {
        checkKey(key);
        if (value == null) {
            throw new IllegalArgumentException("value 不能为 null");
        }
        if (expireMillis <= 0) {
            throw new IllegalArgumentException("expireMillis 必须大于 0，单位毫秒");
        }
        long expireAt = System.currentTimeMillis() + expireMillis;
        // 先原子清理该键上已过期的旧值，保证"过期即视为不存在"的 SET NX 语义
        store.compute(key, (k, oldValue) -> oldValue != null && oldValue.isExpired() ? null : oldValue);
        Entry previous = store.putIfAbsent(key, new Entry(value, expireAt));
        return previous == null || previous.isExpired();
    }

    /**
     * 将键中存储的数字增 1，键不存在时从 0 开始自增（返回 1）；已过期键同样从 0 自增。
     *
     * @param key 键，不能为 null
     * @return 自增后的当前值
     * @throws IllegalArgumentException key 为 null，或当前值为非纯数字字符串时抛出
     */
    @Override
    public long incr(String key) {
        checkKey(key);
        long now = System.currentTimeMillis();
        // compute 在单个键上原子执行：清理过期旧值后按当前值 +1 写回
        Long[] resultHolder = new Long[1];
        store.compute(key, (k, oldValue) -> {
            long base = 0L;
            if (oldValue != null && !oldValue.isExpired()) {
                try {
                    base = Long.parseLong(oldValue.value.trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("incr 要求键 [" + key + "] 的当前值为纯数字，实际: "
                            + oldValue.value);
                }
            }
            long incremented = base + 1;
            resultHolder[0] = incremented;
            return new Entry(String.valueOf(incremented), Long.MAX_VALUE);
        });
        return resultHolder[0].longValue();
    }

    /**
     * 为已存在的键设置过期时间。
     *
     * @param key    键，不能为 null
     * @param millis 过期时长（毫秒），必须大于 0
     * @return true 表示设置成功（键存在且未过期）；false 表示键不存在或已过期
     * @throws IllegalArgumentException key 为 null 或 millis 小于等于 0 时抛出
     */
    @Override
    public boolean expire(String key, long millis) {
        checkKey(key);
        if (millis <= 0) {
            throw new IllegalArgumentException("millis 必须大于 0，单位毫秒");
        }
        long expireAt = System.currentTimeMillis() + millis;
        Entry[] holder = new Entry[1];
        store.computeIfPresent(key, (k, oldValue) -> {
            if (oldValue.isExpired()) {
                return null;
            }
            holder[0] = new Entry(oldValue.value, expireAt);
            return holder[0];
        });
        return holder[0] != null;
    }

    /**
     * 删除键（幂等：键不存在或已过期时静默返回 false）。
     *
     * @param key 键，不能为 null
     * @return true 表示删除成功（键原本存在且未过期）；false 表示键不存在
     * @throws IllegalArgumentException key 为 null 时抛出
     */
    @Override
    public boolean del(String key) {
        checkKey(key);
        Entry removed = store.remove(key);
        return removed != null && !removed.isExpired();
    }

    /**
     * 清空全部键值（仅测试辅助，非 RedisClient 契约方法）。
     */
    public void clear() {
        store.clear();
    }

    /**
     * 校验键非 null。
     *
     * @param key 键
     * @throws IllegalArgumentException key 为 null 时抛出
     */
    private void checkKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key 不能为 null");
        }
    }

    /**
     * 存储条目：值与到期时刻（毫秒）。
     *
     * @author 星河一叶 (RainxButterfly)
     * @since 2026-08-30
     */
    private static final class Entry {

        /** 存储的字符串值 */
        private final String value;

        /** 到期时刻（System.currentTimeMillis() 基准，单位：毫秒） */
        private final long expireAtMillis;

        /**
         * 构造存储条目。
         *
         * @param value          存储的字符串值
         * @param expireAtMillis 到期时刻（毫秒）
         */
        private Entry(String value, long expireAtMillis) {
            this.value = value;
            this.expireAtMillis = expireAtMillis;
        }

        /**
         * 判断条目是否已过期。
         *
         * @return true 表示当前时间已到达或超过到期时刻
         */
        private boolean isExpired() {
            return System.currentTimeMillis() >= expireAtMillis;
        }
    }
}
