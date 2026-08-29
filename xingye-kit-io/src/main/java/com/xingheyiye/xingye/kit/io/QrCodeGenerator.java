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
package com.xingheyiye.xingye.kit.io;

import java.awt.image.BufferedImage;

/**
 * 二维码生成与解码契约：把“底层用哪个二维码库、如何生成/解码”抽象为可替换端口。
 *
 * <p>一句话职责：让业务代码面向本接口编程，底层实现可自由切换。</p>
 *
 * <p>内置选择：{@code com.xingheyiye.xingye.kit.io.impl.ZxingQrCodeGenerator}
 * 基于 ZXing 提供真实的生成/解码能力（编译期零依赖，运行时需把 ZXing 放入类路径，
 * 见 {@code ZxingQrCodeGenerator} 的依赖说明）。需要其它实现（如原生 C 库封装、服务端
 * 二维码 API）时，自行实现本接口并通过 {@link QrCodeUtils} 的带生成器重载方法使用即可。</p>
 *
 * <p>线程安全性：接口不约束线程安全性，由实现方声明；
 * 内置 ZXing 实现无共享可变状态，反射元数据查找由 JVM 内部缓存，线程安全。</p>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public interface QrCodeGenerator {

    /**
     * 生成二维码图片。
     *
     * @param content 二维码内容文本，不可为 null 且不能为空串（中文按实现约定的字符集编码）
     * @param width 图片宽度（像素），必须大于 0
     * @param height 图片高度（像素），必须大于 0
     * @return 二维码图片，永不为 null
     * @throws IllegalArgumentException content 为空或宽高非正数
     * @throws RuntimeException 生成失败（具体异常类型由实现方声明）
     */
    BufferedImage generate(String content, int width, int height);

    /**
     * 解码图片中的二维码文本。
     *
     * @param image 含二维码的图片，不可为 null
     * @return 解码出的文本，永不为 null（可能为空串）
     * @throws IllegalArgumentException image 为 null
     * @throws RuntimeException 图片中没有可识别二维码或解码失败（具体异常类型由实现方声明）
     */
    String decode(BufferedImage image);
}
