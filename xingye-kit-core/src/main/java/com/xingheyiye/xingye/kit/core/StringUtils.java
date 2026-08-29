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

import java.util.Map;

/**
 * 字符串工具类，提供判空、敏感信息脱敏、驼峰/下划线互转与占位符格式化等常用能力。
 *
 * <p>适用场景：入参判空、日志与展示层的敏感字段脱敏（手机号/身份证/邮箱/姓名/银行卡）、
 * 数据库字段与 Java 属性命名互转、简单模板渲染等。
 *
 * <p>线程安全性：本类无任何可变状态，所有方法均为无副作用的纯函数，线程安全。
 *
 * <p>所有方法均为 null 安全：入参为 {@code null} 时按各方法 Javadoc 说明处理
 * （通常原样返回 {@code null}），不会抛出 {@code NullPointerException}。
 *
 * <p>使用示例：
 * <pre>{@code
 * StringUtils.maskPhone("13812345678");        // 138****5678
 * StringUtils.maskEmail("rain@example.com");   // r***@example.com
 * StringUtils.camelToUnderline("userName");    // user_name
 * StringUtils.format("用户{}登录", "张三");      // 用户张三登录
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-18
 */
public final class StringUtils {

    /** 手机号脱敏保留的前缀位数 */
    private static final int PHONE_PREFIX_KEEP = 3;

    /** 手机号脱敏保留的后缀位数 */
    private static final int PHONE_SUFFIX_KEEP = 4;

    /** 身份证脱敏保留的前缀位数 */
    private static final int ID_CARD_PREFIX_KEEP = 3;

    /** 身份证脱敏保留的后缀位数 */
    private static final int ID_CARD_SUFFIX_KEEP = 4;

    /** 邮箱本地部分多于 1 个字符时，首字符后追加的固定掩码字符数 */
    private static final int EMAIL_MASK_LENGTH = 3;

    /** 银行卡脱敏保留的前缀位数 */
    private static final int BANK_CARD_PREFIX_KEEP = 4;

    /** 银行卡脱敏保留的后缀位数 */
    private static final int BANK_CARD_SUFFIX_KEEP = 4;

    /** Map 模板占位符的起始标记 */
    private static final String MAP_PLACEHOLDER_START = "${";

    /** Map 模板占位符的结束标记 */
    private static final char MAP_PLACEHOLDER_END = '}';

    /** 顺序占位符标记 */
    private static final String SEQUENTIAL_PLACEHOLDER = "{}";

    /** 驼峰转下划线时插入的分隔符 */
    private static final char UNDERLINE_SEPARATOR = '_';

    /**
     * 私有构造器，防止工具类被实例化。
     */
    private StringUtils() {
    }

