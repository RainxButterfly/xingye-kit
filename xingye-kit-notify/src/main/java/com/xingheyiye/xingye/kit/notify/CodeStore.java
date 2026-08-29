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
 * @since 2026-08-23
 */
package com.xingheyiye.xingye.kit.notify;

/**
 * 验证码存储接口：为 {@link VerificationCode} 提供带 TTL 的字符串键值存取。
 *
 * <p>一句话职责：把"验证码放在哪里、如何过期"从验证码服务中剥离为可插拔契约。</p>
 *
 * <p>适用场景：注册/登录验证码、二次确认码的短期存储。
 * 单机进程可直接使用
 * {@link com.xingheyiye.xingye.kit.notify.impl.InMemoryCodeStore}；
 * 多实例/分布式部署必须由使用方实现外部存储版本（如基于 Redis 的 SET key value PX ttl 与 GET/DEL），
 * 以保证验证码跨节点可见且过期语义原子可靠。</p>
 *
 * <p>线程安全性：接口不约定状态，线程安全性由实现类声明。</p>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-23
 */
public interface CodeStore {

    /**
     * 写入键值并设定存活时长；同键重复写入时覆盖旧值并重置 TTL。
     *
     * @param key 键，不能为 null 或空白串（如 "vcode:13800138000" 这类带业务前缀的目标键）
     * @param value 值，不能为 null（如验证码本身）
     * @param ttlMillis 存活时长（毫秒），必须大于 0；到期后 {@link #get(String)} 返回 null
     * @throws IllegalArgumentException key/value 为 null、key 为空白串或 ttlMillis 小于等于 0
     *                                  等参数非法时由实现方抛出
     * @throws RuntimeException 存储不可用（如 Redis 连接失败）等故障时由实现方抛出
     */
    void put(String key, String value, long ttlMillis);

    /**
     * 读取键对应的值。
     *
     * @param key 键，不能为 null
     * @return 键对应的当前有效值；键不存在或已过期时返回 null
     * @throws IllegalArgumentException key 为 null 时由实现方抛出
     * @throws RuntimeException 存储不可用等故障时由实现方抛出
     */
    String get(String key);

    /**
     * 删除键（幂等操作：键不存在时静默返回，不抛异常）。
     *
     * @param key 键，不能为 null
     * @throws IllegalArgumentException key 为 null 时由实现方抛出
     * @throws RuntimeException 存储不可用等故障时由实现方抛出
     */
    void remove(String key);
}
