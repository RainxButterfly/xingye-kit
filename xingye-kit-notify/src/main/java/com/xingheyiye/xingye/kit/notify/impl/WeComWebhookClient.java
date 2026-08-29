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
 * {@link WebhookClient} 的企业微信群机器人实现：发送 text 类型消息并按 errcode 判定结果。
 *
 * <p>一句话职责：复用 {@code WebhookJson.textPayload} 通用组包，POST 到企业微信
 * 机器人地址并解析 errcode/errmsg。</p>
 *
 * <p>适用场景：部署通知、告警播报推送到企业微信群。注意：企业微信群机器人有
 * 20 条/分钟的频率限制，超限返回 errcode=45009，高频告警应在上层做聚合或限流。</p>
 *
 * <p>线程安全性：仅持有不可变 webhookUrl 与静态预编译正则，线程安全，可共享单例。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * WebhookClient client = new WeComWebhookClient(
 *         "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx");
 * SendResult result = client.send("部署通知", "服务 v1.2.0 已上线");
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-23
 */
public class WeComWebhookClient implements WebhookClient {

    /** HTTP 连接与读取超时（毫秒） */
    private static final int TIMEOUT_MILLIS = 5000;

    /** 响应体中 errcode 字段的捕获正则（预编译） */
    private static final Pattern ERRCODE_PATTERN = Pattern.compile("\"errcode\"\\s*:\\s*(\\d+)");

    /** 响应体中 errmsg 字段的捕获正则（预编译） */
    private static final Pattern ERRMSG_PATTERN = Pattern.compile("\"errmsg\"\\s*:\\s*\"([^\"]*)\"");

    /** 企业微信群机器人 Webhook 地址（含 key） */
    private final String webhookUrl;

    /**
     * 构造企业微信 Webhook 客户端。
     *
     * @param webhookUrl 企业微信机器人 Webhook 地址（形如
     *                   https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx），
     *                   不能为 null 或空白串
     * @throws IllegalArgumentException webhookUrl 为 null 或空白串时抛出
     */
    public WeComWebhookClient(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.trim().length() == 0) {
            throw new IllegalArgumentException("webhookUrl 不能为 null 或空白串");
        }
        this.webhookUrl = webhookUrl;
    }

    /**
     * 发送 text 类型消息到企业微信群。
     *
     * <p>判定规则（与钉钉一致）：响应体中 {@code "errcode":0} 视为成功（msgId 为
     * "wecom"）；errcode 非 0 时以 errcode 值为错误码、errmsg 为错误信息返回失败；
     * 网络异常（含超时、非 2xx）返回 {@code fail("NETWORK_ERROR", ...)}；
     * 响应体中未找到 errcode 字段返回 {@code fail("PARSE_ERROR", ...)}。</p>
     *
     * @param title 消息标题；企业微信 text 类型消息无标题字段，本实现忽略该参数
     * @param text 消息正文；为 null 时按空串发送；text.content 最长 2048 字节，
     *             超限由企业微信返回错误码
     * @return 发送结果，不会为 null；success 为 true 时 msgId 为 "wecom"
     */
    @Override
    public SendResult send(String title, String text) {
        try {
            String response = HttpSender.postJson(webhookUrl, WebhookJson.textPayload(text), TIMEOUT_MILLIS);
            Matcher errcodeMatcher = ERRCODE_PATTERN.matcher(response);
            if (!errcodeMatcher.find()) {
                return SendResult.fail("PARSE_ERROR", "响应中未找到 errcode 字段: " + response);
            }
            long errcode = Long.parseLong(errcodeMatcher.group(1));
            if (errcode == 0L) {
                return SendResult.ok("wecom");
            }
            String errmsg = "";
            Matcher errmsgMatcher = ERRMSG_PATTERN.matcher(response);
            if (errmsgMatcher.find()) {
                errmsg = errmsgMatcher.group(1);
            }
            return SendResult.fail(String.valueOf(errcode), errmsg);
        } catch (IOException e) {
            return SendResult.fail("NETWORK_ERROR", e.getMessage());
        }
    }
}
