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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

/**
 * 包内共享的 HTTP 发送工具：基于 JDK HttpURLConnection 的 POST JSON 请求。
 *
 * <p>一句话职责：把"POST 一段 JSON 并读回响应体"收敛为一个静态方法，统一超时与
 * 非 2xx 抛错语义。</p>
 *
 * <p>适用场景：本包内钉钉、飞书、企业微信 Webhook 客户端的底层发送通道；
 * 高并发或需要连接池/重试的场景应由使用方以专业 HTTP 客户端另行实现。</p>
 *
 * <p>线程安全性：无状态工具类（仅私有构造与静态方法），线程安全，可并发调用
 * （HttpURLConnection 按调用独享实例，不共享连接）。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * String response = HttpSender.postJson("https://oapi.dingtalk.com/robot/send?access_token=xxx",
 *         "{\"msgtype\":\"text\"}", 5000);
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-23
 */
final class HttpSender {

    /** 响应体读取缓冲区大小（字节） */
    private static final int BUFFER_SIZE = 4096;

    /**
     * 私有构造：禁止实例化工具类。
     */
    private HttpSender() {
    }

    /**
     * 以 POST 方法发送 JSON 请求体并返回响应体字符串。
     *
     * <p>请求头固定为 {@code Content-Type: application/json;charset=UTF-8} 与
     * {@code Accept: application/json}；响应体按 UTF-8 解码。
     * 超时语义：建立连接阶段超时抛 {@code IOException("connect timeout")}，
     * 连接建立后（写出/读取响应）超时抛 {@code IOException("read timeout")}；
     * HTTP 状态码非 2xx 时抛出消息含状态码（及错误响应体摘要）的 IOException。</p>
     *
     * @param url 目标地址，仅支持 http/https，不能为 null 或空白串
     * @param json 请求体 JSON 字符串，不能为 null（无业务参数时传 "{}"）
     * @param timeoutMillis 连接与读取超时（毫秒），必须大于 0
     * @return 响应体字符串（按 UTF-8 解码；响应体为空时返回空串），不会为 null
     * @throws IllegalArgumentException url 为 null 或空白串、json 为 null、
     *                                  timeoutMillis 小于等于 0 时抛出
     * @throws IOException 连接超时（消息 "connect timeout"）、读取超时（消息 "read timeout"）、
     *                    HTTP 状态码非 2xx（消息含状态码）或其它网络/IO 错误时抛出
     */
    static String postJson(String url, String json, int timeoutMillis) throws IOException {
        if (url == null || url.trim().length() == 0) {
            throw new IllegalArgumentException("url 不能为 null 或空白串");
        }
        if (json == null) {
            throw new IllegalArgumentException("json 不能为 null");
        }
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis 必须大于 0，单位毫秒");
        }
        HttpURLConnection connection = null;
        // 标记连接是否已建立：用于区分 connect timeout 与 read timeout
        boolean connected = false;
        try {
            URLConnection raw = new URL(url).openConnection();
            if (!(raw instanceof HttpURLConnection)) {
                throw new IOException("仅支持 http/https 协议: " + url);
            }
            connection = (HttpURLConnection) raw;
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
                output.flush();
            }
            connected = true;
            int status = connection.getResponseCode();
            boolean successStatus = status >= 200 && status < 300;
            String response = readBody(successStatus ? connection.getInputStream() : connection.getErrorStream());
            if (!successStatus) {
                throw new IOException("HTTP 状态码 " + status + (response.length() == 0 ? "" : ": " + response));
            }
            return response;
        } catch (SocketTimeoutException e) {
            throw new IOException(connected ? "read timeout" : "connect timeout", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 读取响应体流并按 UTF-8 解码为字符串。
     *
     * @param stream 响应体输入流；为 null 时返回空串（部分实现无错误响应体）
     * @return 响应体字符串，不会为 null
     * @throws IOException 读取失败或读取超时（由上层归类为 read timeout）时抛出
     */
    private static String readBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream input = stream; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
