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

/**
 * xingye-kit-boot —— 星叶工具集的 Spring Boot 自动装配模块。
 *
 * <p>职责：作为全工具集唯一引入 Spring 依赖的模块，读取 {@code application.yml} /
 * {@code application.properties} 中 {@code xingye-kit} 前缀的配置，自动注册各核心模块的
 * Bean（HTTP 客户端、雪花 ID 生成器、本地缓存、通知器），实现"引入依赖即用"。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>其余七个核心模块（core/id/notify/net/io/cache/security）均为纯 Java 零框架依赖，
 *       本模块仅做薄薄的装配层，不含任何业务逻辑；</li>
 *   <li>同时兼容 Spring Boot 2.7.x 与 3.x：注册文件同时提供
 *       {@code META-INF/spring.factories}（2.7 读取）与
 *       {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 *       （2.7+/3.x 读取），配置类仅使用两个大版本均存在的注解；</li>
 *   <li>所有 Bean 均标注 {@code @ConditionalOnMissingBean}，使用方可自由覆盖默认装配；</li>
 *   <li>敏感信息（如短信 accessKey）仅承载于配置属性对象，由使用方实现读取，
 *       本模块不内置任何厂商 SDK，也不在代码中写死密钥。</li>
 * </ul>
 *
 * <p>自动注册的 Bean 一览：</p>
 * <table border="1" summary="自动装配的 Bean">
 *   <tr><th>Bean</th><th>类型</th><th>默认值来源</th></tr>
 *   <tr><td>httpTool</td><td>com.xingheyiye.xingye.kit.net.HttpTool</td><td>xingye-kit.http.*</td></tr>
 *   <tr><td>snowflake / idGenerator</td><td>com.xingheyiye.xingye.kit.id.Snowflake、core.IdGenerator</td><td>xingye-kit.id.*</td></tr>
 *   <tr><td>localCache</td><td>com.xingheyiye.xingye.kit.cache.LocalCache</td><td>xingye-kit.cache.*</td></tr>
 *   <tr><td>notifier</td><td>com.xingheyiye.xingye.kit.notify.Notifier</td><td>xingye-kit.notify.*</td></tr>
 * </table>
 */
package com.xingheyiye.xingye.kit.boot;
