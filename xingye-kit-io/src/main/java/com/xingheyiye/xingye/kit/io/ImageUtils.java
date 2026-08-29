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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.imageio.ImageIO;

/**
 * 纯 java.awt.image/ImageIO 实现的轻量图片工具：读取、等比缩略图、文字水印与格式转换。
 *
 * <p>适用场景：生成缩略图、给导出图片盖章水印、批量格式转换；
 * 全部为<b>纯软件渲染</b>（不依赖显示设备/显卡），在无显示器的 headless 服务器上可直接运行。</p>
 *
 * <p>线程安全性：无状态静态工具类，线程安全；单次调用内部创建并释放各自的 Graphics2D。</p>
 *
 * <p>渲染质量约定：缩放使用双线性插值（VALUE_INTERPOLATION_BILINEAR）并开质量优先（VALUE_RENDER_QUALITY）；
 * 水印文字为纯白色（简化实现，不做描边）。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * BufferedImage photo = ImageUtils.readImage(new File("photo.jpg"));
 * ImageUtils.thumbnail(new File("photo.jpg"), 320, 240, new File("photo-small.png"), "png");
 * BufferedImage marked = ImageUtils.watermarkText(photo, "xingye-kit", "bottom-right", 0.6f, 28);
 * ImageUtils.convert(new File("photo.bmp"), "png", new File("photo.png"));
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-27
 */
public final class ImageUtils {

    /** 水印文字与图片边缘的留白：10 像素。 */
    private static final int WATERMARK_MARGIN_PX = 10;
    /** 支持的水印位置（不可变集合，校验与文档共用同一来源）。 */
    private static final List<String> WATERMARK_POSITIONS = Collections.unmodifiableList(
            Arrays.asList("top-left", "top-right", "bottom-left", "bottom-right", "center"));

    /**
     * 工具类禁止实例化。
     */
    private ImageUtils() {
    }

