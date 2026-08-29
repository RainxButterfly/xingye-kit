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
package com.xingheyiye.xingye.kit.notify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通知模板渲染工具：把模板中的 "${key}" 占位符替换为参数值。
 *
 * <p>一句话职责：以一次正则扫描完成占位符替换、"$${" 转义与缺失参数校验。</p>
 *
 * <p>适用场景：短信、邮件、Webhook 文案与验证码内容的统一组装。
 * 渲染规则：</p>
 * <ul>
 *     <li>"${key}" 被 params 中键为 key 的值替换；value 为 null 时替换为空串；</li>
 *     <li>"$${" 为转义序列，输出字面 "${"；</li>
 *     <li>模板中的占位符在 params 中缺失 key 时抛出 IllegalArgumentException，
 *         异常消息列出全部缺失 key。</li>
 * </ul>
 *
 * <p>线程安全性：无状态工具类（仅持有预编译正则与私有构造），线程安全，可并发调用。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * Map<String, Object> params = new HashMap<String, Object>();
 * params.put("name", "张三");
 * params.put("code", "123456");
 * String text = NotificationTemplate.render(
 *         "您好 ${name}，验证码为 ${code}，10 分钟内有效；转义示例：$${code}",
 *         params);
 * // 输出：您好 张三，验证码为 123456，10 分钟内有效；转义示例：${code}
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-23
 */
public final class NotificationTemplate {

    /** 占位符/转义匹配正则（类加载时预编译）：优先匹配 "$${" 转义，其次匹配 "${key}" 占位符 */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\$\\{|\\$\\{([^}]*)\\}");

    /**
     * 私有构造：禁止实例化工具类。
     */
    private NotificationTemplate() {
    }

    /**
     * 渲染模板：把所有 "${key}" 占位符替换为 params 中对应的值。
     *
     * <p>替换值中的 "$" 与 "\\" 按字面输出（内部已做 quote 处理）；
     * params 中多余的键值会被忽略，不影响结果。</p>
     *
     * @param template 模板字符串，不能为 null；不含占位符时原样返回
     * @param params 参数表，不能为 null；键为占位符名（不含 "${}"），值可为 null（替换为空串）
     * @return 渲染结果字符串，不会为 null
     * @throws IllegalArgumentException template 或 params 为 null，或模板中的占位符在 params
     *                                  中缺失时抛出；缺失时异常消息会列出全部缺失 key
     */
    public static String render(String template, Map<String, ? extends Object> params) {
        if (template == null) {
            throw new IllegalArgumentException("template 不能为 null");
        }
        if (params == null) {
            throw new IllegalArgumentException("params 不能为 null");
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer(template.length());
        List<String> missingKeys = null;
        while (matcher.find()) {
            String key = matcher.group(1);
            if (key == null) {
                // 命中 "$${" 转义分支：输出字面 "${"
                matcher.appendReplacement(result, Matcher.quoteReplacement("${"));
                continue;
            }
            if (!params.containsKey(key)) {
                if (missingKeys == null) {
                    missingKeys = new ArrayList<String>();
                }
                missingKeys.add(key);
                // 先按原样占位，便于在全部缺失 key 收集完毕后统一抛出
                matcher.appendReplacement(result, Matcher.quoteReplacement("${" + key + "}"));
                continue;
            }
            Object value = params.get(key);
            String replacement = value == null ? "" : String.valueOf(value);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        if (missingKeys != null) {
            throw new IllegalArgumentException("模板占位符在参数表中缺失: " + missingKeys);
        }
        return result.toString();
    }
}
