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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * 敏感数据脱敏工具：按 {@link SensitiveType} 规则脱敏字符串，或反射脱敏标注 {@link Sensitive} 的实体字段。
 *
 * <p>一句话职责：把"手机号/身份证/邮箱/姓名/银行卡/地址"等敏感值替换为保留少量明文的等长掩码串。</p>
 *
 * <p>内置选择：{@link SensitiveType} 提供手机号/身份证/邮箱/姓名/银行卡/地址 6 种常用脱敏规则；
 * 需要自定义规则（车牌、IPv6、正则等）时，实现 {@link MaskingStrategy} 接口并调用
 * {@link #mask(String, MaskingStrategy)} 即可。</p>
 *
 * <p>适用场景：日志打印前的对象净化、对外接口 VO 的敏感字段处理、客服系统展示。</p>
 *
 * <p>线程安全性：全静态方法且无共享可变状态，线程安全。
 * 注意 maskObject 会直接修改传入对象本身（返回同一引用）。</p>
 *
 * <p>实体标注示例：</p>
 * <pre>{@code
 * public class UserVO {
 *     private String userName;
 *
 *     @Sensitive(SensitiveType.PHONE)
 *     private String phone;
 *
 *     @Sensitive(SensitiveType.ID_CARD)
 *     private String idCard;
 * }
 *
 * UserVO safe = SensitiveMask.maskObject(userVO);
 * // safe == userVO，其中 phone -> 138****5678，idCard -> 110101****1234
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-29
 */
public final class SensitiveMask {

    /** 手机号保留前缀位数（位） */
    private static final int PHONE_HEAD_KEEP = 3;

    /** 手机号保留后缀位数（位） */
    private static final int PHONE_TAIL_KEEP = 4;

    /** 身份证/银行卡/地址共用的前缀保留位数（位） */
    private static final int GENERIC_HEAD_KEEP = 6;

    /** 身份证/银行卡/地址共用的后缀保留位数（位） */
    private static final int GENERIC_TAIL_KEEP = 4;

    private SensitiveMask() {
    }

    /**
     * 按策略类型脱敏字符串。
     *
     * <p>统一约定：值为 null 或空串时原样返回；长度不足该类型最小保留位数时
     * 整体替换为与原值等长的 *（保持长度一致，避免额外泄露长度信息）。</p>
     *
     * @param value 原始值，可为 null 或空串（此时原样返回）
     * @param type  脱敏策略类型，不能为 null
     * @return 脱敏后的字符串，不会为 null（入参为 null 时返回 null）
     * @throws IllegalArgumentException type 为 null 时抛出
     */
    public static String mask(String value, SensitiveType type) {
        if (type == null) {
            throw new IllegalArgumentException("type 不能为 null");
        }
        if (value == null || value.length() == 0) {
            return value;
        }
        switch (type) {
            case PHONE:
                return keepHeadAndTail(value, PHONE_HEAD_KEEP, PHONE_TAIL_KEEP);
            case ID_CARD:
                return keepHeadAndTail(value, GENERIC_HEAD_KEEP, GENERIC_TAIL_KEEP);
            case BANK_CARD:
                return keepHeadAndTail(value, GENERIC_HEAD_KEEP, GENERIC_TAIL_KEEP);
            case ADDRESS:
                return keepHeadAndTail(value, GENERIC_HEAD_KEEP, GENERIC_TAIL_KEEP);
            case NAME:
                return maskName(value);
            case EMAIL:
                return maskEmail(value);
            default:
                // 枚举已穷举，理论上不可达；保留兜底避免新增枚举值时静默泄露原值
                return stars(value.length());
        }
    }

    /**
     * 按自定义策略脱敏字符串（内置 6 种 {@link SensitiveType} 之外的自定义规则入口）。
     *
     * <p>null 与空串的原样返回、掩码长度等约定由策略实现方负责（建议与原文等长）。</p>
     *
     * @param value 原始值，可为 null 或空串（此时原样返回，取决于策略实现）
     * @param strategy 自定义脱敏策略，不能为 null
     * @return 脱敏后的字符串，不会为 null（入参为 null 且策略遵循约定时返回 null）
     * @throws IllegalArgumentException strategy 为 null 时抛出
     */
    public static String mask(String value, MaskingStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("strategy 不能为 null");
        }
        return strategy.mask(value);
    }

    /**
     * 反射脱敏实体对象中标注 {@link Sensitive} 的 String 字段（含父类字段），并回写原对象。
     *
     * <p>说明：方法直接修改并返回传入对象本身（同一引用）；仅处理 String 类型的实例字段，
     * 静态字段与非 String 字段即使标注注解也会被忽略。</p>
     *
     * @param bean 待脱敏实体，不能为 null
     * @param <T>  实体类型
     * @return 传入的同一对象（字段已脱敏），不会为 null
     * @throws IllegalArgumentException bean 为 null 时抛出
     * @throws IllegalStateException    反射读取/回写字段失败时抛出（原因链包含底层异常）
     */
    public static <T> T maskObject(T bean) {
        if (bean == null) {
            throw new IllegalArgumentException("bean 不能为 null");
        }
        Class<?> type = bean.getClass();
        // 沿继承链向上遍历全部类层级（Object 本身无业务字段，到其为止）
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !String.class.equals(field.getType())) {
                    continue;
                }
                Sensitive annotation = field.getAnnotation(Sensitive.class);
                if (annotation == null) {
                    continue;
                }
                // 绕过 private 访问限制的原因：脱敏需要直接读写字段本身，
                // 不走 getter/setter 可避免子类覆写访问器带来的不可预期副作用
                field.setAccessible(true);
                try {
                    Object value = field.get(bean);
                    if (value instanceof String) {
                        field.set(bean, mask((String) value, annotation.value()));
                    }
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("脱敏回写字段失败: " + field.getName(), e);
                }
            }
            type = type.getSuperclass();
        }
        return bean;
    }

    /**
     * 姓名脱敏：保留首字符（姓氏），其余以 * 填充。
     *
     * @param value 原始姓名
     * @return 脱敏结果，如 "张三" -> "张*"；单字整体为 "*"
     */
    private static String maskName(String value) {
        if (value.length() < 2) {
            // 单字姓名无法同时做到保留与遮蔽，整体替换为 *
            return stars(value.length());
        }
        return value.charAt(0) + stars(value.length() - 1);
    }

    /**
     * 邮箱脱敏：保留本地部分首字符与 @ 及其后域名。
     *
     * @param value 原始邮箱
     * @return 脱敏结果，如 "alice@example.com" -> "a****@example.com"
     */
    private static String maskEmail(String value) {
        int atIndex = value.indexOf('@');
        if (atIndex <= 0 || atIndex == value.length() - 1) {
            // 缺少合法本地部分或域名时整体脱敏
            return stars(value.length());
        }
        return value.charAt(0) + stars(atIndex - 1) + value.substring(atIndex);
    }

    /**
     * 保留头尾、遮蔽中间的通用脱敏。
     *
     * @param value    原始值
     * @param headKeep 头部保留字符数
     * @param tailKeep 尾部保留字符数
     * @return 脱敏结果
     */
    private static String keepHeadAndTail(String value, int headKeep, int tailKeep) {
        int length = value.length();
        if (length < headKeep + tailKeep) {
            // 长度不足最小保留位数：整体替换为等长 *
            return stars(length);
        }
        return value.substring(0, headKeep) + stars(length - headKeep - tailKeep)
                + value.substring(length - tailKeep);
    }

    /**
     * 生成指定数量的 * 串。
     *
     * @param count 数量，小于等于 0 时返回空串
     * @return 星号串，不会为 null
     */
    private static String stars(int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append('*');
        }
        return builder.toString();
    }
}
