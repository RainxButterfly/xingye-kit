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

/**
 * 敏感数据脱敏策略类型：每种类型对应一条固定的保留/遮蔽规则。
 *
 * <p>一句话职责：枚举常见 PII 字段的脱敏规则，供 {@link Sensitive} 注解引用与
 * {@link SensitiveMask#mask(String, SensitiveType)} 执行。</p>
 *
 * <p>适用场景：日志输出、接口返回 VO、工单展示等需要隐藏敏感信息的场合。</p>
 *
 * <p>线程安全性：枚举常量天然线程安全。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * String masked = SensitiveMask.mask("13812345678", SensitiveType.PHONE);  // 138****5678
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-29
 */
public enum SensitiveType {

    /**
     * 手机号：保留前 3 位与后 4 位，中间以 * 填充，如 {@code 138****5678}；
     * 长度不足 7 位时整体替换为等长 *。
     */
    PHONE,

    /**
     * 身份证号：保留前 6 位与后 4 位，如 {@code 110101****1234}；
     * 长度不足 10 位时整体替换为等长 *。
     */
    ID_CARD,

    /**
     * 邮箱：保留本地部分首字符与 @ 及其后域名，如 {@code a****@example.com}；
     * 无 @、本地部分为空或整体过短时整体替换为等长 *。
     */
    EMAIL,

    /**
     * 姓名：保留首字符（姓氏），其余以 * 填充，如 {@code 张**}；
     * 单字姓名整体替换为 *。
     */
    NAME,

    /**
     * 银行卡号：保留前 6 位与后 4 位，如 {@code 622202****1234}；
     * 长度不足 10 位时整体替换为等长 *。
     */
    BANK_CARD,

    /**
     * 地址：保留前 6 个与后 4 个字符，中间以 * 填充；
     * 长度不足 10 个字符时整体替换为等长 *。
     */
    ADDRESS
}
