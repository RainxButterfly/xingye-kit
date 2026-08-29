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
 * 错误码契约接口，统一“数字错误码 + 描述文案”的错误定义格式。
 *
 * <p>适用场景：以枚举集中管理业务错误定义，再配合
 * {@link BizException}、{@link Result} 与 {@link Assert} 实现错误信息的结构化传递。
 *
 * <p>线程安全性：实现类应为无状态枚举或不可变对象，实现为枚举时天然线程安全。
 *
 * <p>使用示例（自定义枚举实现）：
 * <pre>{@code
 * public enum CommonErrorCode implements ErrorCode {
 *     PARAM_INVALID(40001, "参数非法"),
 *     NOT_FOUND(40404, "资源不存在");
 *
 *     private final int code;
 *     private final String message;
 *
 *     CommonErrorCode(int code, String message) {
 *         this.code = code;
 *         this.message = message;
 *     }
 *
 *     @Override
 *     public int getCode() {
 *         return code;
 *     }
 *
 *     @Override
 *     public String getMessage() {
 *         return message;
 *     }
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-17
 */
public interface ErrorCode {

    /**
     * 获取数字错误码。
     *
     * @return 数字错误码，恒不为 null（原始类型）
     */
    int getCode();

    /**
     * 获取错误描述文案。
     *
     * @return 错误描述文案，建议实现返回非 null 的可读文案
     */
    String getMessage();
}
