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
package com.xingheyiye.xingye.kit.sms.aliyun;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.xingheyiye.xingye.kit.notify.SendResult;
import com.xingheyiye.xingye.kit.notify.SmsClient;

import java.util.Map;

/**
 * 阿里云短信实现的 {@link SmsClient}：真实调用阿里云短信服务发送模板短信。
 *
 * <p>一句话职责：把"手机号 + 签名 + 模板 CODE + 模板变量"转成阿里云 SendSms 请求，
 * 以 SendSmsResponse 的 Code=="OK" 判定成功，BizId 作为回执编号。</p>
 *
 * <p>适用场景：生产环境真实短信投递（验证码、订单通知、告警）。前置条件：
 * 已在阿里云控制台开通短信服务，完成签名与模板审核，并持有 AccessKey；
 * 模板变量 JSON 由本类内部序列化（无需第三方 JSON 库）。</p>
 *
 * <p>线程安全性：内部持有的阿里云 {@link Client} 官方 SDK 线程安全，
 * 本类无可变状态，可跨线程共享单例。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * SmsClient smsClient = new AliyunSmsClient("LTAI...", "secret");
 * SendResult result = smsClient.send("13800138000", "星叶工具", "SMS_123456789",
 *         Collections.singletonMap("code", "123456"));
 * if (result.isSuccess()) {
 *     log.info("短信已发送, BizId={}", result.getMsgId());
 * } else {
 *     log.warn("发送失败: {} {}", result.getErrorCode(), result.getMessage());
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public class AliyunSmsClient implements SmsClient {

    /** 阿里云短信服务默认接入点（国内站，无需更换） */
    private static final String DEFAULT_ENDPOINT = "dysmsapi.aliyuncs.com";

    /** 阿里云短信请求客户端（线程安全，一次构造长生命周期复用） */
    private final Client client;

    /**
     * 构造阿里云短信客户端（使用默认接入点）。
     *
     * @param accessKeyId 阿里云 AccessKey ID，不能为 null 或空白串
     * @param accessKeySecret 阿里云 AccessKey Secret，不能为 null 或空白串
     * @throws IllegalArgumentException accessKeyId 或 accessKeySecret 为 null 或空白串时抛出
     * @throws IllegalStateException 初始化阿里云 SDK 客户端失败时抛出
     */
    public AliyunSmsClient(String accessKeyId, String accessKeySecret) {
        this(accessKeyId, accessKeySecret, DEFAULT_ENDPOINT);
    }

    /**
     * 构造阿里云短信客户端（可自定义接入点）。
     *
     * @param accessKeyId 阿里云 AccessKey ID，不能为 null 或空白串
     * @param accessKeySecret 阿里云 AccessKey Secret，不能为 null 或空白串
     * @param endpoint 服务接入点，如 "dysmsapi.aliyuncs.com"；不能为 null 或空白串
     * @throws IllegalArgumentException 任一参数为 null 或空白串时抛出
     * @throws IllegalStateException 初始化阿里云 SDK 客户端失败时抛出
     */
    public AliyunSmsClient(String accessKeyId, String accessKeySecret, String endpoint) {
        if (accessKeyId == null || accessKeyId.trim().length() == 0) {
            throw new IllegalArgumentException("accessKeyId 不能为 null 或空白串");
        }
        if (accessKeySecret == null || accessKeySecret.trim().length() == 0) {
            throw new IllegalArgumentException("accessKeySecret 不能为 null 或空白串");
        }
        if (endpoint == null || endpoint.trim().length() == 0) {
            throw new IllegalArgumentException("endpoint 不能为 null 或空白串");
        }
        try {
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret)
                    .setEndpoint(endpoint);
            this.client = new Client(config);
        } catch (Exception e) {
            throw new IllegalStateException("初始化阿里云短信客户端失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送一条模板短信到阿里云。
     *
     * <p>判定规则：响应 {@code Code=="OK"} 视为成功（msgId 为 BizId）；
     * 其他 Code 以该 Code 为错误码、Message 为错误信息返回失败；
     * SDK 或网络异常返回 {@code fail("NETWORK_ERROR", ...)}。</p>
     *
     * @param phone 接收手机号，不能为 null 或空白串（格式校验由阿里云完成）
     * @param signName 短信签名，须与阿里云控制台报备的签名一致，不能为 null 或空白串
     * @param templateCode 短信模板 CODE，须为阿里云控制台已审核通过的模板，不能为 null 或空白串
     * @param templateParams 模板变量：键为模板占位名（如 code），值为占位内容；
     *                       无变量模板可传 null 或空 Map
     * @return 发送结果，不会为 null；success 为 true 时 msgId 为阿里云 BizId
     * @throws IllegalArgumentException phone、signName、templateCode 为 null 或空白串时抛出
     */
    @Override
    public SendResult send(String phone, String signName, String templateCode, Map<String, String> templateParams) {
        if (phone == null || phone.trim().length() == 0) {
            throw new IllegalArgumentException("phone 不能为 null 或空白串");
        }
        if (signName == null || signName.trim().length() == 0) {
            throw new IllegalArgumentException("signName 不能为 null 或空白串");
        }
        if (templateCode == null || templateCode.trim().length() == 0) {
            throw new IllegalArgumentException("templateCode 不能为 null 或空白串");
        }
        try {
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(phone)
                    .setSignName(signName)
                    .setTemplateCode(templateCode)
                    .setTemplateParam(toJsonObject(templateParams));
            SendSmsResponse response = client.sendSms(request);
            String code = response.getBody().getCode();
            if ("OK".equals(code)) {
                return SendResult.ok(response.getBody().getBizId());
            }
            return SendResult.fail(code, response.getBody().getMessage());
        } catch (Exception e) {
            return SendResult.fail("NETWORK_ERROR", e.getMessage());
        }
    }

    /**
     * 将模板变量 Map 序列化为阿里云要求的 JSON 对象串，如 {@code {"code":"123456"}}。
     *
     * @param params 模板变量；为 null 或空时返回 "{}"
     * @return JSON 对象字符串，不会为 null
     */
    private static String toJsonObject(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(entry.getKey())).append('"')
                    .append(':')
                    .append('"').append(escape(entry.getValue() == null ? "" : entry.getValue())).append('"');
        }
        return sb.append('}').toString();
    }

    /**
     * 转义 JSON 字符串字面量中的特殊字符（引号、反斜杠、控制字符）。
     *
     * @param s 原始字符串，可为 null（视为空串）
     * @return 转义后的字符串，不会为 null
     */
    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        // 其余控制字符转为反斜杠加 u 加四位十六进制的转义形式，避免直接写入非法字节
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
