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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 纯 Java 自研的二维码编码器：不依赖 ZXing 或任何第三方库，把内容编码为可被
 * 扫码器识别的二维码矩阵，并可输出<b>终端 ASCII 图</b>（半块字符渲染，可直接粘贴到
 * 命令行/CI 日志）。
 *
 * <p>本类是 {@link QrCodeGenerator} 接口的内置实现（{@link QrCodeGenerator} 的可替换选择之一），
 * 与基于 ZXing 的 {@code ZxingQrCodeGenerator} 形成“零依赖纯 Java + 真实 ZXing”的成对选择：
 * 无需任何运行时依赖时用它；需要更强容错或更高版本时用 ZXing 版或自定义实现。</p>
 *
 * <p>能力与限制：</p>
 * <ul>
 *   <li>编码模式：8 位字节模式（UTF-8），支持中文等任意 Unicode 文本；</li>
 *   <li>版本：自动选择 1-40，纠错级别仅内置 L（约 7%）与 M（约 15%）两档；</li>
 *   <li>容量：与标准一致，如 1-L 最多 17 字节、10-M 最多 213 字节、40-L 最多 2956 字节，
 *       超出容量时抛出带指引的异常（需要更高纠错级别请改用 ZXing 版）；</li>
 *   <li>掩码：遍历 8 种掩码按标准罚分规则自动选择最优；</li>
 *   <li>解码：本实现只负责生成，{@link #decode} 抛 {@link UnsupportedOperationException}。</li>
 * </ul>
 *
 * <p>线程安全性：实例仅持有 final 配置字段，内部表格为静态只读，线程安全，可跨线程共享。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * AsciiQrCodeGenerator generator = new AsciiQrCodeGenerator();
 * BufferedImage image = generator.generate("https://example.com", 300, 300); // 纯 Java 生成
 *
 * // 终端 ASCII 图（无需任何库即可在命令行扫码）
 * System.out.print(generator.renderAscii("https://example.com"));
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-30
 */
public class AsciiQrCodeGenerator implements QrCodeGenerator {

    /**
     * 纠错级别（仅内置 L 与 M；更高档位请使用 ZXing 版实现）。
     *
     * @author 星河一叶 (RainxButterfly)
     * @since 2026-08-30
     */
    public enum Level {
        /** L 级：约 7% 纠错，容量最大，终端二维码常用。 */
        L(0b01),
        /** M 级：约 15% 纠错，容量与可靠性均衡，默认档位。 */
        M(0b00);

        /** 格式信息中使用的 2 位纠错级别标识（ISO/IEC 18004）。 */
        private final int formatBits;

        Level(int formatBits) {
            this.formatBits = formatBits;
        }

        int formatBits() {
            return formatBits;
        }
    }

    /**
     * 版本-纠错-分块表：{版本, 每块纠错码字数, 第一组块数, 第一组每块数据码字数, 第二组块数, 第二组每块数据码字数}。
     * 数据来源：ISO/IEC 18004 标准分块表（Level L）。
     */
    private static final int[][] LEVEL_L = {
        {1, 7, 1, 19, 0, 0}, {2, 10, 1, 34, 0, 0}, {3, 15, 1, 55, 0, 0},
        {4, 20, 1, 80, 0, 0}, {5, 26, 1, 108, 0, 0}, {6, 18, 2, 68, 0, 0},
        {7, 20, 2, 78, 0, 0}, {8, 24, 2, 97, 0, 0}, {9, 30, 2, 116, 0, 0},
        {10, 18, 2, 68, 2, 69}, {11, 20, 4, 81, 0, 0}, {12, 24, 2, 92, 2, 93},
        {13, 26, 4, 107, 0, 0}, {14, 30, 3, 115, 1, 116}, {15, 22, 5, 87, 1, 88},
        {16, 24, 5, 98, 1, 99}, {17, 28, 1, 107, 5, 108}, {18, 30, 5, 120, 1, 121},
        {19, 28, 3, 113, 4, 114}, {20, 28, 3, 107, 5, 108}, {21, 28, 4, 116, 4, 117},
        {22, 28, 2, 111, 7, 112}, {23, 30, 4, 121, 5, 122}, {24, 30, 6, 117, 4, 118},
        {25, 26, 8, 106, 4, 107}, {26, 28, 10, 114, 2, 115}, {27, 30, 8, 122, 4, 123},
        {28, 30, 3, 117, 10, 118}, {29, 30, 7, 116, 7, 117}, {30, 30, 5, 115, 10, 116},
        {31, 30, 13, 115, 3, 116}, {32, 30, 17, 115, 0, 0}, {33, 30, 17, 115, 1, 116},
        {34, 30, 13, 115, 6, 116}, {35, 30, 12, 121, 7, 122}, {36, 30, 6, 121, 14, 122},
        {37, 30, 17, 122, 4, 123}, {38, 30, 4, 122, 18, 123}, {39, 30, 20, 117, 4, 118},
        {40, 30, 19, 118, 6, 119},
    };

    /**
     * 版本-纠错-分块表（Level M）。
     */
    private static final int[][] LEVEL_M = {
        {1, 10, 1, 16, 0, 0}, {2, 16, 1, 28, 0, 0}, {3, 26, 1, 44, 0, 0},
        {4, 18, 2, 32, 0, 0}, {5, 24, 2, 43, 0, 0}, {6, 16, 4, 27, 0, 0},
        {7, 18, 4, 31, 0, 0}, {8, 22, 2, 38, 2, 39}, {9, 22, 3, 36, 2, 37},
        {10, 26, 4, 43, 1, 44}, {11, 30, 1, 50, 4, 51}, {12, 22, 6, 36, 2, 37},
        {13, 22, 8, 37, 1, 38}, {14, 24, 4, 40, 5, 41}, {15, 24, 5, 41, 5, 42},
        {16, 28, 7, 45, 3, 46}, {17, 28, 10, 46, 1, 47}, {18, 26, 9, 43, 4, 44},
        {19, 26, 3, 44, 11, 45}, {20, 26, 3, 41, 13, 42}, {21, 26, 17, 42, 0, 0},
        {22, 28, 17, 46, 0, 0}, {23, 28, 4, 47, 14, 48}, {24, 28, 6, 45, 14, 46},
        {25, 28, 8, 47, 13, 48}, {26, 28, 19, 46, 4, 47}, {27, 28, 22, 45, 3, 46},
        {28, 28, 3, 45, 23, 46}, {29, 28, 21, 45, 7, 46}, {30, 28, 19, 47, 10, 48},
        {31, 28, 2, 46, 29, 47}, {32, 28, 10, 46, 23, 47}, {33, 28, 14, 46, 21, 47},
        {34, 28, 14, 46, 23, 47}, {35, 28, 12, 47, 26, 48}, {36, 28, 6, 47, 34, 48},
        {37, 28, 29, 46, 14, 47}, {38, 28, 13, 46, 32, 47}, {39, 28, 40, 47, 7, 48},
        {40, 28, 18, 47, 31, 48},
    };

    /**
     * 对齐图案中心坐标表（ISO/IEC 18004）；版本 1 无对齐图案。
     */
    private static final int[][] ALIGNMENT = {
        {}, {6, 18}, {6, 22}, {6, 26}, {6, 30}, {6, 34}, {6, 22, 38}, {6, 24, 42},
        {6, 26, 46}, {6, 28, 50}, {6, 30, 54}, {6, 32, 58}, {6, 34, 62},
        {6, 26, 46, 66}, {6, 26, 48, 70}, {6, 26, 50, 74}, {6, 30, 54, 78},
        {6, 30, 56, 82}, {6, 30, 58, 86}, {6, 34, 62, 90}, {6, 28, 50, 72, 94},
        {6, 26, 50, 74, 98}, {6, 30, 54, 78, 102}, {6, 28, 54, 80, 106},
        {6, 32, 58, 84, 110}, {6, 30, 58, 86, 114}, {6, 34, 62, 90, 118},
        {6, 26, 50, 74, 98, 122}, {6, 30, 54, 78, 102, 126}, {6, 26, 52, 78, 104, 130},
        {6, 30, 56, 82, 108, 134}, {6, 34, 60, 86, 112, 138}, {6, 30, 58, 86, 114, 142},
        {6, 34, 62, 90, 118, 146}, {6, 30, 54, 78, 102, 126, 150},
        {6, 24, 50, 76, 102, 128, 154}, {6, 28, 54, 80, 106, 132, 158},
        {6, 32, 58, 84, 110, 136, 162}, {6, 26, 54, 82, 110, 138, 166},
        {6, 30, 58, 86, 114, 142, 170},
    };

    /** GF(256) 本原多项式（0x11D），用于 Reed-Solomon 纠错。 */
    private static final int GF_PRIMITIVE = 0x11D;
    /** 格式信息 BCH 生成多项式（度 10）。 */
    private static final int FORMAT_GENERATOR = 0x537;
    /** 格式信息掩码（ISO/IEC 18004 固定值）。 */
    private static final int FORMAT_MASK = 0x5412;
    /** 版本信息 BCH 生成多项式（度 12）。 */
    private static final int VERSION_GENERATOR = 0x1F25;

    /** GF(256) 指数表（长度 512，含循环段避免取模）。 */
    private static final int[] EXP = new int[512];
    /** GF(256) 对数表（长度 256）。 */
    private static final int[] LOG = new int[256];
    /** Reed-Solomon 生成多项式缓存（按度缓存，避免重复计算）。 */
    private static final ConcurrentHashMap<Integer, int[]> GENERATOR_CACHE =
            new ConcurrentHashMap<Integer, int[]>();

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            LOG[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) {
                x ^= GF_PRIMITIVE;
            }
        }
        for (int i = 255; i < 512; i++) {
            EXP[i] = EXP[i - 255];
        }
    }

    /** 纠错级别（默认 M）。 */
    private final Level level;

    /**
     * 以默认 M 级构造。
     */
    public AsciiQrCodeGenerator() {
        this(Level.M);
    }

    /**
     * 以指定纠错级别构造。
     *
     * @param level 纠错级别，不可为 null
     * @throws IllegalArgumentException level 为 null
     */
    public AsciiQrCodeGenerator(Level level) {
        if (level == null) {
            throw new IllegalArgumentException("level 不能为 null");
        }
        this.level = level;
    }

    /**
     * 生成二维码图片（纯 Java 自研编码，白色背景 + 黑色模块 + 4 模块静区）。
     *
     * @param content 二维码内容文本，不可为 null 或空串（中文按 UTF-8 编码）
     * @param width 图片宽度（像素），必须大于 0
     * @param height 图片高度（像素），必须大于 0
     * @return 二维码图片，永不为 null
     * @throws IllegalArgumentException content 为空或宽高非正数
     * @throws IllegalStateException 内容超出当前纠错级别的最大容量时抛出（含容量提示）
     */
    @Override
    public BufferedImage generate(String content, int width, int height) {
        if (width <= 0) {
            throw new IllegalArgumentException("width 必须大于 0: " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException("height 必须大于 0: " + height);
        }
        boolean[][] matrix = encode(content);
        int size = matrix.length;
        int quiet = 4;
        int total = size + quiet * 2;
        // 模块边长取整数，保证模块为正方形；图片居中、四周为白边（静区）
        int moduleSize = Math.max(1, Math.min(width, height) / total);
        int dim = total * moduleSize;
        BufferedImage image = new BufferedImage(dim, dim, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, dim, dim);
            graphics.setColor(Color.BLACK);
            for (int r = 0; r < size; r++) {
                for (int c = 0; c < size; c++) {
                    if (matrix[r][c]) {
                        graphics.fillRect((c + quiet) * moduleSize,
                                (r + quiet) * moduleSize, moduleSize, moduleSize);
                    }
                }
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * 把内容编码为终端 ASCII 二维码字符串（半块字符渲染，2 个模块高度占 1 行文本）。
     *
     * <p>输出可直接粘贴到支持 Unicode 的终端/CI 日志中，用手机扫码即可识别；
     * 四周带 2 模块静区。每行字符数为 {@code (模块边长 + 4)}，可配合等宽字体查看。</p>
     *
     * @param content 二维码内容文本，不可为 null 或空串
     * @return ASCII 二维码字符串（含换行），永不为 null
     * @throws IllegalArgumentException content 为 null/空串
     * @throws IllegalStateException 内容超出容量时抛出
     */
    public String renderAscii(String content) {
        boolean[][] matrix = encode(content);
        int size = matrix.length;
        int quiet = 2;
        int total = size + quiet * 2;
        StringBuilder builder = new StringBuilder((total / 2 + 1) * (total + 1));
        for (int y = 0; y < total; y += 2) {
            for (int x = 0; x < total; x++) {
                boolean top = moduleAt(matrix, size, quiet, x, y);
                boolean bottom = moduleAt(matrix, size, quiet, x, y + 1);
                char ch = top ? (bottom ? '█' : '▀') : (bottom ? '▄' : ' ');
                builder.append(ch);
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    /**
     * 本实现只负责生成，不支持解码。
     *
     * @throws UnsupportedOperationException 始终抛出（如需解码请使用 ZXing 版或自定义实现）
     */
    @Override
    public String decode(BufferedImage image) {
        throw new UnsupportedOperationException(
                "AsciiQrCodeGenerator 仅支持生成；如需解码请使用 ZxingQrCodeGenerator 或自定义 QrCodeGenerator");
    }

    // ------------------------------------------------------------------
    // 编码管线
    // ------------------------------------------------------------------

    /**
     * 完整编码：选择版本 → 构造码字 → 放置数据 → 选择最优掩码 → 返回最终矩阵。
     */
    private boolean[][] encode(String content) {
        if (content == null) {
            throw new IllegalArgumentException("content 不能为 null");
        }
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        if (data.length == 0) {
            throw new IllegalArgumentException("content 不能为空串");
        }
        int version = chooseVersion(data.length);
        int[] codewords = buildCodewords(version, data);
        return selectMask(version, codewords);
    }

    /**
     * 选择能容纳给定字节数的最小版本。
     */
    private int chooseVersion(int numBytes) {
        for (int v = 1; v <= 40; v++) {
            int totalData = totalDataCodewords(v);
            int countBits = v <= 9 ? 8 : 16;
            int requiredBits = 4 + countBits + numBytes * 8;
            if (requiredBits <= totalData * 8) {
                return v;
            }
        }
        int capacity = totalDataCodewords(40);
        int countBits = 16;
        int maxBytes = (capacity * 8 - 4 - countBits) / 8;
        throw new IllegalStateException("内容过长：当前纠错级别 " + level + " 最多容纳约 "
                + maxBytes + " 字节，实际 " + numBytes + " 字节；请缩短内容或改用纠错级别更低的实现");
    }

    /**
     * 构造数据码字 + Reed-Solomon 纠错码字，并按标准交错排列为最终码字序列。
     */
    private int[] buildCodewords(int version, byte[] data) {
        int[] table = levelTable(version);
        int ecPerBlock = table[1];
        int g1Blocks = table[2];
        int g1Data = table[3];
        int g2Blocks = table[4];
        int g2Data = table[5];
        int numBlocks = g1Blocks + g2Blocks;
        int totalData = g1Blocks * g1Data + g2Blocks * g2Data;

        // 组装数据位流：模式(4) + 字符计数 + 数据字节 + 终止符 + 字节对齐 + 填充码字
        int countBits = version <= 9 ? 8 : 16;
        BitBuffer bits = new BitBuffer();
        bits.append(0b0100, 4);
        bits.append(data.length, countBits);
        for (byte b : data) {
            bits.append(b & 0xFF, 8);
        }
        int remaining = totalData * 8 - bits.size();
        bits.append(0, Math.min(4, remaining));
        while (bits.size() % 8 != 0) {
            bits.append(0, 1);
        }
        int[] dataWords = new int[totalData];
        int filledBytes = bits.size() / 8;
        for (int i = 0; i < totalData; i++) {
            dataWords[i] = i < filledBytes ? bits.byteAt(i) : (i % 2 == 0 ? 0xEC : 0x11);
        }

        // 按块拆分 + 每块计算纠错码字
        int[][] blockData = new int[numBlocks][];
        int[][] blockEcc = new int[numBlocks][];
        int idx = 0;
        for (int i = 0; i < g1Blocks; i++) {
            blockData[i] = Arrays.copyOfRange(dataWords, idx, idx + g1Data);
            idx += g1Data;
        }
        for (int i = 0; i < g2Blocks; i++) {
            blockData[g1Blocks + i] = Arrays.copyOfRange(dataWords, idx, idx + g2Data);
            idx += g2Data;
        }
        for (int i = 0; i < numBlocks; i++) {
            blockEcc[i] = computeEcc(blockData[i], ecPerBlock);
        }

        // 交错：先按列交错数据码字，再按列交错纠错码字
        int[] out = new int[totalData + numBlocks * ecPerBlock];
        int p = 0;
        int maxData = Math.max(g1Data, g2Data);
        for (int i = 0; i < maxData; i++) {
            for (int b = 0; b < numBlocks; b++) {
                if (i < blockData[b].length) {
                    out[p++] = blockData[b][i];
                }
            }
        }
        for (int i = 0; i < ecPerBlock; i++) {
            for (int b = 0; b < numBlocks; b++) {
                out[p++] = blockEcc[b][i];
            }
        }
        return out;
    }

    /**
     * 放置功能图案 + 数据码字，然后遍历 8 种掩码选出罚分最低者并写入格式/版本信息。
     */
    private boolean[][] selectMask(int version, int[] codewords) {
        int size = version * 4 + 17;
        boolean[][] base = new boolean[size][size];
        boolean[][] reserved = new boolean[size][size];
        List<int[]> dataCells = new ArrayList<int[]>();
        drawFunctionPatterns(base, reserved, size, version);
        placeData(base, reserved, size, codewords, dataCells);

        int bestMask = 0;
        int bestPenalty = Integer.MAX_VALUE;
        boolean[][] bestMatrix = null;
        int versionBits = version >= 7 ? versionInfo(version) : 0;
        for (int mask = 0; mask < 8; mask++) {
            boolean[][] candidate = new boolean[size][size];
            for (int r = 0; r < size; r++) {
                candidate[r] = base[r].clone();
            }
            for (int[] cell : dataCells) {
                int r = cell[0];
                int c = cell[1];
                if (maskBit(mask, r, c)) {
                    candidate[r][c] = !candidate[r][c];
                }
            }
            drawFormatInfo(candidate, size, formatInfo(level.formatBits(), mask));
            if (version >= 7) {
                drawVersionInfo(candidate, size, versionBits);
            }
            int penalty = penalty(candidate);
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                bestMask = mask;
                bestMatrix = candidate;
            }
        }
        // 记录所选掩码（供日志/调试；字段无对外暴露则忽略返回值）
        @SuppressWarnings("unused")
        int chosenMask = bestMask;
        return bestMatrix;
    }

    // ------------------------------------------------------------------
    // 矩阵绘制
    // ------------------------------------------------------------------

    /**
     * 绘制全部功能图案（定位图案/分隔符/时序图案/对齐图案/暗模块）并标记功能区。
     */
    private static void drawFunctionPatterns(boolean[][] m, boolean[][] reserved, int size, int version) {
        drawFinderRegion(m, reserved, 0, 0);
        drawFinderRegion(m, reserved, 0, size - 8);
        drawFinderRegion(m, reserved, size - 8, 0);

        // 时序图案：第 6 行/列，从索引 8 到 size-9，偶数位置为暗
        for (int i = 8; i < size - 8; i++) {
            m[6][i] = (i % 2 == 0);
            reserved[6][i] = true;
            m[i][6] = (i % 2 == 0);
            reserved[i][6] = true;
        }

        // 对齐图案：跳过与定位图案重叠的角落
        int[] centers = ALIGNMENT[version - 1];
        for (int centerRow : centers) {
            for (int centerCol : centers) {
                if (overlapsFinder(centerRow, centerCol, size)) {
                    continue;
                }
                drawAlignment(m, reserved, centerRow, centerCol);
            }
        }

        // 暗模块：恒为暗，位于 (size-8, 8)
        m[size - 8][8] = true;
        reserved[size - 8][8] = true;

        // 预留格式信息与版本信息区域（数据不可占用）
        reserveFormat(reserved, size);
        if (version >= 7) {
            reserveVersion(reserved, size);
        }
    }

    /**
     * 绘制一个定位图案区域：8×8 全部标记为功能模块，其中左上角 7×7 为定位图案，
     * 第 8 行/列为浅色分隔符（默认 false 即浅色，无需显式绘制）。
     */
    private static void drawFinderRegion(boolean[][] m, boolean[][] reserved, int top, int left) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                reserved[top + r][left + c] = true;
            }
        }
        for (int r = 0; r < 7; r++) {
            for (int c = 0; c < 7; c++) {
                boolean dark = (r == 0 || r == 6 || c == 0 || c == 6)
                        || (r >= 2 && r <= 4 && c >= 2 && c <= 4);
                m[top + r][left + c] = dark;
            }
        }
    }

    /**
     * 绘制 5×5 对齐图案（边框与中心为暗）。
     */
    private static void drawAlignment(boolean[][] m, boolean[][] reserved, int centerRow, int centerCol) {
        for (int r = -2; r <= 2; r++) {
            for (int c = -2; c <= 2; c++) {
                int rr = centerRow + r;
                int cc = centerCol + c;
                reserved[rr][cc] = true;
                m[rr][cc] = Math.abs(r) == 2 || Math.abs(c) == 2 || (r == 0 && c == 0);
            }
        }
    }

    /**
     * 对齐图案是否与三个定位图案之一重叠（位于 9×9 角区）。
     */
    private static boolean overlapsFinder(int row, int col, int size) {
        boolean topLeft = row <= 8 && col <= 8;
        boolean topRight = row <= 8 && col >= size - 9;
        boolean bottomLeft = row >= size - 9 && col <= 8;
        return topLeft || topRight || bottomLeft;
    }

    /**
     * 预留格式信息区域（复制 1 与复制 2）。
     */
    private static void reserveFormat(boolean[][] reserved, int size) {
        // 复制 1：左上角（跳过时序第 6 行/列）
        for (int i = 0; i <= 8; i++) {
            if (i != 6) {
                reserved[8][i] = true;
                reserved[i][8] = true;
            }
        }
        // 复制 2：右上水平段 + 左下垂直段
        for (int c = size - 8; c < size; c++) {
            reserved[8][c] = true;
        }
        for (int r = size - 7; r < size; r++) {
            reserved[r][8] = true;
        }
    }

    /**
     * 预留版本信息区域（两处 3×6 块）。
     */
    private static void reserveVersion(boolean[][] reserved, int size) {
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 3; j++) {
                reserved[i][size - 11 + j] = true;
                reserved[size - 11 + j][i] = true;
            }
        }
    }

    /**
     * 按标准蛇形（zigzag）把码字位写入数据区，并记录数据单元坐标供掩码使用。
     */
    private static void placeData(boolean[][] m, boolean[][] reserved, int size, int[] codewords,
            List<int[]> dataCells) {
        boolean upwards = true;
        int bitIndex = 0;
        for (int col = size - 1; col > 0; col -= 2) {
            if (col == 6) {
                col--; // 跳过时序图案列
            }
            for (int i = 0; i < size; i++) {
                int row = upwards ? size - 1 - i : i;
                for (int j = 0; j < 2; j++) {
                    int c = col - j;
                    if (!reserved[row][c]) {
                        m[row][c] = bit(codewords, bitIndex);
                        dataCells.add(new int[] {row, c});
                        bitIndex++;
                    }
                }
            }
            upwards = !upwards;
        }
    }

    /**
     * 写入 15 位格式信息（两处复制）。
     */
    private static void drawFormatInfo(boolean[][] m, int size, int formatBits) {
        // 复制 1：左上角（第 8 行水平 8 位 + 第 8 列垂直 7 位）
        for (int i = 0; i <= 5; i++) {
            m[8][i] = bit(formatBits, 14 - i);
        }
        m[8][7] = bit(formatBits, 8);
        m[8][8] = bit(formatBits, 7);
        int[] verticalRows = {7, 5, 4, 3, 2, 1, 0};
        for (int i = 0; i < verticalRows.length; i++) {
            m[verticalRows[i]][8] = bit(formatBits, 6 - i);
        }
        // 复制 2：左下垂直 7 位 + 右上水平 8 位
        for (int i = 0; i <= 6; i++) {
            m[size - 1 - i][8] = bit(formatBits, i);
        }
        for (int i = 0; i <= 7; i++) {
            m[8][size - 8 + i] = bit(formatBits, 7 + i);
        }
    }

    /**
     * 写入 18 位版本信息（两处 3×6 块，版本号 ≥ 7 时）。
     */
    private static void drawVersionInfo(boolean[][] m, int size, int versionBits) {
        // 左下块：6 列 × 3 行，bit 序号 = 列 * 3 + 行偏移
        for (int c = 0; c < 6; c++) {
            for (int ro = 0; ro < 3; ro++) {
                m[size - 11 + ro][c] = bit(versionBits, c * 3 + ro);
            }
        }
        // 右上块：3 列 × 6 行，bit 序号 = 列偏移 * 6 + 行
        for (int r = 0; r < 6; r++) {
            for (int co = 0; co < 3; co++) {
                m[r][size - 11 + co] = bit(versionBits, co * 6 + r);
            }
        }
    }

    // ------------------------------------------------------------------
    // 掩码与罚分
    // ------------------------------------------------------------------

    /**
     * 返回掩码条件：为 true 时翻转数据单元颜色（标准 8 种掩码公式）。
     */
    private static boolean maskBit(int mask, int row, int col) {
        switch (mask) {
            case 0:
                return (row + col) % 2 == 0;
            case 1:
                return row % 2 == 0;
            case 2:
                return col % 3 == 0;
            case 3:
                return (row + col) % 3 == 0;
            case 4:
                return (row / 2 + col / 3) % 2 == 0;
            case 5:
                return (row * col) % 2 + (row * col) % 3 == 0;
            case 6:
                return ((row * col) % 2 + (row * col) % 3) % 2 == 0;
            default:
                return ((row + col) % 2 + (row * col) % 3) % 2 == 0;
        }
    }

    /**
     * 计算掩码罚分（规则 N1-N4），越低代表图案越利于扫码。
     */
    private static int penalty(boolean[][] m) {
        int size = m.length;
        int penalty = 0;
        for (int i = 0; i < size; i++) {
            penalty += penaltyLine(m, i, true);
            penalty += penaltyLine(m, i, false);
        }
        // N2：2×2 同色块
        for (int r = 0; r < size - 1; r++) {
            for (int c = 0; c < size - 1; c++) {
                boolean v = m[r][c];
                if (m[r][c + 1] == v && m[r + 1][c] == v && m[r + 1][c + 1] == v) {
                    penalty += 3;
                }
            }
        }
        // N4：暗模块比例偏离 50%
        int dark = 0;
        for (boolean[] row : m) {
            for (boolean v : row) {
                if (v) {
                    dark++;
                }
            }
        }
        int total = size * size;
        penalty += (Math.abs(dark * 100 / total - 50) / 5) * 10;
        return penalty;
    }

    /**
     * 对单行/单列计算 N1（连续同色）与 N3（定位图案样式）罚分。
     */
    private static int penaltyLine(boolean[][] m, int index, boolean rowWise) {
        int size = m.length;
        int penalty = 0;
        boolean[] line = new boolean[size];
        for (int i = 0; i < size; i++) {
            line[i] = rowWise ? m[index][i] : m[i][index];
        }
        // N1：连续 5 个及以上同色
        int run = 1;
        boolean current = line[0];
        for (int i = 1; i < size; i++) {
            if (line[i] == current) {
                run++;
            } else {
                if (run >= 5) {
                    penalty += 3 + (run - 5);
                }
                current = line[i];
                run = 1;
            }
        }
        if (run >= 5) {
            penalty += 3 + (run - 5);
        }
        // N3：定位图案样式 1011101 两侧各带 4 个浅色
        for (int i = 0; i <= size - 11; i++) {
            // 正向 10111010000
            if (line[i] && !line[i + 1] && line[i + 2] && line[i + 3] && line[i + 4]
                    && line[i + 5] && !line[i + 6] && line[i + 7] && !line[i + 8]
                    && !line[i + 9] && !line[i + 10]) {
                penalty += 40;
            }
            // 反向 00001011101
            if (!line[i] && !line[i + 1] && !line[i + 2] && !line[i + 3] && line[i + 4]
                    && !line[i + 5] && line[i + 6] && line[i + 7] && line[i + 8]
                    && !line[i + 9] && line[i + 10]) {
                penalty += 40;
            }
        }
        return penalty;
    }

    // ------------------------------------------------------------------
    // BCH / Reed-Solomon
    // ------------------------------------------------------------------

    /**
     * 计算 15 位格式信息：5 位数据（2 位级别 + 3 位掩码）经 BCH(15,5) 纠错并异或固定掩码。
     */
    private static int formatInfo(int levelBits, int mask) {
        int data = (levelBits << 3) | mask;
        int remainder = data << 10;
        for (int i = 14; i >= 10; i--) {
            if (((remainder >>> i) & 1) == 1) {
                remainder ^= FORMAT_GENERATOR << (i - 10);
            }
        }
        return ((data << 10) | remainder) ^ FORMAT_MASK;
    }

    /**
     * 计算 18 位版本信息：6 位版本号经 BCH(18,6) 纠错。
     */
    private static int versionInfo(int version) {
        int remainder = version << 12;
        for (int i = 17; i >= 12; i--) {
            if (((remainder >>> i) & 1) == 1) {
                remainder ^= VERSION_GENERATOR << (i - 12);
            }
        }
        return (version << 12) | remainder;
    }

    /**
     * 计算数据的 Reed-Solomon 纠错码字。
     */
    private static int[] computeEcc(int[] data, int degree) {
        int[] generator = generatorPolynomial(degree);
        int[] remainder = new int[degree];
        for (int d : data) {
            int factor = d ^ remainder[0];
            System.arraycopy(remainder, 1, remainder, 0, degree - 1);
            remainder[degree - 1] = 0;
            if (factor != 0) {
                for (int i = 0; i < degree; i++) {
                    remainder[i] ^= gfMul(generator[i], factor);
                }
            }
        }
        return remainder;
    }

    /**
     * 生成度数为 degree 的 Reed-Solomon 生成多项式（缓存复用）。
     */
    private static int[] generatorPolynomial(int degree) {
        Integer key = Integer.valueOf(degree);
        int[] cached = GENERATOR_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        int[] poly = {1};
        for (int i = 0; i < degree; i++) {
            int[] next = new int[poly.length + 1];
            int alpha = EXP[i];
            for (int j = 0; j < poly.length; j++) {
                next[j] ^= gfMul(poly[j], alpha);
                next[j + 1] ^= poly[j];
            }
            poly = next;
        }
        GENERATOR_CACHE.putIfAbsent(key, poly);
        return poly;
    }

    /**
     * GF(256) 乘法。
     */
    private static int gfMul(int a, int b) {
        return a == 0 || b == 0 ? 0 : EXP[LOG[a] + LOG[b]];
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    /**
     * 读取当前纠错级别下指定版本的分块表行。
     */
    private int[] levelTable(int version) {
        return (level == Level.L ? LEVEL_L : LEVEL_M)[version - 1];
    }

    /**
     * 当前纠错级别下指定版本的总数据码字数。
     */
    private int totalDataCodewords(int version) {
        int[] table = levelTable(version);
        return table[2] * table[3] + table[4] * table[5];
    }

    /**
     * 取整数值第 index 位（0 为最低位）。
     */
    private static boolean bit(int value, int index) {
        return ((value >>> index) & 1) == 1;
    }

    /**
     * 取码字数组第 index 位（MSB 优先）。
     */
    private static boolean bit(int[] codewords, int index) {
        int value = codewords[index >>> 3];
        return ((value >>> (7 - (index & 7))) & 1) == 1;
    }

    /**
     * 取矩阵中指定模块颜色；静区（含越界）视为浅色。
     */
    private static boolean moduleAt(boolean[][] matrix, int size, int quiet, int x, int y) {
        int mx = x - quiet;
        int my = y - quiet;
        return mx >= 0 && my >= 0 && mx < size && my < size && matrix[my][mx];
    }

    /**
     * 数据位流缓冲器：按位追加，支持按字节读取。
     *
     * @author 星河一叶 (RainxButterfly)
     * @since 2026-08-30
     */
    private static final class BitBuffer {

        /** 位集合（Java 内置，从低位索引 0 开始）。 */
        private final java.util.BitSet bits = new java.util.BitSet();
        /** 当前位长度。 */
        private int size;

        void append(int value, int numBits) {
            for (int i = numBits - 1; i >= 0; i--) {
                if (((value >>> i) & 1) == 1) {
                    bits.set(size);
                }
                size++;
            }
        }

        int size() {
            return size;
        }

        int byteAt(int byteIndex) {
            int value = 0;
            for (int b = 0; b < 8; b++) {
                if (bits.get(byteIndex * 8 + b)) {
                    value |= 1 << (7 - b);
                }
            }
            return value;
        }
    }
}
