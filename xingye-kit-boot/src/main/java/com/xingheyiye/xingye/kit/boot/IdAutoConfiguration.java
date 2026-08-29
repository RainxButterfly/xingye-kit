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

import com.xingheyiye.xingye.kit.core.IdGenerator;
import com.xingheyiye.xingye.kit.id.Snowflake;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ID 生成器（{@link Snowflake} 雪花算法）自动装配。
 *
 * <p>适用场景：Spring Boot 项目需要全局唯一 ID，按 {@code xingye-kit.id.*}
 * 配置机器位与数据中心位，多实例部署时必须为每个实例分配不同的 workerId。</p>
 *
 * <p>线程安全性：装配的 {@code Snowflake} 内部以 synchronized 发号，线程安全，
 * 可作为单例共享。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // application.yml: xingye-kit.id.worker-id: 2
 * @Autowired
 * private IdGenerator idGenerator;
 *
 * String id = idGenerator.nextId();
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 * @see XingyeKitAutoConfiguration
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Snowflake.class)
public class IdAutoConfiguration {

    /**
     * 注册默认的 {@link Snowflake} 单例 Bean。
     *
     * <p>使用方已自定义同类型 Bean 时不注册（{@code @ConditionalOnMissingBean}）。</p>
     *
     * @param properties 星叶工具集统一配置（取 {@code xingye-kit.id.*} 节点）
     * @return 按配置构建的雪花 ID 生成器，永不为 {@code null}
     */
    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean
    public Snowflake snowflake(XingyeKitProperties properties) {
        XingyeKitProperties.Id id = properties.getId();
        return new Snowflake(id.getWorkerId(), id.getDatacenterId());
    }

    /**
     * 以雪花生成器兜底注册 {@link IdGenerator} 接口 Bean，
     * 便于业务代码面向接口注入。
     *
     * <p>使用方已自定义任意 {@code IdGenerator} 实现时不注册。</p>
     *
     * @param snowflake 上方注册的雪花生成器（容器保证非空）
     * @return 委托 {@code snowflake} 的 ID 生成器，永不为 {@code null}
     */
    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean(IdGenerator.class)
    public IdGenerator idGenerator(Snowflake snowflake) {
        return snowflake;
    }
}
