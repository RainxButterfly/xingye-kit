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

import com.xingheyiye.xingye.kit.cache.LocalCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本地缓存（{@link LocalCache}）自动装配。
 *
 * <p>适用场景：Spring Boot 项目需要进程内 TTL + 近似 LRU 缓存，按
 * {@code xingye-kit.cache.*} 配置容量、过期时长与后台清理周期。</p>
 *
 * <p>线程安全性：装配的 {@code LocalCache} 内部基于并发容器与守护清理线程，线程安全；
 * 容器关闭时通过 {@code shutdown()} 方法终止后台清理线程。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * @Autowired
 * private LocalCache<Object, Object> localCache;
 *
 * Object user = localCache.get("user:1", key -> loadUser(key));
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 * @see XingyeKitAutoConfiguration
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(LocalCache.class)
public class CacheAutoConfiguration {

    /**
     * 注册默认的 {@link LocalCache} 单例 Bean，容器关闭时自动调用 {@code shutdown()}
     * 停止后台清理线程。
     *
     * <p>使用方已自定义同类型 Bean 时不注册（{@code @ConditionalOnMissingBean}）。</p>
     *
     * @param properties 星叶工具集统一配置（取 {@code xingye-kit.cache.*} 节点）
     * @return 按配置构建的本地缓存，永不为 {@code null}
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public LocalCache<Object, Object> localCache(XingyeKitProperties properties) {
        XingyeKitProperties.Cache cache = properties.getCache();
        return LocalCache.newBuilder()
                .maximumSize(cache.getMaximumSize())
                .expireAfterWriteMillis(cache.getExpireAfterWriteMillis())
                .cleanupIntervalSeconds(cache.getCleanupIntervalSeconds())
                .build();
    }
}
