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
 * @since 2026-08-27
 */

package com.xingheyiye.xingye.kit.io;

import com.xingheyiye.xingye.kit.io.impl.ZxingQrCodeGenerator;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * 二维码生成与解码的静态门面：默认委托内置的 ZXing 实现（{@link QrCodeGenerator} 的可替换选择之一），
 * 并提供可注入自定义 {@link QrCodeGenerator} 的重载方法。
 *
 * <p>一句话职责：无状态静态工具，屏蔽底层二维码库细节；默认使用 ZXing（编译期零依赖，
 * 运行时需把 ZXing 放入类路径），需要其它实现（原生库封装、服务端二维码 API）时实现
 * {@link QrCodeGenerator} 接口并传入重载方法即可。</p>
 *
 * <p>适用场景：希望“默认轻量、按需增强”的工程——只有把 ZXing 放进运行时类路径，
 * 二维码能力才生效，其余功能完全不受影响；未提供依赖时 {@link #available()} 返回 false、
 * 生成/解码抛出带依赖指引的 {@link IllegalStateException}。</p>
 *
 * <p>线程安全性：无状态静态门面 + 线程安全的内置实现，线程安全。</p>
 *
 * <p>运行时依赖（Maven 坐标示例）：</p>
 * <pre>{@code
 * <dependency>
 *     <groupId>com.google.zxing</groupId>
 *     <artifactId>core</artifactId>
 *     <version>3.5.3</version>
 * </dependency>
 * <dependency>
 *     <groupId>com.google.zxing</groupId>
 *     <artifactId>javase</artifactId>
 *     <version>3.5.3</version>
 * </dependency>
 * }</pre>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * if (QrCodeUtils.available()) {
 *     BufferedImage image = QrCodeUtils.generate("https://example.com", 300, 300);
 *     byte[] png = QrCodeUtils.generatePng("https://example.com", 300, 300);
 *     String text = QrCodeUtils.decode(image);
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-27
 */
public final class QrCodeUtils {

    /** PNG 格式名（ImageIO 插件名）。 */
    private static final String PNG_FORMAT = "png";

    /** 默认生成器：内置 ZXing 实现。 */
    private static final QrCodeGenerator DEFAULT = new ZxingQrCodeGenerator();

    /**
     * 工具类禁止实例化。
     */
    private QrCodeUtils() {
    }

    /**
     * 探测默认的 ZXing 实现是否可用（ZXing 是否在运行时类路径上）。
     *
     * @return ZXing 两个探测类均可加载返回 true；任一缺失（未引入 ZXing 依赖）返回 false
     */
    public static boolean available() {
        return ZxingQrCodeGenerator.available();
    }

    /**
     * 用默认 ZXing 实现生成二维码图片。
     *
     * @param content 二维码内容文本，不可为 null 且不能为空串（中文按 UTF-8 编码）
     * @param width 图片宽度（像素），必须大于 0
     * @param height 图片高度（像素），必须大于 0
     * @return 黑白二维码图片，永不为 null
     * @throws IllegalArgumentException content 为空或宽高非正数
     * @throws IllegalStateException ZXing 不在类路径（异常消息含依赖坐标指引），或反射调用 ZXing 失败（保留 cause）
     */
    public static BufferedImage generate(String content, int width, int height) {
        return DEFAULT.generate(content, width, height);
    }

    /**
     * 用指定生成器生成二维码图片（自定义 {@link QrCodeGenerator} 入口）。
     *
     * @param content 二维码内容文本，不可为 null 且不能为空串
     * @param width 图片宽度（像素），必须大于 0
     * @param height 图片高度（像素），必须大于 0
     * @param generator 二维码生成器，不能为 null
     * @return 二维码图片，永不为 null
     * @throws IllegalArgumentException content 为空、宽高非正数或 generator 为 null
     * @throws RuntimeException 生成失败（异常类型由具体实现声明）
     */
    public static BufferedImage generate(String content, int width, int height, QrCodeGenerator generator) {
        if (generator == null) {
            throw new IllegalArgumentException("generator 不能为 null");
        }
        return generator.generate(content, width, height);
    }

    /**
     * 用默认 ZXing 实现生成二维码并编码为 PNG 字节。
     *
     * @param content 二维码内容文本，不可为 null 且不能为空串（中文按 UTF-8 编码）
     * @param width 图片宽度（像素），必须大于 0
     * @param height 图片高度（像素），必须大于 0
     * @return PNG 文件字节，永不为 null（长度大于 0）
     * @throws IllegalArgumentException content 为空或宽高非正数
     * @throws IllegalStateException ZXing 不可用、PNG 编码器缺失或编码失败（均保留 cause）
     */
    public static byte[] generatePng(String content, int width, int height) {
        return encodePng(DEFAULT.generate(content, width, height));
    }

    /**
     * 用指定生成器生成二维码并编码为 PNG 字节（自定义 {@link QrCodeGenerator} 入口）。
     *
     * @param content 二维码内容文本，不可为 null 且不能为空串
     * @param width 图片宽度（像素），必须大于 0
     * @param height 图片高度（像素），必须大于 0
     * @param generator 二维码生成器，不能为 null
     * @return PNG 文件字节，永不为 null（长度大于 0）
     * @throws IllegalArgumentException content 为空、宽高非正数或 generator 为 null
     * @throws RuntimeException 生成失败（异常类型由具体实现声明）；PNG 编码失败统一抛 IllegalStateException
     */
    public static byte[] generatePng(String content, int width, int height, QrCodeGenerator generator) {
        if (generator == null) {
            throw new IllegalArgumentException("generator 不能为 null");
        }
        return encodePng(generator.generate(content, width, height));
    }

    /**
     * 用默认 ZXing 实现解码图片中的二维码文本。
     *
     * @param image 含二维码的图片，不可为 null
     * @return 解码出的文本，永不为 null（可能为空串）
     * @throws IllegalArgumentException image 为 null
     * @throws IllegalStateException ZXing 不在类路径、图片中没有可识别二维码
     *                               或反射调用失败（均保留 cause）
     */
    public static String decode(BufferedImage image) {
        return DEFAULT.decode(image);
    }

    /**
     * 用指定生成器解码图片中的二维码文本（自定义 {@link QrCodeGenerator} 入口）。
     *
     * @param image 含二维码的图片，不可为 null
     * @param generator 二维码生成器，不能为 null
     * @return 解码出的文本，永不为 null（可能为空串）
     * @throws IllegalArgumentException image 或 generator 为 null
     * @throws RuntimeException 解码失败（异常类型由具体实现声明）
     */
    public static String decode(BufferedImage image, QrCodeGenerator generator) {
        if (generator == null) {
            throw new IllegalArgumentException("generator 不能为 null");
        }
        return generator.decode(image);
    }

    /**
     * 将图片编码为 PNG 字节。
     *
     * @param image 待编码图片，不可为 null
     * @return PNG 文件字节，永不为 null（长度大于 0）
     * @throws IllegalStateException PNG 编码器缺失或编码失败（均保留 cause）
     */
    private static byte[] encodePng(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, PNG_FORMAT, out)) {
                throw new IllegalStateException("当前 JVM 没有可用的 PNG 编码器");
            }
        } catch (IOException e) {
            // 内存流在正常情况下不会抛 IOException，此处兜底包装，保证异常类型统一
            throw new IllegalStateException("生成 PNG 失败", e);
        }
        return out.toByteArray();
    }
}