    /**
     * 判断字符串是否为空（null 或长度为 0）。
     *
     * @param str 待检查字符串，可为 null
     * @return 为 {@code null} 或空字符串时返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    /**
     * 判断字符串是否非空（不为 null 且长度大于 0），不剔除空白字符。
     *
     * @param str 待检查字符串，可为 null
     * @return 非 {@code null} 且非空字符串时返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * 判断字符串是否为空白（null、空串或仅含空白字符）。
     *
     * @param str 待检查字符串，可为 null
     * @return 为 {@code null}、空字符串或仅由空白字符组成时返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().length() == 0;
    }

    /**
     * 判断字符串是否非空白。
     *
     * @param str 待检查字符串，可为 null
     * @return 含至少一个非空白字符时返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * 手机号脱敏：保留前 3 位与后 4 位，中间以 {@code *} 掩码，如 {@code 13812345678 -> 138****5678}。
     *
     * @param phone 手机号，可为 null
     * @return 脱敏结果；入参为 {@code null} 时返回 {@code null}；
     *         长度不足 7 位时全部以 {@code *} 掩码（长度与原串一致）
     */
    public static String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        if (phone.length() < PHONE_PREFIX_KEEP + PHONE_SUFFIX_KEEP) {
            return repeat("*", phone.length());
        }
        return phone.substring(0, PHONE_PREFIX_KEEP)
                + repeat("*", phone.length() - PHONE_PREFIX_KEEP - PHONE_SUFFIX_KEEP)
                + phone.substring(phone.length() - PHONE_SUFFIX_KEEP);
    }

    /**
     * 身份证号脱敏：保留前 3 位与后 4 位，中间以 {@code *} 掩码。
     *
     * @param idCard 身份证号，可为 null
     * @return 脱敏结果；入参为 {@code null} 时返回 {@code null}；
     *         长度不大于 7 位时全部以 {@code *} 掩码（长度与原串一致）
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null) {
            return null;
        }
        if (idCard.length() <= ID_CARD_PREFIX_KEEP + ID_CARD_SUFFIX_KEEP) {
            return repeat("*", idCard.length());
        }
        return idCard.substring(0, ID_CARD_PREFIX_KEEP)
                + repeat("*", idCard.length() - ID_CARD_PREFIX_KEEP - ID_CARD_SUFFIX_KEEP)
                + idCard.substring(idCard.length() - ID_CARD_SUFFIX_KEEP);
    }

    /**
     * 邮箱脱敏：本地部分仅保留首字符并追加掩码，域名原样保留，
     * 如 {@code rain@example.com -> r***@example.com}。
     *
     * <p>本地部分仅 1 个字符时，输出首字符加一个 {@code *}（如 {@code a@x.com -> a*@x.com}）。
     *
     * @param email 邮箱地址，可为 null
     * @return 脱敏结果；入参为 {@code null} 时返回 {@code null}；
     *         不含 {@code @} 或本地部分为空（无法按邮箱结构脱敏）时原样返回
     */
    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return email;
        }
        String localPart = email.substring(0, atIndex);
        String maskedLocal;
        if (localPart.length() == 1) {
            maskedLocal = localPart + "*";
        } else {
            maskedLocal = localPart.charAt(0) + repeat("*", EMAIL_MASK_LENGTH);
        }
        return maskedLocal + email.substring(atIndex);
    }

    /**
     * 中文姓名脱敏：仅保留首字符，其余字符逐位以 {@code *} 掩码并保持长度，
     * 如 {@code 张三丰 -> 张**}。
     *
     * @param name 姓名，可为 null
     * @return 脱敏结果；入参为 {@code null} 时返回 {@code null}；
     *         长度不大于 1 时原样返回
     */
    public static String maskName(String name) {
        if (name == null) {
            return null;
        }
        if (name.length() <= 1) {
            return name;
        }
        StringBuilder builder = new StringBuilder(name.length());
        builder.append(name.charAt(0));
        for (int i = 1; i < name.length(); i++) {
            builder.append('*');
        }
        return builder.toString();
    }

    /**
     * 银行卡号脱敏：保留前 4 位与后 4 位，中间以 {@code *} 掩码。
     *
     * @param card 银行卡号，可为 null
     * @return 脱敏结果；入参为 {@code null} 时返回 {@code null}；
     *         长度不大于 8 位时全部以 {@code *} 掩码（长度与原串一致）
     */
    public static String maskBankCard(String card) {
        if (card == null) {
            return null;
        }
        if (card.length() <= BANK_CARD_PREFIX_KEEP + BANK_CARD_SUFFIX_KEEP) {
            return repeat("*", card.length());
        }
        return card.substring(0, BANK_CARD_PREFIX_KEEP)
                + repeat("*", card.length() - BANK_CARD_PREFIX_KEEP - BANK_CARD_SUFFIX_KEEP)
                + card.substring(card.length() - BANK_CARD_SUFFIX_KEEP);
    }

    /**
     * 驼峰转下划线：在每个大写字母前插入下划线并转为小写，
     * 如 {@code userName -> user_name}（连续大写如 {@code userID -> user_i_d}）。
     *
     * @param text 待转换字符串，可为 null
     * @return 下划线风格字符串；入参为 {@code null} 或空串时原样返回
     */
    public static String camelToUnderline(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder builder = new StringBuilder(text.length() + 4);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    builder.append(UNDERLINE_SEPARATOR);
                }
                builder.append(Character.toLowerCase(c));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    /**
     * 下划线转驼峰：将 {@code _} 后的首字母转为大写并移除下划线，
     * 如 {@code user_name -> userName}。
     *
     * @param text 待转换字符串，可为 null
     * @return 驼峰风格字符串；入参为 {@code null} 或空串时原样返回
     */
    public static String underlineToCamel(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder builder = new StringBuilder(text.length());
        boolean upperNext = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == UNDERLINE_SEPARATOR) {
                upperNext = true;
            } else if (upperNext) {
                builder.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }

    /**
     * 以 {@code ${key}} 占位符渲染模板。
     *
     * <p>占位符对应的 key 在 Map 中不存在时保留占位符原样输出；
     * key 存在但值为 {@code null} 时替换为字符串 {@code "null"}。
     *
     * @param template 模板字符串，可为 null
     * @param params 参数表，可为 null
     * @return 渲染结果；{@code template} 为 {@code null} 时返回 {@code null}；
     *         {@code params} 为 {@code null} 或为空 Map 时返回模板原文
     */
    public static String format(String template, Map<String, ? extends Object> params) {
        if (template == null) {
            return null;
        }
        if (params == null || params.isEmpty()) {
            return template;
        }
        StringBuilder builder = new StringBuilder(template.length() + 32);
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '$' && template.startsWith(MAP_PLACEHOLDER_START, i)) {
                int end = template.indexOf(MAP_PLACEHOLDER_END, i + MAP_PLACEHOLDER_START.length());
                if (end > i) {
                    String key = template.substring(i + MAP_PLACEHOLDER_START.length(), end);
                    if (params.containsKey(key)) {
                        Object value = params.get(key);
                        builder.append(value == null ? "null" : String.valueOf(value));
                        i = end + 1;
                        continue;
                    }
                }
            }
            builder.append(c);
            i++;
        }
        return builder.toString();
    }

    /**
     * 以 {@code {}} 顺序占位符渲染模板，按参数顺序逐个替换。
     *
     * <p>占位符个数多于参数个数时，多余的占位符原样保留；
     * 参数个数多于占位符时，多余的参数被忽略；参数为 {@code null} 时替换为字符串 {@code "null"}。
     *
     * @param template 模板字符串，可为 null
     * @param args 顺序参数，可为 null（等价于无参数）
     * @return 渲染结果；{@code template} 为 {@code null} 时返回 {@code null}；
     *         无参数时返回模板原文
     */
    public static String format(String template, Object... args) {
        if (template == null) {
            return null;
        }
        if (args == null || args.length == 0) {
            return template;
        }
        StringBuilder builder = new StringBuilder(template.length() + 32);
        int argIndex = 0;
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '{' && template.startsWith(SEQUENTIAL_PLACEHOLDER, i) && argIndex < args.length) {
                builder.append(String.valueOf(args[argIndex]));
                argIndex++;
                i += SEQUENTIAL_PLACEHOLDER.length();
            } else {
                builder.append(c);
                i++;
            }
        }
        return builder.toString();
    }

    /**
     * 重复拼接字符串指定次数。
     *
     * @param text 待重复的字符串，可为 null
     * @param count 重复次数，小于等于 0 时返回空字符串
     * @return 重复结果；{@code text} 为 {@code null} 时返回 {@code null}；
     *         {@code count <= 0} 时返回空字符串
     */
    public static String repeat(String text, int count) {
        if (text == null) {
            return null;
        }
        if (count <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(text);
        }
        return builder.toString();
    }
}
