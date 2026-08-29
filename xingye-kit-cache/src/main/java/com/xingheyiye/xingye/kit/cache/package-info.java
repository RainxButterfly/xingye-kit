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

/**
 * 星河工具库 —— 本地缓存与幂等控制模块。
 *
 * <p>一句话职责：提供零第三方依赖的进程内缓存、Redis 命令最小抽象以及接口幂等组件。</p>
 *
 * <p>适用场景：</p>
 * <ul>
 *     <li>{@link com.xingheyiye.xingye.kit.cache.LocalCache}：单实例热点数据缓存、字典/配置缓存、
 *         通过 loader 回源防击穿；</li>
 *     <li>{@link com.xingheyiye.xingye.kit.cache.RedisClient} /
 *         {@link com.xingheyiye.xingye.kit.cache.RedisHelper}：由使用方以 Jedis/Lettuce/Redisson 适配
 *         {@code RedisClient} 后，获得分布式锁、窗口计数等轻量封装；</li>
 *     <li>{@link com.xingheyiye.xingye.kit.cache.Idempotent} /
 *         {@link com.xingheyiye.xingye.kit.cache.IdempotentStore}：支付、下单等写接口的防重复提交。</li>
 * </ul>
 *
 * <p>线程安全性：本模块公开的进程内实现（LocalCache、MemoryIdempotentStore）均为线程安全，
 * 可在多线程间共享实例；Redis 相关组件的线程安全性取决于底层 RedisClient 适配实现。</p>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-28
 */
package com.xingheyiye.xingye.kit.cache;
