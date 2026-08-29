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
package com.xingheyiye.xingye.kit.notify.impl;

import com.xingheyiye.xingye.kit.notify.CodeStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link CodeStore} 的单机进程内实现：基于 ConcurrentHashMap，读取时惰性判断过期。
 *
 * <p>一句话职责：为单实例应用提供零依赖的验证码存取，过期条目在被读取时清除。</p>
 *
 * <p>适用场景：本地联调、单实例部署的小规模验证码场景。限制：</p>
 * <ul>
 *     <li>数据仅存于本进程内存，应用重启即全部丢失；</li>
 *     <li>多实例部署时各节点数据不互通（A 节点生成的验证码在 B 节点校验不到），
 *         分布式场景应自行实现基于 Redis 等外部存储的 CodeStore 版本；</li>
 *     <li>不启动后台清理线程，长期不被读取的过期键会驻留内存，直到被同键覆盖或删除
 *         （验证码键均带 TTL 且量级有限，通常可接受）。</li>
 * </ul>
 *
 * <p>线程安全性：基于 ConcurrentHashMap，线程安全，可在多线程间共享单例。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * VerificationCode service = new VerificationCode(new InMemoryCodeStore());
 * String code = service.generate("13800138000");
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-23
 */
public class InMemoryCodeStore implements CodeStore {

    /** 键值存储：键为业务键，值为带过期时间戳的条目 */
    private final Map<String, Entry> store = new ConcurrentHashMap<String, Entry>();

    /**
     * 写入键值并设定存活时长；同键重复写入时覆盖旧值并重置 TTL。
     *
     * @param key 键，不能为 null 或空白串
     * @param value 值，不能为 null
     * @param ttlMillis 存活时长（毫秒），必须大于 0
     * @throws IllegalArgumentException key/value 为 null、key 为空白串或 ttlMillis 小于等于 0 时抛出
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
        store.put(key, new Entry(value, System.currentTimeMillis() + ttlMillis));
    }

    /**
     * 读取键对应的值；过期条目在本次读取时被惰性清除。
     *
     * @param key 键，不能为 null
     * @return 键对应的当前有效值；键不存在或已过期时返回 null
     * @throws IllegalArgumentException key 为 null 时抛出
     */
    @Override
    public String get(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key 不能为 null");
        }
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() >= entry.expireAtMillis) {
            store.remove(key);
            return null;
        }
        return entry.value;
    }

    /**
     * 删除键（幂等：键不存在时静默返回）。
     *
     * @param key 键，不能为 null
     * @throws IllegalArgumentException key 为 null 时抛出
     */
    @Override
    public void remove(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key 不能为 null");
        }
        store.remove(key);
    }

    /**
     * 存储条目：值与到期时间戳（毫秒）。
     *
     * @author 星河一叶 (RainxButterfly)
     * @since 2026-08-23
     */
    private static final class Entry {

        /** 存储的值 */
        private final String value;

        /** 到期时间戳（毫秒），当前时间大于等于该值即过期 */
        private final long expireAtMillis;

        /**
         * 构造存储条目。
         *
         * @param value 存储的值
         * @param expireAtMillis 到期时间戳（毫秒）
         */
        private Entry(String value, long expireAtMillis) {
            this.value = value;
            this.expireAtMillis = expireAtMillis;
        }
    }
}
