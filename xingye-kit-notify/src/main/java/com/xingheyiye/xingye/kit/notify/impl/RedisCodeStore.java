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
package com.xingheyiye.xingye.kit.notify.impl;

import com.xingheyiye.xingye.kit.cache.RedisClient;
import com.xingheyiye.xingye.kit.notify.CodeStore;

/**
 * 基于 {@link RedisClient} 的分布式验证码存储：借助 Redis 的 TTL 保证过期语义跨节点可靠。
 *
 * <p>一句话职责：在多实例部署下实现 {@link CodeStore}（与单机 {@link InMemoryCodeStore} 互补），
 * 验证码存于共享 Redis，A 节点生成的验证码 B 节点可校验，且过期由 Redis 强制执行。</p>
 *
 * <p>本类是 {@link CodeStore} 的内置实现之一（{@link CodeStore} 的可替换选择）。
 * 底层依赖 cache 模块的 {@link RedisClient} 抽象，不绑定具体 Redis 客户端：
 * 生产环境传入 Jedis/Lettuce/Redisson 的适配实现；本地联调可传入内置
 * {@link com.xingheyiye.xingye.kit.cache.impl.InMemoryRedisClient}。</p>
 *
 * <p>写入语义说明：由于 {@link RedisClient} 命令集最简（不含"无条件写并带 TTL"的原子命令），
 * {@link #put(String, String, long)} 采用 {@code SET + EXPIRE} 两步完成，存在极小概率的两命令
 * 间隔窗口（值已写入、TTL 未设置）。验证码场景下可接受；需要严格原子时请在实现方侧基于
 * 原生命令（SET key value PX ttl）自行扩展。</p>
 *
 * <p>线程安全性：取决于传入的 {@link RedisClient} 实现（推荐连接池或多路复用客户端）。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 生产：真实 Redis 适配（实现 RedisClient）
 * CodeStore store = new RedisCodeStore(redisClient);
 * VerificationCode service = new VerificationCode(store, 6, true, 60000L, 60000L, 5);
 *
 * // 本地联调：不部署 Redis，用内置内存版代替
 * CodeStore devStore = new RedisCodeStore(new InMemoryRedisClient());
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public class RedisCodeStore implements CodeStore {

    /** 底层 Redis 命令抽象 */
    private final RedisClient client;

    /**
     * 构造基于 Redis 的验证码存储。
     *
     * @param client Redis 命令抽象实现，不能为 null
     * @throws IllegalArgumentException client 为 null 时抛出
     */
    public RedisCodeStore(RedisClient client) {
        if (client == null) {
            throw new IllegalArgumentException("client 不能为 null");
        }
        this.client = client;
    }

    /**
     * 写入键值并设定存活时长；同键重复写入时覆盖旧值并重置 TTL。
     *
     * @param key       键，不能为 null 或空白串
     * @param value     值，不能为 null
     * @param ttlMillis 存活时长（毫秒），必须大于 0；到期后 {@link #get(String)} 返回 null
     * @throws IllegalArgumentException key/value 为 null、key 为空白串或 ttlMillis 小于等于 0 时抛出
     * @throws RuntimeException Redis 不可用等故障时抛出
     */
    @Override
    public void put(String key, String value, long ttlMillis) {
        if (key == null || key.trim().length() == 0) {
            throw new IllegalArgumentException("key 不能为 null 或空白串");
        }
        if (value == null) {
            throw new IllegalArgumentException("value 不能为 null");
        }
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("ttlMillis 必须大于 0，单位毫秒");
        }
        client.set(key, value);
        client.expire(key, ttlMillis);
    }

    /**
     * 读取键对应的值。
     *
     * @param key 键，不能为 null
     * @return 键对应的当前有效值；键不存在或已过期返回 null
     * @throws IllegalArgumentException key 为 null 时抛出
     * @throws RuntimeException Redis 不可用等故障时抛出
     */
    @Override
    public String get(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key 不能为 null");
        }
        return client.get(key);
    }

    /**
     * 删除键（幂等：键不存在时静默返回）。
     *
     * @param key 键，不能为 null
     * @throws IllegalArgumentException key 为 null 时抛出
     * @throws RuntimeException Redis 不可用等故障时抛出
     */
    @Override
    public void remove(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key 不能为 null");
        }
        client.del(key);
    }
}
