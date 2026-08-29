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
package com.xingheyiye.xingye.kit.io.impl;

import com.xingheyiye.xingye.kit.io.QrCodeGenerator;

import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于 ZXing 的二维码生成/解码实现：通过<b>反射</b>调用 ZXing，本模块编译期对 ZXing 零依赖
 * （源码不 import 任何 com.google.zxing 类）。
 *
 * <p>本类是 {@link QrCodeGenerator} 接口的内置实现（{@link QrCodeGenerator} 的可替换选择之一），
 * 也是 {@code QrCodeUtils} 门面的默认实现。</p>
 *
 * <p>适用场景：希望“默认轻量、按需增强”的工程——只有把 ZXing 放进运行时类路径，
 * 二维码能力才生效；未提供依赖时 {@link #available()} 返回 false、
 * 生成/解码抛出带依赖指引的 {@link IllegalStateException}。</p>
 *
 * <p>线程安全性：无状态，线程安全（反射元数据查找由 JVM 内部缓存）。</p>
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
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public class ZxingQrCodeGenerator implements QrCodeGenerator {

    /** ZXing 二维码写入器类名。 */
    private static final String QR_CODE_WRITER_CLASS = "com.google.zxing.qrcode.QRCodeWriter";
    /** ZXing 位矩阵转图片工具类名（位于 javase 模块）。 */
    private static final String MATRIX_TO_IMAGE_WRITER_CLASS = "com.google.zxing.client.j2se.MatrixToImageWriter";
    /** ZXing 条码格式枚举类名。 */
    private static final String BARCODE_FORMAT_CLASS = "com.google.zxing.BarcodeFormat";
    /** ZXing 编码提示类型枚举类名。 */
    private static final String ENCODE_HINT_TYPE_CLASS = "com.google.zxing.EncodeHintType";
    /** ZXing 位矩阵类名。 */
    private static final String BIT_MATRIX_CLASS = "com.google.zxing.common.BitMatrix";
    /** ZXing 图像亮度源抽象类名。 */
    private static final String LUMINANCE_SOURCE_CLASS = "com.google.zxing.LuminanceSource";
    /** ZXing 基于 BufferedImage 的亮度源实现类名（位于 javase 模块）。 */
    private static final String BUFFERED_IMAGE_LUMINANCE_SOURCE_CLASS =
            "com.google.zxing.client.j2se.BufferedImageLuminanceSource";
    /** ZXing 二值化器抽象类名。 */
    private static final String BINARIZER_CLASS = "com.google.zxing.Binarizer";
    /** ZXing 混合二值化器类名。 */
    private static final String HYBRID_BINARIZER_CLASS = "com.google.zxing.common.HybridBinarizer";
    /** ZXing 二值位图类名。 */
    private static final String BINARY_BITMAP_CLASS = "com.google.zxing.common.BinaryBitmap";
    /** ZXing 多格式读取器类名。 */
    private static final String MULTI_FORMAT_READER_CLASS = "com.google.zxing.MultiFormatReader";
    /** ZXing 解码结果类名。 */
    private static final String RESULT_CLASS = "com.google.zxing.Result";
    /** 二维码内容编码：UTF-8。 */
    private static final String CONTENT_CHARSET = "UTF-8";

    /**
     * 探测 ZXing 是否在运行时类路径上（检查二维码写入器与矩阵转图片工具两个类）。
     *
     * @return 两个探测类均可加载返回 true；任一缺失（未引入 ZXing 依赖）返回 false
     */
    public static boolean available() {
        try {
            Class.forName(QR_CODE_WRITER_CLASS);
            Class.forName(MATRIX_TO_IMAGE_WRITER_CLASS);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 生成二维码图片。
     *
     * @param content 二维码内容文本，不可为 null 且不能为空串（中文按 UTF-8 编码）
     * @param width 图片宽度（像素），必须大于 0
     * @param height 图片高度（像素），必须大于 0
     * @return 黑白二维码图片，永不为 null
     * @throws IllegalArgumentException content 为空或宽高非正数
     * @throws IllegalStateException ZXing 不在类路径（异常消息含依赖坐标指引），或反射调用 ZXing 失败（保留 cause）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public BufferedImage generate(String content, int width, int height) {
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("content 不能为空");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("宽高必须为正数: " + width + "x" + height);
        }
        ensureAvailable();
        try {
            Class<?> writerClass = Class.forName(QR_CODE_WRITER_CLASS);
            Object writer = writerClass.newInstance();
            Class<?> barcodeFormatClass = Class.forName(BARCODE_FORMAT_CLASS);
            // 反射获取枚举常量 BarcodeFormat.QR_CODE（raw type 转换来满足 Enum.valueOf 的签名）
            Object qrCodeFormat = Enum.valueOf((Class) barcodeFormatClass, "QR_CODE");
            Class<?> hintTypeClass = Class.forName(ENCODE_HINT_TYPE_CLASS);
            Map<Object, Object> hints = new HashMap<Object, Object>();
            hints.put(Enum.valueOf((Class) hintTypeClass, "CHARACTER_SET"), CONTENT_CHARSET);
            Object bitMatrix = writerClass
                    .getMethod("encode", String.class, barcodeFormatClass, int.class, int.class, Map.class)
                    .invoke(writer, content, qrCodeFormat, Integer.valueOf(width), Integer.valueOf(height), hints);
            Class<?> bitMatrixClass = Class.forName(BIT_MATRIX_CLASS);
            Object image = Class.forName(MATRIX_TO_IMAGE_WRITER_CLASS)
                    .getMethod("toBufferedImage", bitMatrixClass)
                    .invoke(null, bitMatrix); // 静态方法，接收者传 null
            return (BufferedImage) image;
        } catch (ReflectiveOperationException e) {
            throw asIllegalState("生成二维码失败", e);
        }
    }

    /**
     * 解码图片中的二维码文本（链路：BufferedImageLuminanceSource → HybridBinarizer →
     * BinaryBitmap → MultiFormatReader#decodeWithState）。
     *
     * @param image 含二维码的图片，不可为 null
     * @return 解码出的文本，永不为 null（可能为空串）
     * @throws IllegalArgumentException image 为 null
     * @throws IllegalStateException ZXing 不在类路径、图片中没有可识别二维码
     *                               或反射调用失败（均保留 cause）
     */
    @Override
    public String decode(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("image 不能为 null");
        }
        ensureAvailable();
        try {
            Class<?> luminanceSourceClass = Class.forName(LUMINANCE_SOURCE_CLASS);
            Object source = Class.forName(BUFFERED_IMAGE_LUMINANCE_SOURCE_CLASS)
                    .getConstructor(BufferedImage.class)
                    .newInstance(image);
            Object binarizer = Class.forName(HYBRID_BINARIZER_CLASS)
                    .getConstructor(luminanceSourceClass)
                    .newInstance(source);
            Class<?> binarizerClass = Class.forName(BINARIZER_CLASS);
            Class<?> binaryBitmapClass = Class.forName(BINARY_BITMAP_CLASS);
            Object bitmap = binaryBitmapClass.getConstructor(binarizerClass).newInstance(binarizer);
            Class<?> readerClass = Class.forName(MULTI_FORMAT_READER_CLASS);
            Object reader = readerClass.newInstance();
            Object result = readerClass
                    .getMethod("decodeWithState", binaryBitmapClass)
                    .invoke(reader, bitmap);
            return (String) Class.forName(RESULT_CLASS).getMethod("getText").invoke(result);
        } catch (ReflectiveOperationException e) {
            throw asIllegalState("解码二维码失败", e);
        }
    }

    // ------------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------------

    /**
     * 确认 ZXing 可用，否则抛出带依赖指引的异常。
     */
    private static void ensureAvailable() {
        if (!available()) {
            throw new IllegalStateException(
                    "ZXing 不在类路径上：二维码功能需要在运行时类路径添加 com.google.zxing:core 与 "
                            + "com.google.zxing:javase 两个依赖（示例坐标：com.google.zxing:core:3.5.3、"
                            + "com.google.zxing:javase:3.5.3）。本模块编译期零依赖，通过反射调用 ZXing，"
                            + "不会在编译期或类加载期引入对它的依赖。");
        }
    }

    /**
     * 把反射异常统一包装为 IllegalStateException：解包 InvocationTargetException 展示底层真实异常消息，
     * 同时保留原始反射异常作为 cause。
     */
    private static IllegalStateException asIllegalState(String message, ReflectiveOperationException e) {
        Throwable root = (e instanceof InvocationTargetException && e.getCause() != null) ? e.getCause() : e;
        return new IllegalStateException(message + ": " + root, e);
    }
}
