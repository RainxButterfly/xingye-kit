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
 * 发送结果的统一不可变值对象：短信、邮件、Webhook 发送接口的返回值。
 *
 * <p>一句话职责：把"是否成功 + 回执编号 + 错误码 + 错误信息"压缩为一个不可变对象，
 * 以返回值（而非异常）表达业务失败。</p>
 *
 * <p>适用场景：所有发送类接口（{@link SmsClient}、{@link MailClient}、{@link WebhookClient}
 * 及其实现）的结果传递与日志输出。</p>
 *
 * <p>线程安全性：不可变对象，线程安全，可自由跨线程共享。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * SendResult result = smsClient.send(phone, signName, templateCode, params);
 * if (result.isSuccess()) {
 *     log.info("短信已发送, msgId={}", result.getMsgId());
 * } else {
 *     log.warn("短信发送失败: {} {}", result.getErrorCode(), result.getMessage());
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-22
 */
public final class SendResult {

    /** 是否发送成功 */
    private final boolean success;

    /** 成功时的厂商回执编号（如短信 BizId、邮件 Message-ID）；失败时为 null */
    private final String msgId;

    /** 失败时的错误码（厂商错误码或本库约定码，如 "NETWORK_ERROR"）；成功时为 null */
    private final String errorCode;

    /** 失败时的人类可读错误信息，用于日志与排查；成功时为 null */
    private final String message;

    /**
     * 私有构造：请使用 {@link #ok(String)} 或 {@link #fail(String, String)} 创建实例。
     *
     * @param success 是否成功
     * @param msgId 成功时的回执编号，可为 null
     * @param errorCode 失败时的错误码，可为 null
     * @param message 失败时的错误信息，可为 null
     */
    private SendResult(boolean success, String msgId, String errorCode, String message) {
        this.success = success;
        this.msgId = msgId;
        this.errorCode = errorCode;
        this.message = message;
    }

    /**
     * 创建成功结果。
     *
     * @param msgId 厂商回执编号；厂商未返回回执时可传 null
     * @return 不可变的成功结果（success=true），不会为 null
     */
    public static SendResult ok(String msgId) {
        return new SendResult(true, msgId, null, null);
    }

    /**
     * 创建失败结果。
     *
     * @param errorCode 错误码（厂商错误码或本库约定码，如 "NETWORK_ERROR"、"PARSE_ERROR"）；
     *                  可为 null，但强烈建议提供以便定位
     * @param message 人类可读错误信息，用于日志与排查；可为 null，但强烈建议提供
     * @return 不可变的失败结果（success=false），不会为 null
     */
    public static SendResult fail(String errorCode, String message) {
        return new SendResult(false, null, errorCode, message);
    }

    /**
     * @return true 表示发送成功；false 表示发送失败
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * @return 成功时的厂商回执编号；失败时或厂商未返回回执时为 null
     */
    public String getMsgId() {
        return msgId;
    }

    /**
     * @return 失败时的错误码；成功时为 null
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * @return 失败时的人类可读错误信息；成功时为 null
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return 形如 {@code SendResult{success=true, msgId='xxx', errorCode='null', message='null'}} 的摘要字符串，不会为 null
     */
    @Override
    public String toString() {
        return "SendResult{success=" + success
                + ", msgId='" + msgId + '\''
                + ", errorCode='" + errorCode + '\''
                + ", message='" + message + '\'' + '}';
    }
}
