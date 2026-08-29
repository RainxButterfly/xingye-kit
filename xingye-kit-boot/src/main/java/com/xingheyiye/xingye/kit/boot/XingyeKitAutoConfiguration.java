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

package com.xingheyiye.xingye.kit.boot;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * 星叶工具集自动配置入口，是 boot 模块唯一对外注册的自动配置类。
 *
 * <p>适用场景：Spring Boot 项目引入 {@code xingye-kit-boot} 依赖后，
 * Spring Boot 通过 {@code META-INF/spring/...AutoConfiguration.imports}（Boot 2.7+/3.x）
 * 或 {@code META-INF/spring.factories}（Boot 2.7）发现并加载本类，
 * 进而完成全部子项自动装配。</p>
 *
 * <p>核心设计：</p>
 * <ul>
 *   <li>本类自身不直接注册 Bean，仅启用配置属性绑定并按固定顺序聚合四个子配置：
 *       {@link HttpAutoConfiguration} → {@link IdAutoConfiguration} →
 *       {@link CacheAutoConfiguration} → {@link NotificationAutoConfiguration}；</li>
 *   <li>每个子配置均带 {@code @ConditionalOnClass} 类存在性守卫，单独排除某个核心模块
 *       依赖不会导致启动失败；</li>
 *   <li>同时兼容 Spring Boot 2.7.x 与 3.x：仅使用 {@code @AutoConfiguration}、
 *       {@code @ConditionalOnClass}、{@code @ConditionalOnMissingBean} 等
 *       两个大版本均存在的注解，注册文件双份提供。</li>
 * </ul>
 *
 * <p>线程安全性：配置类由 Spring 容器在启动期实例化一次，线程安全。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 1. 引入依赖后无需任何注解，容器内直接注入使用
 * @Autowired
 * private IdGenerator idGenerator;
 *
 * // 2. 需要覆盖默认装配时，自行声明同类型 Bean 即可
 * @Bean
 * public Notifier notifier() {
 *     return new CompositeNotifier(new LoggingNotifier(), mySmsNotifier());
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 * @see EnableAutoConfiguration
 * @see XingyeKitProperties
 */
@AutoConfiguration
@EnableConfigurationProperties(XingyeKitProperties.class)
@Import({HttpAutoConfiguration.class, IdAutoConfiguration.class,
        CacheAutoConfiguration.class, NotificationAutoConfiguration.class})
public class XingyeKitAutoConfiguration {

    // 无直接 Bean 定义：装配逻辑全部下放到聚合的四个子配置类，
    // 便于按模块隔离 @ConditionalOnClass 守卫与单元测试。
}
