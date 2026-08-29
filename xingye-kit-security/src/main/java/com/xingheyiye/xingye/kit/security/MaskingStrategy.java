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
 * @since 2026-08-30
 */
package com.xingheyiye.xingye.kit.security;

/**
 * 敏感数据脱敏策略契约：把“把原始串替换成掩码串”的规则抽象为可替换端口。
 *
 * <p>一句话职责：为 {@link SensitiveMask} 提供内置策略之外的<b>自定义脱敏</b>通道——
 * 内置的 {@link SensitiveType} 枚举覆盖手机号/身份证/邮箱等常见类型（即“几个选择”），
 * 当需要更贴合业务的规则（如车牌号、IPv6、自定义正则、加密后回显）时，实现本接口即可。</p>
 *
 * <p>调用约定：实现方应自行处理 null/空串（通常原样返回）；掩码结果建议与原文等长，
 * 避免额外泄露长度信息；实现类应为无状态且线程安全（会被多线程并发调用）。</p>
 *
 * <p>线程安全性：接口不约束线程安全性，由实现方声明；内置策略均为无状态纯函数，线程安全。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 自定义策略：车牌号，保留前 1 位与后 1 位
 * MaskingStrategy plate = v -> v.length() <= 2 ? "**" : v.charAt(0) + "****" + v.charAt(v.length() - 1);
 * String masked = SensitiveMask.mask("京A12345", plate);   // 京****5
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public interface MaskingStrategy {

    /**
     * 将原始值脱敏为掩码串。
     *
     * @param value 原始值，可为 null 或空串
     * @return 脱敏后的字符串；约定入参为 null 时返回 null，为长度不足的短串时返回等长掩码
     */
    String mask(String value);
}
