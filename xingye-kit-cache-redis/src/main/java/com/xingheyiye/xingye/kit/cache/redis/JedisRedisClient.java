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
package com.xingheyiye.xingye.kit.cache.redis;

import com.xingheyiye.xingye.kit.cache.RedisClient;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.SetParams;

/**
 * 基于 Jedis 连接池的 {@link RedisClient} 适配实现：提供真实 Redis 的分布式锁/计数/验证码存储能力。
 *
 * <p>本类是 {@link RedisClient} 接口的内置实现（{@link RedisClient} 的可替换选择之一），
 * 与内存版 {@code com.xingheyiye.xingye.kit.cache.impl.InMemoryRedisClient} 形成
 * “本地联调内存版 + 生产真实 Redis 版”的成对选择：本地联调/单测用内存版，
 * 多实例部署时切换为本类，业务代码面向 {@link RedisClient} 接口不变。</p>
 *
 * <p>配套使用：本类可独立作为 {@link RedisClient} 使用，也可与
 * {@code RedisHelper}、{@code RedisIdempotentStore}、{@code RedisCodeStore}
 * 组合实现跨实例的分布式锁、幂等去重与验证码存储。</p>
 *
 * <p>线程安全性：{@link JedisPool} 线程安全；每次命令从池中借用独立连接、
 * 用后归还，可被多线程安全并发调用。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 生产环境：真实 Redis
 * RedisClient client = new JedisRedisClient("127.0.0.1", 6379);
 * RedisHelper redis = new RedisHelper(client, "app1");
 *
 * // 本地联调：RedisClient client = new InMemoryRedisClient();
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public class JedisRedisClient implements RedisClient {

    /** 底层 Jedis 连接池。 */
    private final JedisPool pool;

    /**
     * 以主机/端口构造（无认证）。
     *
     * @param host Redis 主机名或 IP，不可为 null 或空白串
     * @param port Redis 端口（通常 6379），取值范围 [1, 65535]
     * @throws IllegalArgumentException host 非法或 port 越界
     */
    public JedisRedisClient(String host, int port) {
        this(host, port, null);
    }

    /**
     * 以主机/端口/密码构造（Redis 6+ 默认开启 ACL 认证）。
     *
     * @param host Redis 主机名或 IP，不可为 null 或空白串
     * @param port Redis 端口，取值范围 [1, 65535]
     * @param password 认证密码，可为 null 或空串（表示无认证）
     * @throws IllegalArgumentException host 非法或 port 越界
     */
    public JedisRedisClient(String host, int port, String password) {
        if (host == null || host.trim().length() == 0) {
            throw new IllegalArgumentException("host 不能为 null 或空白串");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port 必须在 1-65535 之间: " + port);
        }
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(16);
        config.setMaxIdle(8);
        config.setMinIdle(1);
        this.pool = password == null || password.length() == 0
                ? new JedisPool(config, host, port, 2000)
                : new JedisPool(config, host, port, 2000, password);
    }

    /**
     * 以已构建的 Jedis 连接池构造（完全由调用方控制连接池参数）。
     *
     * @param pool Jedis 连接池，不可为 null
     * @throws IllegalArgumentException pool 为 null
     */
    public JedisRedisClient(JedisPool pool) {
        if (pool == null) {
            throw new IllegalArgumentException("pool 不能为 null");
        }
        this.pool = pool;
    }

    /**
     * 读取字符串值。
     *
     * @param key 键，不可为 null
     * @return 键对应的值；键不存在返回 null
     */
    @Override
    public String get(String key) {
        checkKey(key);
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(key);
        }
    }

    /**
     * 无条件写入字符串值（覆盖旧值）。
     *
     * @param key   键，不可为 null
     * @param value 值，不可为 null
     * @return 命令回复，成功为 "OK"
     */
    @Override
    public String set(String key, String value) {
        checkKey(key);
        if (value == null) {
            throw new IllegalArgumentException("value 不能为 null");
        }
        try (Jedis jedis = pool.getResource()) {
            return jedis.set(key, value);
        }
    }

    /**
     * 仅当键不存在时写入并同时设置过期时间（原子 SET key value NX PX millis）。
     *
     * @param key          键，不可为 null
     * @param value        值，不可为 null
     * @param expireMillis 过期时长（毫秒），必须大于 0
     * @return true 表示写入成功（此前键不存在）；false 表示键已存在未写入
     */
    @Override
    public boolean setNx(String key, String value, long expireMillis) {
        checkKey(key);
        if (value == null) {
            throw new IllegalArgumentException("value 不能为 null");
        }
        if (expireMillis <= 0) {
            throw new IllegalArgumentException("expireMillis 必须大于 0: " + expireMillis);
        }
        try (Jedis jedis = pool.getResource()) {
            String reply = jedis.set(key, value, SetParams.setParams().nx().px(expireMillis));
            return "OK".equals(reply);
        }
    }

    /**
     * 将键中存储的数字增 1（键不存在时从 0 自增返回 1）。
     *
     * @param key 键，不可为 null
     * @return 自增后的当前值
     */
    @Override
    public long incr(String key) {
        checkKey(key);
        try (Jedis jedis = pool.getResource()) {
            return jedis.incr(key);
        }
    }

    /**
     * 为已存在的键设置过期时间。
     *
     * @param key    键，不可为 null
     * @param millis 过期时长（毫秒），必须大于 0
     * @return true 表示设置成功（键存在）；false 表示键不存在
     */
    @Override
    public boolean expire(String key, long millis) {
        checkKey(key);
        if (millis <= 0) {
            throw new IllegalArgumentException("millis 必须大于 0: " + millis);
        }
        try (Jedis jedis = pool.getResource()) {
            return jedis.expire(key, millis) == 1L;
        }
    }

    /**
     * 删除键。
     *
     * @param key 键，不可为 null
     * @return true 表示删除成功（键原本存在）；false 表示键不存在
     */
    @Override
    public boolean del(String key) {
        checkKey(key);
        try (Jedis jedis = pool.getResource()) {
            return jedis.del(key) == 1L;
        }
    }

    /**
     * 关闭连接池并释放全部连接（进程退出前应调用；关闭后实例不可再使用）。
     */
    public void close() {
        pool.close();
    }

    /**
     * 校验键参数。
     */
    private static void checkKey(String key) {
        if (key == null || key.length() == 0) {
            throw new IllegalArgumentException("key 不能为 null 或空串");
        }
    }
}
