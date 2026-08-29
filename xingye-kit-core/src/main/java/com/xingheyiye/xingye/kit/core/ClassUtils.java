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
 * @since 2026-08-19
 */
package com.xingheyiye.xingye.kit.core;

import java.lang.reflect.InvocationTargetException;

/**
 * 类操作工具，提供类存在性探测、类加载、反射实例化与 CGLIB 代理类还原能力。
 *
 * <p>适用场景：SPI/插件式加载前的类探测、按类名动态加载与实例化、
 * AOP 环境下获取被代理对象的真实类型等。
 *
 * <p>线程安全性：工具类仅含静态方法且无共享状态，线程安全。
 *
 * <p>使用示例：
 * <pre>{@code
 * if (ClassUtils.isPresent("com.example.Foo")) {
 *     Foo foo = ClassUtils.newInstance(ClassUtils.forName("com.example.Foo"));
 * }
 * Class<?> targetClass = ClassUtils.getUserClass(proxyObject); // 还原 CGLIB 代理的真实类
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-19
 */
public final class ClassUtils {

    /** CGLIB 代理类类名中的分隔标记，形如 "com.example.Foo$$EnhancerBySpringCGLIB$$1" */
    private static final String CGLIB_CLASS_SEPARATOR = "$$";

    /**
     * 私有构造器，防止工具类被实例化。
     */
    private ClassUtils() {
    }

    /**
     * 判断指定类在当前运行环境中是否可加载。
     *
     * @param className 全限定类名，可为 null
     * @return 类存在且可加载时返回 {@code true}；类名为空或加载失败时返回 {@code false}
     */
    public static boolean isPresent(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        try {
            forName(className);
            return true;
        } catch (IllegalStateException ex) {
            // 类不存在或不可加载均视为“不存在”
            return false;
        }
    }

    /**
     * 加载指定类，按顺序尝试：当前线程上下文类加载器 -&gt; 本类类加载器 -&gt; {@code Class.forName}。
     *
     * @param className 全限定类名，不能为 null 或空串
     * @return 加载到的 Class 对象，恒不为 null
     * @throws IllegalArgumentException 当 {@code className} 为 {@code null} 或空串时抛出
     * @throws IllegalStateException 当三种方式均加载失败时抛出，cause 保留最后一次 ClassNotFoundException
     */
    public static Class<?> forName(String className) {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("className 不能为空");
        }
        ClassNotFoundException notFound = null;
        // 1) 优先使用线程上下文类加载器，兼容 Web 容器等多类加载器环境
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            try {
                return Class.forName(className, true, contextLoader);
            } catch (ClassNotFoundException ex) {
                notFound = ex;
            }
        }
        // 2) 其次使用本类自身的类加载器
        try {
            return Class.forName(className, true, ClassUtils.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            notFound = ex;
        }
        // 3) 最后使用 Class.forName 的默认加载行为兜底
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            notFound = ex;
        }
        throw new IllegalStateException("无法加载类: " + className, notFound);
    }

    /**
     * 通过无参构造器反射实例化指定类。
     *
     * @param clazz 目标类，不能为 null，且需存在可访问的无参构造器
     * @param <T> 目标类型
     * @return 新建实例，恒不为 null
     * @throws IllegalArgumentException 当 {@code clazz} 为 {@code null} 时抛出
     * @throws IllegalStateException 当类缺少无参构造器、构造器不可访问或构造过程抛出异常时抛出，
     *                               cause 保留原始受检异常
     */
    public static <T> T newInstance(Class<T> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("clazz 不能为 null");
        }
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException | IllegalAccessException
                | InvocationTargetException | InstantiationException ex) {
            throw new IllegalStateException("实例化失败: " + clazz.getName()
                    + "（需存在可访问的无参构造器）", ex);
        }
    }

    /**
     * 获取对象的真实类型：当类名包含 CGLIB 代理标记 {@code "$$"} 时，
     * 取标记前缀重新加载并返回被代理的原始类；否则返回对象自身的 Class。
     *
     * @param target 目标对象，可为 null
     * @return 真实类型；{@code target} 为 {@code null} 时返回 {@code null}；
     *         原始类无法加载时退回代理类本身的 Class
     */
    public static Class<?> getUserClass(Object target) {
        if (target == null) {
            return null;
        }
        Class<?> clazz = target.getClass();
        String name = clazz.getName();
        int index = name.indexOf(CGLIB_CLASS_SEPARATOR);
        if (index > 0) {
            try {
                return forName(name.substring(0, index));
            } catch (IllegalStateException ex) {
                // 原始类不可加载时退回代理类本身，避免影响调用方主流程
                return clazz;
            }
        }
        return clazz;
    }
}
