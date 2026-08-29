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

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 不可变的 HTTP 请求描述对象，同时自身充当建造者：每个配置方法都返回携带新配置的新实例，原实例保持不变。
 *
 * <p>适用场景：需要在多线程间安全共享、复用或渐进式定制请求描述的调用方，
 * 例如基于同一模板派生仅 traceId/query 不同的请求，或将请求放入集合缓存。</p>
 *
 * <p>线程安全性：实例一经创建即完全不可变（内部状态在构造瞬间定稿后不再被修改），
 * 可被多线程无锁共享；所有集合型 getter 返回不可变副本，外部修改不会影响本对象。</p>
 *
 * <p>编码约定：查询参数与表单字段一律按 UTF-8 编码；请求体字符集一律为 UTF-8。
 * 若同时设置了 {@link #body(String)}（或 {@link #json(String)}）与表单/文件，
 * 发送时的优先级为：文件（multipart）&gt; 原始请求体 &gt; 表单字段，详见 {@code HttpTool}。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * HttpRequest request = HttpRequest.get("https://api.example.com/users")
 *         .query("page", "1")
 *         .query("size", "20")
 *         .header("Accept", "application/json")
 *         .bearerToken("token-123")
 *         .traceId("order-42")
 *         .build();
 *
 * // 基于模板派生新请求（原 request 不受影响）
 * HttpRequest nextPage = request.query("page", "2").build();
 *
 * // 上传文件（自动按 multipart/form-data 编码）
 * HttpRequest upload = HttpRequest.post("https://api.example.com/files")
 *         .form(Collections.singletonMap("folder", "inbox"))
 *         .file("attachment", new File("report.pdf"))
 *         .build();
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-24
 */
public final class HttpRequest {

    /** HTTP 方法常量：GET。 */
    private static final String METHOD_GET = "GET";
    /** HTTP 方法常量：POST。 */
    private static final String METHOD_POST = "POST";
    /** HTTP 方法常量：PUT。 */
    private static final String METHOD_PUT = "PUT";
    /** HTTP 方法常量：DELETE。 */
    private static final String METHOD_DELETE = "DELETE";
    /** HTTP 方法常量：HEAD。 */
    private static final String METHOD_HEAD = "HEAD";
    /** {@link #body(String)} 未显式指定 Content-Type 时使用的缺省值。 */
    private static final String DEFAULT_BODY_CONTENT_TYPE = "text/plain; charset=UTF-8";
    /** {@link #json(String)} 使用的 Content-Type。 */
    private static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";
    /** 代理端口合法下限（0 为 IANA 保留端口，不可用作目标端口）。 */
    private static final int MIN_PROXY_PORT = 1;
    /** 代理端口合法上限。 */
    private static final int MAX_PROXY_PORT = 65535;

    /**
     * 内部可变状态载体：仅在“构造新实例”的瞬间被写入和读取，构造完成后不再被任何方法修改，
     * 从而以最小的样板代码保证 HttpRequest 对外不可变。
     */
    private final State state;

    /**
     * 私有全参构造：state 只能由静态工厂或 copyState() 产生，且产生后立即被本实例独占。
     */
    private HttpRequest(State state) {
        this.state = state;
    }

    // ------------------------------------------------------------------
    // 静态工厂：以指定方法与 URL 创建初始请求
    // ------------------------------------------------------------------

    /**
     * 创建 GET 请求。
     *
     * @param url 目标地址，支持 http/https，不含查询参数（查询参数用 {@link #query(String, String)} 添加）；不可为 null
     * @return 新的不可变请求实例，永不为 null
     */
    public static HttpRequest get(String url) {
        return create(METHOD_GET, url);
    }

    /**
     * 创建 POST 请求。
     *
     * @param url 目标地址，支持 http/https；不可为 null
     * @return 新的不可变请求实例，永不为 null
     */
    public static HttpRequest post(String url) {
        return create(METHOD_POST, url);
    }

    /**
     * 创建 PUT 请求。
     *
     * @param url 目标地址，支持 http/https；不可为 null
     * @return 新的不可变请求实例，永不为 null
     */
    public static HttpRequest put(String url) {
        return create(METHOD_PUT, url);
    }

    /**
     * 创建 DELETE 请求。
     *
     * @param url 目标地址，支持 http/https；不可为 null
     * @return 新的不可变请求实例，永不为 null
     */
    public static HttpRequest delete(String url) {
        return create(METHOD_DELETE, url);
    }

    /**
     * 创建 HEAD 请求。
     *
     * @param url 目标地址，支持 http/https；不可为 null
     * @return 新的不可变请求实例，永不为 null
     */
    public static HttpRequest head(String url) {
        return create(METHOD_HEAD, url);
    }

    /**
     * 以指定方法与 URL 构造初始实例。
     */
    private static HttpRequest create(String method, String url) {
        State fresh = new State();
        fresh.method = method;
        fresh.url = url;
        return new HttpRequest(fresh);
    }

    // ------------------------------------------------------------------
    // 配置方法：均返回修改后的新实例，当前实例不变
    // ------------------------------------------------------------------

    /**
     * 追加一个查询参数（可多次调用，保持添加顺序，发送前统一按 UTF-8 做 URL 编码并拼接到 URL 之后）。
     *
     * @param name 参数名，不可为 null 且不能为空串
     * @param value 参数值，不可为 null（无值参数请传空串）；空格按 RFC 3986 编码为 {@code %20}
     * @return 追加了该参数的新实例，永不为 null
     * @throws IllegalArgumentException name 为 null/空串，或 value 为 null
     */
    public HttpRequest query(String name, String value) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("查询参数名不能为空");
        }
        Objects.requireNonNull(value, "查询参数值不能为 null: " + name);
        State next = copyState();
        next.queries.add(new String[] {name, value});
        return new HttpRequest(next);
    }

    /**
     * 追加一个请求头（可多次调用；同名头允许重复出现，按添加顺序发送）。
     *
     * @param name 头名称，不可为 null 且不能为空串（大小写敏感保留，是否忽略大小写由服务端决定）
     * @param value 头取值，不可为 null（允许空串）
     * @return 追加了该头的新实例，永不为 null
     * @throws IllegalArgumentException name 为 null/空串，或 value 为 null
     */
    public HttpRequest header(String name, String value) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("请求头名称不能为空");
        }
        Objects.requireNonNull(value, "请求头取值不能为 null: " + name);
        State next = copyState();
        next.headers.add(new String[] {name, value});
        return new HttpRequest(next);
    }

    /**
     * 批量追加请求头（逐条等价于多次调用 {@link #header(String, String)}）。
     *
     * @param headers 头名到取值的映射，不可为 null；键不可为 null/空串、值不可为 null，否则逐条校验时抛出
     * @return 追加了全部头的新实例，永不为 null
     * @throws IllegalArgumentException 任一头名非法
     * @throws NullPointerException headers 为 null，或任一 value 为 null
     */
    public HttpRequest headers(Map<String, String> headers) {
        Objects.requireNonNull(headers, "headers 不能为 null");
        HttpRequest result = this;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            result = result.header(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * 设置原始文本请求体（字符集 UTF-8），缺省 Content-Type 为 {@code text/plain; charset=UTF-8}。
     *
     * <p>注意：若同时配置了上传文件，发送时以 multipart 为准，原始请求体将被忽略。</p>
     *
     * @param body 请求体文本，不可为 null
     * @return 携带请求体的新实例，永不为 null
     * @throws NullPointerException body 为 null
     */
    public HttpRequest body(String body) {
        Objects.requireNonNull(body, "body 不能为 null");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        State next = copyState();
        next.body = bytes;
        next.bodyContentType = DEFAULT_BODY_CONTENT_TYPE;
        return new HttpRequest(next);
    }

    /**
     * 设置原始二进制请求体。
     *
     * <p>注意：若同时配置了上传文件，发送时以 multipart 为准，原始请求体将被忽略。</p>
     *
     * @param body 请求体字节，不可为 null（内部会做防御性拷贝）
     * @param contentType 请求体 Content-Type，不可为 null（例如 {@code application/octet-stream}）
     * @return 携带请求体的新实例，永不为 null
     * @throws NullPointerException body 或 contentType 为 null
     */
    public HttpRequest body(byte[] body, String contentType) {
        Objects.requireNonNull(body, "body 不能为 null");
        Objects.requireNonNull(contentType, "contentType 不能为 null");
        State next = copyState();
        next.body = body.clone(); // 数组可变，克隆防止外部随后篡改
        next.bodyContentType = contentType;
        return new HttpRequest(next);
    }

    /**
     * 设置 JSON 请求体（字符集 UTF-8），Content-Type 为 {@code application/json; charset=UTF-8}。
     *
     * @param json JSON 文本，不可为 null（本方法不做 JSON 合法性校验）
     * @return 携带 JSON 请求体的新实例，永不为 null
     * @throws NullPointerException json 为 null
     */
    public HttpRequest json(String json) {
        Objects.requireNonNull(json, "json 不能为 null");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        State next = copyState();
        next.body = bytes;
        next.bodyContentType = JSON_CONTENT_TYPE;
        return new HttpRequest(next);
    }

    /**
     * 设置表单字段（{@code application/x-www-form-urlencoded} 编码，UTF-8）。
     *
     * <p>注意：若同时配置了上传文件（{@link #file(String, File)}），表单字段会作为普通
     * multipart 部件随文件一起提交，整体自动变为 multipart/form-data。</p>
     *
     * @param formFields 字段名到取值的映射，不可为 null（保持插入顺序；键、值不可为 null）
     * @return 携带表单字段的新实例，永不为 null
     * @throws NullPointerException formFields 为 null
     */
    public HttpRequest form(Map<String, String> formFields) {
        Objects.requireNonNull(formFields, "formFields 不能为 null");
        for (Map.Entry<String, String> entry : formFields.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "表单字段名不能为 null");
            Objects.requireNonNull(entry.getValue(), "表单字段值不能为 null: " + entry.getKey());
        }
        State next = copyState();
        next.formFields.putAll(formFields);
        return new HttpRequest(next);
    }

    /**
     * 追加一个 multipart 文件部件（可多次调用）。只要存在文件部件，
     * 整个请求即按 {@code multipart/form-data} 编码，表单字段作为普通部件混入，原始请求体被忽略。
     *
     * @param fieldName 表单字段名，不可为 null 且不能为空串
     * @param file 待上传的本地文件，不可为 null（文件存在性与可读性在发送时校验）
     * @return 追加了文件部件的新实例，永不为 null
     * @throws IllegalArgumentException fieldName 为 null/空串
     * @throws NullPointerException file 为 null
     */
    public HttpRequest file(String fieldName, File file) {
        if (fieldName == null || fieldName.isEmpty()) {
            throw new IllegalArgumentException("文件字段名不能为空");
        }
        Objects.requireNonNull(file, "file 不能为 null");
        State next = copyState();
        next.files.add(new FilePart(fieldName, file));
        return new HttpRequest(next);
    }

    /**
     * 设置连接超时（仅覆盖本请求；单位毫秒，必须大于 0；未设置时由 HttpTool 实例默认值兜底）。
     *
     * @param connectTimeoutMillis 连接超时毫秒数，必须大于 0
     * @return 携带该设置的新实例，永不为 null
     * @throws IllegalArgumentException connectTimeoutMillis 不大于 0
     */
    public HttpRequest connectTimeoutMillis(int connectTimeoutMillis) {
        if (connectTimeoutMillis <= 0) {
            throw new IllegalArgumentException("connectTimeoutMillis 必须大于 0: " + connectTimeoutMillis);
        }
        State next = copyState();
        next.connectTimeoutMillis = Integer.valueOf(connectTimeoutMillis);
        return new HttpRequest(next);
    }

    /**
     * 设置读取超时（仅覆盖本请求；单位毫秒，必须大于 0；未设置时由 HttpTool 实例默认值兜底）。
     *
     * @param readTimeoutMillis 读取超时毫秒数，必须大于 0
     * @return 携带该设置的新实例，永不为 null
     * @throws IllegalArgumentException readTimeoutMillis 不大于 0
     */
    public HttpRequest readTimeoutMillis(int readTimeoutMillis) {
        if (readTimeoutMillis <= 0) {
            throw new IllegalArgumentException("readTimeoutMillis 必须大于 0: " + readTimeoutMillis);
        }
        State next = copyState();
        next.readTimeoutMillis = Integer.valueOf(readTimeoutMillis);
        return new HttpRequest(next);
    }

    /**
     * 设置失败重试次数（仅覆盖本请求；0 表示不重试；未设置时由 HttpTool 实例默认值兜底）。
     *
     * <p>重试仅对连接超时与连接类错误生效，详见 {@code HttpTool} 类说明。</p>
     *
     * @param maxRetry 最大重试次数（不含首次请求），必须不小于 0
     * @return 携带该设置的新实例，永不为 null
     * @throws IllegalArgumentException maxRetry 小于 0
     */
    public HttpRequest maxRetry(int maxRetry) {
        if (maxRetry < 0) {
            throw new IllegalArgumentException("maxRetry 不能为负: " + maxRetry);
        }
        State next = copyState();
        next.maxRetry = Integer.valueOf(maxRetry);
        return new HttpRequest(next);
    }

    /**
     * 设置 HTTP 代理。
     *
     * @param host 代理主机名或 IP，不可为 null 且不能为空串
     * @param port 代理端口，取值范围 [1, 65535]
     * @return 携带代理设置的新实例，永不为 null
     * @throws IllegalArgumentException host 为 null/空串，或端口越界
     */
    public HttpRequest proxy(String host, int port) {
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("代理主机不能为空");
        }
        if (port < MIN_PROXY_PORT || port > MAX_PROXY_PORT) {
            throw new IllegalArgumentException("代理端口必须在 " + MIN_PROXY_PORT + "-" + MAX_PROXY_PORT + " 之间: " + port);
        }
        State next = copyState();
        next.proxyHost = host;
        next.proxyPort = Integer.valueOf(port);
        return new HttpRequest(next);
    }

    /**
     * 设置 HTTP Basic 认证（发送时生成 {@code Authorization: Basic base64(user:pass)}，UTF-8 编码）。
     *
     * <p>若同时设置 Bearer 令牌，发送时 Bearer 优先；调用方显式设置的 Authorization 头优先级最高。</p>
     *
     * @param user 用户名，不可为 null（允许空串）
     * @param pass 密码，不可为 null（允许空串）
     * @return 携带认证信息的新实例，永不为 null
     * @throws NullPointerException user 或 pass 为 null
     */
    public HttpRequest basicAuth(String user, String pass) {
        Objects.requireNonNull(user, "user 不能为 null");
        Objects.requireNonNull(pass, "pass 不能为 null");
        State next = copyState();
        next.basicUser = user;
        next.basicPassword = pass;
        return new HttpRequest(next);
    }

    /**
     * 设置 Bearer 令牌（发送时生成 {@code Authorization: Bearer token}）。
     *
     * <p>若同时设置 Basic 认证，发送时 Bearer 优先；调用方显式设置的 Authorization 头优先级最高。</p>
     *
     * @param token 令牌值，不可为 null 且不能为空串（不含 "Bearer " 前缀）
     * @return 携带认证信息的新实例，永不为 null
     * @throws IllegalArgumentException token 为 null/空串
     */
    public HttpRequest bearerToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("token 不能为空");
        }
        State next = copyState();
        next.bearerToken = token;
        return new HttpRequest(next);
    }

    /**
     * 设置全链路追踪 ID（发送时写入 {@code X-Trace-Id} 请求头并回填到响应）。
     *
     * @param traceId 追踪 ID，不可为 null 且不能为空串；未设置时由 HttpTool 自动生成随机 UUID
     * @return 携带 traceId 的新实例，永不为 null
     * @throws IllegalArgumentException traceId 为 null/空串
     */
    public HttpRequest traceId(String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            throw new IllegalArgumentException("traceId 不能为空");
        }
        State next = copyState();
        next.traceId = traceId;
        return new HttpRequest(next);
    }

    /**
     * 结束配置并返回最终请求对象（本实例本身已是不可变对象，此处仅做收尾校验）。
     *
     * @return 本请求实例（未创建新对象），永不为 null
     * @throws IllegalArgumentException URL 为 null 或空串（URL 的语法合法性由发送方校验并归类为 INVALID_URL）
     */
    public HttpRequest build() {
        if (state.url == null || state.url.isEmpty()) {
            throw new IllegalArgumentException("URL 未设置或为空");
        }
        return this;
    }

    // ------------------------------------------------------------------
    // 读取方法
    // ------------------------------------------------------------------

    /**
     * @return HTTP 方法（GET/POST/PUT/DELETE/HEAD），永不为 null
     */
    public String getMethod() {
        return state.method;
    }

    /**
     * @return 创建请求时传入的基础 URL（不含查询参数），永不为 null
     */
    public String getUrl() {
        return state.url;
    }

    /**
     * 获取完整 URL：基础 URL 追加按 UTF-8 编码的查询参数串（以 {@code ?} 或既有 {@code &} 衔接）。
     *
     * @return 完整 URL，永不为 null；未添加查询参数时与 {@link #getUrl()} 相同
     */
    public String getFullUrl() {
        if (state.queries.isEmpty()) {
            return state.url;
        }
        StringBuilder fullUrl = new StringBuilder(state.url);
        fullUrl.append(state.url.indexOf('?') >= 0 ? '&' : '?');
        for (int i = 0; i < state.queries.size(); i++) {
            String[] pair = state.queries.get(i);
            if (i > 0) {
                fullUrl.append('&');
            }
            fullUrl.append(encodeQueryComponent(pair[0])).append('=').append(encodeQueryComponent(pair[1]));
        }
        return fullUrl.toString();
    }

    /**
     * @return 查询参数的不可变副本，元素为长度 2 的数组 {name, value}，保持添加顺序；永不为 null（可为空列表）
     */
    public List<String[]> getQueries() {
        List<String[]> copy = new ArrayList<String[]>(state.queries.size());
        for (String[] pair : state.queries) {
            copy.add(pair.clone()); // 数组本身可变，克隆防止外部篡改
        }
        return Collections.unmodifiableList(copy);
    }

    /**
     * @return 请求头的不可变副本，元素为长度 2 的数组 {name, value}，保持添加顺序（允许同名重复）；永不为 null（可为空列表）
     */
    public List<String[]> getHeaders() {
        List<String[]> copy = new ArrayList<String[]>(state.headers.size());
        for (String[] pair : state.headers) {
            copy.add(pair.clone());
        }
        return Collections.unmodifiableList(copy);
    }

    /**
     * @return 原始请求体的字节副本；未设置请求体时为 null
     */
    public byte[] getBody() {
        return state.body == null ? null : state.body.clone();
    }

    /**
     * @return 原始请求体的 Content-Type；未设置时为 null
     */
    public String getBodyContentType() {
        return state.bodyContentType;
    }

    /**
     * @return 表单字段的不可变副本（保持插入顺序）；永不为 null（可为空 Map）
     */
    public Map<String, String> getFormFields() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(state.formFields));
    }

    /**
     * @return 文件部件的不可变副本，保持添加顺序；永不为 null（可为空列表）
     */
    public List<FilePart> getFiles() {
        return Collections.unmodifiableList(new ArrayList<FilePart>(state.files));
    }

    /**
     * @return 是否将按 multipart/form-data 发送（即至少配置了一个文件部件）
     */
    public boolean isMultipart() {
        return !state.files.isEmpty();
    }

    /**
     * @return 连接超时毫秒数；本请求未设置时为 null（此时使用 HttpTool 实例默认值）
     */
    public Integer getConnectTimeoutMillis() {
        return state.connectTimeoutMillis;
    }

    /**
     * @return 读取超时毫秒数；本请求未设置时为 null（此时使用 HttpTool 实例默认值）
     */
    public Integer getReadTimeoutMillis() {
        return state.readTimeoutMillis;
    }

    /**
     * @return 最大重试次数（不含首次请求）；本请求未设置时为 null（此时使用 HttpTool 实例默认值）
     */
    public Integer getMaxRetry() {
        return state.maxRetry;
    }

    /**
     * @return 代理主机；未设置代理时为 null
     */
    public String getProxyHost() {
        return state.proxyHost;
    }

    /**
     * @return 代理端口；未设置代理时为 null
     */
    public Integer getProxyPort() {
        return state.proxyPort;
    }

    /**
     * @return Basic 认证用户名；未设置时为 null
     */
    public String getBasicUser() {
        return state.basicUser;
    }

    /**
     * @return Basic 认证密码；未设置时为 null
     */
    public String getBasicPassword() {
        return state.basicPassword;
    }

    /**
     * @return Bearer 令牌；未设置时为 null
     */
    public String getBearerToken() {
        return state.bearerToken;
    }

    /**
     * @return 调用方设置的追踪 ID；未设置时为 null（发送时由 HttpTool 自动生成）
     */
    public String getTraceId() {
        return state.traceId;
    }

    // ------------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------------

    /**
     * 包私有：供 HttpTool 在遇到 301/302/303 重定向时按业界惯例把请求改写为不带请求体的 GET。
     *
     * @return 改写后的新实例，永不为 null
     */
    HttpRequest withoutBodyAsGet() {
        State next = copyState();
        next.method = METHOD_GET;
        next.body = null;
        next.bodyContentType = null;
        next.formFields.clear();
        next.files.clear();
        return new HttpRequest(next);
    }

    /**
     * 深拷贝当前状态（集合重建、数组克隆），作为派生新实例的基础。
     */
    private State copyState() {
        State next = new State();
        next.method = this.state.method;
        next.url = this.state.url;
        next.queries.addAll(this.state.queries);
        next.headers.addAll(this.state.headers);
        next.body = this.state.body == null ? null : this.state.body.clone();
        next.bodyContentType = this.state.bodyContentType;
        next.formFields.putAll(this.state.formFields);
        next.files.addAll(this.state.files);
        next.connectTimeoutMillis = this.state.connectTimeoutMillis;
        next.readTimeoutMillis = this.state.readTimeoutMillis;
        next.maxRetry = this.state.maxRetry;
        next.proxyHost = this.state.proxyHost;
        next.proxyPort = this.state.proxyPort;
        next.basicUser = this.state.basicUser;
        next.basicPassword = this.state.basicPassword;
        next.bearerToken = this.state.bearerToken;
        next.traceId = this.state.traceId;
        return next;
    }

    /**
     * 按查询串语义对单个成分做 UTF-8 URL 编码。
     */
    private static String encodeQueryComponent(String value) {
        try {
            // URLEncoder 面向表单语义会把空格编成 '+'，查询串按 RFC 3986 应为 %20，这里做一次替换
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 是 JVM 规范强制要求的字符集，理论上不可达
            throw new IllegalStateException("JVM 不支持 UTF-8", e);
        }
    }

    /**
     * multipart/form-data 上传中的单个文件部件（不可变值对象）。
     *
     * <p>线程安全性：字段全部为 final，线程安全。</p>
     *
     * @author 星河一叶 (RainxButterfly)
     * @since 2026-08-24
     */
    public static final class FilePart {

        /** 表单字段名。 */
        private final String fieldName;
        /** 待上传的本地文件。 */
        private final File file;

        /**
         * 私有构造：仅允许 {@link HttpRequest#file(String, File)} 创建。
         */
        private FilePart(String fieldName, File file) {
            this.fieldName = fieldName;
            this.file = file;
        }

        /**
         * @return 表单字段名，永不为 null
         */
        public String getFieldName() {
            return fieldName;
        }

        /**
         * @return 待上传的本地文件，永不为 null
         */
        public File getFile() {
            return file;
        }
    }

    /**
     * 内部可变状态载体：字段仅在派生新实例的过程中被写入，构造完成后由所属 HttpRequest 独占且不再修改。
     */
    private static final class State {

        /** HTTP 方法（GET/POST/PUT/DELETE/HEAD）。 */
        private String method;
        /** 基础 URL，不含查询参数。 */
        private String url;
        /** 查询参数（保持添加顺序），元素为 {name, value}。 */
        private final List<String[]> queries = new ArrayList<String[]>();
        /** 请求头（保持添加顺序，允许同名重复），元素为 {name, value}。 */
        private final List<String[]> headers = new ArrayList<String[]>();
        /** 原始请求体字节；null 表示未设置。 */
        private byte[] body;
        /** 原始请求体的 Content-Type；null 表示未设置。 */
        private String bodyContentType;
        /** 表单字段（保持插入顺序）。 */
        private final Map<String, String> formFields = new LinkedHashMap<String, String>();
        /** multipart 文件部件（保持添加顺序）。 */
        private final List<FilePart> files = new ArrayList<FilePart>();
        /** 连接超时（毫秒）；null 表示未设置。 */
        private Integer connectTimeoutMillis;
        /** 读取超时（毫秒）；null 表示未设置。 */
        private Integer readTimeoutMillis;
        /** 失败重试次数；null 表示未设置。 */
        private Integer maxRetry;
        /** 代理主机；null 表示未设置。 */
        private String proxyHost;
        /** 代理端口；null 表示未设置。 */
        private Integer proxyPort;
        /** Basic 认证用户名；null 表示未设置。 */
        private String basicUser;
        /** Basic 认证密码；null 表示未设置。 */
        private String basicPassword;
        /** Bearer 令牌；null 表示未设置。 */
        private String bearerToken;
        /** 调用方设置的追踪 ID；null 表示未设置（发送时自动生成）。 */
        private String traceId;
    }
}
