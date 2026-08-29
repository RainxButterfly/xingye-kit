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
 * @since 2026-08-26
 */

package com.xingheyiye.xingye.kit.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 按 RFC 4180 规范写出 CSV 的写入器：字段含逗号、双引号、CR 或 LF 时自动用双引号包裹并把内部引号翻倍。
 *
 * <p>适用场景：导出报表、生成数据交换文件；行结束符固定为 RFC 4180 规定的 CRLF（{@code \r\n}）。</p>
 *
 * <p>线程安全性：实例持有单一 Writer 游标，<b>非线程安全</b>，多线程请外部串行化或每线程各持一个实例。</p>
 *
 * <p>资源语义：以 {@link Writer} 构造时底层流归调用方所有，{@link #close()} 只冲刷不关闭；
 * 以 {@link File} 构造时由本对象负责关闭底层文件流。建议始终用 try-with-resources 包裹。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * try (CsvWriter writer = new CsvWriter(new File("users.csv"), StandardCharsets.UTF_8)) {
 *     writer.writeHeader("id", "name", "note");
 *     writer.writeRow("1", "张三", "含,逗号");
 *     writer.writeRow("2", "李四", "含\"引号\"与\n换行");
 *     writer.flush();
 * }
 * }</pre>
 *
 * @author 星河一叶 (RainxButterfly)
 * @since 2026-08-26
 */
public final class CsvWriter implements AutoCloseable {

    /** 字段分隔符：RFC 4180 规定为半角逗号。 */
    private static final char FIELD_SEPARATOR = ',';
    /** 字段包裹/转义字符：半角双引号。 */
    private static final char QUOTE_CHAR = '"';
    /** 行结束符：RFC 4180 规定为 CRLF。 */
    private static final String LINE_SEPARATOR = "\r\n";
    /** 输出缓冲大小：8192 字符。 */
    private static final int BUFFER_SIZE = 8192;

    /** 底层字符输出流。 */
    private final Writer writer;
    /** 是否由本对象负责关闭底层流（File 构造为 true，Writer 构造为 false）。 */
    private final boolean ownWriter;

    /**
     * 以既有 Writer 创建写入器（覆盖写语义由调用方决定）。
     *
     * @param writer 底层字符流，不可为 null；close() 不会关闭它，由调用方管理生命周期
     * @throws NullPointerException writer 为 null
     */
    public CsvWriter(Writer writer) {
        this.writer = Objects.requireNonNull(writer, "writer 不能为 null");
        this.ownWriter = false;
    }

    /**
     * 以文件创建写入器（覆盖写）。
     *
     * @param file 目标文件，不可为 null（父目录不存在时自动创建）
     * @param charset 写出字符集，不可为 null（建议 UTF-8）
     * @throws IOException 父目录创建失败或打开文件失败
     * @throws NullPointerException 任一参数为 null
     */
    public CsvWriter(File file, Charset charset) throws IOException {
        Objects.requireNonNull(file, "file 不能为 null");
        Objects.requireNonNull(charset, "charset 不能为 null");
        createParentIfAbsent(file);
        this.writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), charset), BUFFER_SIZE);
        this.ownWriter = true; // 文件流由本对象打开，也就由本对象负责关闭
    }

    /**
     * 写出表头行（语义上就是普通一行，仅命名上区分表头与数据）。
     *
     * @param headerNames 表头单元格，不可为 null（元素可为 null，按空串写出）
     * @throws IOException 写出失败
     * @throws NullPointerException headerNames 为 null
     */
    public void writeHeader(String... headerNames) throws IOException {
        Objects.requireNonNull(headerNames, "headerNames 不能为 null");
        writeRow(headerNames);
    }

    /**
     * 写出一行（数组形式，null 数组按空行处理）。
     *
     * @param cells 单元格数组，可为 null（按空行处理）；元素可为 null，按空串写出
     * @throws IOException 写出失败
     */
    public void writeRow(String... cells) throws IOException {
        if (cells == null) {
            writeRowInternal(Collections.<String>emptyList());
            return;
        }
        writeRowInternal(Arrays.asList(cells));
    }

    /**
     * 写出一行（列表形式）。
     *
     * @param cells 单元格列表，不可为 null；元素可为 null，按空串写出
     * @throws IOException 写出失败
     * @throws NullPointerException cells 为 null
     */
    public void writeRow(List<String> cells) throws IOException {
        Objects.requireNonNull(cells, "cells 不能为 null");
        writeRowInternal(cells);
    }

    /**
     * 批量写出多行。
     *
     * @param rows 行集合，不可为 null；元素（单行数组）可为 null，按空行处理
     * @throws IOException 写出失败
     * @throws NullPointerException rows 为 null
     */
    public void writeAll(List<String[]> rows) throws IOException {
        Objects.requireNonNull(rows, "rows 不能为 null");
        for (String[] row : rows) {
            writeRow(row);
        }
    }

    /**
     * 冲刷底层缓冲（长会话写入时建议周期性调用，防止异常退出丢数据）。
     *
     * @throws IOException 冲刷失败
     */
    public void flush() throws IOException {
        writer.flush();
    }

    /**
     * 关闭写入器：先尝试冲刷；仅当底层流由本对象打开（File 构造）时才关闭底层流。
     *
     * @throws IOException 冲刷或关闭失败
     */
    @Override
    public void close() throws IOException {
        try {
            writer.flush();
        } finally {
            if (ownWriter) {
                writer.close();
            }
        }
    }

    // ------------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------------

    /**
     * 行写出的核心实现：逐格转义，逗号连接，CRLF 收尾。
     */
    private void writeRowInternal(List<String> cells) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                line.append(FIELD_SEPARATOR);
            }
            line.append(escapeCell(cells.get(i)));
        }
        line.append(LINE_SEPARATOR);
        writer.write(line.toString());
    }

    /**
     * RFC 4180 字段转义：字段含逗号、双引号、CR、LF 任一字符时用双引号包裹，内部双引号翻倍。
     */
    private String escapeCell(String cell) {
        if (cell == null) {
            return ""; // null 单元格按空串写出
        }
        boolean needQuote = cell.indexOf(FIELD_SEPARATOR) >= 0
                || cell.indexOf(QUOTE_CHAR) >= 0
                || cell.indexOf('\r') >= 0
                || cell.indexOf('\n') >= 0;
        if (!needQuote) {
            return cell;
        }
        StringBuilder escaped = new StringBuilder(cell.length() + 2);
        escaped.append(QUOTE_CHAR);
        for (int i = 0; i < cell.length(); i++) {
            char c = cell.charAt(i);
            escaped.append(c);
            if (c == QUOTE_CHAR) {
                escaped.append(QUOTE_CHAR); // 内部引号翻倍：\" 转义为 \"\"
            }
        }
        escaped.append(QUOTE_CHAR);
        return escaped.toString();
    }

    /**
     * 父目录不存在时创建（含多级）。
     */
    private static void createParentIfAbsent(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建父目录: " + parent);
        }
    }
}
