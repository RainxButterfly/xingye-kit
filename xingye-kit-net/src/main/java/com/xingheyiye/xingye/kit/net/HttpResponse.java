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
 * @since 2026-08-24
 */

package com.xingheyiye.xingye.kit.net;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 不可变的 HTTP 响应对象，同时承载成功响应与网络错误信息。
 *
 * <p>设计说明：网络错误（超时、连接失败、重定向过多等）<b>不</b>以异常抛出，
 * 而是返回一个携带 {@link HttpErrorType} 与 {@code errorMessage} 的响应对象，
 * 让调用方用统一的 {@code if/else} 处理"业务结果"与"网络结果"，避免深嵌套的 try/catch，
 * 也便于批量调用场景的统一收口。</p>
 *
 * <p>适用场景：配合 {@code HttpTool} 使用；也可在自定义 HTTP 封装中作为统一的返回类型。</p>
 *
 * <p>线程安全性：字段全部为 final 且对外只暴露不可变副本（{@code getBody()} 返回克隆、
 * {@code getHeaders()} 返回不可变深拷贝），线程安全。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * HttpResponse response = HttpTool.send(HttpRequest.get("https://example.com/api").build());
 * if (response.isSuccess()) {
 *     System.out.println(response.getStatusCode() + ": " + response.getBodyText());
 * } else {
 *     System.err.println("调用失败: " + response.getErrorType() + " / " + response.getErrorMessage());
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-24
 */
public final class HttpResponse {

    /** 网络错误（从未收到 HTTP 状态行）时使用的状态码哨兵值。 */
    public static final int NO_RESPONSE_CODE = -1;
    /** 2xx 成功状态码下界。 */
    private static final int SUCCESS_MIN = 200;
    /** 2xx 成功状态码上界。 */
    private static final int SUCCESS_MAX = 299;

    /** HTTP 状态码；网络错误时为 {@link #NO_RESPONSE_CODE}。 */
    private final int statusCode;
    /** 响应头（键保留服务端原始大小写）；永不为 null（可为空 Map）。 */
    private final Map<String, List<String>> headers;
    /** 响应体字节；永不为 null（无响应体时为空数组）。 */
    private final byte[] body;
    /** 响应体按 UTF-8 解码后的文本；永不为 null（无响应体时为空串）。 */
    private final String bodyText;
    /** 本次调用总耗时（毫秒），自发起请求起计。 */
    private final long elapsedMillis;
    /** 全链路追踪 ID；调用方未提供时为工具自动生成的 UUID。 */
    private final String traceId;
    /** 网络错误分类；成功时为 {@link HttpErrorType#NONE}，永不为 null。 */
    private final HttpErrorType errorType;
    /** 网络错误的人类可读描述；成功时为 null。 */
    private final String errorMessage;

    /**
     * 包私有构造：请通过 {@link #ok(int, Map, byte[], long, String)} 或
     * {@link #error(HttpErrorType, String, long, String)} 创建实例。
     *
     * @param statusCode HTTP 状态码；从未收到响应时传 {@link #NO_RESPONSE_CODE}
     * @param headers 响应头映射，可为 null（内部转为空 Map），null 键（状态行占位）会被剔除
     * @param body 响应体字节，可为 null（内部转为空数组），内部做防御性拷贝
     * @param elapsedMillis 总耗时毫秒数，应不小于 0
     * @param traceId 追踪 ID，可为 null
     * @param errorType 错误分类，不可为 null（成功传 {@link HttpErrorType#NONE}）
     * @param errorMessage 错误描述，可为 null
     */
    HttpResponse(int statusCode, Map<String, List<String>> headers, byte[] body, long elapsedMillis,
            String traceId, HttpErrorType errorType, String errorMessage) {
        Objects.requireNonNull(errorType, "errorType 不能为 null");
        this.statusCode = statusCode;
        this.headers = copyHeaders(headers);
        this.body = body == null ? new byte[0] : body.clone();
        this.bodyText = this.body.length == 0 ? "" : new String(this.body, StandardCharsets.UTF_8);
        this.elapsedMillis = elapsedMillis;
        this.traceId = traceId;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }

    /**
     * 创建成功（已收到 HTTP 响应）的响应对象。
     *
     * @param statusCode HTTP 状态码（如 200/404/500，均视为"成功收到响应"）
     * @param headers 响应头映射，可为 null；null 键（状态行占位）会被剔除
     * @param body 响应体字节，可为 null（视为空响应体）
     * @param elapsedMillis 本次调用总耗时（毫秒），应不小于 0
     * @param traceId 追踪 ID，可为 null
     * @return 新的不可变响应实例，永不为 null，{@code getErrorType()} 为 {@link HttpErrorType#NONE}
     */
    public static HttpResponse ok(int statusCode, Map<String, List<String>> headers, byte[] body,
            long elapsedMillis, String traceId) {
        return new HttpResponse(statusCode, headers, body, elapsedMillis, traceId, HttpErrorType.NONE, null);
    }

    /**
     * 创建失败的响应对象（未收到 HTTP 响应或调用被中断）。
     *
     * @param errorType 错误分类，不可为 null 且不应为 {@link HttpErrorType#NONE}
     * @param errorMessage 错误描述，可为 null（建议提供）
     * @param elapsedMillis 失败前已耗时（毫秒），应不小于 0
     * @param traceId 追踪 ID，可为 null
     * @return 新的不可变响应实例，永不为 null，{@code getStatusCode()} 为 {@link #NO_RESPONSE_CODE}
     * @throws NullPointerException errorType 为 null
     */
    public static HttpResponse error(HttpErrorType errorType, String errorMessage, long elapsedMillis, String traceId) {
        return new HttpResponse(NO_RESPONSE_CODE, null, null, elapsedMillis, traceId, errorType, errorMessage);
    }

    /**
     * 判断本次调用是否成功：收到 2xx 响应且未发生任何网络错误。
     *
     * @return 状态码在 [200, 299] 区间且 {@code getErrorType() == HttpErrorType.NONE} 时为 true
     */
    public boolean isSuccess() {
        return statusCode >= SUCCESS_MIN && statusCode <= SUCCESS_MAX && errorType == HttpErrorType.NONE;
    }

    /**
     * 大小写不敏感地获取首个指定响应头的取值。
     *
     * @param name 头名称（忽略大小写比较），可为 null（直接返回 null）
     * @return 该头的第一个取值；不存在时为 null
     */
    public String getFirstHeader(String name) {
        if (name == null) {
            return null;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && name.equalsIgnoreCase(entry.getKey())) {
                List<String> values = entry.getValue();
                return values == null || values.isEmpty() ? null : values.get(0);
            }
        }
        return null;
    }

    /**
     * @return HTTP 状态码；网络错误（从未收到响应）时为 {@link #NO_RESPONSE_CODE}
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * @return 响应头的不可变深拷贝（键保留服务端原始大小写，值列表同样不可变）；永不为 null（可为空 Map）
     */
    public Map<String, List<String>> getHeaders() {
        return copyHeaders(headers);
    }

    /**
     * @return 响应体字节的克隆副本；永不为 null（无响应体时为空数组）
     */
    public byte[] getBody() {
        return body.clone();
    }

    /**
     * @return 响应体按 UTF-8 解码后的文本；永不为 null（无响应体时为空串）。若响应为非 UTF-8 文本，解码结果不可靠
     */
    public String getBodyText() {
        return bodyText;
    }

    /**
     * @return 本次调用总耗时（毫秒，基于 System.nanoTime 计时），应不小于 0
     */
    public long getElapsedMillis() {
        return elapsedMillis;
    }

    /**
     * @return 追踪 ID（调用方提供或工具自动生成并回填）；未提供时可能为 null
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * @return 网络错误分类；成功时为 {@link HttpErrorType#NONE}，永不为 null
     */
    public HttpErrorType getErrorType() {
        return errorType;
    }

    /**
     * @return 网络错误描述；成功时为 null
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 对响应头做防御性深拷贝（剔除 null 键并保持原顺序），用于构造与对外暴露两个方向。
     */
    private static Map<String, List<String>> copyHeaders(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new LinkedHashMap<String, List<String>>();
        if (source == null) {
            return Collections.unmodifiableMap(copy);
        }
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue; // HttpURLConnection 的 getHeaderFields() 用 null 键承载状态行，剔除之
            }
            List<String> values = entry.getValue();
            copy.put(entry.getKey(), values == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(values)));
        }
        return Collections.unmodifiableMap(copy);
    }
}
