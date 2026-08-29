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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 不可变邮件消息值对象：由内嵌 Builder 构建，描述一次 {@link MailClient} 发送的全部内容。
 *
 * <p>一句话职责：把收件人、抄送、主题、正文、正文类型与附件收拢为一个线程安全的不可变对象。</p>
 *
 * <p>适用场景：SMTP/邮件 API 发送前的消息组装；收件人至少一个在
 * {@link Builder#build()} 时校验。</p>
 *
 * <p>线程安全性：不可变对象（getter 返回不可修改视图），构建完成后可自由跨线程共享；
 * Builder 本身非线程安全，应在单线程内完成构建。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * MailMessage message = MailMessage.builder()
 *         .to("user@example.com", "boss@example.com")
 *         .cc("audit@example.com")
 *         .subject("订单发货通知")
 *         .content("<h3>您的订单已发货</h3>")
 *         .html(true)
 *         .attachments(new File("invoice.pdf"))
 *         .build();
 * SendResult result = mailClient.send(message);
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-22
 */
public final class MailMessage {

    /** 收件人地址列表，构建后至少包含一个元素且不可修改 */
    private final List<String> to;

    /** 抄送地址列表，可为空列表且不可修改 */
    private final List<String> cc;

    /** 邮件主题；未设置时为 null */
    private final String subject;

    /** 邮件正文；未设置时为 null */
    private final String content;

    /** true 表示正文按 HTML 发送，false 表示纯文本 */
    private final boolean html;

    /** 附件文件列表，可为空列表且不可修改 */
    private final List<File> attachments;

    /**
     * 私有构造：仅由 {@link Builder#build()} 调用。
     *
     * @param builder 已完成组装的构建器，不能为 null
     */
    private MailMessage(Builder builder) {
        this.to = Collections.unmodifiableList(new ArrayList<String>(builder.to));
        this.cc = Collections.unmodifiableList(new ArrayList<String>(builder.cc));
        this.subject = builder.subject;
        this.content = builder.content;
        this.html = builder.html;
        this.attachments = Collections.unmodifiableList(new ArrayList<File>(builder.attachments));
    }

    /**
     * 创建邮件构建器。
     *
     * @return 新的 Builder 实例，不会为 null
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return 收件人地址列表（不可修改），至少包含一个元素，不会为 null
     */
    public List<String> getTo() {
        return to;
    }

    /**
     * @return 抄送地址列表（不可修改），未设置抄送时为空列表，不会为 null
     */
    public List<String> getCc() {
        return cc;
    }

    /**
     * @return 邮件主题；未设置时为 null
     */
    public String getSubject() {
        return subject;
    }

    /**
     * @return 邮件正文；未设置时为 null
     */
    public String getContent() {
        return content;
    }

    /**
     * @return true 表示正文按 HTML 发送；false 表示纯文本
     */
    public boolean isHtml() {
        return html;
    }

    /**
     * @return 附件文件列表（不可修改），未设置附件时为空列表，不会为 null
     */
    public List<File> getAttachments() {
        return attachments;
    }

    /**
     * {@link MailMessage} 的链式构建器。
     *
     * <p>非线程安全：请在单线程内完成构建，构建产物（MailMessage）才是共享对象。</p>
     *
     * @author 星河一叶 (RainxButterfly)
     * @since 2026-08-22
     */
    public static final class Builder {

        /** 收件人列表（待组装） */
        private final List<String> to = new ArrayList<String>();

        /** 抄送列表（待组装） */
        private final List<String> cc = new ArrayList<String>();

        /** 附件列表（待组装） */
        private final List<File> attachments = new ArrayList<File>();

        /** 邮件主题（待组装） */
        private String subject;

        /** 邮件正文（待组装） */
        private String content;

        /** 是否 HTML 正文，默认 false（纯文本） */
        private boolean html;

        /**
         * 私有构造：请通过 {@link MailMessage#builder()} 获取实例。
         */
        private Builder() {
        }

        /**
         * 追加收件人（可多次调用，累计生效）。
         *
         * @param recipients 收件人邮箱地址；可传 null 或空数组（忽略）；其中的 null 元素将被忽略
         * @return 当前 Builder（链式调用），不会为 null
         */
        public Builder to(String... recipients) {
            addAll(to, recipients);
            return this;
        }

        /**
         * 追加抄送人（可多次调用，累计生效）。
         *
         * @param recipients 抄送邮箱地址；可传 null 或空数组（忽略）；其中的 null 元素将被忽略
         * @return 当前 Builder（链式调用），不会为 null
         */
        public Builder cc(String... recipients) {
            addAll(cc, recipients);
            return this;
        }

        /**
         * 设置邮件主题（后设置覆盖先设置）。
         *
         * @param subject 邮件主题；可为 null（由发送实现决定是否允许）
         * @return 当前 Builder（链式调用），不会为 null
         */
        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        /**
         * 设置邮件正文（后设置覆盖先设置）。
         *
         * @param content 邮件正文；可为 null（由发送实现决定是否允许）
         * @return 当前 Builder（链式调用），不会为 null
         */
        public Builder content(String content) {
            this.content = content;
            return this;
        }

        /**
         * 设置正文是否为 HTML。
         *
         * @param html true 表示 HTML 正文，false 表示纯文本
         * @return 当前 Builder（链式调用），不会为 null
         */
        public Builder html(boolean html) {
            this.html = html;
            return this;
        }

        /**
         * 追加附件（可多次调用，累计生效）。
         *
         * @param files 附件文件；可传 null 或空数组（忽略）；其中的 null 元素将被忽略，
         *              文件是否存在由发送实现校验
         * @return 当前 Builder（链式调用），不会为 null
         */
        public Builder attachments(File... files) {
            addAll(attachments, files);
            return this;
        }

        /**
         * 构建不可变邮件对象。
         *
         * @return 不可变 MailMessage 实例，不会为 null
         * @throws IllegalStateException 未通过 {@link #to(String...)} 设置任何收件人时抛出
         */
        public MailMessage build() {
            if (to.isEmpty()) {
                throw new IllegalStateException("收件人(to)不能为空，请至少通过 to(...) 设置一个收件人");
            }
            return new MailMessage(this);
        }

        /**
         * 将数组中的非 null 元素追加到目标列表。
         *
         * @param target 目标列表，不能为 null
         * @param items 来源数组，为 null 时直接忽略
         */
        private static <T> void addAll(List<T> target, T[] items) {
            if (items == null) {
                return;
            }
            for (T item : items) {
                if (item != null) {
                    target.add(item);
                }
            }
        }
    }
}
