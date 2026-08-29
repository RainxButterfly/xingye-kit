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

import java.util.Map;

/**
 * 短信发送接口：以"手机号 + 签名 + 模板 + 模板变量"的模板短信模型发送验证码与通知。
 *
 * <p>一句话职责：把厂商模板短信（签名、模板 CODE、变量）抽象为一个方法。</p>
 *
 * <p>适用场景：注册/登录验证码、订单与物流通知、告警短信。
 * 实现方负责对接阿里云短信（Dysmsapi）、腾讯云 SMS、梦网、创蓝等真实厂商，
 * 并以 {@link SendResult} 表达厂商回执（如阿里云的 BizId 与 Code/Message）。</p>
 *
 * <p>线程安全性：接口不约定状态，线程安全性由实现类声明。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * Map<String, String> params = new HashMap<String, String>();
 * params.put("code", "123456");
 * SendResult result = smsClient.send("13800138000", "星河工具", "SMS_123456789", params);
 * if (!result.isSuccess()) {
 *     log.warn("短信发送失败: {} {}", result.getErrorCode(), result.getMessage());
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-22
 */
public interface SmsClient {

    /**
     * 发送一条模板短信。
     *
     * <p>约定：厂商返回的业务失败（如限流、黑名单）以 {@code SendResult.fail(...)} 返回，
     * 不抛异常；仅参数非法或不可恢复错误才抛出运行时异常。</p>
     *
     * @param phone 接收手机号，不能为 null 或空白串；具体格式（如国际区号）由实现方校验
     * @param signName 短信签名，不能为 null 或空白串；须与厂商控制台报备的签名一致
     * @param templateCode 短信模板 CODE，不能为 null 或空白串；须为厂商控制台已审核通过的模板
     * @param templateParams 模板变量：键为模板占位名（如 name、code），值为占位内容；
     *                       无变量模板可传 null 或空 Map，不会为 null 时也不应包含 null 值
     * @return 发送结果，不会为 null；success 为 true 时 {@link SendResult#getMsgId()}
     *         返回厂商回执编号（厂商未返回时可能为 null）
     * @throws IllegalArgumentException phone、signName、templateCode 为 null 或空白串等
     *                                  参数非法时由实现方抛出
     * @throws RuntimeException 厂商 SDK 或网络等不可恢复异常时由实现方抛出
     */
    SendResult send(String phone, String signName, String templateCode, Map<String, String> templateParams);
}
