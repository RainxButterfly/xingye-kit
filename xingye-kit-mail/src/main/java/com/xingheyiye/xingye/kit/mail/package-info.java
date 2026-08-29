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
 * 星河工具库 —— 邮件发送适配模块。
 *
 * <p>一句话职责：以 JavaMail（SMTP 协议）实现
 * {@code notify} 模块的 {@link com.xingheyiye.xingye.kit.notify.MailClient} 契约，
 * 让"邮件"从本地联调桩变成真实发送（支持纯文本 / HTML 正文与附件）。</p>
 *
 * <p>本模块是对 {@code notify} 契约的官方厂商实现（与
 * {@code com.xingheyiye.xingye.kit.notify.impl.LoggingMailClient} 的本地联调桩互补）：
 * 使用方按需引入本模块即可获得真实邮件能力，不需要时保持 {@code notify} 零第三方依赖。</p>
 *
 * <p>适用场景：账单与报表投递、系统告警、注册激活信等需要真实投递的生产环境。</p>
 *
 * <p>前置条件：具备可用的 SMTP 服务器（自建或企业邮箱 / Gmail / QQ 邮箱等），
 * 并持有发件账号与密码（或授权码）。</p>
 *
 * <p>线程安全性：内部仅持有不可变配置与无状态的 {@code Session}，线程安全，
 * 可跨线程共享单例。</p>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
package com.xingheyiye.xingye.kit.mail;
