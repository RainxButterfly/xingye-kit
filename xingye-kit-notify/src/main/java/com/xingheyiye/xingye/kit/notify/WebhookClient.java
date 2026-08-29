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
 * Webhook 消息发送接口：向钉钉、飞书、企业微信等群机器人地址推送一条文本消息。
 *
 * <p>一句话职责：把"标题 + 正文"推送为一个 Webhook 目标（URL 由实现持有）。</p>
 *
 * <p>适用场景：部署通知、告警播报、CI/CD 结果推送。
 * 本模块自带钉钉、飞书、企业微信三种基于 JDK HttpURLConnection 的默认实现
 * （{@code com.xingheyiye.xingye.kit.notify.impl} 包），其它平台由使用方实现本接口。</p>
 *
 * <p>线程安全性：接口不约定状态，线程安全性由实现类声明
 * （本模块自带实现均仅持有不可变 URL，线程安全）。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * WebhookClient client = new DingTalkWebhookClient("https://oapi.dingtalk.com/robot/send?access_token=xxx");
 * SendResult result = client.send("部署通知", "服务 v1.2.0 已发布");
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-22
 */
public interface WebhookClient {

    /**
     * 向 Webhook 目标发送一条文本消息。
     *
     * <p>约定：网络异常、平台返回业务错误码均以 {@code SendResult.fail(...)} 返回，
     * 不抛异常；仅参数非法等不可继续场景抛出运行时异常。</p>
     *
     * @param title 消息标题，可为 null（部分平台 text 类型消息不使用标题，实现可忽略）
     * @param text 消息正文，不能为 null；平台通常限制长度（如钉钉 text 最长 20000 字节），
     *            超限由实现方决定截断或返回失败
     * @return 发送结果，不会为 null；success 为 true 时 {@link SendResult#getMsgId()}
     *         为平台标识或回执（可能为 null）
     * @throws IllegalArgumentException 实现方校验参数非法时抛出
     * @throws RuntimeException 不可恢复错误时由实现方抛出
     */
    SendResult send(String title, String text);
}
