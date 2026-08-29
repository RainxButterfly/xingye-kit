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

/**
 * 基于 JDK 原生能力实现的零第三方依赖 HTTP 客户端与网络稳定性工具包。
 *
 * <p>模块提供三层能力：</p>
 * <ul>
 *   <li>请求/响应模型：{@link com.xingheyiye.xingye.kit.net.HttpRequest}（不可变请求描述，
 *       自带建造者式 API）与 {@link com.xingheyiye.xingye.kit.net.HttpResponse}
 *       （不可变响应，网络错误以 {@link com.xingheyiye.xingye.kit.net.HttpErrorType} 表达而非异常）；</li>
 *   <li>执行器：{@link com.xingheyiye.xingye.kit.net.HttpTool} 基于 HttpURLConnection 实现，
 *       内置手动重定向跟随、gzip 自动解压、超时区分、指数退避重试、代理、Basic/Bearer 认证与 X-Trace-Id 追踪；
 *       契约见 {@link com.xingheyiye.xingye.kit.net.HttpClient}，实现方可自行替换；</li>
 *   <li>稳定性组件：{@link com.xingheyiye.xingye.kit.net.RateLimiter}（令牌桶
 *       {@link com.xingheyiye.xingye.kit.net.TokenBucketRateLimiter} / 漏桶
 *       {@link com.xingheyiye.xingye.kit.net.LeakyBucketRateLimiter} 两种内置选择）与
 *       {@link com.xingheyiye.xingye.kit.net.CircuitBreaker}（滑动窗口
 *       {@link com.xingheyiye.xingye.kit.net.SlidingWindowCircuitBreaker} / 并发信号量
 *       {@link com.xingheyiye.xingye.kit.net.ConcurrencyLimitCircuitBreaker} 两种内置选择）。</li>
 * </ul>
 *
 * <p>适用场景：中小型服务、运维工具、内部网关代理等不希望引入重量级 HTTP 客户端的场合；
 * 超大文件下载不适用（响应体会被完整读入内存，详见 HttpTool 的类说明）。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * HttpResponse response = HttpTool.send(
 *         HttpRequest.get("https://api.example.com/ping")
 *                 .query("verbose", "true")
 *                 .traceId("job-7")
 *                 .build());
 * if (response.isSuccess()) {
 *     System.out.println(response.getBodyText());
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-24
 */
package com.xingheyiye.xingye.kit.net;
