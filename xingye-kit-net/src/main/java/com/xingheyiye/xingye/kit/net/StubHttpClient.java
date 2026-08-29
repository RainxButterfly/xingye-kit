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
package com.xingheyiye.xingye.kit.net;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地联调/测试用的 HTTP 桩实现：不发任何真实网络请求，按注册的预设响应直接返回。
 *
 * <p>本类是 {@link HttpClient} 接口的内置实现（{@link HttpClient} 的可替换选择之一），
 * 与真实实现 {@link HttpTool} 形成“联调桩 + 真实实现”的成对选择：本地联调、单元测试、
 * 演示代码里用它替代真实 HTTP，可在无网络、无下游服务的环境下稳定运行。</p>
 *
 * <p>核心能力：</p>
 * <ul>
 *   <li>按 URL 精确注册成功响应（状态码 + 文本体，可带自定义响应头）或网络错误响应；</li>
 *   <li>可注册默认兜底响应，覆盖未精确注册的任意请求；</li>
 *   <li>记录每次已执行的请求（方法 / URL / 头 / 体），供断言“请求是否正确发出”；</li>
 *   <li>请求 {@code X-Trace-Id} 自动回填到响应，与 {@link HttpTool} 语义一致。</li>
 * </ul>
 *
 * <p>匹配规则：先按完整 URL 精确匹配；未命中且设置了默认响应时返回默认响应；
 * 未命中且未设置默认响应时返回 500（可视为“未注册”的显式失败）。</p>
 *
 * <p>线程安全性：内部使用并发容器，注册与执行可被多线程并发调用；
 * {@link #getRequests()} 返回不可变快照，不会因并发执行而抛并发修改异常。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * StubHttpClient stub = new StubHttpClient();
 * stub.stub("https://api.example.com/users/1", 200, "{\"name\":\"alice\"}");
 * stub.stubError("https://api.example.com/pay", HttpErrorType.CONNECT_TIMEOUT, "模拟超时");
 *
 * HttpClient client = stub; // 业务代码面向 HttpClient 接口不变
 * HttpResponse ok = client.execute(HttpRequest.get("https://api.example.com/users/1").build());
 * System.out.println(ok.getBodyText());   // {"name":"alice"}
 *
 * assertEquals(1, stub.getRequestCount());
 * assertEquals("GET", stub.getRequests().get(0).getMethod());
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public class StubHttpClient implements HttpClient {

    /** 未命中任何桩且未设置默认响应时的兜底状态码（500 表示“未注册”）。 */
    private static final int UNSTUBBED_STATUS = 500;
    /** 未命中任何桩且未设置默认响应时的兜底错误描述。 */
    private static final String UNSTUBBED_MESSAGE = "StubHttpClient: 该 URL 未注册预设响应: ";
    /** 成功桩的默认 Content-Type 响应头。 */
    private static final String CONTENT_TYPE_JSON = "application/json";

    /** 按完整 URL 精确匹配的预设响应。 */
    private final Map<String, Stub> stubs = new ConcurrentHashMap<String, Stub>();
    /** 已执行的请求历史（线程安全追加）。 */
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<RecordedRequest>();
    /** 已执行请求总数。 */
    private final AtomicLong requestCount = new AtomicLong();
    /** 未精确命中时的默认响应；null 表示未设置。 */
    private volatile Stub defaultStub;

    /**
     * 按完整 URL 注册成功响应（状态码 + 文本体，按 UTF-8 编码，响应头为 JSON Content-Type）。
     *
     * @param url 与 {@link HttpRequest#getFullUrl()} 精确匹配的 URL，不可为 null 或空白串
     * @param statusCode HTTP 状态码（如 200/404/500），任意正整数
     * @param body 响应体文本，可为 null（视为空响应体）
     * @return 本实例（链式调用）
     * @throws IllegalArgumentException url 非法或 statusCode 非正数
     */
    public StubHttpClient stub(String url, int statusCode, String body) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", CONTENT_TYPE_JSON);
        return stub(url, statusCode, body, headers);
    }

    /**
     * 按完整 URL 注册成功响应（可自定义响应头）。
     *
     * @param url 与 {@link HttpRequest#getFullUrl()} 精确匹配的 URL，不可为 null 或空白串
     * @param statusCode HTTP 状态码，任意正整数
     * @param body 响应体文本，可为 null（视为空响应体）
     * @param headers 附加响应头，可为 null（等价于不附加额外头）；键/值不可为 null
     * @return 本实例（链式调用）
     * @throws IllegalArgumentException url 非法或 statusCode 非正数
     */
    public StubHttpClient stub(String url, int statusCode, String body, Map<String, String> headers) {
        checkUrl(url);
        checkStatusCode(statusCode);
        Map<String, List<String>> headerMap = toHeaderMap(headers);
        stubs.put(url, new Stub(true, statusCode, body, headerMap, null, null));
        return this;
    }

    /**
     * 按完整 URL 注册网络错误响应（用于模拟超时/连接失败等，不产生 HTTP 状态码）。
     *
     * @param url 与 {@link HttpRequest#getFullUrl()} 精确匹配的 URL，不可为 null 或空白串
     * @param errorType 错误分类，不可为 null 且不应为 {@link HttpErrorType#NONE}
     * @param message 错误描述，可为 null
     * @return 本实例（链式调用）
     * @throws IllegalArgumentException url 非法或 errorType 为 null/NONE
     */
    public StubHttpClient stubError(String url, HttpErrorType errorType, String message) {
        checkUrl(url);
        checkErrorType(errorType);
        stubs.put(url, new Stub(false, 0, null, Collections.<String, List<String>>emptyMap(),
                errorType, message));
        return this;
    }

    /**
     * 注册默认成功响应：未精确命中的任意请求都返回该响应。
     *
     * @param statusCode HTTP 状态码，任意正整数
     * @param body 响应体文本，可为 null（视为空响应体）
     * @return 本实例（链式调用）
     * @throws IllegalArgumentException statusCode 非正数
     */
    public StubHttpClient stubDefault(int statusCode, String body) {
        checkStatusCode(statusCode);
        Map<String, List<String>> headerMap = new LinkedHashMap<String, List<String>>();
        headerMap.put("Content-Type", Collections.singletonList(CONTENT_TYPE_JSON));
        defaultStub = new Stub(true, statusCode, body, headerMap, null, null);
        return this;
    }

    /**
     * 注册默认网络错误响应：未精确命中的任意请求都返回该错误。
     *
     * @param errorType 错误分类，不可为 null 且不应为 {@link HttpErrorType#NONE}
     * @param message 错误描述，可为 null
     * @return 本实例（链式调用）
     * @throws IllegalArgumentException errorType 为 null/NONE
     */
    public StubHttpClient stubDefaultError(HttpErrorType errorType, String message) {
        checkErrorType(errorType);
        defaultStub = new Stub(false, 0, null, Collections.<String, List<String>>emptyMap(),
                errorType, message);
        return this;
    }

    /**
     * 清除全部预设响应与请求记录（状态复位，便于在多个测试用例间复用同一实例）。
     *
     * @return 本实例（链式调用）
     */
    public StubHttpClient reset() {
        stubs.clear();
        defaultStub = null;
        requests.clear();
        requestCount.set(0L);
        return this;
    }

    /**
     * 记录一次已执行的请求（供断言使用）。
     *
     * @param request 已执行的请求描述，不可为 null
     */
    private void record(HttpRequest request) {
        requestCount.incrementAndGet();
        requests.add(new RecordedRequest(request));
    }

    /**
     * 执行一次请求：按 URL 精确匹配预设响应，未命中回退默认响应。
     *
     * @param request 请求描述，不能为 null
     * @return 响应模型，永不为 null
     * @throws IllegalArgumentException request 为 null
     */
    @Override
    public HttpResponse execute(HttpRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为 null");
        }
        record(request);
        String traceId = request.getTraceId();
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }
        Stub stub = stubs.get(request.getFullUrl());
        if (stub == null) {
            stub = defaultStub;
        }
        if (stub == null) {
            // 未注册且无默认：返回显式 500，避免静默返回成功造成误判
            return HttpResponse.error(HttpErrorType.UNKNOWN, UNSTUBBED_MESSAGE + request.getFullUrl(),
                    0L, traceId);
        }
        if (stub.success) {
            byte[] body = stub.body == null ? new byte[0] : stub.body.getBytes(StandardCharsets.UTF_8);
            return HttpResponse.ok(stub.statusCode, stub.headers, body, 0L, traceId);
        }
        return HttpResponse.error(stub.errorType, stub.errorMessage, 0L, traceId);
    }

    /**
     * @return 已执行请求总数
     */
    public long getRequestCount() {
        return requestCount.get();
    }

    /**
     * @return 已执行请求的不可变快照（按执行顺序）；永不为 null（无请求时为空列表）
     */
    public List<RecordedRequest> getRequests() {
        return Collections.unmodifiableList(new ArrayList<RecordedRequest>(requests));
    }

    /**
     * 校验 URL 参数。
     */
    private static void checkUrl(String url) {
        if (url == null || url.trim().length() == 0) {
            throw new IllegalArgumentException("url 不能为 null 或空白串");
        }
    }

    /**
     * 校验状态码参数。
     */
    private static void checkStatusCode(int statusCode) {
        if (statusCode <= 0) {
            throw new IllegalArgumentException("statusCode 必须为正数: " + statusCode);
        }
    }

    /**
     * 校验错误类型参数。
     */
    private static void checkErrorType(HttpErrorType errorType) {
        if (errorType == null || errorType == HttpErrorType.NONE) {
            throw new IllegalArgumentException("errorType 不能为 null 或 NONE");
        }
    }

    /**
     * 把单值头映射转换为 {@link HttpResponse#ok} 需要的多值头映射（不可变）。
     */
    private static Map<String, List<String>> toHeaderMap(Map<String, String> headers) {
        Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    throw new IllegalArgumentException("响应头键与值不可为 null");
                }
                result.put(entry.getKey(), Collections.singletonList(entry.getValue()));
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 单条预设响应的不可变描述（成功响应或网络错误二选一）。
     */
    private static final class Stub {

        /** 是否为成功响应（true 时使用 statusCode/body/headers；false 时使用 errorType/errorMessage）。 */
        private final boolean success;
        /** 成功响应的状态码。 */
        private final int statusCode;
        /** 成功响应的响应体文本（UTF-8 编码发送）。 */
        private final String body;
        /** 成功响应的响应头。 */
        private final Map<String, List<String>> headers;
        /** 网络错误分类。 */
        private final HttpErrorType errorType;
        /** 网络错误描述。 */
        private final String errorMessage;

        Stub(boolean success, int statusCode, String body, Map<String, List<String>> headers,
                HttpErrorType errorType, String errorMessage) {
            this.success = success;
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;
            this.errorType = errorType;
            this.errorMessage = errorMessage;
        }
    }

    /**
     * 已执行请求的不可变快照：暴露方法/完整 URL/头/体，供测试断言“请求是否正确发出”。
     *
     * <p>线程安全性：字段在构造时定稿，线程安全。</p>
     *
     * @author 星河一叶 (RainxButterfly)
     * @since 2026-08-30
     */
    public static final class RecordedRequest {

        /** HTTP 方法。 */
        private final String method;
        /** 完整 URL（含查询参数）。 */
        private final String fullUrl;
        /** 请求头（保持添加顺序）。 */
        private final List<String[]> headers;
        /** 请求体文本（UTF-8）；无请求体时为 null。 */
        private final String body;

        private RecordedRequest(HttpRequest request) {
            this.method = request.getMethod();
            this.fullUrl = request.getFullUrl();
            this.headers = request.getHeaders();
            byte[] bytes = request.getBody();
            this.body = bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
        }

        /**
         * @return HTTP 方法，永不为 null
         */
        public String getMethod() {
            return method;
        }

        /**
         * @return 完整 URL（含查询参数），永不为 null
         */
        public String getFullUrl() {
            return fullUrl;
        }

        /**
         * @return 请求头的不可变副本，保持添加顺序；永不为 null（可为空列表）
         */
        public List<String[]> getHeaders() {
            List<String[]> copy = new ArrayList<String[]>(headers.size());
            for (String[] pair : headers) {
                copy.add(pair.clone());
            }
            return Collections.unmodifiableList(copy);
        }

        /**
         * @return 请求体文本（UTF-8 解码）；无请求体时为 null
         */
        public String getBody() {
            return body;
        }
    }
}
