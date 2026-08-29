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
 * @since 2026-08-29
 */
package com.xingheyiye.xingye.kit.security;

/**
 * JWT 处理异常：令牌格式非法、签名不匹配、已过期或未生效时抛出。
 *
 * <p>一句话职责：以非受检异常统一表达 JWT 全流程失败，便于业务侧集中捕获处理。</p>
 *
 * <p>适用场景：配合 {@link JwtWrapper#verify(String, String)} 等校验方法，
 * 在登录拦截器/网关过滤器中捕获后统一返回 401。</p>
 *
 * <p>线程安全性：不可变异常对象（仅携带消息与原因），线程安全。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * try {
 *     JwtClaims claims = JwtWrapper.verify(token, secret);
 * } catch (JwtException e) {
 *     // "signature mismatch" / "token expired" / "token not yet valid" / 格式错误等
 *     response.sendError(401, e.getMessage());
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-29
 */
public class JwtException extends RuntimeException {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /**
     * 以错误消息构造异常。
     *
     * @param message 错误消息，可为 null（建议提供便于排查的描述）
     */
    public JwtException(String message) {
        super(message);
    }

    /**
     * 以错误消息与根因构造异常。
     *
     * @param message 错误消息，可为 null
     * @param cause   根因异常，可为 null
     */
    public JwtException(String message, Throwable cause) {
        super(message, cause);
    }
}
