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

import com.xingheyiye.xingye.kit.notify.Notifier;
import com.xingheyiye.xingye.kit.notify.impl.LoggingNotifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通知客户端自动装配。
 *
 * <p>适用场景：Spring Boot 项目需要统一的通知出口。默认装配零副作用的
 * {@link LoggingNotifier}（仅打印控制台，用于本地联调）；生产环境由使用方实现
 * {@code Notifier}/{@code SmsClient}/{@code MailClient} 等接口对接真实厂商后，
 * 本模块的默认 Bean 自动让位（{@code @ConditionalOnMissingBean}）。</p>
 *
 * <p>线程安全性：装配的 {@code LoggingNotifier} 无状态，线程安全。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * @Autowired
 * private Notifier notifier;
 *
 * notifier.send("订单已发货", "webhook");
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 * @see XingyeKitAutoConfiguration
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Notifier.class)
public class NotificationAutoConfiguration {

    /**
     * 注册默认的 {@link LoggingNotifier} 单例 Bean。
     *
     * <p>使用方已自定义任意 {@code Notifier} 实现时不注册（保证业务通知实现优先）。</p>
     *
     * @return 控制台打印型通知器，永不为 {@code null}
     */
    @Bean(destroyMethod = "")
    @ConditionalOnMissingBean(Notifier.class)
    public Notifier notifier() {
        return new LoggingNotifier();
    }
}
