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

/**
 * HTTP 调用失败原因的分类枚举，用于在 {@link HttpResponse} 中以数据（而非异常）的形式表达网络层错误。
 *
 * <p>适用场景：调用方拿到 {@link HttpResponse} 之后按错误类别分别处理——例如对
 * {@link #CONNECT_TIMEOUT}、{@link #CONNECTION_ERROR} 触发重试或告警，
 * 对 {@link #TOO_MANY_REDIRECTS} 直接判定服务端配置异常等。</p>
 *
 * <p>线程安全性：枚举常量天然不可变，可被任意线程并发读取。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * HttpResponse response = HttpTool.send(HttpRequest.get("https://example.com").build());
 * if (!response.isSuccess() && response.getErrorType() == HttpErrorType.CONNECT_TIMEOUT) {
 *     // 连接超时：可安排稍后重试
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-24
 */
public enum HttpErrorType {

    /** 一切正常：HTTP 交换成功完成，未发生任何网络层错误。 */
    NONE,

    /** URL 非法（缺少协议、协议不受支持、格式错误等），请求根本没有发出去。 */
    INVALID_URL,

    /** 在建立 TCP/TLS 连接阶段超时（对应连接超时配置）。 */
    CONNECT_TIMEOUT,

    /** 连接已建立，但在等待或读取响应数据阶段超时（对应读取超时配置）。 */
    READ_TIMEOUT,

    /** 连接建立失败或中途被破坏：拒绝连接、未知主机、网络不可达、连接被重置等。 */
    CONNECTION_ERROR,

    /** 重定向次数超过允许上限（{@code HttpTool} 中为 5 次）。 */
    TOO_MANY_REDIRECTS,

    /** 线程在阻塞等待或网络 IO 过程中被中断。 */
    INTERRUPTED,

    /** 未归入以上类别的其他错误。 */
    UNKNOWN
}
