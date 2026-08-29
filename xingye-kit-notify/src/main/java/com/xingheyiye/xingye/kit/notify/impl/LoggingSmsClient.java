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
import com.xingheyiye.xingye.kit.notify.SmsClient;

import java.util.Map;
import java.util.UUID;

/**
 * {@link SmsClient} 的本地日志实现：把短信参数摘要打印到 System.out 并返回成功。
 *
 * <p>一句话职责：以一行参数日志替代真实厂商调用，返回随机 UUID 作为回执。</p>
 *
 * <p>适用场景：本地联调与单元测试，验证短信触发链路与参数拼装，不产生任何外部副作用
 * （不扣短信费用）。</p>
 *
 * <p>线程安全性：无状态，线程安全，可在多线程间共享单例。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * SmsClient smsClient = new LoggingSmsClient();
 * SendResult result = smsClient.send("13800138000", "星河工具", "SMS_123456789",
 *         Collections.singletonMap("code", "123456"));
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-23
 */
public class LoggingSmsClient implements SmsClient {

    /**
     * 打印短信参数摘要并返回成功结果。
     *
     * <p>参数为 null 时打印 "null"，不做校验、不抛异常（本地联调语义）。</p>
     *
     * @param phone 接收手机号（仅打印）
     * @param signName 短信签名（仅打印）
     * @param templateCode 短信模板 CODE（仅打印）
     * @param templateParams 模板变量（仅打印其 toString 摘要）
     * @return 恒定为成功结果，msgId 为随机 UUID，不会为 null
     */
    @Override
    public SendResult send(String phone, String signName, String templateCode, Map<String, String> templateParams) {
        System.out.println("[logging-sms] phone=" + phone + ", signName=" + signName
                + ", templateCode=" + templateCode + ", templateParams=" + templateParams);
        return SendResult.ok(UUID.randomUUID().toString());
    }
}
