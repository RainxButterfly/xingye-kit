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
 * 基于 {@link RedisClient} 的 Redis 轻量封装：统一 key 前缀拼接、分布式锁与窗口计数。
 *
 * <p>一句话职责：以极小的命令集封装出"带前缀的 key、可重入持有者校验的锁、固定窗口计数"三个常用能力。</p>
 *
 * <p>适用场景：多实例部署下的互斥控制（如定时任务防并发执行）、按业务键的简单限流计数。</p>
 *
 * <p>线程安全性：本类仅持有不可变字段，线程安全性取决于传入的 {@link RedisClient} 实现
 * （推荐连接池或多路复用客户端）。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * RedisHelper helper = new RedisHelper(client, "order");
 * String holder = UUID.randomUUID().toString();
 * if (helper.tryLock("pay-123", holder, 30000)) {
 *     try {
 *         // 处理业务
 *     } finally {
 *         helper.unlock("pay-123", holder);
 *     }
 * }
 * long count = helper.nextCount("user-1", 60000); // 60 秒窗口内第几次访问
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-28
 */
public class RedisHelper {

    /** 分布式锁 key 的命名空间前缀 */
    private static final String LOCK_NAMESPACE = "lock";

    /** 窗口计数 key 的命名空间前缀 */
    private static final String COUNT_NAMESPACE = "count";

    /** key 片段连接分隔符 */
    private static final char KEY_SEPARATOR = ':';

    /** 底层 Redis 命令抽象 */
    private final RedisClient client;

    /** 统一 key 前缀，可为空串（表示无前缀） */
    private final String keyPrefix;

    /**
     * 构造 RedisHelper。
     *
     * @param client    Redis 命令抽象实现，不能为 null
     * @param keyPrefix 统一 key 前缀，可为 null 或空串（表示无前缀）；建议传入应用/模块名隔离命名空间
     * @throws IllegalArgumentException client 为 null 时抛出
     */
    public RedisHelper(RedisClient client, String keyPrefix) {
        if (client == null) {
            throw new IllegalArgumentException("client 不能为 null");
        }
        this.client = client;
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
    }

    /**
     * 拼接带前缀的完整 key：prefix:p1:p2（prefix 为空串时输出 p1:p2，不产生前导冒号）。
     *
     * @param parts key 片段，可变参数；可为空（此时仅返回前缀）
     * @return 拼接后的完整 key，不会为 null
     * @throws IllegalArgumentException 任一片段为 null 或空串时抛出
     */
    public String key(String... parts) {
        if (parts == null || parts.length == 0) {
            return keyPrefix;
        }
        StringBuilder builder = new StringBuilder(keyPrefix);
        for (String part : parts) {
            if (part == null || part.length() == 0) {
                throw new IllegalArgumentException("key 片段不能为 null 或空串");
            }
            if (builder.length() > 0) {
                builder.append(KEY_SEPARATOR);
            }
            builder.append(part);
        }
        return builder.toString();
    }

    /**
     * 尝试获取分布式锁（非阻塞、不可重入）。
     *
     * <p>锁值为持有者标识（如 UUID），用于 {@link #unlock(String, String)} 时校验只释放自己持有的锁。</p>
     *
     * @param name       锁名称（不含前缀），不能为 null 或空串
     * @param holder     持有者标识，不能为 null 或空串；建议每次加锁生成唯一值
     * @param leaseMillis 锁租约时长（毫秒），必须大于 0；到期后锁自动失效
     * @return true 表示获取成功；false 表示锁已被他人持有
     * @throws IllegalArgumentException name/holder 为 null 或空串、leaseMillis 小于等于 0 时抛出
     */
    public boolean tryLock(String name, String holder, long leaseMillis) {
        checkText("name", name);
        checkText("holder", holder);
        if (leaseMillis <= 0) {
            throw new IllegalArgumentException("leaseMillis 必须大于 0，单位毫秒");
        }
        return client.setNx(key(LOCK_NAMESPACE, name), holder, leaseMillis);
    }

    /**
     * 释放分布式锁：仅当锁的当前值与 holder 一致时才删除。
     *
     * <p>实现说明：get 比对与 del 两步操作非原子，理论上存在"比对通过后锁恰好过期、他人抢到新锁却被误删"
     * 的竞态窗口；对安全性要求高的生产环境建议改为 Lua 脚本在 Redis 侧完成 CAS（比对 holder 后原子删除）。</p>
     *
     * @param name   锁名称（与加锁时一致），不能为 null 或空串
     * @param holder 持有者标识（与加锁时一致），不能为 null 或空串
     * @return true 表示释放成功；false 表示锁不存在或持有者不匹配（未释放）
     * @throws IllegalArgumentException name/holder 为 null 或空串时抛出
     */
    public boolean unlock(String name, String holder) {
        checkText("name", name);
        checkText("holder", holder);
        String lockKey = key(LOCK_NAMESPACE, name);
        String current = client.get(lockKey);
        if (current != null && current.equals(holder)) {
            // 注意：此处 get 与 del 两步非原子，存在误删他人新锁的竞态窗口，
            // 生产环境建议用 Lua 脚本在 Redis 侧原子完成"比对 holder 再删除"的 CAS
            return client.del(lockKey);
        }
        return false;
    }

    /**
     * 固定窗口计数自增：返回当前窗口内第几次计数。
     *
     * <p>实现说明：首次计数（返回 1）时为计数器设置窗口过期时间，窗口结束后计数器自动清零，
     * 下一窗口重新从 1 开始。若首计数后设置过期失败（如网络异常），计数器将永不过期，
     * 调用方需容忍该偏差或在外层做兜底清理。</p>
     *
     * @param name         计数器名称（不含前缀），不能为 null 或空串
     * @param windowMillis 窗口时长（毫秒），必须大于 0
     * @return 当前窗口内自增后的计数值（从 1 开始），不会为负
     * @throws IllegalArgumentException name 为 null 或空串、windowMillis 小于等于 0 时抛出
     */
    public long nextCount(String name, long windowMillis) {
        checkText("name", name);
        if (windowMillis <= 0) {
            throw new IllegalArgumentException("windowMillis 必须大于 0，单位毫秒");
        }
        String counterKey = key(COUNT_NAMESPACE, name);
        long current = client.incr(counterKey);
        if (current == 1L) {
            // 首次计数时设置窗口过期，窗口结束后计数器自动清零
            client.expire(counterKey, windowMillis);
        }
        return current;
    }

    /**
     * 校验文本参数非 null 且非空串。
     *
     * @param name  参数名，用于异常提示
     * @param value 参数值
     */
    private void checkText(String name, String value) {
        if (value == null || value.length() == 0) {
            throw new IllegalArgumentException(name + " 不能为 null 或空串");
        }
    }
}
