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
package com.xingheyiye.xingye.kit.mail;

import com.xingheyiye.xingye.kit.notify.MailClient;
import com.xingheyiye.xingye.kit.notify.MailMessage;
import com.xingheyiye.xingye.kit.notify.SendResult;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * 基于 JavaMail（SMTP 协议）实现的 {@link MailClient}：真实发送纯文本 / HTML / 带附件邮件。
 *
 * <p>一句话职责：把 {@link MailMessage}（收件人、抄送、主题、正文、附件）经 SMTP 服务器
 * 真实投递，并以 Message-ID 作为回执编号。</p>
 *
 * <p>适用场景：生产环境真实邮件投递（账单、告警、激活信）。
 * 前置条件：具备可用的 SMTP 服务器与发件账号（如企业邮箱、Gmail、QQ 邮箱，
 * 需开通 SMTP 服务并取得授权码）。</p>
 *
 * <p>配置约定：默认 465 端口 + SSL 加密（smtps）；使用 587 端口 + STARTTLS 时请设置
 * {@code .ssl(false).startTls(true).port(587)}；不需要认证的本地 SMTP 可不设置 username/password。</p>
 *
 * <p>线程安全性：内部仅持有不可变配置与无状态的 {@link Session}，线程安全，可跨线程共享单例。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * MailClient mailClient = JavaMailClient.builder()
 *         .host("smtp.example.com")
 *         .port(465)
 *         .username("noreply@example.com")
 *         .password("smtp-auth-code")
 *         .from("noreply@example.com")
 *         .fromName("星叶工具箱")
 *         .build();
 *
 * SendResult result = mailClient.send(MailMessage.builder()
 *         .to("user@example.com")
 *         .subject("订单发货通知")
 *         .content("<h3>您的订单已发货</h3>")
 *         .html(true)
 *         .build());
 * if (result.isSuccess()) {
 *     log.info("邮件已发送, Message-ID={}", result.getMsgId());
 * } else {
 *     log.warn("发送失败: {} {}", result.getErrorCode(), result.getMessage());
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public class JavaMailClient implements MailClient {

    /** SMTP 会话（不可变配置，无状态，线程安全） */
    private final Session session;

    /** 发件人地址 */
    private final String from;

    /** 发件人显示名；为空时不设置 */
    private final String fromName;

    /**
     * 私有构造：仅由 {@link Builder#build()} 调用。
     *
     * @param builder 已完成校验的构建器，不能为 null
     */
    private JavaMailClient(Builder builder) {
        Properties props = new Properties();
        props.put("mail.smtp.host", builder.host);
        props.put("mail.smtp.port", String.valueOf(builder.port));
        props.put("mail.smtp.connectiontimeout", String.valueOf(builder.connectTimeoutMillis));
        props.put("mail.smtp.timeout", String.valueOf(builder.readTimeoutMillis));
        props.put("mail.smtp.writetimeout", String.valueOf(builder.writeTimeoutMillis));
        if (builder.username != null && builder.username.trim().length() > 0) {
            props.put("mail.smtp.auth", "true");
        }
        if (builder.ssl) {
            props.put("mail.smtp.ssl.enable", "true");
        } else if (builder.startTls) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        this.from = builder.from;
        this.fromName = builder.fromName;
        final String username = builder.username;
        final String password = builder.password;
        if (username == null || username.trim().length() == 0) {
            this.session = Session.getInstance(props);
        } else {
            this.session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });
        }
    }

    /**
     * 创建邮件客户端构建器。
     *
     * @return 新的 Builder 实例，不会为 null
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 经 SMTP 发送一封邮件。
     *
     * <p>判定规则：SMTP 成功投递视为成功（msgId 为 Message-ID）；SMTP 会话异常
     * （认证失败、连接失败、收件人被拒等）返回 {@code fail("SMTP_ERROR", ...)}；
     * 附件读取失败返回 {@code fail("IO_ERROR", ...)}；附件文件不存在视为参数非法
     * 抛出 {@link IllegalArgumentException}。</p>
     *
     * @param message 邮件内容（收件人、主题、正文、附件），不能为 null；
     *                收件人至少一个由 {@link MailMessage.Builder#build()} 保证
     * @return 发送结果，不会为 null；success 为 true 时 msgId 为邮件的 Message-ID
     * @throws IllegalArgumentException message 为 null，或附件文件不存在时抛出
     */
    @Override
    public SendResult send(MailMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message 不能为 null");
        }
        try {
            MimeMessage mime = new MimeMessage(session);
            if (fromName == null || fromName.trim().length() == 0) {
                mime.setFrom(new InternetAddress(from));
            } else {
                mime.setFrom(new InternetAddress(from, fromName));
            }
            for (String to : message.getTo()) {
                mime.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            }
            for (String cc : message.getCc()) {
                mime.addRecipient(Message.RecipientType.CC, new InternetAddress(cc));
            }
            mime.setSubject(message.getSubject() == null ? "" : message.getSubject(), "UTF-8");
            attachBody(mime, message);
            // 显式保存变更以生成 Message-ID 头，供下方取回执编号
            mime.saveChanges();
            Transport.send(mime);
            return SendResult.ok(mime.getMessageID());
        } catch (MessagingException e) {
            return SendResult.fail("SMTP_ERROR", e.getMessage());
        } catch (UnsupportedEncodingException e) {
            return SendResult.fail("CONFIG_ERROR", e.getMessage());
        } catch (IOException e) {
            return SendResult.fail("IO_ERROR", e.getMessage());
        }
    }

    /**
     * 组装邮件正文与附件到 MimeMessage。
     *
     * @param mime 目标邮件对象，不能为 null
     * @param message 邮件消息，不能为 null
     * @throws MessagingException 组装 MIME 结构失败时抛出
     * @throws IOException 附件文件读取失败时抛出
     */
    private static void attachBody(MimeMessage mime, MailMessage message) throws MessagingException, IOException {
        String content = message.getContent() == null ? "" : message.getContent();
        if (message.getAttachments().isEmpty()) {
            if (message.isHtml()) {
                mime.setContent(content, "text/html; charset=UTF-8");
            } else {
                mime.setText(content, "UTF-8");
            }
            return;
        }
        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart textPart = new MimeBodyPart();
        if (message.isHtml()) {
            textPart.setContent(content, "text/html; charset=UTF-8");
        } else {
            textPart.setText(content, "UTF-8");
        }
        multipart.addBodyPart(textPart);
        for (File file : message.getAttachments()) {
            if (file == null || !file.exists() || !file.isFile()) {
                throw new IllegalArgumentException("附件文件不存在或不是普通文件: "
                        + (file == null ? "null" : file.getAbsolutePath()));
            }
            MimeBodyPart filePart = new MimeBodyPart();
            filePart.setDataHandler(new DataHandler(new FileDataSource(file)));
            filePart.setFileName(file.getName());
            multipart.addBodyPart(filePart);
        }
        mime.setContent(multipart);
    }

    /**
     * {@link JavaMailClient} 的链式构建器。
     *
     * <p>非线程安全：请在单线程内完成构建，构建产物（JavaMailClient）才是共享对象。</p>
     *
     * @author 星河一叶 (RainxButterfly)
     * @since 2026-08-30
     */
    public static final class Builder {

        /** SMTP 服务器主机名或 IP，必填 */
        private String host;

        /** SMTP 端口，默认 465（配合默认 SSL） */
        private int port = 465;

        /** 认证用户名（可为 null，表示不需要认证） */
        private String username;

        /** 认证密码或授权码（可为 null） */
        private String password;

        /** 发件人地址，必填 */
        private String from;

        /** 发件人显示名（可为 null，表示不设置） */
        private String fromName;

        /** 是否启用 SSL 加密（smtps），默认 true */
        private boolean ssl = true;

        /** 是否启用 STARTTLS 升级（在非 SSL 端口如 587 上使用），默认 false */
        private boolean startTls;

        /** 连接超时（毫秒），默认 10000 */
        private int connectTimeoutMillis = 10000;

        /** 读超时（毫秒），默认 30000 */
        private int readTimeoutMillis = 30000;

        /** 写超时（毫秒），默认 30000 */
        private int writeTimeoutMillis = 30000;

        /**
         * 私有构造：请通过 {@link JavaMailClient#builder()} 获取实例。
         */
        private Builder() {
        }

        /**
         * 设置 SMTP 服务器主机名或 IP（必填）。
         *
         * @param host 主机名或 IP，不能为 null 或空白串
         * @return 当前 Builder（链式调用），不会为 null
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * 设置 SMTP 端口。
         *
         * @param port 端口号，须为正整数；465 通常配 SSL，587 通常配 STARTTLS
         * @return 当前 Builder（链式调用），不会为 null
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * 设置认证用户名（发件账号）。
         *
         * @param username 用户名；与 password 须同时设置或同时不设
         * @return 当前 Builder（链式调用），不会为 null
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * 设置认证密码（或第三方邮箱的授权码）。
         *
         * @param password 密码或授权码；与 username 须同时设置或同时不设
         * @return 当前 Builder（链式调用），不会为 null
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * 设置发件人地址（必填）。
         *
         * @param from 发件人邮箱地址，不能为 null 或空白串
         * @return 当前 Builder（链式调用），不会为 null
         */
        public Builder from(String from) {
            this.from = from;
            return this;
        }

        /**
         * 设置发件人显示名（收件人侧展示的名称）。
         *
         * @param fromName 显示名；为 null 或空白串时表示不设置
         * @return 当前 Builder（链式调用），不会为 null
         */
        public Builder fromName(String fromName) {
            this.fromName = fromName;
            return this;
        }

        /**
         * 设置是否启用 SSL 加密（smtps）。
         *
         * @param ssl true 表示启用（默认）；false 表示关闭
         * @return 当前 Builder（链式调用），不会为 null
         */
        public Builder ssl(boolean ssl) {
            this.ssl = ssl;
            return this;
        }

        /**
         * 设置是否启用 STARTTLS 升级（适用于 587 等非 SSL 端口）。
         *
         * @param startTls true 表示启用；仅在 ssl 为 false 时生效
         * @return 当前 Builder（链式调用），不会为 null
         */
        public Builder startTls(boolean startTls) {
            this.startTls = startTls;
            return this;
        }

        /**
         * 统一设置三个超时（毫秒）。
         *
         * @param connectMillis 连接超时，须为正整数
         * @param readMillis 读超时，须为正整数
         * @param writeMillis 写超时，须为正整数
         * @return 当前 Builder（链式调用），不会为 null
         */
        public Builder timeouts(int connectMillis, int readMillis, int writeMillis) {
            if (connectMillis <= 0 || readMillis <= 0 || writeMillis <= 0) {
                throw new IllegalArgumentException("三个超时时间都必须为正整数");
            }
            this.connectTimeoutMillis = connectMillis;
            this.readTimeoutMillis = readMillis;
            this.writeTimeoutMillis = writeMillis;
            return this;
        }

        /**
         * 构建邮件客户端。
         *
         * @return 可复用的 JavaMailClient 实例，不会为 null
         * @throws IllegalStateException host 或 from 未设置、port 非正数、
         *                               username 与 password 未成对设置时抛出
         */
        public JavaMailClient build() {
            if (host == null || host.trim().length() == 0) {
                throw new IllegalStateException("host 不能为空");
            }
            if (from == null || from.trim().length() == 0) {
                throw new IllegalStateException("from 发件地址不能为空");
            }
            if (port <= 0) {
                throw new IllegalStateException("port 必须为正整数");
            }
            boolean hasUser = username != null && username.trim().length() > 0;
            boolean hasPwd = password != null && password.trim().length() > 0;
            if (hasUser != hasPwd) {
                throw new IllegalStateException("username 与 password 必须同时设置或同时不设");
            }
            return new JavaMailClient(this);
        }
    }
}
