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
 * @since 2026-08-20
 */

/**
 * xingye-kit ID 生成模块，提供分布式唯一 ID 与业务编号的多种生成策略。
 *
 * <p>适用场景：分布式数据库主键、订单号/流水号、请求追踪号、短链随机码、验证码等需要唯一标识的场景。
 *
 * <p>主要工具清单：
 * <ul>
 *     <li>{@link com.xingheyiye.xingye.kit.id.Snowflake}：雪花算法 ID 生成器，趋势有序，适合做数据库主键；</li>
 *     <li>{@link com.xingheyiye.xingye.kit.id.UuidUtils}：标准 UUID、无连字符 UUID、快速随机串与有序 UUID；</li>
 *     <li>{@link com.xingheyiye.xingye.kit.id.ShortCode}：Base62 编解码与安全随机短码；</li>
 *     <li>{@link com.xingheyiye.xingye.kit.id.RequestNo}：请求号生成器（时间戳 + 节点号 + 回绕序列）；</li>
 *     <li>{@link com.xingheyiye.xingye.kit.id.impl.UuidIdGenerator}：{@code IdGenerator} 的 UUID 实现；</li>
 *     <li>{@link com.xingheyiye.xingye.kit.id.impl.ShortCodeIdGenerator}：{@code IdGenerator} 的随机短码实现。</li>
 * </ul>
 *
 * <p>本模块零第三方依赖，仅依赖 JDK 8 标准库与 xingye-kit-core 模块。
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-20
 */
package com.xingheyiye.xingye.kit.id;
