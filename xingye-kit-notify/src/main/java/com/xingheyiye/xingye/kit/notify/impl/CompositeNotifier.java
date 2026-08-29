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

import com.xingheyiye.xingye.kit.notify.Notifier;

/**
 * {@link Notifier} 的组合实现：把一次发送广播给多个子通知器（日志 + 短信 + Webhook 等）。
 *
 * <p>一句话职责：串行调用全部子通知器，单个失败或异常不影响其余子通知器继续执行。</p>
 *
 * <p>适用场景：同一事件需要同时落日志、发短信、推 Webhook 的告警扇出。</p>
 *
 * <p>线程安全性：仅在构造期固定子通知器数组（发布后只读），线程安全，可共享单例；
 * 子通知器自身的线程安全性由其实现保证。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * Notifier notifier = new CompositeNotifier(
 *         new LoggingNotifier(),
 *         smsNotifier,       // 使用方实现
 *         webhookNotifier);  // 使用方实现
 * notifier.send("服务 CPU 超过 90%", "alert");
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-23
 */
public class CompositeNotifier implements Notifier {

    /** 待广播的子通知器数组（构造后只读） */
    private final Notifier[] notifiers;

    /**
     * 构造组合通知器。
     *
     * @param notifiers 子通知器，至少一个；不能为 null 数组，且不能包含 null 元素
     * @throws IllegalArgumentException notifiers 为 null、为空数组或包含 null 元素时抛出
     */
    public CompositeNotifier(Notifier... notifiers) {
        if (notifiers == null) {
            throw new IllegalArgumentException("notifiers 不能为 null");
        }
        if (notifiers.length == 0) {
            throw new IllegalArgumentException("notifiers 至少需要包含一个通知器");
        }
        for (Notifier notifier : notifiers) {
            if (notifier == null) {
                throw new IllegalArgumentException("notifiers 不能包含 null 元素");
            }
        }
        this.notifiers = notifiers.clone();
    }

    /**
     * 依次向全部子通知器发送消息。
     *
     * <p>单个子通知器返回 false 记为失败；抛出异常时捕获并打印到 System.err，不中断
     * 后续子通知器；全部子通知器都成功时才返回 true。</p>
     *
     * @param message 通知内容，透传给各子通知器；不能为 null（由子通知器校验）
     * @param channel 渠道标识，透传给各子通知器；不能为 null（由子通知器校验）
     * @return true 表示全部子通知器发送成功；false 表示至少一个失败或抛出异常
     */
    @Override
    public boolean send(String message, String channel) {
        boolean allSuccess = true;
        for (Notifier notifier : notifiers) {
            try {
                if (!notifier.send(message, channel)) {
                    allSuccess = false;
                }
            } catch (Exception e) {
                allSuccess = false;
                System.err.println("[CompositeNotifier] 子通知器发送异常: channel=" + channel
                        + ", error=" + e);
            }
        }
        return allSuccess;
    }
}
