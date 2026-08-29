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
 * @since 2026-08-23
 */
package com.xingheyiye.xingye.kit.notify.impl;

/**
 * Webhook JSON 拼装工具（包内共享）：手工完成 JSON 字符串转义与通用文本消息体构造。
 *
 * <p>一句话职责：以零依赖方式提供 JSON 转义与钉钉/企业微信通用 text 消息体。</p>
 *
 * <p>适用场景：本包内各 Webhook 客户端构造请求体；仅覆盖转义与固定结构的极小场景，
 * 复杂 JSON 仍建议使用方引入完整 JSON 库。</p>
 *
 * <p>线程安全性：无状态工具类（仅私有构造与静态方法），线程安全，可并发调用。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * String payload = WebhookJson.textPayload("服务已上线");
 * // {"msgtype":"text","text":{"content":"服务已上线"}}
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-23
 */
final class WebhookJson {

    /**
     * 私有构造：禁止实例化工具类。
     */
    private WebhookJson() {
    }

    /**
     * 把普通字符串转义为可安全嵌入 JSON 双引号内的内容。
     *
     * <p>转义规则：{@code "} → {@code \"}、{@code \} → {@code \\}、
     * 换行 → {@code \n}、回车 → {@code \r}、制表符 → {@code \t}；
     * 其余小于 0x20 的控制字符统一转为 4 位小写十六进制的 Unicode 转义序列（如 U+0001 转为反斜杠 u 加 0001）；
     * 其它字符（含中文）原样输出，最终字节由调用方按 UTF-8 编码发送。</p>
     *
     * @param text 待转义文本；为 null 时视为空串
     * @return 转义后的 JSON 字符串内容（不含首尾双引号），不会为 null
     */
    static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                    break;
            }
        }
        return escaped.toString();
    }

    /**
     * 构造钉钉/企业微信通用的 text 类型消息体。
     *
     * @param content 消息正文；为 null 时视为空串
     * @return 形如 {@code {"msgtype":"text","text":{"content":"..."}}} 的 JSON 串，不会为 null
     */
    static String textPayload(String content) {
        return "{\"msgtype\":\"text\",\"text\":{\"content\":\"" + escape(content) + "\"}}";
    }
}
