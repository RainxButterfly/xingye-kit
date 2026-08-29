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

import java.io.Serializable;

/**
 * 泛型统一返回体，以“错误码 + 描述 + 数据”三段式结构承载接口出参。
 *
 * <p>适用场景：REST 接口与 RPC 调用的统一响应格式；成功使用 {@link #ok()} 或 {@link #ok(Object)}，
 * 失败使用 {@link #fail(int, String)} 或 {@link #fail(ErrorCode)}。
 *
 * <p>线程安全性：本类为可变 POJO，自身不做并发控制；实例未被并发修改时可安全共享，
 * 推荐每次请求创建新实例。
 *
 * <p>使用示例：
 * <pre>{@code
 * Result<User> result = Result.ok(user);
 * if (result.isSuccess()) {
 *     User data = result.getData();
 * } else {
 *     log.warn("失败: code={}, message={}", result.getCode(), result.getMessage());
 * }
 * }</pre>
 *
 * @param <T> 数据载荷类型
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-17
 */
public class Result<T> implements Serializable {

    /** 序列化版本号 */
    private static final long serialVersionUID = 1L;

    /** 成功错误码，与 HTTP 状态码 200 对齐 */
    public static final int CODE_SUCCESS = 200;

    /** 失败错误码，与 HTTP 状态码 500 对齐 */
    public static final int CODE_FAIL = 500;

    /** 成功返回时的默认描述文案 */
    private static final String DEFAULT_SUCCESS_MESSAGE = "操作成功";

    /** 错误码，约定 {@link #CODE_SUCCESS} 表示成功 */
    private int code;

    /** 描述信息，可为 null */
    private String message;

    /** 数据载荷，可为 null */
    private T data;

    /**
     * 无参构造器，供序列化框架使用。
     */
    public Result() {
    }

    /**
     * 全参构造器。
     *
     * @param code 错误码，约定 {@link #CODE_SUCCESS} 表示成功
     * @param message 描述信息，可为 null
     * @param data 数据载荷，可为 null
     */
    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 构造无数据的成功返回体。
     *
     * @param <T> 数据载荷类型
     * @return 成功返回体，code 为 {@link #CODE_SUCCESS}、data 为 null，恒不为 null
     */
    public static <T> Result<T> ok() {
        return new Result<T>(CODE_SUCCESS, DEFAULT_SUCCESS_MESSAGE, null);
    }

    /**
     * 构造携带数据的成功返回体。
     *
     * @param data 数据载荷，可为 null（为 null 时仍视为成功）
     * @param <T> 数据载荷类型
     * @return 成功返回体，code 为 {@link #CODE_SUCCESS}，恒不为 null
     */
    public static <T> Result<T> ok(T data) {
        return new Result<T>(CODE_SUCCESS, DEFAULT_SUCCESS_MESSAGE, data);
    }

    /**
     * 构造失败返回体。
     *
     * @param code 错误码，约定为非 {@link #CODE_SUCCESS} 的值
     * @param message 失败描述，可为 null
     * @param <T> 数据载荷类型
     * @return 失败返回体，data 为 null，恒不为 null
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<T>(code, message, null);
    }

    /**
     * 基于 {@link ErrorCode} 构造失败返回体。
     *
     * @param errorCode 错误码对象，不能为 null
     * @param <T> 数据载荷类型
     * @return 失败返回体，data 为 null，恒不为 null
     * @throws IllegalArgumentException 当 {@code errorCode} 为 {@code null} 时抛出
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        if (errorCode == null) {
            throw new IllegalArgumentException("errorCode 不能为 null");
        }
        return new Result<T>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 获取错误码。
     *
     * @return 错误码，恒不为 null（原始类型）
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取描述信息。
     *
     * @return 描述信息，可能为 null
     */
    public String getMessage() {
        return message;
    }

    /**
     * 获取数据载荷。
     *
     * @return 数据载荷，可能为 null
     */
    public T getData() {
        return data;
    }

    /**
     * 判断是否成功。
     *
     * @return code 等于 {@link #CODE_SUCCESS} 时返回 {@code true}，否则返回 {@code false}
     */
    public boolean isSuccess() {
        return code == CODE_SUCCESS;
    }

    /**
     * 判断是否失败。
     *
     * @return code 不等于 {@link #CODE_SUCCESS} 时返回 {@code true}，否则返回 {@code false}
     */
    public boolean isFail() {
        return !isSuccess();
    }

    /**
     * 返回用于调试的可读字符串。
     *
     * @return 形如 {@code Result{code=200, message='操作成功', data=xxx}} 的字符串，恒不为 null
     */
    @Override
    public String toString() {
        return "Result{code=" + code + ", message='" + message + "', data=" + data + '}';
    }
}
