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
 * @since 2026-08-18
 */
package com.xingheyiye.xingye.kit.core;

import java.util.Collection;
import java.util.Map;

/**
 * 轻量断言工具，校验失败时抛出携带错误码的 {@link BizException}，实现参数与前置条件的快速失败。
 *
 * <p>适用场景：Service 入参校验、业务前置条件检查等，替代手写
 * {@code if (...) { throw ...; }} 的样板代码。
 *
 * <p>线程安全性：工具类仅含静态方法且无共享状态，线程安全。
 *
 * <p>使用示例：
 * <pre>{@code
 * Assert.notNull(user, 40001, "用户不存在");
 * Assert.notEmpty(phone, 40002, "手机号不能为空");
 * Assert.isTrue(amount > 0, 40003, "金额必须大于 0");
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-18
 */
public final class Assert {

    /**
     * 私有构造器，防止工具类被实例化。
     */
    private Assert() {
    }

    /**
     * 断言表达式为真。
     *
     * @param expression 待断言的表达式
     * @param code 校验失败时抛出的错误码
     * @param message 校验失败时的错误描述，可为 null
     * @throws BizException 当 {@code expression} 为 {@code false} 时抛出
     */
    public static void isTrue(boolean expression, int code, String message) {
        if (!expression) {
            throw new BizException(code, message);
        }
    }

    /**
     * 断言对象不为 null。
     *
     * @param object 待检查对象，可为 null
     * @param code 校验失败时抛出的错误码
     * @param message 校验失败时的错误描述，可为 null
     * @throws BizException 当 {@code object} 为 {@code null} 时抛出
     */
    public static void notNull(Object object, int code, String message) {
        if (object == null) {
            throw new BizException(code, message);
        }
    }

    /**
     * 断言字符串非空（仅判断 null 与长度为 0，不剔除空白字符）。
     *
     * @param text 待检查字符串，可为 null
     * @param code 校验失败时抛出的错误码
     * @param message 校验失败时的错误描述，可为 null
     * @throws BizException 当 {@code text} 为 {@code null} 或空字符串时抛出
     */
    public static void notEmpty(String text, int code, String message) {
        if (text == null || text.isEmpty()) {
            throw new BizException(code, message);
        }
    }

    /**
     * 断言集合非空。
     *
     * @param collection 待检查集合，可为 null
     * @param code 校验失败时抛出的错误码
     * @param message 校验失败时的错误描述，可为 null
     * @throws BizException 当 {@code collection} 为 {@code null} 或不含任何元素时抛出
     */
    public static void notEmpty(Collection<?> collection, int code, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new BizException(code, message);
        }
    }

    /**
     * 断言 Map 非空。
     *
     * @param map 待检查 Map，可为 null
     * @param code 校验失败时抛出的错误码
     * @param message 校验失败时的错误描述，可为 null
     * @throws BizException 当 {@code map} 为 {@code null} 或不含任何键值对时抛出
     */
    public static void notEmpty(Map<?, ?> map, int code, String message) {
        if (map == null || map.isEmpty()) {
            throw new BizException(code, message);
        }
    }

    /**
     * 断言数组非空。
     *
     * @param array 待检查数组，可为 null
     * @param code 校验失败时抛出的错误码
     * @param message 校验失败时的错误描述，可为 null
     * @throws BizException 当 {@code array} 为 {@code null} 或长度为 0 时抛出
     */
    public static void notEmpty(Object[] array, int code, String message) {
        if (array == null || array.length == 0) {
            throw new BizException(code, message);
        }
    }
}
