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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 编解码器（包内私有）：仅满足 JWT header/payload 的序列化与反序列化需求。
 *
 * <p>一句话职责：以手写递归下降解析器实现最小 JSON 子集，避免为 JWT 引入第三方依赖。</p>
 *
 * <p>支持范围：JSON 对象、数组、字符串（含引号、反斜杠、斜杠、退格、换页、换行、回车、
 * 制表符以及"反斜杠 u + 四位十六进制"形式的 Unicode 转义）、
 * 数字（整数解析为 Long，含小数/指数的解析为 Double）、true/false/null，
 * 对象与数组可任意嵌套。不支持注释、尾随逗号与单引号字符串。</p>
 *
 * <p>线程安全性：全静态方法且无共享可变状态，线程安全。</p>
 *
 * <p>重要：本类仅供 {@link JwtWrapper} 内部使用（package-private），
 * 不保证完整 JSON 规范兼容，请勿在其它场景直接依赖。</p>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-29
 */
final class JwtJson {

    /** StringBuilder 初始容量估计（字节），避免小型 JSON 频繁扩容 */
    private static final int BUILDER_CAPACITY = 128;

    private JwtJson() {
    }

    /**
     * 将 Map 序列化为 JSON 字符串。
     *
     * @param map 待序列化对象，可为 null（输出 "null"）；值支持 null/Boolean/Number/String/List
     *            与嵌套 Map，其它类型按其 toString 结果以字符串输出
     * @return JSON 字符串，不会为 null
     */
    static String write(Map<String, Object> map) {
        StringBuilder builder = new StringBuilder(BUILDER_CAPACITY);
        writeValue(map, builder);
        return builder.toString();
    }

