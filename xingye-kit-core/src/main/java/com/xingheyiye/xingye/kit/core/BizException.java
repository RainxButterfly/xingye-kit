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
 * @since 2026-08-17
 */
package com.xingheyiye.xingye.kit.core;

/**
 * 携带数字错误码的业务异常，用于在服务层中断流程并向调用方传递结构化错误信息。
 *
 * <p>适用场景：参数校验失败、业务规则不满足、下游依赖不可用等需要携带错误码快速失败的场景，
 * 常与 {@link ErrorCode}、{@link Result}、{@link Assert} 配合使用。
 *
 * <p>线程安全性：错误码字段为 {@code final} 不可变，异常实例可安全地跨线程传递与记录日志。
 *
 * <p>使用示例：
 * <pre>{@code
 * public void withdraw(long amount) {
 *     if (amount <= 0) {
 *         throw new BizException(40001, "提现金额必须大于 0");
 *     }
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-17
 */
public class BizException extends RuntimeException {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /** 未显式指定错误码时的默认错误码，与 HTTP 500 及 {@link Result#CODE_FAIL} 对齐 */
    private static final int DEFAULT_CODE = 500;

    /** 数字错误码 */
    private final int code;

    /**
     * 以默认错误码 500 构造业务异常。
     *
     * @param message 错误描述，可为 null
     */
    public BizException(String message) {
        this(DEFAULT_CODE, message);
    }

    /**
     * 以指定错误码构造业务异常。
     *
     * @param code 数字错误码
     * @param message 错误描述，可为 null
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 以指定错误码与根因构造业务异常。
     *
     * @param code 数字错误码
     * @param message 错误描述，可为 null
     * @param cause 根因异常，可为 null
     */
    public BizException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 以 {@link ErrorCode} 构造业务异常。
     *
     * @param errorCode 错误码对象，不能为 null
     * @throws NullPointerException 当 {@code errorCode} 为 {@code null} 时抛出
     */
    public BizException(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getMessage());
    }

    /**
     * 以 {@link ErrorCode} 与根因构造业务异常。
     *
     * @param errorCode 错误码对象，不能为 null
     * @param cause 根因异常，可为 null
     * @throws NullPointerException 当 {@code errorCode} 为 {@code null} 时抛出
     */
    public BizException(ErrorCode errorCode, Throwable cause) {
        this(errorCode.getCode(), errorCode.getMessage(), cause);
    }

    /**
     * 获取数字错误码。
     *
     * @return 数字错误码，恒不为 null（原始类型）
     */
    public int getCode() {
        return code;
    }
}
