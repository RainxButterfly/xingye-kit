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
 * 极简 Redis 字符串命令抽象接口：仅覆盖缓存、分布式锁与计数场景所需的最小命令集。
 *
 * <p>一句话职责：屏蔽具体 Redis 客户端（Jedis/Lettuce/Redisson）差异，
 * 由使用方自行适配实现，本模块不引入任何 Redis 依赖。</p>
 *
 * <p>适用场景：配合 {@link RedisHelper} 构建分布式锁、窗口限流计数等能力。</p>
 *
 * <p>线程安全性：接口本身无状态；实现的线程安全性由适配方保证
 * （连接池或多路复用客户端如 Lettuce 天然线程安全，单连接 Jedis 需自行加锁或每次获取连接）。</p>
 *
 * <p>Jedis 适配示例：</p>
 * <pre>{@code
 * public final class JedisRedisClient implements RedisClient {
 *
 *     private final JedisPool pool = new JedisPool("127.0.0.1", 6379);
 *
 *     @Override
 *     public String get(String key) {
 *         try (Jedis jedis = pool.getResource()) {
 *             return jedis.get(key);
 *         }
 *     }
 *
 *     @Override
 *     public boolean setNx(String key, String value, long expireMillis) {
 *         try (Jedis jedis = pool.getResource()) {
 *             String reply = jedis.set(key, value, SetParams.setParams().nx().px(expireMillis));
 *             return "OK".equals(reply);
 *         }
 *     }
 *
 *     // set/incr/expire/del 同理，直接委托 Jedis 对应命令即可
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-28
 */
public interface RedisClient {

    /**
     * 读取字符串值。
     *
     * @param key 键，由实现方约束非 null
     * @return 键对应的值；键不存在返回 null
     */
    String get(String key);

    /**
     * 无条件写入字符串值（覆盖旧值）。
     *
     * @param key   键，由实现方约束非 null
     * @param value 值，由实现方约束非 null
     * @return 命令回复（Jedis 语义下成功为 "OK"）；实现也可返回 null 表示执行失败
     */
    String set(String key, String value);

    /**
     * 仅当键不存在时写入，并同时设置过期时间（等价于 SET key value NX PX millis，必须原子完成）。
     *
     * @param key          键，由实现方约束非 null
     * @param value        值，由实现方约束非 null
     * @param expireMillis 过期时长（毫秒），必须大于 0
     * @return true 表示写入成功（此前键不存在）；false 表示键已存在未写入
     */
    boolean setNx(String key, String value, long expireMillis);

    /**
     * 将键中存储的数字增 1，键不存在时从 0 开始自增（返回 1）。
     *
     * @param key 键，由实现方约束非 null
     * @return 自增后的当前值，不会为 null
     */
    long incr(String key);

    /**
     * 为已存在的键设置过期时间。
     *
     * @param key    键，由实现方约束非 null
     * @param millis 过期时长（毫秒），必须大于 0
     * @return true 表示设置成功（键存在）；false 表示键不存在
     */
    boolean expire(String key, long millis);

    /**
     * 删除键。
     *
     * @param key 键，由实现方约束非 null
     * @return true 表示删除成功（键原本存在）；false 表示键不存在
     */
    boolean del(String key);
}
