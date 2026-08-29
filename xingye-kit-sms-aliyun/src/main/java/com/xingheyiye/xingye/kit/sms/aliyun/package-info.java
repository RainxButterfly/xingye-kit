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
 * 星河工具库 —— 阿里云短信适配模块。
 *
 * <p>一句话职责：以阿里云官方 V2 SDK（dysmsapi20170525）实现
 * {@code notify} 模块的 {@link com.xingheyiye.xingye.kit.notify.SmsClient} 契约，
 * 让"短信"从本地联调桩变成真实发送。</p>
 *
 * <p>本模块是对 {@code notify} 契约的官方厂商实现（与
 * {@code com.xingheyiye.xingye.kit.notify.impl.LoggingSmsClient} 的本地联调桩互补）：
 * 使用方按需引入本模块即可获得真实短信能力，不需要时保持 {@code notify} 零第三方依赖。</p>
 *
 * <p>适用场景：注册/登录验证码、订单通知、告警短信等需要真实投递的生产环境。</p>
 *
 * <p>前置条件：已开通阿里云短信服务、完成签名与模板审核，并持有 AccessKey。</p>
 *
 * <p>线程安全性：内部持有阿里云 {@code Client}，官方 SDK 线程安全，可跨线程共享单例。</p>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
package com.xingheyiye.xingye.kit.sms.aliyun;
