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

import com.xingheyiye.xingye.kit.notify.MailClient;
import com.xingheyiye.xingye.kit.notify.MailMessage;
import com.xingheyiye.xingye.kit.notify.SendResult;

import java.util.UUID;

/**
 * {@link MailClient} 的本地日志实现：把邮件摘要打印到 System.out 并返回成功。
 *
 * <p>一句话职责：以一行邮件摘要日志替代真实 SMTP 发送，返回随机 UUID 作为回执。</p>
 *
 * <p>适用场景：本地联调与单元测试，验证邮件组装与触发链路，不产生任何外部副作用。</p>
 *
 * <p>线程安全性：无状态，线程安全，可在多线程间共享单例。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * MailClient mailClient = new LoggingMailClient();
 * SendResult result = mailClient.send(MailMessage.builder()
 *         .to("user@example.com")
 *         .subject("测试邮件")
 *         .content("Hello!")
 *         .build());
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-23
 */
public class LoggingMailClient implements MailClient {

    /**
     * 打印邮件摘要并返回成功结果。
     *
     * <p>摘要包含收件人、抄送、主题、正文类型、正文长度与附件数，不打印正文全文
     * （避免长内容刷屏与敏感信息外泄）。</p>
     *
     * @param message 邮件内容，不能为 null
     * @return 恒定为成功结果，msgId 为随机 UUID，不会为 null
     * @throws IllegalArgumentException message 为 null 时抛出
     */
    @Override
    public SendResult send(MailMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message 不能为 null");
        }
        String content = message.getContent();
        System.out.println("[logging-mail] to=" + message.getTo() + ", cc=" + message.getCc()
                + ", subject=" + message.getSubject()
                + ", html=" + message.isHtml()
                + ", contentLength=" + (content == null ? 0 : content.length())
                + ", attachments=" + message.getAttachments().size());
        return SendResult.ok(UUID.randomUUID().toString());
    }
}