    /**
     * 读取图片文件。
     *
     * @param file 图片文件，不可为 null；支持 JPEG、PNG、BMP、GIF 等 JDK 内置插件能解码的格式
     * @return 解码后的图片，永不为 null
     * @throws java.io.FileNotFoundException 文件不存在（IOException 子类）
     * @throws IOException 读取失败或格式无法识别（此时 ImageIO.read 返回 null，本方法转为 IOException）
     * @throws NullPointerException file 为 null
     */
    public static BufferedImage readImage(File file) throws IOException {
        Objects.requireNonNull(file, "file 不能为 null");
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            // ImageIO.read 对无法识别的格式返回 null 而非抛异常，这里统一转为 IOException
            throw new IOException("无法识别或解码图片: " + file.getAbsolutePath());
        }
        return image;
    }

    /**
     * 生成等比缩略图：宽高按比例缩小且从不放大（原图不超限时按原尺寸重绘输出）。
     *
     * @param src 源图片文件，不可为 null 且必须可解码
     * @param maxWidth 缩略图最大宽度（像素），必须大于 0
     * @param maxHeight 缩略图最大高度（像素），必须大于 0
     * @param dest 输出文件，不可为 null（父目录不存在时自动创建；已存在则覆盖）
     * @param formatName 输出格式名（ImageIO 插件名，如 "png"、"jpg"），不可为 null 且不能为空串
     * @throws IllegalArgumentException maxWidth/maxHeight 非正数，或 formatName 为空
     * @throws IOException 源读取、目录创建或编码写出失败（含无对应格式编码器的情况）
     * @throws NullPointerException 任一参数为 null
     */
    public static void thumbnail(File src, int maxWidth, int maxHeight, File dest, String formatName)
            throws IOException {
        Objects.requireNonNull(src, "src 不能为 null");
        Objects.requireNonNull(dest, "dest 不能为 null");
        if (maxWidth <= 0 || maxHeight <= 0) {
            throw new IllegalArgumentException("maxWidth/maxHeight 必须为正数: " + maxWidth + "x" + maxHeight);
        }
        requireFormatName(formatName);
        BufferedImage source = readImage(src);
        // 等比缩放：取两个方向比例的较小者，且封顶 1.0（不放大）
        double scale = Math.min(1.0d, Math.min(
                (double) maxWidth / source.getWidth(),
                (double) maxHeight / source.getHeight()));
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage thumbnail = new BufferedImage(targetWidth, targetHeight,
                source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = thumbnail.createGraphics();
        try {
            // 双线性插值让缩小过渡更平滑；质量优先让插值计算更精细
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose(); // Graphics2D 持有原生资源，必须显式释放
        }
        writeImage(thumbnail, formatName, dest);
    }

    /**
     * 在图片副本上绘制半透明白色文字水印（返回新图，原图不被修改）。
     *
     * @param src 原图，不可为 null
     * @param text 水印文本，不可为 null（过长文本可能超出图片边界，超出部分被裁掉）
     * @param position 水印位置，必须是 top-left/top-right/bottom-left/bottom-right/center 之一
     * @param alpha 透明度（0f 完全透明 - 1f 完全不透明），必须满足 0 &lt;= alpha &lt;= 1 且非 NaN
     * @param fontSize 字号（像素，逻辑字体尺寸），必须大于 0
     * @return 叠加了水印的新图片（尺寸与原图一致），永不为 null
     * @throws IllegalArgumentException position 非法、alpha 越界或 fontSize 非正数
     * @throws NullPointerException src 或 text 为 null
     */
    public static BufferedImage watermarkText(BufferedImage src, String text, String position,
            float alpha, int fontSize) {
        Objects.requireNonNull(src, "src 不能为 null");
        Objects.requireNonNull(text, "text 不能为 null");
        if (position == null || !WATERMARK_POSITIONS.contains(position)) {
            throw new IllegalArgumentException("不支持的水印位置: " + position
                    + "（可选: " + WATERMARK_POSITIONS + "）");
        }
        if (Float.isNaN(alpha) || alpha < 0f || alpha > 1f) {
            throw new IllegalArgumentException("alpha 必须在 [0, 1] 区间: " + alpha);
        }
        if (fontSize <= 0) {
            throw new IllegalArgumentException("fontSize 必须为正数: " + fontSize);
        }
        BufferedImage result = new BufferedImage(src.getWidth(), src.getHeight(),
                src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(src, 0, 0, null); // 先铺原图，再叠水印
            // SrcOver：标准的“源按透明度叠加在目标之上”合成方式
            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
            graphics.setColor(Color.WHITE); // 简化实现：纯白文字，不做描边
            FontMetrics metrics = graphics.getFontMetrics();
            int textWidth = metrics.stringWidth(text);
            int x;
            int y; // y 为文字基线位置
            if ("top-left".equals(position)) {
                x = WATERMARK_MARGIN_PX;
                y = WATERMARK_MARGIN_PX + metrics.getAscent();
            } else if ("top-right".equals(position)) {
                x = src.getWidth() - textWidth - WATERMARK_MARGIN_PX;
                y = WATERMARK_MARGIN_PX + metrics.getAscent();
            } else if ("bottom-left".equals(position)) {
                x = WATERMARK_MARGIN_PX;
                y = src.getHeight() - WATERMARK_MARGIN_PX - metrics.getDescent();
            } else if ("bottom-right".equals(position)) {
                x = src.getWidth() - textWidth - WATERMARK_MARGIN_PX;
                y = src.getHeight() - WATERMARK_MARGIN_PX - metrics.getDescent();
            } else { // center
                x = (src.getWidth() - textWidth) / 2;
                y = (src.getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            }
            graphics.drawString(text, x, y);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    /**
     * 转换图片格式：解码输入文件并按目标格式重新编码写出。
     *
     * @param in 输入图片文件，不可为 null 且必须可解码
     * @param targetFormat 目标格式名（ImageIO 插件名，如 "png"、"jpg"），不可为 null 且不能为空串
     * @param out 输出文件，不可为 null（父目录不存在时自动创建；已存在则覆盖）
     * @throws IllegalArgumentException targetFormat 为空
     * @throws IOException 输入读取、目录创建或编码写出失败（含无对应格式编码器的情况）
     * @throws NullPointerException 任一参数为 null
     */
    public static void convert(File in, String targetFormat, File out) throws IOException {
        Objects.requireNonNull(in, "in 不能为 null");
        Objects.requireNonNull(out, "out 不能为 null");
        requireFormatName(targetFormat);
        BufferedImage image = readImage(in);
        writeImage(image, targetFormat, out);
    }

    // ------------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------------

    /**
     * 校验格式名非空。
     */
    private static void requireFormatName(String formatName) {
        if (formatName == null || formatName.isEmpty()) {
            throw new IllegalArgumentException("formatName 不能为空");
        }
    }

    /**
     * 编码并写出图片：父目录不存在时自动创建；ImageIO.write 返回 false 视为无可用编码器。
     */
    private static void writeImage(BufferedImage image, String formatName, File dest) throws IOException {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建输出目录: " + parent);
        }
        if (!ImageIO.write(image, formatName, dest)) {
            throw new IOException("没有可用的 " + formatName + " 编码器或该格式不被支持: "
                    + dest.getAbsolutePath());
        }
    }
}
