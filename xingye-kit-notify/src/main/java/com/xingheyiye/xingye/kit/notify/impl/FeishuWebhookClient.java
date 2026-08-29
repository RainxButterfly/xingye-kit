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

import com.xingheyiye.xingye.kit.notify.SendResult;
import com.xingheyiye.xingye.kit.notify.WebhookClient;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link WebhookClient} 的飞书群机器人实现：发送 text 类型消息并按 code 判定结果。
 *
 * <p>一句话职责：自行以 {@code WebhookJson.escape} 拼装飞书
 * {@code {"msg_type":"text","content":{"text":"..."}}} 消息体并 POST 到机器人地址。</p>
 *
 * <p>适用场景：部署通知、告警播报推送到飞书群。注意：飞书机器人若开启了"签名校验"
 * （secret），需自行扩展加签逻辑，本实现不处理签名。</p>
 *
 * <p>线程安全性：仅持有不可变 webhookUrl 与静态预编译正则，线程安全，可共享单例。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * WebhookClient client = new FeishuWebhookClient(
 *         "https://open.feishu.cn/open-apis/bot/v2/hook/xxx");
 * SendResult result = client.send("部署通知", "服务 v1.2.0 已上线");
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-23
 */
public class FeishuWebhookClient implements WebhookClient {

    /** HTTP 连接与读取超时（毫秒） */
    private static final int TIMEOUT_MILLIS = 5000;

    /** 响应体中 code 字段的捕获正则（预编译）；响应不含 code 字段时视为成功 */
    private static final Pattern CODE_PATTERN = Pattern.compile("\"code\"\\s*:\\s*(\\d+)");

    /** 飞书机器人 Webhook 地址 */
    private final String webhookUrl;

    /**
     * 构造飞书 Webhook 客户端。
     *
     * @param webhookUrl 飞书机器人 Webhook 地址（形如
     *                   https://open.feishu.cn/open-apis/bot/v2/hook/xxx），
     *                   不能为 null 或空白串
     * @throws IllegalArgumentException webhookUrl 为 null 或空白串时抛出
     */
    public FeishuWebhookClient(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.trim().length() == 0) {
            throw new IllegalArgumentException("webhookUrl 不能为 null 或空白串");
        }
        this.webhookUrl = webhookUrl;
    }

    /**
     * 发送 text 类型消息到飞书群。
     *
     * <p>判定规则：HTTP 状态码非 2xx 时底层抛 IOException（返回
     * {@code fail("NETWORK_ERROR", ...)}）；响应体中 {@code "code":0} 或不含 code 字段
     * 均视为成功（msgId 为 "feishu"）；code 非 0 时以 code 值为错误码、
     * 完整响应体为错误信息返回失败。</p>
     *
     * @param title 消息标题；飞书 text 类型消息无标题字段，本实现忽略该参数
     * @param text 消息正文；为 null 时按空串发送
     * @return 发送结果，不会为 null；success 为 true 时 msgId 为 "feishu"
     */
    @Override
    public SendResult send(String title, String text) {
        try {
            String payload = "{\"msg_type\":\"text\",\"content\":{\"text\":\""
                    + WebhookJson.escape(text) + "\"}}";
            String response = HttpSender.postJson(webhookUrl, payload, TIMEOUT_MILLIS);
            Matcher codeMatcher = CODE_PATTERN.matcher(response);
            if (!codeMatcher.find()) {
                // 无 code 字段视为成功
                return SendResult.ok("feishu");
            }
            long code = Long.parseLong(codeMatcher.group(1));
            if (code == 0L) {
                return SendResult.ok("feishu");
            }
            return SendResult.fail(String.valueOf(code), "飞书返回错误: " + response);
        } catch (IOException e) {
            return SendResult.fail("NETWORK_ERROR", e.getMessage());
        }
    }
}
