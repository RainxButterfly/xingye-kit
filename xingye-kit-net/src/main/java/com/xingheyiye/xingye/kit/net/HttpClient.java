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

/**
 * HTTP 客户端契约：把不可变的 {@link HttpRequest} 描述执行并返回统一的 {@link HttpResponse}。
 *
 * <p>一句话职责：把"请求描述 → 真实网络往返 → 统一响应模型"抽象为一个可替换的端口，
 * 让业务代码只依赖本接口，底层实现可自由替换。</p>
 *
 * <p>内置选择：本模块的 {@link HttpTool} 基于 JDK 原生 HttpURLConnection 实现该接口
 * （零第三方依赖，内置手动重定向、gzip 解压、超时区分、指数退避重试、代理与认证）。
 * 需要更重量级能力的场景（HTTP/2、连接池、异步）可实现本接口包装 OkHttp、
 * Apache HttpClient 等第三方库，业务代码面向接口不变。</p>
 *
 * <p>线程安全性：接口本身不约束线程安全性，由实现方声明；
 * {@link HttpTool} 为不可变对象、线程安全，可跨线程共享单例。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * HttpClient client = new HttpTool(3000, 5000, 2); // JDK 内置实现
 * // 或自定义：MyOkHttpClient implements HttpClient { ... }
 * HttpResponse response = client.execute(
 *         HttpRequest.get("https://api.example.com/ping").build());
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public interface HttpClient {

    /**
     * 执行一次 HTTP 请求并返回统一响应模型。
     *
     * @param request 请求描述，不能为 null
     * @return 响应模型，不会为 null（网络错误以 {@link HttpErrorType} 表达而非抛异常）
     * @throws IllegalArgumentException request 为 null 时抛出
     */
    HttpResponse execute(HttpRequest request);
}
