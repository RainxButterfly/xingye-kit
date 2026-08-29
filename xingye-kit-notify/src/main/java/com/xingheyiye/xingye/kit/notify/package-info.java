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

/**
 * 星河工具库 —— 通知与验证码模块。
 *
 * <p>一句话职责：定义短信、邮件、Webhook 通知与验证码的统一契约，并提供基于 JDK 的默认实现
 * （本地日志实现与进程内验证码存储）。</p>
 *
 * <p>本模块只定义契约与基于 JDK 的默认实现：真实厂商能力（阿里云短信、SMTP 邮件服务器、
 * 各厂商专属 Webhook）的接入由使用方实现对应接口完成，例如以厂商 SDK 适配
 * {@link com.xingheyiye.xingye.kit.notify.SmsClient}、以 JavaMail 适配
 * {@link com.xingheyiye.xingye.kit.notify.MailClient}、以自有 HTTP 客户端适配
 * {@link com.xingheyiye.xingye.kit.notify.WebhookClient}。</p>
 *
 * <p>适用场景：</p>
 * <ul>
 *     <li>{@link com.xingheyiye.xingye.kit.notify.Notifier}：极简通知门面，按渠道标识发送消息；</li>
 *     <li>{@link com.xingheyiye.xingye.kit.notify.SmsClient} /
 *         {@link com.xingheyiye.xingye.kit.notify.MailClient} /
 *         {@link com.xingheyiye.xingye.kit.notify.WebhookClient}：需要回执编号与错误码的
 *         短信、邮件、Webhook 发送；</li>
 *     <li>{@link com.xingheyiye.xingye.kit.notify.NotificationTemplate}：通知文案的
 *         "${key}" 占位符渲染；</li>
 *     <li>{@link com.xingheyiye.xingye.kit.notify.VerificationCode} /
 *         {@link com.xingheyiye.xingye.kit.notify.CodeStore} /
 *         {@link com.xingheyiye.xingye.kit.notify.CodeGenerator}：验证码生成（生成策略可插拔）、
 *         防刷、校验与失败次数限制。</li>
 * </ul>
 *
 * <p>线程安全性：契约接口的线程安全性由实现方声明；本模块自带的
 * {@code com.xingheyiye.xingye.kit.notify.impl} 包内默认实现均为线程安全
 * （无状态或仅持有不可变配置），可在多线程间共享单例。</p>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-22
 */
package com.xingheyiye.xingye.kit.notify;
