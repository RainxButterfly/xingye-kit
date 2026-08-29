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
 * 邮件发送接口：发送一封由 {@link MailMessage} 描述的邮件（支持 HTML 与附件）。
 *
 * <p>一句话职责：把"收件人 + 主题 + 正文 + 附件"打包为一次发送调用。</p>
 *
 * <p>适用场景：账单与报表投递、系统告警、注册激活信。
 * 实现方负责对接真实 SMTP 服务器（如以 JavaMail 实现）或邮件 API 服务（如阿里云邮件推送），
 * 本模块仅提供契约与本地联调实现
 * {@code com.xingheyiye.xingye.kit.notify.impl.LoggingMailClient}。</p>
 *
 * <p>线程安全性：接口不约定状态，线程安全性由实现类声明。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * MailMessage message = MailMessage.builder()
 *         .to("user@example.com")
 *         .subject("发票")
 *         .content("请查收附件发票。")
 *         .attachments(new File("invoice.pdf"))
 *         .build();
 * SendResult result = mailClient.send(message);
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-22
 */
public interface MailClient {

    /**
     * 发送一封邮件。
     *
     * <p>约定：投递被网关拒绝等业务失败以 {@code SendResult.fail(...)} 返回，不抛异常；
     * 仅参数非法或不可恢复错误才抛出运行时异常。</p>
     *
     * @param message 邮件内容（收件人、主题、正文、附件等），不能为 null；
     *                收件人须至少一个（由 {@link MailMessage.Builder#build()} 保证）
     * @return 发送结果，不会为 null；success 为 true 时 {@link SendResult#getMsgId()}
     *         返回邮件 Message-ID 等回执（可能为 null）
     * @throws IllegalArgumentException message 为 null 等参数非法时由实现方抛出
     * @throws RuntimeException SMTP 会话中断等不可恢复异常时由实现方抛出
     */
    SendResult send(MailMessage message);
}
