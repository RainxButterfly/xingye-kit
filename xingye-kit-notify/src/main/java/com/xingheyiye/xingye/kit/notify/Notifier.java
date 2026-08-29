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
 * @since 2026-08-22
 */
package com.xingheyiye.xingye.kit.notify;

/**
 * 通用通知发送接口：以"消息 + 渠道标识"的最小契约向外部发送通知。
 *
 * <p>一句话职责：屏蔽短信、邮件、Webhook 等渠道差异，调用方只关心"发出去没有"。</p>
 *
 * <p>适用场景：告警、任务结果回执、运营触达等只需布尔结果的轻量通知。
 * 当需要回执编号（msgId）、错误码等细节时，请改用
 * {@link SmsClient}、{@link MailClient} 或 {@link WebhookClient}。</p>
 *
 * <p>线程安全性：接口不约定状态，线程安全性由实现类声明
 * （如 {@code com.xingheyiye.xingye.kit.notify.impl.LoggingNotifier}、
 * {@code com.xingheyiye.xingye.kit.notify.impl.CompositeNotifier} 均为线程安全）。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * Notifier notifier = new LoggingNotifier();   // 本地联调用实现
 * boolean sent = notifier.send("订单已发货", "email");
 * if (!sent) {
 *     log.warn("通知发送失败");
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-22
 */
public interface Notifier {

    /**
     * 向指定渠道发送一条通知消息。
     *
     * <p>约定：普通发送失败应以 false 表达而非抛异常；仅在参数非法等不可继续的场景抛出
     * 运行时异常。</p>
     *
     * @param message 通知内容，不能为 null；是否允许空串由实现方决定
     * @param channel 渠道标识，不能为 null；如 "sms"、"email"、"webhook"、"dingtalk" 等，
     *                具体取值集合由实现方约定
     * @return true 表示发送成功；false 表示发送失败（不抛异常的失败语义）
     * @throws IllegalArgumentException 实现方校验参数非法（如 message 或 channel 为 null）时抛出
     * @throws RuntimeException 实现方遇到不可恢复错误时抛出
     */
    boolean send(String message, String channel);
}
