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
package com.xingheyiye.xingye.kit.notify;

/**
 * 验证码生成策略契约：把“用哪些字符、按什么规则产出验证码”抽象为可替换端口。
 *
 * <p>一句话职责：将验证码字符集的选取从 {@link VerificationCode} 中剥离，
 * 使业务可按安全策略自由切换字符集与生成算法。</p>
 *
 * <p>内置选择：</p>
 * <ul>
 *     <li>{@code com.xingheyiye.xingye.kit.notify.impl.NumericCodeGenerator}：
 *         纯数字 0-9，易于输入，适合手机号短信验证码；</li>
 *     <li>{@code com.xingheyiye.xingye.kit.notify.impl.AlphanumericCodeGenerator}：
 *         数字 + 大小写字母，熵更高，适合邮箱验证码、兑换码。</li>
 * </ul>
 * <p>需要其它字符集（如去掉易混淆字符的防歧义集）或自定义算法（如避免连续重复字符）时，
 * 自行实现本接口并注入 {@link VerificationCode} 即可。</p>
 *
 * <p>线程安全性：接口不约定状态，线程安全性由实现类声明（内置两个实现共享
 * {@link java.security.SecureRandom}，线程安全，可并发调用）。</p>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public interface CodeGenerator {

    /**
     * 生成指定长度的验证码。
     *
     * @param length 验证码长度，必须大于 0
     * @return 生成的验证码字符串，不会为 null
     * @throws IllegalArgumentException length 小于等于 0
     */
    String generate(int length);
}