    /**
     * 解析 JSON 对象字符串。
     *
     * @param json JSON 文本，不能为 null；顶层必须为对象 "{...}"，允许首尾空白
     * @return 解析结果（键为 String，值为 Long/Double/Boolean/String/List/Map/null），
     *         不会为 null；空对象返回空 Map
     * @throws IllegalArgumentException json 为 null、格式非法或顶层不是对象时抛出，消息附带出错位置
     */
    static Map<String, Object> readObject(String json) {
        Parser parser = new Parser(json);
        parser.skipWhitespace();
        Map<String, Object> result = parser.parseObject();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw parser.error("对象结束后存在多余字符");
        }
        return result;
    }

    /**
     * 递归序列化任意 JSON 值。
     *
     * @param value   待序列化值
     * @param builder 输出缓冲
     */
    private static void writeValue(Object value, StringBuilder builder) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String) {
            writeString((String) value, builder);
        } else if (value instanceof Boolean) {
            builder.append(value.toString());
        } else if (value instanceof Map) {
            builder.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                writeString(String.valueOf(entry.getKey()), builder);
                builder.append(':');
                writeValue(entry.getValue(), builder);
            }
            builder.append('}');
        } else if (value instanceof List) {
            builder.append('[');
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                writeValue(item, builder);
            }
            builder.append(']');
        } else if (value instanceof Number) {
            builder.append(value.toString());
        } else {
            // 未知类型退化为字符串，保证 write 不因业务类型失败
            writeString(String.valueOf(value), builder);
        }
    }

    /**
     * 序列化并转义 JSON 字符串。
     *
     * @param text    原始文本
     * @param builder 输出缓冲
     */
    private static void writeString(String text, StringBuilder builder) {
        builder.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        // 控制字符必须转义为"反斜杠 u + 四位十六进制"形式，否则产出非法 JSON
                        builder.append(String.format("\\u%04x", Integer.valueOf(c)));
                    } else {
                        builder.append(c);
                    }
                    break;
            }
        }
        builder.append('"');
    }

    /**
     * 手写递归下降解析器。
     */
    private static final class Parser {

        /** 待解析原文 */
        private final String src;

        /** 当前解析位置（下标，从 0 开始） */
        private int pos;

        /**
         * 构造解析器。
         *
         * @param src 待解析原文
         */
        Parser(String src) {
            this.src = src;
        }

        /**
         * 判断是否已到达文本末尾。
         *
         * @return true 表示已消费全部字符
         */
        boolean isAtEnd() {
            return pos >= src.length();
        }

        /**
         * 构造带位置信息的非法格式异常。
         *
         * @param message 错误描述
         * @return 已附加位置信息的异常实例
         */
        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + "（位置 " + pos + "）");
        }

        /**
         * 跳过空白字符（空格、制表符、回车、换行）。
         */
        void skipWhitespace() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        /**
         * 解析 JSON 对象。
         *
         * @return 键值对（保持出现顺序）
         */
        Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            skipWhitespace();
            if (!isAtEnd() && src.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (isAtEnd() || src.charAt(pos) != '"') {
                    throw error("期望对象键为字符串");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                map.put(key, parseValue());
                skipWhitespace();
                if (isAtEnd()) {
                    throw error("对象未闭合");
                }
                char c = src.charAt(pos++);
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw error("期望 ',' 或 '}'");
                }
            }
        }

        /**
         * 解析 JSON 数组。
         *
         * @return 元素列表
         */
        List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<Object>();
            skipWhitespace();
            if (!isAtEnd() && src.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(parseValue());
                skipWhitespace();
                if (isAtEnd()) {
                    throw error("数组未闭合");
                }
                char c = src.charAt(pos++);
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw error("期望 ',' 或 ']'");
                }
            }
        }

        /**
         * 按首字符分派解析任意 JSON 值。
         *
         * @return 解析值；null 字面量返回 null
         */
        Object parseValue() {
            skipWhitespace();
            char c = current();
            if (c == '"') {
                return parseString();
            }
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == 't') {
                expectWord("true");
                return Boolean.TRUE;
            }
            if (c == 'f') {
                expectWord("false");
                return Boolean.FALSE;
            }
            if (c == 'n') {
                expectWord("null");
                return null;
            }
            if (c == '-' || (c >= '0' && c <= '9')) {
                return parseNumber();
            }
            throw error("非法字符 '" + c + "'");
        }

        /**
         * 解析 JSON 字符串（含转义序列）。
         *
         * @return 解码后的字符串
         */
        String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder(BUILDER_CAPACITY);
            while (true) {
                if (isAtEnd()) {
                    throw error("字符串未闭合");
                }
                char c = src.charAt(pos++);
                if (c == '"') {
                    return builder.toString();
                }
                if (c == '\\') {
                    if (isAtEnd()) {
                        throw error("转义序列不完整");
                    }
                    char escaped = src.charAt(pos++);
                    switch (escaped) {
                        case '"':
                            builder.append('"');
                            break;
                        case '\\':
                            builder.append('\\');
                            break;
                        case '/':
                            builder.append('/');
                            break;
                        case 'b':
                            builder.append('\b');
                            break;
                        case 'f':
                            builder.append('\f');
                            break;
                        case 'n':
                            builder.append('\n');
                            break;
                        case 'r':
                            builder.append('\r');
                            break;
                        case 't':
                            builder.append('\t');
                            break;
                        case 'u':
                            if (pos + 4 > src.length()) {
                                throw error("\\u 转义序列不完整");
                            }
                            String hex = src.substring(pos, pos + 4);
                            try {
                                builder.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException e) {
                                throw error("非法的 \\u 转义: " + hex);
                            }
                            pos += 4;
                            break;
                        default:
                            throw error("非法转义字符 '\\" + escaped + "'");
                    }
                } else if (c < 0x20) {
                    throw error("字符串中存在未转义的控制字符");
                } else {
                    builder.append(c);
                }
            }
        }

        /**
         * 解析 JSON 数字：无小数点与指数时返回 Long（超出 Long 范围退化为 Double），否则返回 Double。
         *
         * @return Long 或 Double 实例
         */
        Object parseNumber() {
            int start = pos;
            if (!isAtEnd() && src.charAt(pos) == '-') {
                pos++;
            }
            if (readDigits() == 0) {
                throw error("数字格式非法");
            }
            boolean decimal = false;
            if (!isAtEnd() && src.charAt(pos) == '.') {
                decimal = true;
                pos++;
                if (readDigits() == 0) {
                    throw error("小数部分缺失");
                }
            }
            if (!isAtEnd() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                decimal = true;
                pos++;
                if (!isAtEnd() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    pos++;
                }
                if (readDigits() == 0) {
                    throw error("指数部分缺失");
                }
            }
            String text = src.substring(start, pos);
            if (!decimal) {
                // 整数优先按 Long 存储，便于 JWT 秒级时间戳直接读取
                try {
                    return Long.valueOf(text);
                } catch (NumberFormatException e) {
                    return Double.valueOf(text);
                }
            }
            return Double.valueOf(text);
        }

        /**
         * 连续消费数字字符。
         *
         * @return 消费的数字字符个数
         */
        int readDigits() {
            int count = 0;
            while (!isAtEnd()) {
                char c = src.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                    count++;
                } else {
                    break;
                }
            }
            return count;
        }

        /**
         * 读取并校验当前位置的字符。
         *
         * @return 当前字符
         */
        char current() {
            if (isAtEnd()) {
                throw error("JSON 意外结束");
            }
            return src.charAt(pos);
        }

        /**
         * 校验当前字符与期望一致并前进一位。
         *
         * @param expected 期望字符
         */
        void expect(char expected) {
            if (isAtEnd() || src.charAt(pos) != expected) {
                throw error("期望字符 '" + expected + "'");
            }
            pos++;
        }

        /**
         * 校验当前位置起匹配指定字面量（true/false/null）并前进。
         *
         * @param word 字面量文本
         */
        void expectWord(String word) {
            if (!src.startsWith(word, pos)) {
                throw error("期望字面量 " + word);
            }
            pos += word.length();
        }
    }
}
