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
 * @since 2026-08-25
 */

package com.xingheyiye.xingye.kit.net;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * 基于 JDK 原生 HttpURLConnection 的 HTTP 执行器，把 {@link HttpRequest} 真正发送出去并产出 {@link HttpResponse}。
 *
 * <p>适用场景：中小体积的 API 调用、简单文件上传、需要统一超时/重试/追踪的小型服务与工具程序；
 * <b>不适合超大文件下载——响应体会被完整读入内存</b>，下载数百 MB 级文件请改用流式方案。</p>
 *
 * <p>线程安全性：实例仅持有不可变配置字段，无会话状态，可被多线程共享并发调用；
 * 静态方法 {@link #send(HttpRequest)} 委托给一个全局共享默认实例。</p>
 *
 * <p>主要行为约定：</p>
 * <ul>
 *   <li>超时与重试：请求未显式设置时使用实例默认值；仅对
 *       {@link HttpErrorType#CONNECT_TIMEOUT} 与 {@link HttpErrorType#CONNECTION_ERROR} 重试，
 *       指数退避 200ms * 2^n（n 为已重试次数），封顶 2000ms；</li>
 *   <li>重定向：手动跟随（关闭 HttpURLConnection 自动跳转以便区分超时阶段），最多 5 次，
 *       超出报 {@link HttpErrorType#TOO_MANY_REDIRECTS}；301/302/303 按业界惯例改写为 GET 并丢弃请求体；</li>
 *   <li>gzip：自动携带 Accept-Encoding: gzip，响应 Content-Encoding 含 gzip 时透明解压；</li>
 *   <li>认证与追踪：支持 Basic/Bearer；traceId 未提供时自动生成随机 UUID，
 *       写入 X-Trace-Id 请求头并回填到响应；</li>
 *   <li>任何网络错误都不抛异常，而是返回带 {@code errorType} 的 {@link HttpResponse}。</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * HttpTool http = new HttpTool(3000, 5000, 2);
 * HttpResponse response = http.execute(
 *         HttpRequest.post("https://api.example.com/orders")
 *                 .json("{\"sku\": \"A-1\"}")
 *                 .bearerToken("token")
 *                 .build());
 * if (response.isSuccess()) {
 *     System.out.println(response.getBodyText());
 * } else {
 *     System.err.println(response.getErrorType() + ": " + response.getErrorMessage());
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-25
 */
public final class HttpTool {

    /** 默认连接超时：5000 毫秒。 */
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5000;
    /** 默认读取超时：10000 毫秒。 */
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 10000;
    /** 默认失败重试次数（不含首次请求）：1 次。 */
    private static final int DEFAULT_MAX_RETRY = 1;
    /** 手动跟随重定向的次数上限。 */
    private static final int MAX_REDIRECTS = 5;
    /** 重试指数退避基数：200 毫秒（首次重试等待 200ms，其后 400/800/1600ms）。 */
    private static final long INITIAL_BACKOFF_MILLIS = 200L;
    /** 重试退避上限：2000 毫秒。 */
    private static final long MAX_BACKOFF_MILLIS = 2000L;
    /** 响应体读取/复制缓冲大小：8192 字节。 */
    private static final int BUFFER_SIZE = 8192;
    /** 客户端/服务端错误状态码下界：达到该值时响应体需改从 getErrorStream() 读取。 */
    private static final int CLIENT_ERROR_MIN = 400;
    /** 纳秒到毫秒的换算系数。 */
    private static final long NANOS_PER_MILLIS = 1000000L;
    /** 表单提交（无文件时）使用的 Content-Type。 */
    private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=UTF-8";
    /** 原始请求体未显式声明 Content-Type 时使用的缺省值。 */
    private static final String DEFAULT_BINARY_CONTENT_TYPE = "application/octet-stream";
    /** multipart 边界前缀：拼接随机 UUID，保证边界不与正文内容冲突。 */
    private static final String MULTIPART_BOUNDARY_PREFIX = "xingyekit-";

    /** 全局共享默认实例（仅含不可变配置，线程安全）。 */
    private static final HttpTool DEFAULT = new HttpTool();

    /** 实例默认连接超时（毫秒），供未显式设置的请求兜底。 */
    private final int connectTimeoutMillis;
    /** 实例默认读取超时（毫秒），供未显式设置的请求兜底。 */
    private final int readTimeoutMillis;
    /** 实例默认失败重试次数，供未显式设置的请求兜底。 */
    private final int maxRetry;

    /**
     * 创建使用默认配置（连接超时 5 秒、读取超时 10 秒、重试 1 次）的执行器。
     */
    public HttpTool() {
        this(DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS, DEFAULT_MAX_RETRY);
    }

    /**
     * 创建自定义超时与重试配置的执行器。
     *
     * @param connectTimeoutMillis 默认连接超时（毫秒），必须大于 0
     * @param readTimeoutMillis 默认读取超时（毫秒），必须大于 0
     * @param maxRetry 默认失败重试次数（不含首次请求，0 表示不重试），必须不小于 0
     * @throws IllegalArgumentException 任一参数越界
     */
    public HttpTool(int connectTimeoutMillis, int readTimeoutMillis, int maxRetry) {
        if (connectTimeoutMillis <= 0) {
            throw new IllegalArgumentException("connectTimeoutMillis 必须大于 0: " + connectTimeoutMillis);
        }
        if (readTimeoutMillis <= 0) {
            throw new IllegalArgumentException("readTimeoutMillis 必须大于 0: " + readTimeoutMillis);
        }
        if (maxRetry < 0) {
            throw new IllegalArgumentException("maxRetry 不能为负: " + maxRetry);
        }
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.maxRetry = maxRetry;
    }

    /**
     * 使用全局默认实例发送请求（等价于 {@code new HttpTool().execute(request)}）。
     *
     * @param request 请求对象，不可为 null
     * @return 响应对象，永不为 null；网络错误以 {@code errorType} 表达，不抛异常
     * @throws NullPointerException request 为 null
     */
    public static HttpResponse send(HttpRequest request) {
        return DEFAULT.execute(request);
    }

    /**
     * 执行请求：请求级配置优先，未设置项回退到本实例默认值；任何网络错误都返回带
     * {@code errorType} 的响应而非抛出异常。
     *
     * @param request 请求对象，不可为 null（建议先经 {@code build()} 校验）
     * @return 响应对象，永不为 null；失败时 {@code isSuccess()} 为 false 且 {@code getErrorType()} 指明原因
     * @throws NullPointerException request 为 null
     */
    public HttpResponse execute(HttpRequest request) {
        Objects.requireNonNull(request, "request 不能为 null");
        long startNanos = System.nanoTime();
        // traceId：请求未提供则生成随机 UUID，保证每次调用可追踪
        String traceId = request.getTraceId() != null ? request.getTraceId() : UUID.randomUUID().toString();
        URL target;
        try {
            target = new URL(request.getFullUrl());
        } catch (MalformedURLException e) {
            return HttpResponse.error(HttpErrorType.INVALID_URL, "无效的 URL: " + request.getFullUrl(),
                    elapsedMillis(startNanos), traceId);
        }
        int retryBudget = request.getMaxRetry() != null ? request.getMaxRetry().intValue() : this.maxRetry;
        int retried = 0;
        while (true) {
            try {
                return exchangeFollowingRedirects(target, request, traceId, startNanos);
            } catch (AttemptFailure failure) {
                boolean retryable = failure.errorType == HttpErrorType.CONNECT_TIMEOUT
                        || failure.errorType == HttpErrorType.CONNECTION_ERROR;
                if (retryable && retried < retryBudget) {
                    try {
                        sleepBackoff(retried);
                    } catch (InterruptedException e) {
                        // 重试等待被中断：恢复中断标记后以 INTERRUPTED 错误返回，不继续重试
                        Thread.currentThread().interrupt();
                        return HttpResponse.error(HttpErrorType.INTERRUPTED, "重试等待被中断",
                                elapsedMillis(startNanos), traceId);
                    }
                    retried++;
                    continue;
                }
                return HttpResponse.error(failure.errorType, failure.getMessage(),
                        elapsedMillis(startNanos), traceId);
            }
        }
    }

    // ------------------------------------------------------------------
    // 交换与重定向
    // ------------------------------------------------------------------

    /**
     * 在单次尝试内完成"请求-响应"并手动跟随最多 {@link #MAX_REDIRECTS} 次重定向。
     */
    private HttpResponse exchangeFollowingRedirects(URL url, HttpRequest request, String traceId, long startNanos)
            throws AttemptFailure {
        URL current = url;
        HttpRequest effective = request;
        int redirects = 0;
        while (true) {
            RoundTrip round = roundTrip(current, effective, traceId);
            if (!isRedirectCode(round.statusCode)) {
                return HttpResponse.ok(round.statusCode, round.headers, round.body,
                        elapsedMillis(startNanos), traceId);
            }
            String location = round.getFirstHeader("Location");
            if (location == null) {
                // 缺少 Location 的 3xx 无法跟随，按普通响应原样返回
                return HttpResponse.ok(round.statusCode, round.headers, round.body,
                        elapsedMillis(startNanos), traceId);
            }
            if (redirects >= MAX_REDIRECTS) {
                throw new AttemptFailure(HttpErrorType.TOO_MANY_REDIRECTS,
                        "重定向次数超过上限 " + MAX_REDIRECTS + " 次，最后指向: " + location);
            }
            redirects++;
            try {
                current = new URL(current, location); // 同时支持相对与绝对 Location
            } catch (MalformedURLException e) {
                throw new AttemptFailure(HttpErrorType.INVALID_URL, "重定向地址非法: " + location, e);
            }
            if (round.statusCode == 301 || round.statusCode == 302 || round.statusCode == 303) {
                // 301/302/303 按浏览器惯例改写为 GET 并丢弃请求体；307/308 严格保持原方法与请求体
                effective = effective.withoutBodyAsGet();
            }
        }
    }

    /**
     * 完成一次 HTTP 往返（建连、发送、读取响应），不做重定向跟随。
     */
    private RoundTrip roundTrip(URL url, HttpRequest request, String traceId) throws AttemptFailure {
        // 组装请求体放在建连之前：上传文件不可读等调用方错误不应占用连接资源
        OutgoingBody outgoing;
        try {
            outgoing = composeBody(request);
        } catch (IOException e) {
            throw new AttemptFailure(HttpErrorType.UNKNOWN, "组装请求体失败: " + messageOf(e), e);
        }
        HttpURLConnection connection = null;
        boolean connected = false; // 是否已成功建立连接：用于区分连接超时与读取超时
        try {
            connection = open(url, request);
            // 关闭自动重定向，由 exchangeFollowingRedirects 手动跟随，以便精确计数并区分超时阶段
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(request.getMethod());
            connection.setConnectTimeout(request.getConnectTimeoutMillis() != null
                    ? request.getConnectTimeoutMillis().intValue() : connectTimeoutMillis);
            connection.setReadTimeout(request.getReadTimeoutMillis() != null
                    ? request.getReadTimeoutMillis().intValue() : readTimeoutMillis);
            applyRequestHeaders(connection, request, traceId, outgoing == null ? null : outgoing.contentType);
            if (outgoing != null) {
                // 注意：HttpURLConnection 在 doOutput 且方法为 GET/HEAD 时会强制把方法改为 POST
                connection.setDoOutput(true);
            }
            connection.connect(); // 显式建连：此阶段抛出的 SocketTimeoutException 即连接超时
            connected = true;
            if (outgoing != null) {
                OutputStream output = connection.getOutputStream();
                try {
                    output.write(outgoing.bytes);
                    output.flush();
                } finally {
                    closeQuietly(output);
                }
            }
            int statusCode = connection.getResponseCode();
            Map<String, List<String>> headers = copyResponseHeaders(connection);
            byte[] body = readFully(openBodyStream(connection, statusCode));
            return new RoundTrip(statusCode, headers, body);
        } catch (SocketTimeoutException e) {
            // JDK 的 SocketTimeoutException 不携带“连接阶段/读取阶段”标志，
            // 因此人为划界：connect() 成功之前视为连接超时，之后视为读取超时
            throw new AttemptFailure(
                    connected ? HttpErrorType.READ_TIMEOUT : HttpErrorType.CONNECT_TIMEOUT,
                    messageOf(e), e);
        } catch (InterruptedIOException e) {
            // 非超时类中断（如阻塞 IO 被线程中断打断）
            throw new AttemptFailure(HttpErrorType.INTERRUPTED, messageOf(e), e);
        } catch (ProtocolException e) {
            // 例如方法不被 HttpURLConnection 支持等协议级问题
            throw new AttemptFailure(HttpErrorType.UNKNOWN, messageOf(e), e);
        } catch (IOException e) {
            // 拒绝连接、未知主机、网络不可达、连接被重置等
            throw new AttemptFailure(HttpErrorType.CONNECTION_ERROR, messageOf(e), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // ------------------------------------------------------------------
    // 连接与请求头
    // ------------------------------------------------------------------

    /**
     * 打开连接：请求配置了代理则经 HTTP 代理，否则直连（沿用 JVM 系统代理设置语义）。
     */
    private static HttpURLConnection open(URL url, HttpRequest request) throws IOException {
        if (request.getProxyHost() != null) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress(request.getProxyHost(), request.getProxyPort().intValue()));
            return (HttpURLConnection) url.openConnection(proxy);
        }
        return (HttpURLConnection) url.openConnection();
    }

    /**
     * 写入请求头：调用方头优先，其次认证头（Bearer 优先于 Basic）与工具生成的头。
     */
    private static void applyRequestHeaders(HttpURLConnection connection, HttpRequest request,
            String traceId, String contentType) {
        boolean userSetAcceptEncoding = false;
        boolean userSetAuthorization = false;
        boolean userSetContentType = false;
        for (String[] header : request.getHeaders()) {
            if ("Accept-Encoding".equalsIgnoreCase(header[0])) {
                userSetAcceptEncoding = true;
            }
            if ("Authorization".equalsIgnoreCase(header[0])) {
                userSetAuthorization = true;
            }
            if ("Content-Type".equalsIgnoreCase(header[0])) {
                userSetContentType = true;
            }
            connection.addRequestProperty(header[0], header[1]);
        }
        // 主动协商 gzip；服务端真返回 gzip 时由本工具统一解压，对调用方透明
        if (!userSetAcceptEncoding) {
            connection.setRequestProperty("Accept-Encoding", "gzip");
        }
        if (!userSetAuthorization) {
            if (request.getBearerToken() != null) {
                connection.setRequestProperty("Authorization", "Bearer " + request.getBearerToken());
            } else if (request.getBasicUser() != null) {
                String credentials = request.getBasicUser() + ":" + request.getBasicPassword();
                String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                connection.setRequestProperty("Authorization", "Basic " + encoded);
            }
        }
        // 请求体 Content-Type：仅在调用方未显式指定时由本工具决定
        if (contentType != null && !userSetContentType) {
            connection.setRequestProperty("Content-Type", contentType);
        }
        connection.setRequestProperty("X-Trace-Id", traceId);
    }

    // ------------------------------------------------------------------
    // 请求体组装
    // ------------------------------------------------------------------

    /**
     * 组装请求体：优先级为 文件（multipart）&gt; 原始请求体 &gt; 表单字段；均无则返回 null。
     */
    private static OutgoingBody composeBody(HttpRequest request) throws IOException {
        if (!request.getFiles().isEmpty()) {
            // 只要包含文件部件，整个请求按 multipart/form-data 编码（表单字段作为普通部件混入）
            String boundary = MULTIPART_BOUNDARY_PREFIX + UUID.randomUUID().toString();
            byte[] bytes = encodeMultipartBody(request, boundary);
            return new OutgoingBody(bytes, "multipart/form-data; boundary=" + boundary);
        }
        byte[] rawBody = request.getBody();
        if (rawBody != null) {
            String contentType = request.getBodyContentType() != null
                    ? request.getBodyContentType() : DEFAULT_BINARY_CONTENT_TYPE;
            return new OutgoingBody(rawBody, contentType);
        }
        if (!request.getFormFields().isEmpty()) {
            return new OutgoingBody(encodeFormUrlEncoded(request.getFormFields()), FORM_CONTENT_TYPE);
        }
        return null;
    }

    /**
     * 按 multipart/form-data 编码表单字段与文件部件。
     */
    private static byte[] encodeMultipartBody(HttpRequest request, String boundary) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : request.getFormFields().entrySet()) {
            writeUtf8(buffer, "--" + boundary + "\r\n");
            writeUtf8(buffer, "Content-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n");
            writeUtf8(buffer, field.getValue());
            writeUtf8(buffer, "\r\n");
        }
        for (HttpRequest.FilePart part : request.getFiles()) {
            File file = part.getFile();
            writeUtf8(buffer, "--" + boundary + "\r\n");
            writeUtf8(buffer, "Content-Disposition: form-data; name=\"" + part.getFieldName()
                    + "\"; filename=\"" + file.getName() + "\"\r\n");
            writeUtf8(buffer, "Content-Type: application/octet-stream\r\n\r\n");
            try (FileInputStream input = new FileInputStream(file)) { // 文件不存在时抛 FileNotFoundException
                copyStreams(input, buffer);
            }
            writeUtf8(buffer, "\r\n");
        }
        writeUtf8(buffer, "--" + boundary + "--\r\n");
        return buffer.toByteArray();
    }

    /**
     * 按 application/x-www-form-urlencoded 编码表单字段。
     */
    private static byte[] encodeFormUrlEncoded(Map<String, String> formFields) throws UnsupportedEncodingException {
        StringBuilder encoded = new StringBuilder();
        for (Map.Entry<String, String> field : formFields.entrySet()) {
            if (encoded.length() > 0) {
                encoded.append('&');
            }
            // 表单编码语义下空格编码为 '+' 恰是规范要求，与查询串（%20）不同，故不做替换
            encoded.append(URLEncoder.encode(field.getKey(), "UTF-8"))
                    .append('=')
                    .append(URLEncoder.encode(field.getValue(), "UTF-8"));
        }
        return encoded.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 向内存缓冲写入 UTF-8 文本（ByteArrayOutputStream 的三参 write 不抛受检异常）。
     */
    private static void writeUtf8(ByteArrayOutputStream buffer, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        buffer.write(bytes, 0, bytes.length);
    }

    // ------------------------------------------------------------------
    // 响应读取
    // ------------------------------------------------------------------

    /**
     * 复制连接的响应头映射：剔除 null 键（状态行占位）并对每个值列表做副本。
     */
    private static Map<String, List<String>> copyResponseHeaders(HttpURLConnection connection) {
        Map<String, List<String>> copy = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            List<String> values = entry.getValue();
            copy.put(entry.getKey(), values == null
                    ? Collections.<String>emptyList()
                    : new ArrayList<String>(values));
        }
        return copy;
    }

    /**
     * 打开响应体输入流：4xx/5xx 从 getErrorStream() 读取；gzip 响应透明解压。
     */
    private static InputStream openBodyStream(HttpURLConnection connection, int statusCode) throws IOException {
        InputStream stream = statusCode >= CLIENT_ERROR_MIN
                ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return null; // 无响应体（如 HEAD 或 204）
        }
        BufferedInputStream buffered = new BufferedInputStream(stream, BUFFER_SIZE);
        if (isGzipEncoded(connection.getContentEncoding())) {
            return new GZIPInputStream(buffered);
        }
        return buffered;
    }

    /**
     * 把响应体完整读入内存（本工具不支持流式消费，超大文件请勿使用）。
     */
    private static byte[] readFully(InputStream stream) throws IOException {
        if (stream == null) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = stream) {
            copyStreams(in, out);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    /**
     * 以 8192 字节缓冲做流复制。
     */
    private static void copyStreams(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    /**
     * 判断状态码是否为需要跟随 Location 的重定向码（301/302/303/307/308）。
     */
    private static boolean isRedirectCode(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303
                || statusCode == 307 || statusCode == 308;
    }

    /**
     * 判断 Content-Encoding 是否声明了 gzip（大小写不敏感）。
     */
    private static boolean isGzipEncoded(String contentEncoding) {
        return contentEncoding != null && contentEncoding.toLowerCase(Locale.ROOT).contains("gzip");
    }

    /**
     * 计算自 startNanos 起的耗时（毫秒），基于 System.nanoTime，不受系统时钟跳变影响。
     */
    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / NANOS_PER_MILLIS;
    }

    /**
     * 提取异常消息，消息为空时退化为异常类名。
     */
    private static String messageOf(IOException e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }

    /**
     * 静默关闭流：读取响应过程中的关闭失败不应掩盖主流程结果。
     */
    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            // 忽略：关闭失败不影响已获取的响应
        }
    }

    /**
     * 重试前休眠：指数退避 200ms * 2^retried，封顶 2000ms。
     */
    private static void sleepBackoff(int retried) throws InterruptedException {
        long delay = INITIAL_BACKOFF_MILLIS * (1L << retried);
        if (delay > MAX_BACKOFF_MILLIS) {
            delay = MAX_BACKOFF_MILLIS;
        }
        Thread.sleep(delay);
    }

    /**
     * 内部请求体载体：已编码的字节与其 Content-Type。
     */
    private static final class OutgoingBody {

        /** 请求体字节（构造后不再修改）。 */
        private final byte[] bytes;
        /** 请求体 Content-Type。 */
        private final String contentType;

        private OutgoingBody(byte[] bytes, String contentType) {
            this.bytes = bytes;
            this.contentType = contentType;
        }
    }

    /**
     * 单次 HTTP 往返的原始结果（未经 HttpResponse 包装）。
     */
    private static final class RoundTrip {

        /** HTTP 状态码。 */
        private final int statusCode;
        /** 响应头（键保留服务端原始大小写）。 */
        private final Map<String, List<String>> headers;
        /** 响应体字节。 */
        private final byte[] body;

        private RoundTrip(int statusCode, Map<String, List<String>> headers, byte[] body) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.body = body;
        }

        /**
         * 大小写不敏感地获取首个指定头的取值，不存在返回 null。
         */
        private String getFirstHeader(String name) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (name.equalsIgnoreCase(entry.getKey())) {
                    List<String> values = entry.getValue();
                    return values == null || values.isEmpty() ? null : values.get(0);
                }
            }
            return null;
        }
    }

    /**
     * 内部控制流异常：把失败从深层 IO 代码解到 execute() 顶层，附带错误分类，不对外暴露。
     */
    private static final class AttemptFailure extends RuntimeException {

        /** 失败分类。 */
        private final HttpErrorType errorType;

        private AttemptFailure(HttpErrorType errorType, String message) {
            super(message);
            this.errorType = errorType;
        }

        private AttemptFailure(HttpErrorType errorType, String message, Throwable cause) {
            super(message, cause);
            this.errorType = errorType;
        }
    }
}
