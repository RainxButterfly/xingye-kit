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

import com.xingheyiye.xingye.kit.net.HttpTool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HTTP 客户端（{@link HttpTool}）自动装配。
 *
 * <p>适用场景：Spring Boot 项目需要统一的轻量 HTTP 客户端 Bean，按
 * {@code xingye-kit.http.*} 配置注入连接/读取超时与重试次数。</p>
 *
 * <p>线程安全性：装配的 {@code HttpTool} 实例为不可变对象，线程安全，可作为单例共享。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // application.yml: xingye-kit.http.connect-timeout: 3000
 * @Autowired
 * private HttpTool httpTool;
 *
 * HttpResponse response = httpTool.execute(
 *         HttpRequest.get("https://api.example.com/users/1").build());
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 * @see XingyeKitAutoConfiguration
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(HttpTool.class)
public class HttpAutoConfiguration {

    /**
     * 注册默认的 {@link HttpTool} 单例 Bean。
     *
     * <p>使用方已自定义同类型 Bean 时不注册（{@code @ConditionalOnMissingBean}）。</p>
     *
     * @param properties 星叶工具集统一配置（取 {@code xingye-kit.http.*} 节点）
     * @return 按配置构建的 HTTP 客户端，永不为 {@code null}
     */
    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean
    public HttpTool httpTool(XingyeKitProperties properties) {
        XingyeKitProperties.Http http = properties.getHttp();
        return new HttpTool(http.getConnectTimeout(), http.getReadTimeout(), http.getMaxRetry());
    }
}
